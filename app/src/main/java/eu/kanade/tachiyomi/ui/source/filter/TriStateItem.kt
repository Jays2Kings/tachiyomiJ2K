package eu.kanade.tachiyomi.ui.source.filter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import eu.kanade.tachiyomi.R as TR

open class TriStateItem(val filter: Filter.TriState) : AbstractFlexibleItem<TriStateItem.Holder>() {

    override fun getLayoutRes(): Int {
        return TR.layout.navigation_view_tristatebox
    }

    override fun getItemViewType(): Int {
        return 103
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): Holder {
        return Holder(view, adapter)
    }

    override fun bindViewHolder(adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>, holder: Holder, position: Int, payloads: MutableList<Any?>?) {
        val view = holder.text
        view.text = filter.name
        view.state = when (filter.state) {
            Filter.TriState.STATE_IGNORE -> TriStateCheckBox.State.UNCHECKED
            Filter.TriState.STATE_INCLUDE -> TriStateCheckBox.State.CHECKED
            Filter.TriState.STATE_EXCLUDE -> TriStateCheckBox.State.INVERSED
            else -> throw Exception("Unknown state")
        }

        view.setOnCheckedChangeListener { _, state ->
            filter.state = when (state) {
                TriStateCheckBox.State.UNCHECKED -> Filter.TriState.STATE_IGNORE
                TriStateCheckBox.State.CHECKED -> Filter.TriState.STATE_INCLUDE
                TriStateCheckBox.State.INVERSED -> Filter.TriState.STATE_EXCLUDE
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return filter == (other as TriStateItem).filter
    }

    override fun hashCode(): Int {
        return filter.hashCode()
    }

    class Holder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>) : FlexibleViewHolder(view, adapter) {

        val text: TriStateCheckBox = itemView.findViewById(TR.id.nav_view_item)
    }
}
