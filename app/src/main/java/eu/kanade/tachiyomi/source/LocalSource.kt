package eu.kanade.tachiyomi.source

import android.content.Context
import com.github.junrar.Archive
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toChapterInfo
import eu.kanade.tachiyomi.source.model.toMangaInfo
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.EpubFile
import eu.kanade.tachiyomi.util.system.ImageUtil
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import rx.Observable
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

class LocalSource(private val context: Context) : CatalogueSource, UnmeteredSource {
    companion object {
        const val ID = 0L
        const val HELP_URL = "https://tachiyomi.org/help/guides/local-manga/"

        private const val COVER_NAME = "cover.jpg"
        private val LATEST_THRESHOLD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)
        private val langMap = hashMapOf<String, String>()

        fun getMangaLang(manga: SManga, context: Context): String {
            return langMap.getOrPut(manga.url) {
                val localDetails = getBaseDirectories(context)
                    .asSequence()
                    .mapNotNull { File(it, manga.url).listFiles()?.toList() }
                    .flatten()
                    .firstOrNull { it.extension.equals("json", ignoreCase = true) }

                return if (localDetails != null) {
                    val obj = Json.decodeFromStream<MangaJson>(localDetails.inputStream())
                    obj.lang ?: "other"
                } else {
                    "other"
                }
            }
        }

        fun updateCover(context: Context, manga: SManga, input: InputStream): File? {
            val dir = getBaseDirectories(context).firstOrNull()
            if (dir == null) {
                input.close()
                return null
            }
            var cover = getCoverFile(File("${dir.absolutePath}/${manga.url}"))
            if (cover == null) {
                cover = File("${dir.absolutePath}/${manga.url}", COVER_NAME)
            }
            // It might not exist if using the external SD card
            cover.parentFile?.mkdirs()
            input.use {
                cover.outputStream().use {
                    input.copyTo(it)
                }
            }
            manga.thumbnail_url = cover.absolutePath
            return cover
        }

        /**
         * Returns valid cover file inside [parent] directory.
         */
        private fun getCoverFile(parent: File): File? {
            return parent.listFiles()?.find { it.nameWithoutExtension == "cover" }?.takeIf {
                it.isFile && ImageUtil.isImage(it.name) { it.inputStream() }
            }
        }

