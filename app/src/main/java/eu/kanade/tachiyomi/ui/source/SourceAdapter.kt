package eu.kanade.tachiyomi.ui.source

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible

/**
 * Adapter that holds the catalogue cards.
 *
 * @param controller instance of [SourceController].
 */
class SourceAdapter(val controller: SourceController) :
    FlexibleAdapter<IFlexible<*>>(null, controller, true) {

    init {
        setDisplayHeadersAtStartUp(true)
    }

    val sourceListener: SourceListener = controller

    override fun onItemSwiped(position: Int, direction: Int) {
        super.onItemSwiped(position, direction)
        controller.hideCatalogue(position)
    }

    interface SourceListener {
        fun onPinClick(position: Int)
        fun onLatestClick(position: Int)
    }
}
