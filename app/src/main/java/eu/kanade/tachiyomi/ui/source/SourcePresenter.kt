package eu.kanade.tachiyomi.ui.source

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

/**
 * Presenter of [BrowseController]
 * Function calls should be done from here. UI calls should be done from the controller.
 *
 * @param sourceManager manages the different sources.
 * @param preferences application preferences.
 */
class SourcePresenter(
    val controller: BrowseController,
    val sourceManager: SourceManager = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
) {

    private var scope = CoroutineScope(Job() + Dispatchers.Default)
    var sources = getEnabledSources()

    var sourceItems = emptyList<SourceItem>()
    var lastUsedItem: SourceItem? = null

    var lastUsedJob: Job? = null

    fun onCreate() {
        if (lastSources != null) {
            if (sourceItems.isEmpty()) {
                sourceItems = lastSources ?: emptyList()
            }
            lastUsedItem = lastUsedItemRem
            lastSources = null
            lastUsedItemRem = null
        }

        // Load enabled and last used sources
        loadSources()
    }

    /**
     * Unsubscribe and create a new subscription to fetch enabled sources.
     */
    private fun loadSources() {
        scope.launch {
            val pinnedSources = mutableListOf<SourceItem>()
            val pinnedCatalogues = preferences.pinnedCatalogues().get()

            val map = TreeMap<String, MutableList<CatalogueSource>> { d1, d2 ->
                // Catalogues without a lang defined will be placed at the end
                when {
                    d1 == "" && d2 != "" -> 1
                    d2 == "" && d1 != "" -> -1
                    else -> d1.compareTo(d2)
                }
            }
            val byLang = sources.groupByTo(map) { it.lang }
            sourceItems = byLang.flatMap {
                val langItem = LangItem(it.key)
                it.value.map { source ->
                    val isPinned = source.id.toString() in pinnedCatalogues
                    if (source.id.toString() in pinnedCatalogues) {
                        pinnedSources.add(SourceItem(source, LangItem(PINNED_KEY)))
                    }

                    SourceItem(source, langItem, isPinned)
                }
            }

            if (pinnedSources.isNotEmpty()) {
                sourceItems = pinnedSources + sourceItems
            }

            lastUsedItem = getLastUsedSource(preferences.lastUsedCatalogueSource().get())
            withUIContext {
                controller.setSources(sourceItems, lastUsedItem)
                loadLastUsedSource()
            }
        }
    }

    private fun loadLastUsedSource() {
        lastUsedJob?.cancel()
        lastUsedJob = preferences.lastUsedCatalogueSource().asFlow()
            .drop(1)
            .onEach {
                lastUsedItem = getLastUsedSource(it)
                withUIContext {
                    controller.setLastUsedSource(lastUsedItem)
                }
            }.launchIn(scope)
    }

    private fun getLastUsedSource(value: Long): SourceItem? {
        return (sourceManager.get(value) as? CatalogueSource)?.let { source ->
            val pinnedCatalogues = preferences.pinnedCatalogues().get()
            val isPinned = source.id.toString() in pinnedCatalogues
            if (isPinned) null
            else SourceItem(source, LangItem(LAST_USED_KEY), isPinned)
        }
    }

    fun updateSources() {
        sources = getEnabledSources()
        loadSources()
    }

    fun onDestroy() {
        lastSources = sourceItems
        lastUsedItemRem = lastUsedItem
    }

    /**
     * Returns a list of enabled sources ordered by language and name.
     *
     * @return list containing enabled sources.
     */
    private fun getEnabledSources(): List<CatalogueSource> {
        val languages = preferences.enabledLanguages().get()
        val hiddenCatalogues = preferences.hiddenSources().get()

        return sourceManager.getCatalogueSources()
            .filter { it.lang in languages }
            .filterNot { it.id.toString() in hiddenCatalogues }
            .sortedBy { "(${it.lang}) ${it.name}" } +
            sourceManager.get(LocalSource.ID) as LocalSource
    }

    companion object {
        const val PINNED_KEY = "pinned"
        const val LAST_USED_KEY = "last_used"

        private var lastSources: List<SourceItem>? = null
        private var lastUsedItemRem: SourceItem? = null

        fun onLowMemory() {
            lastSources = null
            lastUsedItemRem = null
        }
    }
}
