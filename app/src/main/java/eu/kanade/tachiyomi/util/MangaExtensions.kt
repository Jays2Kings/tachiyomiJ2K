package eu.kanade.tachiyomi.util

import android.app.Activity
import android.view.View
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.UnattendedTrackService
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.category.addtolibrary.SetCategoriesSheet
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithTrackServiceTwoWay
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

fun Manga.isLocal() = source == LocalSource.ID

fun Manga.shouldDownloadNewChapters(db: DatabaseHelper, prefs: PreferencesHelper): Boolean {
    if (!favorite) return false

    // Boolean to determine if user wants to automatically download new chapters.
    val downloadNew = prefs.downloadNew()
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
    onMangaAdded: () -> Unit,
    onMangaMoved: () -> Unit,
    onMangaDeleted: () -> Unit
): Snackbar? {
    if (!favorite) {
        val categories = db.getCategories().executeAsBlocking()
        val defaultCategoryId = preferences.defaultCategory()
        val defaultCategory = categories.find { it.id == defaultCategoryId }
        when {
            defaultCategory != null -> {
                favorite = true
                date_added = Date().time
                if (preferences.autoAddTrack()) {
                    autoAddTrack(db, onMangaMoved)
                }
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
                if (preferences.autoAddTrack()) {
                    autoAddTrack(db, onMangaMoved)
                }
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
                    onMangaAdded()
                    if (preferences.autoAddTrack()) {
                        autoAddTrack(db, onMangaMoved)
                    }
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

fun Manga.autoAddTrack(db: DatabaseHelper, onMangaMoved: () -> Unit) {
    val loggedServices = Injekt.get<TrackManager>().services.filter { it.isLogged }
    val source = Injekt.get<SourceManager>().getOrStub(this.source)
    loggedServices
        .filterIsInstance<UnattendedTrackService>()
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
