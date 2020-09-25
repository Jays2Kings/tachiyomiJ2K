package eu.kanade.tachiyomi.data.track.anilist

import androidx.core.net.toUri
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.nullInt
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.obj
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.jsonType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AnilistApi(val client: OkHttpClient, interceptor: AnilistInterceptor) {

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibManga(track: Track): Track {
        return withContext(Dispatchers.IO) {

            val variables = jsonObject(
                "mangaId" to track.media_id,
                "progress" to track.last_chapter_read,
                "status" to track.toAnilistStatus()
            )
            val payload = jsonObject(
                "query" to addToLibraryQuery(),
                "variables" to variables
            )
            val body = payload.toString().toRequestBody(MediaType.jsonType())
            val request = Request.Builder().url(apiUrl).post(body).build()

            val netResponse = authClient.newCall(request).execute()

            val responseBody = netResponse.body?.string().orEmpty()
            netResponse.close()
            if (responseBody.isEmpty()) {
                throw Exception("Null Response")
            }
            val response = JsonParser.parseString(responseBody).obj
            track.library_id = response["data"]["SaveMediaListEntry"]["id"].asLong
            track
        }
    }

    suspend fun updateLibraryManga(track: Track): Track {
        return withContext(Dispatchers.IO) {
            val variables = jsonObject(
                "listId" to track.library_id,
                "progress" to track.last_chapter_read,
                "status" to track.toAnilistStatus(),
                "score" to track.score.toInt()
            )
            val payload = jsonObject(
                "query" to updateInLibraryQuery(),
                "variables" to variables
            )
            val body = payload.toString().toRequestBody(MediaType.jsonType())
            val request = Request.Builder().url(apiUrl).post(body).build()
            val response = authClient.newCall(request).execute()

            track
        }
    }

    suspend fun search(search: String): List<TrackSearch> {
        return withContext(Dispatchers.IO) {
            val variables = jsonObject(
                "query" to search
            )
            val payload = jsonObject(
                "query" to searchQuery(),
                "variables" to variables
            )
            val body = payload.toString().toRequestBody(MediaType.jsonType())
            val request = Request.Builder().url(apiUrl).post(body).build()
            val netResponse = authClient.newCall(request).execute()
            val response = responseToJson(netResponse)

            val media = response["data"]!!.obj["Page"].obj["media"].array
            val entries = media.map { jsonToALManga(it.obj) }
            entries.map { it.toTrack() }
        }
    }

    suspend fun findLibManga(track: Track, userid: Int): Track? {

        return withContext(Dispatchers.IO) {
            val variables = jsonObject(
                "id" to userid,
                "manga_id" to track.media_id
            )
            val payload = jsonObject(
                "query" to findLibraryMangaQuery(),
                "variables" to variables
            )
            val body = payload.toString().toRequestBody(MediaType.jsonType())
            val request = Request.Builder().url(apiUrl).post(body).build()
            val result = authClient.newCall(request).execute()

            result.let { resp ->
                val response = responseToJson(resp)
                val media = response["data"]!!.obj["Page"].obj["mediaList"].array
                val entries = media.map { jsonToALUserManga(it.obj) }

                entries.firstOrNull()?.toTrack()
            }
        }
    }

    suspend fun getLibManga(track: Track, userid: Int): Track {
        val remoteTrack = findLibManga(track, userid)
        if (remoteTrack == null) {
            throw Exception("Could not find manga")
        } else {
            return remoteTrack
        }
    }

    suspend fun remove(track: Track): Boolean {
        return withContext(Dispatchers.IO) {
            try {

                val variables = jsonObject(
                    "listId" to track.library_id
                )
                val payload = jsonObject(
                    "query" to deleteFromLibraryQuery(),
                    "variables" to variables
                )

                val body = payload.toString().toRequestBody(MediaType.jsonType())
                val request = Request.Builder().url(apiUrl).post(body).build()
                val result = authClient.newCall(request).execute()
                return@withContext true
            } catch (e: Exception) {
                Timber.w(e)
            }
            return@withContext false
        }
    }

    fun createOAuth(token: String): OAuth {
        return OAuth(
            token,
            "Bearer",
            System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365),
            TimeUnit.DAYS.toMillis(365)
        )
    }

    suspend fun getCurrentUser(): Pair<Int, String> {
        return withContext(Dispatchers.IO) {
            val payload = jsonObject(
                "query" to currentUserQuery()
            )
            val body = payload.toString().toRequestBody(MediaType.jsonType())
            val request = Request.Builder().url(apiUrl).post(body).build()
            val netResponse = authClient.newCall(request).execute()

            val response = responseToJson(netResponse)
            val viewer = response["data"]!!.obj["Viewer"].obj

            Pair(viewer["id"].asInt, viewer["mediaListOptions"]["scoreFormat"].asString)
        }
    }

    private fun responseToJson(netResponse: Response): JsonObject {
        val responseBody = netResponse.body?.string().orEmpty()

        if (responseBody.isEmpty()) {
            throw Exception("Null Response")
        }

        return JsonParser.parseString(responseBody).obj
    }

    private fun jsonToALManga(struct: JsonObject): ALManga {
        val date = try {
            val date = Calendar.getInstance()
            date.set(
                struct["startDate"]["year"].nullInt ?: 0,
                (struct["startDate"]["month"].nullInt ?: 0) - 1,
                struct["startDate"]["day"].nullInt ?: 0
            )
            date.timeInMillis
        } catch (_: Exception) {
            0L
        }

        return ALManga(
            struct["id"].asInt,
            struct["title"]["romaji"].asString,
            struct["coverImage"]["large"].asString,
            struct["description"].nullString.orEmpty(),
            struct["type"].asString,
            struct["status"].nullString.orEmpty(),
            date,
            struct["chapters"].nullInt ?: 0
        )
    }

    private fun jsonToALUserManga(struct: JsonObject): ALUserManga {
        return ALUserManga(
            struct["id"].asLong,
            struct["status"].asString,
            struct["scoreRaw"].asInt,
            struct["progress"].asInt,
            jsonToALManga(struct["media"].obj)
        )
    }

    companion object {
        private const val clientId = "385"
        private const val apiUrl = "https://graphql.anilist.co/"
        private const val baseUrl = "https://anilist.co/api/v2/"
        private const val baseMangaUrl = "https://anilist.co/manga/"

        fun mangaUrl(mediaId: Int): String {
            return baseMangaUrl + mediaId
        }

        fun authUrl() = "${baseUrl.toUri()}oauth/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "token")
            .build()!!

        fun addToLibraryQuery() =
            """
            |mutation AddManga(${'$'}mangaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus) {
                |SaveMediaListEntry (mediaId: ${'$'}mangaId, progress: ${'$'}progress, status: ${'$'}status) { 
                |   id 
                |   status 
                |} 
            |}
            |""".trimMargin()

        fun deleteFromLibraryQuery() =
            """
                |mutation DeleteManga(${'$'}listId: Int) {
                |DeleteMediaListEntry (id: ${'$'}listId) {
                    |deleted

                |}
            |}""".trimMargin()

        fun updateInLibraryQuery() =
            """
            |mutation UpdateManga(${'$'}listId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus, ${'$'}score: Int) {
                |SaveMediaListEntry (id: ${'$'}listId, progress: ${'$'}progress, status: ${'$'}status, scoreRaw: ${'$'}score) {
                    |id
                    |status
                    |progress
                |}
            |}
            |""".trimMargin()

        fun searchQuery() =
            """
            |query Search(${'$'}query: String) {
                |Page (perPage: 50) {
                    |media(search: ${'$'}query, type: MANGA, format_not_in: [NOVEL]) {
                        |id
                        |title {
                            |romaji
                        |}
                        |coverImage {
                            |large
                        |}
                        |type
                        |status
                        |chapters
                        |description
                        |startDate {
                            |year
                            |month
                            |day
                        |}
                    |}
                |}
            |}
            |""".trimMargin()

        fun findLibraryMangaQuery() =
            """
            |query (${'$'}id: Int!, ${'$'}manga_id: Int!) {
                |Page {
                    |mediaList(userId: ${'$'}id, type: MANGA, mediaId: ${'$'}manga_id) {
                        |id
                        |status
                        |scoreRaw: score(format: POINT_100)
                        |progress
                        |media {
                            |id
                            |title {
                                |romaji
                            |}
                            |coverImage {
                                |large
                            |}
                            |type
                            |status
                            |chapters
                            |description
                            |startDate {
                                |year
                                |month
                                |day
                            |}
                        |}
                    |}
                |}
            |}
            |""".trimMargin()

        fun currentUserQuery() =
            """
            |query User {
                |Viewer {
                    |id
                    |mediaListOptions {
                        |scoreFormat
                    |}
                |}
            |}
            |""".trimMargin()
    }
}
