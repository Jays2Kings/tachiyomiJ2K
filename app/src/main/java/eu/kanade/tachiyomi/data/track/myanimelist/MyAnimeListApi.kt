package eu.kanade.tachiyomi.data.track.myanimelist

import android.net.Uri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.consumeBody
import eu.kanade.tachiyomi.network.consumeXmlBody
import eu.kanade.tachiyomi.util.selectInt
import eu.kanade.tachiyomi.util.selectText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import timber.log.Timber

class MyAnimeListApi(private val client: OkHttpClient, interceptor: MyAnimeListInterceptor) {

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun search(query: String): List<TrackSearch> {
        return withContext(Dispatchers.IO) {
            if (query.startsWith(PREFIX_MY)) {
                queryUsersList(query)
            } else {
                val realQuery = query.take(100)
                val response = client.newCall(GET(searchUrl(realQuery))).await()
                val matches = Jsoup.parse(response.consumeBody())
                    .select("div.js-categories-seasonal.js-block-list.list").select("table")
                    .select("tbody").select("tr").drop(1)

                matches.filter { row -> row.select(TD)[2].text() != "Novel" }.map { row ->
                    TrackSearch.create(TrackManager.MYANIMELIST).apply {
                        title = row.searchTitle()
                        media_id = row.searchMediaId()
                        total_chapters = row.searchTotalChapters()
                        summary = row.searchSummary()
                        cover_url = row.searchCoverUrl()
                        tracking_url = mangaUrl(media_id)
                        publishing_status = row.searchPublishingStatus()
                        publishing_type = row.searchPublishingType()
                        start_date = row.searchStartDate()
                    }
                }.toList()
            }
        }
    }

    private suspend fun queryUsersList(query: String): List<TrackSearch> {
        val realQuery = query.removePrefix(PREFIX_MY).take(100)
        return getList().filter { it.title.contains(realQuery, true) }.toList()
    }

    suspend fun addLibManga(track: Track): Track {
        authClient.newCall(POST(url = addUrl(), body = mangaPostPayload(track))).await()
        return track
    }

    suspend fun updateLibManga(track: Track): Track {
        authClient.newCall(POST(url = updateUrl(), body = mangaPostPayload(track))).await()
        return track
    }

    suspend fun remove(track: Track): Boolean {
        try {
            authClient.newCall(POST(url = removeUrl(track.media_id))).await()
            return true
        } catch (e: Exception) {
            Timber.w(e)
        }
        return false
    }

    suspend fun findLibManga(track: Track): Track? {
        return withContext(Dispatchers.IO) {
            val response = authClient.newCall(GET(url = listEntryUrl(track.media_id))).await()
            var remoteTrack: Track? = null
            response.use {
                if (it.priorResponse?.isRedirect != true) {
                    val trackForm = Jsoup.parse(it.consumeBody())

                    remoteTrack = Track.create(TrackManager.MYANIMELIST).apply {
                        last_chapter_read =
                            trackForm.select("#add_manga_num_read_chapters").`val`().toInt()
                        total_chapters = trackForm.select("#totalChap").text().toInt()
                        status =
                            trackForm.select("#add_manga_status > option[selected]").`val`().toInt()
                        score = trackForm.select("#add_manga_score > option[selected]").`val`()
                            .toFloatOrNull() ?: 0f
                    }
                }
            }
            remoteTrack
        }
    }

    suspend fun getLibManga(track: Track): Track {
        val result = findLibManga(track)
        if (result == null) {
            throw Exception("Could not find manga")
        } else {
            return result
        }
    }

    suspend fun login(username: String, password: String): String {
        return withContext(Dispatchers.IO) {
            val csrf = getSessionInfo()
            login(username, password, csrf)
            csrf
        }
    }

    private suspend fun getSessionInfo(): String {
        val response = client.newCall(GET(loginUrl())).execute()

        return Jsoup.parse(response.consumeBody()).select("meta[name=csrf_token]").attr("content")
    }

    private suspend fun login(username: String, password: String, csrf: String) {
        withContext(Dispatchers.IO) {
            val response =
                client.newCall(POST(loginUrl(), body = loginPostBody(username, password, csrf)))
                    .execute()

            response.use {
                if (response.priorResponse?.code != 302) throw Exception("Authentication error")
            }
        }
    }

