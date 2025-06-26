package eu.kanade.tachiyomi.extension.interactor

import eu.kanade.tachiyomi.extension.api.ExtensionRepoService
import eu.kanade.tachiyomi.extension.model.ExtensionRepo
import eu.kanade.tachiyomi.extension.repository.ExtensionRepoRepository
import eu.kanade.tachiyomi.extension.repository.exception.SaveExtensionRepoException
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Use case to add a new Extension Repository.
 */
class CreateExtensionRepo(
    private val extensionRepoRepository: ExtensionRepoRepository = Injekt.get(),
    private val extensionRepoService: ExtensionRepoService = Injekt.get(),
) {
    private val repoRegex = """^https://.*/index\.min\.json$""".toRegex()

    suspend fun await(repoUrl: String): Result {
        if (!repoUrl.matches(repoRegex)) {
            return Result.InvalidUrl
        }

        val baseUrl = repoUrl.removeSuffix("/index.min.json")
        val repoDetails =
            extensionRepoService.fetchRepoDetails(baseUrl)
                ?: return Result.InvalidUrl

        return insert(repoDetails)
    }

    private suspend fun insert(repo: ExtensionRepo): Result =
        try {
            extensionRepoRepository.insertRepository(
                repo.baseUrl,
                repo.name,
                repo.shortName,
                repo.website,
                repo.signingKeyFingerprint,
            )
            Result.Success
        } catch (e: SaveExtensionRepoException) {
            Timber.w(e, "Conflict attempting to add new repository ${repo.baseUrl}")
            handleInsertionError(repo)
        } catch (e: Exception) {
            Timber.e(e, "Unknown error trying to add repository ${repo.baseUrl}")
            Result.Error
        }

    /**
     * Handles insertion errors, specifically UNIQUE constraint violations.
     * Checks if the base URL or the signing key fingerprint already exists.
     */
    private suspend fun handleInsertionError(repo: ExtensionRepo): Result {
        val repoExists = extensionRepoRepository.getRepository(repo.baseUrl)
        if (repoExists != null) {
            return Result.RepoAlreadyExists
        }

        val matchingFingerprintRepo =
            extensionRepoRepository.getRepositoryBySigningKeyFingerprint(repo.signingKeyFingerprint)
        if (matchingFingerprintRepo != null) {
            return Result.DuplicateFingerprint(matchingFingerprintRepo, repo)
        }
        Timber.e("Failed to add repository ${repo.baseUrl} due to unknown SaveExtensionRepoException cause")
        return Result.Error
    }

    sealed interface Result {
        data class DuplicateFingerprint(
            val existingRepo: ExtensionRepo,
            val newRepo: ExtensionRepo,
        ) : Result

        data object InvalidUrl : Result

        data object RepoAlreadyExists : Result

        data object Success : Result

        data object Error : Result
    }
}
