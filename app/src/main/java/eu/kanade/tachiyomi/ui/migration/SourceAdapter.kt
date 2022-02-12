package eu.kanade.tachiyomi.ui.migration

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Adapter that holds the catalogue cards.
 *
 * @param controller instance of [MigrationController].
 */
class SourceAdapter(val allClickListener: OnAllClickListener) :
    FlexibleAdapter<IFlexible<*>>(null, allClickListener, true) {

    private var items: List<IFlexible<*>>? = null

    val isMultiLanguage =
        Injekt.get<PreferencesHelper>().enabledLanguages().get().filterNot { it == "all" }.size > 1

    init {
        setDisplayHeadersAtStartUp(true)
    }

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
