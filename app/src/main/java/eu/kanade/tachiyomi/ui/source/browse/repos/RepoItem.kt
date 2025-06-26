package eu.kanade.tachiyomi.ui.source.browse.repos

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.ExtensionRepo

/**
 * Repo item for a recycler view.
 */
class RepoItem(
    val repo: ExtensionRepo?,
    val type: Type,
) : AbstractFlexibleItem<RepoHolder>() {
    /**
     * Whether this item is currently selected.
     */
    var isEditing = false

    /**
     * Returns the layout resource for this item.
     */
    override fun getLayoutRes(): Int = R.layout.categories_item

    /**
     * Returns a new view holder for this item.
     *
     * @param view The view of this item.
     * @param adapter The adapter of this item.
     */
    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ): RepoHolder = RepoHolder(view, adapter as RepoAdapter)

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
        holder: RepoHolder,
        position: Int,
        payloads: List<Any?>?,
    ) {
        holder.bind(this)
        holder.isEditing(isEditing)
    }

    /**
     * Returns true if this item is draggable.
     */
    override fun isDraggable(): Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RepoItem) return false

        if (type != other.type) return false

        // For CREATE type, all items are equal
        if (type == Type.CREATE) return true

        // For DATA type, compare by baseUrl
        return repo?.baseUrl == other.repo?.baseUrl
    }

    override fun hashCode(): Int {
        var result = repo?.baseUrl?.hashCode() ?: 0
        result = 31 * result + type.hashCode()
        return result
    }

    enum class Type {
        DATA,
        CREATE,
    }
}
