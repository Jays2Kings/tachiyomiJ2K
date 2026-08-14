package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.R

object LibraryGroup {
    const val BY_DEFAULT = 0
    const val BY_TAG = 1
    const val BY_SOURCE = 2
    const val BY_STATUS = 3
    const val BY_TRACK_STATUS = 4
    const val BY_AUTHOR = 6
    const val BY_LANGUAGE = 7
    const val BY_CUSTOM_TAG = 8
    const val UNGROUPED = 5

    fun groupTypeStringRes(
        type: Int,
        hasCategories: Boolean = true,
    ): Int =
        when (type) {
            BY_STATUS -> R.string.status
            BY_TAG -> R.string.tag
            BY_CUSTOM_TAG -> R.string.custom_tags
            BY_SOURCE -> R.string.sources
            BY_TRACK_STATUS -> R.string.tracking_status
            BY_AUTHOR -> R.string.author
            BY_LANGUAGE -> R.string.language
            UNGROUPED -> R.string.ungrouped
            else -> if (hasCategories) R.string.categories else R.string.ungrouped
        }

    fun groupTypeDrawableRes(type: Int): Int =
        when (type) {
            BY_STATUS -> R.drawable.ic_progress_clock_24dp
            BY_TAG -> R.drawable.ic_style_24dp
            BY_CUSTOM_TAG -> R.drawable.ic_label_outline_24dp
            BY_TRACK_STATUS -> R.drawable.ic_sync_24dp
            BY_SOURCE -> R.drawable.ic_browse_24dp
            BY_AUTHOR -> R.drawable.ic_author_24dp
            BY_LANGUAGE -> R.drawable.ic_translate_24dp
            UNGROUPED -> R.drawable.ic_ungroup_24dp
            else -> R.drawable.ic_label_outline_24dp
        }
}