        private fun getBaseDirectories(context: Context): List<File> {
            val c = context.getString(R.string.app_name) + File.separator + "local"
            val oldLibrary = "Tachiyomi" + File.separator + "local"
            return DiskUtil.getExternalStorages(context).map {
                listOf(File(it.absolutePath, c), File(it.absolutePath, oldLibrary))
            }.flatten()
        }
    }

    private val json: Json by injectLazy()

    override val id = ID
    override val name = context.getString(R.string.local_source)
    override val lang = "other"
    override val supportsLatest = true

    override fun toString() = name

    override fun fetchPopularManga(page: Int) = fetchSearchManga(page, "", popularFilters)

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        val baseDirs = getBaseDirectories(context)

        val time =
            if (filters === latestFilters) System.currentTimeMillis() - LATEST_THRESHOLD else 0L
        var mangaDirs = baseDirs
            .asSequence()
            .mapNotNull { it.listFiles()?.toList() }
            .flatten()
            .filter { it.isDirectory }
            .filterNot { it.name.startsWith('.') }
            .filter { if (time == 0L) it.name.contains(query, ignoreCase = true) else it.lastModified() >= time }
            .distinctBy { it.name }

        val state = ((if (filters.isEmpty()) popularFilters else filters)[0] as OrderBy).state
        when (state?.index) {
            0 -> {
                mangaDirs = if (state.ascending) {
                    mangaDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                } else {
                    mangaDirs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
                }
            }
            1 -> {
                mangaDirs = if (state.ascending) {
                    mangaDirs.sortedBy(File::lastModified)
                } else {
                    mangaDirs.sortedByDescending(File::lastModified)
                }
            }
        }

        val mangas = mangaDirs.map { mangaDir ->
            SManga.create().apply {
                title = mangaDir.name
                url = mangaDir.name

                // Try to find the cover
                for (dir in baseDirs) {
                    val cover = getCoverFile(File("${dir.absolutePath}/$url"))
                    if (cover != null && cover.exists()) {
                        thumbnail_url = cover.absolutePath
                        break
                    }
                }

                val sManga = this
                val mangaInfo = this.toMangaInfo()
                runBlocking {
                    val chapters = getChapterList(mangaInfo)
                    if (chapters.isNotEmpty()) {
                        val chapter = chapters.last().toSChapter()
                        val format = getFormat(chapter)
                        if (format is Format.Epub) {
                            EpubFile(format.file).use { epub ->
                                epub.fillMangaMetadata(sManga)
                            }
                        }

                        // Copy the cover from the first chapter found.
                        if (thumbnail_url == null) {
                            try {
                                val dest = updateCover(chapter, sManga)
                                thumbnail_url = dest?.absolutePath
                            } catch (e: Exception) {
                                Timber.e(e)
                            }
                        }
                    }
                }
            }
        }

        return Observable.just(MangasPage(mangas.toList(), false))
    }

    override fun fetchLatestUpdates(page: Int) = fetchSearchManga(page, "", latestFilters)

    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        val localDetails = getBaseDirectories(context)
            .asSequence()
            .mapNotNull { File(it, manga.key).listFiles()?.toList() }
            .flatten()
            .firstOrNull { it.extension.equals("json", ignoreCase = true) }

        return if (localDetails != null) {
            val obj = json.decodeFromStream<MangaJson>(localDetails.inputStream())

            obj.lang?.let { langMap[manga.key] = it }
            manga.copy(
                title = obj.title ?: manga.title,
                author = obj.author ?: manga.author,
                artist = obj.artist ?: manga.artist,
                description = obj.description ?: manga.description,
                genres = obj.genre?.toList() ?: manga.genres,
                status = obj.status ?: manga.status,
            )
        } else {
            manga
        }
    }

    fun updateMangaInfo(manga: SManga, lang: String?) {
        val directory = getBaseDirectories(context).map { File(it, manga.url) }.find {
            it.exists()
        } ?: return
        lang?.let { langMap[manga.url] = it }
        val json = Json { prettyPrint = true }
        val existingFileName = directory.listFiles()?.find { it.extension == "json" }?.name
        val file = File(directory, existingFileName ?: "info.json")
        file.writeText(json.encodeToString(manga.toJson(lang)))
    }

    private fun SManga.toJson(lang: String?): MangaJson {
        return MangaJson(title, author, artist, description, genre?.split(", ")?.toTypedArray(), status, lang)
    }

    @Serializable
    data class MangaJson(
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: Array<String>? = null,
        val status: Int? = null,
        val lang: String? = null,
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MangaJson

            if (title != other.title) return false
            return true
        }

        override fun hashCode(): Int {
            return title.hashCode()
        }
    }

    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        val sManga = manga.toSManga()

        val chapters = getBaseDirectories(context)
            .asSequence()
            .mapNotNull { File(it, manga.key).listFiles()?.toList() }
            .flatten()
            .filter { it.isDirectory || isSupportedFile(it.extension) }
            .map { chapterFile ->
                SChapter.create().apply {
                    url = "${manga.key}/${chapterFile.name}"
                    name = if (chapterFile.isDirectory) {
                        chapterFile.name
                    } else {
                        chapterFile.nameWithoutExtension
                    }
                    date_upload = chapterFile.lastModified()

                    val format = getFormat(chapterFile)
                    if (format is Format.Epub) {
                        EpubFile(format.file).use { epub ->
                            epub.fillChapterMetadata(this)
                        }
                    }

                    ChapterRecognition.parseChapterNumber(this, sManga)
                }
            }
            .map { it.toChapterInfo() }
            .sortedWith { c1, c2 ->
                val c = c2.number.compareTo(c1.number)
                if (c == 0) c2.name.compareToCaseInsensitiveNaturalOrder(c1.name) else c
            }
            .toList()

        return chapters
    }

    override suspend fun getPageList(chapter: ChapterInfo) = throw Exception("Unused")

    private fun isSupportedFile(extension: String): Boolean {
        return extension.lowercase() in SUPPORTED_ARCHIVE_TYPES
    }

    fun getFormat(chapter: SChapter): Format {
        val baseDirs = getBaseDirectories(context)

        for (dir in baseDirs) {
            val chapFile = File(dir, chapter.url)
            if (!chapFile.exists()) continue

            return getFormat(chapFile)
        }
        throw Exception(context.getString(R.string.chapter_not_found))
    }

    private fun getFormat(file: File) = with(file) {
        when {
            isDirectory -> Format.Directory(this)
            extension.equals("zip", true) || extension.equals("cbz", true) -> Format.Zip(this)
            extension.equals("rar", true) || extension.equals("cbr", true) -> Format.Rar(this)
            extension.equals("epub", true) -> Format.Epub(this)
            else -> throw Exception(context.getString(R.string.local_invalid_format))
        }
    }

    private fun updateCover(chapter: SChapter, manga: SManga): File? {
        return when (val format = getFormat(chapter)) {
            is Format.Directory -> {
                val entry = format.file.listFiles()
                    ?.sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                    ?.find { !it.isDirectory && ImageUtil.isImage(it.name) { FileInputStream(it) } }

                entry?.let { updateCover(context, manga, it.inputStream()) }
            }
            is Format.Zip -> {
                ZipFile(format.file).use { zip ->
                    val entry = zip.entries().toList()
                        .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                        .find { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }

                    entry?.let { updateCover(context, manga, zip.getInputStream(it)) }
                }
            }
            is Format.Rar -> {
                Archive(format.file).use { archive ->
                    val entry = archive.fileHeaders
                        .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
                        .find { !it.isDirectory && ImageUtil.isImage(it.fileName) { archive.getInputStream(it) } }

                    entry?.let { updateCover(context, manga, archive.getInputStream(it)) }
                }
            }
            is Format.Epub -> {
                EpubFile(format.file).use { epub ->
                    val entry = epub.getImagesFromPages()
                        .firstOrNull()
                        ?.let { epub.getEntry(it) }

                    entry?.let { updateCover(context, manga, epub.getInputStream(it)) }
                }
            }
        }
    }

    override fun getFilterList() = popularFilters

    private val popularFilters = FilterList(OrderBy(context))
    private val latestFilters = FilterList(OrderBy(context).apply { state = Filter.Sort.Selection(1, false) })

    private class OrderBy(context: Context) : Filter.Sort(
        context.getString(R.string.order_by),
        arrayOf(context.getString(R.string.title), context.getString(R.string.date)),
        Selection(0, true),
    )

    sealed class Format {
        data class Directory(val file: File) : Format()
        data class Zip(val file: File) : Format()
        data class Rar(val file: File) : Format()
        data class Epub(val file: File) : Format()
    }
}

private val SUPPORTED_ARCHIVE_TYPES = listOf("zip", "cbz", "rar", "cbr", "epub")
