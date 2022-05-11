package eu.kanade.tachiyomi.widget.preference

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckedTextView
import androidx.core.content.edit
import androidx.core.view.children
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.util.system.disableItems

class MultiListMatPreference @JvmOverloads constructor(
    activity: Activity?,
    context: Context,
    attrs: AttributeSet? =
        null,
) :
    ListMatPreference(activity, context, attrs) {

    var allSelectionRes: Int? = null
    var noSelectionRes: Int? = null

    /** All item is always selected and uncheckabele */
    var allIsAlwaysSelected = false
        set(value) {
            field = value
            notifyChanged()
        }

    /** All Item is moved to bottom of list if true */
    var showAllLast = false
        set(value) {
            field = value
            notifyChanged()
        }

    private var defValue: Set<String> = emptySet()

    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        defValue = (defaultValue as? Collection<*>).orEmpty().mapNotNull { it as? String }.toSet()
    }

    override var customSummaryProvider: SummaryProvider<MatPreference>? = SummaryProvider<MatPreference> {
        var values = (sharedPreferences?.getStringSet(key, defValue) ?: defValue).mapNotNull { value ->
            entryValues.indexOf(value).takeUnless { it == -1 }
        }.toIntArray().sorted().map { entries[it] }
        allSelectionRes?.let { allRes ->
            when {
                values.isEmpty() -> values = listOf(context.getString(allRes))
                allIsAlwaysSelected && !showAllLast ->
                    values =
                        listOf(context.getString(allRes)) + values
                allIsAlwaysSelected -> values = values + context.getString(allRes)
            }
        }
        if (values.isEmpty()) {
            noSelectionRes?.let { values = listOf(context.getString(it)) }
        }
        values.joinToString()
    }

    @SuppressLint("CheckResult")
    override fun MaterialAlertDialogBuilder.setListItems() {
        val set = sharedPreferences?.getStringSet(key, defValue) ?: defValue
        val items = if (allSelectionRes != null) {
            if (showAllLast) entries + listOf(context.getString(allSelectionRes!!))
            else listOf(context.getString(allSelectionRes!!)) + entries
        } else entries
        val allPos = if (showAllLast) items.size - 1 else 0

        val allValue = booleanArrayOf(set.isEmpty() || allIsAlwaysSelected)
        val selected =
            if (allSelectionRes != null && !showAllLast) { allValue } else { booleanArrayOf() } +
                entryValues.map { it in set }.toBooleanArray() +
                if (allSelectionRes != null && showAllLast) { allValue } else { booleanArrayOf() }
        setPositiveButton(android.R.string.ok) { _, _ ->
            val pos = mutableListOf<Int>()
            for (i in items.indices)
                if (!(allSelectionRes != null && i == allPos) && selected[i]) pos.add(i)
            var value = pos.mapNotNull {
                entryValues.getOrNull(it - if (allSelectionRes != null && !showAllLast) 1 else 0)
            }.toSet()
            if (allSelectionRes != null && !allIsAlwaysSelected && selected[allPos]) value = emptySet()
            sharedPreferences?.edit { putStringSet(key, value) }
            callChangeListener(value)
            notifyChanged()
        }
        setMultiChoiceItems(items.toTypedArray(), selected) { dialog, pos, checked ->
            // The extra changes above sometimes don't work so theres this too
            if (allSelectionRes != null && pos == allPos) {
                val listView = (dialog as? AlertDialog)?.listView ?: return@setMultiChoiceItems
                listView.setItemChecked(pos, !checked)
                listView.children.forEach {
                    val cText = (it as? AppCompatCheckedTextView)?.text ?: return@forEach
                    val cItemIndex: Int = items.indexOf(cText)
                    if (cItemIndex == allPos) {
                        it.setOnClickListener(null)
                        it.isEnabled = false
                        return@setMultiChoiceItems
                    }
                }
                return@setMultiChoiceItems
            }
            selected[pos] = checked
            if (allSelectionRes != null && !allIsAlwaysSelected) {
                if (checked) selected[allPos] = false
                else if (selected.none { it }) selected[allPos] = true
                (dialog as? AlertDialog)?.listView?.setItemChecked(pos, selected[allPos])
            }
        }
    }

    // Extra changes to make sure the all button is disabled
    override fun onShow(dialog: AlertDialog) {
        if (allSelectionRes != null) {
            dialog.disableItems(arrayOf(context.getString(allSelectionRes!!)))
        }
    }
}
