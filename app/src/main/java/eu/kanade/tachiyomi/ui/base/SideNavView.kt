package eu.kanade.tachiyomi.ui.base

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.transition.TransitionManager
import com.google.android.material.navigation.NavigationBarItemView
import com.google.android.material.navigationrail.NavigationRailMenuView
import com.google.android.material.navigationrail.NavigationRailView
import eu.kanade.tachiyomi.R
import kotlin.math.max
import com.google.android.material.R as MaterialR

/**
 * Navigation rail that hides the standalone recents item while expanded, as the submenu shown covers
 * it. Used as setting visibility of a menu item as hidden has a crossing janky animation
 */
@SuppressLint("RestrictedApi")
class SideNavView : NavigationRailView {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    init {
        setItemIconGravity(ITEM_ICON_GRAVITY_TOP)
        setItemGravity(ITEM_GRAVITY_TOP_CENTER)
        setItemActiveIndicatorExpandedHeight(itemActiveIndicatorHeight)
        setItemActiveIndicatorExpandedMarginHorizontal(itemActiveIndicatorMarginHorizontal)
        setItemActiveIndicatorExpandedPadding(0, 0, 0, 0)
    }

    private val sideNavMenuView: SideNavMenuView?
        get() = menuView as? SideNavMenuView

    // Called from the superclass constructor, so this can't touch anything declared here.
    override fun createNavigationBarMenuView(context: Context): NavigationRailMenuView = SideNavMenuView(context)

    override fun expand() {
        settleRunningTransition()
        super.expand()
    }

    override fun collapse() {
        settleRunningTransition()
        super.collapse()
    }

    // Added as overlapping expand/collapse transitions break the recents visibility
    private fun settleRunningTransition() {
        (parent as? ViewGroup)?.let(TransitionManager::endTransitions)
    }

    /**
     * Checks [itemId] without the menu animating it. That animation garbles the rail when it runs
     * alongside the expand/collapse one, as both move the same items.
     */
    fun setCheckedItemImmediately(itemId: Int) {
        val menuView = sideNavMenuView
        menuView?.suppressTransitions = true
        try {
            selectedItemId = itemId
        } finally {
            menuView?.suppressTransitions = false
        }
    }
}

@SuppressLint("RestrictedApi")
private class SideNavMenuView(
    context: Context,
) : NavigationRailMenuView(context) {
    /** See [SideNavView.setCheckedItemImmediately]. */
    var suppressTransitions = false

    override fun createNavigationBarItemView(context: Context): NavigationBarItemView = SideNavItemView(context)

    // TransitionManager skips a scene root that isn't laid out, the only seam for turning down the
    // transition the menu starts for itself
    override fun isLaidOut(): Boolean = !suppressTransitions && super.isLaidOut()
}

/** Stands in for the rail's own item view, which is final and package private. */
@SuppressLint("RestrictedApi", "PrivateResource")
private class SideNavItemView(
    context: Context,
) : NavigationBarItemView(context) {
    override fun getItemLayoutResId(): Int = MaterialR.layout.mtrl_navigation_rail_item

    override fun getItemDefaultMarginResId(): Int = MaterialR.dimen.mtrl_navigation_rail_icon_margin

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            setMeasuredDimension(
                measuredWidthAndState,
                max(measuredHeight, MeasureSpec.getSize(heightMeasureSpec)),
            )
        }
    }

    // Set here rather than on the rail, as it builds these views fresh whenever the menu changes
    override fun initialize(
        itemData: MenuItemImpl,
        menuType: Int,
    ) {
        super.initialize(itemData, menuType)
        if (itemData.itemId == R.id.nav_settings) {
            setOnlyShowWhenExpanded(true)
        }
    }

    // initialize() and setExpanded() both run the rail's visibility pass, so clamp here instead of
    // at either call site
    override fun setVisibility(visibility: Int) {
        val itemId = itemData?.itemId
        val hidden =
            if (isExpanded) {
                itemId == R.id.nav_recents
            } else {
                itemId == R.id.nav_summary ||
                    itemId == R.id.nav_ungrouped ||
                    itemId == R.id.nav_history ||
                    itemId == R.id.nav_updates
            }
        super.setVisibility(if (hidden) GONE else visibility)
    }
}
