package eu.kanade.tachiyomi.ui.source.browse.repos

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.CategoriesItemBinding
import eu.kanade.tachiyomi.extension.model.ExtensionRepo
import eu.kanade.tachiyomi.util.system.getResourceColor

/**
 * Holder used to display repo items.
 *
 * @param view The view used by repo items.
 * @param adapter The adapter containing this holder.
 */
class RepoHolder(
    view: View,
    val adapter: RepoAdapter,
) : FlexibleViewHolder(view, adapter) {
    private val binding = CategoriesItemBinding.bind(view)

    init {
        binding.editButton.setOnClickListener {
            submitChanges()
        }
    }

    private var regularDrawable: Drawable? = null

    /**
     * Binds this holder with the given repo item.
     *
     * @param item The repo item to bind.
     */
    fun bind(item: RepoItem) {
        binding.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitChanges()
            }
            true
        }

        when (item.type) {
            RepoItem.Type.CREATE -> bindCreateItem()
            RepoItem.Type.DATA -> bindDataItem(item.repo)
        }
    }

    private fun bindCreateItem() {
        binding.title.text = itemView.context.getString(R.string.action_add_repo)
        binding.title.setTextColor(
            ContextCompat.getColor(itemView.context, R.color.material_on_background_disabled),
        )
        binding.image.isVisible = true
        regularDrawable = ContextCompat.getDrawable(itemView.context, R.drawable.ic_add_24dp)
        binding.image.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.ic_add_24dp))
        binding.reorder.isInvisible = true
        binding.editButton.setImageDrawable(null)
        binding.editText.setText("")
        binding.editText.hint = ""
    }

    private fun bindDataItem(repo: ExtensionRepo?) {
        if (repo == null) return

        // Set repository name as title
        binding.title.text = repo.name
        binding.title.maxLines = 2
        binding.title.setTextColor(itemView.context.getResourceColor(R.attr.colorOnBackground))

        // Show repository icon
        binding.image.isVisible = true
        regularDrawable = ContextCompat.getDrawable(itemView.context, R.drawable.ic_github_24dp)
        binding.image.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.ic_github_24dp))
        binding.reorder.setImageDrawable(null)
        // Set click listener for the logo
        binding.reorder.setOnClickListener {
            adapter.repoItemListener.onLogoClick(flexibleAdapterPosition)
        }

        // Set the edit text to the base URL for editing
        binding.editText.setText(repo.baseUrl + "/index.min.json")
    }

    @SuppressLint("ClickableViewAccessibility")
    fun isEditing(editing: Boolean) {
        val item = adapter.getItem(flexibleAdapterPosition) as? RepoItem ?: return
        val isCreateItem = item.type == RepoItem.Type.CREATE

        itemView.isActivated = editing
        binding.title.isInvisible = editing
        binding.editText.isInvisible = !editing

        if (editing) {
            binding.editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
            binding.editText.requestFocus()
            binding.editText.selectAll()
            binding.editButton.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.ic_check_24dp))
            binding.editButton.drawable
                ?.mutate()
                ?.setTint(itemView.context.getResourceColor(R.attr.colorSecondary))
            showKeyboard()

            if (!isCreateItem) {
                binding.reorder.setImageDrawable(
                    ContextCompat.getDrawable(itemView.context, R.drawable.ic_delete_24dp),
                )
                binding.reorder.setOnClickListener {
                    adapter.repoItemListener.onItemDelete(flexibleAdapterPosition)
                    hideKeyboard()
                }
            }
        } else {
            binding.editText.clearFocus()
            binding.editButton.drawable?.mutate()?.setTint(
                ContextCompat.getColor(itemView.context, R.color.gray_button),
            )

            if (!isCreateItem) {
                binding.reorder.setOnClickListener {
                    adapter.repoItemListener.onLogoClick(flexibleAdapterPosition)
                }
                binding.editButton.setImageDrawable(
                    ContextCompat.getDrawable(itemView.context, R.drawable.ic_edit_24dp),
                )
                binding.image.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.ic_github_24dp))
                binding.reorder.setImageDrawable(null)
                binding.reorder.isVisible = true
            } else {
                binding.editButton.setImageDrawable(null)
                binding.reorder.setOnTouchListener { _, _ -> true }
                binding.image.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.ic_add_24dp))
                binding.reorder.isInvisible = true
            }
        }
    }

    private fun submitChanges() {
        if (binding.editText.isVisible) {
            if (adapter.repoItemListener.onRepoRename(flexibleAdapterPosition, binding.editText.text.toString())) {
                isEditing(false)
            }
        } else {
            itemView.performClick()
        }
        hideKeyboard()
    }

    private fun showKeyboard() {
        val inputMethodManager = itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(binding.editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val inputMethodManager = itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.editText.windowToken, 0)
    }
}
