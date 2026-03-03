package eu.kanade.tachiyomi.extension.backend

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import eu.kanade.tachiyomi.extension.contract.ExtensionPackageInstaller
import eu.kanade.tachiyomi.extension.model.ExtensionDistribution
import eu.kanade.tachiyomi.extension.model.ExtensionInstallProgress
import eu.kanade.tachiyomi.extension.model.ExtensionPackage
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.LoadedExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class AndroidApkExtensionPackageInstaller(
    private val context: Context,
) : ExtensionPackageInstaller {

    override fun install(extensionPackage: ExtensionPackage): Flow<ExtensionInstallProgress> = flow {
        val distribution = extensionPackage.distribution as? ExtensionDistribution.AndroidApk
        if (distribution == null) {
            emit(ExtensionInstallProgress(extensionPackage.id, InstallStep.Error, "Unsupported distribution"))
            return@flow
        }

        emit(ExtensionInstallProgress(extensionPackage.id, InstallStep.Downloading))
        val apkFile = downloadApk(extensionPackage.id, distribution.downloadUrl)

        emit(ExtensionInstallProgress(extensionPackage.id, InstallStep.Installing))
        installApk(apkFile)

        emit(ExtensionInstallProgress(extensionPackage.id, InstallStep.Installed))
        emit(ExtensionInstallProgress(extensionPackage.id, InstallStep.Done))
    }

    override suspend fun uninstall(extensionId: String) {
        withContext(Dispatchers.IO) {
            context.packageManager.packageInstaller.uninstall(
                extensionId,
                PackageInstallerStatusReceiver.intentSender(context),
            )
        }
    }

    override suspend fun load(extensionId: String): LoadedExtension? {
        val packageInfo = context.packageManager.getPackageInfo(extensionId, 0)
        return LoadedExtension(
            extensionId = extensionId,
            entrypointClass = packageInfo.applicationInfo.className ?: "",
        )
    }

    private suspend fun downloadApk(extensionId: String, url: String): File = withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "$extensionId.apk")
        URL(url).openStream().use { input ->
            outputFile.outputStream().use { output -> input.copyTo(output) }
        }
        outputFile
    }

    private suspend fun installApk(apkFile: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        session.openWrite("base.apk", 0, apkFile.length()).use { output ->
            apkFile.inputStream().use { input -> input.copyTo(output) }
            session.fsync(output)
        }

        session.commit(PackageInstallerStatusReceiver.intentSender(context))
        session.close()
    }
}

private object PackageInstallerStatusReceiver {
    private const val ACTION_PACKAGE_INSTALL_COMMIT = "eu.kanade.tachiyomi.shared.extension.PACKAGE_INSTALL_COMMIT"

    fun intentSender(context: Context) = android.app.PendingIntent.getBroadcast(
        context,
        0,
        Intent(ACTION_PACKAGE_INSTALL_COMMIT).setData(Uri.parse("package:${context.packageName}")),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
    ).intentSender
}
