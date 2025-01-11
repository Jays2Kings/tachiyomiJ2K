package eu.kanade.tachiyomi.ui.migration.manga.process

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.core.animation.doOnEnd
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MigrationListControllerBinding
import eu.kanade.tachiyomi.smartsearch.SmartSearchEngine
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.ui.base.SmallToolbarInterface
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.base.controller.FadeChangeHandler
import eu.kanade.tachiyomi.ui.main.BottomNavBarInterface
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.migration.SearchController
import eu.kanade.tachiyomi.ui.migration.manga.design.PreMigrationController
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.lang.toNormalized
import eu.kanade.tachiyomi.util.system.executeOnIO
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.RecyclerWindowInsetsListener
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.liftAppbarWith
import eu.kanade.tachiyomi.util.view.setTextColorAlpha
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

class MigrationListController(bundle: Bundle? = null) :
    BaseController<MigrationListControllerBinding>(bundle),
    MigrationProcessAdapter.MigrationProcessInterface,
    BottomNavBarInterface,
    SmallToolbarInterface,
    CoroutineScope {

    init {
        setHasOptionsMenu(true)
    }

    private var adapter: MigrationProcessAdapter? = null

    override val coroutineContext: CoroutineContext = Job() + Dispatchers.Default

    val config: MigrationProcedureConfig? = args.getParcelable(CONFIG_EXTRA)

    private val db: DatabaseHelper by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()

    private val smartSearchEngine = SmartSearchEngine(coroutineContext, config?.extraSearchParams)

    var migrationsJob: Job? = null
        private set
    private var migratingManga: MutableList<MigratingManga>? = null
    private var selectedPosition: Int? = null
    private var manaulMigrations = 0

    override fun createBinding(inflater: LayoutInflater) = MigrationListControllerBinding.inflate(inflater)
    override fun getTitle(): String {
        val progress = adapter?.items?.count { it.manga.migrationStatus != MigrationStatus.RUNNUNG }
        return resources?.getString(R.string.migration) + " ($progress/${adapter?.itemCount})"
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        liftAppbarWith(binding.recycler)
        val toolbarTextView = activityBinding?.toolbar?.toolbarTitle
        toolbarTextView?.setTextColorAlpha(255)
        val config = this.config ?: return

        val newMigratingManga = migratingManga ?: run {
            val new = config.mangaIds.map {
                MigratingManga(db, sourceManager, it, coroutineContext)
            }
            migratingManga = new.toMutableList()
            new
        }

        adapter = MigrationProcessAdapter(this)

        binding.recycler.adapter = adapter
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.setHasFixedSize(true)
        binding.recycler.setOnApplyWindowInsetsListener(RecyclerWindowInsetsListener)

        adapter?.updateDataSet(newMigratingManga.map { it.toModal() })

        if (migrationsJob == null) {
            migrationsJob = launch {
                runMigrations(newMigratingManga)
            }
        }
    }

    private suspend fun runMigrations(mangas: List<MigratingManga>) {
        val useSourceWithMost = preferences.useSourceWithMost().get()

        val sources = preferences.migrationSources().get().split("/").mapNotNull {
            val value = it.toLongOrNull() ?: return
            sourceManager.get(value) as? CatalogueSource
        }
        if (config == null) return
        for (manga in mangas) {
            if (migrationsJob?.isCancelled == true) {
                break
            }
            // in case it was removed
            if (manga.mangaId !in config.mangaIds) {
                continue
            }
            if (!manga.searchResult.initialized && manga.migrationJob.isActive) {
                val mangaObj = manga.manga()

                if (mangaObj == null) {
                    manga.searchResult.initialize(null)
                    continue
                }

                val mangaSource = manga.mangaSource()

                val result = try {
                    CoroutineScope(manga.migrationJob).async {
                        val validSources = if (sources.size == 1) {
                            sources
                        } else {
                            sources.filter { it.id != mangaSource.id }
                        }
                        if (useSourceWithMost) {
                            val sourceSemaphore = Semaphore(3)
                            val processedSources = AtomicInteger()

                            validSources.map { source ->
                                async source@{
                                    sourceSemaphore.withPermit {
                                        try {
                                            val searchResult = smartSearchEngine.normalSearch(
                                                source,
                                                mangaObj.title,
                                            )

                                            if (searchResult != null &&
                                                !(
                                                    searchResult.url == mangaObj.url &&
                                                        source.id == mangaObj.source
                                                    )
                                            ) {
                                                val localManga =
                                                    smartSearchEngine.networkToLocalManga(
                                                        searchResult,
                                                        source.id,
                                                    )
                                                val chapters = source.getChapterList(localManga)
                                                try {
                                                    syncChaptersWithSource(
                                                        db,
                                                        chapters,
                                                        localManga,
                                                        source,
                                                    )
                                                } catch (e: Exception) {
                                                    return@source null
                                                }
                                                manga.progress.send(validSources.size to processedSources.incrementAndGet())
                                                localManga to chapters.size
                                            } else {
                                                null
                                            }
                                        } catch (e: CancellationException) {
                                            // Ignore cancellations
                                            throw e
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                }
                            }.mapNotNull { it.await() }.maxByOrNull { it.second }?.first
                        } else {
                            validSources.forEachIndexed { index, source ->
                                val searchResult = try {
                                    val searchResult = smartSearchEngine.normalSearch(
                                        source,
                                        mangaObj.title,
                                    )

                                    if (searchResult != null) {
                                        val localManga = smartSearchEngine.networkToLocalManga(
                                            searchResult,
                                            source.id,
                                        )
                                        val chapters: List<SChapter> = try {
                                            source.getChapterList(localManga)
                                        } catch (e: java.lang.Exception) {
                                            Timber.e(e)
                                            emptyList()
                                        }
                                        withContext(Dispatchers.IO) {
                                            syncChaptersWithSource(db, chapters, localManga, source)
                                        }
                                        localManga
                                    } else {
                                        null
                                    }
                                } catch (e: CancellationException) {
                                    // Ignore cancellations
                                    throw e
                                } catch (e: Exception) {
                                    null
                                }

                                manga.progress.send(validSources.size to (index + 1))

                                if (searchResult != null) return@async searchResult
                            }

                            null
                        }
                    }.await()
                } catch (e: CancellationException) {
                    // Ignore canceled migrations
                    continue
                }

                if (result != null && result.thumbnail_url == null) {
                    try {
                        val newManga =
                            sourceManager.getOrStub(result.source).getMangaDetails(result)
                        result.copyFrom(newManga)

                        db.insertManga(result).executeAsBlocking()
                    } catch (e: CancellationException) {
                        // Ignore cancellations
                        throw e
                    } catch (e: Exception) {
                    }
                }

                manga.migrationStatus =
                    if (result == null) MigrationStatus.MANGA_NOT_FOUND else MigrationStatus.MANGA_FOUND
                adapter?.sourceFinished()
                manga.searchResult.initialize(result?.id)
            }
        }
    }

    override fun updateCount() {
        viewScope.launchUI {
            if (isControllerVisible) {
                setTitle()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun enableButtons() {
        activity?.invalidateOptionsMenu()
    }

    override fun removeManga(item: MigrationProcessItem) {
        val ids = config?.mangaIds?.toMutableList() ?: return
        val index = ids.indexOf(item.manga.mangaId)
        if (index > -1) {
            ids.removeAt(index)
            config.mangaIds = ids
            val index2 = migratingManga?.indexOf(item.manga) ?: return
            if (index2 > -1) migratingManga?.removeAt(index2)
        }
    }

    override fun noMigration() {
        launchUI {
            val res = resources
            if (res != null) {
                activity?.toast(
                    res.getQuantityString(
                        R.plurals.manga_migrated,
                        manaulMigrations,
                        manaulMigrations,
                    ),
                )
            }
            router.popCurrentController()
        }
    }

    override fun onMenuItemClick(position: Int, item: MenuItem) {
        when (item.itemId) {
            R.id.action_search_manually -> {
                launchUI {
                    val manga = adapter?.getItem(position)?.manga?.manga() ?: return@launchUI
                    selectedPosition = position
                    val sources = preferences.migrationSources().get().split("/").mapNotNull {
                        val value = it.toLongOrNull() ?: return@mapNotNull null
                        sourceManager.get(value) as? CatalogueSource
                    }
                    val validSources = if (sources.size == 1) {
                        sources
                    } else {
                        sources.filter { it.id != manga.source }
                    }
                    manga.title = manga.title.toNormalized()
                    val searchController = SearchController(manga, validSources)
                    searchController.targetController = this@MigrationListController
                    router.pushController(searchController.withFadeTransaction())
                }
            }
            R.id.action_skip -> adapter?.removeManga(position)
            R.id.action_migrate_now -> {
                adapter?.migrateManga(position, false)
                manaulMigrations++
            }
            R.id.action_copy_now -> {
                adapter?.migrateManga(position, true)
                manaulMigrations++
            }
        }
    }

    fun useMangaForMigration(manga: Manga, source: Source) {
        val firstIndex = selectedPosition ?: return
        val migratingManga = adapter?.getItem(firstIndex) ?: return
        migratingManga.manga.migrationStatus = MigrationStatus.RUNNUNG
        adapter?.notifyItemChanged(firstIndex)
        launchUI {
            val result = CoroutineScope(migratingManga.manga.migrationJob).async {
                val localManga = smartSearchEngine.networkToLocalManga(manga, source.id)
                try {
                    val chapters = source.getChapterList(localManga)
                    syncChaptersWithSource(db, chapters, localManga, source)
                } catch (e: Exception) {
                    return@async null
                }
                localManga
            }.await()

            if (result != null) {
                try {
                    val newManga =
                        sourceManager.getOrStub(result.source).getMangaDetails(result)
                    result.copyFrom(newManga)

                    db.insertManga(result).executeAsBlocking()
                } catch (e: CancellationException) {
                    // Ignore cancellations
                    throw e
                } catch (e: Exception) {
                }

                migratingManga.manga.migrationStatus = MigrationStatus.MANGA_FOUND
                migratingManga.manga.searchResult.set(result.id)
                adapter?.notifyItemChanged(firstIndex)
            } else {
                migratingManga.manga.migrationStatus = MigrationStatus.MANGA_NOT_FOUND
                activity?.toast(R.string.no_chapters_found_for_migration, Toast.LENGTH_LONG)
                adapter?.notifyItemChanged(firstIndex)
            }
        }
    }

    fun migrateMangas() {
        launchUI {
            adapter?.performMigrations(false)
            navigateOut()
        }
    }

    fun copyMangas() {
        launchUI {
            adapter?.performMigrations(true)
            navigateOut()
        }
    }

    private fun navigateOut() {
        if (migratingManga?.size == 1) {
            launchUI {
                val hasDetails = router.backstack.any { it.controller is MangaDetailsController }
                if (hasDetails) {
                    val manga = migratingManga?.firstOrNull()?.searchResult?.get()?.let {
                        db.getManga(it).executeOnIO()
                    }
                    if (manga != null) {
                        val newStack = router.backstack.filter {
                            it.controller !is MangaDetailsController &&
                                it.controller !is MigrationListController &&
                                it.controller !is PreMigrationController
                        } + MangaDetailsController(manga).withFadeTransaction()
                        router.setBackstack(newStack, FadeChangeHandler())
                        return@launchUI
                    }
                }
                router.popCurrentController()
            }
        } else {
            router.popCurrentController()
        }
    }

    override fun handleBack(): Boolean {
        view?.let { view ->
            if (view.x != 0f || view.alpha != 1f) {
                val animatorSet = AnimatorSet()
                animatorSet.play(ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 1f))
                val tA = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, view.translationX, 0f)
                tA.addUpdateListener {
                    activityBinding?.backShadow?.let { backShadow ->
                        backShadow.x = view.x - backShadow.width
                    }
                }
                animatorSet.duration = 150
                animatorSet.doOnEnd {
                    activityBinding?.backShadow?.alpha = 0.25f
                    activityBinding?.backShadow?.isVisible = false
                }
                animatorSet.play(tA)
                animatorSet.start()
            }
        }
        activity?.materialAlertDialog()
            ?.setTitle(R.string.stop_migrating)
            ?.setPositiveButton(R.string.stop) { _, _ ->
                router.popCurrentController()
                migrationsJob?.cancel()
            }
            ?.setNegativeButton(android.R.string.cancel, null)?.show()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.migration_list, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        // Initialize menu items.

        val allMangasDone = adapter?.allMangasDone() ?: return

        val menuCopy = menu.findItem(R.id.action_copy_manga)
        val menuMigrate = menu.findItem(R.id.action_migrate_manga)

        if (adapter?.itemCount == 1) {
            menuMigrate.icon = VectorDrawableCompat.create(
                resources!!,
                R.drawable.ic_done_24dp,
                null,
            )
        }

        menuCopy.icon?.mutate()
        menuMigrate.icon?.mutate()
        val tintColor = activity?.getResourceColor(R.attr.actionBarTintColor) ?: Color.WHITE
        val translucentWhite = ColorUtils.setAlphaComponent(tintColor, 127)
        menuCopy.icon?.setTint(if (allMangasDone) tintColor else translucentWhite)
        menuMigrate?.icon?.setTint(if (allMangasDone) tintColor else translucentWhite)
        menuCopy.isEnabled = allMangasDone
        menuMigrate.isEnabled = allMangasDone
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val totalManga = adapter?.itemCount ?: 0
        val mangaSkipped = adapter?.mangasSkipped() ?: 0
        when (item.itemId) {
            R.id.action_copy_manga, R.id.action_migrate_manga -> {
                showCopyMigrateDialog(
                    R.id.action_copy_manga == item.itemId,
                    totalManga,
                    mangaSkipped,
                )
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showCopyMigrateDialog(copy: Boolean, totalManga: Int, mangaSkipped: Int) {
        val activity = activity ?: return
        val confirmRes = if (copy) R.plurals.copy_manga else R.plurals.migrate_manga
        val skipping by lazy { activity.getString(R.string.skipping_, mangaSkipped) }
        val additionalString = if (mangaSkipped > 0) " $skipping" else ""
        val confirmString = activity.resources.getQuantityString(
            confirmRes,
            totalManga,
            totalManga,
            additionalString,
        )
        activity.materialAlertDialog()
            .setMessage(confirmString)
            .setPositiveButton(if (copy) R.string.copy_value else R.string.migrate) { _, _ ->
                if (copy) {
                    copyMangas()
                } else {
                    migrateMangas()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun canChangeTabs(block: () -> Unit): Boolean {
        if (migrationsJob?.isCancelled == false || adapter?.allMangasDone() == true) {
            activity?.materialAlertDialog()
                ?.setTitle(R.string.stop_migrating)
                ?.setPositiveButton(R.string.stop) { _, _ ->
                    block()
                    migrationsJob?.cancel()
                }
                ?.setNegativeButton(android.R.string.cancel, null)
            return false
        }
        return true
    }

    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
        super.handleOnBackProgressed(backEvent)
        if (router.backstackSize > 1 && isControllerVisible) {
            router.backstack[router.backstackSize - 2].controller.view?.let { view2 ->
                view2.alpha = 0f
                view2.x = 0f
            }
        }
    }

    companion object {
        const val CONFIG_EXTRA = "config_extra"
        const val TAG = "migration_list"

        fun create(config: MigrationProcedureConfig): MigrationListController {
            return MigrationListController(
                Bundle().apply {
                    putParcelable(CONFIG_EXTRA, config)
                },
            )
        }
    }
}
