package eu.kanade.tachiyomi.ui.setting.database

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.forEach
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.Payload
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ClearDatabaseControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.setCustomTitleAndMessage
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.fullAppBarHeight
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.snack
import kotlin.math.max
import kotlin.math.roundToInt

class ClearDatabaseController :
    BaseCoroutineController<ClearDatabaseControllerBinding, ClearDatabasePresenter>(),
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnUpdateListener {

    private var adapter: FlexibleAdapter<ClearDatabaseSourceItem>? = null
    private var menu: Menu? = null

    override fun createBinding(inflater: LayoutInflater): ClearDatabaseControllerBinding {
        return ClearDatabaseControllerBinding.inflate(inflater)
    }

    override val presenter = ClearDatabasePresenter()

    override fun getTitle(): String? {
        return activity?.getString(R.string.clear_database)
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        adapter = FlexibleAdapter<ClearDatabaseSourceItem>(null, this, true)
        binding.recycler.adapter = adapter
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.setHasFixedSize(true)
        adapter?.fastScroller = binding.fastScroller
        val fabBaseMarginBottom = binding.fab.marginBottom

        scrollViewWith(
            binding.recycler,
            true,
            afterInsets = { insets ->
                if (binding.fastScroller.marginBottom != insets.getInsets(systemBars()).bottom + fabBaseMarginBottom) {
                    binding.fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = insets.getInsets(systemBars()).bottom + fabBaseMarginBottom
                    }
                }
                binding.recycler.updatePadding(
                    bottom = if (adapter?.selectedItemCount ?: 0 > 0) {
                        binding.fab.height + binding.fab.marginBottom
                    } else {
                        insets.getInsets(systemBars()).bottom
                    },
                )
                binding.fastScroller.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = insets.getInsets(systemBars()).bottom
                }
                binding.emptyView.updatePadding(
                    top = (fullAppBarHeight ?: 0) + (activityBinding?.appBar?.paddingTop ?: 0),
                    bottom = insets.getInsets(systemBars()).bottom,
                )
            },
        )
        binding.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            fun updateFastScrollMargins() {
                if (binding.fastScroller.isFastScrolling) return
                val activityBinding = activityBinding ?: return
                val bigToolbarHeight = fullAppBarHeight ?: return
                val value = max(
                    0,
                    bigToolbarHeight + activityBinding.appBar.y.roundToInt(),
                ) + activityBinding.appBar.paddingTop
                if (value != binding.fastScroller.marginTop) {
                    binding.fastScroller.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        topMargin = value
                    }
                }
            }
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateFastScrollMargins()
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                updateFastScrollMargins()
            }
        },
        )
        binding.fab.isInvisible = true
        binding.fab.setOnClickListener {
            if (adapter!!.selectedItemCount > 0) {
                val ctrl = ClearDatabaseSourcesDialog()
                ctrl.targetController = this
                ctrl.showDialog(router)
            }
        }
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.clear_database, menu)
        this.menu = menu
        val id = when (presenter.sortBy) {
            ClearDatabasePresenter.SortSources.ALPHA -> R.id.action_sort_alpha
            ClearDatabasePresenter.SortSources.MOST_ENTRIES -> R.id.action_sort_largest
        }
        menu.findItem(id).isChecked = true
        menu.forEach { menuItem -> menuItem.isVisible = (adapter?.itemCount ?: 0) > 0 }
        setUninstalledMenuItem()
    }

    private fun setUninstalledMenuItem() {
        menu?.findItem(R.id.action_select_uninstalled)?.isVisible =
            presenter.hasStubSources && adapter?.itemCount ?: 0 > 0
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val adapter = adapter ?: return false
        when (item.itemId) {
            R.id.action_select_all -> adapter.selectAll()
            R.id.action_select_inverse -> {
                adapter.currentItems.forEachIndexed { index, _ ->
                    adapter.toggleSelection(index)
                }
            }
            R.id.action_sort_alpha, R.id.action_sort_largest -> {
                val sortBy = when (item.itemId) {
                    R.id.action_sort_alpha -> ClearDatabasePresenter.SortSources.ALPHA
                    R.id.action_sort_largest -> ClearDatabasePresenter.SortSources.MOST_ENTRIES
                    else -> return false
                }
                presenter.reorder(sortBy)
                item.isChecked = true
                adapter.clearSelection()
            }
            R.id.action_select_uninstalled -> {
                val currentSelection = adapter.selectedPositionsAsSet
                val uninstalledSelection = (0 until adapter.itemCount)
                    .filter { adapter.getItem(it)?.isStub == true }
                currentSelection.clear()
                currentSelection.addAll(uninstalledSelection)
            }
        }
        adapter.notifyItemRangeChanged(0, adapter.itemCount, Payload.SELECTION)
        updateFab()
        return super.onOptionsItemSelected(item)
    }

    override fun onUpdateEmptyView(size: Int) {
        if (size > 0) {
            binding.emptyView.hide()
        } else {
            binding.emptyView.show(
                R.drawable.ic_book_24dp,
                R.string.database_clean,
            )
        }
        menu?.forEach { menuItem -> menuItem.isVisible = size > 0 }
        setUninstalledMenuItem()
    }

    override fun onItemClick(view: View?, position: Int): Boolean {
        val adapter = adapter ?: return false
        adapter.toggleSelection(position)
        adapter.notifyItemChanged(position, Payload.SELECTION)
        updateFab()
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val size = adapter?.itemCount ?: 0
        menu.forEach { menuItem -> menuItem.isVisible = size > 0 }
        setUninstalledMenuItem()
    }

    fun setItems(items: List<ClearDatabaseSourceItem>) {
        adapter?.updateDataSet(items)
        menu?.findItem(R.id.action_select_uninstalled)?.isVisible = presenter.hasStubSources
    }

    private fun updateFab() {
        val adapter = adapter ?: return
        if (adapter.selectedItemCount > 0) {
            binding.fab.show()
        } else {
            binding.fab.hide()
        }
        val bottomPadding = if (adapter.selectedItemCount > 0) {
            binding.fab.height + binding.fab.marginBottom
        } else {
            binding.root.rootWindowInsetsCompat?.getInsets(systemBars())?.bottom ?: 0
        }
        if (bottomPadding != binding.recycler.paddingBottom) {
            binding.recycler.updatePadding(bottom = bottomPadding)
        }
    }

    class ClearDatabaseSourcesDialog : DialogController() {
        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val item = arrayOf(activity!!.getString(R.string.clear_db_exclude_read))
            val selected = booleanArrayOf(true)
            return activity!!.materialAlertDialog()
                .setCustomTitleAndMessage(0, activity!!.getString(R.string.clear_database_confirmation))
                .setMultiChoiceItems(item, selected) { _, which, checked ->
                    selected[which] = checked
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    (targetController as? ClearDatabaseController)?.clearDatabaseForSelectedSources(selected.last())
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearDatabaseForSelectedSources(keepReadManga: Boolean) {
        val adapter = adapter ?: return
        val selectedSourceIds = adapter.selectedPositions.mapNotNull { position ->
            adapter.getItem(position)?.source?.id
        }
        presenter.clearDatabaseForSourceIds(selectedSourceIds, keepReadManga)
        binding.fab.isVisible = false
        adapter.clearSelection()
        adapter.notifyDataSetChanged()
        view?.snack(R.string.clear_database_completed)
    }
}
