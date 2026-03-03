package eu.kanade.tachiyomi.extension.contract

import eu.kanade.tachiyomi.extension.model.ExtensionInstallProgress
import eu.kanade.tachiyomi.extension.model.ExtensionPackage
import eu.kanade.tachiyomi.extension.model.LoadedExtension
import kotlinx.coroutines.flow.Flow

/**
 * Contract for installing, uninstalling, and loading extension artifacts.
 *
 * Each platform implementation must map platform-specific installation mechanics to these
 * operations and emit lifecycle progress through [install] without leaking target-only types.
 */
interface ExtensionPackageInstaller {
    /**
     * Starts installation of [artifact] for [extensionPackage] and emits progress updates until completion.
     */
    fun install(extensionPackage: ExtensionPackage, artifact: DownloadedExtensionArtifact): Flow<ExtensionInstallProgress>

    /**
     * Removes the installed extension identified by [extensionId].
     */
    suspend fun uninstall(extensionId: String)

    /**
     * Loads metadata required to instantiate an already installed extension.
     */
    suspend fun load(extensionId: String): LoadedExtension?
}
