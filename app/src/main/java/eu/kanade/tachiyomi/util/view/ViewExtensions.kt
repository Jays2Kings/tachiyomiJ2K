@file:Suppress("NOTHING_TO_INLINE")

package eu.kanade.tachiyomi.util.view

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Point
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.IdRes
import androidx.annotation.Px
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.graphics.ColorUtils
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.ThemeUtil
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isInNightMode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
    val theme = Injekt.get<PreferencesHelper>().theme()
    if (ThemeUtil.isAMOLEDTheme(theme) && context.isInNightMode()) {
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

inline fun View.visible() {
    visibility = View.VISIBLE
}

inline fun View.invisible() {
    visibility = View.INVISIBLE
}

inline fun View.gone() {
    visibility = View.GONE
}

inline fun View.isVisible(): Boolean {
    return visibility == View.VISIBLE
}

inline fun View.visibleIf(show: Boolean) {
    visibility = if (show) View.VISIBLE else View.GONE
}

inline fun View.visInvisIf(show: Boolean) {
    visibility = if (show) View.VISIBLE else View.INVISIBLE
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

fun View.applyWindowInsetsForRootController(bottomNav: View) {
    viewTreeObserver.addOnGlobalLayoutListener(
        object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (bottomNav.height > 0) {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    setOnApplyWindowInsetsListener { view, insets ->
                        view.updateLayoutParams<FrameLayout.LayoutParams> {
                            bottomMargin = bottomNav.height
                        }
                        insets
                    }
                    requestApplyInsetsWhenAttached()
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

fun BottomSheetDialog.setEdgeToEdge(
    activity: Activity,
    contentView: View,
    setTopMargin: Int = -1
) {
    window?.setBackgroundDrawable(null)
    window?.navigationBarColor = activity.window.navigationBarColor
    val isLight = (activity.window?.decorView?.systemUiVisibility ?: 0) and View
        .SYSTEM_UI_FLAG_LIGHT_STATUS_BAR == View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isLight)
        window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    window?.findViewById<View>(com.google.android.material.R.id.container)?.fitsSystemWindows =
        false
    contentView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View
        .SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

    val insets = activity.window.decorView.rootWindowInsets
    (contentView.parent as View).translationX = (
        insets.systemWindowInsetLeft - insets
            .systemWindowInsetRight
        ).toFloat() / 2f
    if (setTopMargin > 0) (contentView.parent as View).updateLayoutParams<ViewGroup.MarginLayoutParams> {
        height = activity.window.decorView.height - insets.systemWindowInsetTop - setTopMargin
    }
    else if (setTopMargin == 0) contentView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = insets.systemWindowInsetTop
    }
    contentView.requestLayout()
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
fun BottomNavigationView.getItemView(@IdRes id: Int): BottomNavigationItemView? {
    val order = (menu as MenuBuilder).findItemIndex(id)
    return (getChildAt(0) as BottomNavigationMenuView).getChildAt(order) as? BottomNavigationItemView
}
