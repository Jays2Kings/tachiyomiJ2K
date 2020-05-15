package eu.kanade.tachiyomi.data.download.coil

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import coil.Coil
import coil.ImageLoader
import coil.bitmappool.BitmapPool
import coil.decode.DataSource
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.Options
import coil.decode.SvgDecoder
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.map.Mapper
import coil.request.GetRequest
import coil.request.LoadRequest
import coil.size.Size
import coil.transform.CircleCropTransformation
import coil.util.CoilUtils
import coil.util.DebugLogger
import com.bumptech.glide.load.model.FileLoader
import com.chuckerteam.chucker.api.ChuckerInterceptor
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File

class CoilSetup(val context: Context, private val coverCache: CoverCache) {

    init {
        val imageLoader = ImageLoader.Builder(context)
            .availableMemoryPercentage(0.40)
            .crossfade(true)
            .allowRgb565(true)
            .allowHardware(false)
            .logger(DebugLogger())
            .error(R.drawable.ic_broken_image_grey_24dp)
            .componentRegistry {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder())
                } else {
                    add(GifDecoder())
                }
                add(SvgDecoder(context))
                add(MangaFetcher())
            }.okHttpClient {
                OkHttpClient.Builder()
                    .cache(CoilUtils.createDefaultCache(context))
                    .addInterceptor(ChuckerInterceptor(context))
                    .build()
            }
            .build()

        Coil.setImageLoader(imageLoader)
    }
}

class MangaFetcher() : Fetcher<Manga>{

    private val coverCache: CoverCache by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val defaultClient = Injekt.get<NetworkHelper>().client

    override fun key(manga: Manga): String? {
        if(manga.thumbnail_url.isNullOrBlank()) return null
       return DiskUtil.hashKeyForDisk(manga.thumbnail_url!!)
    }

    override suspend fun fetch(pool: BitmapPool, manga: Manga, size: Size, options: Options): FetchResult {
        val cover = manga.thumbnail_url
        when(getResourceType(cover)){
            Type.File  ->{
                return fileLoader(manga)
            }
            Type.URL ->{
                return httpLoader(manga)
            }
            Type.CUSTOM ->{
                return customLoader(manga)
            }
            null -> error("Invalid image")
        }
    }

    private fun customLoader(manga: Manga): FetchResult {
        val cover = manga.thumbnail_url!!
        val coverFile = coverCache.getCoverFile(cover)
        if (coverFile.exists()) {
            return fileLoader(coverFile)
        }
        manga.thumbnail_url = manga.thumbnail_url!!.substringAfter("Custom-")
        return httpLoader(manga)
    }

    private fun httpLoader(manga: Manga): FetchResult {
        val cover = manga.thumbnail_url!!
        val coverFile = coverCache.getCoverFile(cover)
        if(coverFile.exists()){
            return fileLoader(coverFile)
        }
        val call = getCall(manga)
        val tmpFile = File(coverFile.absolutePath + "_tmp")

        val response =  call.execute()
        val body = checkNotNull(response.body) { "Null response source" }

        body.source().use { input ->
            tmpFile.sink().buffer().use { output ->
                output.writeAll(input)
            }
        }

        tmpFile.renameTo(coverFile)
        return fileLoader(coverFile)


    }


    private fun fileLoader(manga:Manga): FetchResult {
        return fileLoader(File(manga.thumbnail_url!!.substringAfter("file://")))
    }
    private fun fileLoader(file: File): FetchResult{
       return SourceResult(
            source = file.source().buffer(),
            mimeType = "image/*",
            dataSource = DataSource.DISK)
    }

    private fun getCall(manga: Manga, useCache: Boolean = true): Call {
        val source = sourceManager.get(manga.source) as? HttpSource
        val client = source?.client ?: defaultClient

        val newClient = client.newBuilder()
            .cache(if (useCache) coverCache.cache else null)
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
