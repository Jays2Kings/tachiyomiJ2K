package eu.kanade.tachiyomi.ui.source

import android.content.res.ColorStateList
import android.view.View
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visible
import kotlinx.android.synthetic.main.source_item.*

class SourceHolder(view: View, val adapter: SourceAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    init {
        source_pin.setOnClickListener {
            adapter.sourceListener.onPinClick(adapterPosition)
        }
        source_latest.setOnClickListener {
            adapter.sourceListener.onLatestClick(adapterPosition)
        }
    }

    fun bind(item: SourceItem) {
        val source = item.source
        // setCardEdges(item)

        // Set source name
        title.text = source.name

        val isPinned = item.isPinned ?: item.header?.code?.equals(SourcePresenter.PINNED_KEY) ?: false
        source_pin.apply {
            imageTintList = ColorStateList.valueOf(
                context.getResourceColor(
                    if (isPinned) R.attr.colorAccent
                    else android.R.attr.textColorSecondary
                )
            )
            setImageResource(
                if (isPinned) R.drawable.ic_pin_24dp
                else R.drawable.ic_pin_outline_24dp
            )
        }

        // Set circle letter image.
        itemView.post {
            val icon = source.icon()
            when {
                icon != null -> edit_button.setImageDrawable(icon)
                item.source.id == LocalSource.ID -> edit_button.setImageResource(R.mipmap.ic_local_source)
            }
        }

        if (source.supportsLatest) {
            source_latest.visible()
        } else {
            source_latest.gone()
        }
    }

    override fun getFrontView(): View {
        return card
    }

    override fun getRearLeftView(): View {
        return left_view
    }

    override fun getRearRightView(): View {
        return right_view
    }
}
