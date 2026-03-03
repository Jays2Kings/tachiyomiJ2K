package eu.kanade.tachiyomi.bootstrap

import eu.kanade.tachiyomi.extension.contract.ExtensionPackageInstaller
import eu.kanade.tachiyomi.extension.contract.ExtensionRepository
import eu.kanade.tachiyomi.extension.contract.ExtensionTrustStore
import eu.kanade.tachiyomi.network.PlatformHttpClientFactory

interface NetworkConfigRegistrar {
    fun registerNetworkConfiguration()
}

interface RepositoryRegistrar {
    fun registerRepositories()
}

interface DomainServiceRegistrar {
    fun registerDomainServices()
}

/**
 * Shared contract that exposes platform networking adapter instances to common consumers.
 */
interface NetworkContractsProvider {
    val platformHttpClientFactory: PlatformHttpClientFactory
}

/**
 * Shared contract that exposes extension adapters to common consumers.
 */
interface ExtensionContractsProvider {
    val extensionRepository: ExtensionRepository
    val extensionPackageInstaller: ExtensionPackageInstaller
    val extensionTrustStore: ExtensionTrustStore
}

class AppBootstrap(
    private val networkConfigRegistrar: NetworkConfigRegistrar,
    private val repositoryRegistrar: RepositoryRegistrar,
    private val domainServiceRegistrar: DomainServiceRegistrar,
) {

    fun initialize() {
        networkConfigRegistrar.registerNetworkConfiguration()
        repositoryRegistrar.registerRepositories()
        domainServiceRegistrar.registerDomainServices()
    }
}
