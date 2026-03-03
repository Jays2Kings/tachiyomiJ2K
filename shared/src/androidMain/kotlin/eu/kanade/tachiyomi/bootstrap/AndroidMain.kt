package eu.kanade.tachiyomi.bootstrap

import android.content.Context
import eu.kanade.tachiyomi.extension.backend.AndroidApkExtensionPackageInstaller
import eu.kanade.tachiyomi.extension.backend.AndroidExtensionPackageDownloader
import eu.kanade.tachiyomi.extension.backend.AndroidExtensionRepository
import eu.kanade.tachiyomi.extension.backend.AndroidExtensionTrustStore
import eu.kanade.tachiyomi.network.AndroidSharedCookieStore
import eu.kanade.tachiyomi.network.AndroidSharedPlatformHttpClientFactory
import eu.kanade.tachiyomi.network.JsRuntimeFactory

fun androidAppFactories(context: Context): AppContractFactories {
    return AppContractFactories(
        extensionRepositoryFactory = ExtensionRepositoryFactory {
            AndroidExtensionRepository(context) { emptyList() }
        },
        extensionPackageDownloaderFactory = ExtensionPackageDownloaderFactory {
            AndroidExtensionPackageDownloader(context)
        },
        extensionPackageInstallerFactory = ExtensionPackageInstallerFactory {
            AndroidApkExtensionPackageInstaller(context)
        },
        extensionTrustStoreFactory = ExtensionTrustStoreFactory {
            AndroidExtensionTrustStore(context)
        },
        cookieStoreFactory = CookieStoreFactory { AndroidSharedCookieStore() },
        platformHttpClientFactoryFactory = PlatformHttpClientFactoryFactory { cookieStore ->
            AndroidSharedPlatformHttpClientFactory(cookieStore)
        },
        jsRuntimeProviderFactory = JsRuntimeProviderFactory { JsRuntimeFactory.create() },
    )
}

fun initializeAndroidAppBootstrap(context: Context): AppContracts {
    return AppBootstrap(androidAppFactories(context)).initialize()
}
