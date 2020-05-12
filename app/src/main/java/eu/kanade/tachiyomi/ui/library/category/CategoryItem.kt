package eu.kanade.tachiyomi.ui.library.category

import android.view.View
import android.widget.TextView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category

class CategoryItem(val category: Category) : AbstractItem<FastAdapter.ViewHolder<CategoryItem>>() {

    /** defines the type defining this item. must be unique. preferably an id */
    override val type: Int = R.id.category_text

    /** defines the layout which will be used for this item in the list */
    override val layoutRes: Int = R.layout.catergory_text_view

    override var identifier = category.id?.toLong() ?: -1L

    override fun getViewHolder(v: View): FastAdapter.ViewHolder<CategoryItem> {
        return ViewHolder(v)
    }

    class ViewHolder(view: View) : FastAdapter.ViewHolder<CategoryItem>(view) {
        val categoryTitle: TextView = view.findViewById(R.id.category_text)

        override fun bindView(item: CategoryItem, payloads: List<Any>) {
            categoryTitle.text = item.category.name
        }

        override fun unbindView(item: CategoryItem) {
            categoryTitle.text = null
        }
    }
}
