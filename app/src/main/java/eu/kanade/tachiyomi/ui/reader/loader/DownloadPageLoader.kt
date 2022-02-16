package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
import android.net.Uri
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.system.ImageUtil
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.zip.ZipFile

/**
 * Loader used to load a chapter from the downloaded chapters.
 */
class DownloadPageLoader(
    private val chapter: ReaderChapter,
    private val manga: Manga,
    private val source: Source,
    private val downloadManager: DownloadManager,
) : PageLoader() {

    /**
     * The application context. Needed to open input streams.
     */
    private val context by injectLazy<Application>()
    private val downloadProvider by lazy { DownloadProvider(context) }

    /**
     * Returns an observable containing the pages found on this downloaded chapter.
     */
    override fun getPages(): Observable<List<ReaderPage>> {
        val chapterPath = downloadProvider.findChapterDir(chapter.chapter, manga, source)
        if (chapterPath?.isFile == true) {
            val zip = if (!File(chapterPath.filePath!!).canRead()) {
                val tmpFile = File.createTempFile(chapterPath.name!!.replace(".cbz", ""), ".cbz")
                val buffer = ByteArray(1024)
                chapterPath.openInputStream().use { input ->
                    tmpFile.outputStream().use { fileOut ->
                        while (true) {
                            val length = input.read(buffer)
                            if (length <= 0) break
                            fileOut.write(buffer, 0, length)
                        }
                        fileOut.flush()
                    }
                }
                ZipFile(tmpFile.absolutePath)
            } else ZipFile(chapterPath.filePath)
            return zip.entries().toList()
                .filter { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }
                .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                .mapIndexed { i, entry ->
                    val streamFn = { zip.getInputStream(entry) }
                    ReaderPage(i).apply {
                        stream = streamFn
                        status = Page.READY
                    }
                }
                .let { Observable.just(it) }
        } else {
            return downloadManager.buildPageList(source, manga, chapter.chapter)
                .map { pages ->
                    pages.map { page ->
                        ReaderPage(
                            page.index,
                            page.url,
                            page.imageUrl,

                            {
                                context.contentResolver.openInputStream(page.uri ?: Uri.EMPTY)!!
                            },
                        ).apply {
                            status = Page.READY
                        }
                    }
                }
        }
    }

    override fun getPage(page: ReaderPage): Observable<Int> {
        return Observable.just(Page.READY) // TODO maybe check if file still exists?
    }
}
