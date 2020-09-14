package eu.kanade.tachiyomi.ui.security

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.biometric.BiometricManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.main.SearchActivity
import uy.kohesive.injekt.injectLazy
import java.util.Date

object SecureActivityDelegate {

    private val preferences by injectLazy<PreferencesHelper>()

    var locked: Boolean = true

    fun setSecure(activity: Activity?, force: Boolean? = null) {
        val enabled = force ?: preferences.secureScreen().getOrDefault()
        if (enabled) {
            activity?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun promptLockIfNeeded(activity: Activity?) {
        if (activity == null) return
        val lockApp = preferences.useBiometrics().getOrDefault()
        if (lockApp && BiometricManager.from(activity).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
            if (isAppLocked()) {
                val intent = Intent(activity, BiometricActivity::class.java)
                intent.putExtra("fromSearch", (activity is SearchActivity))
                activity.startActivity(intent)
                activity.overridePendingTransition(0, 0)
            }
        } else if (lockApp) {
            preferences.useBiometrics().set(false)
        }
    }

    fun shouldBeLocked(): Boolean {
        val lockApp = preferences.useBiometrics().getOrDefault()
        if (lockApp && isAppLocked()) return true
        return false
    }

    private fun isAppLocked(): Boolean {
        return locked &&
            (
                preferences.lockAfter().getOrDefault() <= 0 ||
                    Date().time >= preferences.lastUnlock().getOrDefault() + 60 * 1000 * preferences
                    .lockAfter().getOrDefault()
                )
    }
}