    private suspend fun getList(): List<TrackSearch> {
        val results = getListXml(getListUrl()).select("manga")

        return results.map {
            TrackSearch.create(TrackManager.MYANIMELIST).apply {
                title = it.selectText("manga_title")!!
                media_id = it.selectInt("manga_mangadb_id")
                last_chapter_read = it.selectInt("my_read_chapters")
                status = getStatus(it.selectText("my_status")!!)
                score = it.selectInt("my_score").toFloat()
                total_chapters = it.selectInt("manga_chapters")
                tracking_url = mangaUrl(media_id)
            }
        }.toList()
    }

    private suspend fun getListUrl(): String {
        return withContext(Dispatchers.IO) {
            val response =
                authClient.newCall(POST(url = exportListUrl(), body = exportPostBody())).execute()

            baseUrl + Jsoup.parse(response.consumeBody()).select("div.goodresult").select("a")
                .attr("href")
        }
    }

    private suspend fun getListXml(url: String): Document {
        val response = authClient.newCall(GET(url)).await()
        return Jsoup.parse(response.consumeXmlBody(), "", Parser.xmlParser())
    }

    companion object {
        const val CSRF = "csrf_token"

        private const val baseUrl = "https://myanimelist.net"
        private const val baseMangaUrl = "$baseUrl/manga/"
        private const val baseModifyListUrl = "$baseUrl/ownlist/manga"
        private const val PREFIX_MY = "my:"
        private const val TD = "td"

        private fun mangaUrl(remoteId: Int) = baseMangaUrl + remoteId

        private fun loginUrl() = Uri.parse(baseUrl).buildUpon().appendPath("login.php").toString()

        private fun searchUrl(query: String): String {
            val col = "c[]"
            return Uri.parse(baseUrl).buildUpon().appendPath("manga.php")
                .appendQueryParameter("q", query).appendQueryParameter(col, "a")
                .appendQueryParameter(col, "b").appendQueryParameter(col, "c")
                .appendQueryParameter(col, "d").appendQueryParameter(col, "e")
                .appendQueryParameter(col, "g").toString()
        }

        private fun exportListUrl() = Uri.parse(baseUrl).buildUpon().appendPath("panel.php")
            .appendQueryParameter("go", "export").toString()

        private fun updateUrl() =
            Uri.parse(baseModifyListUrl).buildUpon().appendPath("edit.json").toString()

        private fun removeUrl(mediaId: Int) = Uri.parse(baseModifyListUrl).buildUpon().appendPath(mediaId.toString())
            .appendPath("delete").toString()

        private fun addUrl() =
            Uri.parse(baseModifyListUrl).buildUpon().appendPath("add.json").toString()

        private fun listEntryUrl(mediaId: Int) =
            Uri.parse(baseModifyListUrl).buildUpon().appendPath(mediaId.toString())
                .appendPath("edit").toString()

        private fun loginPostBody(username: String, password: String, csrf: String): RequestBody {
            return FormBody.Builder().add("user_name", username).add("password", password)
                .add("cookie", "1").add("sublogin", "Login").add("submit", "1").add(CSRF, csrf)
                .build()
        }

        private fun exportPostBody(): RequestBody {
            return FormBody.Builder().add("type", "2").add("subexport", "Export My List").build()
        }

        private fun mangaPostPayload(track: Track): RequestBody {
            val body = JSONObject().put("manga_id", track.media_id).put("status", track.status)
                .put("score", track.score).put("num_read_chapters", track.last_chapter_read)

            return body.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        }

        private fun Element.searchTitle() = select("strong").text()!!

        private fun Element.searchTotalChapters() =
            if (select(TD)[4].text() == "-") 0 else select(TD)[4].text().toInt()

        private fun Element.searchCoverUrl() =
            select("img").attr("data-src").split("\\?")[0].replace("/r/50x70/", "/")

        private fun Element.searchMediaId() =
            select("div.picSurround").select("a").attr("id").replace("sarea", "").toInt()

        private fun Element.searchSummary() = select("div.pt4").first().ownText()!!

        private fun Element.searchPublishingStatus() =
            if (select(TD).last().text() == "-") "Publishing" else "Finished"

        private fun Element.searchPublishingType() = select(TD)[2].text()!!

        private fun Element.searchStartDate() = select(TD)[6].text()!!

        private fun getStatus(status: String) = when (status) {
            "Reading" -> 1
            "Completed" -> 2
            "On-Hold" -> 3
            "Dropped" -> 4
            "Plan to Read" -> 6
            else -> 1
        }
    }
}
