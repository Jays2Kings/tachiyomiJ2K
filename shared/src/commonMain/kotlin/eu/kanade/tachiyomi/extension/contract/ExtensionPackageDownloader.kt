package eu.kanade.tachiyomi.extension.contract

import eu.kanade.tachiyomi.extension.model.ExtensionPackage

/**
 * Contract for resolving and downloading extension artifacts before install.
 */
interface ExtensionPackageDownloader {
    suspend fun download(extensionPackage: ExtensionPackage): DownloadedExtensionArtifact
}

data class DownloadedExtensionArtifact(
    val extensionId: String,
    val localPath: String,
)

