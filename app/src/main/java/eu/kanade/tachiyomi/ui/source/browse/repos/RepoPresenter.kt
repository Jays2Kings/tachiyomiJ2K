package eu.kanade.tachiyomi.ui.source.browse.repos

import eu.kanade.tachiyomi.extension.interactor.CreateExtensionRepo
import eu.kanade.tachiyomi.extension.interactor.DeleteExtensionRepo
import eu.kanade.tachiyomi.extension.interactor.GetExtensionRepo
import eu.kanade.tachiyomi.extension.interactor.UpdateExtensionRepo
import eu.kanade.tachiyomi.extension.model.ExtensionRepo
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [RepoController]. Used to manage the repos for the extensions.
 */
class RepoPresenter(
    private val controller: RepoController,
    private val getExtensionRepo: GetExtensionRepo = Injekt.get(),
    private val createExtensionRepo: CreateExtensionRepo = Injekt.get(),
    private val deleteExtensionRepo: DeleteExtensionRepo = Injekt.get(),
    private val updateExtensionRepo: UpdateExtensionRepo = Injekt.get(),
) : BaseCoroutinePresenter<RepoController>() {
    /**
     * Called when the presenter is created.
     * Subscribes to the flow of repositories and updates the UI when changes occur.
     */
    fun getRepos() {
        getExtensionRepo
            .subscribeAll()
            .onEach { repos ->
                withContext(Dispatchers.Main) {
                    controller.setRepos(repos)
                }
            }.catch { error ->
                Timber.e(error, "Error while subscribing to repos")
            }.launchIn(presenterScope)
    }

    /**
     * Returns the website URL for a repository.
     * Uses the website field from ExtensionRepo, or falls back to GitHub URL parsing for compatibility.
     */
    fun getRepoUrl(repo: ExtensionRepo): String {
        if (repo.website.isNotBlank()) {
            return repo.website
        }

        // Fallback for compatibility with old URLs
        return githubRepoRegex
            .find(repo.baseUrl)
            ?.let {
                val (user, repoName) = it.destructured
                "https://github.com/$user/$repoName"
            } ?: repo.baseUrl
    }

    /**
     * Creates and adds a new repo to the database.
     *
     * @param repoUrl The URL of the repo to create (ending with /index.min.json).
     */
    fun createRepo(repoUrl: String): Boolean {
        if (!repoUrl.matches(repoRegex)) {
            controller.onRepoInvalidNameError()
            return false
        }

        presenterScope.launch {
            when (val result = createExtensionRepo.await(repoUrl)) {
                is CreateExtensionRepo.Result.Success -> {
                    // The flow will automatically update the UI, but ensure it does so immediately.
                    // Collect the latest list and update the controller.
                    getExtensionRepo.subscribeAll().firstOrNull()?.let { latestRepos ->
                        withContext(Dispatchers.Main) {
                            // Ensure UI update is on the main thread
                            controller.setRepos(latestRepos)
                        }
                    }
                }
                is CreateExtensionRepo.Result.InvalidUrl -> {
                    controller.onRepoInvalidNameError()
                }
                is CreateExtensionRepo.Result.RepoAlreadyExists -> {
                    controller.onRepoExistsError()
                }
                is CreateExtensionRepo.Result.DuplicateFingerprint -> {
                    controller.onRepoDuplicateFingerprintError(
                        result.existingRepo.name,
                        result.newRepo.name,
                    )
                }
                is CreateExtensionRepo.Result.Error -> {
                    controller.onGenericError()
                }
            }
        }
        return true
    }

    /**
     * Deletes the repo from the database.
     *
     * @param baseUrl The base URL of the repo to delete.
     */
    fun deleteRepo(baseUrl: String?) {
        val safeBaseUrl = baseUrl ?: return
        presenterScope.launch {
            deleteExtensionRepo.await(safeBaseUrl)
            // The flow will automatically update the UI
        }
    }

    /**
     * Renames a repo by deleting the old one and creating a new one.
     *
     * @param oldBaseUrl The base URL of the repo to rename.
     * @param newRepoUrl The new URL of the repo (ending with /index.min.json).
     */
    fun renameRepo(
        oldBaseUrl: String,
        newRepoUrl: String,
    ): Boolean {
        if (!newRepoUrl.matches(repoRegex)) {
            controller.onRepoInvalidNameError()
            return false
        }

        presenterScope.launch {
            deleteExtensionRepo.await(oldBaseUrl)
            createExtensionRepo.await(newRepoUrl)
            // The flow will automatically update the UI
        }
        return true
    }

    companion object {
        private val repoRegex = """^https://.*/index\.min\.json$""".toRegex()
        private val githubRepoRegex = """https://(?:raw.githubusercontent.com|github.com)/(.+?)/(.+?)/.+""".toRegex()
        const val CREATE_REPO_ITEM = "create_repo"
    }
}
