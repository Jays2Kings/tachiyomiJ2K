package eu.kanade.tachiyomi.data.download.coil

import coil.bitmappool.BitmapPool
import coil.decode.DataSource
import coil.decode.Options
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.size.Size
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream

class ByteArrayFetcher : Fetcher<ByteArray> {

    override fun key(data: ByteArray): String? = null

    override suspend fun fetch(
        pool: BitmapPool,
        data: ByteArray,
        size: Size,
        options: Options
    ): FetchResult {
        val source = ByteArrayInputStream(data).source().buffer()
        return SourceResult(
            source = ByteArrayInputStream(data).source().buffer(),
            mimeType = "image/gif",
            dataSource = DataSource.MEMORY
        )
    }
}
