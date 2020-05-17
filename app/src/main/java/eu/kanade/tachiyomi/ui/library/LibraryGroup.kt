package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.R

object LibraryGroup {

    const val BY_DEFAULT = 0
    const val BY_TAG = 1
    const val BY_SOURCE = 2
    const val BY_STATUS = 3
    const val BY_TRACK_STATUS = 4
    const val UNGROUPED = 5

    fun groupTypeStringRes(type: Int): Int {
        return when (type) {
            BY_STATUS -> R.string.status
            BY_TAG -> R.string.tag
            BY_SOURCE -> R.string.sources
            BY_TRACK_STATUS -> R.string.tracking_status
            UNGROUPED -> R.string.ungrouped
            else -> R.string.categories
        }
    }

    fun groupTypeDrawableRes(type: Int): Int {
        return when (type) {
            BY_STATUS -> R.drawable.ic_progress_clock_24dp
            BY_TAG -> R.drawable.ic_style_24dp
            BY_TRACK_STATUS -> R.drawable.ic_sync_black_24dp
            BY_SOURCE -> R.drawable.ic_browse_24dp
            UNGROUPED -> R.drawable.ic_ungroup_24dp
            else -> R.drawable.ic_label_outline_white_24dp
        }
    }
}
