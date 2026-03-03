package eu.kanade.tachiyomi.platform

import eu.kanade.tachiyomi.extension.model.ExtensionDistribution

data class PlatformCapabilities(
    val targetName: String,
    val supportsAndroidApk: Boolean = false,
    val supportsDesktopJar: Boolean = false,
    val supportsDesktopZipExperimental: Boolean = false,
    val supportsDesktopPluginFolderExperimental: Boolean = false,
) {
    fun supports(distribution: ExtensionDistribution): Boolean {
        return when (distribution) {
            is ExtensionDistribution.AndroidApk -> supportsAndroidApk
            is ExtensionDistribution.DesktopJar -> supportsDesktopJar
            is ExtensionDistribution.DesktopZip -> supportsDesktopZipExperimental
            is ExtensionDistribution.DesktopPluginFolder -> supportsDesktopPluginFolderExperimental
        }
    }

    fun unsupportedMessage(distribution: ExtensionDistribution): String {
        return "Target '$targetName' does not support distribution '${distribution::class.simpleName}'."
    }

    companion object {
        fun conservative(targetName: String): PlatformCapabilities {
            return PlatformCapabilities(targetName = targetName)
        }
    }
}
