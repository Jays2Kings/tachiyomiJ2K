package eu.kanade.tachiyomi.util

import android.app.Activity
import android.content.DialogInterface
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bluelinelabs.conductor.Controller
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.category.addtolibrary.SetCategoriesSheet
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.migration.MigrationFlags
import eu.kanade.tachiyomi.ui.migration.manga.process.MigrationProcessAdapter
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithTrackServiceTwoWay
import eu.kanade.tachiyomi.util.lang.asButton
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.setCustomTitleAndMessage
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

fun Manga.isLocal() = source == LocalSource.ID

fun Manga.shouldDownloadNewChapters(db: DatabaseHelper, prefs: PreferencesHelper): Boolean {
    if (!favorite) return false

    // Boolean to determine if user wants to automatically download new chapters.
    val downloadNew = prefs.downloadNew().get()
    if (!downloadNew) return false

    val categoriesToDownload = prefs.downloadNewCategories().get().map(String::toInt)
    if (categoriesToDownload.isEmpty()) return true

    // Get all categories, else default category (0)
    val categoriesForManga =
        db.getCategoriesForManga(this).executeAsBlocking()
            .mapNotNull { it.id }
            .takeUnless { it.isEmpty() } ?: listOf(0)

    val categoriesToExclude = prefs.downloadNewCategoriesExclude().get().map(String::toInt)
    if (categoriesForManga.intersect(categoriesToExclude).isNotEmpty()) return false

    return categoriesForManga.intersect(categoriesToDownload).isNotEmpty()
}

fun Manga.moveCategories(
    db: DatabaseHelper,
    activity: Activity,
    onMangaMoved: () -> Unit
) {
    val categories = db.getCategories().executeAsBlocking()
    val categoriesForManga = db.getCategoriesForManga(this).executeAsBlocking()
    val ids = categoriesForManga.mapNotNull { it.id }.toTypedArray()
    SetCategoriesSheet(
        activity,
        this,
        categories.toMutableList(),
        ids,
        false
    ) {
        onMangaMoved()
    }.show()
}

fun List<Manga>.moveCategories(
    db: DatabaseHelper,
    activity: Activity,
    onMangaMoved: () -> Unit
) {
    if (this.isEmpty()) return
    val categories = db.getCategories().executeAsBlocking()
    val commonCategories = map { db.getCategoriesForManga(it).executeAsBlocking() }
        .reduce { set1: Iterable<Category>, set2 -> set1.intersect(set2).toMutableList() }
        .toTypedArray()
    val mangaCategories = map { db.getCategoriesForManga(it).executeAsBlocking() }
    val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2).toMutableList() }
    val mixedCategories = mangaCategories.flatten().distinct().subtract(common).toMutableList()
    SetCategoriesSheet(
        activity,
        this,
        categories.toMutableList(),
        categories.map {
            when (it) {
                in commonCategories -> TriStateCheckBox.State.CHECKED
                in mixedCategories -> TriStateCheckBox.State.IGNORE
                else -> TriStateCheckBox.State.UNCHECKED
            }
        }.toTypedArray(),
        false
    ) {
        onMangaMoved()
    }.show()
}

fun Manga.addOrRemoveToFavorites(
    db: DatabaseHelper,
    preferences: PreferencesHelper,
    view: View,
    activity: Activity,
    sourceManager: SourceManager,
    controller: Controller,
    checkForDupes: Boolean = true,
    onMangaAdded: (Pair<Long, Boolean>?) -> Unit,
    onMangaMoved: () -> Unit,
    onMangaDeleted: () -> Unit,
): Snackbar? {
    if (!favorite) {
        if (checkForDupes) {
            val duplicateManga = db.getDuplicateLibraryManga(this).executeAsBlocking()
            if (duplicateManga != null) {
                showAddDuplicateDialog(
                    this,
                    duplicateManga,
                    activity,
                    db,
                    sourceManager,
                    controller,
                    addManga = {
                        addOrRemoveToFavorites(
                            db,
                            preferences,
                            view,
                            activity,
                            sourceManager,
                            controller,
                            false,
                            onMangaAdded,
                            onMangaMoved,
                            onMangaDeleted
                        )
                    },
                    migrateManga = { source, faved ->
                        onMangaAdded(source to faved)
                    }
                )
                return null
            }
        }

        val categories = db.getCategories().executeAsBlocking()
        val defaultCategoryId = preferences.defaultCategory()
        val defaultCategory = categories.find { it.id == defaultCategoryId }
        when {
            defaultCategory != null -> {
                favorite = true
                date_added = Date().time
                autoAddTrack(db, onMangaMoved)
                db.insertManga(this).executeAsBlocking()
                val mc = MangaCategory.create(this, defaultCategory)
                db.setMangaCategories(listOf(mc), listOf(this))
                onMangaMoved()
                return view.snack(activity.getString(R.string.added_to_, defaultCategory.name)) {
                    setAction(R.string.change) {
                        moveCategories(db, activity, onMangaMoved)
                    }
                }
            }
            defaultCategoryId == 0 || categories.isEmpty() -> { // 'Default' or no category
                favorite = true
                date_added = Date().time
                autoAddTrack(db, onMangaMoved)
                db.insertManga(this).executeAsBlocking()
                db.setMangaCategories(emptyList(), listOf(this))
                onMangaMoved()
                return if (categories.isNotEmpty()) {
                    view.snack(activity.getString(R.string.added_to_, activity.getString(R.string.default_value))) {
                        setAction(R.string.change) {
                            moveCategories(db, activity, onMangaMoved)
                        }
                    }
                } else {
                    view.snack(R.string.added_to_library)
                }
            }
            else -> {
                val categoriesForManga = db.getCategoriesForManga(this).executeAsBlocking()
                val ids = categoriesForManga.mapNotNull { it.id }.toTypedArray()

                SetCategoriesSheet(
                    activity,
                    this,
                    categories.toMutableList(),
                    ids,
                    true
                ) {
                    onMangaAdded(null)
                    autoAddTrack(db, onMangaMoved)
                }.show()
            }
        }
    } else {
        val lastAddedDate = date_added
        favorite = false
        date_added = 0
        db.insertManga(this).executeAsBlocking()
        onMangaMoved()
        return view.snack(view.context.getString(R.string.removed_from_library), Snackbar.LENGTH_INDEFINITE) {
            setAction(R.string.undo) {
                favorite = true
                date_added = lastAddedDate
                db.insertManga(this@addOrRemoveToFavorites).executeAsBlocking()
                onMangaMoved()
            }
            addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        super.onDismissed(transientBottomBar, event)
                        if (!favorite) {
                            onMangaDeleted()
                        }
                    }
                }
            )
        }
    }
    return null
}

