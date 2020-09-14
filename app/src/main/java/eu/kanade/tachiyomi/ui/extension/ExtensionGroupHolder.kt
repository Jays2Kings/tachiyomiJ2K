package eu.kanade.tachiyomi.ui.extension

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import kotlinx.android.synthetic.main.extension_card_header.*

class ExtensionGroupHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>) :
    BaseFlexibleViewHolder(view, adapter) {

    @SuppressLint("SetTextI18n")
    fun bind(item: ExtensionGroupItem) {
        title.text = item.name
    }
}
