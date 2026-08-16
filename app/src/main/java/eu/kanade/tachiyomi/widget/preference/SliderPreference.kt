package eu.kanade.tachiyomi.widget.preference

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.TypeSafeSharedPreferences

/**
 * An Int preference rendered as a title/value row with a slider below instead of a list dialog.
 * [entryValues] must be evenly spaced and have at least 2 entries (min, max, and step are derived from it).
 * [valueFormatter] formats the value shown both in the top-right value text and the slider's own
 * floating drag label; defaults to the raw number if not set.
 */
class SliderPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : Preference(context, attrs) {
        var entryValues: List<Int> = emptyList()
        var valueFormatter: ((Int) -> String)? = null
        private var defValue: Int = 0

        override fun getSharedPreferences(): SharedPreferences? = super.getSharedPreferences()?.let(::TypeSafeSharedPreferences)

        init {
            layoutResource = R.layout.preference_slider
            isSelectable = false
        }

        override fun onSetInitialValue(defaultValue: Any?) {
            super.onSetInitialValue(defaultValue)
            defValue = defaultValue?.toString()?.toIntOrNull() ?: defValue
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            val titleView = holder.findViewById(R.id.slider_title) as? MaterialTextView ?: return
            val subtitleView = holder.findViewById(R.id.slider_subtitle) as? MaterialTextView ?: return
            val valueView = holder.findViewById(R.id.slider_value) as? MaterialTextView ?: return
            val slider = holder.findViewById(R.id.slider) as? Slider ?: return
            val values = entryValues
            if (values.size < 2) return

            titleView.text = title
            subtitleView.isVisible = !summary.isNullOrBlank()
            subtitleView.text = summary

            slider.valueFrom = values.first().toFloat()
            slider.valueTo = values.last().toFloat()
            slider.stepSize = (values[1] - values[0]).toFloat()
            slider.setLabelFormatter { value -> formatValue(value.toInt()) }

            val current = (sharedPreferences?.getInt(key, defValue) ?: defValue).coerceIn(values.first(), values.last())
            slider.value = current.toFloat()
            valueView.text = formatValue(current)

            slider.clearOnChangeListeners()
            slider.addOnChangeListener { _, value, fromUser ->
                val intValue = value.toInt()
                valueView.text = formatValue(intValue)
                if (fromUser && callChangeListener(intValue) && isPersistent) {
                    sharedPreferences?.edit { putInt(key, intValue) }
                }
            }
        }

        private fun formatValue(value: Int): String = valueFormatter?.invoke(value) ?: "$value"
    }
