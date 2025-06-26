package eu.kanade.tachiyomi.extension.interactor

import eu.kanade.tachiyomi.extension.repository.ExtensionRepoRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Use case to delete an existing Extension Repository.
 */
class DeleteExtensionRepo(
    private val extensionRepoRepository: ExtensionRepoRepository = Injekt.get(),
) {
    suspend fun await(baseUrl: String) {
        extensionRepoRepository.deleteRepository(baseUrl)
    }
}
