package eu.kanade.tachiyomi.ui.source.browse

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.f2prateek.rx.preferences.Preference
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import kotlinx.android.synthetic.main.manga_grid_item.view.*

class BrowseSourceItem(
    val manga: Manga,
    private val catalogueAsList: Preference<Boolean>,
    private val catalogueListType: Preference<Int>
) :
    AbstractFlexibleItem<BrowseSourceHolder>() {

    override fun getLayoutRes(): Int {
        return if (catalogueAsList.getOrDefault())
            R.layout.manga_list_item
        else
            R.layout.manga_grid_item
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): BrowseSourceHolder {
        val parent = adapter.recyclerView
        return if (parent is AutofitRecyclerView && !catalogueAsList.getOrDefault()) {
            val listType = catalogueListType.getOrDefault()
            view.apply {
                val coverHeight = (parent.itemWidth / 3 * 4f).toInt()
                if (listType == 1) {
                    gradient.layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        (coverHeight * 0.66f).toInt(),
                        Gravity.BOTTOM
                    )
                    card.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        bottomMargin = 6.dpToPx
                    }
                } else {
                    constraint_layout.background = ContextCompat.getDrawable(
                        context,
                        R.drawable.library_item_selector
                    )
                }
                constraint_layout.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                cover_thumbnail.maxHeight = Int.MAX_VALUE
                cover_thumbnail.minimumHeight = 0
                constraint_layout.minHeight = 0
                cover_thumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
                cover_thumbnail.adjustViewBounds = false
                cover_thumbnail.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (parent.itemWidth / 3f * 3.7f).toInt()
                )
            }
            BrowseSourceGridHolder(view, adapter, listType == 1)
        } else {
            BrowseSourceListHolder(view, adapter)
        }
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: BrowseSourceHolder,
        position: Int,
        payloads: MutableList<Any?>?
    ) {

        holder.onSetValues(manga)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is BrowseSourceItem) {
            return manga.id!! == other.manga.id!!
        }
        return false
    }

    override fun hashCode(): Int {
        return manga.id!!.hashCode()
    }
}
