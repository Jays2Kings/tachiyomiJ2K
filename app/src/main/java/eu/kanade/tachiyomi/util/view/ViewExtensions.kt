@file:Suppress("NOTHING_TO_INLINE")

package eu.kanade.tachiyomi.util.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Point
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.FloatRange
import androidx.annotation.IdRes
import androidx.annotation.Px
import androidx.annotation.RequiresApi
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.animation.addListener
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEach
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SmoothScroller
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationBarItemView
import com.google.android.material.navigation.NavigationBarMenuView
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.snackbar.Snackbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.lang.tintText
import eu.kanade.tachiyomi.util.system.ThemeUtil
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.pxToDp
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Returns coordinates of view.
 * Used for animation
 *
 * @return coordinates of view
 */
fun View.getCoordinates() = Point((left + right) / 2, (top + bottom) / 2)

/**
 * Shows a snackbar in this view.
 *
 * @param message the message to show.
 * @param length the duration of the snack.
 * @param f a function to execute in the snack, allowing for example to define a custom action.
 */
fun View.snack(
    message: String,
    length: Int = Snackbar.LENGTH_SHORT,
    f: (Snackbar.() -> Unit)? = null
): Snackbar {
    val snack = Snackbar.make(this, message, length)
    if (f != null) {
        snack.f()
    }
    if (ThemeUtil.isPitchBlack(context)) {
        val textView: TextView =
            snack.view.findViewById(com.google.android.material.R.id.snackbar_text)
        val button: Button? =
            snack.view.findViewById(com.google.android.material.R.id.snackbar_action)
        textView.setTextColor(Color.WHITE)
        button?.setTextColor(Color.WHITE)
        snack.view.backgroundTintList = ColorStateList.valueOf(Color.DKGRAY)
    }
    snack.show()
    return snack
}

fun View.snack(
    resource: Int,
    length: Int = Snackbar.LENGTH_SHORT,
    f: (Snackbar.() -> Unit)? = null
): Snackbar {
    return snack(context.getString(resource), length, f)
}

fun Snackbar.getText(): CharSequence {
    val textView: TextView = view.findViewById(com.google.android.material.R.id.snackbar_text)
    return textView.text
}

inline val View.marginTop: Int
    get() = (layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0

inline val View.marginBottom: Int
    get() = (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0

inline val View.marginRight: Int
    get() = (layoutParams as? ViewGroup.MarginLayoutParams)?.rightMargin ?: 0

inline val View.marginLeft: Int
    get() = (layoutParams as? ViewGroup.MarginLayoutParams)?.leftMargin ?: 0

object RecyclerWindowInsetsListener : View.OnApplyWindowInsetsListener {
    override fun onApplyWindowInsets(v: View, insets: WindowInsets): WindowInsets {
        v.updatePaddingRelative(bottom = insets.systemWindowInsetBottom)
        // v.updatePaddingRelative(bottom = v.paddingBottom + insets.systemWindowInsetBottom)
        return insets
    }
}

fun View.applyBottomAnimatedInsets(bottomMargin: Int = 0, setPadding: Boolean = false) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val setInsets: ((WindowInsets) -> Unit) = { insets ->
        val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime()).bottom
        } else {
            insets.systemWindowInsetBottom
        }
        if (setPadding) {
            updatePaddingRelative(bottom = bottomMargin + bottom)
        } else {
            updateLayoutParams<ViewGroup.MarginLayoutParams> {
                this.bottomMargin = bottom + bottomMargin
            }
        }
    }
    var handleInsets = true
    doOnApplyWindowInsets { _, insets, _ ->
        if (handleInsets) {
            setInsets(insets)
        }
    }

    ViewCompat.setWindowInsetsAnimationCallback(
        this,
        object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                handleInsets = false
                super.onPrepare(animation)
            }

            override fun onStart(
                animation: WindowInsetsAnimationCompat,
                bounds: WindowInsetsAnimationCompat.BoundsCompat
            ): WindowInsetsAnimationCompat.BoundsCompat {
                handleInsets = false
                rootWindowInsets?.let { insets -> setInsets(insets) }
                return super.onStart(animation, bounds)
            }
            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: List<WindowInsetsAnimationCompat>
            ): WindowInsetsCompat {
                insets.toWindowInsets()?.let { setInsets(it) }
                return insets
            }

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                handleInsets = true
                rootWindowInsets?.let { insets -> setInsets(insets) }
            }
        }
    )
}

