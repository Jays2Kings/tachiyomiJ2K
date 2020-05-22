package eu.kanade.tachiyomi.ui.library

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import coil.api.clear
import coil.size.Precision
import coil.size.Scale
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.loadLibraryManga
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.manga_grid_item.*
import kotlinx.android.synthetic.main.unread_download_badge.*

/**
 * Class used to hold the displayed data of a manga in the library, like the cover or the title.
 * All the elements from the layout file "item_catalogue_grid" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @param listener a listener to react to single tap and long tap events.
 * @constructor creates a new library holder.
 */
class LibraryGridHolder(
    private val view: View,
    adapter: LibraryCategoryAdapter,
    var width: Int,
    compact: Boolean,
    private var fixedSize: Boolean
) : LibraryHolder(view, adapter) {

    init {
        play_layout.setOnClickListener { playButtonClicked() }
        if (compact) {
            text_layout.gone()
        } else {
            compact_title.gone()
            gradient.gone()
            val playLayout = play_layout.layoutParams as FrameLayout.LayoutParams
            val buttonLayout = play_button.layoutParams as FrameLayout.LayoutParams
            playLayout.gravity = Gravity.BOTTOM or Gravity.END
            buttonLayout.gravity = Gravity.BOTTOM or Gravity.END
            play_layout.layoutParams = playLayout
            play_button.layoutParams = buttonLayout
        }
    }

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param item the manga item to bind.
     */
    override fun onSetValues(item: LibraryItem) {
        // Update the title and subtitle of the manga.
        constraint_layout.visibleIf(!item.manga.isBlank())
        title.text = item.manga.title
        subtitle.text = item.manga.author?.trim()

        compact_title.text = title.text

        setUnreadBadge(badge_view, item)
        setReadingButton(item)

        // Update the cover.
        if (item.manga.thumbnail_url == null) cover_thumbnail.clear()
        else {
            if (cover_thumbnail.height == 0) {
                val oldPos = adapterPosition
                adapter.recyclerView.post {
                    if (oldPos == adapterPosition)
                        setCover(item.manga)
                }
            } else setCover(item.manga)
        }
    }

    private fun setCover(manga: Manga) {
        if ((adapter.recyclerView.context as? Activity)?.isDestroyed == true) return
        cover_thumbnail.loadLibraryManga(manga) {
            if (!fixedSize) {
                precision(Precision.INEXACT)
                scale(Scale.FIT)
            }
        }
    }

    private fun playButtonClicked() {
        adapter.libraryListener.startReading(adapterPosition)
    }

    override fun onActionStateChanged(position: Int, actionState: Int) {
        super.onActionStateChanged(position, actionState)
        if (actionState == 2) {
            card.isDragged = true
            badge_view.isDragged = true
        }
    }

    override fun onItemReleased(position: Int) {
        super.onItemReleased(position)
        card.isDragged = false
        badge_view.isDragged = false
    }
}
