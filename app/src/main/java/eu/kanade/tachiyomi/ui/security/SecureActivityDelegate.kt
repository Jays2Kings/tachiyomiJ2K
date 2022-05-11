package eu.kanade.tachiyomi.ui.security

import android.app.Activity
import android.content.Intent
import android.view.Window
import android.view.WindowManager
import androidx.biometric.BiometricManager
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.main.SearchActivity
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import uy.kohesive.injekt.injectLazy
import java.util.Date

object SecureActivityDelegate {

    private val preferences by injectLazy<PreferencesHelper>()

    var locked: Boolean = true

    fun setSecure(activity: Activity?) {
        val incognitoMode = preferences.incognitoMode().get()
        val enabled = when (preferences.secureScreen().get()) {
            PreferenceValues.SecureScreenMode.ALWAYS -> true
            PreferenceValues.SecureScreenMode.INCOGNITO -> incognitoMode
            else -> false
        }
        activity?.window?.setSecureScreen(enabled)
    }

    private fun Window.setSecureScreen(enabled: Boolean) {
        if (enabled) {
            setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun promptLockIfNeeded(activity: Activity?, requireSuccess: Boolean = false) {
        if (activity == null || AuthenticatorUtil.isAuthenticating) return
        val lockApp = preferences.useBiometrics().get()
        if (lockApp && BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
            if (isAppLocked()) {
                val intent = Intent(activity, BiometricActivity::class.java)
                intent.putExtra("fromSearch", (activity is SearchActivity) && !requireSuccess)
                activity.startActivity(intent)
                activity.overridePendingTransition(0, 0)
            }
        } else if (lockApp) {
            preferences.useBiometrics().set(false)
        }
    }

    fun shouldBeLocked(): Boolean {
        val lockApp = preferences.useBiometrics().get()
        if (lockApp && isAppLocked()) return true
        return false
    }

    private fun isAppLocked(): Boolean {
        return locked &&
            (
                preferences.lockAfter().get() <= 0 ||
                    Date().time >= preferences.lastUnlock().get() + 60 * 1000 * preferences
                    .lockAfter().get()
                )
    }
}
