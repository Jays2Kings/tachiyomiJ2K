package eu.kanade.tachiyomi.ui.library.category

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.listeners.OnBindViewHolderListenerImpl
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.marginTop

class CategoryRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    val manager = LinearLayoutManager(context)
    private val fastAdapter: FastAdapter<CategoryItem>
    var onCategoryClicked: (Int) -> Unit = { _ -> }
    var onShowAllClicked: (Boolean) -> Unit = { }
    private val itemAdapter = ItemAdapter<CategoryItem>()
    var selectedCategory: Int = 0

    init {
        fastAdapter = FastAdapter.with(itemAdapter)
        layoutManager = manager
        adapter = fastAdapter
    }

    fun setCategories(items: List<Category>) {
        itemAdapter.set(items.map(::CategoryItem))
        fastAdapter.onBindViewHolderListener =
            (
                object : OnBindViewHolderListenerImpl<CategoryItem>() {
                    override fun onBindViewHolder(
                        viewHolder: ViewHolder,
                        position: Int,
                        payloads: List<Any>
                    ) {
                        super.onBindViewHolder(viewHolder, position, payloads)
                        (viewHolder as? CategoryItem.ViewHolder)?.categoryTitle?.isSelected =
                            selectedCategory == position
                    }
                }
                )
        fastAdapter.onClickListener = { _, _, item, _ ->
            if (item.category.id != -1)
                onCategoryClicked(item.category.order)
            true
        }
    }

    fun scrollToCategory(order: Int) {
        val index = itemAdapter.adapterItems.indexOfFirst { it.category.order == order }
        if (index > -1) {
            manager.scrollToPositionWithOffset(
                index,
                (height - 38.dpToPx) / 2
            )
        }
    }

    fun setCategories(selected: Int) {
        selectedCategory = selected
        for (i in 0..manager.itemCount) {
            (findViewHolderForAdapterPosition(i) as? CategoryItem.ViewHolder)?.categoryTitle?.isSelected =
                selectedCategory == i
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val mainView = (parent.parent.parent as ViewGroup)
        val top = marginTop
        val parent = mainView.measuredHeight - top - 100.dpToPx
        val heightS = if (parent > 0) {
            MeasureSpec.makeMeasureSpec(parent, MeasureSpec.AT_MOST)
        } else {
            heightSpec
        }
        super.onMeasure(widthSpec, heightS)
    }
}
