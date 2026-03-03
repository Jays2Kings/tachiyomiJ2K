package eu.kanade.tachiyomi.bootstrap

import eu.kanade.tachiyomi.extension.backend.DesktopExtensionPackageInstaller
import eu.kanade.tachiyomi.extension.backend.DesktopExtensionRepository
import eu.kanade.tachiyomi.extension.backend.DesktopExtensionTrustStore
import eu.kanade.tachiyomi.extension.contract.ExtensionPackageInstaller
import eu.kanade.tachiyomi.extension.contract.ExtensionRepository
import eu.kanade.tachiyomi.extension.contract.ExtensionTrustStore
import eu.kanade.tachiyomi.network.DesktopPlatformHttpClientFactory
import eu.kanade.tachiyomi.network.PlatformHttpClientFactory
import java.nio.file.Path

class DesktopBootstrapAdapters :
    NetworkConfigRegistrar,
    RepositoryRegistrar,
    DomainServiceRegistrar,
    NetworkContractsProvider,
    ExtensionContractsProvider {

    override lateinit var platformHttpClientFactory: PlatformHttpClientFactory
        private set

    override lateinit var extensionRepository: ExtensionRepository
        private set

    override lateinit var extensionPackageInstaller: ExtensionPackageInstaller
        private set

    override lateinit var extensionTrustStore: ExtensionTrustStore
        private set

    override fun registerNetworkConfiguration() {
        platformHttpClientFactory = DesktopPlatformHttpClientFactory()
    }

    override fun registerRepositories() {
        val appDir = Path.of(System.getProperty("user.home"), ".tachiyomi-j2k")
        val pluginDir = appDir.resolve("plugins")

        extensionRepository = DesktopExtensionRepository(pluginDir) { emptyList() }
        extensionPackageInstaller = DesktopExtensionPackageInstaller(
            distributionRoot = appDir.resolve("distribution"),
            pluginDirectory = pluginDir,
        )
        extensionTrustStore = DesktopExtensionTrustStore(appDir.resolve("extension-trust.json"))
    }

    override fun registerDomainServices() {
        // Domain services can now consume extension contracts from commonMain.
    }
}

fun initializeDesktopAppBootstrap(): DesktopBootstrapAdapters {
    return DesktopBootstrapAdapters().also {
        AppBootstrap(it, it, it).initialize()
    }
}
