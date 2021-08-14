package eu.kanade.tachiyomi.ui.extension

import android.widget.TextView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.extension.ExtensionAdapter.OnButtonClickListener
import uy.kohesive.injekt.injectLazy

/**
 * Adapter that holds the catalogue cards.
 *
 * @param listener instance of [OnButtonClickListener].
 */
class ExtensionAdapter(val listener: OnButtonClickListener) :
    FlexibleAdapter<IFlexible<*>>(null, listener, true) {

    val preferences: PreferencesHelper by injectLazy()

    var installedSortOrder = preferences.installedExtensionsOrder().get()

    init {
        setDisplayHeadersAtStartUp(true)
    }

    /**
     * Listener for browse item clicks.
     */
    val buttonClickListener: OnButtonClickListener = listener

    interface OnButtonClickListener {
        fun onButtonClick(position: Int)
        fun onCancelClick(position: Int)
        fun onUpdateAllClicked(position: Int)
        fun onExtSortClicked(view: TextView, position: Int)
    }
}
