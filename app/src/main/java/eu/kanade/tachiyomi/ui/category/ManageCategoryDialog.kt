package eu.kanade.tachiyomi.ui.category

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.f2prateek.rx.preferences.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.util.view.visibleIf
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.android.synthetic.main.edit_manga_dialog.view.title
import kotlinx.android.synthetic.main.manga_category_dialog.view.*
import uy.kohesive.injekt.injectLazy

class ManageCategoryDialog(bundle: Bundle? = null) :
    DialogController(bundle) {

    constructor(libraryController: LibraryController, category: Category) : this() {
        this.libraryController = libraryController
        this.category = category
    }

    private lateinit var libraryController: LibraryController
    private lateinit var category: Category

    private var dialogView: View? = null

    private val preferences by injectLazy<PreferencesHelper>()
    private val db by injectLazy<DatabaseHelper>()

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val dialog = MaterialDialog(activity!!).apply {
            title(R.string.manage_category)
            customView(viewRes = R.layout.manga_category_dialog)
            negativeButton(android.R.string.cancel)
            positiveButton(R.string.save) { onPositiveButtonClick() }
        }
        dialogView = dialog.view
        onViewCreated(dialog.view)
        return dialog
    }

    private fun onPositiveButtonClick() {
        val view = dialogView ?: return
        if (category.id ?: 0 <= 0) return
        val text = view.title.text.toString()
        val categoryExists = categoryExists(text)
        if (text.isNotBlank() && !categoryExists && !text.equals(category.name, true)) {
            category.name = text
            db.insertCategory(category).executeAsBlocking()
            libraryController.presenter.getLibrary()
        } else if (categoryExists) {
            activity?.toast(R.string.category_with_name_exists)
        }
        if (!updatePref(preferences.downloadNewCategories(), view.download_new)) {
            preferences.downloadNew().set(false)
        } else {
            preferences.downloadNew().set(true)
        }
        if (preferences.libraryUpdateInterval().getOrDefault() > 0 &&
            !updatePref(preferences.libraryUpdateCategories(), view.include_global)
        ) {
            preferences.libraryUpdateInterval().set(0)
            LibraryUpdateJob.setupTask(0)
        }
    }

    /**
     * Returns true if a category with the given name already exists.
     */
    private fun categoryExists(name: String): Boolean {
        return db.getCategories().executeAsBlocking().any {
            it.name.equals(name, true) && category.id != it.id
        }
    }

    fun onViewCreated(view: View) {
        if (category.id ?: 0 <= 0) {
            view.title.gone()
            view.download_new.gone()
            view.include_global.gone()
            return
        }
        view.edit_categories.setOnClickListener {
            router.popCurrentController()
            router.pushController(CategoryController().withFadeTransaction())
        }
        view.title.hint = category.name
        view.title.append(category.name)
        val downloadNew = preferences.downloadNew().getOrDefault()
        setCheckbox(
            view.download_new,
            preferences.downloadNewCategories(),
            true
        )
        if (downloadNew && preferences.downloadNewCategories().getOrDefault().isEmpty())
            view.download_new.gone()
        else if (!downloadNew)
            view.download_new.visible()
        view.download_new.isChecked =
            preferences.downloadNew().getOrDefault() && view.download_new.isChecked
        setCheckbox(
            view.include_global,
            preferences.libraryUpdateCategories(),
            preferences.libraryUpdateInterval().getOrDefault() > 0
        )
    }

    /** Update a pref based on checkbox, and return if the pref is not empty */
    private fun updatePref(categories: Preference<Set<String>>, box: CompoundButton): Boolean {
        val categoryId = category.id ?: return true
        val updateCategories = categories.getOrDefault().toMutableSet()
        if (box.isChecked) {
            updateCategories.add(categoryId.toString())
        } else {
            updateCategories.remove(categoryId.toString())
        }
        categories.set(updateCategories)
        return updateCategories.isNotEmpty()
    }

    private fun setCheckbox(
        box: CompoundButton,
        categories: Preference<Set<String>>,
        shouldShow: Boolean
    ) {
        val updateCategories = categories.getOrDefault()
        box.visibleIf(updateCategories.isNotEmpty() && shouldShow)
        if (updateCategories.isNotEmpty() && shouldShow) box.isChecked =
            updateCategories.any { category.id == it.toIntOrNull() }
    }
}
