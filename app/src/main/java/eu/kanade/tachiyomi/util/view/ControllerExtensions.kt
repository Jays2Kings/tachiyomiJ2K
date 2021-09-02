package eu.kanade.tachiyomi.util.view

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.bluelinelabs.conductor.RouterTransaction
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MainActivityBinding
import eu.kanade.tachiyomi.ui.base.controller.OneWayFadeChangeHandler
import eu.kanade.tachiyomi.ui.main.BottomSheetController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.system.toast
import uy.kohesive.injekt.injectLazy
import kotlin.math.abs
import kotlin.random.Random

fun Controller.setOnQueryTextChangeListener(
    searchView: SearchView,
    onlyOnSubmit: Boolean = false,
    hideKbOnSubmit: Boolean = true,
    f: (text: String?) -> Boolean
) {
    searchView.setOnQueryTextListener(
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
                if (router.backstack.lastOrNull()?.controller == this@setOnQueryTextChangeListener) {
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
        }
    )
}

fun Controller.removeQueryListener() {
    val searchView = activityBinding?.cardToolbar?.menu?.findItem(R.id.action_search)?.actionView as? SearchView
    val searchView2 = activityBinding?.toolbar?.menu?.findItem(R.id.action_search)?.actionView as? SearchView
    searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?) = true
        override fun onQueryTextChange(newText: String?) = true
    })
    searchView2?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?) = true
        override fun onQueryTextChange(newText: String?) = true
    })
}

fun Controller.liftAppbarWith(recycler: RecyclerView, padView: Boolean = false) {
    if (padView) {
        val attrsArray = intArrayOf(R.attr.actionBarSize)
        val array = recycler.context.obtainStyledAttributes(attrsArray)
        var appBarHeight = (
            if (toolbarHeight ?: 0 > 0) toolbarHeight!!
            else array.getDimensionPixelSize(0, 0)
            )
        array.recycle()
        activityBinding!!.toolbar.post {
            if (toolbarHeight!! > 0) {
                appBarHeight = toolbarHeight!!
                recycler.requestApplyInsets()
            }
        }
        recycler.updatePaddingRelative(
            top = activityBinding!!.toolbar.y.toInt() + appBarHeight
        )
        recycler.applyBottomAnimatedInsets(setPadding = true)
        recycler.doOnApplyWindowInsets { view, insets, _ ->
            val headerHeight = insets.systemWindowInsetTop + appBarHeight
            view.updatePaddingRelative(
                top = headerHeight
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                view.updatePaddingRelative(
                    bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        insets.getInsets(WindowInsets.Type.ime() or WindowInsets.Type.systemBars()).bottom
                    } else {
                        insets.systemWindowInsetBottom
                    }
                )
            }
        }
    } else {
        view?.applyWindowInsetsForController()
        recycler.setOnApplyWindowInsetsListener(RecyclerWindowInsetsListener)
    }

    var elevationAnim: ValueAnimator? = null
    var elevate = false
    val elevateFunc: (Boolean) -> Unit = f@{ el ->
        elevate = el
        elevationAnim?.cancel()
        val floatingBar =
            !(activityBinding?.toolbar?.isVisible == true || activityBinding?.tabsFrameLayout?.isVisible == true)
        if (floatingBar) {
            activityBinding?.appBar?.elevation = 0f
            return@f
        }
        elevationAnim = ValueAnimator.ofFloat(
            activityBinding?.appBar?.elevation ?: 0f,
            if (el) 15f else 0f
        )
        elevationAnim?.addUpdateListener { valueAnimator ->
            activityBinding?.appBar?.elevation = valueAnimator.animatedValue as Float
        }
        elevationAnim?.start()
    }

    val floatingBar =
        !(activityBinding?.toolbar?.isVisible == true || activityBinding?.tabsFrameLayout?.isVisible == true)
    if (floatingBar) {
        activityBinding?.appBar?.elevation = 0f
    }
    elevateFunc(recycler.canScrollVertically(-1))
    recycler.addOnScrollListener(
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (router?.backstack?.lastOrNull()
                    ?.controller == this@liftAppbarWith && activity != null
                ) {
                    val notAtTop = recycler.canScrollVertically(-1)
                    if (notAtTop != elevate) elevateFunc(notAtTop)
                }
            }
        }
    )
}

