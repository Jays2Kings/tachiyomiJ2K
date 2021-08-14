package eu.kanade.tachiyomi.ui.source.filter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractSectionableItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.widget.SortTextView

class SortItem(val name: String, val group: SortGroup) : AbstractSectionableItem<SortItem.Holder, SortGroup>(group) {

    override fun getLayoutRes(): Int {
        return R.layout.navigation_view_checkedtext
    }

    override fun getItemViewType(): Int {
        return 102
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): Holder {
        return Holder(view, adapter)
    }

    override fun bindViewHolder(adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>, holder: Holder, position: Int, payloads: MutableList<Any?>?) {
        val view = holder.text
        view.text = name

        val filter = group.filter
        val i = filter.values.indexOf(name)
        view.state = when (filter.state) {
            Filter.Sort.Selection(i, false) -> SortTextView.State.DESCENDING
            Filter.Sort.Selection(i, true) -> SortTextView.State.ASCENDING
            else -> SortTextView.State.NONE
        }

        view.setOnSortChangeListener { _, state ->
            filter.state = Filter.Sort.Selection(i, state == SortTextView.State.ASCENDING)
            group.subItems.forEach { adapter.notifyItemChanged(adapter.getGlobalPositionOf(it)) }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SortItem
        return name == other.name && group == other.group
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + group.hashCode()
        return result
    }

    class Holder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>) : FlexibleViewHolder(view, adapter) {

        val text: SortTextView = itemView.findViewById(R.id.nav_view_item)
    }
}
