package eu.kanade.tachiyomi.extension.contract

import eu.kanade.tachiyomi.extension.model.ExtensionPackage
import kotlinx.coroutines.flow.Flow

interface ExtensionRepository {
    suspend fun refresh()

    fun observeInstalled(): Flow<List<ExtensionPackage>>

    fun observeAvailable(): Flow<List<ExtensionPackage>>

    suspend fun getById(extensionId: String): ExtensionPackage?
}
