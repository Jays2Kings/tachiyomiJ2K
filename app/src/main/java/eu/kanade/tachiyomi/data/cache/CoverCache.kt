package eu.kanade.tachiyomi.data.cache

import android.content.Context
import android.text.format.Formatter
import coil.Coil
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
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
 * Names of files are created with the md5 of the thumbnail URL.
 *
 * @param context the application context.
 * @constructor creates an instance of the cover cache.
 */
class CoverCache(val context: Context) {

    companion object {
        private const val COVERS_DIR = "covers"
        private const val CUSTOM_COVERS_DIR = "covers/custom"
    }

    /** Cache directory used for cache management.*/
    private val cacheDir = getCacheDir(COVERS_DIR)

    /** Cache directory used for custom cover cache management.*/
    private val customCoverCacheDir = getCacheDir(CUSTOM_COVERS_DIR)

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
                if (file.isFile && file.name !in urls) {
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
     * Returns the custom cover from cache.
     *
     * @param manga the manga.
     * @return cover image.
     */
    fun getCustomCoverFile(manga: Manga): File {
        return File(customCoverCacheDir, DiskUtil.hashKeyForDisk(manga.id.toString()))
    }

    /**
     * Saves the given stream as the manga's custom cover to cache.
     *
     * @param manga the manga.
     * @param inputStream the stream to copy.
     * @throws IOException if there's any error.
     */
    @Throws(IOException::class)
    fun setCustomCoverToCache(manga: Manga, inputStream: InputStream) {
        getCustomCoverFile(manga).outputStream().use {
            inputStream.copyTo(it)
            Coil.imageLoader(context).invalidate(manga.key())
        }
    }

    /**
     * Delete custom cover of the manga from the cache
     *
     * @param manga the manga.
     * @return whether the cover was deleted.
     */
    fun deleteCustomCover(manga: Manga): Boolean {
        val result = getCustomCoverFile(manga).let {
            it.exists() && it.delete()
        }
        Coil.imageLoader(context).invalidate(manga.key())
        return result
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

    fun deleteFromCache(name: String?) {
        if (name.isNullOrEmpty()) return
        val file = getCoverFile(MangaImpl().apply { thumbnail_url = name })
        Coil.imageLoader(context).invalidate(file.name)
        if (file.exists()) file.delete()
    }

    /**
     * Delete the cover file from the disk cache and optional from memory cache
     *
     * @param thumbnailUrl the thumbnail url.
     * @return status of deletion.
     */
    fun deleteFromCache(
        manga: Manga,
        deleteCustom: Boolean = true
    ) {
        // Check if url is empty.
        if (manga.thumbnail_url.isNullOrEmpty()) return

        // Remove file
        val file = getCoverFile(manga)
        if (deleteCustom) deleteCustomCover(manga)
        if (file.exists()) file.delete()
    }

    private fun getCacheDir(dir: String): File {
        return context.getExternalFilesDir(dir)
            ?: File(context.filesDir, dir).also { it.mkdirs() }
    }
}
