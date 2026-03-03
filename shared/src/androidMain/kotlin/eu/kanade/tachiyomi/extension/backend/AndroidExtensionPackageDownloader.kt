package eu.kanade.tachiyomi.extension.backend

import android.content.Context
import eu.kanade.tachiyomi.extension.contract.DownloadedExtensionArtifact
import eu.kanade.tachiyomi.extension.contract.ExtensionPackageDownloader
import eu.kanade.tachiyomi.extension.model.ExtensionDistribution
import eu.kanade.tachiyomi.extension.model.ExtensionPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class AndroidExtensionPackageDownloader(
    private val context: Context,
) : ExtensionPackageDownloader {

    override suspend fun download(extensionPackage: ExtensionPackage): DownloadedExtensionArtifact = withContext(Dispatchers.IO) {
        val distribution = extensionPackage.distribution as? ExtensionDistribution.AndroidApk
            ?: error("Unsupported distribution for Android downloader")
        val outputFile = File(context.cacheDir, "${extensionPackage.id}.apk")
        URL(distribution.downloadUrl).openStream().use { input ->
            outputFile.outputStream().use { output -> input.copyTo(output) }
        }
        DownloadedExtensionArtifact(extensionPackage.id, outputFile.absolutePath)
    }
}
