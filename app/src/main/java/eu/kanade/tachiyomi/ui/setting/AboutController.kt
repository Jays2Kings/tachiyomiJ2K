package eu.kanade.tachiyomi.ui.setting

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.updater.UpdateChecker
import eu.kanade.tachiyomi.data.updater.UpdateResult
import eu.kanade.tachiyomi.data.updater.UpdaterService
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.main.ChangelogDialogController
import eu.kanade.tachiyomi.util.lang.toTimestampString
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AboutController : SettingsController() {

    /**
     * Checks for new releases
     */
    private val updateChecker by lazy { UpdateChecker.getUpdateChecker() }

    private val userPreferences: PreferencesHelper by injectLazy()

    private val dateFormat: DateFormat = userPreferences.dateFormat().getOrDefault()

    /**
     * The subscribtion service of the obtained release object
     */
    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val isUpdaterEnabled = BuildConfig.INCLUDE_UPDATER

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.about

        preference {
            titleRes = R.string.website
            val url = "https://tachiyomi.org"
            summary = url
            onClick {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                startActivity(intent)
            }
        }

        preference {
            title = "Discord"
            val url = "https://discord.gg/tachiyomi"
            summary = url
            onClick {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                startActivity(intent)
            }
        }
        preference {
            title = "Github"
            val url = "https://github.com/Jays2Kings/tachiyomiJ2K"
            summary = url
            onClick {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                startActivity(intent)
            }
        }
        preferenceCategory {
            preference {
                titleRes = R.string.whats_new
                onClick {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        if (BuildConfig.DEBUG) {
                            "https://github.com/Jays2Kings/tachiyomiJ2K/commits/master"
                        } else {
                            "https://github.com/Jays2Kings/tachiyomiJ2K/releases/tag/v${BuildConfig.VERSION_NAME}"
                        }.toUri()
                    )
                    startActivity(intent)
                }
            }
            preference {
                titleRes = R.string.version
                summary = if (BuildConfig.DEBUG) "r" + BuildConfig.COMMIT_COUNT
                else BuildConfig.VERSION_NAME

                if (isUpdaterEnabled) {
                    onClick {
                        if (activity!!.isOnline()) {
                            checkVersion()
                        } else {
                            activity!!.toast(R.string.no_network_connection)
                        }
                    }
                }
            }
            preference {
                titleRes = R.string.build_time
                summary = getFormattedBuildTime()

                onClick {
                    ChangelogDialogController().showDialog(router)
                }
            }
            preference {
                titleRes = R.string.open_source_licenses

                onClick {
                    startActivity(Intent(activity, OssLicensesMenuActivity::class.java))
                }
            }
        }
    }

    /**
     * Checks version and shows a user prompt if an update is available.
     */
    private fun checkVersion() {
        if (activity == null) return

        activity?.toast(R.string.searching_for_updates)
        scope.launch {
            val result = try {
                updateChecker.checkForUpdate()
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    activity?.toast(error.message)
                    Timber.e(error)
                }
            }
            when (result) {
                is UpdateResult.NewUpdate<*> -> {
                    val body = result.release.info
                    val url = result.release.downloadLink

                    // Create confirmation window
                    withContext(Dispatchers.Main) {
                        NewUpdateDialogController(body, url).showDialog(router)
                    }
                }
                is UpdateResult.NoNewUpdate -> {
                    withContext(Dispatchers.Main) {
                        activity?.toast(R.string.no_new_updates_available)
                    }
                }
            }
        }
    }

    class NewUpdateDialogController(bundle: Bundle? = null) : DialogController(bundle) {

        constructor(body: String, url: String) : this(
            Bundle().apply {
                putString(BODY_KEY, body)
                putString(URL_KEY, url)
            }
        )

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            return MaterialDialog(activity!!)
                .title(R.string.new_version_available)
                .message(text = args.getString(BODY_KEY) ?: "")
                .positiveButton(R.string.download) {
                    val appContext = applicationContext
                    if (appContext != null) {
                        // Start download
                        val url = args.getString(URL_KEY) ?: ""
                        UpdaterService.start(appContext, url)
                    }
                }
                .negativeButton(R.string.ignore)
        }

        private companion object {
            const val BODY_KEY = "NewUpdateDialogController.body"
            const val URL_KEY = "NewUpdateDialogController.key"
        }
    }

    private fun getFormattedBuildTime(): String {
        try {
            val inputDf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
            inputDf.timeZone = TimeZone.getTimeZone("UTC")
            val buildTime = inputDf.parse(BuildConfig.BUILD_TIME) ?: return BuildConfig.BUILD_TIME

            return buildTime.toTimestampString(dateFormat)
        } catch (e: ParseException) {
            return BuildConfig.BUILD_TIME
        }
    }
}
