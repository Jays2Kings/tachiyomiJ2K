package eu.kanade.tachiyomi.ui.manga

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.app.Dialog
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.addListener
import androidx.core.view.updateLayoutParams
import androidx.transition.ChangeBounds
import androidx.transition.ChangeImageTransform
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.google.android.material.shape.CornerFamily
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.FullCoverDialogBinding
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.animateBlur
import uy.kohesive.injekt.injectLazy

class FullCoverDialog(val controller: MangaDetailsController, drawable: Drawable, private val thumbView: View) :
    Dialog(controller.activity!!, R.style.FullCoverDialogTheme) {

    val activity = controller.activity
    val binding = FullCoverDialogBinding.inflate(LayoutInflater.from(context), null, false)
    val preferences: PreferencesHelper by injectLazy()

    private val ratio = 5f.dpToPx
    private val fullRatio = 0f
    val shortAnimationDuration = (
        activity?.resources?.getInteger(
            android.R.integer.config_shortAnimTime
        ) ?: 0
        ).toLong()

    init {
        setContentView(binding.root)

        binding.touchOutside.setOnClickListener {
            onBackPressed()
        }
        binding.mangaCoverFull.setOnClickListener {
            onBackPressed()
        }

        binding.btnSave.setOnClickListener {
            controller.saveCover()
        }
        binding.btnShare.setOnClickListener {
            controller.shareCover()
        }

        val expandedImageView = binding.mangaCoverFull
        expandedImageView.shapeAppearanceModel =
            expandedImageView.shapeAppearanceModel.toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, ratio)
                .build()

        expandedImageView.setImageDrawable(drawable)

        val rect = Rect()
        thumbView.getGlobalVisibleRect(rect)
        val topInset = activity?.window?.decorView?.rootWindowInsets?.systemWindowInsetTop ?: 0
        val leftInset = activity?.window?.decorView?.rootWindowInsets?.systemWindowInsetLeft ?: 0
        val rightInset = activity?.window?.decorView?.rootWindowInsets?.systemWindowInsetRight ?: 0
        expandedImageView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = thumbView.height
            width = thumbView.width
            topMargin = rect.top - topInset
            leftMargin = rect.left - leftInset
            rightMargin = rect.right - rightInset
            bottomMargin = rect.bottom
            horizontalBias = 0.0f
            verticalBias = 0.0f
        }
        expandedImageView.requestLayout()
        binding.btnShare.alpha = 0f
        binding.btnSave.alpha = 0f

        expandedImageView.post {
            // Hide the thumbnail and show the zoomed-in view. When the animation
            // begins, it will position the zoomed-in view in the place of the
            // thumbnail.
            thumbView.alpha = 0f
            val defMargin = 16.dpToPx
            if (Build.VERSION.SDK_INT >= 31) {
                activity?.window?.decorView?.animateBlur(1f, 20f, 50)?.start()
            }
            expandedImageView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                height = 0
                width = 0
                topMargin = defMargin
                leftMargin = defMargin
                rightMargin = defMargin
                bottomMargin = defMargin
                horizontalBias = 0.5f
                verticalBias = 0.5f
            }

            // TransitionSet for the full cover because using animation for this SUCKS
            val transitionSet = TransitionSet()
            val bound = ChangeBounds()
            transitionSet.addTransition(bound)
            val changeImageTransform = ChangeImageTransform()
            transitionSet.addTransition(changeImageTransform)
            transitionSet.duration = shortAnimationDuration
            TransitionManager.beginDelayedTransition(binding.root, transitionSet)

            AnimatorSet().apply {
                val radiusAnimator = ValueAnimator.ofFloat(ratio, fullRatio).apply {
                    addUpdateListener {
                        val value = it.animatedValue as Float
                        expandedImageView.shapeAppearanceModel =
                            expandedImageView.shapeAppearanceModel.toBuilder()
                                .setAllCorners(CornerFamily.ROUNDED, value)
                                .build()
                    }
                    duration = shortAnimationDuration
                }
                val saveAnimator = ValueAnimator.ofFloat(binding.btnShare.alpha, 1f).apply {
                    addUpdateListener {
                        binding.btnShare.alpha = it.animatedValue as Float
                        binding.btnSave.alpha = it.animatedValue as Float
                    }
                }
                playTogether(radiusAnimator, saveAnimator)
                duration = shortAnimationDuration
                interpolator = DecelerateInterpolator()
                start()
            }
        }

        window?.let { window ->
            window.navigationBarColor = Color.TRANSPARENT
            window.decorView.fitsSystemWindows = true
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility
                .rem(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility
                    .rem(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
            }
        }
    }

    override fun cancel() {
        super.cancel()
        thumbView.alpha = 1f
    }

    override fun dismiss() {
        super.dismiss()
        thumbView.alpha = 1f
    }

    private fun animateBack() {
        val rect2 = Rect()
        thumbView.getGlobalVisibleRect(rect2)
        binding.mangaCoverFull.isClickable = false
        binding.touchOutside.isClickable = false
        val expandedImageView = binding.mangaCoverFull
        val topInset = activity?.window?.decorView?.rootWindowInsets?.systemWindowInsetTop ?: 0
        val leftInset = activity?.window?.decorView?.rootWindowInsets?.systemWindowInsetLeft ?: 0
        val rightInset = activity?.window?.decorView?.rootWindowInsets?.systemWindowInsetRight ?: 0
        expandedImageView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = thumbView.height
            width = thumbView.width
            topMargin = rect2.top - topInset
            leftMargin = rect2.left - leftInset
            rightMargin = rect2.right - rightInset
            bottomMargin = rect2.bottom
            horizontalBias = 0.0f
            verticalBias = 0.0f
        }

        // Zoom out back to tc thumbnail
        val transitionSet2 = TransitionSet()
        val bound2 = ChangeBounds()
        transitionSet2.addTransition(bound2)
        val changeImageTransform2 = ChangeImageTransform()
        transitionSet2.addTransition(changeImageTransform2)
        transitionSet2.duration = shortAnimationDuration
        TransitionManager.beginDelayedTransition(binding.root, transitionSet2)

        if (Build.VERSION.SDK_INT >= 31) {
            activity?.window?.decorView?.animateBlur(20f, 0.1f, 50, true)?.apply {
                startDelay = shortAnimationDuration - 100
            }?.start()
        }
        val attrs = window?.attributes
        val ogDim = attrs?.dimAmount ?: 0.25f

        // AnimationSet for backdrop because idk how to use TransitionSet
        AnimatorSet().apply {
            val radiusAnimator = ValueAnimator.ofFloat(fullRatio, ratio).apply {
                addUpdateListener {
                    val value = it.animatedValue as Float
                    expandedImageView.shapeAppearanceModel =
                        expandedImageView.shapeAppearanceModel.toBuilder()
                            .setAllCorners(CornerFamily.ROUNDED, value)
                            .build()
                }
            }
            val dimAnimator = ValueAnimator.ofFloat(ogDim, 0f).apply {
                addUpdateListener {
                    window?.setDimAmount(it.animatedValue as Float)
                }
            }

            val saveAnimator = ValueAnimator.ofFloat(binding.btnShare.alpha, 0f).apply {
                addUpdateListener {
                    binding.btnShare.alpha = it.animatedValue as Float
                    binding.btnSave.alpha = it.animatedValue as Float
                }
            }

            playTogether(radiusAnimator, dimAnimator, saveAnimator)

            addListener(
                onEnd = {
                    TransitionManager.endTransitions(binding.root)
                    thumbView.alpha = 1f
                    expandedImageView.post {
                        dismiss()
                    }
                },
                onCancel = {
                    TransitionManager.endTransitions(binding.root)
                    thumbView.alpha = 1f
                    expandedImageView.post {
                        dismiss()
                    }
                }
            )
            interpolator = DecelerateInterpolator()
            duration = shortAnimationDuration
        }.start()
    }

    override fun onBackPressed() {
        if (binding.mangaCoverFull.isClickable) {
            animateBack()
        }
    }
}
