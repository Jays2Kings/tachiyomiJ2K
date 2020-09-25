package eu.kanade.tachiyomi.ui.source

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.android.synthetic.main.source_header_item.*

class LangHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>) :
    BaseFlexibleViewHolder(view, adapter) {

    fun bind(item: LangItem) {
        title.text = LocaleHelper.getDisplayName(item.code, itemView.context)
    }
}
