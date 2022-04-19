package eu.kanade.tachiyomi.util.chapter

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bluelinelabs.conductor.Controller
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.util.system.launchIO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs

/**
 * Helper method for syncing a remote track with the local chapters, and back
 *
 * @param db the database.
 * @param chapters a list of chapters from the source.
 * @param remoteTrack the remote Track object.
 * @param service the tracker service.
 */
fun syncChaptersWithTrackServiceTwoWay(db: DatabaseHelper, chapters: List<Chapter>, remoteTrack: Track, service: TrackService) {
    val sortedChapters = chapters.sortedBy { it.chapter_number }
    sortedChapters
        .filterIndexed { index, chapter -> index < remoteTrack.last_chapter_read && !chapter.read }
        .forEach { it.read = true }
    db.updateChaptersProgress(sortedChapters).executeAsBlocking()

    val localLastRead = when {
        sortedChapters.all { it.read } -> sortedChapters.size
        sortedChapters.any { !it.read } -> sortedChapters.indexOfFirst { !it.read }
        else -> 0
    }

    // update remote
    remoteTrack.last_chapter_read = localLastRead

    launchIO {
        try {
            service.update(remoteTrack)
            db.insertTrack(remoteTrack).executeAsBlocking()
        } catch (e: Throwable) {
            Timber.w(e)
        }
    }
}

private var job: Job? = null
private var oldMangaId: Long? = null

/**
 * Starts the service that updates the last chapter read in sync services. This operation
 * will run in a background thread and errors are ignored.
 */
fun Controller.updateTrackChapterMarkedAsRead(
    db: DatabaseHelper,
    preferences: PreferencesHelper,
    oldLastChapter: Chapter?,
    newLastChapter: Chapter?,
    mangaId: Long?,
    delay: Long = 3000
) {
    if (!preferences.trackMarkedAsRead()) return

    val oldChapterRead = oldLastChapter?.chapter_number?.toInt() ?: 0
    val newChapterRead = newLastChapter?.chapter_number?.toInt() ?: 0

    // To avoid unnecessary calls if multiple marked as read for same manga
    if (mangaId == oldMangaId) job?.cancel() else oldMangaId = mangaId

    // We want these to execute even if the presenter is destroyed
    job = (activity as AppCompatActivity).lifecycleScope.launchIO {
        delay(delay)
        updateTrackChapterRead(db, mangaId, oldChapterRead, newChapterRead)
        (router.backstack.lastOrNull()?.controller as? MangaDetailsController)?.presenter?.fetchTracks()
    }
}

suspend fun updateTrackChapterRead(db: DatabaseHelper, mangaId: Long?, oldChapterRead: Int, newChapterRead: Int) {
    val trackManager = Injekt.get<TrackManager>()
    val trackList = db.getTracks(mangaId).executeAsBlocking()
    trackList.map { track ->
        val service = trackManager.getService(track.sync_id)
        if (service != null && service.isLogged) {
            val shouldCustomCount = listOf(
                abs(track.last_chapter_read - oldChapterRead), oldChapterRead, track.last_chapter_read
            ).all { it > 15 }
            val newCountChapter = if (shouldCustomCount) {
                (track.last_chapter_read + (newChapterRead - oldChapterRead)).coerceAtLeast(0)
            } else newChapterRead
            try {
                track.last_chapter_read = newCountChapter
                service.update(track, true)
                db.insertTrack(track).executeAsBlocking()
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }
}
