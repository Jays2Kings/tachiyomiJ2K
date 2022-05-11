package eu.kanade.tachiyomi.util.view

import android.Manifest
import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.math.MathUtils
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.bluelinelabs.conductor.RouterTransaction
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MainActivityBinding
import eu.kanade.tachiyomi.ui.base.SmallToolbarInterface
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.OneWayFadeChangeHandler
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.TabbedInterface
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.ignoredSystemInsets
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.toInt
import eu.kanade.tachiyomi.util.system.toast
import uy.kohesive.injekt.injectLazy
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

fun Controller.setOnQueryTextChangeListener(
    searchView: SearchView?,
    onlyOnSubmit: Boolean = false,
    hideKbOnSubmit: Boolean = true,
    f: (text: String?) -> Boolean,
) {
    searchView?.setOnQueryTextListener(
        object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String?): Boolean {
                if (!onlyOnSubmit && router.backstack.lastOrNull()
                    ?.controller == this@setOnQueryTextChangeListener
                ) {
                    return f(newText)
                }
                return false
            }

            override fun onQueryTextSubmit(query: String?): Boolean {
                if (isControllerVisible) {
                    if (hideKbOnSubmit) {
                        val imm =
                            activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                ?: return f(query)
                        imm.hideSoftInputFromWindow(searchView.windowToken, 0)
                    }
                    return f(query)
                }
                return true
            }
        },
    )
}

fun Controller.removeQueryListener(includeSearchTB: Boolean = true) {
    val searchView =
        activityBinding?.searchToolbar?.menu?.findItem(R.id.action_search)?.actionView as? SearchView
    val searchView2 =
        activityBinding?.toolbar?.menu?.findItem(R.id.action_search)?.actionView as? SearchView
    if (includeSearchTB) {
        searchView?.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?) = true
                override fun onQueryTextChange(newText: String?) = true
            },
        )
    }
    searchView2?.setOnQueryTextListener(
        object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true
            override fun onQueryTextChange(newText: String?) = true
        },
    )
}

fun Controller.liftAppbarWith(recycler: RecyclerView, padView: Boolean = false) {
    if (padView) {
        var appBarHeight = (
            if (fullAppBarHeight ?: 0 > 0) fullAppBarHeight!!
            else activityBinding?.appBar?.attrToolbarHeight ?: 0
            )
        activityBinding!!.toolbar.post {
            if (fullAppBarHeight!! > 0) {
                appBarHeight = fullAppBarHeight!!
                recycler.requestApplyInsets()
            }
        }
        recycler.updatePaddingRelative(
            top = activityBinding!!.toolbar.y.toInt() + appBarHeight,
            bottom = recycler.rootWindowInsetsCompat?.getInsets(systemBars())?.bottom ?: 0,
        )
        recycler.applyBottomAnimatedInsets(setPadding = true) { view, insets ->
            val headerHeight = insets.getInsets(systemBars()).top + appBarHeight
            view.updatePaddingRelative(top = headerHeight)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            recycler.doOnApplyWindowInsetsCompat { view, insets, _ ->
                val headerHeight = insets.getInsets(systemBars()).top + appBarHeight
                view.updatePaddingRelative(
                    top = headerHeight,
                    bottom = insets.getInsets(ime() or systemBars()).bottom,
                )
            }
        }
    } else {
        view?.applyWindowInsetsForController(activityBinding?.appBar?.attrToolbarHeight ?: 0)
        recycler.setOnApplyWindowInsetsListener(RecyclerWindowInsetsListener)
    }

    var toolbarColorAnim: ValueAnimator? = null
    var isToolbarColored = false

    val colorToolbar: (Boolean) -> Unit = f@{ isColored ->
        isToolbarColored = isColored
        toolbarColorAnim?.cancel()
        val floatingBar =
            !(activityBinding?.toolbar?.isVisible == true || activityBinding?.tabsFrameLayout?.isVisible == true)
        val percent = ImageUtil.getPercentOfColor(
            activityBinding!!.appBar.backgroundColor ?: Color.TRANSPARENT,
            activity!!.getResourceColor(R.attr.colorSurface),
            activity!!.getResourceColor(R.attr.colorPrimaryVariant),
        )
        if (floatingBar) {
            setAppBarBG(0f)
            return@f
        }
        toolbarColorAnim = ValueAnimator.ofFloat(percent, isColored.toInt().toFloat())
        toolbarColorAnim?.addUpdateListener { valueAnimator ->
            setAppBarBG(valueAnimator.animatedValue as Float)
        }
        toolbarColorAnim?.start()
    }

    val floatingBar =
        !(activityBinding?.toolbar?.isVisible == true || activityBinding?.tabsFrameLayout?.isVisible == true)
    if (floatingBar) {
        setAppBarBG(0f)
    }

    activityBinding?.appBar?.setToolbarModeBy(this)
    activityBinding?.appBar?.hideBigView(true)
    activityBinding?.appBar?.y = 0f
    activityBinding?.appBar?.updateAppBarAfterY(recycler)

    setAppBarBG(0f)
    recycler.addOnScrollListener(
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (router?.backstack?.lastOrNull()
                    ?.controller == this@liftAppbarWith && activity != null
                ) {
                    val notAtTop = recycler.canScrollVertically(-1)
                    if (notAtTop != isToolbarColored) colorToolbar(notAtTop)
                }
            }
        },
    )
}