object ControllerViewWindowInsetsListener : View.OnApplyWindowInsetsListener {
    override fun onApplyWindowInsets(v: View, insets: WindowInsets): WindowInsets {
        v.updateLayoutParams<FrameLayout.LayoutParams> {
            val attrsArray = intArrayOf(android.R.attr.actionBarSize)
            val array = v.context.obtainStyledAttributes(attrsArray)
            topMargin = insets.systemWindowInsetTop + array.getDimensionPixelSize(0, 0)
            array.recycle()
        }
        return insets
    }
}

object HeightTopWindowInsetsListener : View.OnApplyWindowInsetsListener {
    override fun onApplyWindowInsets(v: View, insets: WindowInsets): WindowInsets {
        val topInset = insets.systemWindowInsetTop
        v.setPadding(0, topInset, 0, 0)
        if (v.layoutParams.height != topInset) {
            v.layoutParams.height = topInset
            v.requestLayout()
        }
        return insets
    }
}

fun View.doOnApplyWindowInsets(f: (View, WindowInsets, ViewPaddingState) -> Unit) {
    // Create a snapshot of the view's padding state
    val paddingState = createStateForView(this)
    setOnApplyWindowInsetsListener { v, insets ->
        f(v, insets, paddingState)
        insets
    }
    requestApplyInsetsWhenAttached()
}

fun View.doOnApplyWindowInsetsCompat(f: (View, WindowInsetsCompat, ViewPaddingState) -> Unit) {
    // Create a snapshot of the view's padding state
    val paddingState = createStateForView(this)
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        f(v, insets, paddingState)
        insets
    }
    requestApplyInsetsWhenAttached()
}

fun View.applyWindowInsetsForController() {
    setOnApplyWindowInsetsListener(ControllerViewWindowInsetsListener)
    requestApplyInsetsWhenAttached()
}

fun View.checkHeightThen(f: () -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener(
        object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (height > 0) {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    f()
                }
            }
        }
    )
}

fun View.requestApplyInsetsWhenAttached() {
    if (isAttachedToWindow) {
        requestApplyInsets()
    } else {
        addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.requestApplyInsets()
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            }
        )
    }
}

inline fun <reified T : ViewGroup.LayoutParams> View.updateLayoutParams(block: T.() -> Unit) {
    val params = layoutParams as T
    block(params)
    layoutParams = params
}

inline fun View.updatePadding(
    @Px left: Int = paddingLeft,
    @Px top: Int = paddingTop,
    @Px right: Int = paddingRight,
    @Px bottom: Int = paddingBottom
) {
    setPadding(left, top, right, bottom)
}

private fun createStateForView(view: View) = ViewPaddingState(
    view.paddingLeft,
    view.paddingTop,
    view.paddingRight,
    view.paddingBottom,
    view.paddingStart,
    view.paddingEnd
)

data class ViewPaddingState(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val start: Int,
    val end: Int
)

inline fun View.updatePaddingRelative(
    @Px start: Int = paddingStart,
    @Px top: Int = paddingTop,
    @Px end: Int = paddingEnd,
    @Px bottom: Int = paddingBottom
) {
    setPaddingRelative(start, top, end, bottom)
}

fun setBottomEdge(view: View, activity: Activity) {
    val marginB = view.marginBottom
    view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        bottomMargin = marginB + activity.window.decorView.rootWindowInsets.systemWindowInsetBottom
    }
}

fun SwipeRefreshLayout.setStyle() {
    setColorSchemeColors(context.getResourceColor(R.attr.actionBarTintColor))
    setProgressBackgroundColorSchemeColor(context.getResourceColor(R.attr.colorPrimaryVariant))
}

fun MaterialButton.resetStrokeColor() {
    strokeColor = ColorStateList.valueOf(
        ColorUtils.setAlphaComponent(
            context.getResourceColor(
                R.attr.colorOnSurface
            ),
            31
        )
    )
}

fun TextView.setTextColorRes(@ColorRes id: Int) {
    setTextColor(context.contextCompatColor(id))
}

@SuppressLint("RestrictedApi")
fun NavigationBarView.getItemView(@IdRes id: Int): NavigationBarItemView? {
    val order = (menu as MenuBuilder).findItemIndex(id)
    return (getChildAt(0) as NavigationBarMenuView).getChildAt(order) as? NavigationBarItemView
}

