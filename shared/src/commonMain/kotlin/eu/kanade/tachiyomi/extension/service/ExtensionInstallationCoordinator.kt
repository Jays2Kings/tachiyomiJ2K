package eu.kanade.tachiyomi.extension.service

import eu.kanade.tachiyomi.extension.contract.ExtensionPackageInstaller
import eu.kanade.tachiyomi.extension.contract.ExtensionRepository
import eu.kanade.tachiyomi.extension.contract.ExtensionTrustStore
import eu.kanade.tachiyomi.extension.model.ExtensionInstallProgress
import eu.kanade.tachiyomi.extension.model.ExtensionPackage
import eu.kanade.tachiyomi.extension.model.InstallStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

/**
 * Platform-agnostic install orchestration so UI layers only render [ExtensionInstallProgress].
 */
class ExtensionInstallationCoordinator(
    private val repository: ExtensionRepository,
    private val installer: ExtensionPackageInstaller,
    private val trustStore: ExtensionTrustStore,
) {

    fun install(extensionPackage: ExtensionPackage, fingerprint: String): Flow<ExtensionInstallProgress> {
        return flow {
            emit(ExtensionInstallProgress(extensionPackage.id, InstallStep.Pending))

            if (!trustStore.isTrusted(extensionPackage.id, fingerprint)) {
                emit(
                    ExtensionInstallProgress(
                        extensionId = extensionPackage.id,
                        step = InstallStep.Error,
                        message = "Extension is not trusted",
                    ),
                )
                return@flow
            }

            installer.install(extensionPackage).collect { emit(it) }
        }.onCompletion {
            repository.refresh()
        }
    }

    suspend fun trustAndInstall(extensionPackage: ExtensionPackage, fingerprint: String): Flow<ExtensionInstallProgress> {
        trustStore.trust(extensionPackage.id, fingerprint)
        return install(extensionPackage, fingerprint)
    }
}
