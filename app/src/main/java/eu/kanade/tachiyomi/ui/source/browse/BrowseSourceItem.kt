package eu.kanade.tachiyomi.ui.source.browse

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.tfcporciuncula.flow.Preference
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.MangaGridItemBinding
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.widget.AutofitRecyclerView

class BrowseSourceItem(
    val manga: Manga,
    private val catalogueAsList: Preference<Boolean>,
    private val catalogueListType: Preference<Int>,
    private val outlineOnCovers: Preference<Boolean>
) :
    AbstractFlexibleItem<BrowseSourceHolder>() {

    override fun getLayoutRes(): Int {
        return if (catalogueAsList.get()) {
            R.layout.manga_list_item
        } else {
            R.layout.manga_grid_item
        }
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): BrowseSourceHolder {
        val parent = adapter.recyclerView
        return if (parent is AutofitRecyclerView && !catalogueAsList.get()) {
            val listType = catalogueListType.get()
            view.apply {
                val binding = MangaGridItemBinding.bind(this)
                val coverHeight = (parent.itemWidth / 3 * 4f).toInt()
                if (listType == 1) {
                    binding.gradient.layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        (coverHeight * 0.66f).toInt(),
                        Gravity.BOTTOM
                    )
                    binding.card.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        bottomMargin = 6.dpToPx
                    }
                } else {
                    binding.constraintLayout.background = context.contextCompatDrawable(
                        R.drawable.library_comfortable_grid_selector
                    )
                    binding.constraintLayout.foreground = context.contextCompatDrawable(
                        R.drawable.library_comfortable_grid_selector_overlay
                    )
                    binding.card.setCardForegroundColor(
                        ContextCompat.getColorStateList(
                            context,
                            R.color.library_comfortable_grid_foreground
                        )
                    )
                }
                binding.constraintLayout.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                binding.coverThumbnail.maxHeight = Int.MAX_VALUE
                binding.coverThumbnail.minimumHeight = 0
                binding.constraintLayout.minHeight = 0
                binding.coverThumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
                binding.coverThumbnail.adjustViewBounds = false
                binding.coverThumbnail.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                    dimensionRatio = "15:22"
                }
            }
            BrowseSourceGridHolder(view, adapter, listType == 1, outlineOnCovers.get())
        } else {
            BrowseSourceListHolder(view, adapter, outlineOnCovers.get())
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
