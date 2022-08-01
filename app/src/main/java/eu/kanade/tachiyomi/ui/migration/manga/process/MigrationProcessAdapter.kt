package eu.kanade.tachiyomi.ui.migration.manga.process

import android.view.MenuItem
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.migration.MigrationFlags
import eu.kanade.tachiyomi.util.system.launchUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Date

class MigrationProcessAdapter(
    val controller: MigrationListController,
) : FlexibleAdapter<MigrationProcessItem>(null, controller, true) {

    private val db: DatabaseHelper by injectLazy()
    var items: List<MigrationProcessItem> = emptyList()
    val preferences: PreferencesHelper by injectLazy()
    val sourceManager: SourceManager by injectLazy()

    var showOutline = preferences.outlineOnCovers().get()
    val menuItemListener: MigrationProcessInterface = controller

    private val enhancedServices by lazy { Injekt.get<TrackManager>().services.filterIsInstance<EnhancedTrackService>() }

    override fun updateDataSet(items: List<MigrationProcessItem>?) {
        this.items = items ?: emptyList()
        super.updateDataSet(items)
    }

    interface MigrationProcessInterface {
        fun onMenuItemClick(position: Int, item: MenuItem)
        fun enableButtons()
        fun removeManga(item: MigrationProcessItem)
        fun noMigration()
        fun updateCount()
    }

    fun sourceFinished() {
        menuItemListener.updateCount()
        if (itemCount == 0) menuItemListener.noMigration()
        if (allMangasDone()) menuItemListener.enableButtons()
    }

    fun allMangasDone() = (
        items.all {
            it.manga.migrationStatus != MigrationStatus
                .RUNNUNG
        } && items.any { it.manga.migrationStatus == MigrationStatus.MANGA_FOUND }
        )

    fun mangasSkipped() =
        (items.count { it.manga.migrationStatus == MigrationStatus.MANGA_NOT_FOUND })

    suspend fun performMigrations(copy: Boolean) {
        withContext(Dispatchers.IO) {
            db.inTransaction {
                currentItems.forEach { migratingManga ->
                    val manga = migratingManga.manga
                    if (manga.searchResult.initialized) {
                        val toMangaObj =
                            db.getManga(manga.searchResult.get() ?: return@forEach)
                                .executeAsBlocking()
                                ?: return@forEach
                        val prevManga = manga.manga() ?: return@forEach
                        val source = sourceManager.get(toMangaObj.source) ?: return@forEach
                        val prevSource = sourceManager.get(prevManga.source)
                        migrateMangaInternal(
                            prevSource,
                            source,
                            prevManga,
                            toMangaObj,
                            !copy,
                        )
                    }
                }
            }
        }
    }

    fun migrateManga(position: Int, copy: Boolean) {
        launchUI {
            val manga = getItem(position)?.manga ?: return@launchUI
            db.inTransaction {
                val toMangaObj =
                    db.getManga(manga.searchResult.get() ?: return@launchUI).executeAsBlocking()
                        ?: return@launchUI
                val prevManga = manga.manga() ?: return@launchUI
                val source = sourceManager.get(toMangaObj.source) ?: return@launchUI
                val prevSource = sourceManager.get(prevManga.source)
                migrateMangaInternal(
                    prevSource,
                    source,
                    prevManga,
                    toMangaObj,
                    !copy,
                )
            }
            removeManga(position)
        }
    }

    fun removeManga(position: Int) {
        val item = getItem(position) ?: return
        menuItemListener.removeManga(item)
        item.manga.migrationJob.cancel()
        removeItem(position)
        items = currentItems
        sourceFinished()
    }

    private fun migrateMangaInternal(
        prevSource: Source?,
        source: Source,
        prevManga: Manga,
        manga: Manga,
        replace: Boolean,
    ) {
        if (controller.config == null) return
        val flags = preferences.migrateFlags().get()
        migrateMangaInternal(flags, db, enhancedServices, prevSource, source, prevManga, manga, replace)
    }

    companion object {

        fun migrateMangaInternal(
            flags: Int,
            db: DatabaseHelper,
            enhancedServices: List<EnhancedTrackService>,
            prevSource: Source?,
            source: Source,
            prevManga: Manga,
            manga: Manga,
            replace: Boolean,
        ) {
            // Update chapters read
            if (MigrationFlags.hasChapters(flags)) {
                val prevMangaChapters = db.getChapters(prevManga).executeAsBlocking()
                val maxChapterRead =
                    prevMangaChapters.filter { it.read }.maxOfOrNull { it.chapter_number } ?: 0f
                val dbChapters = db.getChapters(manga).executeAsBlocking()
                val prevHistoryList = db.getHistoryByMangaId(prevManga.id!!).executeAsBlocking()
                val historyList = mutableListOf<History>()
                for (chapter in dbChapters) {
                    if (chapter.isRecognizedNumber) {
                        val prevChapter =
                            prevMangaChapters.find { it.isRecognizedNumber && it.chapter_number == chapter.chapter_number }
                        if (prevChapter != null) {
                            chapter.bookmark = prevChapter.bookmark
                            chapter.read = prevChapter.read
                            chapter.date_fetch = prevChapter.date_fetch
                            prevHistoryList.find { it.chapter_id == prevChapter.id }
                                ?.let { prevHistory ->
                                    val history = History.create(chapter)
                                        .apply {
                                            last_read = prevHistory.last_read
                                            time_read = prevHistory.time_read
                                        }
                                    historyList.add(history)
                                }
                        } else if (chapter.chapter_number <= maxChapterRead) {
                            chapter.read = true
                        }
                    }
                }
                db.insertChapters(dbChapters).executeAsBlocking()
                db.upsertHistoryLastRead(historyList).executeAsBlocking()
            }
            // Update categories
            if (MigrationFlags.hasCategories(flags)) {
                val categories = db.getCategoriesForManga(prevManga).executeAsBlocking()
                val mangaCategories = categories.map { MangaCategory.create(manga, it) }
                db.setMangaCategories(mangaCategories, listOf(manga))
            }
            // Update track
            if (MigrationFlags.hasTracks(flags)) {
                val tracksToUpdate =
                    db.getTracks(prevManga).executeAsBlocking().mapNotNull { track ->
                        track.id = null
                        track.manga_id = manga.id!!

                        val service = enhancedServices
                            .firstOrNull { it.isTrackFrom(track, prevManga, prevSource) }
                        if (service != null) service.migrateTrack(track, manga, source)
                        else track
                    }
                db.insertTracks(tracksToUpdate).executeAsBlocking()
            }
            // Update favorite status
            if (replace) {
                prevManga.favorite = false
                db.updateMangaFavorite(prevManga).executeAsBlocking()
            }
            manga.favorite = true
            if (replace) manga.date_added = prevManga.date_added
            else manga.date_added = Date().time
            db.updateMangaFavorite(manga).executeAsBlocking()
            db.updateMangaAdded(manga).executeAsBlocking()
            db.updateMangaTitle(manga).executeAsBlocking()
        }
    }
}
