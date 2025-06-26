package eu.kanade.tachiyomi.ui.source.browse.repos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.CategoriesControllerBinding
import eu.kanade.tachiyomi.extension.model.ExtensionRepo
import eu.kanade.tachiyomi.ui.base.SmallToolbarInterface
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.liftAppbarWith
import eu.kanade.tachiyomi.util.view.snack

/**
 * Controller to manage the repos for the user's extensions.
 */
class RepoController(
    bundle: Bundle? = null,
) : BaseController<CategoriesControllerBinding>(bundle),
    FlexibleAdapter.OnItemClickListener,
    SmallToolbarInterface,
    RepoAdapter.RepoItemListener {
    constructor(repoUrl: String) : this(
        Bundle().apply {
            putString(REPO_URL, repoUrl)
        },
    ) {
        presenter.createRepo(repoUrl)
    }

    /**
     * Adapter containing repo items.
     */
    private var adapter: RepoAdapter? = null

    /**
     * Undo helper used for restoring a deleted repo.
     */
    private var snack: Snackbar? = null

    /**
     * Creates the presenter for this controller. Not to be manually called.
     */
    private val presenter = RepoPresenter(this)

    /**
     * Returns the toolbar title to show when this controller is attached.
     */
    override fun getTitle(): String? = resources?.getString(R.string.extension_repos)

    override fun createBinding(inflater: LayoutInflater) = CategoriesControllerBinding.inflate(inflater)

    /**
     * Called after view inflation. Used to initialize the view.
     *
     * @param view The view of this controller.
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        liftAppbarWith(binding.recycler, true)

        adapter = RepoAdapter(this@RepoController)
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.setHasFixedSize(true)
        binding.recycler.adapter = adapter
        adapter?.isPermanentDelete = false

        presenter.getRepos()
    }

    /**
     * Called when the view is being destroyed. Used to release references and remove callbacks.
     *
     * @param view The view of this controller.
     */
    override fun onDestroyView(view: View) {
        // Manually call callback to delete repos if required
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
     * Called from the presenter when the repos are updated.
     *
     * @param repos The list of extension repositories.
     */
    fun setRepos(repos: List<ExtensionRepo>) {
        val items = mutableListOf<AbstractFlexibleItem<*>>()
        items.add(InfoRepoMessage())

        items.add(RepoItem(null, RepoItem.Type.CREATE))

        items.addAll(repos.map { RepoItem(it, RepoItem.Type.DATA) })

        adapter?.updateDataSet(items)
    }

    /**
     * Called when an item in the list is clicked.
     *
     * @param position The position of the clicked item.
     * @return true if this click should enable selection mode.
     */
    override fun onItemClick(
        view: View?,
        position: Int,
    ): Boolean {
        adapter?.resetEditing(position)
        return true
    }

    override fun onLogoClick(position: Int) {
        val item = adapter?.getItem(position) as? RepoItem ?: return
        val repo = item.repo ?: return

        if (isNotOnline()) return

        val repoUrl = presenter.getRepoUrl(repo)
        if (repoUrl.isBlank()) {
            activity?.toast(R.string.url_not_set_click_again)
        } else {
            activity?.openInBrowser(repoUrl.toUri())
        }
    }

    private fun isNotOnline(showSnackbar: Boolean = true): Boolean {
        if (activity == null || !activity!!.isOnline()) {
            if (showSnackbar) view?.snack(R.string.no_network_connection)
            return true
        }
        return false
    }

    override fun onRepoRename(
        position: Int,
        newName: String,
    ): Boolean {
        val item = adapter?.getItem(position) as? RepoItem ?: return false

        if (newName.isBlank()) {
            activity?.toast(R.string.repo_cannot_be_blank)
            return false
        }

        if (item.type == RepoItem.Type.CREATE) {
            return presenter.createRepo(newName)
        }

        val repo = item.repo ?: return false
        return presenter.renameRepo(repo.baseUrl, newName)
    }

    override fun onItemDelete(position: Int) {
        val item = adapter?.getItem(position) as? RepoItem ?: return
        val repo = item.repo ?: return

        deleteRepo(position)
    }

    private fun deleteRepo(position: Int) {
        val item = adapter?.getItem(position) as? RepoItem ?: return

        adapter?.removeItem(position)
        snack =
            view?.snack(R.string.snack_repo_deleted, Snackbar.LENGTH_INDEFINITE) {
                var undoing = false
                setAction(R.string.undo) {
                    adapter?.restoreDeletedItems()
                    undoing = true
                }
                addCallback(
                    object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(
                            transientBottomBar: Snackbar?,
                            event: Int,
                        ) {
                            super.onDismissed(transientBottomBar, event)
                            if (!undoing) confirmDelete()
                        }
                    },
                )
            }
        (activity as? MainActivity)?.setUndoSnackBar(snack)
    }

    fun confirmDelete() {
        val adapter = adapter ?: return
        val deletedItem = adapter.deletedItems.firstOrNull() as? RepoItem ?: return
        val repo = deletedItem.repo ?: return

        presenter.deleteRepo(repo.baseUrl)
        adapter.confirmDeletion()
        snack = null
    }

    /**
     * Called from the presenter when a repo already exists.
     */
    fun onRepoExistsError() {
        activity?.toast(R.string.error_repo_exists)
    }

    /**
     * Called from the presenter when an invalid repo is made
     */
    fun onRepoInvalidNameError() {
        activity?.toast(R.string.invalid_repo_name)
    }

    /**
     * Called from the presenter when a repo with duplicate fingerprint is detected
     */
    fun onRepoDuplicateFingerprintError(
        existingRepoName: String,
        newRepoName: String,
    ) {
        activity?.toast(activity?.getString(R.string.duplicate_fingerprint_error, existingRepoName, newRepoName))
    }

    /**
     * Called from the presenter when a generic error occurs
     */
    fun onGenericError() {
        activity?.toast(R.string.unknown_error)
    }

    companion object {
        const val REPO_URL = "repo_url"
    }
}
