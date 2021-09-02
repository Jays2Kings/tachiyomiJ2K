package eu.kanade.tachiyomi.ui.manga.track

import android.view.View
import androidx.core.view.isVisible
import coil.clear
import coil.load
import com.google.android.material.shape.CornerFamily
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.databinding.TrackSearchItemBinding
import eu.kanade.tachiyomi.util.system.dpToPx
import java.util.Locale

class TrackSearchItem(val trackSearch: TrackSearch) : AbstractItem<TrackSearchItem.ViewHolder>() {

    /** defines the type defining this item. must be unique. preferably an id */
    override val type: Int = R.id.track_search_cover

    /**
     * Returns the layout resource for this item.
     */
    override val layoutRes: Int = R.layout.track_search_item
    override var identifier = trackSearch.media_id.toLong()

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder(view: View) : FastAdapter.ViewHolder<TrackSearchItem>(view) {

        val binding = TrackSearchItemBinding.bind(view)

        init {
            binding.trackSearchCover.shapeAppearanceModel =
                binding.trackSearchCover.shapeAppearanceModel.toBuilder()
                    .setAllCorners(CornerFamily.ROUNDED, 5f.dpToPx)
                    .build()
        }

        override fun bindView(item: TrackSearchItem, payloads: List<Any>) {
            val track = item.trackSearch
            binding.checkbox.isVisible = item.isSelected
            binding.trackSearchTitle.text = track.title
            binding.trackSearchSummary.text = track.summary
            binding.trackSearchSummary.isVisible = track.summary.isNotBlank()
            binding.trackSearchCover.clear()
            if (track.cover_url.isNotEmpty()) {
                binding.trackSearchCover.load(track.cover_url)
            }

            if (track.publishing_status.isBlank()) {
                binding.trackSearchStatus.isVisible = false
                binding.trackSearchStatusResult.isVisible = false
            } else {
                binding.trackSearchStatusResult.text = track.publishing_status.capitalize(Locale.ROOT)
            }

            if (track.publishing_type.isBlank()) {
                binding.trackSearchType.isVisible = false
                binding.trackSearchTypeResult.isVisible = false
            } else {
                binding.trackSearchTypeResult.text = track.publishing_type.capitalize(Locale.ROOT)
            }

            if (track.start_date.isBlank()) {
                binding.trackSearchStart.isVisible = false
                binding.trackSearchStartResult.isVisible = false
            } else {
                binding.trackSearchStartResult.text = track.start_date
            }
        }

        override fun unbindView(item: TrackSearchItem) {
        }
    }
}