fun Controller.scrollViewWith(
    recycler: RecyclerView,
    padBottom: Boolean = false,
    customPadding: Boolean = false,
    swipeRefreshLayout: SwipeRefreshLayout? = null,
    afterInsets: ((WindowInsets) -> Unit)? = null,
    liftOnScroll: ((Boolean) -> Unit)? = null,
    onLeavingController: (() -> Unit)? = null,
    onBottomNavUpdate: (() -> Unit)? = null,
    includeTabView: Boolean = false
): ((Boolean) -> Unit) {
    var statusBarHeight = -1
    val tabBarHeight = 48.dpToPx
    activityBinding?.appBar?.y = 0f
    activityBinding?.tabsFrameLayout?.elevation = 0f
    val isSideNavWithTabs = activityBinding?.sideNav != null && includeTabView && recycler.context.isTablet()
    val attrsArray = intArrayOf(R.attr.actionBarSize)
    val array = recycler.context.obtainStyledAttributes(attrsArray)
    var appBarHeight = (
        if (toolbarHeight ?: 0 > 0) toolbarHeight!!
        else array.getDimensionPixelSize(0, 0)
        ) + if (includeTabView) tabBarHeight else 0
    array.recycle()
    swipeRefreshLayout?.setDistanceToTriggerSync(150.dpToPx)
    activityBinding!!.toolbar.post {
        if (toolbarHeight!! > 0) {
            appBarHeight = toolbarHeight!! + if (includeTabView) tabBarHeight else 0
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
    val randomTag = Random.nextLong()
    var lastY = 0f
    var fakeToolbarView: View? = null
    var fakeBottomNavView: View? = null
    if (!customPadding) {
        recycler.updatePaddingRelative(
            top = (activity?.window?.decorView?.rootWindowInsets?.systemWindowInsetTop ?: 0) +
                appBarHeight
        )
    }
    recycler.doOnApplyWindowInsets { view, insets, _ ->
        val headerHeight = insets.systemWindowInsetTop + appBarHeight
        if (!customPadding) view.updatePaddingRelative(
            top = headerHeight,
            bottom = if (padBottom) insets.systemWindowInsetBottom else view.paddingBottom
        )
        swipeRefreshLayout?.setProgressViewOffset(
            true,
            headerHeight + (-60).dpToPx,
            headerHeight + 10.dpToPx
        )
        statusBarHeight = insets.systemWindowInsetTop
        afterInsets?.invoke(insets)
    }
    var elevationAnim: ValueAnimator? = null
    var elevate = false
    var isInView = true
    val preferences: PreferencesHelper by injectLazy()
    val elevateFunc: (Boolean) -> Unit = f@{ el ->
        elevate = el
        if (liftOnScroll != null) {
            liftOnScroll.invoke(el)
        } else {
            elevationAnim?.cancel()
            val floatingBar =
                (this as? FloatingSearchInterface)?.showFloatingBar() == true && !includeTabView
            if (floatingBar) {
                activityBinding?.appBar?.elevation = 0f
                return@f
            }
            elevationAnim = ValueAnimator.ofFloat(
                activityBinding?.appBar?.elevation ?: 0f,
                if (el) 15f else 0f
            )
            elevationAnim?.addUpdateListener { valueAnimator ->
                activityBinding?.appBar?.elevation = valueAnimator.animatedValue as Float
            }
            elevationAnim?.start()
        }
    }
    if ((this as? FloatingSearchInterface)?.showFloatingBar() == true && !includeTabView) {
        activityBinding?.appBar?.elevation = 0f
    }
    addLifecycleListener(
        object : Controller.LifecycleListener() {
            override fun onChangeStart(
                controller: Controller,
                changeHandler: ControllerChangeHandler,
                changeType: ControllerChangeType
            ) {
                super.onChangeStart(controller, changeHandler, changeType)
                isInView = changeType.isEnter
                if (changeType.isEnter) {
                    elevateFunc(elevate)
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
                    activityBinding!!.toolbar.tag = randomTag
                    activityBinding!!.toolbar.setOnClickListener {
                        if ((this@scrollViewWith as? BottomSheetController)?.sheetIsExpanded() != true) {
                            recycler.smoothScrollToTop()
                        } else {
                            (this@scrollViewWith as? BottomSheetController)?.toggleSheet()
                        }
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
                    if (!customPadding && router.backstackSize == 2 && changeType == ControllerChangeType.PUSH_EXIT) {
                        val parent = recycler.parent as? ViewGroup ?: return
                        val bottomNav = activityBinding?.bottomNav ?: return
                        val v = View(activity)
                        fakeBottomNavView = v
                        parent.addView(v, parent.indexOfChild(recycler) + 1)
                        val params = fakeBottomNavView?.layoutParams
                        params?.height = recycler.paddingBottom
                        (params as? FrameLayout.LayoutParams)?.gravity = Gravity.BOTTOM
                        fakeBottomNavView?.translationY = bottomNav.translationY
                        params?.width = MATCH_PARENT
                        v.setBackgroundColor(v.context.getResourceColor(R.attr.colorPrimaryVariant))
                        v.layoutParams = params
                    }
                    elevationAnim?.cancel()
                    if (activityBinding!!.toolbar.tag == randomTag) activityBinding!!.toolbar.setOnClickListener(null)
                }
            }
        }
    )
    elevateFunc(recycler.canScrollVertically(-1))

    recycler.post {
        elevateFunc(recycler.canScrollVertically(-1))
    }
    val isTablet = recycler.context.isTablet() && recycler.context.resources.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE
    recycler.addOnScrollListener(
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (router?.backstack?.lastOrNull()
                    ?.controller == this@scrollViewWith && statusBarHeight > -1 &&
                    activity != null && activityBinding!!.appBar.height > 0 &&
                    recycler.translationY == 0f
                ) {
                    if (!recycler.canScrollVertically(-1)) {
                        val shortAnimationDuration = resources?.getInteger(
                            android.R.integer.config_shortAnimTime
                        ) ?: 0
                        activityBinding!!.appBar.animate().y(0f)
                            .setDuration(shortAnimationDuration.toLong())
                            .start()
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
                        if (elevate) elevateFunc(false)
                    } else {
                        if (!isTablet) {
                            activityBinding!!.appBar.y -= dy
                            activityBinding!!.appBar.y = MathUtils.clamp(
                                activityBinding!!.appBar.y,
                                -activityBinding!!.appBar.height.toFloat(),
                                0f
                            )
                            activityBinding!!.bottomNav?.let { bottomNav ->
                                if (bottomNav.isVisible && isInView) {
                                    if (preferences.hideBottomNavOnScroll().get()) {
                                        bottomNav.translationY += dy
                                        bottomNav.translationY = MathUtils.clamp(
                                            bottomNav.translationY,
                                            0f,
                                            bottomNav.height.toFloat()
                                        )
                                        updateViewsNearBottom()
                                    } else if (bottomNav.translationY != 0f) {
                                        bottomNav.translationY = 0f
                                        activityBinding!!.bottomView?.translationY = 0f
                                    }
                                }
                            }

                            if (!elevate && (
                                dy == 0 ||
                                    (
                                        activityBinding!!.appBar.y <= -activityBinding!!.appBar.height.toFloat() ||
                                            dy == 0 && activityBinding!!.appBar.y == 0f
                                        )
                                )
                            ) {
                                elevateFunc(true)
                            }
                        } else {
                            val notAtTop = recycler.canScrollVertically(-1)
                            if (notAtTop != elevate) elevateFunc(notAtTop)
                        }
                        lastY = activityBinding!!.appBar.y
                    }
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (isTablet) {
                        return
                    }
                    if (router?.backstack?.lastOrNull()
                        ?.controller == this@scrollViewWith && statusBarHeight > -1 &&
                        activity != null && activityBinding!!.appBar.height > 0 &&
                        recycler.translationY == 0f
                    ) {
                        val halfWay = activityBinding!!.appBar.height.toFloat() / 2
                        val shortAnimationDuration = resources?.getInteger(
                            android.R.integer.config_shortAnimTime
                        ) ?: 0
                        val closerToTop = abs(activityBinding!!.appBar.y) > halfWay
                        val halfWayBottom = (activityBinding!!.bottomNav?.height?.toFloat() ?: 0f) / 2
                        val closerToBottom = activityBinding!!.bottomNav?.translationY ?: 0f > halfWayBottom
                        val atTop = !recycler.canScrollVertically(-1)
                        val closerToEdge =
                            if (activityBinding!!.bottomNav?.isVisible == true &&
                                preferences.hideBottomNavOnScroll().get()
                            ) closerToBottom else closerToTop
                        lastY =
                            if (closerToEdge && !atTop) (-activityBinding!!.appBar.height.toFloat()) else 0f
                        activityBinding!!.appBar.animate().y(lastY)
                            .setDuration(shortAnimationDuration.toLong()).start()
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
                        if (recycler.canScrollVertically(-1) && !elevate) elevateFunc(true)
                        else if (!recycler.canScrollVertically(-1) && elevate) elevateFunc(false)
                    }
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    val view = activity?.window?.currentFocus ?: return
                    val imm =
                        activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            ?: return
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                }
            }
        }
    )
    return elevateFunc
}

fun Controller.requestPermissionsSafe(permissions: Array<String>, requestCode: Int) {
    val activity = activity ?: return
    permissions.forEach { permission ->
        if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), requestCode)
        }
    }
}

fun Controller.requestFilePermissionsSafe(
    requestCode: Int,
    preferences: PreferencesHelper,
    showA11PermissionAnyway: Boolean = false
) {
    val activity = activity ?: return
    val permissions = mutableListOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    permissions.forEach { permission ->
        if (ContextCompat.checkSelfPermission(
                activity,
                permission
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
        MaterialDialog(activity)
            .title(R.string.all_files_permission_required)
            .message(R.string.external_storage_permission_notice)
            .cancelOnTouchOutside(false)
            .positiveButton(android.R.string.ok) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    "package:${activity.packageName}".toUri()
                )
                try {
                    activity.startActivity(intent)
                } catch (_: Exception) {
                    val intent2 = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    activity.startActivity(intent2)
                }
            }
            .negativeButton(android.R.string.cancel)
            .show()
    }
}

fun Controller.withFadeTransaction(): RouterTransaction {
    return RouterTransaction.with(this)
        .pushChangeHandler(OneWayFadeChangeHandler())
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
