package eu.kanade.tachiyomi.ui.source.globalsearch

import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R

class GlobalSearchFilterHeaderItem(
    private val showPinnedButton: () -> Boolean,
    private val isPinnedOnly: () -> Boolean,
    private val isHasResults: () -> Boolean,
    private val onPinnedClick: (Boolean) -> Unit,
    private val onHasResultsClick: (Boolean) -> Unit,
) : AbstractFlexibleItem<GlobalSearchFilterHeaderItem.Holder>() {
    override fun getLayoutRes(): Int = R.layout.source_global_search_filter_header

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ): Holder = Holder(view, adapter)

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: Holder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        holder.pinnedButton.isVisible = showPinnedButton()
        holder.pinnedButton.isChecked = isPinnedOnly()
        holder.hasResultsButton.isChecked = isHasResults()

        holder.pinnedButton.setOnClickListener {
            val enabled = !holder.pinnedButton.isChecked
            holder.pinnedButton.isChecked = enabled
            onPinnedClick(enabled)
        }
        holder.hasResultsButton.setOnClickListener {
            val enabled = !holder.hasResultsButton.isChecked
            holder.hasResultsButton.isChecked = enabled
            onHasResultsClick(enabled)
        }
    }

    override fun isSelectable() = false

    override fun isSwipeable() = false

    override fun isDraggable() = false

    override fun equals(other: Any?): Boolean = this === other

    class Holder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ) : FlexibleViewHolder(view, adapter) {
        val pinnedButton: MaterialButton = view.findViewById(R.id.pinned_button)
        val hasResultsButton: MaterialButton = view.findViewById(R.id.has_results_button)
    }

    override fun hashCode(): Int {
        var result = showPinnedButton.hashCode()
        result = 31 * result + isPinnedOnly.hashCode()
        result = 31 * result + isHasResults.hashCode()
        result = 31 * result + onPinnedClick.hashCode()
        result = 31 * result + onHasResultsClick.hashCode()
        return result
    }
}
