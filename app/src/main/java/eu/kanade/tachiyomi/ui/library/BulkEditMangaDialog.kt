package eu.kanade.tachiyomi.ui.library

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.children
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.BulkEditMangaDialogBinding
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.system.materialAlertDialog

class BulkEditMangaDialog : DialogController {
    private val mangaIds: LongArray

    private lateinit var binding: BulkEditMangaDialogBinding
    private lateinit var initialState: BulkEditState

    private val displayedTags = mutableListOf<BulkSharedTag>()
    private val tagsToAdd = mutableListOf<String>()
    private val tagsToRemove = mutableListOf<String>()
    private var resetTags = false

    private val libraryController
        get() = targetController as LibraryController

    constructor(target: LibraryController, mangaIds: LongArray) : super(
        Bundle().apply { putLongArray(KEY_MANGA_IDS, mangaIds) },
    ) {
        targetController = target
        this.mangaIds = mangaIds
    }

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        mangaIds = bundle.getLongArray(KEY_MANGA_IDS) ?: longArrayOf()
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        binding = BulkEditMangaDialogBinding.inflate(activity!!.layoutInflater)
        initialState = libraryController.presenter.getBulkEditState(mangaIds.toList())
        displayedTags += initialState.sharedTags

        val statusEntries =
            listOf(activity!!.getString(R.string.bulk_edit_default)) +
                resources!!.getStringArray(R.array.manga_statuses).toList() +
                activity!!.getString(R.string.source_default)
        binding.mangaStatus.setEntries(statusEntries)
        val initialStatusPosition =
            initialState.commonStatus
                ?.coerceIn(SManga.UNKNOWN, SManga.ON_HIATUS)
                ?.plus(1) ?: DEFAULT_POSITION
        binding.mangaStatus.setSelection(initialStatusPosition)

        val seriesTypeEntries =
            listOf(activity!!.getString(R.string.bulk_edit_default)) +
                resources!!.getStringArray(R.array.series_type).toList() +
                activity!!.getString(R.string.source_default)
        binding.seriesType.setEntries(seriesTypeEntries)
        val initialSeriesTypePosition = initialState.commonSeriesType ?: DEFAULT_POSITION
        binding.seriesType.setSelection(initialSeriesTypePosition)

        setupTagEditor()
        binding.resetTags.setOnClickListener {
            resetTags = true
            tagsToAdd.clear()
            tagsToRemove.clear()
            displayedTags.clear()
            displayedTags += initialState.sourceSharedTags
            binding.addTagEditText.text?.clear()
            binding.addTagEditText.isVisible = false
            binding.addTagChip.isVisible = true
            renderSharedTags()
        }
        renderSharedTags()

        return activity!!
            .materialAlertDialog()
            .setTitle(R.string.edit)
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                addTags(false)

                val selectedStatusPosition = binding.mangaStatus.selectedPosition
                val resetStatus = selectedStatusPosition == statusEntries.lastIndex
                val status =
                    selectedStatusPosition
                        .takeIf {
                            it != DEFAULT_POSITION &&
                                it != statusEntries.lastIndex &&
                                it != initialStatusPosition
                        }?.minus(1)

                val selectedSeriesTypePosition = binding.seriesType.selectedPosition
                val resetSeriesType = selectedSeriesTypePosition == seriesTypeEntries.lastIndex
                val seriesType =
                    selectedSeriesTypePosition.takeIf {
                        it != DEFAULT_POSITION &&
                            it != seriesTypeEntries.lastIndex &&
                            it != initialSeriesTypePosition
                    }

