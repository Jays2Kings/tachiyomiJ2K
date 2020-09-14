package eu.kanade.tachiyomi.ui.migration.manga.design

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.bluelinelabs.conductor.Controller
import com.f2prateek.rx.preferences.Preference
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.migration.MigrationFlags
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.setBottomEdge
import eu.kanade.tachiyomi.util.view.setEdgeToEdge
import eu.kanade.tachiyomi.util.view.visible
import kotlinx.android.synthetic.main.migration_bottom_sheet.*
import uy.kohesive.injekt.injectLazy

class MigrationBottomSheetDialog(
    activity: Activity,
    private val listener: StartMigrationListener
) : BottomSheetDialog(activity, R.style.BottomSheetDialogTheme) {

    /**
     * Preferences helper.
     */
    private val preferences by injectLazy<PreferencesHelper>()

    init {
        val view = activity.layoutInflater.inflate(R.layout.migration_bottom_sheet, null)

        setContentView(view)
        if (activity.resources.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            sourceGroup.orientation = LinearLayout.HORIZONTAL
            val params = skip_step.layoutParams as ConstraintLayout.LayoutParams
            params.apply {
                topToBottom = -1
                startToStart = -1
                bottomToBottom = extra_search_param.id
                startToEnd = extra_search_param.id
                endToEnd = sourceGroup.id
                topToTop = extra_search_param.id
                marginStart = 16.dpToPx
            }
            skip_step.layoutParams = params

            val params2 = extra_search_param_text.layoutParams as ConstraintLayout.LayoutParams
            params2.bottomToBottom = options_layout.id
            extra_search_param_text.layoutParams = params2

            val params3 = extra_search_param.layoutParams as ConstraintLayout.LayoutParams
            params3.endToEnd = -1
            extra_search_param.layoutParams = params3
        }
        setEdgeToEdge(activity, view)
        setBottomEdge(
            if (activity.resources.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE) extra_search_param_text
            else skip_step,
            activity
        )
    }

    /**
     * Called when the sheet is created. It initializes the listeners and values of the preferences.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initPreferences()

        // window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        fab.setOnClickListener {
            preferences.skipPreMigration().set(skip_step.isChecked)
            listener.startMigration(
                if (extra_search_param.isChecked && extra_search_param_text.text.isNotBlank()) extra_search_param_text.text.toString() else null
            )
            dismiss()
        }
    }

    /**
     * Init general reader preferences.
     */
    private fun initPreferences() {
        val flags = preferences.migrateFlags().getOrDefault()

        mig_chapters.isChecked = MigrationFlags.hasChapters(flags)
        mig_categories.isChecked = MigrationFlags.hasCategories(flags)
        mig_tracking.isChecked = MigrationFlags.hasTracks(flags)

        mig_chapters.setOnCheckedChangeListener { _, _ -> setFlags() }
        mig_categories.setOnCheckedChangeListener { _, _ -> setFlags() }
        mig_tracking.setOnCheckedChangeListener { _, _ -> setFlags() }

        extra_search_param_text.gone()
        extra_search_param.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                extra_search_param_text.visible()
            } else {
                extra_search_param_text.gone()
            }
        }
        sourceGroup.bindToPreference(preferences.useSourceWithMost())

        skip_step.isChecked = preferences.skipPreMigration().getOrDefault()
        skip_step.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) (listener as? Controller)?.activity?.toast(
                R.string.to_show_again_setting_sources,
                Toast.LENGTH_LONG
            )
        }
    }

    private fun setFlags() {
        var flags = 0
        if (mig_chapters.isChecked) flags = flags or MigrationFlags.CHAPTERS
        if (mig_categories.isChecked) flags = flags or MigrationFlags.CATEGORIES
        if (mig_tracking.isChecked) flags = flags or MigrationFlags.TRACK
        preferences.migrateFlags().set(flags)
    }

    /**
     * Binds a checkbox or switch view with a boolean preference.
     */
    private fun CompoundButton.bindToPreference(pref: Preference<Boolean>) {
        isChecked = pref.getOrDefault()
        setOnCheckedChangeListener { _, isChecked -> pref.set(isChecked) }
    }

    /**
     * Binds a radio group with a boolean preference.
     */
    private fun RadioGroup.bindToPreference(pref: Preference<Boolean>) {
        (getChildAt(pref.getOrDefault().toInt()) as RadioButton).isChecked = true
        setOnCheckedChangeListener { _, value ->
            val index = indexOfChild(findViewById(value))
            pref.set(index == 1)
        }
    }

    private fun Boolean.toInt() = if (this) 1 else 0
}

interface StartMigrationListener {
    fun startMigration(extraParam: String?)
}
