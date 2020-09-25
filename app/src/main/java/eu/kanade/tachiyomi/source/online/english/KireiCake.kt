package eu.kanade.tachiyomi.source.online.english

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.util.lang.capitalizeWords

class KireiCake : FoolSlide("kireicake") {

    override suspend fun getManga(url: String): Manga? {
        val request = GET("${delegate!!.baseUrl}$url")
        val document = network.client.newCall(request).await().asJsoup()
        val mangaDetailsInfoSelector = "div.info"
        return MangaImpl().apply {
            this.url = url
            source = delegate?.id ?: -1
            title = document.select("$mangaDetailsInfoSelector li:has(b:contains(title))").first()
                ?.ownText()?.substringAfter(":")?.trim()
                ?: url.split("/").last().replace("_", " " + "").capitalizeWords()
            description =
                document.select("$mangaDetailsInfoSelector li:has(b:contains(description))").first()
                    ?.ownText()?.substringAfter(":")
            thumbnail_url = document.select("div.thumbnail img").firstOrNull()?.attr("abs:src")
        }
    }
}
