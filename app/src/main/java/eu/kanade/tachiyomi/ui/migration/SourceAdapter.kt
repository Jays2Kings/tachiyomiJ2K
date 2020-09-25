package eu.kanade.tachiyomi.ui.migration

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible

/**
 * Adapter that holds the catalogue cards.
 *
 * @param controller instance of [MigrationController].
 */
class SourceAdapter(val controller: MigrationController) :
    FlexibleAdapter<IFlexible<*>>(null, controller, true) {

    private var items: List<IFlexible<*>>? = null

    init {
        setDisplayHeadersAtStartUp(true)
    }

    /**
     * Listener for auto item clicks.
     */
    val allClickListener: OnAllClickListener? = controller

    /**
     * Listener which should be called when user clicks select.
     */
    interface OnAllClickListener {
        fun onAllClick(position: Int)
    }

    override fun updateDataSet(items: MutableList<IFlexible<*>>?) {
        if (this.items !== items) {
            this.items = items
            super.updateDataSet(items)
        }
    }
}
