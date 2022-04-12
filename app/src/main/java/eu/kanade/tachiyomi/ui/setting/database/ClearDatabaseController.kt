package eu.kanade.tachiyomi.ui.setting.database

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.*
import androidx.core.view.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.Payload
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ClearDatabaseControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.FabController
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.liftAppbarWith
import eu.kanade.tachiyomi.util.view.marginBottom

class ClearDatabaseController :
    NucleusController<ClearDatabaseControllerBinding, ClearDatabasePresenter>(),
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnUpdateListener,
    FabController {

    private var recycler: RecyclerView? = null
    private var adapter: FlexibleAdapter<ClearDatabaseSourceItem>? = null

    private var menu: Menu? = null

    private var actionFab: ExtendedFloatingActionButton? = null
    private var actionFabScrollListener: RecyclerView.OnScrollListener? = null

    init {
        setHasOptionsMenu(true)
    }

    override fun createBinding(inflater: LayoutInflater): ClearDatabaseControllerBinding {
        return ClearDatabaseControllerBinding.inflate(inflater)
    }

    override fun createPresenter(): ClearDatabasePresenter {
        return ClearDatabasePresenter()
    }

    override fun getTitle(): String? {
        return activity?.getString(R.string.clear_database)
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        liftAppbarWith(binding.recycler, true)

        adapter = FlexibleAdapter<ClearDatabaseSourceItem>(null, this, true)
        binding.recycler.adapter = adapter
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.setHasFixedSize(true)
        adapter?.fastScroller = binding.fastScroller
        recycler = binding.recycler
        val fabBaseMarginBottom = binding.fab.marginBottom
        binding.recycler.doOnApplyWindowInsetsCompat { v, insets, _ ->

            binding.fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = fabBaseMarginBottom + insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            }
            v.post {
                // offset the binding.recycler by the binding.fab's inset + some inset on top
                v.updatePaddingRelative(
                    bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom + binding.fab.marginBottom +
                        (binding.fab.height)
                )
            }
        }
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
        inflater.inflate(R.menu.generic_selection, menu)
        this.menu = menu
        menu.forEach { menuItem -> menuItem.isVisible = (adapter?.itemCount ?: 0) > 0 }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val adapter = adapter ?: return false
        when (item.itemId) {
            R.id.action_select_all -> adapter.selectAll()
            R.id.action_select_inverse -> {
                val currentSelection = adapter.selectedPositionsAsSet
                val invertedSelection = (0..adapter.itemCount)
                    .filterNot { currentSelection.contains(it) }
                currentSelection.clear()
                currentSelection.addAll(invertedSelection)
            }
        }
        adapter.notifyItemRangeChanged(0, adapter.itemCount, Payload.SELECTION)
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
    }

    override fun onItemClick(view: View?, position: Int): Boolean {
        val adapter = adapter ?: return false
        adapter.toggleSelection(position)
        adapter.notifyItemChanged(position, Payload.SELECTION)
        return true
    }

    fun setItems(items: List<ClearDatabaseSourceItem>) {
        adapter?.updateDataSet(items)
    }

    class ClearDatabaseSourcesDialog : DialogController() {
        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            return MaterialAlertDialogBuilder(activity!!)
                .setMessage(R.string.clear_database_confirmation)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    (targetController as? ClearDatabaseController)?.clearDatabaseForSelectedSources()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearDatabaseForSelectedSources() {
        val adapter = adapter ?: return
        val selectedSourceIds = adapter.selectedPositions.mapNotNull { position ->
            adapter.getItem(position)?.source?.id
        }
        presenter.clearDatabaseForSourceIds(selectedSourceIds)
        adapter.clearSelection()
        adapter.notifyDataSetChanged()
        activity?.toast(R.string.clear_database_completed)
    }
}
