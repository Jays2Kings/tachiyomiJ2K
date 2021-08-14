package eu.kanade.tachiyomi.ui.library

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import coil.clear
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.image.coil.loadManga
import eu.kanade.tachiyomi.databinding.MangaListItemBinding
import eu.kanade.tachiyomi.util.lang.highlightText
import eu.kanade.tachiyomi.util.system.dpToPx

/**
 * Class used to hold the displayed data of a manga in the library, like the cover or the binding.title.
 * All the elements from the layout file "item_library_list" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @constructor creates a new library holder.
 */

class LibraryListHolder(
    private val view: View,
    adapter: LibraryCategoryAdapter
) : LibraryHolder(view, adapter) {

    private val binding = MangaListItemBinding.bind(view)

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param item the manga item to bind.
     */
    override fun onSetValues(item: LibraryItem) {
        binding.title.isVisible = true
        binding.constraintLayout.minHeight = 56.dpToPx
        if (item.manga.isBlank()) {
            binding.constraintLayout.minHeight = 0
            binding.constraintLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                height = ViewGroup.MarginLayoutParams.WRAP_CONTENT
            }
            if (item.manga.status == -1) {
                binding.title.text = null
                binding.title.isVisible = false
            } else {
                binding.title.text = itemView.context.getString(R.string.category_is_empty)
            }
            binding.title.textAlignment = View.TEXT_ALIGNMENT_CENTER
            binding.card.isVisible = false
            binding.unreadDownloadBadge.badgeView.isVisible = false
            binding.padding.isVisible = false
            binding.subtitle.isVisible = false
            return
        }
        binding.constraintLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            height = 52.dpToPx
        }
        binding.padding.isVisible = true
        binding.card.isVisible = true
        binding.title.textAlignment = View.TEXT_ALIGNMENT_TEXT_START

        // Update the binding.title of the manga.
        binding.title.text = item.manga.title.highlightText(item.filter, color)
        setUnreadBadge(binding.unreadDownloadBadge.badgeView, item)

        val authorArtist = if (item.manga.author == item.manga.artist || item.manga.artist.isNullOrBlank()) {
            item.manga.author?.trim() ?: ""
        } else {
            listOfNotNull(
                item.manga.author?.trim()?.takeIf { it.isNotBlank() },
                item.manga.artist?.trim()?.takeIf { it.isNotBlank() }
            ).joinToString(", ")
        }

        binding.subtitle.text = authorArtist.highlightText(item.filter, color)
        binding.title.maxLines = 2
        binding.title.post {
            val hasAuthorInFilter =
                item.filter.isNotBlank() && authorArtist.contains(item.filter, true)
            binding.subtitle.isVisible = binding.title.lineCount <= 1 || hasAuthorInFilter
            binding.title.maxLines = if (hasAuthorInFilter) 1 else 2
        }

        // Update the cover.
        if (item.manga.thumbnail_url == null) {
            binding.coverThumbnail.clear()
        } else {
            item.manga.id ?: return
            binding.coverThumbnail.loadManga(item.manga)
        }
    }

    override fun onActionStateChanged(position: Int, actionState: Int) {
        super.onActionStateChanged(position, actionState)
        if (actionState == 2) {
            binding.card.isDragged = true
        }
    }

    override fun onItemReleased(position: Int) {
        super.onItemReleased(position)
        binding.card.isDragged = false
    }
}
