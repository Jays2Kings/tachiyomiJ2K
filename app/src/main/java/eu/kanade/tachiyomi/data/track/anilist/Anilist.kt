package eu.kanade.tachiyomi.data.track.anilist

import android.content.Context
import android.graphics.Color
import com.google.gson.Gson
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.track.TrackService
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

class Anilist(private val context: Context, id: Int) : TrackService(id) {

    override val name = "AniList"

    private val gson: Gson by injectLazy()

    private val interceptor by lazy { AnilistInterceptor(this, getPassword()) }

    private val api by lazy { AnilistApi(client, interceptor) }

    private val scorePreference = preferences.anilistScoreType()

    init {
        // If the preference is an int from APIv1, logout user to force using APIv2
        try {
            scorePreference.get()
        } catch (e: ClassCastException) {
            logout()
            scorePreference.delete()
        }
    }

    override fun getLogo() = R.drawable.ic_tracker_anilist

    override fun getLogoColor() = Color.rgb(18, 25, 35)

    override fun getStatusList() = listOf(READING, PLANNING, COMPLETED, REPEATING, PAUSED, DROPPED)

    override fun isCompletedStatus(index: Int) = getStatusList()[index] == COMPLETED

    override fun getStatus(status: Int): String = with(context) {
        when (status) {
            READING -> getString(R.string.reading)
            COMPLETED -> getString(R.string.completed)
            PAUSED -> getString(R.string.paused)
            DROPPED -> getString(R.string.dropped)
            PLANNING -> getString(R.string.plan_to_read)
            REPEATING -> getString(R.string.rereading)
            else -> ""
        }
    }

    override fun getGlobalStatus(status: Int): String = with(context) {
        when (status) {
            READING -> getString(R.string.reading)
            PLANNING -> getString(R.string.plan_to_read)
            COMPLETED -> getString(R.string.completed)
            PAUSED -> getString(R.string.on_hold)
            DROPPED -> getString(R.string.dropped)
            REPEATING -> getString(R.string.rereading)
            else -> ""
        }
    }

    override fun getScoreList(): List<String> {
        return when (scorePreference.getOrDefault()) {
            // 10 point
            POINT_10 -> IntRange(0, 10).map(Int::toString)
            // 100 point
            POINT_100 -> IntRange(0, 100).map(Int::toString)
            // 5 stars
            POINT_5 -> IntRange(0, 5).map { "$it ★" }
            // Smiley
            POINT_3 -> listOf("-", "😦", "😐", "😊")
            // 10 point decimal
            POINT_10_DECIMAL -> IntRange(0, 100).map { (it / 10f).toString() }
            else -> throw Exception("Unknown score type")
        }
    }

    override fun indexToScore(index: Int): Float {
        return when (scorePreference.getOrDefault()) {
            // 10 point
            POINT_10 -> index * 10f
            // 100 point
            POINT_100 -> index.toFloat()
            // 5 stars
            POINT_5 -> when (index) {
                0 -> 0f
                else -> index * 20f - 10f
            }
            // Smiley
            POINT_3 -> when (index) {
                0 -> 0f
                else -> index * 25f + 10f
            }
            // 10 point decimal
            POINT_10_DECIMAL -> index.toFloat()
            else -> throw Exception("Unknown score type")
        }
    }

    override fun displayScore(track: Track): String {
        val score = track.score

        return when (scorePreference.getOrDefault()) {
            POINT_5 -> when (score) {
                0f -> "0 ★"
                else -> "${((score + 10) / 20).toInt()} ★"
            }
            POINT_3 -> when {
                score == 0f -> "0"
                score <= 35 -> "😦"
                score <= 60 -> "😐"
                else -> "😊"
            }
            else -> track.toAnilistScore()
        }
    }

    override suspend fun update(track: Track): Track {
        if (track.total_chapters != 0 && track.last_chapter_read == track.total_chapters) {
            track.status = COMPLETED
        }
        // If user was using API v1 fetch library_id
        if (track.library_id == null || track.library_id!! == 0L) {
            val libManga = api.findLibManga(track, getUsername().toInt())
                ?: throw Exception("$track not found on user library")

            track.library_id = libManga.library_id
        }

        return api.updateLibraryManga(track)
    }

    override suspend fun bind(track: Track): Track {
        val remoteTrack = api.findLibManga(track, getUsername().toInt())

        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.library_id = remoteTrack.library_id
            update(track)
        } else {
            // Set default fields if it's not found in the list
            track.score = DEFAULT_SCORE.toFloat()
            track.status = DEFAULT_STATUS
            api.addLibManga(track)
        }
    }

    override fun canRemoveFromService(): Boolean = true

    override suspend fun removeFromService(track: Track): Boolean {
        return api.remove(track)
    }

    override suspend fun search(query: String) = api.search(query)

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getLibManga(track, getUsername().toInt())
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(token: String): Boolean {
        val oauth = api.createOAuth(token)
        interceptor.setAuth(oauth)

        return try {
            val currentUser = api.getCurrentUser()
            scorePreference.set(currentUser.second)
            saveCredentials(currentUser.first.toString(), oauth.access_token)
            true
        } catch (e: Exception) {
            Timber.e(e)
            logout()
            false
        }
    }

    override fun logout() {
        super.logout()
        preferences.trackToken(this).set(null)
        interceptor.setAuth(null)
    }

    fun saveOAuth(oAuth: OAuth?) {
        preferences.trackToken(this).set(gson.toJson(oAuth))
    }

    fun loadOAuth(): OAuth? {
        return try {
            gson.fromJson(preferences.trackToken(this).get(), OAuth::class.java)
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    }

    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val PAUSED = 3
        const val DROPPED = 4
        const val PLANNING = 5
        const val REPEATING = 6

        const val DEFAULT_STATUS = READING
        const val DEFAULT_SCORE = 0

        const val POINT_100 = "POINT_100"
        const val POINT_10 = "POINT_10"
        const val POINT_10_DECIMAL = "POINT_10_DECIMAL"
        const val POINT_5 = "POINT_5"
        const val POINT_3 = "POINT_3"
    }
}
