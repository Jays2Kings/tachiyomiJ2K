package eu.kanade.tachiyomi.data.cache

import android.content.Context
import android.text.format.Formatter
import coil.Coil
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.executeOnIO
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Class used to create cover cache.
 * It is used to store the covers of the library.
 * Names of files are created with the md5 of the thumbnail URL.
 *
 * @param context the application context.
 * @constructor creates an instance of the cover cache.
 */
class CoverCache(val context: Context) {

    /**
     * Cache directory used for cache management.
     */
    private val cacheDir = context.getExternalFilesDir("covers")
        ?: File(context.filesDir, "covers").also { it.mkdirs() }

    val cache = Cache(cacheDir, 300 * 1024 * 1024) // 300MB

    fun getChapterCacheSize(): String {
        return Formatter.formatFileSize(context, DiskUtil.getDirectorySize(cacheDir))
    }

    fun deleteOldCovers() {
        GlobalScope.launch(Dispatchers.Default) {
            val db = Injekt.get<DatabaseHelper>()
            var deletedSize = 0L
            val urls = db.getLibraryMangas().executeOnIO().mapNotNull {
                it.thumbnail_url?.let { url -> return@mapNotNull it.key() }
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
    fun getCoverFile(manga: Manga): File {
        return File(cacheDir, manga.key())
    }

    /**
     * Copy the given stream to this cache.
     *
     * @param thumbnailUrl url of the thumbnail.
     * @param inputStream the stream to copy.
     * @throws IOException if there's any error.
     */
    @Throws(IOException::class)
    fun copyToCache(manga: Manga, inputStream: InputStream) {
        // Get destination file.
        val destFile = getCoverFile(manga)

        destFile.outputStream().use { inputStream.copyTo(it) }
    }

    /**
     * Delete the cover file from the disk cache and optional from memory cache
     *
     * @param thumbnailUrl the thumbnail url.
     * @return status of deletion.
     */
    fun deleteFromCache(manga: Manga, deleteMemoryCache: Boolean = true) {
        // Check if url is empty.
        if (manga.thumbnail_url.isNullOrEmpty()) return

        // Remove file
        val file = getCoverFile(manga)
        if (deleteMemoryCache) Coil.imageLoader(context).invalidate(file.name)
        if (file.exists()) file.delete()
    }
}
