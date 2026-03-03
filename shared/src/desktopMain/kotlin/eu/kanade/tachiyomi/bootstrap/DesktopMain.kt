package eu.kanade.tachiyomi.bootstrap

import eu.kanade.tachiyomi.extension.backend.DesktopExtensionPackageInstaller
import eu.kanade.tachiyomi.extension.backend.DesktopExtensionPackageDownloader
import eu.kanade.tachiyomi.extension.backend.DesktopExtensionRepository
import eu.kanade.tachiyomi.extension.backend.DesktopExtensionTrustStore
import eu.kanade.tachiyomi.network.DesktopCookieStore
import eu.kanade.tachiyomi.network.DesktopPlatformHttpClientFactory
import eu.kanade.tachiyomi.network.JsRuntimeFactory
import eu.kanade.tachiyomi.platform.PlatformCapabilities
import java.nio.file.Path

fun desktopAppFactories(
    appDir: Path = Path.of(System.getProperty("user.home"), ".tachiyomi-j2k"),
): AppContractFactories {
    val pluginDir = appDir.resolve("plugins")

    return AppContractFactories(
        extensionRepositoryFactory = ExtensionRepositoryFactory {
            DesktopExtensionRepository(pluginDir) { emptyList() }
        },
        extensionPackageDownloaderFactory = ExtensionPackageDownloaderFactory {
            DesktopExtensionPackageDownloader(appDir.resolve("distribution"))
        },
        extensionPackageInstallerFactory = ExtensionPackageInstallerFactory {
            DesktopExtensionPackageInstaller(
                pluginDirectory = pluginDir,
            )
        },
        extensionTrustStoreFactory = ExtensionTrustStoreFactory {
            DesktopExtensionTrustStore(appDir.resolve("extension-trust.json"))
        },
        cookieStoreFactory = CookieStoreFactory { DesktopCookieStore() },
        platformHttpClientFactoryFactory = PlatformHttpClientFactoryFactory { cookieStore ->
            DesktopPlatformHttpClientFactory(cookieStore = cookieStore)
        },
        jsRuntimeProviderFactory = JsRuntimeProviderFactory { JsRuntimeFactory.create() },
        platformCapabilitiesFactory = PlatformCapabilitiesFactory {
            PlatformCapabilities(
                targetName = "desktop",
                supportsDesktopJar = true,
                supportsDesktopZipExperimental = true,
                supportsDesktopPluginFolderExperimental = true,
            )
        },
    )
}

fun initializeDesktopAppBootstrap(
    appDir: Path = Path.of(System.getProperty("user.home"), ".tachiyomi-j2k"),
): AppContracts {
    return AppBootstrap(desktopAppFactories(appDir)).initialize()
}
