package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.CheckBoxPreference
import androidx.preference.DialogPreference
import androidx.preference.DropDownPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import eu.kanade.tachiyomi.widget.preference.IntListMatPreference
import eu.kanade.tachiyomi.widget.preference.ListMatPreference
import eu.kanade.tachiyomi.widget.preference.MultiListMatPreference

@DslMarker
@Target(AnnotationTarget.TYPE)
annotation class DSL

inline fun PreferenceManager.newScreen(block: (@DSL PreferenceScreen).() -> Unit): PreferenceScreen {
    return createPreferenceScreen(context).also { it.block() }
}

inline fun PreferenceGroup.preference(block: (@DSL Preference).() -> Unit): Preference {
    return initThenAdd(Preference(context), block)
}

inline fun PreferenceGroup.switchPreference(block: (@DSL SwitchPreferenceCompat).() -> Unit): SwitchPreferenceCompat {
    return initThenAdd(SwitchPreferenceCompat(context), block)
}

inline fun PreferenceGroup.checkBoxPreference(block: (@DSL CheckBoxPreference).() -> Unit): CheckBoxPreference {
    return initThenAdd(CheckBoxPreference(context), block)
}

inline fun PreferenceGroup.editTextPreference(block: (@DSL EditTextPreference).() -> Unit): EditTextPreference {
    return initThenAdd(EditTextPreference(context), block).also(::initDialog)
}

inline fun PreferenceGroup.dropDownPreference(block: (@DSL DropDownPreference).() -> Unit):
    DropDownPreference {
        return initThenAdd(DropDownPreference(context), block).also(::initDialog)
    }

inline fun PreferenceGroup.listPreference(
    activity: Activity?,
    block: (@DSL ListMatPreference).()
    -> Unit
):
    ListMatPreference {
        return initThenAdd(ListMatPreference(activity, context), block)
    }

inline fun PreferenceGroup.intListPreference(
    activity: Activity?,
    block: (
        @DSL
        IntListMatPreference
    ).() -> Unit
):
    IntListMatPreference {
        return initThenAdd(IntListMatPreference(activity, context), block)
    }

inline fun PreferenceGroup.multiSelectListPreferenceMat(
    activity: Activity?,
    block: (
        @DSL
        MultiListMatPreference
    ).()
    -> Unit
): MultiListMatPreference {
    return initThenAdd(MultiListMatPreference(activity, context), block)
}

inline fun PreferenceScreen.preferenceCategory(block: (@DSL PreferenceCategory).() -> Unit): PreferenceCategory {
    return addThenInit(
        PreferenceCategory(context).apply {
            isIconSpaceReserved = false
        },
        block
    )
}

inline fun PreferenceScreen.preferenceScreen(block: (@DSL PreferenceScreen).() -> Unit): PreferenceScreen {
    return addThenInit(preferenceManager.createPreferenceScreen(context), block)
}

fun initDialog(dialogPreference: DialogPreference) {
    with(dialogPreference) {
        if (dialogTitle == null) {
            dialogTitle = title
        }
    }
}

inline fun <P : Preference> PreferenceGroup.initThenAdd(p: P, block: P.() -> Unit): P {
    return p.apply {
        block()
        this.isIconSpaceReserved = false
        addPreference(this)
    }
}

inline fun <P : Preference> PreferenceGroup.addThenInit(p: P, block: P.() -> Unit): P {
    return p.apply {
        this.isIconSpaceReserved = false
        addPreference(this)
        block()
    }
}

inline fun Preference.onClick(crossinline block: () -> Unit) {
    setOnPreferenceClickListener { block(); true }
}

inline fun Preference.onChange(crossinline block: (Any?) -> Boolean) {
    setOnPreferenceChangeListener { _, newValue -> block(newValue) }
}

var Preference.defaultValue: Any?
    get() = null // set only
    set(value) { setDefaultValue(value) }

var Preference.titleRes: Int
    get() = 0 // set only
    set(value) { setTitle(value) }

var Preference.iconRes: Int
    get() = 0 // set only
    set(value) { icon = VectorDrawableCompat.create(context.resources, value, context.theme) }

var Preference.summaryRes: Int
    get() = 0 // set only
    set(value) { setSummary(value) }

var Preference.iconTint: Int
    get() = 0 // set only
    set(value) { DrawableCompat.setTint(icon, value) }
