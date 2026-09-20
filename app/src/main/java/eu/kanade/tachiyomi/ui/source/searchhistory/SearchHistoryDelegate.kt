package eu.kanade.tachiyomi.ui.source.searchhistory

import android.view.MenuItem
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bluelinelabs.conductor.Controller
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.moveRecyclerViewUp
import eu.kanade.tachiyomi.util.view.snack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Wires a [SearchHistoryView] for a controller
 *
 * @param container the ViewGroup the history view should be added to
 * @param recycler the recycler view behind the history view
 * @param isEnabled whether this controller supports the feature at all
 * @param extraShouldShow an extra condition for the history to show
 * @param requireSearchExpanded whether the search toolbar must be expanded before showing
 * @param onApplyFilters called when a picked entry carries filters, for the controller to apply
 * to whatever it searches. A blank-query snapshot has no submission following it, so that one
 * has to run the search itself. Return how many filters matched - anything short of
 * [FilterApplyResult.ALL] shows a heads-up, so return it to report that some other way.
 * @param showFilterSnapshots whether to show query-less filter-only entries
 * @param extraBottomPadding extra clearance for whatever floats over the bottom of [recycler]
 * @param currentSourceId the single source currently being browsed, if any
 * @param onHidden called whenever the history overlay goes from shown to hidden
 * @param collapseSearchOnSnapshot whether applying a snapshot should also close the search bar
 */
class SearchHistoryDelegate(
    private val controller: Controller,
    private val container: () -> ViewGroup,
    private val recycler: () -> RecyclerView?,
    private val isEnabled: () -> Boolean = { true },
    private val extraShouldShow: () -> Boolean = { true },
    private val requireSearchExpanded: Boolean = true,
    private val onApplyFilters: (SearchHistoryEntry) -> FilterApplyResult = { FilterApplyResult.ALL },
    private val showFilterSnapshots: () -> Boolean = { false },
    private val extraBottomPadding: () -> Int = { 0 },
    private val currentSourceId: () -> Long? = { null },
    private val onHidden: () -> Unit = {},
    private val collapseSearchOnSnapshot: Boolean = true,
) {
    private val preferences: PreferencesHelper by lazy { Injekt.get() }

    var view: SearchHistoryView? = null
        private set

    private var suppressNextSave = false

    /**
     * Whether the submission this call is answering for should skip being recorded into recent
     * history - true right after picking a saved search, since [searchView]'s own submit fires
     * the controller's normal search-and-save flow the same way a manually typed query would.
     * Resets itself on read so it only ever applies to the one submission it was set for.
     */
    fun consumeSuppressSave(): Boolean {
        val value = suppressNextSave
        suppressNextSave = false
        return value
    }

    private fun searchView(): SearchView? = controller.activityBinding?.searchToolbar?.searchView

    fun setUp(): SearchHistoryView? {
        view?.let { return it }
        if (!isEnabled() || !preferences.showBrowseSearchHistory().get()) return null
        view =
            SearchHistoryView(container().context).apply {
                isVisible = false
                onQueryClicked = { entry ->
                    val result =
                        if (entry.filters.isEmpty()) FilterApplyResult.ALL else onApplyFilters(entry)
                    val isSnapshot = entry.query.isBlank()
                    if (isSnapshot) {
                        if (result != FilterApplyResult.NONE) {
                            // onApplyFilters above already re-searched with it, so there's no
                            // reason to keep the search bar open - if nothing landed there's
                            // nothing to show for it, so leave things as they were instead
                            setVisible(false)
                            if (collapseSearchOnSnapshot) {
                                controller.activityBinding
                                    ?.searchToolbar
                                    ?.searchItem
                                    ?.collapseActionView()
                            }
                        }
                    } else {
                        // a saved search is never itself reordered into/recorded as recent - the
                        // controller's own submit listener saves every search by default, so tell
                        // it to skip just this once
                        if (entry.name != null) suppressNextSave = true
                        searchView()?.setQuery(entry.query, true)
                    }
                    val message =
                        when (result) {
                            FilterApplyResult.NONE -> if (isSnapshot) R.string.no_filters_applied else R.string.some_filters_not_applied
                            FilterApplyResult.SOME -> R.string.some_filters_not_applied
                            FilterApplyResult.ALL -> null
                        }
                    message?.let { container().snack(it).moveAboveSafeAreas(container().context) }
                }
                onQueryFilled = { searchView()?.setQuery(it, false) }
                onHistoryEmptied = { setVisible(false) }
                onSaveHistoryEntry = { entry -> openSaveDialog(entry, isExisting = false) }
                onEditSavedSearch = { entry -> openSaveDialog(entry, isExisting = true) }
                container().addView(this, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
        return view
    }

    private fun openSaveDialog(
        entry: SearchHistoryEntry,
        isExisting: Boolean,
    ) {
        val activity = controller.activity ?: return
        SaveSearchDialog.show(activity, entry, isExisting)
    }

    /** Pads the history list under whatever height the app bar is currently showing on screen. */
    fun updatePadding() {
        val recycler = recycler() ?: return
        val appBar = controller.activityBinding?.appBar
        val visibleAppBarHeight =
            if (appBar != null) {
                (appBar.height + appBar.translationY).toInt().coerceAtLeast(0)
            } else {
                recycler.paddingTop
            }
        view?.setContentPadding(top = visibleAppBarHeight, bottom = recycler.paddingBottom + extraBottomPadding())
    }

    fun setVisible(show: Boolean) {
        val historyView = setUp() ?: return
        // re-evaluated every call, not just once at setUp() - e.g. a source's filters might not
        // have loaded yet the first time this ran
        val showFilterSnapshots = showFilterSnapshots()
        val sourceId = currentSourceId()
        historyView.showFilterSnapshots = showFilterSnapshots
        historyView.currentSourceId = sourceId
        val shouldShow =
            show &&
                extraShouldShow() &&
                (!requireSearchExpanded || controller.activityBinding?.searchToolbar?.isSearchExpanded == true) &&
                SearchHistoryView.hasHistory(preferences, includeFilterSnapshots = showFilterSnapshots, sourceId = sourceId)
        if (historyView.historyShown == shouldShow) return
        historyView.setAnimatedVisible(shouldShow)
        if (!shouldShow) {
            recycler()?.suppressLayout(false)
            onHidden()
            return
        }
        val revealHistory = {
            // freeze the recycler behind the history it can't drag the app bar
            recycler()?.suppressLayout(true)
            updatePadding()
            historyView.scrollToTop()
        }
        if (controller.activityBinding?.appBar?.y != 0f) {
            controller.moveRecyclerViewUp()
            recycler()?.post { revealHistory() }
        } else {
            revealHistory()
        }
    }

    fun onActionViewExpand(item: MenuItem?) = setVisible(true)

    fun onActionViewCollapse(item: MenuItem?) = setVisible(false)

    fun onDestroyView() {
        view = null
    }
}
