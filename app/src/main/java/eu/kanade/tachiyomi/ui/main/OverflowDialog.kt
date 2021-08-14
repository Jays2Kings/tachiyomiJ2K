package eu.kanade.tachiyomi.ui.main

import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.toggle
import eu.kanade.tachiyomi.databinding.TachiOverflowLayoutBinding
import eu.kanade.tachiyomi.util.lang.withSubtitle
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.openInBrowser
import uy.kohesive.injekt.injectLazy

class OverflowDialog(activity: MainActivity) : Dialog(activity, R.style.OverflowDialogTheme) {

    val binding = TachiOverflowLayoutBinding.inflate(activity.layoutInflater, null, false)
    val preferences: PreferencesHelper by injectLazy()

    init {
        setContentView(binding.root)

        binding.touchOutside.setOnClickListener {
            cancel()
        }
        val incogText = context.getString(R.string.incognito_mode)
        with(binding.incognitoModeItem) {
            val titleText = context.getString(
                if (preferences.incognitoMode().get()) R.string.turn_off_
                else R.string.turn_on_,
                incogText
            )
            val subtitleText = context.getString(R.string.pauses_reading_history)
            text = titleText.withSubtitle(context, subtitleText)
            setIcon(
                if (preferences.incognitoMode().get()) R.drawable.ic_incognito_24dp
                else R.drawable.ic_glasses_24dp
            )
            setOnClickListener {
                preferences.incognitoMode().toggle()
                val incog = preferences.incognitoMode().get()
                val newTitle = context.getString(
                    if (incog) R.string.turn_off_
                    else R.string.turn_on_,
                    incogText
                )
                text = newTitle.withSubtitle(context, subtitleText)
                val drawable = AnimatedVectorDrawableCompat.create(
                    context,
                    if (incog) R.drawable.anim_read_to_incog
                    else R.drawable.anim_incog_to_read
                )
                setIcon(drawable)
                (getIcon() as? AnimatedVectorDrawableCompat)?.start()
            }
        }
        binding.settingsItem.setOnClickListener {
            activity.showSettings()
            dismiss()
        }

        binding.helpItem.setOnClickListener {
            activity.openInBrowser(URL_HELP)
            dismiss()
        }

        binding.aboutItem.text = context.getString(R.string.about).withSubtitle(binding.aboutItem.context, "v${BuildConfig.VERSION_NAME}")

        binding.aboutItem.setOnClickListener {
            activity.showAbout()
            dismiss()
        }

        binding.overflowCardView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = activity.toolbarHeight - 2.dpToPx
        }
        window?.let { window ->
            window.navigationBarColor = Color.TRANSPARENT
            window.decorView.fitsSystemWindows = true
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility
                .rem(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility
                    .rem(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
            }
        }
    }

    private companion object {
        private const val URL_HELP = "https://tachiyomi.org/help/"
    }
}
