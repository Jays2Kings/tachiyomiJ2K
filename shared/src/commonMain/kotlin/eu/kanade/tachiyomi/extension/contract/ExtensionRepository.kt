package eu.kanade.tachiyomi.extension.contract

import eu.kanade.tachiyomi.extension.model.ExtensionPackage
import kotlinx.coroutines.flow.Flow

/**
 * Contract used by common services to query extension metadata.
 *
 * Every target implementation must keep [observeInstalled] and [observeAvailable]
 * synchronized with the same id namespace used by [getById], so common code can
 * reason about packages consistently across Android/Desktop.
 */
interface ExtensionRepository {
    /**
     * Refreshes installed and available extension data from the platform source of truth.
     */
    suspend fun refresh()

    /**
     * Emits the current set of installed extension packages.
     */
    fun observeInstalled(): Flow<List<ExtensionPackage>>

    /**
     * Emits extension packages that can be installed on the current target.
     */
    fun observeAvailable(): Flow<List<ExtensionPackage>>

    /**
     * Returns the package identified by [extensionId], if present in either installed or available sets.
     */
    suspend fun getById(extensionId: String): ExtensionPackage?
}
