package eu.kanade.tachiyomi.source.online.english

import android.net.Uri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.DelegatedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

open class FoolSlide(override val domainName: String, private val urlModifier: String = "") :
    DelegatedHttpSource
    () {

    override fun canOpenUrl(uri: Uri): Boolean = true

    override fun chapterUrl(uri: Uri): String? {
        val offset = if (urlModifier.isEmpty()) 0 else 1
        val mangaName = uri.pathSegments.getOrNull(1 + offset) ?: return null
        val lang = uri.pathSegments.getOrNull(2 + offset) ?: return null
        val volume = uri.pathSegments.getOrNull(3 + offset) ?: return null
        val chapterNumber = uri.pathSegments.getOrNull(4 + offset) ?: return null
        val subChapterNumber = uri.pathSegments.getOrNull(5 + offset)?.toIntOrNull()?.toString()
        return "$urlModifier/read/" + listOfNotNull(
            mangaName,
            lang,
            volume,
            chapterNumber,
            subChapterNumber
        ).joinToString("/") + "/"
    }

    override fun pageNumber(uri: Uri): Int? {
        val count = uri.pathSegments.count()
        if (count > 2 && uri.pathSegments[count - 2] == "page") {
            return super.pageNumber(uri)
        }
        return null
    }

    override suspend fun fetchMangaFromChapterUrl(uri: Uri): Triple<Chapter, Manga, List<SChapter>>? {
        val offset = if (urlModifier.isEmpty()) 0 else 1
        val mangaName = uri.pathSegments.getOrNull(1 + offset) ?: return null
        var chapterNumber = uri.pathSegments.getOrNull(4 + offset) ?: return null
        val subChapterNumber = uri.pathSegments.getOrNull(5 + offset)?.toIntOrNull()
        if (subChapterNumber != null) {
            chapterNumber += ".$subChapterNumber"
        }
        return withContext(Dispatchers.IO) {
            val mangaUrl = "$urlModifier/series/$mangaName/"
            val sourceId = delegate?.id ?: return@withContext null
            val dbManga = db.getManga(mangaUrl, sourceId).executeAsBlocking()
            val deferredManga = async {
                dbManga ?: getManga(mangaUrl)
            }
            val chapterUrl = chapterUrl(uri)
            val deferredChapters = async { getChapters(mangaUrl) }
            val manga = deferredManga.await()
            val chapters = deferredChapters.await()
            val context = Injekt.get<PreferencesHelper>().context
            val trueChapter = chapters?.find { it.url == chapterUrl }?.toChapter() ?: error(
                context.getString(R.string.chapter_not_found)
            )
            if (manga != null) Triple(trueChapter, manga, chapters) else null
        }
    }

    open suspend fun getManga(url: String): Manga? {
        val request = GET("${delegate!!.baseUrl}$url")
        val document = network.client.newCall(allowAdult(request)).await().asJsoup()
        val mangaDetailsInfoSelector = "div.info"
        val infoElement = document.select(mangaDetailsInfoSelector).first().text()
        return MangaImpl().apply {
            this.url = url
            source = delegate?.id ?: -1
            title = infoElement.substringAfter("Title:").substringBefore("Author:").trim()
            author = infoElement.substringAfter("Author:").substringBefore("Artist:").trim()
            artist = infoElement.substringAfter("Artist:").substringBefore("Synopsis:").trim()
            description = infoElement.substringAfter("Synopsis:").trim()
            thumbnail_url = document.select("div.thumbnail img").firstOrNull()?.attr("abs:src")?.trim()
        }
    }

    /**
     * Transform a GET request into a POST request that automatically authorizes all adult content
     */
    private fun allowAdult(request: Request) = allowAdult(request.url.toString())

    private fun allowAdult(url: String): Request {
        return POST(
            url,
            body = FormBody.Builder()
                .add("adult", "true")
                .build()
        )
    }
}
