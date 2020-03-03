package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.storage.DiskUtil
import uy.kohesive.injekt.injectLazy

/**
 * This class is used to provide the directories where the downloads should be saved.
 * It uses the following path scheme: /<root downloads dir>/<source name>/<manga>/<chapter>
 *
 * @param context the application context.
 */
class DownloadProvider(private val context: Context) {

    /**
     * Preferences helper.
     */
    private val preferences: PreferencesHelper by injectLazy()

    /**
     * The root directory for downloads.
     */
    private var downloadsDir = preferences.downloadsDirectory().getOrDefault().let {
        val dir = UniFile.fromUri(context, Uri.parse(it))
        DiskUtil.createNoMediaFile(dir, context)
        dir
    }

    init {
        preferences.downloadsDirectory().asObservable()
                .skip(1)
                .subscribe { downloadsDir = UniFile.fromUri(context, Uri.parse(it)) }
    }

    /**
     * Returns the download directory for a manga. For internal use only.
     *
     * @param manga the manga to query.
     * @param source the source of the manga.
     */
    internal fun getMangaDir(manga: Manga, source: Source): UniFile {
        try {
            return downloadsDir
                    .createDirectory(getSourceDirName(source))
                    .createDirectory(getMangaDirName(manga))
        } catch (e: NullPointerException) {
            throw Exception(context.getString(R.string.invalid_download_dir))
        }
    }

    /**
     * Returns the download directory for a source if it exists.
     *
     * @param source the source to query.
     */
    fun findSourceDir(source: Source): UniFile? {
        return downloadsDir.findFile(getSourceDirName(source))
    }

    /**
     * Returns the download directory for a manga if it exists.
     *
     * @param manga the manga to query.
     * @param source the source of the manga.
     */
    fun findMangaDir(manga: Manga, source: Source): UniFile? {
        val sourceDir = findSourceDir(source)
        return sourceDir?.findFile(getMangaDirName(manga))
    }

    /**
     * Returns the download directory for a chapter if it exists.
     *
     * @param chapter the chapter to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    fun findChapterDir(chapter: Chapter, manga: Manga, source: Source): UniFile? {
        val mangaDir = findMangaDir(manga, source)
        var chapterPath = getValidChapterDirNames(chapter).mapNotNull { mangaDir?.findFile(it) }.firstOrNull()
        if (chapterPath==null){
            chapterPath = getValidChapterDirNames(chapter).mapNotNull { mangaDir?.findFile(it+".cbz") }.firstOrNull()
        }
        return chapterPath
    }

    /**
     * Returns a list of downloaded directories for the chapters that exist.
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    fun findChapterDirs(chapters: List<Chapter>, manga: Manga, source: Source): List<UniFile> {
        val mangaDir = findMangaDir(manga, source) ?: return emptyList()
        var chaptersPath =chapters.mapNotNull { chp ->
            var chapterPath = getValidChapterDirNames(chp).mapNotNull { mangaDir.findFile(it) }.firstOrNull()
            if (chapterPath==null){
                chapterPath = getValidChapterDirNames(chp).mapNotNull { mangaDir.findFile(it+".cbz") }.firstOrNull()
            }
            chapterPath
        }
                return chaptersPath
    }

    /**
     * Renames the chapter folders with id's and removes it + null scanlators
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    fun renameChaapters() {
        val db by injectLazy<DatabaseHelper>()
        val sourceManager by injectLazy<SourceManager>()
        val mangas = db.getLibraryMangas().executeAsBlocking()
        mangas.forEach sfor@{ manga ->
            val sourceId = manga.source
            val source = sourceManager.get(sourceId) ?: return@sfor
            val mangaDir = findMangaDir(manga, source) ?: return@sfor
            mangaDir.listFiles()?.forEach {
                val nameSplit = it.name?.split("_")?.toMutableList() ?: return@sfor
                if (nameSplit.size > 2 && nameSplit.first().first().isDigit()) {
                    nameSplit.removeAt(0)
                    val newName = nameSplit.joinToString("_").removePrefix("null_")
                    it.renameTo(newName)
                }
            }
        }
    }

    fun renameMangaFolder(from: String, to: String, sourceId: Long) {
        val sourceManager by injectLazy<SourceManager>()
        val source = sourceManager.get(sourceId) ?: return
        val sourceDir = findSourceDir(source)
        val mangaDir = sourceDir?.findFile(DiskUtil.buildValidFilename(from))
        mangaDir?.renameTo(to)
       // val downloadManager:DownloadManager by injectLazy()
       // downloadManager.renameCache(from, to, sourceId)
    }

    /**
     * Returns a list of all files in manga directory
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    fun findUnmatchedChapterDirs(chapters: List<Chapter>, manga: Manga, source: Source): List<UniFile> {
        val mangaDir = findMangaDir(manga, source) ?: return emptyList()
        return mangaDir.listFiles()!!.asList().filter {
            (chapters.find { chp ->
                getValidChapterDirNames(chp).any { dir ->
                    mangaDir.findFile(dir) != null
                }
            } == null) || it.name?.endsWith("_tmp") == true
        }
    }

    /**
     * Returns a list of downloaded directories for the chapters that exist.
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    fun findTempChapterDirs(chapters: List<Chapter>, manga: Manga, source: Source): List<UniFile> {
        val mangaDir = findMangaDir(manga, source) ?: return emptyList()
        return chapters.mapNotNull { mangaDir.findFile("${getChapterDirName(it)}_tmp") }
    }


    /**
     * Returns the download directory name for a source.
     *
     * @param source the source to query.
     */
    fun getSourceDirName(source: Source): String {
        return source.toString()
    }

    /**
     * Returns the download directory name for a manga.
     *
     * @param manga the manga to query.
     */
    fun getMangaDirName(manga: Manga): String {
        return DiskUtil.buildValidFilename(manga.originalTitle())
    }

    /**
     * Returns the chapter directory name for a chapter.
     *
     * @param chapter the chapter to query.
     */
    fun getChapterDirName(chapter: Chapter): String {
        return DiskUtil.buildValidFilename(
            if (chapter.scanlator != null) "${chapter.scanlator}_${chapter.name}"
            else chapter.name
        )
    }

    /**
     * Returns valid downloaded chapter directory names.
     *
     * @param chapter the chapter to query.
     */
    fun getValidChapterDirNames(chapter: Chapter): List<String> {
        return listOf(
            getChapterDirName(chapter),
            // Legacy chapter directory name used in v0.8.4 and before
            DiskUtil.buildValidFilename(chapter.name)
        )
    }

}
