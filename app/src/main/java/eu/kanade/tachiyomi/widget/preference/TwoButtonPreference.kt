package eu.kanade.tachiyomi.widget.preference

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.view.View
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.TypeSafeSharedPreferences

/**
 * A boolean preference rendered as a two-button toggle group instead of a switch.
 * [startTextRes] corresponds to a `true` value, [endTextRes] to `false`.
 */
class TwoButtonPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : Preference(context, attrs) {
        @StringRes var startTextRes: Int = 0

        @StringRes var endTextRes: Int = 0
        private var defValue: Boolean = false
        var inverted = false
        var forceDirection = false

        override fun getSharedPreferences(): SharedPreferences? = super.getSharedPreferences()?.let(::TypeSafeSharedPreferences)

        init {
            widgetLayoutResource = R.layout.preference_widget_button_toggle_group
            isSelectable = false
        }

        override fun onSetInitialValue(defaultValue: Any?) {
            super.onSetInitialValue(defaultValue)
            defValue = defaultValue as? Boolean ?: defValue
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            val group = holder.findViewById(R.id.toggle_group) as? MaterialButtonToggleGroup ?: return
            val startButton = holder.findViewById(R.id.button_start) as? MaterialButton ?: return
            val endButton = holder.findViewById(R.id.button_end) as? MaterialButton ?: return
            startButton.setText(startTextRes)
            endButton.setText(endTextRes)

            group.layoutDirection = if (forceDirection) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_INHERIT
            val current = sharedPreferences?.getBoolean(key, defValue) ?: defValue
            group.check(if (current.xor(inverted)) endButton.id else startButton.id)

            group.clearOnButtonCheckedListeners()
            group.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val value = (checkedId == endButton.id).xor(inverted)
                if (callChangeListener(value) && isPersistent) {
                    sharedPreferences?.edit { putBoolean(key, value) }
                }
            }
        }
    }
