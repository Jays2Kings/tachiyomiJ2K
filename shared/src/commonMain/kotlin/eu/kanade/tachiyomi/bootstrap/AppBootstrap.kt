package eu.kanade.tachiyomi.bootstrap

import eu.kanade.tachiyomi.extension.contract.ExtensionPackageInstaller
import eu.kanade.tachiyomi.extension.contract.ExtensionRepository
import eu.kanade.tachiyomi.extension.contract.ExtensionTrustStore
import eu.kanade.tachiyomi.network.CookieStore
import eu.kanade.tachiyomi.network.JsRuntime
import eu.kanade.tachiyomi.network.PlatformHttpClientFactory

fun interface ExtensionRepositoryFactory {
    fun create(): ExtensionRepository
}

fun interface ExtensionPackageInstallerFactory {
    fun create(): ExtensionPackageInstaller
}

fun interface ExtensionTrustStoreFactory {
    fun create(): ExtensionTrustStore
}

fun interface CookieStoreFactory {
    fun create(): CookieStore
}

fun interface PlatformHttpClientFactoryFactory {
    fun create(cookieStore: CookieStore): PlatformHttpClientFactory
}

fun interface JsRuntimeProviderFactory {
    fun create(): JsRuntime
}

data class AppContractFactories(
    val extensionRepositoryFactory: ExtensionRepositoryFactory,
    val extensionPackageInstallerFactory: ExtensionPackageInstallerFactory,
    val extensionTrustStoreFactory: ExtensionTrustStoreFactory,
    val cookieStoreFactory: CookieStoreFactory,
    val platformHttpClientFactoryFactory: PlatformHttpClientFactoryFactory,
    val jsRuntimeProviderFactory: JsRuntimeProviderFactory,
)

data class AppContracts(
    val extensionRepository: ExtensionRepository,
    val extensionPackageInstaller: ExtensionPackageInstaller,
    val extensionTrustStore: ExtensionTrustStore,
    val cookieStore: CookieStore,
    val platformHttpClientFactory: PlatformHttpClientFactory,
    val jsRuntimeProvider: JsRuntimeProviderFactory,
)

class AppBootstrap(private val factories: AppContractFactories) {

    fun initialize(): AppContracts {
        val cookieStore = factories.cookieStoreFactory.create()
        return AppContracts(
            extensionRepository = factories.extensionRepositoryFactory.create(),
            extensionPackageInstaller = factories.extensionPackageInstallerFactory.create(),
            extensionTrustStore = factories.extensionTrustStoreFactory.create(),
            cookieStore = cookieStore,
            platformHttpClientFactory = factories.platformHttpClientFactoryFactory.create(cookieStore),
            jsRuntimeProvider = factories.jsRuntimeProviderFactory,
        )
    }
}
