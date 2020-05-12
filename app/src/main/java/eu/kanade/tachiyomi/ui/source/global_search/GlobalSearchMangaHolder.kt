package eu.kanade.tachiyomi.ui.source.global_search

import android.view.View
import com.bumptech.glide.load.engine.DiskCacheStrategy
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.view.visibleIf
import eu.kanade.tachiyomi.widget.StateImageViewTarget
import kotlinx.android.synthetic.main.source_global_search_controller_card_item.*

class GlobalSearchMangaHolder(view: View, adapter: GlobalSearchCardAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    init {
        // Call onMangaClickListener when item is pressed.
        itemView.setOnClickListener {
            val item = adapter.getItem(adapterPosition)
            if (item != null) {
                adapter.mangaClickListener.onMangaClick(item.manga)
            }
        }
        itemView.setOnLongClickListener {
            val item = adapter.getItem(adapterPosition)
            if (item != null) {
                adapter.mangaClickListener.onMangaLongClick(item.manga)
            }
            true
        }
    }

    fun bind(manga: Manga) {
        title.text = manga.title
        favorite_button.visibleIf(manga.favorite)
        setImage(manga)
    }

    fun setImage(manga: Manga) {
        GlideApp.with(itemView.context).clear(itemImage)
        if (!manga.thumbnail_url.isNullOrEmpty()) {
            GlideApp.with(itemView.context)
                    .load(manga)
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .centerCrop()
                    .skipMemoryCache(true)
                    .placeholder(android.R.color.transparent)
                    .into(StateImageViewTarget(itemImage, progress))
        }
    }
}
