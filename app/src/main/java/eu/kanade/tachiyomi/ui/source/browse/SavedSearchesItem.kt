package eu.kanade.tachiyomi.ui.source.browse

import android.content.res.ColorStateList
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.chip.Chip
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractHeaderItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.SavedSearch
import eu.kanade.tachiyomi.databinding.SavedSearchesItemBinding
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor

class SavedSearchesItem(
    val savedSearches: List<SavedSearch>,
) : AbstractHeaderItem<SavedSearchesItem.Holder>() {
    var onSavedSearchClicked: (SavedSearch) -> Unit = {}
    var onSavedSearchLongClicked: (SavedSearch) -> Unit = {}

    override fun getLayoutRes(): Int = R.layout.saved_searches_item

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ): Holder = Holder(view, adapter)

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: Holder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        holder.bind(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is SavedSearchesItem) {
            return savedSearches == other.savedSearches
        }
        return false
    }

    override fun hashCode(): Int = savedSearches.hashCode()

    class Holder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ) : FlexibleViewHolder(view, adapter) {
        private val binding = SavedSearchesItemBinding.bind(view)

        fun bind(item: SavedSearchesItem) {
            binding.flexbox.removeAllViews()
            item.savedSearches.forEach { savedSearch ->
                val chip =
                    Chip(binding.root.context).apply {
                        text = savedSearch.name
                        setOnClickListener { item.onSavedSearchClicked(savedSearch) }
                        setOnLongClickListener {
                            item.onSavedSearchLongClicked(savedSearch)
                            true
                        }
                        chipBackgroundColor =
                            ColorStateList.valueOf(
                                context.getResourceColor(R.attr.colorSecondaryContainer),
                            )
                        setTextColor(context.getResourceColor(R.attr.colorOnSecondaryContainer))
                        chipStrokeWidth = 0f
                        layoutParams =
                            FlexboxLayout
                                .LayoutParams(
                                    FlexboxLayout.LayoutParams.WRAP_CONTENT,
                                    FlexboxLayout.LayoutParams.WRAP_CONTENT,
                                ).apply {
                                    setMargins(0, 0, 8.dpToPx, 8.dpToPx)
                                }
                    }
                binding.flexbox.addView(chip)
            }
        }
    }
}
