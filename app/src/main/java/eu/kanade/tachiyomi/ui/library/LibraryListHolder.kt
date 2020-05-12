package eu.kanade.tachiyomi.ui.library

import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.manga_list_item.*
import kotlinx.android.synthetic.main.manga_list_item.view.*
import kotlinx.android.synthetic.main.unread_download_badge.*

/**
 * Class used to hold the displayed data of a manga in the library, like the cover or the title.
 * All the elements from the layout file "item_library_list" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @param listener a listener to react to single tap and long tap events.
 * @constructor creates a new library holder.
 */

class LibraryListHolder(
    private val view: View,
    adapter: LibraryCategoryAdapter
) : LibraryHolder(view, adapter) {

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param item the manga item to bind.
     */
    override fun onSetValues(item: LibraryItem) {

        title.visible()
        constraint_layout.minHeight = 56.dpToPx
        if (item.manga.isBlank()) {
            constraint_layout.minHeight = 0
            constraint_layout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                height = ViewGroup.MarginLayoutParams.WRAP_CONTENT
            }
            if (item.manga.status == -1) {
                title.text = null
                title.gone()
            } else
                title.text = itemView.context.getString(R.string.category_is_empty)
            title.textAlignment = View.TEXT_ALIGNMENT_CENTER
            card.gone()
            badge_view.gone()
            padding.gone()
            subtitle.gone()
            return
        }
        constraint_layout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            height = 52.dpToPx
        }
        padding.visible()
        card.visible()
        title.textAlignment = View.TEXT_ALIGNMENT_TEXT_START

        // Update the title of the manga.
        title.text = item.manga.title
        setUnreadBadge(badge_view, item)

        subtitle.text = item.manga.author?.trim()
        title.post {
            if (title.text == item.manga.title) {
                subtitle.visibleIf(title.lineCount == 1 && !item.manga.author.isNullOrBlank())
            }
        }

        // Update the cover.
        if (item.manga.thumbnail_url == null) Glide.with(view.context).clear(cover_thumbnail)
        else {
            val id = item.manga.id ?: return

            GlideApp.with(view.context).load(item.manga)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .signature(ObjectKey(MangaImpl.getLastCoverFetch(id).toString()))
                .centerCrop()
                .into(cover_thumbnail)
        }
    }

    override fun onActionStateChanged(position: Int, actionState: Int) {
        super.onActionStateChanged(position, actionState)
        if (actionState == 2) {
            view.card.isDragged = true
        }
    }

    override fun onItemReleased(position: Int) {
        super.onItemReleased(position)
        view.card.isDragged = false
    }
}