private fun showAddDuplicateDialog(
    newManga: Manga,
    libraryManga: Manga,
    activity: Activity,
    db: DatabaseHelper,
    sourceManager: SourceManager,
    controller: Controller,
    addManga: () -> Unit,
    migrateManga: (Long, Boolean) -> Unit,
) {
    val source = sourceManager.getOrStub(libraryManga.source)

    fun migrateManga(mDialog: DialogInterface, replace: Boolean) {
        val listView = (mDialog as AlertDialog).listView
        var flags = 0
        if (listView.isItemChecked(0)) flags = flags or MigrationFlags.CHAPTERS
        if (listView.isItemChecked(1)) flags = flags or MigrationFlags.CATEGORIES
        if (listView.isItemChecked(2)) flags = flags or MigrationFlags.TRACK
        val enhancedServices by lazy { Injekt.get<TrackManager>().services.filterIsInstance<EnhancedTrackService>() }
        MigrationProcessAdapter.migrateMangaInternal(
            flags,
            db,
            enhancedServices,
            source,
            sourceManager.getOrStub(newManga.source),
            libraryManga,
            newManga,
            replace
        )
        migrateManga(libraryManga.source, !replace)
    }

    activity.materialAlertDialog().apply {
        setCustomTitleAndMessage(0, activity.getString(R.string.confirm_manga_add_duplicate, source.name))
        setItems(
            arrayOf(
                activity.getString(R.string.show_, libraryManga.seriesType(activity, sourceManager)).asButton(activity),
                activity.getString(R.string.add_to_library).asButton(activity),
                activity.getString(R.string.migrate).asButton(activity, !newManga.initialized),
            )
        ) { dialog, i ->
            when (i) {
                0 -> controller.router.pushController(
                    MangaDetailsController(libraryManga)
                        .withFadeTransaction()
                )
                1 -> addManga()
                2 -> {
                    if (!newManga.initialized) {
                        activity.toast(R.string.must_view_details_before_migration, Toast.LENGTH_LONG)
                        return@setItems
                    }
                    activity.materialAlertDialog().apply {
                        setTitle(R.string.migration)
                        setMultiChoiceItems(
                            arrayOf(
                                activity.getString(R.string.chapters),
                                activity.getString(R.string.categories),
                                activity.getString(R.string.tracking),
                            ),
                            booleanArrayOf(true, true, true), null
                        )
                        setPositiveButton(R.string.migrate) { mDialog, _ ->
                            migrateManga(mDialog, true)
                        }
                        setNegativeButton(R.string.copy) { mDialog, _ ->
                            migrateManga(mDialog, false)
                        }
                        setNeutralButton(android.R.string.cancel, null)
                        setCancelable(true)
                    }.show()
                }
                else -> {}
            }
            dialog.dismiss()
        }
        setNegativeButton(activity.getString(android.R.string.cancel)) { _, _ -> }
        setCancelable(true)
    }.create().apply {
        setOnShowListener {
            if (!newManga.initialized) {
                val listView = (it as AlertDialog).listView
                val view = listView.getChildAt(2)
                view?.setOnClickListener {
                    if (!newManga.initialized) {
                        activity.toast(
                            R.string.must_view_details_before_migration,
                            Toast.LENGTH_LONG
                        )
                    }
                }
            }
        }
    }.show()
}

fun Manga.autoAddTrack(db: DatabaseHelper, onMangaMoved: () -> Unit) {
    val loggedServices = Injekt.get<TrackManager>().services.filter { it.isLogged }
    val source = Injekt.get<SourceManager>().getOrStub(this.source)
    loggedServices
        .filterIsInstance<EnhancedTrackService>()
        .filter { it.accept(source) }
        .forEach { service ->
            launchIO {
                try {
                    service.match(this@autoAddTrack)?.let { track ->
                        track.manga_id = this@autoAddTrack.id!!
                        (service as TrackService).bind(track)
                        db.insertTrack(track).executeAsBlocking()

                        syncChaptersWithTrackServiceTwoWay(db, db.getChapters(this@autoAddTrack).executeAsBlocking(), track, service as TrackService)
                        withUIContext {
                            onMangaMoved()
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Could not match manga: ${this@autoAddTrack.title} with service $service")
                }
            }
        }
}
