package eu.kanade.tachiyomi.data.download.coil

import android.content.Context
import android.os.Build
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.util.CoilUtils
import com.chuckerteam.chucker.api.ChuckerInterceptor
import eu.kanade.tachiyomi.R
import okhttp3.OkHttpClient

class CoilSetup(context: Context) {
    init {
        val imageLoader = ImageLoader.Builder(context)
            .availableMemoryPercentage(0.40)
            .crossfade(true)
            .allowRgb565(true)
            .allowHardware(false)
            .error(R.drawable.ic_broken_image_grey_24dp)
            .componentRegistry {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder())
                } else {
                    add(GifDecoder())
                }
                add(SvgDecoder(context))
                add(MangaFetcher())
                add(ByteArrayFetcher())
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
