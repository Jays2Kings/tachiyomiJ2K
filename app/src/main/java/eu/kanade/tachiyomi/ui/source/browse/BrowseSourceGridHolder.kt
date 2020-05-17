package eu.kanade.tachiyomi.ui.source.browse

import android.app.Activity
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.api.clear
import coil.request.LoadRequest
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.library.LibraryCategoryAdapter
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.widget.CoverViewTarget
import kotlinx.android.synthetic.main.manga_grid_item.*
import kotlinx.android.synthetic.main.manga_grid_item.cover_thumbnail
import kotlinx.android.synthetic.main.manga_grid_item.title
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
class BrowseSourceGridHolder(
    private val view: View,
    private val adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    compact: Boolean
) : BrowseSourceHolder(view, adapter) {

    init {
        if (compact) {
            text_layout.gone()
        } else {
            compact_title.gone()
            gradient.gone()
        }
    }

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param manga the manga item to bind.
     */
    override fun onSetValues(manga: Manga) {
        // Update the title of the manga.
        title.text = manga.title
        compact_title.text = title.text
        badge_view.setInLibrary(manga.favorite)

        // Update the cover.
        setImage(manga)
    }

    override fun setImage(manga: Manga) {
        if ((view.context as? Activity)?.isDestroyed == true) return
        if (manga.thumbnail_url == null) {
            cover_thumbnail.clear()
        } else {
            val id = manga.id ?: return
            val request = LoadRequest.Builder(view.context).data(manga).target(CoverViewTarget(cover_thumbnail, progress)).build()
            Coil.imageLoader(view.context).execute(request)
        }
    }
}
