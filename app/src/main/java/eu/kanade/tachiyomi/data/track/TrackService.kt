package eu.kanade.tachiyomi.data.track

import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy

abstract class TrackService(val id: Int) {

    val preferences: PreferencesHelper by injectLazy()
    val networkService: NetworkHelper by injectLazy()
    open fun canRemoveFromService() = false
    open val client: OkHttpClient
        get() = networkService.client

    // Name of the manga sync service to display
    abstract val name: String

    @DrawableRes
    abstract fun getLogo(): Int

    abstract fun getLogoColor(): Int

    abstract fun getStatusList(): List<Int>

    abstract fun isCompletedStatus(index: Int): Boolean

    abstract fun getStatus(status: Int): String

    abstract fun getGlobalStatus(status: Int): String

    abstract fun getScoreList(): List<String>

    open fun indexToScore(index: Int): Float {
        return index.toFloat()
    }

    abstract fun displayScore(track: Track): String

    abstract suspend fun update(track: Track): Track

    abstract suspend fun bind(track: Track): Track

    abstract suspend fun search(query: String): List<TrackSearch>

    abstract suspend fun refresh(track: Track): Track

    abstract suspend fun login(username: String, password: String): Boolean

    open suspend fun removeFromService(track: Track): Boolean = false

    @CallSuper
    open fun logout() {
        preferences.setTrackCredentials(this, "", "")
    }

    open val isLogged: Boolean
        get() = getUsername().isNotEmpty() &&
            getPassword().isNotEmpty()

    fun getUsername() = preferences.trackUsername(this)!!

    fun getPassword() = preferences.trackPassword(this)!!

    fun saveCredentials(username: String, password: String) {
        preferences.setTrackCredentials(this, username, password)
    }
}
