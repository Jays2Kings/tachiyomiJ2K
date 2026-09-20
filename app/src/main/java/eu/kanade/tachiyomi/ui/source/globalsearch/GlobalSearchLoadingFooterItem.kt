package eu.kanade.tachiyomi.ui.source.globalsearch

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R

class GlobalSearchLoadingFooterItem : AbstractFlexibleItem<GlobalSearchLoadingFooterItem.Holder>() {
    override fun getLayoutRes(): Int = R.layout.source_global_search_loading_footer

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
    }

    override fun isSelectable() = false

    override fun isSwipeable() = false

    override fun isDraggable() = false

    override fun equals(other: Any?): Boolean = this === other

    class Holder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ) : FlexibleViewHolder(view, adapter)

    override fun hashCode(): Int = javaClass.hashCode()
}
