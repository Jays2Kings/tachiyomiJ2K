package eu.kanade.tachiyomi.widget.preference

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.checkItem
import com.afollestad.materialdialogs.list.isItemChecked
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.afollestad.materialdialogs.list.uncheckItem
import eu.kanade.tachiyomi.data.preference.getOrDefault

class MultiListMatPreference @JvmOverloads constructor(
    activity: Activity?,
    context: Context,
    attrs: AttributeSet? =
        null
) :
    ListMatPreference(activity, context, attrs) {

    var allSelectionRes: Int? = null
    var customSummaryRes: Int
        get() = 0
        set(value) { customSummary = context.getString(value) }

    override fun getSummary(): CharSequence {
        if (customSummary != null) return customSummary!!
        return prefs.getStringSet(key, emptySet<String>()).getOrDefault().mapNotNull {
            if (entryValues.indexOf(it) == -1) null
            else entryValues.indexOf(it) + if (allSelectionRes != null) 1 else 0
        }.toIntArray().joinToString(",") {
            entries[it]
        }
    }

    @SuppressLint("CheckResult")
    override fun MaterialDialog.setItems() {
        val set = prefs.getStringSet(key, emptySet()).getOrDefault()
        var default = set.mapNotNull {
            if (entryValues.indexOf(it) == -1) null
            else entryValues.indexOf(it) + if (allSelectionRes != null) 1 else 0
        }
            .toIntArray()
        if (allSelectionRes != null && default.isEmpty()) default = intArrayOf(0)
        val items = if (allSelectionRes != null)
            (listOf(context.getString(allSelectionRes!!)) + entries) else entries
        positiveButton(android.R.string.ok) {
            val pos = mutableListOf<Int>()
            for (i in items.indices)
                if (!(allSelectionRes != null && i == 0) && isItemChecked(i)) pos.add(i)
            var value = pos.map {
                entryValues[it - if (allSelectionRes != null) 1 else 0]
            }?.toSet() ?: emptySet()
            if (allSelectionRes != null && isItemChecked(0)) value = emptySet()
            prefs.getStringSet(key, emptySet()).set(value)
            callChangeListener(value)
            this@MultiListMatPreference.summary = this@MultiListMatPreference.summary
        }
        listItemsMultiChoice(
            items = items,
            allowEmptySelection = true,
            disabledIndices = if (allSelectionRes != null) intArrayOf(0) else null,
            waitForPositiveButton = false,
            initialSelection = default
        ) { _, pos, _ ->
            if (allSelectionRes != null) {
                if (pos.isEmpty()) checkItem(0)
                else uncheckItem(0)
            }
        }
    }
}
