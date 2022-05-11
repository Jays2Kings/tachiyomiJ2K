package eu.kanade.tachiyomi.widget.preference

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder

open class ListMatPreference @JvmOverloads constructor(
    activity: Activity?,
    context: Context,
    attrs: AttributeSet? =
        null,
) :
    MatPreference(activity, context, attrs) {

    var entryValues: List<String> = emptyList()
    var entriesRes: Array<Int>
        get() = emptyArray()
        set(value) { entries = value.map { context.getString(it) } }
    private var defValue: String = ""
    var entries: List<String> = emptyList()

    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        defValue = defaultValue as? String ?: defValue
    }

    override var customSummaryProvider: SummaryProvider<MatPreference>? = SummaryProvider<MatPreference> {
        val index = entryValues.indexOf(sharedPreferences?.getString(key, defValue))
        if (entries.isEmpty() || index == -1) ""
        else entries[index]
    }

    override fun dialog(): MaterialAlertDialogBuilder {
        return super.dialog().apply {
            setListItems()
        }
    }

    @SuppressLint("CheckResult")
    open fun MaterialAlertDialogBuilder.setListItems() {
        val default = entryValues.indexOf(sharedPreferences?.getString(key, defValue) ?: defValue)
        setSingleChoiceItems(entries.toTypedArray(), default) { dialog, pos ->
            val value = entryValues[pos]
            sharedPreferences?.edit { putString(key, value) }
            this@ListMatPreference.summary = this@ListMatPreference.summary
            callChangeListener(value)
            dialog.dismiss()
        }
    }
}
