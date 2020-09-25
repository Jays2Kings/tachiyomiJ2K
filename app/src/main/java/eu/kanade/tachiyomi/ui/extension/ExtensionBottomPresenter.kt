package eu.kanade.tachiyomi.ui.extension

import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.ExtensionsChangedListener
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

typealias ExtensionTuple =
    Triple<List<Extension.Installed>, List<Extension.Untrusted>, List<Extension.Available>>

/**
 * Presenter of [ExtensionBottomSheet].
 */
class ExtensionBottomPresenter(
    private val bottomSheet: ExtensionBottomSheet,
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get()
) : ExtensionsChangedListener {
    private var scope = CoroutineScope(Job() + Dispatchers.Default)

    private var extensions = emptyList<ExtensionItem>()

    private var currentDownloads = hashMapOf<String, InstallStep>()

    fun onCreate() {
        scope.launch {
            extensionManager.findAvailableExtensionsAsync()
            extensions = toItems(
                Triple(
                    extensionManager.installedExtensions,
                    extensionManager.untrustedExtensions,
                    extensionManager.availableExtensions
                )
            )
            withContext(Dispatchers.Main) { bottomSheet.setExtensions(extensions) }
            extensionManager.setListener(this@ExtensionBottomPresenter)
        }
    }

    fun onDestroy() {
        extensionManager.removeListener(this)
    }

    fun refreshExtensions() {
        scope.launch {
            extensions = toItems(
                Triple(
                    extensionManager.installedExtensions,
                    extensionManager.untrustedExtensions,
                    extensionManager.availableExtensions
                )
            )
            withContext(Dispatchers.Main) { bottomSheet.setExtensions(extensions) }
        }
    }

    override fun extensionsUpdated() {
        refreshExtensions()
    }

    @Synchronized
    private fun toItems(tuple: ExtensionTuple): List<ExtensionItem> {
        val context = bottomSheet.context
        val activeLangs = preferences.enabledLanguages().getOrDefault()

        val (installed, untrusted, available) = tuple

        val items = mutableListOf<ExtensionItem>()

        val installedSorted = installed.sortedWith(compareBy({ !it.hasUpdate }, { !it.isObsolete }, { it.pkgName }))
        val untrustedSorted = untrusted.sortedBy { it.pkgName }
        val availableSorted = available
            // Filter out already installed extensions and disabled languages
            .filter { avail ->
                installed.none { it.pkgName == avail.pkgName } &&
                    untrusted.none { it.pkgName == avail.pkgName } &&
                    (avail.lang in activeLangs || avail.lang == "all")
            }
            .sortedBy { it.pkgName }

        if (installedSorted.isNotEmpty() || untrustedSorted.isNotEmpty()) {
            val header = ExtensionGroupItem(context.getString(R.string.installed), installedSorted.size + untrustedSorted.size)
            items += installedSorted.map { extension ->
                ExtensionItem(extension, header, currentDownloads[extension.pkgName])
            }
            items += untrustedSorted.map { extension ->
                ExtensionItem(extension, header)
            }
        }
        if (availableSorted.isNotEmpty()) {
            val availableGroupedByLang = availableSorted
                .groupBy { LocaleHelper.getDisplayName(it.lang, context) }
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

    fun getExtensionUpdateCount(): Int = preferences.extensionUpdatesCount().getOrDefault()
    fun getAutoCheckPref() = preferences.automaticExtUpdates()

    @Synchronized
    private fun updateInstallStep(extension: Extension, state: InstallStep): ExtensionItem? {
        val extensions = extensions.toMutableList()
        val position = extensions.indexOfFirst { it.extension.pkgName == extension.pkgName }

        return if (position != -1) {
            val item = extensions[position].copy(installStep = state)
            extensions[position] = item

            this.extensions = extensions
            item
        } else {
            null
        }
    }

    fun installExtension(extension: Extension.Available) {
        extensionManager.installExtension(extension).subscribeToInstallUpdate(extension)
    }

    fun updateExtension(extension: Extension.Installed) {
        extensionManager.updateExtension(extension).subscribeToInstallUpdate(extension)
    }

    private fun Observable<InstallStep>.subscribeToInstallUpdate(extension: Extension) {
        this.doOnNext { currentDownloads[extension.pkgName] = it }
            .doOnUnsubscribe { currentDownloads.remove(extension.pkgName) }
            .map { state -> updateInstallStep(extension, state) }
            .subscribe { item ->
                if (item != null) {
                    bottomSheet.downloadUpdate(item)
                }
            }
    }

    fun uninstallExtension(pkgName: String) {
        extensionManager.uninstallExtension(pkgName)
    }

    fun findAvailableExtensions() {
        extensionManager.findAvailableExtensions()
    }

    fun trustSignature(signatureHash: String) {
        extensionManager.trustSignature(signatureHash)
    }
}
