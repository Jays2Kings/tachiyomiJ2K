package eu.kanade.tachiyomi.ui.source.latest

import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourcePresenter

/**
 * Controller that shows the latest manga from the catalogue. Inherit [BrowseSourceController].
 */
class LatestUpdatesController(bundle: Bundle) : BrowseSourceController(bundle) {

    constructor(source: CatalogueSource) : this(
        Bundle().apply {
            putLong(SOURCE_ID_KEY, source.id)
        }
    )

    override fun getTitle(): String {
        return presenter.source.name
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        binding.fab.isVisible = false
    }

    override fun createPresenter(): BrowseSourcePresenter {
        return LatestUpdatesPresenter(args.getLong(SOURCE_ID_KEY))
    }

    override fun showFloatingBar() = false

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_search)?.isVisible = false
    }
}
