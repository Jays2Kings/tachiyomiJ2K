package eu.kanade.tachiyomi.data.track.bangumi

import android.net.Uri
import androidx.core.net.toUri
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.obj
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder

class BangumiApi(private val client: OkHttpClient, interceptor: BangumiInterceptor) {

    private val gson: Gson by injectLazy()
    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibManga(track: Track): Track {
        val body = FormBody.Builder().add("rating", track.score.toInt().toString())
            .add("status", track.toBangumiStatus()).build()
        val request =
            Request.Builder().url("$apiUrl/collection/${track.media_id}/update").post(body).build()
        val response = authClient.newCall(request).await()
        return track
    }

    suspend fun updateLibManga(track: Track): Track {
        // chapter update
        return withContext(Dispatchers.IO) {
            val body =
                FormBody.Builder().add("watched_eps", track.last_chapter_read.toString()).build()
            val request =
                Request.Builder().url("$apiUrl/subject/${track.media_id}/update/watched_eps")
                    .post(body).build()

            // read status update
            val sbody = FormBody.Builder().add("status", track.toBangumiStatus()).build()
            val srequest =
                Request.Builder().url("$apiUrl/collection/${track.media_id}/update").post(sbody)
                    .build()
            authClient.newCall(srequest).execute()
            authClient.newCall(request).execute()
            track
        }
    }

    suspend fun search(search: String): List<TrackSearch> {
        return withContext(Dispatchers.IO) {
            val url = "$apiUrl/search/subject/${URLEncoder.encode(search, Charsets.UTF_8.name())}"
                .toUri().buildUpon().appendQueryParameter("max_results", "20").build()
            val request = Request.Builder().url(url.toString()).get().build()

            val netResponse = authClient.newCall(request).await()
            var responseBody = netResponse.body?.string().orEmpty()
            if (responseBody.isEmpty()) {
                throw Exception("Null Response")
            }
            if (responseBody.contains("\"code\":404")) {
                responseBody = "{\"results\":0,\"list\":[]}"
            }
            val response = JsonParser.parseString(responseBody).obj["list"]?.array
            if (response != null) {
                response.filter { it.obj["type"].asInt == 1 }?.map { jsonToSearch(it.obj) }
            } else {
                listOf()
            }
        }
    }

    private fun jsonToSearch(obj: JsonObject): TrackSearch {
        return TrackSearch.create(TrackManager.BANGUMI).apply {
            media_id = obj["id"].asInt
            title = obj["name_cn"].asString
            cover_url = obj["images"].obj["common"].asString
            summary = obj["name"].asString
            tracking_url = obj["url"].asString
        }
    }

    private fun jsonToTrack(mangas: JsonObject): Track {
        return Track.create(TrackManager.BANGUMI).apply {
            title = mangas["name"].asString
            media_id = mangas["id"].asInt
            score =
                if (mangas["rating"] != null) (if (mangas["rating"].isJsonObject) mangas["rating"].obj["score"].asFloat else 0f)
                else 0f
            status = Bangumi.DEFAULT_STATUS
            tracking_url = mangas["url"].asString
        }
    }

    suspend fun findLibManga(track: Track): Track? {
        return withContext(Dispatchers.IO) {
            val urlMangas = "$apiUrl/subject/${track.media_id}"
            val requestMangas = Request.Builder().url(urlMangas).get().build()
            val netResponse = authClient.newCall(requestMangas).execute()
            val responseBody = netResponse.body?.string().orEmpty()
            jsonToTrack(JsonParser.parseString(responseBody).obj)
        }
    }

    suspend fun statusLibManga(track: Track): Track? {
        val urlUserRead = "$apiUrl/collection/${track.media_id}"
        val requestUserRead =
            Request.Builder().url(urlUserRead).cacheControl(CacheControl.FORCE_NETWORK).get()
                .build()

        // todo get user readed chapter here
        val response = authClient.newCall(requestUserRead).await()
        val resp = response.body?.toString()
        val coll = gson.fromJson(resp, Collection::class.java)
        track.status = coll.status?.id!!
        track.last_chapter_read = coll.ep_status!!
        return track
    }

    suspend fun accessToken(code: String): OAuth {
        return withContext(Dispatchers.IO) {
            val netResponse = client.newCall(accessTokenRequest(code)).execute()
            val responseBody = netResponse.body?.string().orEmpty()
            if (responseBody.isEmpty()) {
                throw Exception("Null Response")
            }
            gson.fromJson(responseBody, OAuth::class.java)
        }
    }

    private fun accessTokenRequest(code: String) = POST(
        oauthUrl,
        body = FormBody.Builder().add("grant_type", "authorization_code").add("client_id", clientId)
            .add("client_secret", clientSecret).add("code", code).add("redirect_uri", redirectUrl)
            .build()
    )

    companion object {
        private const val clientId = "bgm10555cda0762e80ca"
        private const val clientSecret = "8fff394a8627b4c388cbf349ec865775"

        private const val baseUrl = "https://bangumi.org"
        private const val apiUrl = "https://api.bgm.tv"
        private const val oauthUrl = "https://bgm.tv/oauth/access_token"
        private const val loginUrl = "https://bgm.tv/oauth/authorize"

        private const val redirectUrl = "tachiyomi://bangumi-auth"
        private const val baseMangaUrl = "$apiUrl/mangas"

        fun mangaUrl(remoteId: Int): String {
            return "$baseMangaUrl/$remoteId"
        }

        fun authUrl() = loginUrl.toUri().buildUpon().appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUrl).build()

        fun refreshTokenRequest(token: String) = POST(
            oauthUrl,
            body = FormBody.Builder().add("grant_type", "refresh_token").add("client_id", clientId)
                .add("client_secret", clientSecret).add("refresh_token", token)
                .add("redirect_uri", redirectUrl).build()
        )
    }
}
