package eu.kanade.tachiyomi.ui.source.browse

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.api.clear
import coil.request.LoadRequest
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.data.image.coil.CoverViewTarget
import kotlinx.android.synthetic.main.manga_list_item.*

/**
 * Class used to hold the displayed data of a manga in the catalogue, like the cover or the title.
 * All the elements from the layout file "item_catalogue_list" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @constructor creates a new catalogue holder.
 */
class BrowseSourceListHolder(private val view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>) :
    BrowseSourceHolder(view, adapter) {

    /**
     * Method called from [CatalogueAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param manga the manga to bind.
     */
    override fun onSetValues(manga: Manga) {
        title.text = manga.title
        with(subtitle) {
            visibility = if (manga.favorite) View.VISIBLE else View.GONE
            text = view.resources.getString(R.string.in_library)
            setTextColor(view.context.getResourceColor(android.R.attr.colorAccent))
        }

        setImage(manga)
    }

    override fun setImage(manga: Manga) {
        // Update the cover.
        if (manga.thumbnail_url == null) {
            cover_thumbnail.clear()
        } else {
            val id = manga.id ?: return
            val request = LoadRequest.Builder(view.context).data(manga)
                .target(CoverViewTarget(cover_thumbnail)).build()
            Coil.imageLoader(view.context).execute(request)
        }
    }
}
