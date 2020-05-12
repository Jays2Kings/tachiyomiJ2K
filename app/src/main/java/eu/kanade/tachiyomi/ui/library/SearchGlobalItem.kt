package eu.kanade.tachiyomi.ui.library

import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import kotlinx.android.synthetic.main.material_text_button.*

class SearchGlobalItem : AbstractFlexibleItem<SearchGlobalItem.Holder>() {

    var string = ""

    override fun getLayoutRes(): Int {
        return R.layout.material_text_button
    }

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>
    ): Holder {
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            width = MATCH_PARENT
        }
        return Holder(view, adapter)
    }

    override fun isSelectable(): Boolean {
        return false
    }

    override fun isSwipeable(): Boolean {
        return false
    }

    override fun isDraggable(): Boolean {
        return false
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: Holder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        holder.bind(string)
    }

    override fun equals(other: Any?): Boolean {
        return this === other
    }

    override fun hashCode(): Int {
        return -100
    }

    class Holder(val view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>) :
        BaseFlexibleViewHolder(view, adapter, true) {

        init {
            button.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = MATCH_PARENT
            }
            button.setOnClickListener {
                val query = (adapter.getItem(adapterPosition) as SearchGlobalItem).string
                (adapter as? LibraryCategoryAdapter)?.libraryListener?.globalSearch(query)
            }
        }

        fun bind(string: String) {
            button.text = view.context.getString(R.string.search_globally, string)
        }

        override fun onLongClick(view: View?): Boolean {
            super.onLongClick(view)
            return false
        }
    }
}