fun Controller.scrollViewWith(
    recycler: RecyclerView,
    padBottom: Boolean = false,
    customPadding: Boolean = false,
    ignoreInsetVisibility: Boolean = false,
    swipeRefreshLayout: SwipeRefreshLayout? = null,
    afterInsets: ((WindowInsetsCompat) -> Unit)? = null,
    liftOnScroll: ((Boolean) -> Unit)? = null,
    onLeavingController: (() -> Unit)? = null,
    onBottomNavUpdate: (() -> Unit)? = null,
): ((Boolean) -> Unit) {
    var statusBarHeight = -1
    val tabBarHeight = 48.dpToPx
    activityBinding?.appBar?.lockYPos = false
    activityBinding?.appBar?.y = 0f
    val includeTabView = this is TabbedInterface
    activityBinding?.appBar?.useTabsInPreLayout = includeTabView
    activityBinding?.appBar?.setToolbarModeBy(this@scrollViewWith)
    var appBarHeight = (
        if (fullAppBarHeight ?: 0 > 0) fullAppBarHeight!!
        else activityBinding?.appBar?.preLayoutHeight ?: 0
        )
    swipeRefreshLayout?.setDistanceToTriggerSync(150.dpToPx)
    val swipeCircle = swipeRefreshLayout?.findChild<ImageView>()
    activityBinding!!.appBar.doOnLayout {
        if (fullAppBarHeight!! > 0 && isControllerVisible) {
            appBarHeight = fullAppBarHeight!!
            recycler.requestApplyInsets()
        }
    }
    val updateViewsNearBottom = {
        onBottomNavUpdate?.invoke()
        activityBinding?.bottomView?.translationY = activityBinding?.bottomNav?.translationY ?: 0f
    }
    recycler.post {
        updateViewsNearBottom()
    }

    setItemAnimatorForAppBar(recycler)

    val randomTag = Random.nextLong()
    var lastY = 0f
    var fakeToolbarView: View? = null
    val preferences: PreferencesHelper by injectLazy()
    var fakeBottomNavView: View? = null
    if (!customPadding) {
        recycler.updatePaddingRelative(
            top = (
                activity?.window?.decorView?.rootWindowInsetsCompat?.getInsets(systemBars())?.top
                    ?: 0
                ) + appBarHeight,
        )
    }
    val atTopOfRecyclerView: () -> Boolean = f@{
        if (this is SmallToolbarInterface || activityBinding?.appBar?.useLargeToolbar == false) {
            return@f !recycler.canScrollVertically(-1)
        }
        val activityBinding = activityBinding ?: return@f true
        return@f recycler.computeVerticalScrollOffset() - recycler.paddingTop <=
            0 - activityBinding.appBar.paddingTop -
            activityBinding.toolbar.height - if (includeTabView) tabBarHeight else 0
    }
    recycler.doOnApplyWindowInsetsCompat { view, insets, _ ->
        appBarHeight = fullAppBarHeight!!
        val systemInsets = if (ignoreInsetVisibility) insets.ignoredSystemInsets else insets.getInsets(systemBars())
        val headerHeight = systemInsets.top + appBarHeight
        if (!customPadding) view.updatePaddingRelative(
            top = headerHeight,
            bottom = if (padBottom) systemInsets.bottom else view.paddingBottom,
        )
        swipeRefreshLayout?.setProgressViewOffset(
            true,
            headerHeight + (-60).dpToPx,
            headerHeight + 10.dpToPx,
        )
        statusBarHeight = systemInsets.top
        afterInsets?.invoke(insets)
    }

    var toolbarColorAnim: ValueAnimator? = null
    var isToolbarColor = false
    var isInView = true
    val colorToolbar: (Boolean) -> Unit = f@{ isColored ->
        isToolbarColor = isColored
        if (liftOnScroll != null) {
            liftOnScroll.invoke(isColored)
        } else {
            toolbarColorAnim?.cancel()
            val floatingBar =
                (this as? FloatingSearchInterface)?.showFloatingBar() == true && !includeTabView
            if (floatingBar) {
                setAppBarBG(isColored.toInt().toFloat(), includeTabView)
                return@f
            }
            val percent = ImageUtil.getPercentOfColor(
                activityBinding!!.appBar.backgroundColor ?: Color.TRANSPARENT,
                activity!!.getResourceColor(R.attr.colorSurface),
                activity!!.getResourceColor(R.attr.colorPrimaryVariant),
            )
            toolbarColorAnim = ValueAnimator.ofFloat(percent, isColored.toInt().toFloat())
            toolbarColorAnim?.addUpdateListener { valueAnimator ->
                setAppBarBG(valueAnimator.animatedValue as Float, includeTabView)
            }
            toolbarColorAnim?.start()
        }
    }
    if ((this as? FloatingSearchInterface)?.showFloatingBar() == true && !includeTabView) {
        setAppBarBG(0f, includeTabView)
    }
    addLifecycleListener(
        object : Controller.LifecycleListener() {
            override fun onChangeStart(
                controller: Controller,
                changeHandler: ControllerChangeHandler,
                changeType: ControllerChangeType,
            ) {
                super.onChangeStart(controller, changeHandler, changeType)
                isInView = changeType.isEnter
                if (changeType.isEnter) {
                    activityBinding?.appBar?.hideBigView(
                        this@scrollViewWith is SmallToolbarInterface,
                        setTitleAlpha = this@scrollViewWith !is MangaDetailsController,
                    )
                    activityBinding?.appBar?.setToolbarModeBy(this@scrollViewWith)
                    activityBinding?.appBar?.useTabsInPreLayout = includeTabView
                    colorToolbar(isToolbarColor)
                    if (fakeToolbarView?.parent != null) {
                        val parent = fakeToolbarView?.parent as? ViewGroup ?: return
                        parent.removeView(fakeToolbarView)
                        fakeToolbarView = null
                    }
                    if (fakeBottomNavView?.parent != null) {
                        val parent = fakeBottomNavView?.parent as? ViewGroup ?: return
                        parent.removeView(fakeBottomNavView)
                        fakeBottomNavView = null
                    }
                    lastY = 0f
                    activityBinding!!.appBar.updateAppBarAfterY(recycler)
                    activityBinding!!.toolbar.tag = randomTag
                    activityBinding!!.toolbar.setOnClickListener {
                        recycler.smoothScrollToTop()
                    }
                } else {
                    if (!customPadding && lastY == 0f && (
                        (
                            this@scrollViewWith !is FloatingSearchInterface && router.backstack.lastOrNull()
                                ?.controller is MangaDetailsController
                            ) || includeTabView
                        )
                    ) {
                        val parent = recycler.parent as? ViewGroup ?: return
                        val v = View(activity)
                        fakeToolbarView = v
                        parent.addView(v, parent.indexOfChild(recycler) + 1)
                        val params = fakeToolbarView?.layoutParams
                        params?.height = recycler.paddingTop
                        params?.width = MATCH_PARENT
                        v.setBackgroundColor(v.context.getResourceColor(R.attr.colorSurface))
                        v.layoutParams = params
                        onLeavingController?.invoke()
                    }
                    if (!customPadding && router.backstackSize == 2 && changeType == ControllerChangeType.PUSH_EXIT &&
                        router.backstack.lastOrNull()?.controller !is DialogController
                    ) {
                        val parent = recycler.parent as? ViewGroup ?: return
                        val bottomNav = activityBinding?.bottomNav ?: return
                        val v = View(activity)
                        fakeBottomNavView = v
                        parent.addView(v)
                        val params = fakeBottomNavView?.layoutParams
                        params?.height = bottomNav.height
                        (params as? FrameLayout.LayoutParams)?.gravity = Gravity.BOTTOM
                        fakeBottomNavView?.translationY = bottomNav.translationY
                        params?.width = MATCH_PARENT
                        v.setBackgroundColor(v.context.getResourceColor(R.attr.colorPrimaryVariant))
                        v.layoutParams = params
                    }
                    toolbarColorAnim?.cancel()
                    if (activityBinding!!.toolbar.tag == randomTag) activityBinding!!.toolbar.setOnClickListener(null)
                }
            }
        },
    )
    colorToolbar(!atTopOfRecyclerView())

    recycler.post {
        if (isControllerVisible) {
            activityBinding!!.appBar.updateAppBarAfterY(recycler)
            colorToolbar(!atTopOfRecyclerView())
        }
    }
    recycler.addOnScrollListener(
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (isControllerVisible && statusBarHeight > -1 &&
                    (this@scrollViewWith as? BaseController<*>)?.isDragging != true &&
                    activity != null && activityBinding!!.appBar.height > 0 &&
                    recycler.translationY == 0f
                ) {
                    if (!recycler.canScrollVertically(-1)) {
                        val shortAnimationDuration = resources?.getInteger(
                            android.R.integer.config_shortAnimTime,
                        ) ?: 0
                        activityBinding!!.appBar.y = 0f
                        activityBinding!!.appBar.updateAppBarAfterY(recycler)
                        if (router.backstackSize == 1 && isInView) {
                            activityBinding!!.bottomNav?.let {
                                val animator = it.animate()?.translationY(0f)
                                    ?.setDuration(shortAnimationDuration.toLong())
                                animator?.setUpdateListener {
                                    updateViewsNearBottom()
                                }
                                animator?.start()
                            }
                        }
                        lastY = 0f
                        if (isToolbarColor) colorToolbar(false)
                    } else {
                        activityBinding!!.appBar.y -= dy
                        activityBinding!!.appBar.updateAppBarAfterY(recycler)
                        activityBinding!!.bottomNav?.let { bottomNav ->
                            if (bottomNav.isVisible && isInView) {
                                if (preferences.hideBottomNavOnScroll().get()) {
                                    bottomNav.translationY += dy
                                    bottomNav.translationY = MathUtils.clamp(
                                        bottomNav.translationY,
                                        0f,
                                        bottomNav.height.toFloat(),
                                    )
                                    updateViewsNearBottom()
                                } else if (bottomNav.translationY != 0f) {
                                    bottomNav.translationY = 0f
                                    activityBinding!!.bottomView?.translationY = 0f
                                }
                            }
                        }

                        if (!isToolbarColor && (
                            dy == 0 ||
                                (
                                    activityBinding!!.appBar.y <= -activityBinding!!.appBar.height.toFloat() ||
                                        dy == 0 && activityBinding!!.appBar.y == 0f
                                    )
                            )
                        ) {
                            colorToolbar(true)
                        }
                        val notAtTop = !atTopOfRecyclerView()
                        if (notAtTop != isToolbarColor) colorToolbar(notAtTop)
                        lastY = activityBinding!!.appBar.y
                    }
                    swipeCircle?.translationY = max(
                        activityBinding!!.appBar.y,
                        -activityBinding!!.appBar.height + activityBinding!!.appBar.paddingTop.toFloat(),
                    )
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE &&
                    (this@scrollViewWith as? BaseController<*>)?.isDragging != true
                ) {
                    if (isControllerVisible && statusBarHeight > -1 &&
                        activity != null && activityBinding!!.appBar.height > 0 &&
                        recycler.translationY == 0f
                    ) {
                        val halfWay = activityBinding!!.appBar.height.toFloat() / 2
                        val shortAnimationDuration = resources?.getInteger(
                            android.R.integer.config_shortAnimTime,
                        ) ?: 0
                        val closerToTop = abs(activityBinding!!.appBar.y) > halfWay
                        val halfWayBottom = (activityBinding!!.bottomNav?.height?.toFloat() ?: 0f) / 2
                        val closerToBottom = activityBinding!!.bottomNav?.translationY ?: 0f > halfWayBottom
                        val atTop = !recycler.canScrollVertically(-1)
                        val closerToEdge =
                            if (activityBinding!!.bottomNav?.isVisible == true &&
                                preferences.hideBottomNavOnScroll().get()
                            ) closerToBottom else closerToTop
                        lastY = activityBinding!!.appBar.snapAppBarY(this@scrollViewWith, recycler) {
                            val activityBinding = activityBinding ?: return@snapAppBarY
                            swipeCircle?.translationY = max(
                                activityBinding.appBar.y,
                                -activityBinding.appBar.height + activityBinding.appBar.paddingTop.toFloat(),
                            )
                        }
                        if (activityBinding!!.bottomNav?.isVisible == true &&
                            isInView && preferences.hideBottomNavOnScroll().get()
                        ) {
                            activityBinding!!.bottomNav?.let {
                                val lastBottomY =
                                    if (closerToEdge && !atTop) it.height.toFloat() else 0f
                                val animator = it.animate()?.translationY(lastBottomY)
                                    ?.setDuration(shortAnimationDuration.toLong())
                                animator?.setUpdateListener {
                                    updateViewsNearBottom()
                                }
                                animator?.start()
                            }
                        }
                        val notAtTop = !atTopOfRecyclerView()
                        if (notAtTop != isToolbarColor) colorToolbar(notAtTop)
                    }
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    val view = activity?.window?.currentFocus ?: return
                    val imm =
                        activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            ?: return
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                }
            }
        },
    )
    return colorToolbar
}

