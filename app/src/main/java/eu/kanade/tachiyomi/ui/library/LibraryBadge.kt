package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.MaterialShapeDrawable
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.UnreadDownloadBadgeBinding
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.spToPx
import eu.kanade.tachiyomi.util.view.makeShapeCorners

class LibraryBadge
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : MaterialCardView(context, attrs) {
        private lateinit var binding: UnreadDownloadBadgeBinding
        val ogRadius = radius
        val roundedRadius = 8.7f.spToPx

        companion object {
            // resources.getIdentifier() is a slow string-based lookup; the lang->drawable mapping
            // never changes at runtime, so cache it once instead of resolving it on every bind.
            private val flagResCache = HashMap<String, Int>()
        }

        override fun onFinishInflate() {
            super.onFinishInflate()
            binding = UnreadDownloadBadgeBinding.bind(this)
            shapeAppearanceModel = makeShapeCorners(ogRadius, ogRadius)
        }

        fun setUnreadDownload(
            unread: Int,
            downloads: Int,
            showTotalChapters: Boolean,
            lang: String?,
            changeShape: Boolean,
            latestChapter: Int? = null,
        ) {
            // Update the unread count and its visibility.

            val unreadBadgeBackground =
                if (showTotalChapters) {
                    context.contextCompatColor(R.color.total_badge)
                } else {
                    context.getResourceColor(R.attr.colorPrimary)
                }

            with(binding.unreadText) {
                isVisible = unread > 0 || unread == -1 || showTotalChapters
                if (!isVisible) {
                    return@with
                }
                text = if (unread == -1) "." else unread.toString()
                setTextColor(
                    // hide the badge text when preference is only show badge
                    when {
                        unread == -1 && !showTotalChapters -> unreadBadgeBackground
                        showTotalChapters -> context.contextCompatColor(R.color.total_badge_text)
                        else -> context.getResourceColor(R.attr.colorOnPrimary)
                    },
                )
                setBackgroundColor(unreadBadgeBackground)
            }

            val latestBadgeBackground = context.getResourceColor(R.attr.colorSecondaryContainer)
            with(binding.latestText) {
                isVisible = latestChapter != null
                if (latestChapter != null) {
                    text = latestChapter.toString()
                    setTextColor(context.getResourceColor(R.attr.colorOnSecondaryContainer))
                    setBackgroundColor(latestBadgeBackground)
                }
            }

            // Update the download count or local status and its visibility.
            with(binding.downloadText) {
                isVisible = downloads == -2 || downloads > 0
                if (!isVisible) {
                    return@with
                }
                text =
                    if (downloads == -2) {
                        resources.getString(R.string.local)
                    } else {
                        downloads.toString()
                    }

                setTextColor(context.getResourceColor(R.attr.colorOnTertiary))
                setBackgroundColor(context.getResourceColor(R.attr.colorTertiary))
            }

            with(binding.langImage) {
                isVisible = !lang.isNullOrBlank()
                if (!lang.isNullOrBlank()) {
                    val flagId = resolveFlagRes(lang).takeIf { it != 0 }
                    if (flagId != null) {
                        setImageResource(flagId)
                    } else {
                        isVisible = false
                    }
                }
            }

            binding.unreadAngle.isVisible = false
            binding.downloadAngle.isVisible = false
            binding.latestAngle.isVisible = false
            val visibleChildren: List<View> =
                (0 until binding.cardConstraint.childCount)
                    .mapNotNull {
                        binding.cardConstraint.getChildAt(it)
                    }.filter { it.isVisible }
            shapeAppearanceModel = shapeAppearanceModel.withCornerSize(ogRadius)
            binding.unreadText.updateLayoutParams { width = LayoutParams.WRAP_CONTENT }
            if (changeShape) {
                val hasUnreadDot = binding.unreadText.isVisible && unread == -1
                if (visibleChildren.size == 1 && hasUnreadDot) {
                    binding.unreadText.setBackgroundColor(unreadBadgeBackground)
                    binding.unreadText.updateLayoutParams { width = (roundedRadius * 2).toInt() }
                    shapeAppearanceModel = shapeAppearanceModel.withCornerSize(roundedRadius)
                } else {
                    val unreadDotIsLast = hasUnreadDot && visibleChildren.lastOrNull() == binding.unreadText
                    shapeAppearanceModel =
                        makeShapeCorners(
                            ogRadius,
                            if (unreadDotIsLast) roundedRadius else ogRadius,
                            unreadDotIsLast,
                        )
                    if (hasUnreadDot) {
                        binding.unreadText.updateLayoutParams { width = (roundedRadius * 1.25f).toInt() }
                    }
                    visibleChildren.forEachIndexed { index, view ->
                        val startRadius = if (index == 0) ogRadius else 0f
                        val endRadius =
                            if (index == visibleChildren.size - 1) {
                                if (view == binding.unreadText && hasUnreadDot) roundedRadius else ogRadius
                            } else {
                                0f
                            }
                        val bgColor =
                            when (view) {
                                binding.downloadText -> context.getResourceColor(R.attr.colorTertiary)
                                binding.unreadText -> unreadBadgeBackground
                                binding.latestText -> latestBadgeBackground
                                else -> context.getResourceColor(R.attr.background)
                            }
                        if (view is ShapeableImageView) {
                            view.shapeAppearanceModel =
                                makeShapeCorners(
                                    topStart = startRadius,
                                    bottomEnd = endRadius,
                                    index == visibleChildren.size - 1 && hasUnreadDot,
                                )
                        } else {
                            view.background =
                                MaterialShapeDrawable(
                                    makeShapeCorners(
                                        topStart = startRadius,
                                        bottomEnd = endRadius,
                                        index == visibleChildren.size - 1 && hasUnreadDot,
                                    ),
                                ).apply {
                                    this.fillColor = ColorStateList.valueOf(bgColor)
                                }
                        }
                    }
                }
            }

            // Show the badge card if any badge segment exists
            isVisible = visibleChildren.isNotEmpty()

            // Show an angled divider before each visible segment that follows another segment.
            binding.unreadAngle.isVisible =
                binding.unreadText.isVisible &&
                visibleChildren.indexOf(binding.unreadText) > 0
            binding.downloadAngle.isVisible =
                binding.downloadText.isVisible &&
                binding.langImage.isVisible
            binding.latestAngle.isVisible =
                binding.latestText.isVisible &&
                visibleChildren.indexOf(binding.latestText) > 0

            binding.unreadAngle.setColorFilter(unreadBadgeBackground)
            binding.latestAngle.setColorFilter(latestBadgeBackground)

            binding.downloadText.updatePaddingRelative(
                start = if (binding.downloadAngle.isVisible) 2.dpToPx else 5.dpToPx,
                end =
                    if (binding.unreadAngle.isVisible || (!binding.unreadText.isVisible && binding.latestAngle.isVisible)) {
                        8.dpToPx
                    } else {
                        5.dpToPx
                    },
            )
            binding.unreadText.updatePaddingRelative(
                start = if (binding.unreadAngle.isVisible) 2.dpToPx else 5.dpToPx,
                end = if (binding.latestAngle.isVisible) 8.dpToPx else 5.dpToPx,
            )
            binding.latestText.updatePaddingRelative(
                start = if (binding.latestAngle.isVisible) 2.dpToPx else 5.dpToPx,
                end = 5.dpToPx,
            )
        }

        fun setChapters(chapters: Int?) {
            setUnreadDownload(chapters ?: 0, 0, chapters != null, null, true)
        }

        /** Returns the flag drawable res id for [lang], or 0 if none exists. */
        private fun resolveFlagRes(lang: String): Int =
            flagResCache.getOrPut(lang) {
                resources
                    .getIdentifier(
                        "ic_flag_${lang.replace("-", "_")}",
                        "drawable",
                        context.packageName,
                    ).takeIf { it != 0 }
                    ?: if (lang.contains("-")) {
                        resources.getIdentifier(
                            "ic_flag_${lang.split("-").first()}",
                            "drawable",
                            context.packageName,
                        )
                    } else {
                        0
                    }
            }

        fun setInLibrary(inLibrary: Boolean) {
            this.isVisible = inLibrary
            binding.unreadAngle.isVisible = false
            binding.unreadText.updatePaddingRelative(start = 5.dpToPx)
            binding.unreadText.isVisible = inLibrary
            binding.unreadText.text = resources.getText(R.string.in_library)
            binding.unreadText.background =
                MaterialShapeDrawable(makeShapeCorners(ogRadius, ogRadius)).apply {
                    this.fillColor =
                        ColorStateList.valueOf(context.getResourceColor(R.attr.colorPrimary))
                }
        }

        fun setDuplicateInLibrary(duplicateInLibrary: Boolean) {
            this.isVisible = duplicateInLibrary
            binding.unreadAngle.isVisible = false
            binding.unreadText.updatePaddingRelative(start = 5.dpToPx)
            binding.unreadText.isVisible = duplicateInLibrary
            binding.unreadText.text = resources.getText(R.string.duplicate_in_library)
            binding.unreadText.background =
                MaterialShapeDrawable(makeShapeCorners(ogRadius, ogRadius)).apply {
                    this.fillColor =
                        ColorStateList.valueOf(context.getResourceColor(R.attr.colorSecondary))
                }
        }
    }
