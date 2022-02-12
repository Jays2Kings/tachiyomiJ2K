package eu.kanade.tachiyomi.ui.source

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Adapter that holds the catalogue cards.
 *
 * @param controller instance of [BrowseController].
 */
class SourceAdapter(val controller: BrowseController) :
    FlexibleAdapter<IFlexible<*>>(null, controller, true) {

    init {
        setDisplayHeadersAtStartUp(true)
    }

    val sourceListener: SourceListener = controller

    val isMultiLanguage =
        Injekt.get<PreferencesHelper>().enabledLanguages().get().filterNot { it == "all" }.size > 1

    override fun onItemSwiped(position: Int, direction: Int) {
        super.onItemSwiped(position, direction)
        controller.hideCatalogue(position)
    }

    interface SourceListener {
        fun onPinClick(position: Int)
        fun onLatestClick(position: Int)
    }
}
