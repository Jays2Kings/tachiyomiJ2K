package eu.kanade.tachiyomi.ui.migration

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.migration.manga.design.PreMigrationController
import eu.kanade.tachiyomi.util.system.await
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.scrollViewWith
import kotlinx.android.synthetic.main.migration_controller.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationController :
    NucleusController<MigrationPresenter>(),
    FlexibleAdapter.OnItemClickListener,
    SourceAdapter.OnAllClickListener,
    MigrationInterface {

    private var adapter: FlexibleAdapter<IFlexible<*>>? = null

    private var title: String? = null
        set(value) {
            field = value
            setTitle()
        }

    override fun createPresenter(): MigrationPresenter {
        return MigrationPresenter()
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.migration_controller, container, false)
    }

    fun searchController(manga: Manga): SearchController {
        val controller = SearchController(manga)
        controller.targetController = this

        return controller
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        scrollViewWith(migration_recycler, padBottom = true)

        adapter = FlexibleAdapter(null, this)
        migration_recycler.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(view.context)
        migration_recycler.adapter = adapter
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
    }

    override fun getTitle(): String? {
        return title
    }

    override fun handleBack(): Boolean {
        return if (presenter.state.selectedSource != null) {
            presenter.deselectSource()
            true
        } else {
            super.handleBack()
        }
    }

    fun render(state: ViewState) {
        if (state.selectedSource == null) {
            title = resources?.getString(R.string.source_migration)
            if (adapter !is SourceAdapter) {
                adapter = SourceAdapter(this)
                migration_recycler.adapter = adapter
            }
            adapter?.updateDataSet(state.sourcesWithManga)
        } else {
            val switching = title == resources?.getString(R.string.source_migration)
            title = state.selectedSource.toString()
            if (adapter !is MangaAdapter) {
                adapter = MangaAdapter(this)
                migration_recycler.adapter = adapter
            }
            adapter?.updateDataSet(state.mangaForSource, true)
            /*if (switching) launchUI {
                migration_recycler.alpha = 0f
                migration_recycler.animate().alpha(1f).setStartDelay(100).setDuration(200).start()
            }*/
        }
    }

    override fun onItemClick(view: View?, position: Int): Boolean {
        val item = adapter?.getItem(position) ?: return false

        if (item is MangaItem) {
            PreMigrationController.navigateToMigration(
                Injekt.get<PreferencesHelper>().skipPreMigration().getOrDefault(),
                router,
                listOf(item.manga.id!!)
            )
        } else if (item is SourceItem) {
            presenter.setSelectedSource(item.source)
        }
        return false
    }

    override fun onAllClick(position: Int) {
        val item = adapter?.getItem(position) as? SourceItem ?: return

        launchUI {
            val manga = Injekt.get<DatabaseHelper>().getFavoriteMangas().asRxSingle().await(
                Schedulers.io()
            )
            val sourceMangas =
                manga.asSequence().filter { it.source == item.source.id }.map { it.id!! }.toList()
            withContext(Dispatchers.Main) {
                PreMigrationController.navigateToMigration(
                    Injekt.get<PreferencesHelper>().skipPreMigration().getOrDefault(),
                    router,
                    sourceMangas
                )
            }
        }
    }

    override fun migrateManga(prevManga: Manga, manga: Manga, replace: Boolean): Manga? {
        presenter.migrateManga(prevManga, manga, replace)
        return null
    }
}

interface MigrationInterface {
    fun migrateManga(prevManga: Manga, manga: Manga, replace: Boolean): Manga?
}
