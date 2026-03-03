package eu.kanade.tachiyomi.extension.model

import eu.kanade.tachiyomi.extension.model.InstallStep

sealed interface ExtensionDistribution {
    data class AndroidApk(val downloadUrl: String) : ExtensionDistribution

    data class DesktopJar(val fileName: String) : ExtensionDistribution

    data class DesktopZip(val fileName: String) : ExtensionDistribution

    data class DesktopPluginFolder(val folderName: String) : ExtensionDistribution
}

data class ExtensionPackage(
    val id: String,
    val name: String,
    val versionName: String,
    val distribution: ExtensionDistribution,
)

data class ExtensionInstallProgress(
    val extensionId: String,
    val step: InstallStep,
    val message: String? = null,
)

data class LoadedExtension(
    val extensionId: String,
    val entrypointClass: String,
)
