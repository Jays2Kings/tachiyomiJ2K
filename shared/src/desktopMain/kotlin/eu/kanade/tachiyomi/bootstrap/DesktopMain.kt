package eu.kanade.tachiyomi.bootstrap

import eu.kanade.tachiyomi.extension.backend.DesktopExtensionPackageInstaller
import eu.kanade.tachiyomi.extension.backend.DesktopExtensionRepository
import eu.kanade.tachiyomi.extension.backend.DesktopExtensionTrustStore
import eu.kanade.tachiyomi.network.DesktopPlatformHttpClientFactory
import java.nio.file.Path

class DesktopBootstrapAdapters : NetworkConfigRegistrar, RepositoryRegistrar, DomainServiceRegistrar {

    lateinit var platformHttpClientFactory: DesktopPlatformHttpClientFactory
        private set

    lateinit var extensionRepository: DesktopExtensionRepository
        private set

    lateinit var extensionPackageInstaller: DesktopExtensionPackageInstaller
        private set

    lateinit var extensionTrustStore: DesktopExtensionTrustStore
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
