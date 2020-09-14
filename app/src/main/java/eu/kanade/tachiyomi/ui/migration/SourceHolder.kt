package eu.kanade.tachiyomi.ui.migration

import android.view.View
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import kotlinx.android.synthetic.main.migration_card_item.*
import kotlinx.android.synthetic.main.source_item.edit_button
import kotlinx.android.synthetic.main.source_item.title

class SourceHolder(view: View, val adapter: SourceAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    init {
        migration_all.setOnClickListener {
            adapter.allClickListener?.onAllClick(adapterPosition)
        }
    }

    fun bind(item: SourceItem) {
        val source = item.source

        // Set source name
        title.text = source.name

        // Set circle letter image.
        itemView.post {
            edit_button.setImageDrawable(source.icon())
        }
    }
}
