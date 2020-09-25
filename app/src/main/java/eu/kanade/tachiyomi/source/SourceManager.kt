package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.DelegatedHttpSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.english.FoolSlide
import eu.kanade.tachiyomi.source.online.english.KireiCake
import eu.kanade.tachiyomi.source.online.english.MangaPlus
import rx.Observable

open class SourceManager(private val context: Context) {

    private val sourcesMap = mutableMapOf<Long, Source>()

    private val stubSourcesMap = mutableMapOf<Long, StubSource>()

    private val delegatedSources = listOf(
        DelegatedSource(
            "reader.kireicake.com",
            5509224355268673176,
            KireiCake()
        ),
        DelegatedSource(
            "jaiminisbox.com",
            9064882169246918586,
            FoolSlide("jaiminis", "/reader")
        ),
        DelegatedSource(
            "mangadex.org",
            2499283573021220255,
            MangaDex()
        ),
        DelegatedSource(
            "mangaplus.shueisha.co.jp",
            1998944621602463790,
            MangaPlus()
        )
    ).associateBy { it.sourceId }

    init {
        createInternalSources().forEach { registerSource(it) }
    }

    open fun get(sourceKey: Long): Source? {
        return sourcesMap[sourceKey]
    }

    fun getOrStub(sourceKey: Long): Source {
        return sourcesMap[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            StubSource(sourceKey)
        }
    }

    fun getDelegatedSource(urlName: String): DelegatedHttpSource? {
        return delegatedSources.values.find { it.urlName == urlName }?.delegatedHttpSource
    }

    fun getOnlineSources() = sourcesMap.values.filterIsInstance<HttpSource>()

    fun getCatalogueSources() = sourcesMap.values.filterIsInstance<CatalogueSource>()

    internal fun registerSource(source: Source, overwrite: Boolean = false) {
        if (overwrite || !sourcesMap.containsKey(source.id)) {
            delegatedSources[source.id]?.delegatedHttpSource?.delegate = source as? HttpSource
            sourcesMap[source.id] = source
        }
    }

    internal fun unregisterSource(source: Source) {
        sourcesMap.remove(source.id)
    }

    private fun createInternalSources(): List<Source> = listOf(
        LocalSource(context)
    )

    private inner class StubSource(override val id: Long) : Source {

        override val name: String
            get() = id.toString()

        override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun toString(): String {
            return name
        }

        private fun getSourceNotInstalledException(): Exception {
            return SourceNotFoundException(
                context.getString(
                    R.string.source_not_installed_,
                    id.toString()
                ),
                id
            )
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }

    private data class DelegatedSource(
        val urlName: String,
        val sourceId: Long,
        val delegatedHttpSource: DelegatedHttpSource
    )
}

class SourceNotFoundException(message: String, val id: Long) : Exception(message)
