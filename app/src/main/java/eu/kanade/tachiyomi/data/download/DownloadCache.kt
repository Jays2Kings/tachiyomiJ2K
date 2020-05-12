package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.storage.DiskUtil
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit

/**
 * Cache where we dump the downloads directory from the filesystem. This class is needed because
 * directory checking is expensive and it slowdowns the app. The cache is invalidated by the time
 * defined in [renewInterval] as we don't have any control over the filesystem and the user can
 * delete the folders at any time without the app noticing.
 *
 * @param context the application context.
 * @param provider the downloads directories provider.
 * @param sourceManager the source manager.
 * @param preferences the preferences of the app.
 */
class DownloadCache(
    private val context: Context,
    private val provider: DownloadProvider,
    private val sourceManager: SourceManager,
    private val preferences: PreferencesHelper = Injekt.get()
) {

    /**
     * The interval after which this cache should be invalidated. 1 hour shouldn't cause major
     * issues, as the cache is only used for UI feedback.
     */
    private val renewInterval = TimeUnit.HOURS.toMillis(1)

    /**
     * The last time the cache was refreshed.
     */
    private var lastRenew = 0L

    private var mangaFiles: MutableMap<Long, MutableSet<String>> = mutableMapOf()

    init {
        preferences.downloadsDirectory().asObservable().skip(1).subscribe {
                lastRenew = 0L // invalidate cache
            }
    }

    /**
     * Returns the downloads directory from the user's preferences.
     */
    private fun getDirectoryFromPreference(): UniFile {
        val dir = preferences.downloadsDirectory().getOrDefault()
        return UniFile.fromUri(context, Uri.parse(dir))
    }

    /**
     * Returns true if the chapter is downloaded.
     *
     * @param chapter the chapter to check.
     * @param manga the manga of the chapter.
     * @param skipCache whether to skip the directory cache and check in the filesystem.
     */
    fun isChapterDownloaded(chapter: Chapter, manga: Manga, skipCache: Boolean): Boolean {
        if (skipCache) {
            val source = sourceManager.get(manga.source) ?: return false
            return provider.findChapterDir(chapter, manga, source) != null
        }

        checkRenew()

        val files = mangaFiles[manga.id]?.toSet() ?: return false
        return files.any { file ->
            provider.getValidChapterDirNames(chapter).any {
                it.toLowerCase() == file.toLowerCase()
            }
        }
    }

    /**
     * Returns the amount of downloaded chapters for a manga.
     *
     * @param manga the manga to check.
     */
    fun getDownloadCount(manga: Manga): Int {
        checkRenew()

        val files = mangaFiles[manga.id] ?: return 0
        return files.filter { !it.endsWith(Downloader.TMP_DIR_SUFFIX) }.size
    }

    /**
     * Checks if the cache needs a renewal and performs it if needed.
     */
    @Synchronized
    private fun checkRenew() {
        if (lastRenew + renewInterval < System.currentTimeMillis()) {
            renew()
            lastRenew = System.currentTimeMillis()
        }
    }

    /**
     * Renews the downloads cache.
     */
    private fun renew() {
        val onlineSources = sourceManager.getOnlineSources()

        val sourceDirs = getDirectoryFromPreference().listFiles().orEmpty()
            .associate { it.name to SourceDirectory(it) }.mapNotNullKeys { entry ->
                onlineSources.find { provider.getSourceDirName(it) == entry.key }?.id
            }

        val db: DatabaseHelper by injectLazy()
        val mangas = db.getMangas().executeAsBlocking().groupBy { it.source }

        sourceDirs.forEach { sourceValue ->
            val sourceMangasRaw = mangas[sourceValue.key]?.toMutableSet() ?: return@forEach
            val sourceMangas = arrayOf(sourceMangasRaw.filter { it.favorite },
                sourceMangasRaw.filterNot { it.favorite })
            val sourceDir = sourceValue.value
            val mangaDirs = sourceDir.dir.listFiles().orEmpty().mapNotNull {
                    val name = it.name ?: return@mapNotNull null
                    name to MangaDirectory(it)
                }.toMap()

            mangaDirs.values.forEach { mangaDir ->
                val chapterDirs =
                    mangaDir.dir.listFiles().orEmpty().mapNotNull { it.name }.toHashSet()
                mangaDir.files = chapterDirs
            }
            val trueMangaDirs = mangaDirs.mapNotNull { mangaDir ->
                val manga = sourceMangas.firstOrNull()?.find {
                    DiskUtil.buildValidFilename(
                        it.title
                    ).toLowerCase() == mangaDir.key.toLowerCase() && it.source == sourceValue.key
                } ?: sourceMangas.lastOrNull()?.find {
                    DiskUtil.buildValidFilename(
                        it.title
                    ).toLowerCase() == mangaDir.key.toLowerCase() && it.source == sourceValue.key
                }
                val id = manga?.id ?: return@mapNotNull null
                id to mangaDir.value.files
            }.toMap()

            mangaFiles.putAll(trueMangaDirs)
        }
    }

    /**
     * Adds a chapter that has just been download to this cache.
     *
     * @param chapterDirName the downloaded chapter's directory name.
     * @param mangaUniFile the directory of the manga.
     * @param manga the manga of the chapter.
     */
    @Synchronized
    fun addChapter(chapterDirName: String, mangaUniFile: UniFile, manga: Manga) {

        val id = manga.id ?: return
        val files = mangaFiles[id]
        if (files == null) {
            mangaFiles[id] = mutableSetOf(chapterDirName)
        } else {
            mangaFiles[id]?.add(chapterDirName)
        }
    }

    /**
     * Removes a list of chapters that have been deleted from this cache.
     *
     * @param chapters the list of chapter to remove.
     * @param manga the manga of the chapter.
     */
    @Synchronized
    fun removeChapters(chapters: List<Chapter>, manga: Manga) {
        val id = manga.id ?: return
        for (chapter in chapters) {
            val list = provider.getValidChapterDirNames(chapter)
            list.forEach {
                if (mangaFiles[id] != null && it in mangaFiles[id]!!) {
                    mangaFiles[id]?.remove(it)
                }
            }
        }
    }

    fun removeFolders(folders: List<String>, manga: Manga) {
        val id = manga.id ?: return
        for (chapter in folders) {
            if (mangaFiles[id] != null && chapter in mangaFiles[id]!!) {
                mangaFiles[id]?.remove(chapter)
            }
        }
    }

    /*fun renameFolder(from: String, to: String, source: Long) {
        val sourceDir = rootDir.files[source] ?: return
        val list = sourceDir.files.toMutableMap()
        val mangaFiles = sourceDir.files[DiskUtil.buildValidFilename(from)] ?: return
        val newFile = UniFile.fromFile(File(sourceDir.dir.filePath + "/" + DiskUtil
            .buildValidFilename(to))) ?: return
        val newDir = MangaDirectory(newFile)
        newDir.files = mangaFiles.files
        list.remove(DiskUtil.buildValidFilename(from))
        list[to] = newDir
        sourceDir.files = list
    }*/

    /**
     * Removes a manga that has been deleted from this cache.
     *
     * @param manga the manga to remove.
     */
    @Synchronized
    fun removeManga(manga: Manga) {
        mangaFiles.remove(manga.id)
    }

    /**
     * Class to store the files under the root downloads directory.
     */
    private class RootDirectory(
        val dir: UniFile,
        var files: Map<Long, SourceDirectory> = hashMapOf()
    )

    /**
     * Class to store the files under a source directory.
     */
    private class SourceDirectory(
        val dir: UniFile,
        var files: Map<Long, MutableSet<String>> = hashMapOf()
    )

    /**
     * Class to store the files under a manga directory.
     */
    private class MangaDirectory(
        val dir: UniFile,
        var files: MutableSet<String> = hashSetOf()
    )

    /**
     * Returns a new map containing only the key entries of [transform] that are not null.
     */
    private inline fun <K, V, R> Map<out K, V>.mapNotNullKeys(transform: (Map.Entry<K?, V>) -> R?): Map<R, V> {
        val destination = LinkedHashMap<R, V>()
        forEach { element -> transform(element)?.let { destination.put(it, element.value) } }
        return destination
    }

    /**
     * Returns a map from a list containing only the key entries of [transform] that are not null.
     */
    private inline fun <T, K, V> Array<T>.associateNotNullKeys(transform: (T) -> Pair<K?, V>): Map<K, V> {
        val destination = LinkedHashMap<K, V>()
        for (element in this) {
            val (key, value) = transform(element)
            if (key != null) {
                destination[key] = value
            }
        }
        return destination
    }
}