fun Controller.setItemAnimatorForAppBar(recycler: RecyclerView) {
    var itemAppBarAnimator: Animator? = null

    fun animateAppBar() {
        if (this !is SmallToolbarInterface && isControllerVisible) {
            itemAppBarAnimator?.cancel()
            val duration = (recycler.itemAnimator?.changeDuration ?: 250) * 2
            itemAppBarAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                addUpdateListener {
                    if (isControllerVisible) {
                        activityBinding?.appBar?.updateAppBarAfterY(recycler)
                    }
                }
            }
            itemAppBarAnimator?.duration = duration
            itemAppBarAnimator?.start()
        }
    }

    recycler.itemAnimator = object : DefaultItemAnimator() {
        override fun animateMove(
            holder: RecyclerView.ViewHolder?,
            fromX: Int,
            fromY: Int,
            toX: Int,
            toY: Int,
        ): Boolean {
            animateAppBar()
            return super.animateMove(holder, fromX, fromY, toX, toY)
        }

        override fun onAnimationFinished(viewHolder: RecyclerView.ViewHolder) {
            if (isControllerVisible) {
                activityBinding?.appBar?.updateAppBarAfterY(recycler)
            }
            super.onAnimationFinished(viewHolder)
        }

        override fun animateChange(
            oldHolder: RecyclerView.ViewHolder,
            newHolder: RecyclerView.ViewHolder,
            preInfo: ItemHolderInfo,
            postInfo: ItemHolderInfo,
        ): Boolean {
            animateAppBar()
            return super.animateChange(oldHolder, newHolder, preInfo, postInfo)
        }

        override fun animateChange(
            oldHolder: RecyclerView.ViewHolder?,
            newHolder: RecyclerView.ViewHolder?,
            fromX: Int,
            fromY: Int,
            toX: Int,
            toY: Int,
        ): Boolean {
            animateAppBar()
            return super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY)
        }
    }
}

