package eu.kanade.tachiyomi.ui.source.global_search

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga

class GlobalSearchMangaItem(val manga: Manga) : AbstractFlexibleItem<GlobalSearchMangaHolder>() {

    override fun getLayoutRes(): Int {
        return R.layout.source_global_search_controller_card_item
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): GlobalSearchMangaHolder {
        return GlobalSearchMangaHolder(view, adapter as GlobalSearchCardAdapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: GlobalSearchMangaHolder,
        position: Int,
        payloads: MutableList<Any?>?
    ) {
        holder.bind(manga)
    }

    override fun equals(other: Any?): Boolean {
        if (other is GlobalSearchMangaItem) {
            return manga.id == other.manga.id
        }
        return false
    }

    override fun hashCode(): Int {
        return manga.id?.toInt() ?: 0
    }
}
