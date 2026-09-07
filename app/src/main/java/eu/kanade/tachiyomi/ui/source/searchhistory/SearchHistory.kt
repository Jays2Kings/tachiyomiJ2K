package eu.kanade.tachiyomi.ui.source.searchhistory

import eu.kanade.tachiyomi.data.preference.PreferencesHelper

private const val SEARCH_HISTORY_LIMIT = 20

fun PreferencesHelper.addToSearchHistory(query: String) {
    if (!showBrowseSearchHistory().get()) return
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank()) return
    val pref = browseSearchHistory()
    // drop the old copy first so searching the same thing twice bumps it instead of doubling up
    val history = listOf(trimmedQuery) + pref.get().filterNot { it.equals(trimmedQuery, true) }
    pref.set(history.take(SEARCH_HISTORY_LIMIT))
}

fun PreferencesHelper.removeFromSearchHistory(query: String) {
    val pref = browseSearchHistory()
    pref.set(pref.get().filterNot { it.equals(query, true) })
}

fun PreferencesHelper.clearSearchHistory() = browseSearchHistory().delete()
