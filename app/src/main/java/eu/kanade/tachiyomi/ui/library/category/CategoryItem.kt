package eu.kanade.tachiyomi.ui.library.category

import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoryItem(val category: Category) : AbstractItem<FastAdapter.ViewHolder<CategoryItem>>() {

    /** defines the type defining this item. must be unique. preferably an id */
    override val type: Int = if (category.id == -1) {
        R.id.auto_checkbox
    } else {
        R.id.category_text
    }

    /** defines the layout which will be used for this item in the list */
    override val layoutRes: Int = if (category.id == -1) {
        R.layout.auto_ext_checkbox
    } else {
        R.layout.catergory_text_view
    }

    override var identifier = category.id?.toLong() ?: -1L

    override fun getViewHolder(v: View): FastAdapter.ViewHolder<CategoryItem> {
        return if (category.id == -1) {
            ShowAllViewHolder(Injekt.get<PreferencesHelper>().showAllCategories(), v)
        } else {
            ViewHolder(v)
        }
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

    class ShowAllViewHolder(val pref: Preference<Boolean>, view: View) :
        FastAdapter.ViewHolder<CategoryItem>(view) {

        val checkbox: CheckBox? = view.findViewById(R.id.auto_checkbox)

        init {
            checkbox?.setText(R.string.show_all_categories)
        }

        override fun bindView(item: CategoryItem, payloads: List<Any>) {
            checkbox?.isChecked = pref.get()
        }
        override fun unbindView(item: CategoryItem) {}
    }
}
