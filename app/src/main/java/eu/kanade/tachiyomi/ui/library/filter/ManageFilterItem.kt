package eu.kanade.tachiyomi.ui.library.filter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.view.gone
import kotlinx.android.synthetic.main.categories_item.*

/**
 * Category item for a recycler view.
 */
class ManageFilterItem(val char: Char) : AbstractFlexibleItem<ManageFilterItem.Holder>() {

    /**
     * Returns the layout resource for this item.
     */
    override fun getLayoutRes(): Int {
        return R.layout.categories_item
    }

    /**
     * Returns a new view holder for this item.
     *
     * @param view The view of this item.
     * @param adapter The adapter of this item.
     */
    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>
    ): Holder {
        return Holder(view, adapter)
    }

    /**
     * Binds the given view holder with this item.
     *
     * @param adapter The adapter of this item.
     * @param holder The holder to bind.
     * @param position The position of this item in the adapter.
     * @param payloads List of partial changes.
     */
    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: Holder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        holder.bind(char)
    }

    /**
     * Returns true if this item is draggable.
     */
    override fun isDraggable(): Boolean {
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is ManageFilterItem) {
            return char == other.char
        }
        return false
    }

    override fun hashCode(): Int {
        return char.hashCode()
    }

    class Holder(val view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>) :
        BaseFlexibleViewHolder(view, adapter, true) {

        init {
            image.gone()
            edit_button.gone()
            edit_text.isEnabled = false
            setDragHandleView(reorder)
        }

        fun bind(char: Char) {
            title.setText(
                when (char) {
                    'u' -> R.string.read_progress
                    'r' -> R.string.unread
                    'd' -> R.string.downloaded
                    'c' -> R.string.status
                    'm' -> R.string.series_type
                    't' -> R.string.tracked
                    else -> R.string.unread
                }
            )
        }
    }
}
