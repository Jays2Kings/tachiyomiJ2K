package eu.kanade.tachiyomi.extension.interactor

import eu.kanade.tachiyomi.extension.api.ExtensionRepoService
import eu.kanade.tachiyomi.extension.model.ExtensionRepo
import eu.kanade.tachiyomi.extension.repository.ExtensionRepoRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Use case to update Extension Repository details by fetching them again from the source.
 */
class UpdateExtensionRepo(
    private val extensionRepoRepository: ExtensionRepoRepository = Injekt.get(),
    private val extensionRepoService: ExtensionRepoService = Injekt.get(),
) {
    /**
     * Refreshes the details for all existing repositories.
     * Fetches repo.json for each and updates the database entry if necessary.
     */
    suspend fun awaitAll() =
        coroutineScope {
            extensionRepoRepository
                .getAll()
                .map { async { await(it) } }
                .awaitAll()
        }

    /**
     * Refreshes the details for a single repository.
     *
     * @param repo The existing repository data to refresh.
     */
    suspend fun await(repo: ExtensionRepo) {
        val newRepoDetails = extensionRepoService.fetchRepoDetails(repo.baseUrl)
        if (newRepoDetails == null) {
            Timber.w("Could not fetch updated details for ${repo.name} (${repo.baseUrl})")
            return
        }
        if (repo.signingKeyFingerprint.startsWith("NOFINGERPRINT") ||

            repo.signingKeyFingerprint == newRepoDetails.signingKeyFingerprint
        ) {
            try {
                extensionRepoRepository.upsertRepository(newRepoDetails)
            } catch (e: Exception) {
                Timber.e(e, "Could not update repository ${repo.baseUrl}")
            }
        } else {
            Timber.w(
                "Fingerprint mismatch for ${repo.name} (${repo.baseUrl}). " +
                    "Expected ${repo.signingKeyFingerprint}, got ${newRepoDetails.signingKeyFingerprint}. " +
                    "Repo details not updated.",
            )
        }
    }
}
