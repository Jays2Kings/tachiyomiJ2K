package eu.kanade.tachiyomi.ui.extension

import android.content.pm.PackageInstaller
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.ExtensionInstallerJob
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.InstalledExtensionsOrder
import eu.kanade.tachiyomi.ui.migration.BaseMigrationPresenter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias ExtensionTuple =
    Triple<List<Extension.Installed>, List<Extension.Untrusted>, List<Extension.Available>>
typealias ExtensionIntallInfo = Pair<InstallStep, PackageInstaller.SessionInfo?>

/**
 * Presenter of [ExtensionBottomSheet].
 */
class ExtensionBottomPresenter : BaseMigrationPresenter<ExtensionBottomSheet>() {

    private var extensions = emptyList<ExtensionItem>()

    private var currentDownloads = hashMapOf<String, ExtensionIntallInfo>()

    private var firstLoad = true

    override fun onCreate() {
        super.onCreate()
        presenterScope.launch {
            val extensionJob = async {
                extensionManager.findAvailableExtensions()
                extensions = toItems(
                    Triple(
                        extensionManager.installedExtensionsFlow.value,
                        extensionManager.untrustedExtensionsFlow.value,
                        extensionManager.availableExtensionsFlow.value,
                    ),
                )
                withContext(Dispatchers.Main) { view?.setExtensions(extensions, false) }
            }
            val migrationJob = async { firstTimeMigration() }
            listOf(migrationJob, extensionJob).awaitAll()
        }
        presenterScope.launch {
            extensionManager.downloadSharedFlow
                .collect {
                    if (it.first.startsWith("Finished") || it.first.startsWith("Uninstalled")) {
                        if (it.first.startsWith("Finished")) {
                            firstLoad = true
                            currentDownloads.clear()
                        }
                        extensions = toItems(
                            Triple(
                                extensionManager.installedExtensionsFlow.value,
                                extensionManager.untrustedExtensionsFlow.value,
                                extensionManager.availableExtensionsFlow.value,
                            ),
                        )
                        withUIContext { view?.setExtensions(extensions) }
                        return@collect
                    }
                    val extension = extensions.find { item ->
                        it.first == item.extension.pkgName
                    } ?: return@collect
                    when (it.second.first) {
                        InstallStep.Installed, InstallStep.Error -> {
                            currentDownloads.remove(extension.extension.pkgName)
                        }
                        else -> {
                            currentDownloads[extension.extension.pkgName] = it.second
                        }
                    }
                    val item = updateInstallStep(extension.extension, it.second.first, it.second.second)
                    if (item != null) {
                        withUIContext { view?.downloadUpdate(item) }
                    }
                }
        }
    }

    fun refreshExtensions() {
        presenterScope.launch {
            extensions = toItems(
                Triple(
                    extensionManager.installedExtensionsFlow.value,
                    extensionManager.untrustedExtensionsFlow.value,
                    extensionManager.availableExtensionsFlow.value,
                ),
            )
            withContext(Dispatchers.Main) { view?.setExtensions(extensions, false) }
        }
    }

    @Synchronized
    private fun toItems(tuple: ExtensionTuple): List<ExtensionItem> {
        val context = view?.context ?: return emptyList()
        val activeLangs = preferences.enabledLanguages().get()
        val showNsfwSources = preferences.showNsfwSources().get()

        val (installed, untrusted, available) = tuple

        val items = mutableListOf<ExtensionItem>()

        if (firstLoad) {
            val listOfExtensions = installed + untrusted + available
            listOfExtensions.forEach {
                val installInfo = extensionManager.getInstallInfo(it.pkgName) ?: return@forEach
                currentDownloads[it.pkgName] = installInfo
            }
            firstLoad = false
        }

        val updatesSorted = installed.filter { it.hasUpdate && (showNsfwSources || !it.isNsfw) }.sortedBy { it.name }
        val sortOrder = InstalledExtensionsOrder.fromPreference(preferences)
        val installedSorted = installed
            .filter { !it.hasUpdate && (showNsfwSources || !it.isNsfw) }
            .sortedWith(
                compareBy(
                    { !it.isObsolete },
                    {
                        when (sortOrder) {
                            InstalledExtensionsOrder.Name -> it.name
                            InstalledExtensionsOrder.RecentlyUpdated -> Long.MAX_VALUE - extensionUpdateDate(it.pkgName)
                            InstalledExtensionsOrder.RecentlyInstalled -> Long.MAX_VALUE - extensionInstallDate(it.pkgName)
                            InstalledExtensionsOrder.Language -> it.lang
                        }
                    },
                    { it.name },
                ),
            )
        val untrustedSorted = untrusted.sortedBy { it.name }
        val availableSorted = available
            // Filter out already installed extensions and disabled languages
            .filter { avail ->
                installed.none { it.pkgName == avail.pkgName } &&
                    untrusted.none { it.pkgName == avail.pkgName } &&
                    (avail.lang in activeLangs) &&
                    (showNsfwSources || !avail.isNsfw)
            }
            .sortedBy { it.name }

        if (updatesSorted.isNotEmpty()) {
            val header = ExtensionGroupItem(
                context.resources.getQuantityString(
                    R.plurals._updates_pending,
                    updatesSorted.size,
                    updatesSorted.size,
                ),
                updatesSorted.size,
                items.count { it.extension.pkgName in currentDownloads.keys } != updatesSorted.size,
            )
            items += updatesSorted.map { extension ->
                ExtensionItem(extension, header, currentDownloads[extension.pkgName])
            }
        }
        if (installedSorted.isNotEmpty() || untrustedSorted.isNotEmpty()) {
            val header = ExtensionGroupItem(context.getString(R.string.installed), installedSorted.size + untrustedSorted.size, installedSorting = preferences.installedExtensionsOrder().get())
            items += installedSorted.map { extension ->
                ExtensionItem(extension, header, currentDownloads[extension.pkgName])
            }
            items += untrustedSorted.map { extension ->
                ExtensionItem(extension, header)
            }
        }
        if (availableSorted.isNotEmpty()) {
            val availableGroupedByLang = availableSorted
                .groupBy { LocaleHelper.getSourceDisplayName(it.lang, context) }
                .toSortedMap()

            availableGroupedByLang
                .forEach {
                    val header = ExtensionGroupItem(it.key, it.value.size)
                    items += it.value.map { extension ->
                        ExtensionItem(extension, header, currentDownloads[extension.pkgName])
                    }
                }
        }

        this.extensions = items
        return items
    }

