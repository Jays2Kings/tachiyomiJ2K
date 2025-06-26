package eu.kanade.tachiyomi.extension.interactor

import eu.kanade.tachiyomi.extension.model.ExtensionRepo
import eu.kanade.tachiyomi.extension.repository.ExtensionRepoRepository
import kotlinx.coroutines.flow.Flow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Use case to retrieve Extension Repositories.
 */
class GetExtensionRepo(
    private val extensionRepoRepository: ExtensionRepoRepository = Injekt.get(),
) {
    /**
     * Returns a flow containing the list of all repositories.
     */
    fun subscribeAll(): Flow<List<ExtensionRepo>> = extensionRepoRepository.subscribeAll()

    /**
     * Returns the current list of all repositories.
     */
    suspend fun getAll(): List<ExtensionRepo> = extensionRepoRepository.getAll()
}
