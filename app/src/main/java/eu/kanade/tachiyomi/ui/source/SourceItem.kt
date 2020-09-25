package eu.kanade.tachiyomi.ui.source

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractSectionableItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource

/**
 * Item that contains source information.
 *
 * @param source Instance of [CatalogueSource] containing source information.
 * @param header The header for this item.
 */
class SourceItem(val source: CatalogueSource, header: LangItem? = null, val isPinned: Boolean? = null) :
    AbstractSectionableItem<SourceHolder, LangItem>(header) {

    /**
     * Returns the layout resource of this item.
     */
    override fun getLayoutRes(): Int {
        return R.layout.source_item
    }

    override fun isSwipeable(): Boolean {
        return source.id != LocalSource.ID && header != null
    }

    /**
     * Creates a new view holder for this item.
     */
    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>
    ): SourceHolder {
        return SourceHolder(view, adapter as SourceAdapter)
    }

    /**
     * Binds this item to the given view holder.
     */
    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: SourceHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        holder.bind(this)
    }

    override fun equals(other: Any?): Boolean {
        if (other is SourceItem) {
            return source.id == other.source.id && header?.code == other.header?.code
        }
        return false
    }

    override fun hashCode(): Int {
        return source.id.hashCode() + (header?.code?.hashCode() ?: 0).toInt()
    }
}