    private fun extensionInstallDate(pkgName: String): Long {
        val context = view?.context ?: return 0
        return try {
            context.packageManager.getPackageInfo(pkgName, 0).firstInstallTime
        } catch (e: java.lang.Exception) {
            0
        }
    }

    private fun extensionUpdateDate(pkgName: String): Long {
        val context = view?.context ?: return 0
        return try {
            context.packageManager.getPackageInfo(pkgName, 0).lastUpdateTime
        } catch (e: java.lang.Exception) {
            0
        }
    }

    fun getExtensionUpdateCount(): Int = preferences.extensionUpdatesCount().get()

    @Synchronized
    private fun updateInstallStep(
        extension: Extension,
        state: InstallStep?,
        session: PackageInstaller.SessionInfo?,
    ): ExtensionItem? {
        val extensions = extensions.toMutableList()
        val position = extensions.indexOfFirst { it.extension.pkgName == extension.pkgName }

        return if (position != -1) {
            val item = extensions[position].copy(
                installStep = state,
                session = session,
            )
            extensions[position] = item

            this.extensions = extensions
            item
        } else {
            null
        }
    }

    fun cancelExtensionInstall(extItem: ExtensionItem) {
        val sessionId = extItem.session?.sessionId ?: return
        extensionManager.cancelInstallation(sessionId)
    }

    fun installExtension(extension: Extension.Available) {
        presenterScope.launch {
            extensionManager.installExtension(
                ExtensionManager.ExtensionInfo(extension),
                presenterScope,
            )
                .collect {
                    when (it.first) {
                        InstallStep.Installed, InstallStep.Error -> {
                            currentDownloads.remove(extension.pkgName)
                        }
                        else -> {
                            currentDownloads[extension.pkgName] = it
                        }
                    }
                    val item = updateInstallStep(extension, it.first, it.second)
                    if (item != null) {
                        withUIContext { view?.downloadUpdate(item) }
                    }
                }
        }
    }

    fun updateExtension(extension: Extension.Installed) {
        val availableExt =
            extensionManager.availableExtensionsFlow.value.find { it.pkgName == extension.pkgName } ?: return
        installExtension(availableExt)
    }

    fun updateExtensions(extensions: List<Extension.Installed>) {
        if (extensions.isEmpty()) return
        val context = view?.context ?: return
        extensions.forEach {
            val pkgName = it.pkgName
            currentDownloads[pkgName] = InstallStep.Pending to null
            val item = updateInstallStep(it, InstallStep.Pending, null) ?: return@forEach
            view?.downloadUpdate(item)
        }
        ExtensionInstallerJob.start(
            context,
            extensions.mapNotNull { extension ->
                extensionManager.availableExtensionsFlow.value.find { it.pkgName == extension.pkgName }
            },
        )
    }

    fun uninstallExtension(pkgName: String) {
        extensionManager.uninstallExtension(pkgName)
    }

    fun findAvailableExtensions() {
        presenterScope.launch {
            extensionManager.findAvailableExtensions()
        }
    }

    fun trustSignature(signatureHash: String) {
        extensionManager.trustSignature(signatureHash)
    }
}
