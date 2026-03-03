package eu.kanade.tachiyomi.bootstrap

import eu.kanade.tachiyomi.network.DesktopPlatformHttpClientFactory

class DesktopBootstrapAdapters : NetworkConfigRegistrar, RepositoryRegistrar, DomainServiceRegistrar {

    lateinit var platformHttpClientFactory: DesktopPlatformHttpClientFactory
        private set

    override fun registerNetworkConfiguration() {
        platformHttpClientFactory = DesktopPlatformHttpClientFactory()
    }

    override fun registerRepositories() {
        // Repository wiring for desktop adapters will be attached here as migration continues.
    }

    override fun registerDomainServices() {
        // Domain service wiring for desktop adapters will be attached here as migration continues.
    }
}

fun initializeDesktopAppBootstrap(): DesktopBootstrapAdapters {
    return DesktopBootstrapAdapters().also {
        AppBootstrap(it, it, it).initialize()
    }
}
