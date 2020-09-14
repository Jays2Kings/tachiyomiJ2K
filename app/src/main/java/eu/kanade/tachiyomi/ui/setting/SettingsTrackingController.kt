package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.widget.preference.LoginPreference
import eu.kanade.tachiyomi.widget.preference.TrackLoginDialog
import eu.kanade.tachiyomi.widget.preference.TrackLogoutDialog
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsTrackingController :
    SettingsController(),
    TrackLoginDialog.Listener,
    TrackLogoutDialog.Listener {

    private val trackManager: TrackManager by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.tracking

        switchPreference {
            key = Keys.autoUpdateTrack
            titleRes = R.string.sync_chapters_after_reading
            defaultValue = true
        }
        preferenceCategory {
            titleRes = R.string.services

            trackPreference(trackManager.myAnimeList) {
                onClick {
                    showDialog(trackManager.myAnimeList)
                }
            }
            trackPreference(trackManager.aniList) {
                onClick {
                    showDialog(trackManager.aniList, AnilistApi.authUrl())
                }
            }
            trackPreference(trackManager.kitsu) {
                onClick {
                    showDialog(trackManager.kitsu, userNameLabel = context.getString(R.string.email))
                }
            }
            trackPreference(trackManager.shikimori) {
                onClick {
                    showDialog(trackManager.shikimori, ShikimoriApi.authUrl())
                }
            }
            trackPreference(trackManager.bangumi) {
                onClick {
                    showDialog(trackManager.bangumi, BangumiApi.authUrl())
                }
            }
        }
    }

    inline fun PreferenceScreen.trackPreference(
        service: TrackService,
        block: (@DSL LoginPreference).() -> Unit
    ): LoginPreference {
        return initThenAdd(
            LoginPreference(context).apply {
                key = Keys.trackUsername(service.id)
                title = service.name
            },
            block
        )
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        // Manually refresh anilist holder
        updatePreference(trackManager.aniList.id)
        updatePreference(trackManager.shikimori.id)
        updatePreference(trackManager.bangumi.id)
    }

    private fun showDialog(trackService: TrackService, url: Uri? = null, userNameLabel: String? = null) {
        if (trackService.isLogged) {
            val dialog = TrackLogoutDialog(trackService)
            dialog.targetController = this@SettingsTrackingController
            dialog.showDialog(router)
        } else if (url == null) {
            val dialog = TrackLoginDialog(trackService, userNameLabel)
            dialog.targetController = this@SettingsTrackingController
            dialog.showDialog(router)
        } else {
            val tabsIntent = CustomTabsIntent.Builder()
                .setToolbarColor(activity!!.getResourceColor(R.attr.colorPrimaryVariant))
                .build()
            tabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            tabsIntent.launchUrl(activity!!, url)
        }
    }

    private fun updatePreference(id: Int) {
        val pref = findPreference(Keys.trackUsername(id)) as? LoginPreference
        pref?.notifyChanged()
    }

    override fun trackLoginDialogClosed(service: TrackService) {
        updatePreference(service.id)
    }

    override fun trackLogoutDialogClosed(service: TrackService) {
        updatePreference(service.id)
    }
}
