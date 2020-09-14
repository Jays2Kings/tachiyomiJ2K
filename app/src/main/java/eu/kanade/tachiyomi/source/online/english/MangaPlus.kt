package eu.kanade.tachiyomi.source.online.english

import android.net.Uri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.DelegatedHttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaPlus : DelegatedHttpSource() {
    override val domainName: String = "jumpg-webapi.tokyo-cdn"

    private val titleIdRegex =
        Regex("https:\\/\\/mangaplus\\.shueisha\\.co\\.jp\\/drm\\/title\\/\\d*")
    private val titleRegex = Regex("#MANGA_Plus .*\u0012")

    private val chapterUrlTemplate =
        "https://jumpg-webapi.tokyo-cdn.com/api/manga_viewer?chapter_id=##&split=no&img_quality=low"

    override fun canOpenUrl(uri: Uri): Boolean = true

    override fun chapterUrl(uri: Uri): String? = "#/viewer/${uri.pathSegments[1]}"

    override fun pageNumber(uri: Uri): Int? = null

    override suspend fun fetchMangaFromChapterUrl(uri: Uri): Triple<Chapter, Manga, List<SChapter>>? {
        val url = chapterUrl(uri) ?: return null
        val request = GET(
            chapterUrlTemplate.replace("##", uri.pathSegments[1]),
            delegate!!.headers,
            CacheControl.FORCE_NETWORK
        )
        return withContext(Dispatchers.IO) {
            val response = network.client.newCall(request).await()
            if (response.code != 200) throw Exception("HTTP error ${response.code}")
            val body = response.body!!.string()
            val match = titleIdRegex.find(body)
            val titleId = match?.groupValues?.firstOrNull()?.substringAfterLast("/")
                ?: error("Title not found")
            val title = titleRegex.find(body)?.groups?.firstOrNull()?.value?.substringAfter("Plus ")
                ?: error("Title not found")
            val trimmedTitle = title.substring(0, title.length - 1)
            val mangaUrl = "#/titles/$titleId"
            val deferredManga = async {
                db.getManga(mangaUrl, delegate?.id!!).executeAsBlocking() ?: getMangaInfo(mangaUrl)
            }
            val deferredChapters = async { getChapters(mangaUrl) }
            val manga = deferredManga.await()
            val chapters = deferredChapters.await()
            val context = Injekt.get<PreferencesHelper>().context
            val trueChapter = chapters?.find { it.url == url }?.toChapter() ?: error(
                context.getString(R.string.chapter_not_found)
            )
            if (manga != null) {
                Triple(
                    trueChapter,
                    manga.apply {
                        this.title = trimmedTitle
                    },
                    chapters.orEmpty()
                )
            } else null
        }
    }
}
