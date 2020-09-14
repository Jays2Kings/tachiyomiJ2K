package eu.kanade.tachiyomi.ui.extension

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.ui.extension.ExtensionAdapter.OnButtonClickListener

/**
 * Adapter that holds the catalogue cards.
 *
 * @param listener instance of [OnButtonClickListener].
 */
class ExtensionAdapter(val listener: OnButtonClickListener) :
    FlexibleAdapter<IFlexible<*>>(null, listener, true) {

    init {
        setDisplayHeadersAtStartUp(true)
    }

    /**
     * Listener for browse item clicks.
     */
    val buttonClickListener: ExtensionAdapter.OnButtonClickListener = listener

    interface OnButtonClickListener {
        fun onButtonClick(position: Int)
    }
}
