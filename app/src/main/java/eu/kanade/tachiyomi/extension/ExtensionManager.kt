package eu.kanade.tachiyomi.extension

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Parcelable
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.api.ExtensionApi
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.extension.util.ExtensionInstallReceiver
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.extension.util.TrustExtension
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.extension.ExtensionIntallInfo
import eu.kanade.tachiyomi.util.system.launchNow
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

/**
 * The manager of extensions installed as another apk which extend the available sources. It handles
 * the retrieval of remotely available extensions as well as installing, updating and removing them.
 * To avoid malicious distribution, every extension must be signed and it will only be loaded if its
 * signature is trusted, otherwise the user will be prompted with a warning to trust it before being
 * loaded.
 *
 * @param context The application context.
 * @param preferences The application preferences.
 */
class ExtensionManager(
    private val context: Context,
    private val preferences: PreferencesHelper = Injekt.get(),
    private val trustExtension: TrustExtension = Injekt.get(),
) {
    /**
     * API where all the available extensions can be found.
     */
    private val api = ExtensionApi()

    /**
     * The installer which installs, updates and uninstalls the extensions.
     */
    private val installer by lazy { ExtensionInstaller(context) }

    private val iconMap = mutableMapOf<String, Drawable>()

    val downloadSharedFlow = installer.downloadSharedFlow

    private fun getExtension(downloadId: Long): String? =
        installer.activeDownloads.entries
            .find { downloadId == it.value }
            ?.key

    /**
     * Relay used to notify the installed extensions.
     */
    private val _installedExtensionsFlow = MutableStateFlow(emptyList<Extension.Installed>())
    val installedExtensionsFlow = _installedExtensionsFlow.asStateFlow()

    private var subLanguagesEnabledOnFirstRun = preferences.enabledLanguages().isSet()

    fun getAppIconForSource(source: Source): Drawable? = getAppIconForSource(source.id)

    private fun getAppIconForSource(sourceId: Long): Drawable? {
        val pkgName =
            _installedExtensionsFlow.value
                .find { ext -> ext.sources.any { it.id == sourceId } }
                ?.pkgName
        return if (pkgName != null) {
            try {
                return iconMap.getOrPut(pkgName) {
                    ExtensionLoader
                        .getExtensionPackageInfoFromPkgName(context, pkgName)!!
                        .applicationInfo!!
                        .loadIcon(context.packageManager)
                }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    /**
     * Relay used to notify the available extensions.
     */
    private val _availableExtensionsFlow = MutableStateFlow(emptyList<Extension.Available>())
    val availableExtensionsFlow = _availableExtensionsFlow.asStateFlow()

    private var availableSources = hashMapOf<Long, Extension.AvailableSource>()

    private val _untrustedExtensionsFlow = MutableStateFlow(emptyList<Extension.Untrusted>())
    val untrustedExtensionsFlow = _untrustedExtensionsFlow.asStateFlow()

    init {
        initExtensions()
        ExtensionInstallReceiver(InstallationListener()).register(context)
    }

    /**
     * Loads and registers the installed extensions.
     */
    private fun initExtensions() {
        val extensions = ExtensionLoader.loadExtensions(context)

        _installedExtensionsFlow.value =
            extensions
                .filterIsInstance<LoadResult.Success>()
                .map { it.extension }

        _untrustedExtensionsFlow.value =
            extensions
                .filterIsInstance<LoadResult.Untrusted>()
                .map { it.extension }
    }

    fun isInstalledByApp(extension: Extension.Available): Boolean = ExtensionLoader.isExtensionInstalledByApp(context, extension.pkgName)

    /**
     * Finds the available extensions in the [api] and updates [availableExtensionsFlow].
     */
    suspend fun findAvailableExtensions() {
        val extensions: List<Extension.Available> =
            try {
                api.findExtensions()
            } catch (e: Exception) {
                Timber.e(e, context.getString(R.string.extension_api_error))
                withUIContext { context.toast(R.string.extension_api_error) }
                emptyList()
            }
        enableAdditionalSubLanguages(extensions)

        _availableExtensionsFlow.value = extensions
        updatedInstalledExtensionsStatuses(extensions)
        setupAvailableSourcesMap()
        emitToInstaller("Finished/Available/${extensions.size}", (InstallStep.Done to null))
    }

    /**
     * Enables the additional sub-languages in the app first run. This addresses
     * the issue where users still need to enable some specific languages even when
     * the device language is inside that major group. As an example, if a user
     * has a zh device language, the app will also enable zh-Hans and zh-Hant.
     *
     * If the user have already changed the enabledLanguages preference value once,
     * the new languages will not be added to respect the user enabled choices.
     */
    private fun enableAdditionalSubLanguages(extensions: List<Extension.Available>) {
        if (subLanguagesEnabledOnFirstRun || extensions.isEmpty()) {
            return
        }

        // Use the source lang as some aren't present on the extension level.
        val availableLanguages =
            extensions
                .flatMap(Extension.Available::sources)
                .distinctBy(Extension.AvailableSource::lang)
                .map(Extension.AvailableSource::lang)

        val deviceLanguage = Locale.getDefault().language
        val defaultLanguages = preferences.enabledLanguages().defaultValue
        val languagesToEnable =
            availableLanguages.filter {
                it != deviceLanguage && it.startsWith(deviceLanguage) && !it.startsWith("en")
            }

        preferences.enabledLanguages().set(defaultLanguages + languagesToEnable)
        subLanguagesEnabledOnFirstRun = true
    }

    private fun setupAvailableSourcesMap() {
        availableSources = hashMapOf()
        _availableExtensionsFlow.value.map { it.sources }.flatten().forEach {
            availableSources[it.id] = it
        }
    }

    fun getStubSource(id: Long) = availableSources[id]

    /**
     * Sets the update field of the installed extensions with the given [availableExtensions].
     *
     * @param availableExtensions The list of extensions given by the [api].
     */
    private fun updatedInstalledExtensionsStatuses(availableExtensions: List<Extension.Available>) {
        if (availableExtensions.isEmpty()) {
            preferences.extensionUpdatesCount().set(0)
            return
        }
        val mutInstalledExtensions = installedExtensionsFlow.value.toMutableList()
        var changed = false
        var hasUpdateCount = 0
        for ((index, installedExt) in mutInstalledExtensions.withIndex()) {
            val pkgName = installedExt.pkgName
            val availableExt = availableExtensions.find { it.pkgName == pkgName }

            if (availableExt == null != installedExt.isObsolete) {
                mutInstalledExtensions[index] = installedExt.copy(isObsolete = true)
                changed = true
            }
            if (availableExt != null) {
                val hasUpdate = installedExt.updateExists(availableExt)
                if (installedExt.hasUpdate != hasUpdate) {
                    mutInstalledExtensions[index] =
                        installedExt.copy(
                            hasUpdate = hasUpdate,
                            repoUrl = availableExt.repoUrl,
                        )
                    hasUpdateCount++
                    changed = true
                }
            }
        }
        if (changed) {
            _installedExtensionsFlow.value = mutInstalledExtensions
        }
        preferences.extensionUpdatesCount().set(installedExtensionsFlow.value.count { it.hasUpdate })
    }

    /**
     * Returns a flow of the installation process for the given extension. It will complete
     * once the extension is installed or throws an error. The process will be canceled the scope
     * is canceled before its completion.
     *
     * @param extension The extension to be installed.
     */
    suspend fun installExtension(
        extension: ExtensionInfo,
        scope: CoroutineScope,
    ): Flow<ExtensionIntallInfo> = installer.downloadAndInstall(api.getApkUrl(extension), extension, scope)

    /**
     * Sets the result of the installation of an extension.
     *
     * @param downloadId The id of the download.
     * @param result Whether the extension was installed or not.
     */
    fun setInstallationResult(
        downloadId: Long,
        result: Boolean,
    ) {
        val pkgName = getExtension(downloadId) ?: return
        setInstallationResult(pkgName, result)
    }

    fun cleanUpInstallation(pkgName: String) {
        installer.cleanUpInstallation(pkgName)
    }

    fun setInstallationResult(
        pkgName: String,
        result: Boolean,
    ) {
        installer.setInstallationResult(pkgName, result)
    }

    /** Sets the result of the installation of an extension.
     *
     * @param sessionId The id of the download.
     */
    fun cancelInstallation(sessionId: Int) {
        installer.cancelInstallation(sessionId)
    }

    /**
     * Sets the result of the installation of an extension.
     *
     * @param downloadId The id of the download.
     */
    fun setInstalling(
        downloadId: Long,
        sessionId: Int,
    ) {
        val pkgName = getExtension(downloadId) ?: return
        installer.setInstalling(pkgName, sessionId)
    }

    suspend fun setPending(pkgName: String) {
        installer.setPending(pkgName)
    }

    fun getInstallInfo(pkgName: String): ExtensionIntallInfo? {
        val installStep =
            when {
                installer.downloadInstallerMap[pkgName] != null &&
                    context.packageManager.packageInstaller
                        .getSessionInfo(installer.downloadInstallerMap[pkgName] ?: 0) != null -> {
                    InstallStep.Installing
                }
                installer.activeDownloads[pkgName] != null -> InstallStep.Downloading
                ExtensionInstallerJob
                    .activeInstalls()
                    ?.contains(pkgName) == true -> InstallStep.Pending
                else -> return null
            }
        val sessionInfo =
            run {
                val sessionId = installer.downloadInstallerMap[pkgName]
                if (sessionId != null) {
                    context.packageManager.packageInstaller.getSessionInfo(sessionId)
                } else {
                    null
                }
            }
        return ExtensionIntallInfo(installStep, sessionInfo)
    }

    /**
     * Uninstalls the extension that matches the given package name.
     *
     * @param pkgName The package name of the application to uninstall.
     */
    fun uninstallExtension(pkgName: String) {
        ExtensionLoader.uninstallPrivateExtension(context, pkgName)
        installer.uninstallApk(pkgName)
    }

    /**
     * Adds the given extension to the list of trusted extensions. It also loads in background the
     * now trusted extensions.
     *
     * @param pkgName the package name of the extension to trust
     * @param versionCode the version code of the extension to trust
     * @param signatureHash the signature hash of the extension to trust
     */
    fun trust(
        pkgName: String,
        versionCode: Long,
        signatureHash: String,
    ) {
        val untrustedPkgNames = untrustedExtensionsFlow.value.map { it.pkgName }.toSet()
        if (pkgName !in untrustedPkgNames) return

        launchNow {
            trustExtension.trust(pkgName, versionCode, signatureHash)

            val nowTrustedExtensions =
                untrustedExtensionsFlow.value
                    .filter { it.pkgName == pkgName && it.versionCode == versionCode }
            _untrustedExtensionsFlow.value -= nowTrustedExtensions

            nowTrustedExtensions
                .map { extension ->
                    async { ExtensionLoader.loadExtensionFromPkgName(context, extension.pkgName) }.await()
                }.filterIsInstance<LoadResult.Success>()
                .forEach { registerNewExtension(it.extension) }
        }
    }

    /**
     * Registers the given extension in this and the source managers.
     *
     * @param extension The extension to be registered.
     */
    private fun registerNewExtension(extension: Extension.Installed) {
        _installedExtensionsFlow.value += extension
        emitToInstaller(
            "Finished/${extension.pkgName}",
            ExtensionIntallInfo(InstallStep.Installed, null),
        )
    }

    /**
     * Registers the given updated extension in this and the source managers previously removing
     * the outdated ones.
     *
     * @param extension The extension to be registered.
     */
    private fun registerUpdatedExtension(extension: Extension.Installed) {
        val mutInstalledExtensions = _installedExtensionsFlow.value.toMutableList()
        val oldExtension = mutInstalledExtensions.find { it.pkgName == extension.pkgName }
        if (oldExtension != null) {
            mutInstalledExtensions -= oldExtension
        }
        mutInstalledExtensions += extension
        _installedExtensionsFlow.value = mutInstalledExtensions
        emitToInstaller(
            "Finished/${extension.pkgName}",
            ExtensionIntallInfo(InstallStep.Installed, null),
        )
    }

    fun emitToInstaller(
        name: String,
        extensionInfo: ExtensionIntallInfo,
    ) = installer.emitToFlow(name, extensionInfo)

    /**
     * Unregisters the extension in this and the source managers given its package name. Note this
     * method is called for every uninstalled application in the system.
     *
     * @param pkgName The package name of the uninstalled application.
     */
    private fun unregisterExtension(pkgName: String) {
        val installedExtension = installedExtensionsFlow.value.find { it.pkgName == pkgName }
        if (installedExtension != null) {
            _installedExtensionsFlow.value -= installedExtension
        }
        val untrustedExtension = untrustedExtensionsFlow.value.find { it.pkgName == pkgName }
        if (untrustedExtension != null) {
            _untrustedExtensionsFlow.value -= untrustedExtension
        }
        installer.emitToFlow("Uninstalled/$pkgName", ExtensionIntallInfo(InstallStep.Done, null))
    }

    /**
     * Listener which receives events of the extensions being installed, updated or removed.
     */
    private inner class InstallationListener : ExtensionInstallReceiver.Listener {
        override fun onExtensionInstalled(extension: Extension.Installed) {
            registerNewExtension(extension.withUpdateCheck())
            preferences.extensionUpdatesCount().set(installedExtensionsFlow.value.count { it.hasUpdate })
        }

        override fun onExtensionUpdated(extension: Extension.Installed) {
            registerUpdatedExtension(extension.withUpdateCheck())
            preferences.extensionUpdatesCount().set(installedExtensionsFlow.value.count { it.hasUpdate })
        }

        override fun onExtensionUntrusted(extension: Extension.Untrusted) {
            val installedExtension =
                _installedExtensionsFlow.value
                    .find { it.pkgName == extension.pkgName }
                    ?: return
            _installedExtensionsFlow.value -= installedExtension
            _untrustedExtensionsFlow.value += extension
        }

        override fun onPackageUninstalled(pkgName: String) {
            unregisterExtension(pkgName)
            preferences.extensionUpdatesCount().set(installedExtensionsFlow.value.count { it.hasUpdate })
        }
    }

    /**
     * Extension method to set the update field of an installed extension.
     */
    private fun Extension.Installed.withUpdateCheck(): Extension.Installed = if (updateExists()) copy(hasUpdate = true) else this

    private fun Extension.Installed.updateExists(availableExtension: Extension.Available? = null): Boolean {
        val availableExt =
            availableExtension ?: availableExtensionsFlow.value.find { it.pkgName == pkgName }
                ?: return false

        return (availableExt.versionCode > versionCode || availableExt.libVersion > libVersion)
    }

    @kotlinx.serialization.Serializable
    @Parcelize
    data class ExtensionInfo(
        val apkName: String,
        val pkgName: String,
        val name: String,
        val versionCode: Long,
        val libVersion: Double,
        val repoUrl: String,
    ) : Parcelable {
        constructor(extension: Extension.Available) : this(
            apkName = extension.apkName,
            pkgName = extension.pkgName,
            name = extension.name,
            versionCode = extension.versionCode,
            libVersion = extension.libVersion,
            repoUrl = extension.repoUrl,
        )
    }

    companion object {
        fun canAutoInstallUpdates(checkIfShizukuIsRunning: Boolean = false): Boolean {
            val prefs = Injekt.get<PreferencesHelper>().extensionInstaller().get()
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ||
                (
                    prefs == ExtensionInstaller.SHIZUKU &&
                        (!checkIfShizukuIsRunning || ShizukuInstaller.isShizukuRunning())
                ) ||
                prefs == ExtensionInstaller.PRIVATE
        }
    }
}
