package eu.kanade.tachiyomi.ui.category.addtolibrary

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.select.SelectExtension
import com.mikepenz.fastadapter.select.getSelectExtension
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.databinding.SetCategoriesSheetBinding
import eu.kanade.tachiyomi.ui.category.ManageCategoryDialog
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.updatePaddingRelative
import eu.kanade.tachiyomi.widget.E2EBottomSheetDialog
import uy.kohesive.injekt.injectLazy
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import kotlin.math.max

class SetCategoriesSheet(
    private val activity: Activity,
    private val listManga: List<Manga>,
    var categories: MutableList<Category>,
    var preselected: Array<Int>,
    private val addingToLibrary: Boolean,
    val onMangaAdded: (() -> Unit) = { }
) : E2EBottomSheetDialog<SetCategoriesSheetBinding>(activity) {

    constructor(activity: Activity, manga: Manga, categories: MutableList<Category>, preselected: Array<Int>, addingToLibrary: Boolean, onMangaAdded: () -> Unit) :
        this(activity, listOf(manga), categories, preselected, addingToLibrary, onMangaAdded)

    private val fastAdapter: FastAdapter<AddCategoryItem>
    private val itemAdapter = ItemAdapter<AddCategoryItem>()
    private val selectExtension: SelectExtension<AddCategoryItem>
    private val db: DatabaseHelper by injectLazy()
    override var recyclerView: RecyclerView? = binding.categoryRecyclerView

    override fun createBinding(inflater: LayoutInflater) =
        SetCategoriesSheetBinding.inflate(inflater)
    init {
        binding.toolbarTitle.text = context.getString(
            if (addingToLibrary) {
                R.string.add_x_to
            } else {
                R.string.move_x_to
            },
            if (listManga.size == 1) {
                listManga.first().seriesType(context)
            } else {
                context.getString(R.string.selection).lowercase(Locale.ROOT)
            }
        )

        setOnShowListener {
            updateBottomButtons()
        }
        sheetBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    updateBottomButtons()
                }

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    updateBottomButtons()
                }
            }
        )

        binding.titleLayout.viewTreeObserver.addOnGlobalLayoutListener {
            binding.categoryRecyclerView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                val fullHeight = activity.window.decorView.height
                val insets = activity.window.decorView.rootWindowInsets
                matchConstraintMaxHeight =
                    fullHeight - (insets?.systemWindowInsetTop ?: 0) -
                    binding.titleLayout.height - binding.buttonLayout.height - 75.dpToPx
            }
        }

        fastAdapter = FastAdapter.with(itemAdapter)
        fastAdapter.setHasStableIds(true)
        binding.categoryRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.categoryRecyclerView.adapter = fastAdapter
        itemAdapter.set(categories.map(::AddCategoryItem))
        itemAdapter.adapterItems.forEach { item ->
            item.isSelected = preselected.any { it == item.category.id }
        }

        selectExtension = fastAdapter.getSelectExtension()
        selectExtension.apply {
            isSelectable = true
            multiSelect = true
            setCategoriesButtons()
            selectionListener = object : ISelectionListener<AddCategoryItem> {
                override fun onSelectionChanged(item: AddCategoryItem, selected: Boolean) {
                    setCategoriesButtons()
                }
            }
        }
    }

    fun setCategoriesButtons() {
        binding.addToCategoriesButton.text = context.getString(
            if (addingToLibrary) {
                R.string.add_to_
            } else {
                R.string.move_to_
            },
            when (selectExtension.selections.size) {
                0 -> context.getString(R.string.default_category).lowercase(Locale.ROOT)
                1 -> selectExtension.selectedItems.firstOrNull()?.category?.name ?: ""
                else -> context.resources.getQuantityString(
                    R.plurals.category_plural,
                    selectExtension.selections.size,
                    selectExtension.selections.size
                )
            }
        )
    }

    override fun onStart() {
        super.onStart()
        sheetBehavior.expand()
        sheetBehavior.skipCollapsed = true
        updateBottomButtons()
        binding.root.post {
            binding.categoryRecyclerView.scrollToPosition(
                max(0, itemAdapter.adapterItems.indexOf(selectExtension.selectedItems.firstOrNull()))
            )
        }
    }

    fun updateBottomButtons() {
        val bottomSheet = binding.root.parent as View
        val bottomSheetVisibleHeight = -bottomSheet.top + (activity.window.decorView.height - bottomSheet.height)

        binding.buttonLayout.translationY = bottomSheetVisibleHeight.toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val attrsArray = intArrayOf(android.R.attr.actionBarSize)
        val array = context.obtainStyledAttributes(attrsArray)
        val headerHeight = array.getDimensionPixelSize(0, 0)
        binding.buttonLayout.updatePaddingRelative(
            bottom = activity.window.decorView.rootWindowInsets.systemWindowInsetBottom
        )

        binding.buttonLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = headerHeight + binding.buttonLayout.paddingBottom
        }
        array.recycle()

        binding.cancelButton.setOnClickListener { dismiss() }
        binding.newCategoryButton.setOnClickListener {
            ManageCategoryDialog(null) {
                categories = db.getCategories().executeAsBlocking()
                itemAdapter.set(categories.map(::AddCategoryItem))
                itemAdapter.adapterItems.forEach { item ->
                    item.isSelected = it == item.category.id
                }
                setCategoriesButtons()
            }.show(activity)
        }

        binding.addToCategoriesButton.setOnClickListener {
            addMangaToCategories()
            dismiss()
        }
    }

    private fun addMangaToCategories() {
        if (listManga.size == 1 && !listManga.first().favorite) {
            val manga = listManga.first()
            manga.favorite = !manga.favorite

            manga.date_added = Date().time

            db.insertManga(manga).executeAsBlocking()
        }

        val mc = ArrayList<MangaCategory>()

        val selectedCategories = selectExtension.selectedItems.map(AddCategoryItem::category)
        for (manga in listManga) {
            for (cat in selectedCategories) {
                mc.add(MangaCategory.create(manga, cat))
            }
        }
        db.setMangaCategories(mc, listManga)
        onMangaAdded()
    }
}
