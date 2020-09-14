package eu.kanade.tachiyomi.ui.category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.category.CategoryPresenter.Companion.CREATE_CATEGORY_ORDER
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.liftAppbarWith
import eu.kanade.tachiyomi.util.view.snack
import kotlinx.android.synthetic.main.categories_controller.*

/**
 * Controller to manage the categories for the users' library.
 */
class CategoryController(bundle: Bundle? = null) :
    BaseController(bundle),
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemMoveListener,
    CategoryAdapter.CategoryItemListener {

    /**
     * Adapter containing category items.
     */
    private var adapter: CategoryAdapter? = null

    /**
     * Undo helper used for restoring a deleted category.
     */
    private var snack: Snackbar? = null

    /**
     * Creates the presenter for this controller. Not to be manually called.
     */
    private val presenter = CategoryPresenter(this)

    /**
     * Returns the toolbar title to show when this controller is attached.
     */
    override fun getTitle(): String? {
        return resources?.getString(R.string.edit_categories)
    }

    /**
     * Returns the view of this controller.
     *
     * @param inflater The layout inflater to create the view from XML.
     * @param container The parent view for this one.
     */
    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.categories_controller, container, false)
    }

    /**
     * Called after view inflation. Used to initialize the view.
     *
     * @param view The view of this controller.
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        liftAppbarWith(recycler)

        adapter = CategoryAdapter(this@CategoryController)
        recycler.layoutManager = LinearLayoutManager(view.context)
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter
        adapter?.isHandleDragEnabled = true
        adapter?.isPermanentDelete = false

        presenter.getCategories()
    }

    /**
     * Called when the view is being destroyed. Used to release references and remove callbacks.
     *
     * @param view The view of this controller.
     */
    override fun onDestroyView(view: View) {
        // Manually call callback to delete categories if required
        snack?.dismiss()
        view.clearFocus()
        confirmDelete()
        snack = null
        adapter = null
        super.onDestroyView(view)
    }

    override fun handleBack(): Boolean {
        view?.clearFocus()
        confirmDelete()
        return super.handleBack()
    }

    /**
     * Called from the presenter when the categories are updated.
     *
     * @param categories The new list of categories to display.
     */
    fun setCategories(categories: List<CategoryItem>) {
        adapter?.updateDataSet(categories)
    }

    /**
     * Called when an item in the list is clicked.
     *
     * @param position The position of the clicked item.
     * @return true if this click should enable selection mode.
     */
    override fun onItemClick(view: View?, position: Int): Boolean {
        adapter?.resetEditing(position)
        return true
    }

    override fun onCategoryRename(position: Int, newName: String): Boolean {
        val category = adapter?.getItem(position)?.category ?: return false
        if (category.order == CREATE_CATEGORY_ORDER)
            return (presenter.createCategory(newName))
        return (presenter.renameCategory(category, newName))
    }

    override fun onItemDelete(position: Int) {
        MaterialDialog(activity!!)
            .title(R.string.confirm_category_deletion)
            .message(R.string.confirm_category_deletion_message)
            .positiveButton(R.string.delete) {
                deleteCategory(position)
            }
            .negativeButton(android.R.string.no)
            .show()
    }

    private fun deleteCategory(position: Int) {
        confirmDelete()
        adapter?.removeItem(position)
        snack =
            view?.snack(R.string.category_deleted, Snackbar.LENGTH_INDEFINITE) {
                var undoing = false
                setAction(R.string.undo) {
                    adapter?.restoreDeletedItems()
                    undoing = true
                }
                addCallback(
                    object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            if (!undoing) confirmDelete()
                        }
                    }
                )
            }
        (activity as? MainActivity)?.setUndoSnackBar(snack)
    }

    /**
     * Called when an item is released from a drag.
     *
     * @param position The position of the released item.
     */
    override fun onItemReleased(position: Int) {
        val adapter = adapter ?: return
        val categories = (0 until adapter.itemCount).mapNotNull { adapter.getItem(it)?.category }
        presenter.reorderCategories(categories)
    }

    fun confirmDelete() {
        val adapter = adapter ?: return
        presenter.deleteCategory(adapter.deletedItems.map { it.category }.firstOrNull())
        adapter.confirmDeletion()
        snack = null
    }

    override fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {}

    override fun onItemMove(fromPosition: Int, toPosition: Int) {}

    override fun shouldMoveItem(fromPosition: Int, toPosition: Int): Boolean {
        return toPosition > 0
    }
    /**
     * Called from the presenter when a category with the given name already exists.
     */
    fun onCategoryExistsError() {
        activity?.toast(R.string.category_with_name_exists)
    }
}
