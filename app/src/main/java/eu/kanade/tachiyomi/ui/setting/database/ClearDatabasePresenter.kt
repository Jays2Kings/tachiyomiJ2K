package eu.kanade.tachiyomi.ui.setting.database

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.withUIContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ClearDatabasePresenter : BaseCoroutinePresenter<ClearDatabaseController>() {

    private val db = Injekt.get<DatabaseHelper>()

    private val sourceManager = Injekt.get<SourceManager>()

    var sortBy = SortSources.ALPHA
        private set

    var hasStubSources = false

    enum class SortSources {
        ALPHA,
        MOST_ENTRIES,
    }

    override fun onCreate() {
        super.onCreate()
        getDatabaseSources()
    }

    fun clearDatabaseForSourceIds(sources: List<Long>, keepReadManga: Boolean) {
        if (keepReadManga) {
            db.deleteMangasNotInLibraryAndNotReadBySourceIds(sources).executeAsBlocking()
        } else {
            db.deleteMangasNotInLibraryBySourceIds(sources).executeAsBlocking()
        }
        db.deleteHistoryNoLastRead().executeAsBlocking()
        getDatabaseSources()
    }

    fun reorder(sortBy: SortSources) {
        this.sortBy = sortBy
        getDatabaseSources()
    }

    private fun getDatabaseSources() {
        presenterScope.launchUI {
            hasStubSources = false
            val sources = db.getSourceIdsWithNonLibraryManga().executeAsBlocking()
                .map {
                    val sourceObj = sourceManager.getOrStub(it.source)
                    hasStubSources = sourceObj is SourceManager.StubSource || hasStubSources
                    ClearDatabaseSourceItem(sourceObj, it.count)
                }
                .sortedWith(
                    compareBy(
                        {
                            when (sortBy) {
                                SortSources.ALPHA -> it.source.name
                                SortSources.MOST_ENTRIES -> Int.MAX_VALUE - it.mangaCount
                            }
                        },
                        { it.source.name },
                    ),
                )
            withUIContext {
                controller?.setItems(sources)
            }
        }
    }
}
