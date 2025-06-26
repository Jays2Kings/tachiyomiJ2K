package eu.kanade.tachiyomi.extension.interactor

import eu.kanade.tachiyomi.extension.repository.ExtensionRepoRepository
import kotlinx.coroutines.flow.Flow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Use case to get the count of Extension Repositories.
 */
class GetExtensionRepoCount(
    private val extensionRepoRepository: ExtensionRepoRepository = Injekt.get(),
) {
    /**
     * Returns a flow containing the total count of repositories.
     */
    fun subscribe(): Flow<Int> = extensionRepoRepository.getCount()
}
