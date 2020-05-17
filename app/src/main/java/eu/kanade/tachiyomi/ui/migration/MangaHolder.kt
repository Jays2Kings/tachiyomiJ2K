package eu.kanade.tachiyomi.ui.migration

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import coil.api.clear
import coil.api.loadAny
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import kotlinx.android.synthetic.main.manga_list_item.*

class MangaHolder(
    view: View,
    adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>
) : BaseFlexibleViewHolder(view, adapter) {

    fun bind(item: MangaItem) {
        // Update the title of the manga.
        title.text = item.manga.title
        subtitle.text = item.manga.author?.trim()

        // Update the cover.
        cover_thumbnail.clear()
        cover_thumbnail.loadAny(item.manga)
    }
}
