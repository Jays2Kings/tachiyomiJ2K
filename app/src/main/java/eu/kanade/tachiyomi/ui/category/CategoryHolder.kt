package eu.kanade.tachiyomi.ui.category

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.category.CategoryPresenter.Companion.CREATE_CATEGORY_ORDER
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visible
import kotlinx.android.synthetic.main.categories_item.*

/**
 * Holder used to display category items.
 *
 * @param view The view used by category items.
 * @param adapter The adapter containing this holder.
 */
class CategoryHolder(view: View, val adapter: CategoryAdapter) : BaseFlexibleViewHolder(view, adapter) {

    init {
        edit_button.setOnClickListener {
            submitChanges()
        }
    }

    var createCategory = false
    private var regularDrawable: Drawable? = null

    /**
     * Binds this holder with the given category.
     *
     * @param category The category to bind.
     */
    fun bind(category: Category) {
        // Set capitalized title.
        title.text = category.name.capitalize()
        edit_text.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitChanges()
            }
            true
        }
        createCategory = category.order == CREATE_CATEGORY_ORDER
        if (createCategory) {
            title.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_color_hint))
            regularDrawable = ContextCompat.getDrawable(
                itemView.context,
                R.drawable
                    .ic_add_24dp
            )
            image.gone()
            edit_button.setImageDrawable(null)
            edit_text.setText("")
            edit_text.hint = title.text
        } else {
            title.setTextColor(ContextCompat.getColor(itemView.context, R.color.textColorPrimary))
            regularDrawable = ContextCompat.getDrawable(
                itemView.context,
                R.drawable
                    .ic_drag_handle_24dp
            )
            image.visible()
            edit_text.setText(title.text)
        }
    }

    fun isEditing(editing: Boolean) {
        itemView.isActivated = editing
        title.visibility = if (editing) View.INVISIBLE else View.VISIBLE
        edit_text.visibility = if (!editing) View.INVISIBLE else View.VISIBLE
        if (editing) {
            edit_text.inputType = InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            edit_text.requestFocus()
            edit_text.selectAll()
            edit_button.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.ic_check_24dp))
            edit_button.drawable.mutate().setTint(itemView.context.getResourceColor(R.attr.colorAccent))
            showKeyboard()
            if (!createCategory) {
                reorder.setImageDrawable(
                    ContextCompat.getDrawable(
                        itemView.context,
                        R.drawable.ic_delete_24dp
                    )
                )
                reorder.setOnClickListener {
                    adapter.categoryItemListener.onItemDelete(adapterPosition)
                }
            }
        } else {
            if (!createCategory) {
                setDragHandleView(reorder)
                edit_button.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.ic_edit_24dp))
            } else {
                edit_button.setImageDrawable(null)
                reorder.setOnTouchListener { _, _ -> true }
            }
            edit_text.clearFocus()
            edit_button.drawable?.mutate()?.setTint(
                ContextCompat.getColor(
                    itemView.context,
                    R
                        .color.gray_button
                )
            )
            reorder.setImageDrawable(regularDrawable)
        }
    }

    private fun submitChanges() {
        if (edit_text.visibility == View.VISIBLE) {
            if (adapter.categoryItemListener
                .onCategoryRename(adapterPosition, edit_text.text.toString())
            ) {
                isEditing(false)
                edit_text.inputType = InputType.TYPE_NULL
                if (!createCategory)
                    title.text = edit_text.text.toString()
            }
        } else {
            itemView.performClick()
        }
    }

    private fun showKeyboard() {
        val inputMethodManager: InputMethodManager =
            itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(
            edit_text,
            WindowManager.LayoutParams
                .SOFT_INPUT_ADJUST_PAN
        )
    }

    /**
     * Called when an item is released.
     *
     * @param position The position of the released item.
     */
    override fun onItemReleased(position: Int) {
        super.onItemReleased(position)
        adapter.categoryItemListener.onItemReleased(position)
    }
}
