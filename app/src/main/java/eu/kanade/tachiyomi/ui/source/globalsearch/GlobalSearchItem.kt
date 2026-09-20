package eu.kanade.tachiyomi.ui.source.globalsearch

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.source.searchhistory.FilterApplyResult

/**
 * Item that contains search result information.
 *
 * @param source the source for the search results.
 * @param results the search results.
 * @param highlighted whether this search item should be highlighted/marked in the catalogue search view.
 * @param filterResult how much of the picked entry's filter set this source could take, or null
 * when the search carries no filters at all (and while results are still loading).
 */
class GlobalSearchItem(
    val source: CatalogueSource,
    val results: List<GlobalSearchMangaItem>?,
    val highlighted: Boolean = false,
    val filterResult: FilterApplyResult? = null,
) : AbstractFlexibleItem<GlobalSearchHolder>() {
    /**
     * True when the source was never searched because the picked entry was filters-only and none
     * of them matched here
     */
    var filtersOnlySkipped: Boolean = false

    /**
     * Set view.
     *
     * @return id of view
     */
    override fun getLayoutRes(): Int = R.layout.source_global_search_controller_card

    /**
     * Create view holder (see [GlobalSearchAdapter].
     *
     * @return holder of view.
     */
    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ): GlobalSearchHolder = GlobalSearchHolder(view, adapter as GlobalSearchAdapter)

    /**
     * Bind item to view.
     */
    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: GlobalSearchHolder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        holder.bind(this)
    }

    /**
     * Used to check if two items are equal.
     *
     * @return items are equal?
     */
    override fun equals(other: Any?): Boolean {
        if (other is GlobalSearchItem) {
            return source.id == other.source.id
        }
        return false
    }

    /**
     * Return hash code of item.
     *
     * @return hashcode
     */
    override fun hashCode(): Int = source.id.toInt()
}
