package eu.kanade.tachiyomi.util.view

import android.Manifest
import android.animation.Animator
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.activity.BackEventCompat
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.appcompat.widget.SearchView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.ColorUtils
import androidx.core.math.MathUtils
import androidx.core.net.toUri
import androidx.core.view.ScrollingView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.google.android.material.snackbar.Snackbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MainActivityBinding
import eu.kanade.tachiyomi.ui.base.SmallToolbarInterface
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.base.controller.CrossFadeChangeHandler
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.FadeChangeHandler
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
import eu.kanade.tachiyomi.widget.StatefulNestedScrollView
import uy.kohesive.injekt.injectLazy
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

fun Controller.setOnQueryTextChangeListener(
    searchView: SearchView?,
    onlyOnSubmit: Boolean = false,
    hideKbOnSubmit: Boolean = true,
    onTextChange: ((text: String?) -> Unit)? = null,
    f: (text: String?) -> Boolean,
) {
    searchView?.setOnQueryTextListener(
        object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String?): Boolean {
                if (router.backstack
                        .lastOrNull()
                        ?.controller == this@setOnQueryTextChangeListener
                ) {
                    // fires even with onlyOnSubmit, for anything watching what's typed
                    onTextChange?.invoke(newText)
                    if (!onlyOnSubmit) {
                        return f(newText)
                    }
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
        activityBinding
            ?.searchToolbar
            ?.menu
            ?.findItem(R.id.action_search)
            ?.actionView as? SearchView
    val searchView2 =
        activityBinding
            ?.toolbar
            ?.menu
            ?.findItem(R.id.action_search)
            ?.actionView as? SearchView
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

fun <T> Controller.liftAppbarWith(
    recyclerOrNested: T,
    padView: Boolean = false,
    changeMarginsInstead: Boolean = false,
    liftOnScroll: ((Boolean) -> Unit)? = null,
) {
    val recycler = recyclerOrNested as? RecyclerView ?: recyclerOrNested as? NestedScrollView ?: return
    if (padView) {
        var appBarHeight = (
            if ((fullAppBarHeight ?: 0) > 0) {
                fullAppBarHeight!!
            } else {
                activityBinding?.appBar?.attrToolbarHeight ?: 0
            }
        )
        activityBinding!!.toolbar.post {
            if (fullAppBarHeight!! > 0) {
                appBarHeight = fullAppBarHeight!!
                recycler.requestApplyInsets()
            }
        }
        recycler.updatePaddingRelative(
            top = if (changeMarginsInstead) 0 else activityBinding!!.toolbar.y.toInt() + appBarHeight,
            bottom = recycler.rootWindowInsetsCompat?.getInsets(systemBars())?.bottom ?: 0,
        )
        if (changeMarginsInstead) {
            recycler.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = activityBinding!!.toolbar.y.toInt() + appBarHeight
            }
        }
        recycler.applyBottomAnimatedInsets(setPadding = true) { view, insets ->
            val headerHeight = insets.getInsets(systemBars()).top + appBarHeight
            if (changeMarginsInstead) {
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = headerHeight
                }
            } else {
                view.updatePaddingRelative(top = headerHeight)
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            recycler.doOnApplyWindowInsetsCompat { view, insets, _ ->
                val headerHeight = insets.getInsets(systemBars()).top + appBarHeight
                view.updatePaddingRelative(
                    top = if (changeMarginsInstead) 0 else headerHeight,
                    bottom = insets.getInsets(ime() or systemBars()).bottom,
                )
                if (changeMarginsInstead) {
                    view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        topMargin = headerHeight
                    }
                }
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
        liftOnScroll?.invoke(isColored)
        val floatingBar =
            !(activityBinding?.toolbar?.isVisible == true || activityBinding?.tabsFrameLayout?.isVisible == true)
        val percent =
            ImageUtil.getPercentOfColor(
                activityBinding!!.appBar.backgroundColor ?: Color.TRANSPARENT,
                activity!!.getResourceColor(R.attr.colorSurface),
                activity!!.getResourceColor(R.attr.colorSurfaceContainer),
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
    (recycler as? NestedScrollView)?.setOnScrollChangeListener { _, _, _, _, _ ->
        if (router
                ?.backstack
                ?.lastOrNull()
                ?.controller == this@liftAppbarWith &&
            activity != null
        ) {
            val notAtTop = recycler.canScrollVertically(-1)
            if (notAtTop != isToolbarColored) colorToolbar(notAtTop)
        }
    }
    (recycler as? RecyclerView)?.addOnScrollListener(
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(
                recyclerView: RecyclerView,
                dx: Int,
                dy: Int,
            ) {
                super.onScrolled(recyclerView, dx, dy)
                if (router
                        ?.backstack
                        ?.lastOrNull()
                        ?.controller == this@liftAppbarWith &&
                    activity != null
                ) {
                    val notAtTop = recycler.canScrollVertically(-1)
                    if (notAtTop != isToolbarColored) colorToolbar(notAtTop)
                }
            }
        },
    )
    addLifecycleListener(
        object : Controller.LifecycleListener() {
            override fun onChangeStart(
                controller: Controller,
                changeHandler: ControllerChangeHandler,
                changeType: ControllerChangeType,
            ) {
                super.onChangeStart(controller, changeHandler, changeType)
                if (changeType.isEnter) {
                    activityBinding?.appBar?.hideBigView(
                        true,
                        setTitleAlpha = this@liftAppbarWith !is MangaDetailsController,
                    )
                    activityBinding?.appBar?.setToolbarModeBy(this@liftAppbarWith)
                    activityBinding?.appBar?.useTabsInPreLayout = false
                    colorToolbar(isToolbarColored)
                    // since liftAppbarWith never drags the app bar, just pass null
                    activityBinding?.appBar?.updateAppBarAfterY(null)
                }
            }
        },
    )
}

fun Controller.scrollViewWith(
    recycler: ScrollingView,
    padBottom: Boolean = false,
    customPadding: Boolean = false,
    ignoreInsetVisibility: Boolean = false,
    swipeRefreshLayout: SwipeRefreshLayout? = null,
    afterInsets: ((WindowInsetsCompat) -> Unit)? = null,
    liftOnScroll: ((Boolean) -> Unit)? = null,
    onLeavingController: (() -> Unit)? = null,
    onBottomNavUpdate: (() -> Unit)? = null,
): ((Boolean) -> Unit) {
    if (recycler !is View) return { _ -> }
    var statusBarHeight = -1
    val tabBarHeight = 48.dpToPx
    activityBinding?.appBar?.lockYPos = false
    activityBinding?.appBar?.y = 0f

    // Read live, the side nav can take over for the tabs while the controller is up
    fun includeTabView() = (this as? TabbedInterface)?.showTabs() == true
    activityBinding?.appBar?.useTabsInPreLayout = includeTabView()
    activityBinding?.appBar?.setToolbarModeBy(this@scrollViewWith)
    var appBarHeight = (
        if ((fullAppBarHeight ?: 0) > 0) {
            fullAppBarHeight!!
        } else {
            activityBinding?.appBar?.preLayoutHeight ?: 0
        }
    )
    swipeRefreshLayout?.setDistanceToTriggerSync(150.dpToPx)
    val swipeCircle = swipeRefreshLayout?.findChild<ImageView>()
    activityBinding!!.appBar.doOnLayout {
        if ((fullAppBarHeight ?: 0) > 0 && isControllerVisible) {
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

    (recycler as? RecyclerView)?.let { setItemAnimatorForAppBar(it) }

    val randomTag = Random.nextLong()
    var lastY = 0f
    var fakeToolbarView: View? = null
    val preferences: PreferencesHelper by injectLazy()
    var fakeBottomNavView: View? = null
    if (!customPadding) {
        recycler.updatePaddingRelative(
            top = ((activity as? MainActivity)?.cachedSystemInsets?.top ?: 0) + appBarHeight,
        )
    }
    val atTopOfRecyclerView: () -> Boolean = f@{
        if (this is SmallToolbarInterface || activityBinding?.appBar?.useLargeToolbar == false) {
            return@f !recycler.canScrollVertically(-1)
        }
        val activityBinding = activityBinding ?: return@f true
        return@f recycler.computeVerticalScrollOffset() - recycler.paddingTop <=
            0 - activityBinding.appBar.paddingTop -
            activityBinding.toolbar.height - if (includeTabView()) tabBarHeight else 0
    }
    recycler.doOnApplyWindowInsetsCompat { view, insets, _ ->
        appBarHeight = fullAppBarHeight ?: 0
        val systemInsets = if (ignoreInsetVisibility) insets.ignoredSystemInsets else insets.getInsets(systemBars())
        val headerHeight = systemInsets.top + appBarHeight
        if (!customPadding) {
            view.updatePaddingRelative(
                top = headerHeight,
                bottom = if (padBottom) systemInsets.bottom else view.paddingBottom,
            )
        }
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
                (this as? FloatingSearchInterface)?.showFloatingBar() == true && !includeTabView()
            if (floatingBar) {
                setAppBarBG(isColored.toInt().toFloat(), false)
                return@f
            }
            val percent =
                ImageUtil.getPercentOfColor(
                    activityBinding!!.appBar.backgroundColor ?: Color.TRANSPARENT,
                    activity!!.getResourceColor(R.attr.colorSurface),
                    activity!!.getResourceColor(R.attr.colorSurfaceContainer),
                )
            toolbarColorAnim = ValueAnimator.ofFloat(percent, isColored.toInt().toFloat())
            toolbarColorAnim?.addUpdateListener { valueAnimator ->
                setAppBarBG(valueAnimator.animatedValue as Float, includeTabView())
            }
            toolbarColorAnim?.start()
        }
    }
    if ((this as? FloatingSearchInterface)?.showFloatingBar() == true && !includeTabView()) {
        setAppBarBG(0f, false)
    }
    addLifecycleListener(
        object : Controller.LifecycleListener() {
            override fun onChangeEnd(
                controller: Controller,
                changeHandler: ControllerChangeHandler,
                changeType: ControllerChangeType,
            ) {
                super.onChangeEnd(controller, changeHandler, changeType)
                if (changeType.isEnter) {
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
                }
            }

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
                    activityBinding?.appBar?.useTabsInPreLayout = includeTabView()
                    colorToolbar(isToolbarColor)
                    lastY = 0f
                    activityBinding?.appBar?.updateAppBarAfterY(recycler)
                    activityBinding?.toolbar?.tag = randomTag
                    activityBinding?.toolbar?.setOnClickListener {
                        if (recycler is RecyclerView) {
                            recycler.smoothScrollToTop()
                        } else if (recycler is NestedScrollView) {
                            recycler.smoothScrollTo(0, 0)
                        }
                    }
                    activityBinding!!.appBar.doOnLayout {
                        if ((fullAppBarHeight ?: 0) > 0 && isControllerVisible) {
                            appBarHeight = fullAppBarHeight!!
                        }
                    }
                } else {
                    if (!customPadding &&
                        lastY == 0f &&
                        (
                            (
                                this@scrollViewWith !is FloatingSearchInterface &&
                                    router.backstack
                                        .lastOrNull()
                                        ?.controller is MangaDetailsController
                            ) ||
                                includeTabView()
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
                    if (!customPadding &&
                        router.backstackSize == 2 &&
                        changeType == ControllerChangeType.PUSH_EXIT &&
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
                        v.setBackgroundColor(v.context.getResourceColor(R.attr.colorSurfaceContainer))
                        v.layoutParams = params
                    }
                    toolbarColorAnim?.cancel()
                    if (activityBinding?.toolbar?.tag == randomTag) {
                        activityBinding?.toolbar?.setOnClickListener(null)
                    }
                }
            }
        },
    )
    colorToolbar(!atTopOfRecyclerView())

    recycler.post {
        if (isControllerVisible) {
            activityBinding?.appBar?.updateAppBarAfterY(recycler)
            colorToolbar(!atTopOfRecyclerView())
        }
    }

    fun onScrolled(dy: Int) {
        if (isControllerVisible &&
            statusBarHeight > -1 &&
            (this@scrollViewWith as? BaseController<*>)?.isDragging != true &&
            activity != null &&
            (activityBinding?.appBar?.height ?: 0) > 0 &&
            recycler.translationY == 0f
        ) {
            if (!recycler.canScrollVertically(-1)) {
                val shortAnimationDuration =
                    resources?.getInteger(
                        android.R.integer.config_shortAnimTime,
                    ) ?: 0
                activityBinding?.appBar?.y = 0f
                activityBinding?.appBar?.updateAppBarAfterY(recycler)
                if (router.backstackSize == 1 && isInView) {
                    activityBinding!!.bottomNav?.let {
                        val animator =
                            it
                                .animate()
                                ?.translationY(0f)
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
                activityBinding?.appBar?.let { it.y -= dy }
                activityBinding?.appBar?.updateAppBarAfterY(recycler)
                activityBinding?.bottomNav?.let { bottomNav ->
                    if (bottomNav.isVisible && isInView) {
                        if (preferences.hideBottomNavOnScroll().get()) {
                            bottomNav.translationY += dy
                            bottomNav.translationY =
                                MathUtils.clamp(
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

                if (!isToolbarColor &&
                    (
                        dy == 0 ||
                            activityBinding?.appBar?.let { it.y <= -it.height.toFloat() } == true
                    )
                ) {
                    colorToolbar(true)
                }
                val notAtTop = !atTopOfRecyclerView()
                if (notAtTop != isToolbarColor) colorToolbar(notAtTop)
                lastY = activityBinding!!.appBar.y
            }
            activityBinding?.appBar?.let {
                swipeCircle?.translationY = max(it.y, -it.height + it.paddingTop.toFloat())
            }
        }
    }

    fun onScrollIdle() {
        val activityBinding = activityBinding ?: return
        if (isControllerVisible &&
            statusBarHeight > -1 &&
            activity != null &&
            activityBinding.appBar.height > 0 &&
            recycler.translationY == 0f
        ) {
            val halfWay = activityBinding.appBar.height.toFloat() / 2
            val shortAnimationDuration =
                resources?.getInteger(
                    android.R.integer.config_shortAnimTime,
                ) ?: 0
            val closerToTop = abs(activityBinding.appBar.y) > halfWay
            val halfWayBottom = (activityBinding.bottomNav?.height?.toFloat() ?: 0f) / 2
            val closerToBottom = (activityBinding.bottomNav?.translationY ?: 0f) > halfWayBottom
            val atTop = !recycler.canScrollVertically(-1)
            val closerToEdge =
                if (activityBinding.bottomNav?.isVisible == true &&
                    preferences.hideBottomNavOnScroll().get()
                ) {
                    closerToBottom
                } else {
                    closerToTop
                }
            lastY =
                activityBinding.appBar.snapAppBarY(this@scrollViewWith, recycler) {
                    val appBar = this.activityBinding?.appBar ?: return@snapAppBarY
                    swipeCircle?.translationY =
                        max(
                            appBar.y,
                            -appBar.height + appBar.paddingTop.toFloat(),
                        )
                }
            if (activityBinding.bottomNav?.isVisible == true &&
                isInView &&
                preferences.hideBottomNavOnScroll().get()
            ) {
                activityBinding.bottomNav.let {
                    val lastBottomY =
                        if (closerToEdge && !atTop) it.height.toFloat() else 0f
                    val animator =
                        it
                            .animate()
                            ?.translationY(lastBottomY)
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
    }

    (recycler as? RecyclerView)?.addOnScrollListener(
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(
                recyclerView: RecyclerView,
                dx: Int,
                dy: Int,
            ) {
                onScrolled(dy)
            }

            override fun onScrollStateChanged(
                recyclerView: RecyclerView,
                newState: Int,
            ) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE &&
                    (this@scrollViewWith as? BaseController<*>)?.isDragging != true
                ) {
                    onScrollIdle()
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

    (recycler as? NestedScrollView)?.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
        onScrolled(scrollY - oldScrollY)
    }

    (recycler as? StatefulNestedScrollView)?.setScrollStoppedListener {
        if ((this@scrollViewWith as? BaseController<*>)?.isDragging != true) {
            onScrollIdle()
        }
    }
    return colorToolbar
}

fun Controller.setItemAnimatorForAppBar(recycler: RecyclerView) {
    var itemAppBarAnimator: Animator? = null

    fun animateAppBar() {
        if (this !is SmallToolbarInterface && isControllerVisible) {
            itemAppBarAnimator?.cancel()
            val duration = (recycler.itemAnimator?.changeDuration ?: 250) * 2
            itemAppBarAnimator =
                ValueAnimator.ofFloat(0f, 1f).apply {
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

    recycler.itemAnimator =
        object : DefaultItemAnimator() {
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

fun Controller.moveRecyclerViewUp(
    allTheWayUp: Boolean = false,
    scrollUpAnyway: Boolean = false,
) {
    if (activityBinding?.bigToolbar?.isVisible == false) return
    val recycler = mainRecyclerView ?: return
    val activityBinding = activityBinding ?: return
    val appBarOffset = activityBinding.appBar.toolbarDistanceToTop
    if (allTheWayUp &&
        recycler.computeVerticalScrollOffset() - recycler.paddingTop <=
        (fullAppBarHeight ?: activityBinding.appBar.preLayoutHeight)
    ) {
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

fun Controller.setAppBarBG(
    value: Float,
    includeTabView: Boolean = false,
) {
    val context = view?.context ?: return
    val floatingBar =
        (this as? FloatingSearchInterface)?.showFloatingBar() == true && !includeTabView
    if (!isControllerVisible) return
    if (floatingBar) {
        (activityBinding?.cardView as? CardView)?.setCardBackgroundColor(context.getResourceColor(R.attr.colorSurfaceContainer))
        if (this !is SmallToolbarInterface &&
            activityBinding?.appBar?.useLargeToolbar == true &&
            activityBinding?.appBar?.compactSearchMode != true
        ) {
            val colorSurface = context.getResourceColor(R.attr.colorSurface)
            val color =
                ColorUtils.blendARGB(
                    colorSurface,
                    ColorUtils.setAlphaComponent(colorSurface, 0),
                    value,
                )
            activityBinding?.appBar?.backgroundColor = color
        } else {
            activityBinding?.appBar?.backgroundColor = Color.TRANSPARENT
        }
    } else {
        val color =
            ColorUtils.blendARGB(
                context.getResourceColor(R.attr.colorSurface),
                context.getResourceColor(R.attr.colorSurfaceContainer),
                value,
            )
        activityBinding?.appBar?.setBackgroundColor(color)
        if ((this as? FloatingSearchInterface)?.showFloatingBar() == true) {
            val invColor =
                ColorUtils.blendARGB(
                    context.getResourceColor(R.attr.colorSurface),
                    context.getResourceColor(R.attr.colorSurfaceContainer),
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
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
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
    }
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        !Environment.isExternalStorageManager() &&
        (!preferences.hasDeniedA11FilePermission().get() || showA11PermissionAnyway)
    ) {
        preferences.hasDeniedA11FilePermission().set(true)
        activity
            .materialAlertDialog()
            .setTitle(R.string.all_files_permission_required)
            .setMessage(R.string.external_storage_permission_notice)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val intent =
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        "package:${activity.packageName}".toUri(),
                    )
                try {
                    activity.startActivity(intent)
                } catch (_: Exception) {
                    val intent2 = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    activity.startActivity(intent2)
                }
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    } else if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) &&
        preferences.backupInterval().isNotSet()
    ) {
        preferences.backupInterval().set(24)
        BackupCreatorJob.setupTask(activity, 24)
    }
}

fun Controller.withFadeTransaction(): RouterTransaction =
    RouterTransaction
        .with(this)
        .pushChangeHandler(fadeTransactionHandler())
        .popChangeHandler(fadeTransactionHandler())

fun Controller.fadeTransactionHandler(): ControllerChangeHandler {
    val isLowRam = activity?.getSystemService<ActivityManager>()?.isLowRamDevice == true
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || isLowRam) {
        FadeChangeHandler(isLowRam)
    } else {
        CrossFadeChangeHandler(false)
    }
}

fun Controller.withFadeInTransaction(): RouterTransaction =
    RouterTransaction
        .with(this)
        .pushChangeHandler(FadeChangeHandler())
        .popChangeHandler(OneWayFadeChangeHandler())

fun Controller.openInBrowser(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    } catch (e: Throwable) {
        activity?.toast(e.message)
    }
}

fun Controller.copyToClipboard(
    content: String,
    label: String?,
    useToast: Boolean,
): Snackbar? {
    if (content.isBlank()) return null

    val activity = activity ?: return null
    val view = view ?: return null

    val clipboard = activity.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, content))

    label ?: return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return null
    return if (useToast) {
        activity.toast(view.context.getString(R.string._copied_to_clipboard, label))
        null
    } else {
        view.snack(view.context.getString(R.string._copied_to_clipboard, label))
    }
}

val Controller.activityBinding: MainActivityBinding?
    get() = (activity as? MainActivity)?.binding

val Controller.toolbarHeight: Int?
    get() = (activity as? MainActivity)?.toolbarHeight

/** Returns the expected height of the app bar - top insets, based on what the controller needs */
val Controller.fullAppBarHeight: Int?
    get() =
        (activity as? MainActivity)?.bigToolbarHeight(
            (this as? FloatingSearchInterface)?.showFloatingBar() == true,
            (this as? TabbedInterface)?.showTabs() == true,
            this !is SmallToolbarInterface,
        )

val Controller.isControllerVisible: Boolean
    get() = router.backstack.lastOrNull()?.controller == this

val Controller.previousController: Controller?
    get() = router.backstack.getOrNull(router.backstackSize - 2)?.controller

@MainThread
fun Router.canStillGoBack(): Boolean {
    if (backstack.size > 1) return true
    (backstack.lastOrNull()?.controller as? BaseController<*>)?.let { controller ->
        return controller.canStillGoBack()
    }
    return false
}

interface BackHandlerControllerInterface {
    fun handleOnBackStarted(backEvent: BackEventCompat) {
    }

    @CallSuper
    fun handleOnBackProgressed(backEvent: BackEventCompat) {
        if (this !is Controller) return
        if (router.backstackSize > 1 && isControllerVisible) {
            val progress = ((backEvent.progress.takeIf { it > 0.001f } ?: 0f) * 0.5f).pow(0.6f)
            view?.let { view ->
                view.alpha = 1f - progress
                view.x = progress * view.width * 0.15f

                activityBinding?.let { activityBinding ->
                    val container = activityBinding.controllerContainer
                    val backShadow = activityBinding.backShadow
                    if (container.indexOfChild(backShadow) != container.indexOfChild(view) - 1) {
                        container.removeView(backShadow)
                        container.addView(backShadow, container.indexOfChild(view))
                    }
                    if (!backShadow.isVisible) {
                        backShadow.isVisible = true
                    }
                    backShadow.x = view.x - backShadow.width
                    backShadow.alpha = 0.33f * view.alpha
                }

                router.backstack[router.backstackSize - 2].controller.view?.let { view2 ->
                    view2.alpha = progress
                    view2.x = view.x - view.width * 0.2f
                }
            }
        }
    }

    @CallSuper
    fun handleOnBackCancelled() {
        if (this !is Controller) return
        view?.alpha = 1f
        view?.x = 0f
        if (router.backstackSize > 1) {
            router.backstack[router.backstackSize - 2].controller.view?.let { view ->
                view.alpha = 0f
                view.x = 0f
            }
        }
        activityBinding?.backShadow?.isVisible = false
        activityBinding?.backShadow?.alpha = 0.25f
    }
}
