package eu.kanade.tachiyomi.ui.source.searchhistory

import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.view.makeContainerShape

class SearchHistoryItem(
    val query: String,
    private val isTopOfGroup: Boolean,
    private val isBottomOfGroup: Boolean,
    private val onDeleteClicked: (String) -> Unit,
) : AbstractItem<FastAdapter.ViewHolder<SearchHistoryItem>>() {
    override val type: Int = R.id.history_card

    override val layoutRes: Int = R.layout.search_history_item

    override var identifier = query.hashCode().toLong()

    override fun getViewHolder(v: View): FastAdapter.ViewHolder<SearchHistoryItem> = ViewHolder(v)

    class ViewHolder(
        view: View,
    ) : FastAdapter.ViewHolder<SearchHistoryItem>(view) {
        private val card: MaterialCardView = view.findViewById(R.id.history_card)
        private val frontView: View = view.findViewById(R.id.front_view)
        private val title: TextView = view.findViewById(R.id.title)
        private val deleteButton: MaterialButton = view.findViewById(R.id.delete_button)

        override fun bindView(
            item: SearchHistoryItem,
            payloads: List<Any>,
        ) {
            title.text = item.query
            // merges consecutive rows into one rounded card, like ChapterHolder does for chapters
            card.shapeAppearanceModel =
                card.makeContainerShape(
                    item.isTopOfGroup,
                    item.isBottomOfGroup,
                    clipContentTo = frontView,
                )
            deleteButton.setOnClickListener { item.onDeleteClicked(item.query) }
        }

        override fun unbindView(item: SearchHistoryItem) {
            title.text = null
            deleteButton.setOnClickListener(null)
        }
    }
}
