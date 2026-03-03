package eu.kanade.tachiyomi.bootstrap

interface NetworkConfigRegistrar {
    fun registerNetworkConfiguration()
}

interface RepositoryRegistrar {
    fun registerRepositories()
}

interface DomainServiceRegistrar {
    fun registerDomainServices()
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
