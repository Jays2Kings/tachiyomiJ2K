package eu.kanade.tachiyomi.extension.service

import eu.kanade.tachiyomi.extension.contract.DownloadedExtensionArtifact
import eu.kanade.tachiyomi.extension.contract.ExtensionPackageDownloader
import eu.kanade.tachiyomi.extension.contract.ExtensionPackageInstaller
import eu.kanade.tachiyomi.extension.contract.ExtensionRepository
import eu.kanade.tachiyomi.extension.contract.ExtensionTrustStore
import eu.kanade.tachiyomi.extension.model.ExtensionDistribution
import eu.kanade.tachiyomi.extension.model.ExtensionInstallProgress
import eu.kanade.tachiyomi.extension.model.ExtensionPackage
import eu.kanade.tachiyomi.extension.model.InstallErrorCode
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.LoadedExtension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionInstallationCoordinatorTest {

    @Test
    fun install_runsDiscoveryTrustDownloadInstallAndRefresh() = runBlocking {
        val extension = sampleExtension()
        val repository = FakeRepository(extension)
        val downloader = FakeDownloader()
        val installer = FakeInstaller()
        val trustStore = FakeTrustStore(trusted = true)
        val coordinator = ExtensionInstallationCoordinator(repository, downloader, installer, trustStore)

        val progress = coordinator.install(extension, "fingerprint").toList()

        assertEquals(
            listOf(
                InstallStep.Pending,
                InstallStep.Discovering,
                InstallStep.VerifyingTrust,
                InstallStep.Downloading,
                InstallStep.Installing,
                InstallStep.Installed,
                InstallStep.Done,
                InstallStep.Refreshing,
            ),
            progress.map { it.step },
        )
        assertEquals(1, repository.refreshCalls)
        assertEquals(1, downloader.downloadCalls)
        assertEquals(1, installer.installCalls)
        assertEquals("/tmp/artifact.apk", installer.lastArtifact?.localPath)
    }

    @Test
    fun install_stopsWhenExtensionIsUntrusted() = runBlocking {
        val extension = sampleExtension()
        val repository = FakeRepository(extension)
        val downloader = FakeDownloader()
        val installer = FakeInstaller()
        val trustStore = FakeTrustStore(trusted = false)
        val coordinator = ExtensionInstallationCoordinator(repository, downloader, installer, trustStore)

        val progress = coordinator.install(extension, "fingerprint").toList()

        assertEquals(InstallStep.Error, progress.last().step)
        assertEquals(InstallErrorCode.Untrusted, progress.last().error?.code)
        assertEquals(0, downloader.downloadCalls)
        assertEquals(0, installer.installCalls)
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun install_mapsDownloaderFailureToDomainError() = runBlocking {
        val extension = sampleExtension()
        val repository = FakeRepository(extension)
        val downloader = FakeDownloader(shouldFail = true)
        val installer = FakeInstaller()
        val trustStore = FakeTrustStore(trusted = true)
        val coordinator = ExtensionInstallationCoordinator(repository, downloader, installer, trustStore)

        val progress = coordinator.install(extension, "fingerprint").toList()

        assertEquals(InstallStep.Error, progress.last().step)
        assertEquals(InstallErrorCode.DownloadFailed, progress.last().error?.code)
        assertFalse(repository.refreshed)
        assertEquals(0, installer.installCalls)
    }

    @Test
    fun trustAndInstall_persistsTrustBeforeInstall() = runBlocking {
        val extension = sampleExtension()
        val repository = FakeRepository(extension)
        val downloader = FakeDownloader()
        val installer = FakeInstaller()
        val trustStore = FakeTrustStore(trusted = false)
        val coordinator = ExtensionInstallationCoordinator(repository, downloader, installer, trustStore)

        val progress = coordinator.trustAndInstall(extension, "fingerprint").toList()

        assertTrue(trustStore.wasTrusted)
        assertEquals(InstallStep.Refreshing, progress.last().step)
        assertEquals(1, installer.installCalls)
    }

    private fun sampleExtension() = ExtensionPackage(
        id = "eu.kanade.tachiyomi.extension.test",
        name = "Test",
        versionName = "1.0",
        distribution = ExtensionDistribution.AndroidApk("https://example.org/test.apk"),
    )

    private class FakeRepository(
        private val extension: ExtensionPackage?,
    ) : ExtensionRepository {
        var refreshed = false
        var refreshCalls = 0

        override suspend fun refresh() {
            refreshed = true
            refreshCalls++
        }

        override fun observeInstalled(): Flow<List<ExtensionPackage>> = flow { emit(emptyList()) }

        override fun observeAvailable(): Flow<List<ExtensionPackage>> = flow { emit(listOfNotNull(extension)) }

        override suspend fun getById(extensionId: String): ExtensionPackage? = extension?.takeIf { it.id == extensionId }
    }

    private class FakeDownloader(
        private val shouldFail: Boolean = false,
    ) : ExtensionPackageDownloader {
        var downloadCalls = 0

        override suspend fun download(extensionPackage: ExtensionPackage): DownloadedExtensionArtifact {
            downloadCalls++
            if (shouldFail) {
                error("network failure")
            }
            return DownloadedExtensionArtifact(extensionPackage.id, "/tmp/artifact.apk")
        }
    }

    private class FakeInstaller : ExtensionPackageInstaller {
        var installCalls = 0
        var lastArtifact: DownloadedExtensionArtifact? = null

        override fun install(
            extensionPackage: ExtensionPackage,
            artifact: DownloadedExtensionArtifact,
        ): Flow<ExtensionInstallProgress> = flow {
            installCalls++
            lastArtifact = artifact
            emit(ExtensionInstallProgress(extensionPackage.id, InstallStep.Installing))
            emit(ExtensionInstallProgress(extensionPackage.id, InstallStep.Installed))
            emit(ExtensionInstallProgress(extensionPackage.id, InstallStep.Done))
        }

        override suspend fun uninstall(extensionId: String) = Unit

        override suspend fun load(extensionId: String): LoadedExtension? = null
    }

    private class FakeTrustStore(
        private var trusted: Boolean,
    ) : ExtensionTrustStore {
        var wasTrusted = false

        override suspend fun isTrusted(extensionId: String, fingerprint: String): Boolean = trusted

        override suspend fun trust(extensionId: String, fingerprint: String) {
            trusted = true
            wasTrusted = true
        }

        override suspend fun revoke(extensionId: String) {
            trusted = false
        }
    }
}
