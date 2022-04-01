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
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AnilistApi(val client: OkHttpClient, interceptor: AnilistInterceptor) {

    private val authClient = client.newBuilder()
        .addInterceptor(interceptor)
        .rateLimit(85, 1, TimeUnit.MINUTES)
        .build()

    suspend fun addLibManga(track: Track): Track {
        return withContext(Dispatchers.IO) {
            val variables = jsonObject(
                "mangaId" to track.media_id,
                "progress" to track.last_chapter_read,
                "status" to track.toAnilistStatus()
            )
            createDate(track.started_reading_date)?.let {
                variables.add("startedAt", it)
            }
            createDate(track.finished_reading_date)?.let {
                variables.add("completedAt", it)
            }
            val payload = jsonObject(
                "query" to addToLibraryQuery(),
                "variables" to variables
            )
            val body = payload.toString().toRequestBody(jsonMime)
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
            createDate(track.started_reading_date)?.let {
                variables.add("startedAt", it)
            }
            createDate(track.finished_reading_date)?.let {
                variables.add("completedAt", it)
            }
            val payload = jsonObject(
                "query" to updateInLibraryQuery(),
                "variables" to variables
            )
            val body = payload.toString().toRequestBody(jsonMime)
            val request = Request.Builder().url(apiUrl).post(body).build()
            val netResponse = authClient.newCall(request).await()
            val response = responseToJson(netResponse)
            try {
                val media = response["data"]["SaveMediaListEntry"].asJsonObject
                val startedDate = parseDate(media, "startedAt")
                if (track.started_reading_date <= 0L || startedDate > 0) {
                    track.started_reading_date = startedDate
                }
                val finishedDate = parseDate(media, "completedAt")
                if (track.finished_reading_date <= 0L || finishedDate > 0) {
                    track.finished_reading_date = finishedDate
                }
            } catch (e: Exception) {
            }
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
            val body = payload.toString().toRequestBody(jsonMime)
            val request = Request.Builder().url(apiUrl).post(body).build()
            val netResponse = authClient.newCall(request).await()
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
            val body = payload.toString().toRequestBody(jsonMime)
            val request = Request.Builder().url(apiUrl).post(body).build()
            val result = authClient.newCall(request).await()

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

                val body = payload.toString().toRequestBody(jsonMime)
                val request = Request.Builder().url(apiUrl).post(body).build()
                authClient.newCall(request).await()
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
            val body = payload.toString().toRequestBody(jsonMime)
            val request = Request.Builder().url(apiUrl).post(body).build()
            val netResponse = authClient.newCall(request).await()

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
        return ALManga(
            struct["id"].asInt,
            struct["title"]["romaji"].asString,
            struct["coverImage"]["large"].asString,
            struct["description"].nullString.orEmpty(),
            struct["type"].asString,
            struct["status"].nullString.orEmpty(),
            parseDate(struct, "startDate"),
            struct["chapters"].nullInt ?: 0
        )
    }

    private fun parseDate(struct: JsonObject, dateKey: String): Long {
        return try {
            val year = struct[dateKey]["year"].nullInt ?: throw Exception()
            val month = struct[dateKey]["month"].nullInt?.minus(1) ?: throw Exception()
            val day = struct[dateKey]["day"].nullInt ?: throw Exception()
            val date = Calendar.getInstance()
            date.set(year, month, day)
            date.timeInMillis
        } catch (_: Exception) {
            0L
        }
    }

    private fun createDate(dateValue: Long): JsonObject? {
        if (dateValue == -1L) return jsonObject(
            "year" to null,
            "month" to null,
            "day" to null,
        )
        if (dateValue == 0L) return null
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dateValue
        return jsonObject(
            "year" to calendar.get(Calendar.YEAR),
            "month" to calendar.get(Calendar.MONTH) + 1,
            "day" to calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun jsonToALUserManga(struct: JsonObject): ALUserManga {
        return ALUserManga(
            struct["id"].asLong,
            struct["status"].asString,
            struct["scoreRaw"].asInt,
            struct["progress"].asInt,
            parseDate(struct, "startedAt"),
            parseDate(struct, "completedAt"),
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
            |mutation AddManga(${'$'}mangaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus, ${'$'}startedAt: FuzzyDateInput, ${'$'}completedAt: FuzzyDateInput) {
                |SaveMediaListEntry (mediaId: ${'$'}mangaId, progress: ${'$'}progress, status: ${'$'}status, startedAt: ${'$'}startedAt, completedAt: ${'$'}completedAt) { 
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
            |mutation UpdateManga(${'$'}listId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus, ${'$'}score: Int, ${'$'}startedAt: FuzzyDateInput, ${'$'}completedAt: FuzzyDateInput) {
                |SaveMediaListEntry (id: ${'$'}listId, progress: ${'$'}progress, status: ${'$'}status, scoreRaw: ${'$'}score, startedAt: ${'$'}startedAt, completedAt: ${'$'}completedAt) {
                    |id
                    |status
                    |progress
                    |startedAt {
                        |year
                        |month
                        |day
                    |}
                    |completedAt {
                        |year
                        |month
                        |day
                    |}
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
                        |startedAt {
                            |year
                            |month
                            |day
                        |}
                        |completedAt {
                            |year
                            |month
                            |day
                        |}
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
