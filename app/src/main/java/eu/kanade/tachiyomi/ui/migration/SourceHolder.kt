package eu.kanade.tachiyomi.ui.migration

import android.view.View
import eu.kanade.tachiyomi.databinding.MigrationCardItemBinding
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder

class SourceHolder(view: View, val adapter: SourceAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    private val binding = MigrationCardItemBinding.bind(view)
    init {
        binding.migrationAll.setOnClickListener {
            adapter.allClickListener.onAllClick(flexibleAdapterPosition)
        }
    }

    fun bind(item: SourceItem) {
        val source = item.source

        // Set source name
        val sourceName =
            if (adapter.isMultiLanguage) source.toString() else source.name.capitalize()
        binding.title.text = sourceName

        // Set circle letter image.
        itemView.post {
            binding.sourceImage.setImageDrawable(source.icon())
        }
    }
}