val Controller.mainRecyclerView: RecyclerView?
    get() = (this as? SettingsController)?.listView ?: (this as? BaseController<*>)?.mainRecycler

fun Controller.moveRecyclerViewUp(allTheWayUp: Boolean = false, scrollUpAnyway: Boolean = false) {
    if (activityBinding?.bigToolbar?.isVisible == false) return
    val recycler = mainRecyclerView ?: return
    val activityBinding = activityBinding ?: return
    val appBarOffset = activityBinding.appBar.toolbarDistanceToTop
    if (allTheWayUp && recycler.computeVerticalScrollOffset() - recycler.paddingTop <= fullAppBarHeight ?: activityBinding.appBar.preLayoutHeight) {
        (recycler.layoutManager as? LinearLayoutManager)?.scrollToPosition(0)
        (recycler.layoutManager as? StaggeredGridLayoutManager)?.scrollToPosition(0)
        recycler.post {
            activityBinding.appBar.updateAppBarAfterY(recycler)
            activityBinding.appBar.useSearchToolbarForMenu(false)
        }
        return
    }
    if (scrollUpAnyway || recycler.computeVerticalScrollOffset() - recycler.paddingTop <= 0 - appBarOffset) {
        (recycler.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(0, activityBinding.appBar.yNeededForSmallToolbar)
        (recycler.layoutManager as? StaggeredGridLayoutManager)
            ?.scrollToPositionWithOffset(0, activityBinding.appBar.yNeededForSmallToolbar)
        recycler.post {
            activityBinding.appBar.updateAppBarAfterY(recycler)
            activityBinding.appBar.useSearchToolbarForMenu(recycler.computeVerticalScrollOffset() != 0)
        }
    }
}

fun Controller.setAppBarBG(value: Float, includeTabView: Boolean = false) {
    val context = view?.context ?: return
    val floatingBar =
        (this as? FloatingSearchInterface)?.showFloatingBar() == true && !includeTabView
    if (!isControllerVisible) return
    if (floatingBar) {
        (activityBinding?.cardView as? CardView)?.setCardBackgroundColor(context.getResourceColor(R.attr.colorPrimaryVariant))
        if (this !is SmallToolbarInterface && activityBinding?.appBar?.useLargeToolbar == true &&
            activityBinding?.appBar?.compactSearchMode != true
        ) {
            val colorSurface = context.getResourceColor(R.attr.colorSurface)
            val color = ColorUtils.blendARGB(
                colorSurface,
                ColorUtils.setAlphaComponent(colorSurface, 0),
                value,
            )
            activityBinding?.appBar?.backgroundColor = color
        } else {
            activityBinding?.appBar?.backgroundColor = Color.TRANSPARENT
        }
        if (activityBinding?.appBar?.isInvisible != true) {
            activity?.window?.statusBarColor =
                context.getResourceColor(android.R.attr.statusBarColor)
        }
    } else {
        val color = ColorUtils.blendARGB(
            context.getResourceColor(R.attr.colorSurface),
            context.getResourceColor(R.attr.colorPrimaryVariant),
            value,
        )
        activityBinding?.appBar?.setBackgroundColor(color)
        if (activityBinding?.appBar?.isInvisible != true) {
            activity?.window?.statusBarColor =
                ColorUtils.setAlphaComponent(color, (0.87f * 255).roundToInt())
        }
        if ((this as? FloatingSearchInterface)?.showFloatingBar() == true) {
            val invColor = ColorUtils.blendARGB(
                context.getResourceColor(R.attr.colorSurface),
                context.getResourceColor(R.attr.colorPrimaryVariant),
                1 - value,
            )
            (activityBinding?.cardView as? CardView)?.setCardBackgroundColor(
                ColorStateList.valueOf(
                    invColor,
                ),
            )
        }
    }
}

fun Controller.requestFilePermissionsSafe(
    requestCode: Int,
    preferences: PreferencesHelper,
    showA11PermissionAnyway: Boolean = false,
) {
    val activity = activity ?: return
    val permissions = mutableListOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    permissions.forEach { permission ->
        if (ContextCompat.checkSelfPermission(
                activity,
                permission,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(permission), requestCode)
        }
    }
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        !Environment.isExternalStorageManager() &&
        (!preferences.hasDeniedA11FilePermission().get() || showA11PermissionAnyway)
    ) {
        preferences.hasDeniedA11FilePermission().set(true)
        activity.materialAlertDialog()
            .setTitle(R.string.all_files_permission_required)
            .setMessage(R.string.external_storage_permission_notice)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    "package:${activity.packageName}".toUri(),
                )
                try {
                    activity.startActivity(intent)
                } catch (_: Exception) {
                    val intent2 = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    activity.startActivity(intent2)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

fun Controller.withFadeTransaction(): RouterTransaction {
    return RouterTransaction.with(this)
        .pushChangeHandler(OneWayFadeChangeHandler())
        .popChangeHandler(OneWayFadeChangeHandler())
}

fun Controller.withFadeInTransaction(): RouterTransaction {
    return RouterTransaction.with(this)
        .pushChangeHandler(OneWayFadeChangeHandler().apply { fadeOut = false })
        .popChangeHandler(OneWayFadeChangeHandler())
}

fun Controller.openInBrowser(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    } catch (e: Throwable) {
        activity?.toast(e.message)
    }
}

val Controller.activityBinding: MainActivityBinding?
    get() = (activity as? MainActivity)?.binding

val Controller.toolbarHeight: Int?
    get() = (activity as? MainActivity)?.toolbarHeight

/** Returns the expected height of the app bar - top insets, based on what the controller needs */
val Controller.fullAppBarHeight: Int?
    get() = (activity as? MainActivity)?.bigToolbarHeight(
        (this as? FloatingSearchInterface)?.showFloatingBar() == true,
        this is TabbedInterface,
        this !is SmallToolbarInterface,
    )

val Controller.isControllerVisible: Boolean
    get() = router.backstack.lastOrNull()?.controller == this
