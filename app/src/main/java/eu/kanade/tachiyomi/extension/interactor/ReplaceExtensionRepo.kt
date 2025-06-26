package eu.kanade.tachiyomi.extension.interactor

import eu.kanade.tachiyomi.extension.model.ExtensionRepo
import eu.kanade.tachiyomi.extension.repository.ExtensionRepoRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Use case to replace an existing Extension Repository, typically used
 * when a new repository addition conflicts with an existing one
 * due to the same signing key fingerprint.
 */
class ReplaceExtensionRepo(
    private val extensionRepoRepository: ExtensionRepoRepository = Injekt.get(),
) {
    /**
     * Replaces the repository entry that has the same signing key fingerprint
     * as the provided [repo].
     *
     * @param repo The new repository data to insert/update.
     */
    suspend fun await(repo: ExtensionRepo) {
        extensionRepoRepository.replaceRepository(repo)
    }
}