fun RecyclerView.smoothScrollToTop() {
    val linearLayoutManager = layoutManager as? LinearLayoutManager
    if (linearLayoutManager != null) {
        val smoothScroller: SmoothScroller = object : LinearSmoothScroller(context) {
            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }
        }
        smoothScroller.targetPosition = 0
        val firstItemPos = linearLayoutManager.findFirstVisibleItemPosition()
        if (firstItemPos > 15) {
            scrollToPosition(15)
            post {
                linearLayoutManager.startSmoothScroll(smoothScroller)
            }
        } else {
            linearLayoutManager.startSmoothScroll(smoothScroller)
        }
    } else {
        scrollToPosition(0)
    }
}

fun View.rowsForValue(value: Int): Int {
    return rowsForValue((value / 2f) - .5f)
}

fun View.rowsForValue(value: Float): Int {
    val size = 1.5f.pow(value)
    val trueSize = AutofitRecyclerView.MULTIPLE * ((size * 100 / AutofitRecyclerView.MULTIPLE).roundToInt()) / 100f
    val dpWidth = (measuredWidth.pxToDp / 100f).roundToInt()
    return max(1, (dpWidth / trueSize).roundToInt())
}

var View.compatToolTipText: CharSequence?
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        tooltipText
    } else {
        ""
    }
    set(value) {
        ViewCompat.setTooltipText(this, value)
    }

@SuppressLint("RestrictedApi")
inline fun View.popupMenu(
    items: List<Pair<Int, Int>>,
    selectedItemId: Int? = null,
    noinline onMenuItemClick: MenuItem.() -> Unit
): PopupMenu {
    val popup = PopupMenu(context, this, Gravity.NO_GRAVITY)
    items.forEach { (id, stringRes) ->
        popup.menu.add(0, id, 0, stringRes)
    }

    if (selectedItemId != null) {
        val blendedAccent = ColorUtils.blendARGB(
            context.getResourceColor(android.R.attr.colorSecondary),
            context.getResourceColor(android.R.attr.textColorPrimary),
            0.5f
        )
        (popup.menu as? MenuBuilder)?.setOptionalIconsVisible(true)
        val emptyIcon = ContextCompat.getDrawable(context, R.drawable.ic_blank_24dp)
        popup.menu.forEach { item ->
            item.icon = when (item.itemId) {
                selectedItemId -> ContextCompat.getDrawable(context, R.drawable.ic_check_24dp)?.mutate()?.apply {
                    setTint(blendedAccent)
                }
                else -> emptyIcon
            }
            if (item.itemId == selectedItemId) {
                item.title = item.title?.tintText(blendedAccent)
            }
        }
    }

    popup.setOnMenuItemClickListener {
        it.onMenuItemClick()
        true
    }

    popup.show()
    return popup
}

fun Dialog.blurBehindWindow(
    window: Window?,
    blurAmount: Float = 20f,
    onShow: DialogInterface.OnShowListener? = null,
    onDismiss: DialogInterface.OnDismissListener? = null,
    onCancel: DialogInterface.OnCancelListener? = null
) {
    setOnShowListener {
        onShow?.onShow(it)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window?.decorView?.animateBlur(1f, blurAmount, 50)?.start()
        }
    }
    setOnDismissListener {
        onDismiss?.onDismiss(it)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window?.decorView?.animateBlur(blurAmount, 1f, 50, true)?.start()
        }
    }
    setOnCancelListener {
        onCancel?.onCancel(it)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window?.decorView?.animateBlur(blurAmount, 1f, 50, true)?.start()
        }
    }
}

@RequiresApi(31)
fun View.animateBlur(
    @FloatRange(from = 0.1) from: Float,
    @FloatRange(from = 0.1) to: Float,
    duration: Long,
    removeBlurAtEnd: Boolean = false
): ValueAnimator {
    return ValueAnimator.ofFloat(from, to).apply {
        interpolator = FastOutLinearInInterpolator()
        this.duration = duration
        addUpdateListener { animator ->
            val amount = animator.animatedValue as Float
            try {
                setRenderEffect(
                    RenderEffect.createBlurEffect(amount, amount, Shader.TileMode.CLAMP)
                )
            } catch (_: Exception) {}
        }
        if (removeBlurAtEnd) {
            addListener(
                onEnd = {
                    setRenderEffect(null)
                }
            )
        }
    }
}
