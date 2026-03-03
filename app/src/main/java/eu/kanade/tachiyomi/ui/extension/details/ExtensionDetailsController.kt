package eu.kanade.tachiyomi.ui.extension.details

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.preference.DialogPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.ConcatAdapter
import com.google.android.material.snackbar.Snackbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.SharedPreferencesDataStore
import eu.kanade.tachiyomi.data.preference.minusAssign
import eu.kanade.tachiyomi.data.preference.plusAssign
import eu.kanade.tachiyomi.databinding.ExtensionDetailControllerBinding
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.preferenceKey
import eu.kanade.tachiyomi.source.sourcePreferences
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.setting.DSL
import eu.kanade.tachiyomi.ui.setting.defaultValue
import eu.kanade.tachiyomi.ui.setting.onChange
import eu.kanade.tachiyomi.ui.setting.switchPreference
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.view.openInBrowser
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.widget.LinearLayoutManagerAccurateOffset
import eu.kanade.tachiyomi.widget.TachiyomiTextInputEditText.Companion.setIncognito
import eu.kanade.tachiyomi.widget.preference.EditTextResetPreference
import eu.kanade.tachiyomi.widget.preference.ListMatPreference
import eu.kanade.tachiyomi.widget.preference.MultiListMatPreference
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

@SuppressLint("RestrictedApi")
class ExtensionDetailsController(bundle: Bundle? = null) :
    BaseCoroutineController<ExtensionDetailControllerBinding, ExtensionDetailsPresenter>(bundle),
    PreferenceManager.OnDisplayPreferenceDialogListener,
    DialogPreference.TargetFragment {

    private var lastOpenPreferencePosition: Int? = null

    private var preferenceScreen: PreferenceScreen? = null

    private val preferences: PreferencesHelper = Injekt.get()
    private val network: NetworkHelper by injectLazy()

    init {
        setHasOptionsMenu(true)
    }

    constructor(pkgName: String) : this(
        Bundle().apply {
            putString(PKGNAME_KEY, pkgName)
        },
    )

    override fun createBinding(inflater: LayoutInflater) =
        ExtensionDetailControllerBinding.inflate(inflater.cloneInContext(getPreferenceThemeContext()))

    override val presenter = ExtensionDetailsPresenter(args.getString(PKGNAME_KEY)!!)

    override fun getTitle(): String? {
        return resources?.getString(R.string.extension_info)
    }

    @SuppressLint("PrivateResource")
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        scrollViewWith(binding.extensionPrefsRecycler, padBottom = true)

        val extension = presenter.extension ?: return
        val context = view.context

        val themedContext by lazy { getPreferenceThemeContext() }
        val manager = PreferenceManager(themedContext)
        val dataStore = SharedPreferencesDataStore(
            context.getSharedPreferences(extension.getPreferenceKey(), Context.MODE_PRIVATE),
        )
        manager.preferenceDataStore = dataStore
        manager.onDisplayPreferenceDialogListener = this
        val screen = manager.createPreferenceScreen(themedContext)
        preferenceScreen = screen

        val multiSource = extension.sources.size > 1
        val isMultiLangSingleSource = multiSource && extension.sources.map { it.name }.distinct().size == 1
        val langauges = preferences.enabledLanguages().get()

        for (source in extension.sources.sortedByDescending { it.isLangEnabled(langauges) }) {
            addPreferencesForSource(screen, source, multiSource, isMultiLangSingleSource)
        }

        manager.setPreferences(screen)

        binding.extensionPrefsRecycler.layoutManager = LinearLayoutManagerAccurateOffset(context)
        val concatAdapterConfig = ConcatAdapter.Config.Builder()
            .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS)
            .build()
        screen.setShouldUseGeneratedIds(true)
        val extHeaderAdapter = ExtensionDetailsHeaderAdapter(presenter)
        extHeaderAdapter.setHasStableIds(true)
        binding.extensionPrefsRecycler.adapter = ConcatAdapter(
            concatAdapterConfig,
            extHeaderAdapter,
            PreferenceGroupAdapter(screen),
        )
        binding.extensionPrefsRecycler.addItemDecoration(ExtensionSettingsDividerItemDecoration(context))
    }

    override fun onDestroyView(view: View) {
        preferenceScreen = null
        super.onDestroyView(view)
    }

    fun onExtensionUninstalled() {
        router.popCurrentController()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        lastOpenPreferencePosition?.let { outState.putInt(LASTOPENPREFERENCE_KEY, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        lastOpenPreferencePosition = savedInstanceState.get(LASTOPENPREFERENCE_KEY) as? Int
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.extension_details, menu)
        menu.findItem(R.id.action_open_repo)?.isVisible = presenter.extension?.repoUrl != null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_open_repo -> openRepo()
            R.id.action_clear_cookies -> clearCookies()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun clearCookies() {
        val urls = presenter.extension?.sources
            ?.filterIsInstance<HttpSource>()
            ?.map { it.baseUrl }
            ?.distinct() ?: emptyList()

        val cleared = urls.sumOf {
            network.cookieJar.remove(it.toHttpUrl(), null, -1)
        }

        Timber.d("Cleared $cleared cookies for: ${urls.joinToString()}")
        val context = view?.context ?: return
        binding.coordinator.snack(context.getString(R.string.cookies_cleared))
    }

    private fun openRepo() {
        val regex = """https://(?:raw.githubusercontent.com|github.com)/(.+?)/(.+?)/.+""".toRegex()
        val url = regex.find(presenter.extension?.repoUrl.orEmpty())
            ?.let {
                val (user, repo) = it.destructured
                "https://github.com/$user/$repo"
            }
            ?: presenter.extension?.repoUrl ?: return
        openInBrowser(url)
    }

    private fun createUrl(url: String, pkgName: String, pkgFactory: String?, path: String = ""): String {
        return if (!pkgFactory.isNullOrEmpty()) {
            when (path.isEmpty()) {
                true -> "$url/multisrc/src/main/java/eu/kanade/tachiyomi/multisrc/$pkgFactory"
                else -> "$url/multisrc/overrides/$pkgFactory/" + (pkgName.split(".").lastOrNull() ?: "") + path
            }
        } else {
            url + "/src/" + pkgName.replace(".", "/") + path
        }
    }

    private fun addPreferencesForSource(screen: PreferenceScreen, source: Source, isMultiSource: Boolean, isMultiLangSingleSource: Boolean) {
        val context = screen.context

        val prefs = mutableListOf<Preference>()
        val block: (@DSL SwitchPreferenceCompat).() -> Unit = {
            key = source.preferenceKey() + "_enabled"
            title = when {
                isMultiSource && !isMultiLangSingleSource -> source.toString()
                else -> LocaleHelper.getSourceDisplayName(source.lang, context)
            }
            isPersistent = false
            isChecked = source.isEnabled()

            onChange { newValue ->
                if (source.isLangEnabled()) {
                    val checked = newValue as Boolean
                    toggleSource(source, checked)
                    prefs.forEach { it.isVisible = checked }
                    true
                } else {
                    binding.coordinator.snack(
                        context.getString(
                            R.string._must_be_enabled_first,
                            title,
                        ),
                        Snackbar.LENGTH_LONG,
                    ) {
                        setAction(R.string.enable) {
                            preferences.enabledLanguages() += source.lang
                            isChecked = true
                            toggleSource(source, true)
                            prefs.forEach { it.isVisible = true }
                        }
                    }
                    false
                }
            }

            // React to enable/disable all changes
            preferences.hiddenSources().asFlow()
                .onEach {
                    val enabled = source.isEnabled()
                    isChecked = enabled
                }
                .launchIn(viewScope)
        }

        screen.switchPreference(block)
        if (source is ConfigurableSource) {
            val newScreen = screen.preferenceManager.createPreferenceScreen(context)
            source.setupPreferenceScreen(newScreen)

            val dataStore = SharedPreferencesDataStore(source.sourcePreferences())
            // Reparent the preferences
            while (newScreen.preferenceCount != 0) {
                val pref = newScreen.getPreference(0)
                pref.isIconSpaceReserved = true
                pref.fragment = "source_${source.id}"
                pref.order = Int.MAX_VALUE
                pref.preferenceDataStore = dataStore
                pref.isVisible = source.isEnabled()

                // Apply incognito IME for EditTextPreference
                if (pref is EditTextPreference) {
                    pref.setOnBindEditTextListener {
                        it.setIncognito(viewScope)
                    }
                }

                prefs.add(pref)
                newScreen.removePreference(pref)
                screen.addPreference(pref)
            }
        }
    }

    private fun toggleSource(source: Source, enable: Boolean) {
        if (enable) {
            preferences.hiddenSources() -= source.id.toString()
        } else {
            preferences.hiddenSources() += source.id.toString()
        }
    }

    private fun getPreferenceThemeContext(): Context {
        val tv = TypedValue()
        activity!!.theme.resolveAttribute(R.attr.preferenceTheme, tv, true)
        return ContextThemeWrapper(activity, tv.resourceId)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (!isAttached) return

        val screen = preference.parent!!

        lastOpenPreferencePosition = (0 until screen.preferenceCount).indexOfFirst {
            screen.getPreference(it) === preference
        }

        val context = preferences.context
        val matPref = when (preference) {
            is EditTextPreference -> EditTextResetPreference(activity, context).apply {
                dialogSummary = preference.dialogMessage
                onPreferenceChangeListener = preference.onPreferenceChangeListener
            }

            is ListPreference -> ListMatPreference(activity, context).apply {
                isPersistent = false
                defaultValue = preference.value
                entries = preference.entries.map { it.toString() }
                entryValues = preference.entryValues.map { it.toString() }
                onChange {
                    if (preference.callChangeListener(it)) {
                        preference.value = it as? String
                        true
                    } else {
                        false
                    }
                }
            }

            is MultiSelectListPreference -> MultiListMatPreference(activity, context).apply {
                isPersistent = false
                defaultValue = preference.values
                entries = preference.entries.map { it.toString() }
                entryValues = preference.entryValues.map { it.toString() }
                onChange { newValue ->
                    if (newValue is Set<*> && preference.callChangeListener(newValue)) {
                        preference.values = newValue.map { it.toString() }.toSet()
                        true
                    } else {
                        false
                    }
                }
            }

            else -> throw IllegalArgumentException(
                "Tried to display dialog for unknown " +
                    "preference type. Did you forget to override onDisplayPreferenceDialog()?",
            )
        }
        matPref.apply {
            key = preference.key
            preferenceDataStore = preference.preferenceDataStore
            title = (preference as? DialogPreference)?.dialogTitle ?: preference.title
        }.performClick()
    }

    private fun Source.isEnabled(): Boolean {
        return id.toString() !in preferences.hiddenSources().get() && isLangEnabled()
    }

    private fun Source.isLangEnabled(langs: Set<String>? = null): Boolean {
        return lang in (langs ?: preferences.enabledLanguages().get())
    }

    private fun Extension.getPreferenceKey(): String = "extension_$pkgName"

    @Suppress("UNCHECKED_CAST")
    override fun <T : Preference> findPreference(key: CharSequence): T? {
        // We track [lastOpenPreferencePosition] when displaying the dialog
        // [key] isn't useful since there may be duplicates
        return preferenceScreen!!.getPreference(lastOpenPreferencePosition!!) as T
    }

    private companion object {
        const val PKGNAME_KEY = "pkg_name"
        const val LASTOPENPREFERENCE_KEY = "last_open_preference"
        private const val URL_EXTENSION_BLOB =
            "https://github.com/tachiyomiorg/tachiyomi-extensions/blob/master"
        private const val URL_EXTENSION_COMMITS =
            "https://github.com/tachiyomiorg/tachiyomi-extensions/commits/master"
    }
}
