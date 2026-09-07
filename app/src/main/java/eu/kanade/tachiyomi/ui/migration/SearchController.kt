package eu.kanade.tachiyomi.ui.migration

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import androidx.core.os.bundleOf
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.main.BottomNavBarInterface
import eu.kanade.tachiyomi.ui.migration.manga.process.MigrationListController
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchCardAdapter
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class SearchController(
    private var manga: Manga? = null,
    private var sources: List<CatalogueSource>? = null,
) : GlobalSearchController(
        manga?.originalTitle,
        bundle =
            bundleOf(
                OLD_MANGA to manga?.id,
                SOURCES to sources?.map { it.id }?.toLongArray(),
            ),
    ),
    BottomNavBarInterface {
    /**
     * Called when controller is initialized.
     */
    init {
        setHasOptionsMenu(true)
    }

    constructor(mangaId: Long, sources: LongArray) :
        this(
            Injekt.get<DatabaseHelper>().getManga(mangaId).executeAsBlocking(),
            sources.map { Injekt.get<SourceManager>().getOrStub(it) }.filterIsInstance<CatalogueSource>(),
        )

    @Suppress("unused")
    constructor(bundle: Bundle) : this(
        bundle.getLong(OLD_MANGA),
        bundle.getLongArray(SOURCES) ?: LongArray(0),
    )

    override val presenter = SearchPresenter(initialQuery, manga!!, sources = sources)

    // picking a migration target isnt a browse search, keep it out of the history
    override val supportsSearchHistory: Boolean = false

    override fun onMangaClick(manga: Manga) {
        if (targetController is MigrationListController) {
            val migrationListController = targetController as? MigrationListController
            val sourceManager: SourceManager by injectLazy()
            val source = sourceManager.get(manga.source) ?: return
            migrationListController?.useMangaForMigration(manga, source)
            router.popCurrentController()
            return
        }
    }

    override fun onMangaLongClick(
        position: Int,
        adapter: GlobalSearchCardAdapter,
    ) {
        // Call parent's default click listener
        val manga = adapter.getItem(position)?.manga ?: return
        super.onMangaClick(manga)
    }

    /**
     * Adds items to the options menu.
     *
     * @param menu menu containing options.
     * @param inflater used to load the menu xml.
     */
    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        // Inflate menu.
        inflater.inflate(R.menu.catalogue_new_list, menu)

        setOnQueryTextChangeListener(activityBinding?.searchToolbar?.searchView, onlyOnSubmit = true, hideKbOnSubmit = true) {
            presenter.search(it ?: "")
            setTitle() // Update toolbar title
            true
        }
    }

    override fun canChangeTabs(block: () -> Unit): Boolean {
        val migrationListController =
            router.getControllerWithTag(MigrationListController.TAG)
                as? BottomNavBarInterface
        if (migrationListController != null) return migrationListController.canChangeTabs(block)
        return true
    }

    companion object {
        const val OLD_MANGA = "old_manga"
        const val SOURCES = "sources"
    }
}
