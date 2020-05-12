package eu.kanade.tachiyomi.data.cache

import android.content.Context
import android.text.format.Formatter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.executeOnIO
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Class used to create cover cache.
 * It is used to store the covers of the library.
 * Makes use of Glide (which can avoid repeating requests) to download covers.
 * Names of files are created with the md5 of the thumbnail URL.
 *
 * @param context the application context.
 * @constructor creates an instance of the cover cache.
 */
class CoverCache(private val context: Context) {

    /**
     * Cache directory used for cache management.
     */
    private val cacheDir = context.getExternalFilesDir("covers")
            ?: File(context.filesDir, "covers").also { it.mkdirs() }

    fun deleteOldCovers() {
        GlobalScope.launch(Dispatchers.Default) {
            val db = Injekt.get<DatabaseHelper>()
            var deletedSize = 0L
            val urls = db.getLibraryMangas().executeOnIO().mapNotNull {
                it.thumbnail_url?.let { url ->
                    return@mapNotNull DiskUtil.hashKeyForDisk(url)
                }
                null
            }
            val files = cacheDir.listFiles()?.iterator() ?: return@launch
            while (files.hasNext()) {
                val file = files.next()
                if (file.name !in urls) {
                    deletedSize += file.length()
                    file.delete()
                }
            }
            withContext(Dispatchers.Main) {
                context.toast(
                    context.getString(
                        R.string.deleted_, Formatter.formatFileSize(context, deletedSize)
                    )
                )
            }
        }
    }

    /**
     * Returns the cover from cache.
     *
     * @param thumbnailUrl the thumbnail url.
     * @return cover image.
     */
    fun getCoverFile(thumbnailUrl: String): File {
        return File(cacheDir, DiskUtil.hashKeyForDisk(thumbnailUrl))
    }

    /**
     * Copy the given stream to this cache.
     *
     * @param thumbnailUrl url of the thumbnail.
     * @param inputStream the stream to copy.
     * @throws IOException if there's any error.
     */
    @Throws(IOException::class)
    fun copyToCache(thumbnailUrl: String, inputStream: InputStream) {
        // Get destination file.
        val destFile = getCoverFile(thumbnailUrl)

        destFile.outputStream().use { inputStream.copyTo(it) }
    }

    /**
     * Delete the cover file from the cache.
     *
     * @param thumbnailUrl the thumbnail url.
     * @return status of deletion.
     */
    fun deleteFromCache(thumbnailUrl: String?): Boolean {
        // Check if url is empty.
        if (thumbnailUrl.isNullOrEmpty())
            return false

        // Remove file.
        val file = getCoverFile(thumbnailUrl)
        return file.exists() && file.delete()
    }
}
