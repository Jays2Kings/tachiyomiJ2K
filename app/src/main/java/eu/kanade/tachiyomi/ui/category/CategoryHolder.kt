package eu.kanade.tachiyomi.ui.category

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.databinding.CategoriesItemBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.category.CategoryPresenter.Companion.CREATE_CATEGORY_ORDER
import eu.kanade.tachiyomi.util.system.getResourceColor

/**
 * Holder used to display category items.
 *
 * @param view The view used by category items.
 * @param adapter The adapter containing this holder.
 */
class CategoryHolder(view: View, val adapter: CategoryAdapter) : BaseFlexibleViewHolder(view, adapter) {

    private val binding = CategoriesItemBinding.bind(view)
    init {
        binding.editButton.setOnClickListener {
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
        binding.title.text = category.name.capitalize()
        binding.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitChanges()
            }
            true
        }
        createCategory = category.order == CREATE_CATEGORY_ORDER
        if (createCategory) {
            binding.title.setTextColor(ContextCompat.getColor(itemView.context, R.color.material_on_background_disabled))
            regularDrawable = ContextCompat.getDrawable(
                itemView.context,
                R.drawable
                    .ic_add_24dp
            )
            binding.image.isVisible = false
            binding.editButton.setImageDrawable(null)
            binding.editText.setText("")
            binding.editText.hint = binding.title.text
        } else {
            binding.title.setTextColor(itemView.context.getResourceColor(R.attr.colorOnBackground))
            regularDrawable = ContextCompat.getDrawable(
                itemView.context,
                R.drawable
                    .ic_drag_handle_24dp
            )
            binding.image.isVisible = true
            binding.editText.setText(binding.title.text)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun isEditing(editing: Boolean) {
        itemView.isActivated = editing
        binding.title.visibility = if (editing) View.INVISIBLE else View.VISIBLE
        binding.editText.visibility = if (!editing) View.INVISIBLE else View.VISIBLE
        if (editing) {
            binding.editText.requestFocus()
            binding.editText.selectAll()
            binding.editButton.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.ic_check_24dp))
            binding.editButton.drawable.mutate().setTint(itemView.context.getResourceColor(R.attr.colorSecondary))
            showKeyboard()
            if (!createCategory) {
                binding.reorder.setImageDrawable(
                    ContextCompat.getDrawable(
                        itemView.context,
                        R.drawable.ic_delete_24dp
                    )
                )
                binding.reorder.setOnClickListener {
                    adapter.categoryItemListener.onItemDelete(flexibleAdapterPosition)
                }
            }
        } else {
            if (!createCategory) {
                setDragHandleView(binding.reorder)
                binding.editButton.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.ic_edit_24dp))
            } else {
                binding.editButton.setImageDrawable(null)
                binding.reorder.setOnTouchListener { _, _ -> true }
            }
            binding.editText.clearFocus()
            binding.editButton.drawable?.mutate()?.setTint(
                ContextCompat.getColor(
                    itemView.context,
                    R
                        .color.gray_button
                )
            )
            binding.reorder.setImageDrawable(regularDrawable)
        }
    }

    private fun submitChanges() {
        if (binding.editText.visibility == View.VISIBLE) {
            if (adapter.categoryItemListener
                .onCategoryRename(flexibleAdapterPosition, binding.editText.text.toString())
            ) {
                isEditing(false)
                if (!createCategory) {
                    binding.title.text = binding.editText.text.toString()
                }
            }
        } else {
            itemView.performClick()
        }
    }

    private fun showKeyboard() {
        val inputMethodManager: InputMethodManager =
            itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(
            binding.editText,
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
