package eu.kanade.tachiyomi.ui.source.searchhistory

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.LinearLayoutManager
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.SearchHistoryViewBinding
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.GroupedRowDivider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.injectLazy

/**
 * Recent browse queries, shown over a browse screen's content while its search bar is open and
 * empty. Reads the pref itself so a query saved on one screen shows up on the next one.
 */
class SearchHistoryView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : LinearLayout(context, attrs) {
        private val preferences: PreferencesHelper by injectLazy()
        private val binding: SearchHistoryViewBinding
        private val itemAdapter = ItemAdapter<SearchHistoryItem>()
        private val fastAdapter = FastAdapter.with(itemAdapter)
        private var scope: CoroutineScope? = null

        var onQueryClicked: (String) -> Unit = { _ -> }
        var onHistoryEmptied: () -> Unit = { }

        init {
            orientation = VERTICAL
            setBackgroundColor(context.getResourceColor(R.attr.background))
            binding = SearchHistoryViewBinding.inflate(LayoutInflater.from(context), this)
            binding.recycler.layoutManager = LinearLayoutManager(context)
            binding.recycler.adapter = fastAdapter
            binding.recycler.itemAnimator = null
            binding.recycler.addItemDecoration(
                GroupedRowDivider(context, isGroupedRow = { it is SearchHistoryItem.ViewHolder }),
            )
            fastAdapter.onClickListener = { _, _, item, _ ->
                onQueryClicked(item.query)
                true
            }
            binding.clearAllButton.setOnClickListener { preferences.clearSearchHistory() }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            scope = newScope
            preferences
                .browseSearchHistory()
                .asFlow()
                .onEach(::setHistory)
                .launchIn(newScope)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            scope?.cancel()
            scope = null
        }

        fun hasHistory(): Boolean =
            preferences.showBrowseSearchHistory().get() &&
                preferences.browseSearchHistory().get().isNotEmpty()

        /** Mirrors the host recycler's insets so the list clears the app bar and bottom nav. */
        fun setContentPadding(
            top: Int,
            bottom: Int,
        ) {
            binding.header.updatePaddingRelative(top = top)
            binding.recycler.updatePaddingRelative(bottom = bottom)
        }

        fun scrollToTop() = binding.recycler.scrollToPosition(0)

        private fun setHistory(history: List<String>) {
            itemAdapter.set(
                history.mapIndexed { index, query ->
                    SearchHistoryItem(
                        query = query,
                        isTopOfGroup = index == 0,
                        isBottomOfGroup = index == history.lastIndex,
                        onDeleteClicked = { preferences.removeFromSearchHistory(it) },
                    )
                },
            )
            if (history.isEmpty() && isVisible) {
                onHistoryEmptied()
            }
        }
    }
