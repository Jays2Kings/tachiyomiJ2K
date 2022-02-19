package eu.kanade.tachiyomi.ui.library

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import coil.clear
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.image.coil.loadManga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MangaListItemBinding
import eu.kanade.tachiyomi.util.lang.highlightText
import eu.kanade.tachiyomi.util.system.dpToPx
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
    adapter: LibraryCategoryAdapter,
    preferences: PreferencesHelper = Injekt.get()
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
                binding.title.text = itemView.context.getString(
                    if (adapter.hasActiveFilters) R.string.no_matches_for_filters_short
                    else R.string.category_is_empty
                )
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

        val authorArtist =
            if (item.manga.author == item.manga.artist || item.manga.artist.isNullOrBlank()) {
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
        binding.coverThumbnail.clear()
        binding.coverThumbnail.loadManga(item.manga)
        binding.unreadDownloadBadge.languageAngle.isVisible = item.sourceLanguage.isNotEmpty()
        binding.unreadDownloadBadge.languageText.isVisible = item.sourceLanguage.isNotEmpty()
        binding.unreadDownloadBadge.languageText.text = item.sourceLanguage
        val startPadding = if (item.sourceLanguage.isNotEmpty()) 2.dpToPx else 5.dpToPx
        val endPadding = if (item.sourceLanguage.isNotEmpty()) 8.dpToPx else 5.dpToPx
        binding.unreadDownloadBadge.unreadText.updatePaddingRelative(
            start = startPadding, end = endPadding
        )
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
