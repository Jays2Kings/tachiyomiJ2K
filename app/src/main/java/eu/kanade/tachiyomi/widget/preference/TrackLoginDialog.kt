package eu.kanade.tachiyomi.widget.preference

import android.os.Bundle
import android.view.View
import br.com.simplepass.loadingbutton.animatedDrawables.ProgressType
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.android.synthetic.main.pref_account_login.view.*
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrackLoginDialog(usernameLabel: String? = null, bundle: Bundle? = null) :
    LoginDialogPreference(usernameLabel, bundle) {

    private val service = Injekt.get<TrackManager>().getService(args.getInt("key"))!!

    override var canLogout = true

    constructor(service: TrackService, usernameLabel: String?) :
        this(usernameLabel, Bundle().apply { putInt("key", service.id) })

    override fun setCredentialsOnView(view: View) = with(view) {
        dialog_title.text = context.getString(R.string.log_in_to_, service.name)
        username.setText(service.getUsername())
        password.setText(service.getPassword())
    }

    override fun checkLogin() {

        v?.apply {
            login.apply {
                progressType = ProgressType.INDETERMINATE
                startAnimation()
            }
            if (username.text.isNullOrBlank() || password.text.isNullOrBlank()) {
                errorResult()
                context.toast(R.string.username_must_not_be_blank)
                return
            }

            val user = username.text.toString()
            val pass = password.text.toString()
            scope.launch {
                try {
                    val result = service.login(user, pass)
                    if (result) {
                        dialog?.dismiss()
                        context.toast(R.string.successfully_logged_in)
                    } else {
                        errorResult()
                    }
                } catch (error: Exception) {
                    errorResult()
                    error.message?.let { context.toast(it) }
                }
            }
        }
    }

    private fun errorResult() {
        v?.apply {
            login.revertAnimation {
                login.text = activity!!.getText(R.string.unknown_error)
            }
        }
    }

    override fun onDialogClosed() {
        super.onDialogClosed()
        (targetController as? Listener)?.trackLoginDialogClosed(service)
    }

    interface Listener {
        fun trackLoginDialogClosed(service: TrackService)
    }
}
