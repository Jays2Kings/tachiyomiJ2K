package eu.kanade.tachiyomi.ui.library

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.bluelinelabs.conductor.Controller
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.base.controller.DialogController

/**
 * This class is used when adding new manga to your library
 */
class AddToLibraryCategoriesDialog<T>(bundle: Bundle? = null) :
    DialogController(bundle) where T : Controller, T : AddToLibraryCategoriesDialog.Listener {

    private var manga: Manga? = null

    private var categories = emptyList<Category>()

    private var preselected = emptyArray<Int>()

    private var position = 0

    constructor(
        target: T,
        manga: Manga,
        categories: List<Category>,
        preselected: Array<Int>,
        position: Int = 0
    ) : this() {

        this.manga = manga
        this.categories = categories
        this.preselected = preselected
        this.position = position
        targetController = target
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {

        return MaterialDialog(activity!!).title(R.string.add_to_library).message(R.string.add_to_categories)
            .listItemsMultiChoice(
                items = categories.map { it.name },
                initialSelection = preselected.toIntArray(),
                allowEmptySelection = true
            ) { _, selections, _ ->
                val newCategories = selections.map { categories[it] }
                (targetController as? Listener)?.updateCategoriesForManga(manga, newCategories)
            }
            .positiveButton(android.R.string.ok)
            .negativeButton(android.R.string.cancel) {
                    (targetController as? Listener)?.addToLibraryCancelled(manga, position)
            }
    }

    interface Listener {
        fun updateCategoriesForManga(manga: Manga?, categories: List<Category>)
        fun addToLibraryCancelled(manga: Manga?, position: Int)
    }
}
