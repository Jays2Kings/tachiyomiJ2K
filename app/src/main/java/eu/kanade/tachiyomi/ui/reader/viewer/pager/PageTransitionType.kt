package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.R

/**
 * Enumeration of different page transition animation types for the pager viewer.
 * Inspired by popular e-reader applications like Moon+ Reader.
 */
enum class PageTransitionType(
    val value: Int,
    val nameRes: Int,
    val descriptionRes: Int? = null,
) {
    NONE(0, R.string.none, R.string.transition_none_description),
    SLIDE(1, R.string.slide, R.string.transition_slide_description),
    FADE(2, R.string.fade, R.string.transition_fade_description),
    FLIP(3, R.string.flip, R.string.transition_flip_description),
    DEPTH(4, R.string.depth, R.string.transition_depth_description),
    ZOOM(5, R.string.zoom, R.string.transition_zoom_description),
    CUBE(6, R.string.cube, R.string.transition_cube_description),
    ACCORDION(7, R.string.accordion, R.string.transition_accordion_description),
    ;

    companion object {
        fun fromValue(value: Int): PageTransitionType = entries.find { it.value == value } ?: SLIDE

        fun fromPreference(preference: Int): PageTransitionType = fromValue(preference)

        val DEFAULT = SLIDE
    }
}
