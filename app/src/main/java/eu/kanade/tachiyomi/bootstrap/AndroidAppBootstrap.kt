package eu.kanade.tachiyomi.bootstrap

import android.app.Application
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.preference.AndroidPreferenceStore
import eu.kanade.tachiyomi.data.preference.PreferenceStore
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackPreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.util.TrustExtension
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.chapter.ChapterFilter
import eu.kanade.tachiyomi.util.manga.MangaShortcutManager
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class AndroidAppBootstrap(
    private val app: Application,
) : NetworkConfigRegistrar, RepositoryRegistrar, DomainServiceRegistrar {

    private val appBootstrap = AppBootstrap(this, this, this)

    fun initialize() {
        Injekt.importModule(CommonAndroidModule(app))
        appBootstrap.initialize()
        warmupSingletons()
    }

    override fun registerNetworkConfiguration() {
        Injekt.importModule(NetworkConfigModule(app))
    }

    override fun registerRepositories() {
        Injekt.importModule(RepositoryModule(app))
    }

    override fun registerDomainServices() {
        Injekt.importModule(DomainServicesModule(app))
    }

    private fun warmupSingletons() {
        ContextCompat.getMainExecutor(app).execute {
            Injekt.get<PreferencesHelper>()
            Injekt.get<NetworkHelper>()
            Injekt.get<SourceManager>()
            Injekt.get<DatabaseHelper>()
            Injekt.get<DownloadManager>()
            Injekt.get<CustomMangaManager>()
        }
    }
}

private class CommonAndroidModule(private val app: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)

        addSingletonFactory<PreferenceStore> {
            AndroidPreferenceStore(app)
        }

        addSingletonFactory { PreferencesHelper(app) }

        addSingletonFactory { TrackPreferences(get()) }

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
    }
}

private class NetworkConfigModule(private val app: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory { NetworkHelper(app) }
        addSingletonFactory { JavaScriptEngine() }
    }
}

private class RepositoryModule(private val app: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory { DatabaseHelper(app) }
        addSingletonFactory { ChapterCache(app) }
        addSingletonFactory { CoverCache(app) }
        addSingletonFactory { SourceManager(app, get()) }
        addSingletonFactory { ExtensionManager(app) }
        addSingletonFactory { CustomMangaManager(app) }
        addSingletonFactory { TrackManager(app) }
    }
}

private class DomainServicesModule(private val app: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory { DownloadManager(app) }
        addSingletonFactory { ChapterFilter() }
        addSingletonFactory { MangaShortcutManager() }
        addSingletonFactory { TrustExtension(get()) }
    }
}
