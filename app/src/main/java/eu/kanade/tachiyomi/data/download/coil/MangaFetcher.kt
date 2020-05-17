package eu.kanade.tachiyomi.data.download.coil

import coil.bitmappool.BitmapPool
import coil.decode.DataSource
import coil.decode.Options
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.size.Size
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.storage.DiskUtil
import okhttp3.Call
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File

class MangaFetcher() : Fetcher<Manga> {

    private val coverCache: CoverCache by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val defaultClient = Injekt.get<NetworkHelper>().client

    override fun key(manga: Manga): String? {
        if (manga.thumbnail_url.isNullOrBlank()) return null
        return DiskUtil.hashKeyForDisk(manga.thumbnail_url!!)
    }

    override suspend fun fetch(pool: BitmapPool, manga: Manga, size: Size, options: Options): FetchResult {
        val cover = manga.thumbnail_url
        when (getResourceType(cover)) {
            Type.File -> {
                return fileLoader(manga)
            }
            Type.URL -> {
                return httpLoader(manga)
            }
            Type.CUSTOM -> {
                return customLoader(manga)
            }
            null -> error("Invalid image")
        }
    }

    private fun customLoader(manga: Manga): FetchResult {
        val coverFile = coverCache.getCoverFile(manga)
        if (coverFile.exists()) {
            return fileLoader(coverFile)
        }
        manga.thumbnail_url = manga.thumbnail_url!!.substringAfter("-J2K-").substringAfter("CUSTOM-")
        return httpLoader(manga)
    }

    private fun httpLoader(manga: Manga): FetchResult {
        val coverFile = coverCache.getCoverFile(manga)
        if (coverFile.exists()) {
            return fileLoader(coverFile)
        }
        val call = getCall(manga)
        val tmpFile = File(coverFile.absolutePath + "_tmp")

        val response = call.execute()
        val body = checkNotNull(response.body) { "Null response source" }

        body.source().use { input ->
            tmpFile.sink().buffer().use { output ->
                output.writeAll(input)
            }
        }

        tmpFile.renameTo(coverFile)
        return fileLoader(coverFile)
    }

    private fun fileLoader(manga: Manga): FetchResult {
        return fileLoader(File(manga.thumbnail_url!!.substringAfter("file://")))
    }

    private fun fileLoader(file: File): FetchResult {
        return SourceResult(
            source = file.source().buffer(),
            mimeType = "image/*",
            dataSource = DataSource.DISK
        )
    }

    private fun getCall(manga: Manga): Call {
        val source = sourceManager.get(manga.source) as? HttpSource
        val client = source?.client ?: defaultClient

        val newClient = client.newBuilder()
            .cache(coverCache.cache)
            .build()

        val request = Request.Builder().url(manga.thumbnail_url!!).also {
            if (source != null) {
                it.headers(source.headers)
            }
        }.build()

        return newClient.newCall(request)
    }

    private fun getResourceType(cover: String?): Type? {
        return when {
            cover.isNullOrEmpty() -> null
            cover.startsWith("http") -> Type.URL
            cover.startsWith("Custom-") -> Type.CUSTOM
            cover.startsWith("/") || cover.startsWith("file://") -> Type.File
            else -> null
        }
    }

    private enum class Type {
        File, CUSTOM, URL;
    }
}
