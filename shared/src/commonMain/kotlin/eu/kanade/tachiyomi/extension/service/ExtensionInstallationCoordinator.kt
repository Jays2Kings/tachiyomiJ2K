package eu.kanade.tachiyomi.extension.service

import eu.kanade.tachiyomi.extension.contract.ExtensionPackageDownloader
import eu.kanade.tachiyomi.extension.contract.ExtensionPackageInstaller
import eu.kanade.tachiyomi.extension.contract.ExtensionRepository
import eu.kanade.tachiyomi.extension.contract.ExtensionTrustStore
import eu.kanade.tachiyomi.extension.model.ExtensionInstallProgress
import eu.kanade.tachiyomi.extension.model.ExtensionPackage
import eu.kanade.tachiyomi.extension.model.InstallError
import eu.kanade.tachiyomi.extension.model.InstallErrorCode
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.platform.PlatformCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Platform-agnostic install orchestration so UI layers only render [ExtensionInstallProgress].
 */
class ExtensionInstallationCoordinator(
    private val repository: ExtensionRepository,
    private val downloader: ExtensionPackageDownloader,
    private val installer: ExtensionPackageInstaller,
    private val trustStore: ExtensionTrustStore,
    private val platformCapabilities: PlatformCapabilities = PlatformCapabilities.conservative(targetName = "unknown"),
) {

    fun install(extensionPackage: ExtensionPackage, fingerprint: String): Flow<ExtensionInstallProgress> {
        return flow {
            emit(ExtensionInstallProgress(extensionPackage.id, InstallStep.Pending))
            emit(ExtensionInstallProgress(extensionPackage.id, InstallStep.Discovering))

            val discovered = repository.getById(extensionPackage.id)
            if (discovered == null) {
                emit(
                    errorProgress(
                        extensionId = extensionPackage.id,
                        step = InstallStep.Discovering,
                        code = InstallErrorCode.ExtensionNotFound,
                        message = "Extension not found in repository",
                    ),
                )
                return@flow
            }

            if (!platformCapabilities.supports(discovered.distribution)) {
                val message = platformCapabilities.unsupportedMessage(discovered.distribution)
                println("[ExtensionInstallationCoordinator] Unsupported install target: $message")
                emit(
                    errorProgress(
                        extensionId = discovered.id,
                        step = InstallStep.Discovering,
                        code = InstallErrorCode.UnsupportedDistribution,
                        message = message,
                    ),
                )
                return@flow
            }

            emit(ExtensionInstallProgress(discovered.id, InstallStep.VerifyingTrust))
            if (!trustStore.isTrusted(discovered.id, fingerprint)) {
                emit(
                    errorProgress(
                        extensionId = discovered.id,
                        step = InstallStep.VerifyingTrust,
                        code = InstallErrorCode.Untrusted,
                        message = "Extension is not trusted",
                    ),
                )
                return@flow
            }

            emit(ExtensionInstallProgress(discovered.id, InstallStep.Downloading))
            val artifact = runCatching { downloader.download(discovered) }.getOrElse { error ->
                emit(
                    errorProgress(
                        extensionId = discovered.id,
                        step = InstallStep.Downloading,
                        code = InstallErrorCode.DownloadFailed,
                        message = error.message ?: "Download failed",
                    ),
                )
                return@flow
            }

            installer.install(discovered, artifact).collect { emit(it) }

            emit(ExtensionInstallProgress(discovered.id, InstallStep.Refreshing))
            runCatching { repository.refresh() }.onFailure { error ->
                emit(
                    errorProgress(
                        extensionId = discovered.id,
                        step = InstallStep.Refreshing,
                        code = InstallErrorCode.RefreshFailed,
                        message = error.message ?: "Repository refresh failed",
                    ),
                )
            }
        }
    }

    suspend fun trustAndInstall(extensionPackage: ExtensionPackage, fingerprint: String): Flow<ExtensionInstallProgress> {
        trustStore.trust(extensionPackage.id, fingerprint)
        return install(extensionPackage, fingerprint)
    }

    private fun errorProgress(
        extensionId: String,
        step: InstallStep,
        code: InstallErrorCode,
        message: String,
    ): ExtensionInstallProgress {
        return ExtensionInstallProgress(
            extensionId = extensionId,
            step = InstallStep.Error,
            message = message,
            error = InstallError(code = code, step = step, detail = message),
        )
    }
}