                libraryController.presenter.bulkEditManga(
                    mangaIds = mangaIds.toList(),
                    status = status,
                    resetStatus = resetStatus,
                    seriesType = seriesType,
                    resetSeriesType = resetSeriesType,
                    resetTags = resetTags,
                    tagsToAdd = tagsToAdd,
                    tagsToRemove = tagsToRemove,
                )
            }.create()
    }

    private fun setupTagEditor() {
        binding.addTagChip.setOnClickListener {
            binding.addTagChip.isVisible = false
            binding.addTagEditText.isVisible = true
            binding.addTagEditText.requestFocus()
            binding.addTagEditText.post {
                (activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(binding.addTagEditText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        binding.addTagEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) addTags(false)
        }
        binding.addTagEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTags(true)
                true
            } else {
                false
            }
        }
    }

    private fun addTags(closeKeyboard: Boolean) {
        val newTags =
            binding.addTagEditText.text
                ?.toString()
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }

        newTags.forEach(::addTag)
        binding.addTagEditText.text?.clear()
        binding.addTagEditText.isVisible = false
        binding.addTagChip.isVisible = true

        if (closeKeyboard) {
            (activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(binding.addTagEditText.windowToken, 0)
            binding.addTagEditText.clearFocus()
        }
        renderSharedTags()
    }

    private fun addTag(tag: String) {
        if (displayedTags.any { it.name.equals(tag, ignoreCase = true) }) return

        val originalTags = if (resetTags) initialState.sourceSharedTags else initialState.sharedTags
        val originalTag = originalTags.find { it.name.equals(tag, ignoreCase = true) }
        if (originalTag != null) {
            tagsToRemove.removeAll { it.equals(tag, ignoreCase = true) }
            displayedTags += originalTag
            return
        }

        if (tagsToAdd.none { it.equals(tag, ignoreCase = true) }) {
            tagsToAdd += tag
        }
        displayedTags += BulkSharedTag(tag, isCustom = true, removable = true)
    }

    private fun removeTag(tag: BulkSharedTag) {
        if (!tag.removable) return

        displayedTags.removeAll { it.name.equals(tag.name, ignoreCase = true) }
        val wasNew = tagsToAdd.removeAll { it.equals(tag.name, ignoreCase = true) }
        if (!wasNew && tagsToRemove.none { it.equals(tag.name, ignoreCase = true) }) {
            tagsToRemove += tag.name
        }
        renderSharedTags()
    }

    private fun renderSharedTags() {
        val tagGroup = binding.sharedTags
        tagGroup.children
            .toList()
            .filter { it.id != R.id.add_tag_chip && it.id != R.id.add_tag_edit_text }
            .forEach(tagGroup::removeView)

        displayedTags.forEach { tag ->
            val chip =
                LayoutInflater
                    .from(tagGroup.context)
                    .inflate(R.layout.genre_chip, tagGroup, false) as Chip
            chip.id = View.generateViewId()
            chip.text = tag.name
            applyTagColors(chip, tag)
            chip.isCloseIconVisible = tag.removable
            if (tag.removable) {
                chip.setOnCloseIconClickListener { removeTag(tag) }
            }
            tagGroup.addView(chip, (tagGroup.childCount - STATIC_TAG_EDITOR_CHILDREN).coerceAtLeast(0))
        }
    }

    private fun applyTagColors(
        chip: Chip,
        tag: BulkSharedTag,
    ) {
        val backgroundAttr =
            if (tag.isCustom) {
                androidx.appcompat.R.attr.colorPrimary
            } else {
                com.google.android.material.R.attr.colorSurfaceContainerHighest
            }
        val foregroundAttr =
            if (tag.isCustom) {
                com.google.android.material.R.attr.colorOnPrimary
            } else {
                com.google.android.material.R.attr.colorOnSurface
            }
        val background = MaterialColors.getColor(chip, backgroundAttr)
        val foreground = MaterialColors.getColor(chip, foregroundAttr)

        chip.chipBackgroundColor = ColorStateList.valueOf(background)
        chip.setTextColor(foreground)
        chip.closeIconTint = ColorStateList.valueOf(foreground)
    }

    companion object {
        private const val KEY_MANGA_IDS = "manga_ids"
        private const val DEFAULT_POSITION = 0
        private const val STATIC_TAG_EDITOR_CHILDREN = 2
    }
}
