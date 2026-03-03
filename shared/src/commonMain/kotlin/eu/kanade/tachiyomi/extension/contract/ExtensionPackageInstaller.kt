package eu.kanade.tachiyomi.extension.contract

import eu.kanade.tachiyomi.extension.model.ExtensionInstallProgress
import eu.kanade.tachiyomi.extension.model.ExtensionPackage
import eu.kanade.tachiyomi.extension.model.LoadedExtension
import kotlinx.coroutines.flow.Flow

interface ExtensionPackageInstaller {
    fun install(extensionPackage: ExtensionPackage): Flow<ExtensionInstallProgress>

    suspend fun uninstall(extensionId: String)

    suspend fun load(extensionId: String): LoadedExtension?
}
