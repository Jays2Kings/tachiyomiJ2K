package eu.kanade.tachiyomi.extension.backend

import eu.kanade.tachiyomi.extension.contract.DownloadedExtensionArtifact
import eu.kanade.tachiyomi.extension.contract.ExtensionPackageDownloader
import eu.kanade.tachiyomi.extension.model.ExtensionDistribution
import eu.kanade.tachiyomi.extension.model.ExtensionPackage
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class DesktopExtensionPackageDownloader(
    private val distributionRoot: Path,
) : ExtensionPackageDownloader {

    override suspend fun download(extensionPackage: ExtensionPackage): DownloadedExtensionArtifact {
        val source = when (val distribution = extensionPackage.distribution) {
            is ExtensionDistribution.DesktopJar -> distributionRoot.resolve(distribution.fileName)
            is ExtensionDistribution.DesktopZip -> distributionRoot.resolve(distribution.fileName)
            is ExtensionDistribution.DesktopPluginFolder -> distributionRoot.resolve(distribution.folderName)
            is ExtensionDistribution.AndroidApk -> error("Android distribution is not supported in desktop")
        }

        if (!source.exists()) {
            error("Distribution artifact not found: ${source.fileName}")
        }

        return DownloadedExtensionArtifact(extensionPackage.id, source.absolutePathString())
    }
}
