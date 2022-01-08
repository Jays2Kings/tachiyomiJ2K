package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.NoLoginTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.util.system.openInBrowser
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

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.tracking

        multiSelectListPreferenceMat(activity) {
            key = Keys.autoUpdateSyncReadingAndToggleTrack
            titleRes = R.string.sync_chapters
            noSelectionRes = R.string.never

            entriesRes = arrayOf(R.string.sync_chapters_after_reading, R.string.sync_chapters_after_toggle)
            entryValues = listOf("reading", "toggle")

            defaultValue = listOf("reading")
        }
        switchPreference {
            key = Keys.autoAddTrack
            titleRes = R.string.track_when_adding_to_library
            summaryRes = R.string.only_applies_silent_trackers
            defaultValue = true
        }
        preferenceCategory {
            titleRes = R.string.services

            trackPreference(trackManager.myAnimeList) {
                activity?.openInBrowser(MyAnimeListApi.authUrl(), trackManager.myAnimeList.getLogoColor())
            }
            trackPreference(trackManager.aniList) {
                activity?.openInBrowser(AnilistApi.authUrl(), trackManager.aniList.getLogoColor())
            }
            trackPreference(trackManager.kitsu) {
                val dialog = TrackLoginDialog(trackManager.kitsu, R.string.email)
                dialog.targetController = this@SettingsTrackingController
                dialog.showDialog(router)
            }
            trackPreference(trackManager.shikimori) {
                activity?.openInBrowser(ShikimoriApi.authUrl(), trackManager.shikimori.getLogoColor())
            }
            trackPreference(trackManager.bangumi) {
                activity?.openInBrowser(BangumiApi.authUrl(), trackManager.bangumi.getLogoColor())
            }
            trackPreference(trackManager.komga) {
                trackManager.komga.loginNoop()
                updatePreference(trackManager.komga.id)
            }
        }
    }

    private inline fun PreferenceScreen.trackPreference(
        service: TrackService,
        crossinline login: () -> Unit
    ): LoginPreference {
        return initThenAdd(
            LoginPreference(context).apply {
                key = Keys.trackUsername(service.id)
                title = context.getString(service.nameRes())
            },
            {
                onClick {
                    if (service.isLogged) {
                        if (service is NoLoginTrackService) {
                            service.logout()
                            updatePreference(service.id)
                        } else {
                            val dialog = TrackLogoutDialog(service)
                            dialog.targetController = this@SettingsTrackingController
                            dialog.showDialog(router)
                        }
                    } else {
                        login()
                    }
                }
            }
        )
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        updatePreference(trackManager.myAnimeList.id)
        updatePreference(trackManager.aniList.id)
        updatePreference(trackManager.shikimori.id)
        updatePreference(trackManager.bangumi.id)
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
