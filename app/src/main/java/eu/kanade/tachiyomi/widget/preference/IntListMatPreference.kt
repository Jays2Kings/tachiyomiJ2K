package eu.kanade.tachiyomi.widget.preference

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import eu.kanade.tachiyomi.data.preference.getOrDefault

class IntListMatPreference @JvmOverloads constructor(
    activity: Activity?,
    context: Context,
    attrs:
        AttributeSet? =
            null
) :
    MatPreference(activity, context, attrs) {
    var entryValues: List<Int> = emptyList()
    var entryRange: IntRange
        get() = 0..0
        set(value) { entryValues = value.toList() }
    var entriesRes: Array<Int>
        get() = emptyArray()
        set(value) { entries = value.map { context.getString(it) } }
    private var defValue: Int = 0
    var entries: List<String> = emptyList()

    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        defValue = defaultValue as? Int ?: defValue
    }
    override fun getSummary(): CharSequence {
        if (customSummary != null) return customSummary!!
        if (key == null) return super.getSummary()
        val index = entryValues.indexOf(prefs.getInt(key, defValue).getOrDefault())
        return if (entries.isEmpty() || index == -1) ""
        else entries[index]
    }

    override fun dialog(): MaterialDialog {
        return super.dialog().apply {
            val default = entryValues.indexOf(prefs.getInt(key, defValue).getOrDefault())
            listItemsSingleChoice(
                items = entries,
                waitForPositiveButton = false,
                initialSelection = default
            ) {
                _, pos, _ ->
                val value = entryValues[pos]
                if (key != null)
                    prefs.getInt(key, defValue).set(value)
                callChangeListener(value)
                this@IntListMatPreference.summary = this@IntListMatPreference.summary
                dismiss()
            }
        }
    }
}
