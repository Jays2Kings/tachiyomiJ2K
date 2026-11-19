package eu.kanade.tachiyomi.ui.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.assist.AssistContent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.transition.addListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.WindowInsetsCompat.Type.tappableElement
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.updatePaddingRelative
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import com.google.android.material.slider.SliderOrientation
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.data.preference.toggle
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.databinding.ReaderActivityBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.SearchActivity
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.AddToLibraryFirst
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Error
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Success
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.settings.FlashColor
import eu.kanade.tachiyomi.ui.reader.settings.OrientationType
import eu.kanade.tachiyomi.ui.reader.settings.PageLayout
import eu.kanade.tachiyomi.ui.reader.settings.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.settings.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.settings.TabbedReaderSettingsSheet
import eu.kanade.tachiyomi.ui.reader.viewer.BaseViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.L2RPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.VerticalPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.preferredChapterName
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.ThemeUtil
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getBottomGestureInsets
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.hasSideNavBar
import eu.kanade.tachiyomi.util.system.ignoredSystemInsets
import eu.kanade.tachiyomi.util.system.isBottomTappable
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchNonCancellable
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.spToPx
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.backgroundColor
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.hide
import eu.kanade.tachiyomi.util.view.isCollapsed
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.popupMenu
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.widget.doOnEnd
import eu.kanade.tachiyomi.widget.doOnStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Collections
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Activity containing the reader of Tachiyomi. This activity is mostly a container of the
 * viewers, to which calls from the view model or UI events are delegated.
 */
class ReaderActivity : BaseActivity<ReaderActivityBinding>() {
    val viewModel by viewModels<ReaderViewModel>()

    val scope = lifecycleScope

    /**
     * Viewer used to display the pages (pager, webtoon, ...).
     */
    var viewer: BaseViewer? = null
        private set

    /**
     * Whether the menu is currently visible.
     */
    var menuVisible = false
        private set

    /**
     * Whether the menu should stay visible.
     */
    private var menuTemporarilyVisible = false

    private var coroutine: Job? = null

    private var flashJob: Job? = null
    private var flashTimesCalled = 0

    private var fromUrl = false

    /**
     * Configuration at reader level, like background color or forced orientation.
     */
    private var config: ReaderConfig? = null

    /**
     * Current Bottom Sheet on display, used to dismiss
     */
    private var bottomSheet: BottomSheetDialog? = null

    var sheetManageNavColor = false

    private var themeLightStatusBars = false

    private val wic by lazy { WindowInsetsControllerCompat(window, binding.root) }
    private var lastVis = false

    private var snackbar: Snackbar? = null

    private var intentPageNumber: Int? = null

    var isLoading = false

    private var lastShiftDoubleState: Boolean? = null
    private var indexPageToShift: Int? = null
    private var indexChapterToShift: Long? = null

    private var lastCropRes = 0
    var manuallyShiftedPages = false
        private set

    private var pendingVerticalSeekbarHeightUpdate = false

    private var didTransitionFromChapter = false
    private var visibleChapterRange = longArrayOf()
    private var backPressedCallback: OnBackPressedCallback? = null

    var isScrollingThroughPagesOrChapters = false

    /**
     * Whether the page seekbar's value is currently being changed by an active touch/mouse drag,
     * as opposed to a gamepad/keyboard adjustment. Used to limit haptic feedback to a pointer
     * drag, since that's the only one of these with a physical surface to actually feel it.
     */
    private var isPointerAdjustingSeekbar = false
    private var hingeGapSize = 0
        set(value) {
            field = value
            (viewer as? PagerViewer)?.config?.hingeGapSize = value
        }

    companion object {
        const val SHIFT_DOUBLE_PAGES = "shiftingDoublePages"
        const val SHIFTED_PAGE_INDEX = "shiftedPageIndex"
        const val SHIFTED_CHAP_INDEX = "shiftedChapterIndex"

        const val TRANSITION_NAME = "${BuildConfig.APPLICATION_ID}.TRANSITION_NAME"
        const val VISIBLE_CHAPTERS = "${BuildConfig.APPLICATION_ID}.VISIBLE_CHAPTERS"

        fun newIntent(
            context: Context,
            manga: Manga,
            chapter: Chapter,
        ): Intent {
            MainActivity.chapterIdToExitTo = 0L
            val intent = Intent(context, ReaderActivity::class.java)
            intent.putExtra("manga", manga.id)
            intent.putExtra("chapter", chapter.id)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }

        fun newIntentWithTransitionOptions(
            activity: Activity,
            manga: Manga,
            chapter: Chapter,
            sharedElement: View,
        ): Pair<Intent, Bundle?> {
            MainActivity.chapterIdToExitTo = 0L
            val intent = newIntent(activity, manga, chapter)
            intent.putExtra(TRANSITION_NAME, sharedElement.transitionName)
            val activityOptions =
                ActivityOptions.makeSceneTransitionAnimation(
                    activity,
                    sharedElement,
                    sharedElement.transitionName,
                )
            return intent to activityOptions.toBundle()
        }
    }

    /**
     * Called when the activity is created. Initializes the view model and configuration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        SubsamplingScaleImageView.setPreferredBitmapConfig(Bitmap.Config.ARGB_8888)

        // Setup shared element transitions
        if (savedInstanceState == null && intent.extras?.getString(TRANSITION_NAME) != null) {
            window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
            findViewById<View>(android.R.id.content)?.let { contentView ->
                MainActivity.chapterIdToExitTo = 0L
                contentView.transitionName = intent.extras?.getString(TRANSITION_NAME)
                visibleChapterRange = intent.extras?.getLongArray(VISIBLE_CHAPTERS) ?: longArrayOf()
                didTransitionFromChapter = contentView.transitionName.contains("details chapter")
                setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
                window.sharedElementEnterTransition = buildContainerTransform(true)
                window.sharedElementReturnTransition = buildContainerTransform(false)
                // Postpone custom transition until manga ready
                postponeEnterTransition()
            }
        }

        super.onCreate(savedInstanceState)
        binding = ReaderActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        withStyledAttributes(null, intArrayOf(android.R.attr.windowLightStatusBar)) {
            themeLightStatusBars = getBoolean(0, false)
        }
        setCutoutMode()

        wic.isAppearanceLightStatusBars = themeLightStatusBars
        wic.isAppearanceLightNavigationBars = themeLightStatusBars
        binding.appBar.setBackgroundColor(contextCompatColor(R.color.surface_alpha))
        ViewCompat.setBackgroundTintList(
            binding.readerNav.root,
            ColorStateList.valueOf(contextCompatColor(R.color.surface_alpha)),
        )

        backPressedCallback =
            object : OnBackPressedCallback(enabled = true) {
                override fun handleOnBackPressed() {
                    if (binding.chaptersSheet.root.sheetBehavior
                            .isExpanded()
                    ) {
                        binding.chaptersSheet.root.lastScale = binding.chaptersSheet.root.scaleX
                        binding.chaptersSheet.root.sheetBehavior
                            ?.collapse()
                    }
                    reEnableBackPressedCallBack()
                }

                override fun handleOnBackStarted(backEvent: BackEventCompat) {
                    if (binding.chaptersSheet.root.sheetBehavior
                            .isExpanded()
                    ) {
                        binding.chaptersSheet.root.sheetBehavior
                            ?.startBackProgress(backEvent)
                    }
                }

                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    if (binding.chaptersSheet.root.sheetBehavior
                            .isExpanded()
                    ) {
                        binding.chaptersSheet.root.sheetBehavior
                            ?.updateBackProgress(backEvent)
                    }
                }

                override fun handleOnBackCancelled() {
                    if (binding.chaptersSheet.root.sheetBehavior
                            .isExpanded()
                    ) {
                        binding.chaptersSheet.root.sheetBehavior
                            ?.cancelBackProgress()
                    }
                }
            }
        onBackPressedDispatcher.addCallback(backPressedCallback!!)
        if (viewModel.needsInit()) {
            fromUrl = handleIntentAction(intent)
            if (!fromUrl) {
                val manga = intent.extras!!.getLong("manga", -1)
                val chapter = intent.extras!!.getLong("chapter", -1)
                if (manga == -1L || chapter == -1L) {
                    finish()
                    return
                }
                lifecycleScope.launchNonCancellable {
                    val initResult = viewModel.init(manga, chapter)
                    if (!initResult.getOrDefault(false)) {
                        val exception = initResult.exceptionOrNull() ?: IllegalStateException("Unknown err")
                        withUIContext {
                            setInitialChapterError(exception)
                        }
                    } else {
                        withUIContext {
                            SecureActivityDelegate.setSecure(this@ReaderActivity, viewModel.manga?.source)
                        }
                    }
                }
            } else {
                binding.pleaseWait.isVisible = true
            }
        }

        if (savedInstanceState != null) {
            menuVisible = savedInstanceState.getBoolean(::menuVisible.name)
            lastShiftDoubleState =
                savedInstanceState
                    .getBoolean(SHIFT_DOUBLE_PAGES)
                    .takeIf { savedInstanceState.containsKey(SHIFT_DOUBLE_PAGES) }
            indexPageToShift =
                savedInstanceState
                    .getInt(SHIFTED_PAGE_INDEX, Int.MIN_VALUE)
                    .takeIf { it != Int.MIN_VALUE }
            indexChapterToShift =
                savedInstanceState
                    .getLong(SHIFTED_CHAP_INDEX, Long.MIN_VALUE)
                    .takeIf { it != Long.MIN_VALUE }
            binding.readerNav.root.isInvisible = !menuVisible
        } else {
            binding.readerNav.root.isInvisible = true
        }

        binding.chaptersSheet.chaptersBottomSheet.setup(this)
        config = ReaderConfig()
        initializeMenu()

        preferences
            .incognitoMode()
            .asImmediateFlowIn(lifecycleScope) {
                SecureActivityDelegate.setSecure(this, viewModel.manga?.source)
            }
        reEnableBackPressedCallBack()

        viewModel.state
            .map { it.isLoadingAdjacentChapter }
            .distinctUntilChanged()
            .onEach(::setProgressDialog)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.manga }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setManga)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.viewerChapters }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setChapters)
            .launchIn(lifecycleScope)

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    ReaderViewModel.Event.ReloadMangaAndChapters -> {
                        viewModel.manga?.let(::setManga)
                        viewModel.state.value.viewerChapters
                            ?.let(::setChapters)
                    }
                    ReaderViewModel.Event.ReloadViewerChapters -> {
                        viewModel.state.value.viewerChapters
                            ?.let(::setChapters)
                    }
                    is ReaderViewModel.Event.SetOrientation -> {
                        setOrientation(event.orientation)
                    }
                    is ReaderViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is ReaderViewModel.Event.ShareImage -> {
                        onShareImageResult(event.file, event.page)
                    }
                    is ReaderViewModel.Event.SetCoverResult -> {
                        onSetAsCoverResult(event.result)
                    }
                    is ReaderViewModel.Event.ShareTrackingError -> {
                        showTrackingError(event.errors)
                    }
                }
            }.launchIn(lifecycleScope)

        lifecycleScope.launchUI {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker
                    .getOrCreate(this@ReaderActivity)
                    .windowLayoutInfo(this@ReaderActivity)
                    .collect { newLayoutInfo ->
                        hingeGapSize = 0
                        for (displayFeature: DisplayFeature in newLayoutInfo.displayFeatures) {
                            if (displayFeature is FoldingFeature &&
                                displayFeature.occlusionType == FoldingFeature.OcclusionType.FULL &&
                                displayFeature.isSeparating &&
                                displayFeature.orientation == FoldingFeature.Orientation.VERTICAL
                            ) {
                                hingeGapSize = displayFeature.bounds.width()
                            }
                        }
                        if (hingeGapSize > 0) {
                            binding.navLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                                gravity = Gravity.TOP or Gravity.CENTER
                                anchorGravity = Gravity.TOP or Gravity.CENTER
                                width = (binding.root.width - hingeGapSize) / 2 - 24.dpToPx
                            }
                            binding.chaptersSheet.root.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                                gravity = Gravity.END
                                width = (binding.root.width - hingeGapSize) / 2
                            }
                            binding.pleaseWait.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                                marginStart = binding.root.width / 2 + hingeGapSize
                            }
                        }
                    }
            }
        }
    }

    /**
     * Called when the activity is destroyed. Cleans up the viewer, configuration and any view.
     */
    override fun onDestroy() {
        super.onDestroy()
        viewer?.destroy()
        binding.chaptersSheet.chaptersBottomSheet.adapter = null
        viewer = null
        config = null
        bottomSheet?.dismiss()
        bottomSheet = null
        snackbar?.dismiss()
        snackbar = null
    }

    /**
     * Called when the activity is saving instance state. Current progress is persisted if this
     * activity isn't changing configurations.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(::menuVisible.name, menuVisible)
        (viewer as? PagerViewer)?.let { pViewer ->
            val config = pViewer.config
            if (config.doublePages) {
                outState.putBoolean(SHIFT_DOUBLE_PAGES, config.shiftDoublePage)
            }
            if (config.shiftDoublePage && config.doublePages) {
                pViewer.getShiftedPage()?.let {
                    outState.putInt(SHIFTED_PAGE_INDEX, it.index)
                    outState.putLong(SHIFTED_CHAP_INDEX, it.chapter.chapter.id ?: 0L)
                }
            }
        }
        viewModel.onSaveInstanceState()
        super.onSaveInstanceState(outState)
    }

    /**
     * Called when the options menu of the binding.toolbar is being created. It adds our custom menu.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.reader, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val splitItem = menu.findItem(R.id.action_shift_double_page)
        splitItem?.isVisible = ((viewer as? PagerViewer)?.config?.doublePages ?: false) && !canShowSplitAtBottom()
        binding.chaptersSheet.shiftPageButton.isVisible = ((viewer as? PagerViewer)?.config?.doublePages ?: false) && canShowSplitAtBottom()
        updateSums()
        (viewer as? PagerViewer)?.config?.let { config ->
            val icon =
                ContextCompat.getDrawable(
                    this,
                    if ((!config.shiftDoublePage).xor(
                            viewer is R2LPagerViewer,
                        )
                    ) {
                        R.drawable.ic_page_previous_outline_24dp
                    } else {
                        R.drawable.ic_page_next_outline_24dp
                    },
                )
            splitItem?.icon = icon
            binding.chaptersSheet.shiftPageButton.icon = icon
        }
        setBottomNavButtons(preferences.pageLayout().get())
        (binding.toolbar.background as? LayerDrawable)?.let { layerDrawable ->
            val isDoublePage = splitItem?.isVisible ?: false
            // Shout out to Google for not fixing setVisible https://issuetracker.google.com/issues/127538945
            layerDrawable.findDrawableByLayerId(R.id.layer_full_width).alpha = if (!isDoublePage) 255 else 0
            layerDrawable.findDrawableByLayerId(R.id.layer_one_item).alpha = if (isDoublePage) 255 else 0
        }
        return super.onPrepareOptionsMenu(menu)
    }

    private fun canShowSplitAtBottom(): Boolean =
        if (preferences.readerBottomButtons().isNotSet()) {
            isTablet()
        } else {
            ReaderBottomButton.ShiftDoublePage.isIn(preferences.readerBottomButtons().get())
        }

    fun setBottomNavButtons(pageLayout: Int) {
        val isDoublePage =
            pageLayout == PageLayout.DOUBLE_PAGES.value ||
                (pageLayout == PageLayout.AUTOMATIC.value && (viewer as? PagerViewer)?.config?.doublePages ?: false)
        binding.chaptersSheet.doublePage.icon =
            ContextCompat.getDrawable(
                this,
                when {
                    isDoublePage -> R.drawable.ic_book_open_variant_24dp
                    (viewer as? PagerViewer)?.config?.splitPages == true -> R.drawable.ic_book_open_split_24dp
                    else -> R.drawable.ic_single_page_24dp
                },
            )
        with(binding.readerNav) {
            listOf(leftPageText, rightPageText).forEach {
                it.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    val isCurrent = (viewer is R2LPagerViewer && !binding.readerNav.pageSeekbar.isVertical).xor(it === leftPageText)
                    width = if (isDoublePage && isCurrent) 48.spToPx else 32.spToPx
                }
            }
        }
    }

    /**
     * Re-evaluates whether the current manga's reading mode should use a vertical seekbar
     * and applies the resulting layout to the nav bar.
     */
    private fun reapplyVerticalSeekbarLayout() {
        updateNavBarOrientation(wantsVerticalSeekbar())
    }

    // Only flips orientation if the size-gated result actually differs from what's currently applied;
    // otherwise just refreshes the height (cheap, already-debounced) to avoid requestLayout churn on
    // every resize/inset tick when nothing about vertical-vs-horizontal actually needs to change.
    private fun reapplyVerticalSeekbarLayoutIfSizeChanged() {
        val vertical = wantsVerticalSeekbar()
        if (vertical != binding.readerNav.pageSeekbar.isVertical) {
            updateNavBarOrientation(vertical)
        } else if (vertical) {
            updateVerticalSeekbarHeight()
        }
    }

    private fun wantsVerticalSeekbar(): Boolean {
        val mangaViewer = ReadingModeType.fromPreference(viewModel.getMangaReadingMode())
        val modeWantsVertical = mangaViewer.prefValue.toString() in preferences.readerVerticalSeekbarModes().get()
        return modeWantsVertical && hasEnoughHeightForVerticalSeekbar()
    }

    // Split-screen/multi-window can shrink the reader well below a usable vertical-bar height; fall
    // back to the horizontal bar in that case without touching the underlying preference.
    private fun hasEnoughHeightForVerticalSeekbar(): Boolean {
        val rootHeight = binding.root.height
        if (rootHeight <= 0) return true
        val insets = binding.root.rootWindowInsetsCompat?.ignoredSystemInsets
        val availableHeight = rootHeight - (insets?.top ?: 0) - (insets?.bottom ?: 0)
        return availableHeight > 300.dpToPx
    }

    @SuppressLint("RtlHardcoded")
    private fun updateNavBarOrientation(vertical: Boolean) {
        // R2L only flips left/right when horizontal (rotation already handles vertical's value direction); re-swap if this call changes that.
        val flipChanged = viewer is R2LPagerViewer && binding.readerNav.pageSeekbar.isVertical != vertical
        val orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        val dockLeft = preferences.readerVerticalSeekbarDockLeft().get()
        with(binding.readerNav) {
            root.orientation = orientation
            readerSeekbar.orientation = orientation
            pageSeekbar.setOrientation(if (vertical) SliderOrientation.VERTICAL else SliderOrientation.HORIZONTAL)
            pageSeekbar.scaleY = if (vertical) -1f else 1f
            binding.readerNav.pageSeekbar.isRTL =
                if (vertical) {
                    dockLeft
                } else {
                    viewer is R2LPagerViewer
                }
            leftChapter.rotation = if (vertical) 90f else 0f
            rightChapter.rotation = if (vertical) 90f else 0f

            readerSeekbar.updateLayoutParams<LinearLayout.LayoutParams> {
                if (vertical) {
                    width = LinearLayout.LayoutParams.WRAP_CONTENT
                    height = 0
                } else {
                    width = 0
                    height = LinearLayout.LayoutParams.WRAP_CONTENT
                }
            }
            pageSeekbar.updateLayoutParams<LinearLayout.LayoutParams> {
                if (vertical) {
                    width = 40.dpToPx
                    height = 0
                } else {
                    width = 0
                    height = 40.dpToPx
                }
            }
        }

        binding.navLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            if (vertical) {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                anchorGravity = Gravity.TOP or if (dockLeft) Gravity.LEFT else Gravity.RIGHT
            } else {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                anchorGravity = Gravity.TOP
            }
        }
        if (vertical) {
            binding.readerNav.root.updatePaddingRelative(2.dpToPx, 12.dpToPx, 2.dpToPx, 12.dpToPx)
        } else {
            binding.readerNav.root.updatePaddingRelative(12.dpToPx, 6.dpToPx, 12.dpToPx, 6.dpToPx)
        }
        binding.readerNav.root.updateLayoutParams<FrameLayout.LayoutParams> {
            width = if (vertical) FrameLayout.LayoutParams.WRAP_CONTENT else FrameLayout.LayoutParams.MATCH_PARENT
            if (!vertical) height = FrameLayout.LayoutParams.WRAP_CONTENT
        }
        if (vertical) updateVerticalSeekbarHeight()

        if (flipChanged) {
            with(binding.readerNav) {
                val alpha = leftChapter.alpha
                leftChapter.alpha = rightChapter.alpha
                rightChapter.alpha = alpha

                val pageText = leftPageText.text
                leftPageText.text = rightPageText.text
                rightPageText.text = pageText

                val tooltip = leftChapter.tooltipText
                leftChapter.tooltipText = rightChapter.tooltipText
                rightChapter.tooltipText = tooltip
            }
        }
    }

    // root's height may not be measured yet on first call; a persistent layout listener retries this later.
    // The actual write is deferred to post{} (and coalesced via the pending flag) since mutating
    // LayoutParams synchronously from a layout-change callback re-triggers a layout pass mid-layout.
    private fun updateVerticalSeekbarHeight() {
        if (!binding.readerNav.pageSeekbar.isVertical) return
        if (pendingVerticalSeekbarHeightUpdate) return
        pendingVerticalSeekbarHeightUpdate = true
        binding.readerNav.root.post {
            pendingVerticalSeekbarHeightUpdate = false
            if (!binding.readerNav.pageSeekbar.isVertical) return@post
            val rootHeight = binding.root.height
            if (rootHeight <= 0) return@post
            val heightPercent = preferences.readerVerticalSeekbarHeightPercent().get()
            val availableHeight =
                rootHeight - binding.appBar.height - (
                    binding.chaptersSheet.root.sheetBehavior
                        ?.peekHeight ?: 0
                ) - 12.dpToPx
            val newHeight = (availableHeight * heightPercent / 100).coerceAtLeast(200.dpToPx)
            binding.readerNav.root.updateLayoutParams<FrameLayout.LayoutParams> {
                if (height != newHeight) height = newHeight
            }
        }
    }

    private fun updateOrientationShortcut(preference: Int) {
        val orientation = OrientationType.fromPreference(preference)
        binding.chaptersSheet.rotationSheetButton.setIconResource(orientation.iconRes)
    }

    private fun updateCropBordersShortcut() {
        val isPagerType = viewer is PagerViewer || (viewer as? WebtoonViewer)?.hasMargins == true
        val enabled =
            if (isPagerType) {
                preferences.cropBorders().get()
            } else {
                preferences.cropBordersWebtoon().get()
            }

        with(binding.chaptersSheet.cropBordersSheetButton) {
            val drawableRes =
                if (enabled) {
                    R.drawable.anim_free_to_crop
                } else {
                    R.drawable.anim_crop_to_free
                }
            if (lastCropRes != drawableRes) {
                val drawable = AnimatedVectorDrawableCompat.create(context, drawableRes)
                icon = drawable
                drawable?.start()
                lastCropRes = drawableRes
            }
            tooltipText =
                getString(
                    if (enabled) {
                        R.string.remove_crop
                    } else {
                        R.string.crop_borders
                    },
                )
        }
    }

    private fun updateBottomShortcuts() {
        val enabledButtons = preferences.readerBottomButtons().get()
        with(binding.chaptersSheet) {
            readingMode.isVisible = ReaderBottomButton.ReadingMode.isIn(enabledButtons)
            rotationSheetButton.isVisible =
                ReaderBottomButton.Rotation.isIn(enabledButtons)
            doublePage.isVisible = viewer is PagerViewer &&
                ReaderBottomButton.PageLayout.isIn(enabledButtons)
            cropBordersSheetButton.isVisible =
                if (viewer is PagerViewer) {
                    ReaderBottomButton.CropBordersPaged.isIn(enabledButtons)
                } else {
                    ReaderBottomButton.CropBordersWebtoon.isIn(enabledButtons)
                }
            webviewButton.isVisible =
                ReaderBottomButton.WebView.isIn(enabledButtons)
            chaptersButton.isVisible =
                ReaderBottomButton.ViewChapters.isIn(enabledButtons)
            shiftPageButton.isVisible =
                ((viewer as? PagerViewer)?.config?.doublePages ?: false) &&
                canShowSplitAtBottom()
            updateSums()
        }
        binding.toolbar.menu
            .findItem(R.id.action_shift_double_page)
            ?.isVisible =
            ((viewer as? PagerViewer)?.config?.doublePages ?: false) &&
            !canShowSplitAtBottom()
    }

    private fun updateSums() {
        with(binding.chaptersSheet) {
            val visibleButtons =
                listOf(
                    chaptersButton,
                    webviewButton,
                    readingMode,
                    rotationSheetButton,
                    cropBordersSheetButton,
                    doublePage,
                    shiftPageButton,
                ).count { it.isVisible }
            // Plus one for the always visible settings button
            buttonGroup.weightSum = 1f + visibleButtons
        }
    }

    /**
     * Called when an item of the options menu was clicked. Used to handle clicks on our menu
     * entries.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_shift_double_page -> {
                shiftDoublePages()
                manuallyShiftedPages = true
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    fun shiftDoublePages(
        forceShift: Boolean? = null,
        page: ReaderPage? = null,
    ) {
        (viewer as? PagerViewer)?.let { pViewer ->
            if (forceShift == pViewer.config.shiftDoublePage) return
            pViewer.config.shiftDoublePage = !pViewer.config.shiftDoublePage
            viewModel.state.value.viewerChapters?.let {
                pViewer.updateShifting(page)
                pViewer.setChaptersDoubleShift(it)
                invalidateOptionsMenu()
            }
        }
    }

    private fun popToMain() {
        if (fromUrl) {
            val intent =
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            startActivity(intent)
            finishAfterTransition()
        } else {
            backPressedCallback?.isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

    fun reEnableBackPressedCallBack() {
        backPressedCallback?.isEnabled =
            binding.chaptersSheet.chaptersBottomSheet.sheetBehavior
                .isExpanded()
    }

    override fun finishAfterTransition() {
        if (didTransitionFromChapter && visibleChapterRange.isNotEmpty() && MainActivity.chapterIdToExitTo !in visibleChapterRange) {
            finish()
        } else {
            viewModel.onBackPressed()
            super.finishAfterTransition()
        }
    }

    override fun finish() {
        viewModel.onBackPressed()
        super.finish()
    }

    /**
     * Dispatches a key event. If the viewer doesn't handle it, call the default implementation.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Consume confirm buttons so they don't activate menu buttons while theyre hidden
        if (!menuVisible && isConfirmKeyCode(event.keyCode)) {
            return true
        }
        val handled = viewer?.handleKeyEvent(event) ?: false
        return handled || super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        // Each of these should only fire once per physical press, not on every auto-repeated
        // ACTION_DOWN a held key/button generates - matching the old ACTION_UP-based firing,
        // which has no repeat concept, only a single release.
        val isInitialPress = event?.repeatCount == 0
        when (keyCode) {
            KeyEvent.KEYCODE_N -> {
                if (isInitialPress) {
                    if (viewer is R2LPagerViewer && !binding.readerNav.pageSeekbar.isVertical) {
                        binding.readerNav.leftChapter.performClick()
                    } else {
                        binding.readerNav.rightChapter.performClick()
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_P -> {
                if (isInitialPress) {
                    if (viewer !is R2LPagerViewer && !binding.readerNav.pageSeekbar.isVertical) {
                        binding.readerNav.leftChapter.performClick()
                    } else {
                        binding.readerNav.rightChapter.performClick()
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_L -> {
                if (isInitialPress) binding.readerNav.leftChapter.performClick()
                return true
            }
            KeyEvent.KEYCODE_R -> {
                if (isInitialPress) binding.readerNav.rightChapter.performClick()
                return true
            }
            KeyEvent.KEYCODE_E -> {
                if (isInitialPress) viewer?.moveToNext()
                return true
            }
            KeyEvent.KEYCODE_Q -> {
                if (isInitialPress) viewer?.moveToPrevious()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_START -> {
                if (isInitialPress) toggleMenu()
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (isInitialPress && !event.isFromSource(InputDevice.SOURCE_GAMEPAD)) {
                    toggleMenu()
                }
                return true
            }
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_C -> {
                if (isInitialPress) toggleChapterList()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (!isInitialPress) return true
                if (viewer?.isAtEndOfReader() == true) {
                    return false
                }
                if (binding.chaptersSheet.root.sheetBehavior
                        .isExpanded() &&
                    menuVisible
                ) {
                    binding.chaptersSheet.root.lastScale = binding.chaptersSheet.root.scaleX
                    binding.chaptersSheet.root.sheetBehavior
                        ?.collapse()
                    binding.chaptersSheet.chaptersBottomSheet.focusFirstReaderBottomButton()
                } else if (menuVisible) {
                    hideMenu()
                } else {
                    return false
                }
                return true
            }
            else -> return super.onKeyDown(keyCode, event)
        }
    }

    /**
     * Shows the menu and expands the chapter list sheet, or collapses it if it's already
     * expanded.
     */
    private fun toggleChapterList() {
        with(binding.chaptersSheet.chaptersBottomSheet) {
            if (sheetBehavior?.isExpanded() == false) {
                if (!menuVisible) {
                    toggleMenu()
                }
                sheetBehavior?.expand()
                focusCurrentChapter()
            } else {
                sheetBehavior?.collapse()
            }
        }
    }

    /**
     * Dispatches a generic motion event. If the viewer doesn't handle it, call the default
     * implementation.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val handled = viewer?.handleGenericMotionEvent(event) ?: false
        return handled || super.dispatchGenericMotionEvent(event)
    }

    private fun buildContainerTransform(entering: Boolean): MaterialContainerTransform =
        MaterialContainerTransform(this, entering).apply {
            duration =
                (
                    resources?.getInteger(
                        if (entering) {
                            android.R.integer.config_longAnimTime
                        } else {
                            android.R.integer.config_mediumAnimTime
                        },
                    ) ?: 500
                ).toLong()
            addTarget(android.R.id.content)
        }

    /**
     * Initializes the reader menu. It sets up click listeners and the initial visibility.
     */
    @SuppressLint("ClickableViewAccessibility", "RtlHardcoded")
    private fun initializeMenu() {
        // Set binding.toolbar
        setSupportActionBar(binding.toolbar)
        val primaryColor =
            ColorUtils.setAlphaComponent(
                getResourceColor(R.attr.colorSurface),
                200,
            )
        binding.appBar.setBackgroundColor(primaryColor)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.navigationIcon?.setTint(getResourceColor(R.attr.actionBarTintColor))
        binding.toolbar.setNavigationOnClickListener {
            popToMain()
        }

        binding.toolbar.setOnClickListener {
            viewModel.manga?.id?.let { id ->
                val intent = SearchActivity.openMangaIntent(this, id)
                startActivity(intent)
            }
        }

        binding.touchView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (!binding.chaptersSheet.chaptersBottomSheet.sheetBehavior.isCollapsed()) {
                    binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.collapse()
                }
            }
            true
        }

        with(binding.chaptersSheet) {
            with(doublePage) {
                setOnClickListener {
                    if (preferences.pageLayout().get() == PageLayout.AUTOMATIC.value) {
                        (viewer as? PagerViewer)?.config?.let { config ->
                            config.doublePages = !config.doublePages
                            reloadChapters(config.doublePages, true)
                        }
                    } else {
                        showPageLayoutMenu()
                    }
                }
                setOnLongClickListener {
                    showPageLayoutMenu()
                    true
                }
            }
            cropBordersSheetButton.setOnClickListener {
                val pref =
                    if ((viewer as? WebtoonViewer)?.hasMargins == true ||
                        (viewer is PagerViewer)
                    ) {
                        preferences.cropBorders()
                    } else {
                        preferences.cropBordersWebtoon()
                    }
                pref.toggle()
            }

            with(rotationSheetButton) {
                tooltipText = getString(R.string.rotation)

                setOnClickListener {
                    popupMenu(
                        items = OrientationType.entries.map { it.flagValue to it.stringRes },
                        selectedItemId =
                            viewModel.manga?.orientationType
                                ?: preferences.defaultOrientationType().get(),
                    ) {
                        val newOrientation = OrientationType.fromPreference(itemId)

                        viewModel.setMangaOrientationType(newOrientation.flagValue)

                        updateOrientationShortcut(newOrientation.flagValue)
                    }
                }
            }

            webviewButton.setOnClickListener {
                openMangaInBrowser()
            }

            displayOptions.setOnClickListener {
                TabbedReaderSettingsSheet(this@ReaderActivity).show()
            }

            displayOptions.setOnLongClickListener {
                TabbedReaderSettingsSheet(this@ReaderActivity, true).show()
                true
            }

            readingMode.setOnClickListener { readingMode ->
                readingMode.popupMenu(
                    items = ReadingModeType.entries.map { it.flagValue to it.stringRes },
                    selectedItemId = viewModel.manga?.readingModeType,
                ) {
                    viewModel.setMangaReadingMode(itemId)
                }
            }
        }

        listOf(preferences.cropBorders(), preferences.cropBordersWebtoon())
            .forEach { pref ->
                pref
                    .asFlow()
                    .onEach { updateCropBordersShortcut() }
                    .launchIn(scope)
            }

        preferences
            .readerVerticalSeekbarModes()
            .asFlow()
            .onEach { reapplyVerticalSeekbarLayout() }
            .launchIn(scope)
        preferences
            .readerVerticalSeekbarDockLeft()
            .asFlow()
            .onEach { reapplyVerticalSeekbarLayout() }
            .launchIn(scope)
        preferences
            .readerVerticalSeekbarHeightPercent()
            .asFlow()
            .onEach { reapplyVerticalSeekbarLayout() }
            .launchIn(scope)
        // appBar/chapters-sheet peek height only change alongside root's own height (insets, screen size), so only react to that.
        binding.root.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) reapplyVerticalSeekbarLayoutIfSizeChanged()
        }

        binding.chaptersSheet.shiftPageButton.setOnClickListener {
            shiftDoublePages()
            manuallyShiftedPages = true
        }

        binding.readerNav.leftChapter.setOnClickListener { loadAdjacentChapter(false) }
        binding.readerNav.rightChapter.setOnClickListener { loadAdjacentChapter(true) }

        binding.touchView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (binding.chaptersSheet.chaptersBottomSheet.sheetBehavior
                        .isExpanded()
                ) {
                    binding.chaptersSheet.chaptersBottomSheet.sheetBehavior
                        ?.collapse()
                }
            }
            false
        }
        val readerNavGestureDetector = ReaderNavGestureDetector(this)
        val gestureDetector = GestureDetector(this, readerNavGestureDetector)
        with(binding.readerNav) {
            binding.readerNav.pageSeekbar.addOnSliderTouchListener(
                object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {
                        readerNavGestureDetector.lockVertical = false
                        readerNavGestureDetector.hasScrollHorizontal = true
                        isScrollingThroughPagesOrChapters = true
                        isPointerAdjustingSeekbar = true
                    }

                    override fun onStopTrackingTouch(slider: Slider) {
                        isScrollingThroughPagesOrChapters = false
                        isPointerAdjustingSeekbar = false
                    }
                },
            )
            listOf(root, leftChapter, rightChapter, pageSeekbar).forEach {
                it.setOnTouchListener { _, event ->
                    val result = gestureDetector.onTouchEvent(event)
                    if (event?.action == MotionEvent.ACTION_UP) {
                        if (!result) {
                            val sheetBehavior = binding.chaptersSheet.root.sheetBehavior
                            if (sheetBehavior?.state != BottomSheetBehavior.STATE_SETTLING && !sheetBehavior.isCollapsed()) {
                                sheetBehavior?.collapse()
                            }
                        }
                        if (readerNavGestureDetector.lockVertical) {
                            return@setOnTouchListener true
                        }
                    } else if ((event?.action != MotionEvent.ACTION_UP || event.action != MotionEvent.ACTION_DOWN) && result) {
                        event.action = MotionEvent.ACTION_CANCEL
                        return@setOnTouchListener false
                    }
                    if (it == pageSeekbar) {
                        readerNavGestureDetector.lockVertical
                    } else {
                        result
                    }
                }
            }
        }

        // Init listeners on bottom menu
        binding.readerNav.pageSeekbar.addOnChangeListener { _, value, fromUser ->
            if (viewer != null && fromUser) {
                // setting isScrollingThroughPagesOrChapters here as well for d-pad navigation
                // as it is also from the user
                val wasScrollingThroughPagesOrChapters = isScrollingThroughPagesOrChapters
                isScrollingThroughPagesOrChapters = true
                val prevValue = (viewer as? PagerViewer)?.pager?.currentItem ?: -1
                moveToPageIndex(value.roundToInt())
                isScrollingThroughPagesOrChapters = wasScrollingThroughPagesOrChapters
                val newValue = (viewer as? PagerViewer)?.pager?.currentItem ?: -1
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
                    isPointerAdjustingSeekbar &&
                    ((prevValue > -1 && newValue != prevValue) || viewer !is PagerViewer)
                ) {
                    binding.readerNav.pageSeekbar.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                }
            }
        }

        binding.readerNav.pageSeekbar.setLabelFormatter { value ->
            val pageNumber = (value + 1).roundToInt()
            (viewer as? PagerViewer)?.let {
                if (it.config.doublePages || it.config.splitPages) {
                    if (it.hasExtraPage(value.roundToInt(), viewModel.getCurrentChapter())) {
                        val invertDoublePage = (viewer as? PagerViewer)?.config?.invertDoublePages ?: false
                        return@setLabelFormatter if ((
                                !binding.readerNav.pageSeekbar.isRTL ||
                                    binding.readerNav.pageSeekbar.isVertical
                            ).xor(invertDoublePage)
                        ) {
                            "$pageNumber-${pageNumber + 1}"
                        } else {
                            "${pageNumber + 1}-$pageNumber"
                        }
                    }
                }
            }
            pageNumber.toString()
        }

        // Set initial visibility
        setMenuVisibility(menuVisible, false)
        binding.chaptersSheet.chaptersBottomSheet.sheetBehavior
            ?.isHideable = !menuVisible
        if (!menuVisible) {
            binding.chaptersSheet.chaptersBottomSheet.sheetBehavior
                ?.hide()
        }
        binding.chaptersSheet.root.sheetBehavior
            ?.isGestureInsetBottomIgnored = true
        val peek = 50.dpToPx
        lastVis = window.decorView.rootWindowInsetsCompat?.isVisible(statusBars()) ?: false
        var firstPass = true
        binding.readerLayout.doOnApplyWindowInsetsCompat { _, insets, _ ->
            setNavColor(insets)
            val systemInsets = insets.getInsetsIgnoringVisibility(systemBars())
            val currentOrientation = resources.configuration.orientation
            val isLandscapeFully =
                currentOrientation == Configuration.ORIENTATION_LANDSCAPE &&
                    (preferences.landscapeCutoutBehavior().get() == 1 || Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            val vis = insets.isVisible(statusBars())
            val fullscreen = preferences.fullscreen().get()
            val systemCutoutInsets = insets.getInsetsIgnoringVisibility(systemBars() or displayCutout())
            val cutoutInsets = insets.getInsetsIgnoringVisibility(displayCutout())
            if (!firstPass && lastVis != vis && fullscreen && !isInMultiWindowMode) {
                onVisibilityChange(vis)
            }
            firstPass = false
            lastVis = vis
            wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            if (!(fullscreen && !isInMultiWindowMode) && sheetManageNavColor) {
                binding.navBar.backgroundColor = if (!fullscreen) Color.TRANSPARENT else getResourceColor(R.attr.colorSurface)
            }
            binding.navBar.isVisible = insets.isVisible(navigationBars())
            if (insets.hasSideNavBar()) {
                if (!fullscreen) {
                    wic.isAppearanceLightNavigationBars = themeLightStatusBars
                }
                val tappableElement = insets.getInsetsIgnoringVisibility(tappableElement())
                val navOnLeft = tappableElement.left > tappableElement.right
                binding.navBar.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                    val newGravity = if (navOnLeft) Gravity.LEFT else Gravity.RIGHT
                    if (gravity != newGravity) {
                        gravity = newGravity
                        height = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    width = if (navOnLeft) tappableElement.left else tappableElement.right
                }
            } else {
                binding.navBar.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                    if (gravity != Gravity.BOTTOM) {
                        gravity = Gravity.BOTTOM
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    height = insets.getInsetsIgnoringVisibility(tappableElement()).bottom
                }
            }
            binding.appBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = systemInsets.left
                rightMargin = systemInsets.right
            }
            binding.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemInsets.top
            }
            binding.chaptersSheet.chaptersBottomSheet.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = systemInsets.left
                rightMargin = systemInsets.right
                height = 280.dpToPx + systemInsets.bottom
            }
            binding.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = cutoutInsets.left
                rightMargin = cutoutInsets.right
            }
            binding.chaptersSheet.topbarLayout.updatePadding(
                left = cutoutInsets.left,
                right = cutoutInsets.right,
            )
            binding.chaptersSheet.chapterRecycler.updatePadding(
                left = cutoutInsets.left,
                right = cutoutInsets.right,
            )
            binding.navLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = 12.dpToPx + systemCutoutInsets.left
                rightMargin = 12.dpToPx + systemCutoutInsets.right
            }
            binding.chaptersSheet.root.sheetBehavior
                ?.peekHeight =
                peek + insets.getBottomGestureInsets()
            binding.chaptersSheet.chapterRecycler.updatePaddingRelative(bottom = systemInsets.bottom)
            val noInsetForFullScreen = fullscreen && !isInMultiWindowMode

            val insetsToUse = if (!fullscreen) systemCutoutInsets else systemInsets
            listOf(
                binding.viewerContainer,
                binding.navigationOverlay,
                binding.colorOverlay,
                binding.brightnessOverlay,
                binding.flashOverlay,
            ).forEach {
                it.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                    if (!isLandscapeFully) {
                        val cutoutInsets =
                            insets.getInsetsIgnoringVisibility(displayCutout())
                        leftMargin = cutoutInsets.left
                        rightMargin = cutoutInsets.right
                    } else {
                        leftMargin = if (noInsetForFullScreen) 0 else insetsToUse.left
                        rightMargin = if (noInsetForFullScreen) 0 else insetsToUse.right
                    }
                    topMargin = if (noInsetForFullScreen) 0 else insetsToUse.top
                    bottomMargin = if (noInsetForFullScreen) 0 else insetsToUse.bottom
                }
            }
            binding.pageNumber.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                bottomMargin = if (noInsetForFullScreen) 0 else systemInsets.bottom
            }
            binding.viewerContainer.requestLayout()
            reapplyVerticalSeekbarLayoutIfSizeChanged()
        }
    }

    private fun loadAdjacentChapter(rightButton: Boolean) {
        if (isLoading) {
            return
        }
        isScrollingThroughPagesOrChapters = true
        lifecycleScope.launch {
            val getNextChapter = (viewer is R2LPagerViewer && !binding.readerNav.pageSeekbar.isVertical).xor(rightButton)
            val adjChapter = viewModel.adjacentChapter(getNextChapter)
            if (adjChapter != null) {
                if (rightButton) {
                    binding.readerNav.rightChapter.isInvisible = true
                    binding.readerNav.rightProgress.isVisible = true
                } else {
                    binding.readerNav.leftChapter.isInvisible = true
                    binding.readerNav.leftProgress.isVisible = true
                }
                loadChapter(adjChapter)
            } else {
                toast(
                    if (getNextChapter) {
                        R.string.theres_no_next_chapter
                    } else {
                        R.string.theres_no_previous_chapter
                    },
                )
            }
        }
    }

    suspend fun loadChapter(chapter: Chapter) {
        loadChapter(ReaderChapter(chapter))
    }

    private suspend fun loadChapter(chapter: ReaderChapter) {
        val lastPage = viewModel.loadChapter(chapter) ?: return
        scope.launchUI {
            moveToPageIndex(lastPage, false, chapterChange = true)
        }
        refreshChapters()
    }

    fun setNavColor(insets: WindowInsetsCompat) {
        // Status bar is always transparent; navigationBarColor is deprecated on API 35+ (edge-to-edge
        // is enforced there and this setter is a no-op), kept only so pre-35 devices don't show the
        // system's default opaque nav bar behind our transparent content. The actual nav bar scrim is
        // drawn by binding.navBar instead, which works consistently across all API levels.
        sheetManageNavColor =
            when {
                isInMultiWindowMode -> {
                    binding.navBar.backgroundColor = getResourceColor(R.attr.colorSurfaceContainer)
                    false
                }
                insets.isBottomTappable() -> {
                    binding.navBar.backgroundColor = Color.TRANSPARENT
                    false
                }
                insets.hasSideNavBar() -> {
                    binding.navBar.backgroundColor =
                        ColorUtils.setAlphaComponent(
                            getResourceColor(R.attr.colorSurface),
                            200,
                        )
                    false
                }
                // if in portrait with 2/3 button mode, translucent nav bar
                else -> {
                    true
                }
            }
    }

    private fun showPageLayoutMenu() {
        with(binding.chaptersSheet.doublePage) {
            val config = (viewer as? PagerViewer)?.config
            val selectedId =
                when {
                    config?.doublePages == true -> PageLayout.DOUBLE_PAGES
                    config?.splitPages == true -> PageLayout.SPLIT_PAGES
                    else -> PageLayout.SINGLE_PAGE
                }
            popupMenu(
                items =
                    listOf(
                        PageLayout.SINGLE_PAGE,
                        PageLayout.DOUBLE_PAGES,
                        PageLayout.SPLIT_PAGES,
                    ).map { it.value to it.stringRes },
                selectedItemId = selectedId.value,
            ) {
                val newLayout = PageLayout.fromPreference(itemId)

                if (preferences.pageLayout().get() == PageLayout.AUTOMATIC.value) {
                    (viewer as? PagerViewer)?.config?.let { config ->
                        config.doublePages = newLayout == PageLayout.DOUBLE_PAGES
                        if (newLayout == PageLayout.SINGLE_PAGE) {
                            preferences.automaticSplitsPage().set(false)
                        } else if (newLayout == PageLayout.SPLIT_PAGES) {
                            preferences.automaticSplitsPage().set(true)
                        }
                        reloadChapters(config.doublePages, true)
                    }
                } else {
                    preferences.pageLayout().set(newLayout.value)
                }
            }
        }
    }

    fun hideMenu() {
        if (menuVisible && !isScrollingThroughPagesOrChapters) {
            setMenuVisibility(false)
        }
    }

    /**
     * Sets the visibility of the menu according to [visible] and with an optional parameter to
     * [animate] the views.
     */
    private fun setMenuVisibility(
        visible: Boolean,
        animate: Boolean = true,
    ) {
        val oldVisibility = menuVisible
        menuVisible = visible
        if (visible) coroutine?.cancel()
        binding.viewerContainer.requestLayout()
        if (visible) {
            snackbar?.dismiss()
            wic.show(systemBars())
            binding.appBar.isVisible = true
            wic.isAppearanceLightStatusBars = themeLightStatusBars
            wic.isAppearanceLightNavigationBars = themeLightStatusBars

            if (binding.chaptersSheet.chaptersBottomSheet.sheetBehavior
                    .isExpanded()
            ) {
                binding.chaptersSheet.chaptersBottomSheet.sheetBehavior
                    ?.isHideable = false
            }
            if (!binding.chaptersSheet.chaptersBottomSheet.sheetBehavior
                    .isExpanded() &&
                sheetManageNavColor
            ) {
                binding.navBar.backgroundColor = Color.TRANSPARENT
            }
            if (animate && oldVisibility != menuVisible) {
                if (!menuTemporarilyVisible) {
                    val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_top)
                    toolbarAnimation.doOnStart {
                        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                    }
                    toolbarAnimation.doOnEnd { delayTitleScroll() }
                    binding.appBar.startAnimation(toolbarAnimation)
                } else {
                    delayTitleScroll()
                }
                binding.chaptersSheet.chaptersBottomSheet.sheetBehavior
                    ?.collapse()
            }
        } else {
            binding.touchView.isVisible = false
            if (preferences.fullscreen().get() && !isInMultiWindowMode) {
                wic.hide(systemBars())
                wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            } else {
                wic.isAppearanceLightStatusBars = false
                wic.isAppearanceLightNavigationBars = window.decorView.rootWindowInsetsCompat?.hasSideNavBar() == true
            }

            if (animate && binding.appBar.isVisible) {
                val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.exit_to_top)
                toolbarAnimation.doOnEnd {
                    binding.appBar.isVisible = false
                    stopTitleScroll()
                }
                binding.appBar.startAnimation(toolbarAnimation)
                binding.chaptersSheet.chaptersBottomSheet.sheetBehavior
                    ?.isHideable = true
                binding.chaptersSheet.chaptersBottomSheet.sheetBehavior
                    ?.hide()
            } else if (!animate) {
                binding.appBar.isVisible = false
                stopTitleScroll()
            }
        }
        menuTemporarilyVisible = false
    }

    /**
     * Called from the view model when a manga is ready. Used to instantiate the appropriate viewer
     * and the binding.toolbar title.
     */
    private fun setManga(manga: Manga) {
        val prevViewer = viewer
        val noDefault = manga.viewer_flags == -1
        val mangaViewer = viewModel.getMangaReadingMode()
        val newViewer =
            when (mangaViewer) {
                ReadingModeType.LEFT_TO_RIGHT.flagValue -> L2RPagerViewer(this)
                ReadingModeType.VERTICAL.flagValue -> VerticalPagerViewer(this)
                ReadingModeType.WEBTOON.flagValue -> WebtoonViewer(this)
                ReadingModeType.CONTINUOUS_VERTICAL.flagValue -> WebtoonViewer(this, hasMargins = true)
                else -> R2LPagerViewer(this)
            }

        if (noDefault &&
            viewModel.manga?.readingModeType!! > 0 &&
            viewModel.manga?.readingModeType!! != preferences.defaultReadingMode()
        ) {
            snackbar =
                binding.readerLayout.snack(
                    getString(
                        R.string.reading_,
                        getString(
                            when (mangaViewer) {
                                ReadingModeType.RIGHT_TO_LEFT.flagValue -> R.string.right_to_left_viewer
                                ReadingModeType.VERTICAL.flagValue -> R.string.vertical_viewer
                                ReadingModeType.WEBTOON.flagValue, ReadingModeType.CONTINUOUS_VERTICAL.flagValue -> R.string.long_strip
                                else -> R.string.left_to_right_viewer
                            },
                        ).lowercase(Locale.getDefault()),
                    ),
                    4000,
                ) {
                    setAction(R.string.use_default) {
                        viewModel.setMangaReadingMode(0)
                    }
                }
        }

        if (window.sharedElementEnterTransition is MaterialContainerTransform &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            // Wait until transition is complete to avoid crash on API 26
            window.sharedElementEnterTransition.addListener(
                onEnd = { setOrientation(viewModel.getMangaOrientationType()) },
            )
        } else {
            setOrientation(viewModel.getMangaOrientationType())
        }

        // Destroy previous viewer if there was one
        if (prevViewer != null) {
            prevViewer.destroy()
            binding.viewerContainer.removeAllViews()
        }
        viewer = newViewer
        binding.viewerContainer.addView(newViewer.getView())

        if (newViewer is R2LPagerViewer && !binding.readerNav.pageSeekbar.isVertical) {
            binding.readerNav.leftChapter.tooltipText = getString(R.string.next_chapter)
            binding.readerNav.rightChapter.tooltipText = getString(R.string.previous_chapter)
        } else {
            binding.readerNav.leftChapter.tooltipText = getString(R.string.previous_chapter)
            binding.readerNav.rightChapter.tooltipText = getString(R.string.next_chapter)
        }

        if (newViewer is PagerViewer) {
            newViewer.config.hingeGapSize = hingeGapSize
            if (preferences.pageLayout().get() == PageLayout.AUTOMATIC.value) {
                setDoublePageMode(newViewer)
            }
            lastShiftDoubleState?.let { newViewer.config.shiftDoublePage = it }
        }

        binding.navigationOverlay.isLTR = viewer !is R2LPagerViewer
        binding.viewerContainer.setBackgroundColor(
            if (viewer is WebtoonViewer) {
                Color.BLACK
            } else {
                getResourceColor(R.attr.background)
            },
        )

        supportActionBar?.title = manga.title

        binding.readerNav.pageSeekbar.isRTL =
            if (binding.readerNav.pageSeekbar.isVertical) {
                val params =
                    binding.navLayout.layoutParams as CoordinatorLayout.LayoutParams
                params.anchorGravity == Gravity.TOP or Gravity.END
            } else {
                newViewer is R2LPagerViewer
            }
        reapplyVerticalSeekbarLayout()

        binding.pleaseWait.isVisible = true
        binding.pleaseWait.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in_long))
        invalidateOptionsMenu()
        updateCropBordersShortcut()
        updateBottomShortcuts()
        val viewerMode =
            ReadingModeType.fromPreference(
                viewModel.state.value.manga
                    ?.readingModeType ?: 0,
            )
        binding.chaptersSheet.readingMode.setIconResource(viewerMode.iconRes)
        startPostponedEnterTransition()
    }

    override fun onPause() {
        viewModel.saveCurrentChapterReadingProgress()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        viewModel.setReadStartTime()
    }

    override fun onMultiWindowModeChanged(
        isInMultiWindowMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        config?.setFullscreen()
        (viewer as? PagerViewer)?.refreshCutoutInsets()
        if (isInMultiWindowMode) {
            wic.show(systemBars())
        } else if (!menuVisible && preferences.fullscreen().get()) {
            wic.hide(systemBars())
            wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
        binding.root.requestApplyInsets()
    }

    fun reloadChapters(
        doublePages: Boolean,
        force: Boolean = false,
    ) {
        val pViewer = viewer as? PagerViewer ?: return
        pViewer.updateShifting()
        if (!force && pViewer.config.autoDoublePages) {
            setDoublePageMode(pViewer)
        } else {
            pViewer.config.doublePages = doublePages
            if (pViewer.config.autoDoublePages) {
                pViewer.config.splitPages = preferences.automaticSplitsPage().get() && !pViewer.config.doublePages
            }
        }
        if (doublePages) {
            // If we're moving from single to double, we want the current page to be the first page
            val currentIndex =
                binding.readerNav.pageSeekbar.value
                    .roundToInt()
            viewModel.getCurrentChapter()?.requestedPage = currentIndex
            pViewer.hasMoved = false
            pViewer.config.shiftDoublePage = shouldShiftDoublePages(currentIndex)
        }
        viewModel.state.value.viewerChapters?.let {
            pViewer.setChaptersDoubleShift(it)
        }
        invalidateOptionsMenu()
    }

    private fun shouldShiftDoublePages(currentIndex: Int): Boolean {
        val currentChapter = viewModel.getCurrentChapter()
        return (
            currentIndex +
                (currentChapter?.pages?.take(currentIndex)?.count { it.alonePage } ?: 0)
        ) % 2 != 0
    }

    /**
     * Called from the view model whenever a new [viewerChapters] have been set. It delegates the
     * method to the current viewer, but also set the subtitle on the binding.toolbar.
     */
    fun setChapters(viewerChapters: ViewerChapters) {
        binding.pleaseWait.clearAnimation()
        binding.pleaseWait.isVisible = false
        if (indexChapterToShift != null && indexPageToShift != null) {
            viewerChapters.currChapter.pages?.find { it.index == indexPageToShift && it.chapter.chapter.id == indexChapterToShift }?.let {
                (viewer as? PagerViewer)?.updateShifting(it)
            }
            indexChapterToShift = null
            indexPageToShift = null
        }
        val currentChapterPageCount = viewerChapters.currChapter.pages?.size ?: 1
        binding.readerNav.root.visibility =
            when {
                currentChapterPageCount == 1 -> View.GONE
                binding.chaptersSheet.root.sheetBehavior
                    .isCollapsed() -> View.VISIBLE
                else -> View.INVISIBLE
            }
        if (lastShiftDoubleState == null) {
            manuallyShiftedPages = false
        }
        lastShiftDoubleState = null
        viewer?.setChapters(viewerChapters)
        intentPageNumber?.let { moveToPageIndex(it) }
        intentPageNumber = null
        val chapter = viewerChapters.currChapter.chapter
        binding.toolbar.subtitle =
            chapter.preferredChapterName(this, viewModel.manga!!, preferences)

        listOfNotNull(getTitleTextView(), getSubtitleTextView()).forEach { textView ->
            textView.ellipsize = TextUtils.TruncateAt.MARQUEE
            textView.marqueeRepeatLimit = -1
            textView.isSingleLine = true
            textView.isFocusable = true
            textView.isFocusableInTouchMode = true
            textView.isHorizontalFadingEdgeEnabled = true
            textView.setFadingEdgeLength(16.dpToPx)
            textView.setHorizontallyScrolling(true)
        }

        if (viewerChapters.nextChapter == null && viewerChapters.prevChapter == null) {
            binding.readerNav.startCell.isVisible = false
            binding.readerNav.endCell.isVisible = false
        } else if (viewer is R2LPagerViewer && !binding.readerNav.pageSeekbar.isVertical) {
            binding.readerNav.leftChapter.alpha = if (viewerChapters.nextChapter != null) 1f else 0.5f
            binding.readerNav.rightChapter.alpha = if (viewerChapters.prevChapter != null) 1f else 0.5f
        } else {
            binding.readerNav.rightChapter.alpha = if (viewerChapters.nextChapter != null) 1f else 0.5f
            binding.readerNav.leftChapter.alpha = if (viewerChapters.prevChapter != null) 1f else 0.5f
        }
        if (didTransitionFromChapter) {
            MainActivity.chapterIdToExitTo = viewerChapters.currChapter.chapter.id ?: 0L
        }
    }

    private fun getTitleTextView(): TextView? = getTextViewsWithText(binding.toolbar.title)

    private fun getSubtitleTextView(): TextView? = getTextViewsWithText(binding.toolbar.subtitle)

    private fun getTextViewsWithText(text: CharSequence?): TextView? {
        if (text.isNullOrBlank()) return null
        val viewTopComparator = Comparator<View> { view1, view2 -> view1.top - view2.top }
        val textViews =
            binding.toolbar.children
                .filterIsInstance<TextView>()
                .filter { TextUtils.equals(it.text, text) }
                .toList()
        return if (textViews.isEmpty()) null else Collections.max(textViews, viewTopComparator)
    }

    private fun delayTitleScroll() {
        val list = listOfNotNull(getTitleTextView(), getSubtitleTextView())
        if (list.isNotEmpty()) {
            scope.launchUI {
                delay(1.seconds)
                if (menuVisible) {
                    list.forEach { it.isSelected = true }
                }
            }
        }
    }

    private fun stopTitleScroll() = listOfNotNull(getTitleTextView(), getSubtitleTextView()).forEach { it.isSelected = false }

    /**
     * Called from the view model if the initial load couldn't load the pages of the chapter. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialChapterError(error: Throwable) {
        Timber.e(error)
        finish()
        toast(error.message)
    }

    /**
     * Called from the view model whenever it's loading the next or previous chapter. It shows or
     * dismisses a non-cancellable dialog to prevent user interaction according to the value of
     * [show]. This is only used when the next/previous buttons on the binding.toolbar are clicked; the
     * other cases are handled with chapter transitions on the viewers and chapter preloading.
     */
    private fun setProgressDialog(show: Boolean) {
        if (!show) {
            binding.readerNav.startCell.isVisible = true
            binding.readerNav.endCell.isVisible = true
            binding.readerNav.leftChapter.isVisible = true
            binding.readerNav.rightChapter.isVisible = true

            binding.readerNav.leftProgress.isVisible = false
            binding.readerNav.rightProgress.isVisible = false
            binding.chaptersSheet.root.resetChapter()
        }
        if (show) {
            isLoading = true
        } else {
            scope.launchIO {
                delay(100.milliseconds)
                isLoading = false
            }
        }
    }

    /**
     * Moves the viewer to the given page [index]. It does nothing if the viewer is null or the
     * page is not found.
     */
    private fun moveToPageIndex(
        index: Int,
        animated: Boolean = true,
        chapterChange: Boolean = false,
    ) {
        val viewer = viewer ?: return
        val currentChapter = viewModel.getCurrentChapter() ?: return
        val page = currentChapter.pages?.getOrNull(index) ?: return
        viewer.moveToPage(page, animated)
        if (chapterChange) {
            isScrollingThroughPagesOrChapters = false
        }
    }

    private fun refreshChapters() {
        binding.chaptersSheet.chaptersBottomSheet.refreshList()
    }

    /**
     * Briefly flashes the screen black/white to reduce ghosting on e-ink displays.
     */
    private fun flashPage() {
        if (!preferences.flashOnPageChange().get()) return

        val interval = preferences.flashPageInterval().get().coerceAtLeast(1)
        val shouldFlash = flashTimesCalled % interval == 0
        flashTimesCalled++
        if (!shouldFlash) return

        val durationHalf = preferences.flashDurationMillis().get() / 2L
        val color = preferences.flashColor().get()

        flashJob?.cancel()
        flashJob =
            scope.launch {
                binding.flashOverlay.isVisible = true
                binding.flashOverlay.setBackgroundColor(
                    if (color == FlashColor.BLACK) Color.BLACK else Color.WHITE,
                )
                delay(durationHalf)
                if (color == FlashColor.WHITE_BLACK) {
                    binding.flashOverlay.setBackgroundColor(Color.BLACK)
                }
                delay(durationHalf)
                binding.flashOverlay.isVisible = false
            }
    }

    /**
     * Called from the viewer whenever a [page] is marked as active. It updates the values of the
     * bottom menu and delegates the change to the view model.
     */
    @SuppressLint("SetTextI18n")
    fun onPageSelected(
        page: ReaderPage,
        hasExtraPage: Boolean,
    ) {
        viewModel.onPageSelected(page, hasExtraPage)
        flashPage()
        val pages = page.chapter.pages ?: return

        val currentPage =
            if (hasExtraPage) {
                val invertDoublePage = (viewer as? PagerViewer)?.config?.invertDoublePages ?: false
                if ((
                        !binding.readerNav.pageSeekbar.isRTL ||
                            binding.readerNav.pageSeekbar.isVertical
                    ).xor(invertDoublePage)
                ) {
                    "${page.number}-${page.number + 1}"
                } else {
                    "${page.number + 1}-${page.number}"
                }
            } else {
                "${page.number}${if (page.firstHalf == false) "*" else ""}"
            }

        val totalPages = pages.size.toString()
        if (hingeGapSize > 0) {
            binding.pageNumber.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                marginStart = (binding.root.width) / 2 + hingeGapSize
            }
        }
        binding.pageNumber.text = if (resources.isLTR) "$currentPage/$totalPages" else "$totalPages/$currentPage"
        if (viewer is R2LPagerViewer && !binding.readerNav.pageSeekbar.isVertical) {
            binding.readerNav.rightPageText.text = currentPage
            binding.readerNav.leftPageText.text = totalPages
        } else {
            binding.readerNav.leftPageText.text = currentPage
            binding.readerNav.rightPageText.text = totalPages
        }
        if (binding.chaptersSheet.chaptersBottomSheet.selectedChapterId != page.chapter.chapter.id) {
            binding.chaptersSheet.chaptersBottomSheet.refreshList()
        }
        // Set seekbar progress
        binding.readerNav.pageSeekbar.valueTo = max(pages.lastIndex.toFloat(), 1f)
        val progress = page.index + if (hasExtraPage) 1 else 0
        // For a double page, show the last 2 pages as if it was the final part of the seekbar
        binding.readerNav.pageSeekbar.value = (if (progress == pages.lastIndex) progress else page.index).toFloat()
    }

    /**
     * Called from the viewer whenever a [page] is long clicked. A bottom sheet with a list of
     * actions to perform is shown.
     */
    fun onPageLongTap(
        page: ReaderPage,
        extraPage: ReaderPage? = null,
    ) {
        val items =
            if (extraPage != null) {
                listOf(
                    MaterialMenuSheet.MenuSheetItem(
                        3,
                        R.drawable.ic_outline_share_24dp,
                        R.string.share_second_page,
                    ),
                    MaterialMenuSheet.MenuSheetItem(
                        4,
                        R.drawable.ic_outline_save_24dp,
                        R.string.save_second_page,
                    ),
                    MaterialMenuSheet.MenuSheetItem(
                        5,
                        R.drawable.ic_outline_photo_24dp,
                        R.string.set_second_page_as_cover,
                    ),
                    MaterialMenuSheet.MenuSheetItem(
                        0,
                        R.drawable.ic_share_24dp,
                        R.string.share_first_page,
                    ),
                    MaterialMenuSheet.MenuSheetItem(
                        1,
                        R.drawable.ic_save_24dp,
                        R.string.save_first_page,
                    ),
                    MaterialMenuSheet.MenuSheetItem(
                        2,
                        R.drawable.ic_photo_24dp,
                        R.string.set_first_page_as_cover,
                    ),
                    MaterialMenuSheet.MenuSheetItem(
                        6,
                        R.drawable.ic_share_all_outline_24dp,
                        R.string.share_combined_pages,
                    ),
                    MaterialMenuSheet.MenuSheetItem(
                        7,
                        R.drawable.ic_save_all_outline_24dp,
                        R.string.save_combined_pages,
                    ),
                )
            } else {
                listOf(
                    MaterialMenuSheet.MenuSheetItem(
                        0,
                        R.drawable.ic_share_24dp,
                        R.string.share,
                    ),
                    MaterialMenuSheet.MenuSheetItem(
                        1,
                        R.drawable.ic_save_24dp,
                        R.string.save,
                    ),
                    MaterialMenuSheet.MenuSheetItem(
                        2,
                        R.drawable.ic_photo_24dp,
                        R.string.set_as_cover,
                    ),
                )
            }
        MaterialMenuSheet(this, items) { _, item ->
            when (item) {
                0 -> shareImage(page)
                1 -> saveImage(page)
                2 -> showSetCoverPrompt(page)
                3 -> extraPage?.let { shareImage(it) }
                4 -> extraPage?.let { saveImage(it) }
                5 -> extraPage?.let { showSetCoverPrompt(it) }
                6, 7 ->
                    extraPage?.let { secondPage ->
                        (viewer as? PagerViewer)?.let { viewer ->
                            val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)
                            val bg = ThemeUtil.readerBackgroundColor(viewer.config.readerTheme)
                            if (item == 6) {
                                viewModel.shareImages(page, secondPage, isLTR, bg)
                            } else {
                                viewModel.saveImages(page, secondPage, isLTR, bg)
                            }
                        }
                    }
            }
            true
        }.show()
        if (binding.chaptersSheet.root.sheetBehavior
                .isExpanded()
        ) {
            binding.chaptersSheet.root.sheetBehavior
                ?.collapse()
        }
    }

    /**
     * Called from the viewer when the given [chapter] should be preloaded. It should be called when
     * the viewer is reaching the beginning or end of a chapter or the transition page is active.
     */
    fun requestPreloadChapter(chapter: ReaderChapter) {
        lifecycleScope.launch {
            viewModel.preloadChapter(chapter)
        }
    }

    /**
     * Called from the viewer to toggle the visibility of the menu. It's implemented on the
     * viewer because each one implements its own touch and key events.
     */
    fun toggleMenu() {
        if (!binding.chaptersSheet.chaptersBottomSheet.sheetBehavior.isCollapsed()) {
            binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.collapse()
            return
        }
        setMenuVisibility(!menuVisible)
    }

    /**
     * Called from the viewer to show the menu.
     */
    fun showMenu() {
        if (!menuVisible) {
            setMenuVisibility(true)
        }
    }

    /**
     * Called from the page sheet. It delegates the call to the view model to do some IO, which
     * will call [onShareImageResult] with the path the image was saved on when it's ready.
     */
    private fun shareImage(page: ReaderPage) {
        viewModel.shareImage(page)
    }

    private fun showSetCoverPrompt(page: ReaderPage) {
        if (page.status != Page.State.READY) return

        materialAlertDialog()
            .setMessage(R.string.use_image_as_cover)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                setAsCover(page)
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Called from the view model when a page is ready to be shared. It shows Android's default
     * sharing tool.
     */
    private fun onShareImageResult(
        file: File,
        page: ReaderPage,
        secondPage: ReaderPage? = null,
    ) {
        val manga = viewModel.manga ?: return
        val chapter = page.chapter.chapter

        val decimalFormat =
            DecimalFormat("#.###", DecimalFormatSymbols().apply { decimalSeparator = '.' })

        val pageNumber =
            if (secondPage != null) {
                getString(
                    R.string.pages_,
                    if (resources.isLTR) "${page.number}-${page.number + 1}" else "${page.number + 1}-${page.number}",
                )
            } else {
                getString(R.string.page_, page.number)
            }
        val text = "${manga.title}: ${if (chapter.isRecognizedNumber) {
            getString(R.string.chapter_, decimalFormat.format(chapter.chapter_number))
        } else {
            chapter.preferredChapterName(this, manga, preferences)
        }
        }, $pageNumber"

        val stream = file.getUriCompat(this)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_STREAM, stream)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                clipData = ClipData.newRawUri(null, stream)
                type = "image/*"
            }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        val chapterUrl = viewModel.getChapterUrl() ?: return
        outContent.webUri = chapterUrl.toUri()
    }

    /**
     * Called from the page sheet. It delegates saving the image of the given [page] on external
     * storage to the viewModel.
     */
    private fun saveImage(page: ReaderPage) {
        viewModel.saveImage(page)
    }

    /**
     * Called from the view model when a page is saved or fails. It shows a message or logs the
     * event depending on the [result].
     */
    private fun onSaveImageResult(result: ReaderViewModel.SaveImageResult) {
        when (result) {
            is ReaderViewModel.SaveImageResult.Success -> {
                toast(R.string.picture_saved)
            }
            is ReaderViewModel.SaveImageResult.Error -> {
                Timber.e(result.error)
            }
        }
    }

    /**
     * Called from the page sheet. It delegates setting the image of the given [page] as the
     * cover to the viewModel.
     */
    private fun setAsCover(page: ReaderPage) {
        viewModel.setAsCover(page)
    }

    /**
     * Called from the view model when a page is set as cover or fails. It shows a different message
     * depending on the [result].
     */
    private fun onSetAsCoverResult(result: ReaderViewModel.SetAsCoverResult) {
        toast(
            when (result) {
                Success -> R.string.cover_updated
                AddToLibraryFirst -> R.string.must_be_in_library_to_edit
                Error -> R.string.failed_to_update_cover
            },
        )
    }

    private fun showTrackingError(errors: List<Pair<TrackService, String?>>) {
        if (errors.isEmpty()) return
        snackbar?.dismiss()
        val errorText =
            if (errors.size > 1) {
                getString(R.string.failed_to_update_, errors.joinToString(", ") { getString(it.first.nameRes()) })
            } else {
                val (service, errorMessage) = errors.first()
                buildSpannedString {
                    if (errorMessage != null) {
                        val icon =
                            contextCompatDrawable(service.getLogo())
                                ?.mutate()
                                ?.run {
                                    (this as? BitmapDrawable)?.run {
                                        val newBitmap =
                                            createBitmap(
                                                intrinsicWidth,
                                                intrinsicHeight,
                                                bitmap.config!!,
                                            )
                                        val canvas = Canvas(newBitmap)
                                        val bgColor = ColorUtils.setAlphaComponent(service.getLogoColor(), 255)
                                        canvas.drawColor(bgColor)
                                        canvas.drawBitmap(bitmap, 0f, 0f, null)
                                        newBitmap.toDrawable(resources)
                                    } ?: this
                                }?.apply {
                                    val size =
                                        resources.getDimension(com.google.android.material.R.dimen.design_snackbar_text_size)
                                    val dRatio = intrinsicWidth / intrinsicHeight.toFloat()
                                    setBounds(0, 0, (size * dRatio).roundToInt(), size.roundToInt())
                                } ?: return
                        val alignment =
                            if (Build.VERSION.SDK_INT >=
                                Build.VERSION_CODES.Q
                            ) {
                                DynamicDrawableSpan.ALIGN_CENTER
                            } else {
                                DynamicDrawableSpan.ALIGN_BASELINE
                            }
                        inSpans(ImageSpan(icon, alignment)) { append("image") }
                        append(" - $errorMessage")
                    }
                }
            }
        snackbar = binding.readerLayout.snack(errorText, 5000)
    }

    private fun onVisibilityChange(visible: Boolean) {
        if (visible && !menuTemporarilyVisible && !menuVisible && !binding.appBar.isVisible) {
            menuTemporarilyVisible = true
            coroutine =
                scope.launchUI {
                    delay(2.seconds)
                    if (window.decorView.rootWindowInsetsCompat?.isVisible(statusBars()) == true) {
                        menuTemporarilyVisible = false
                        setMenuVisibility(false)
                    }
                }
            val fullscreen = preferences.fullscreen().get()
            if (sheetManageNavColor) {
                binding.navBar.backgroundColor =
                    if (!fullscreen) {
                        Color.TRANSPARENT
                    } else {
                        ColorUtils.setAlphaComponent(
                            getResourceColor(R.attr.colorSurface),
                            if (binding.root.rootWindowInsetsCompat?.hasSideNavBar() == true) {
                                255
                            } else {
                                179
                            },
                        )
                    }
            }
            binding.appBar.isVisible = true
            val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_top)
            toolbarAnimation.doOnStart {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            }
            binding.appBar.startAnimation(toolbarAnimation)
        } else if (!visible && (menuTemporarilyVisible || menuVisible)) {
            if (menuTemporarilyVisible && !menuVisible) {
                setMenuVisibility(false)
            }
            coroutine?.cancel()
        }
    }

    private fun setCutoutMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            binding.root.requestApplyInsets()
        }
    }

    private fun setDoublePageMode(viewer: PagerViewer) {
        val currentOrientation = resources.configuration.orientation
        viewer.config.doublePages = (currentOrientation == Configuration.ORIENTATION_LANDSCAPE)
        if (viewer.config.autoDoublePages) {
            viewer.config.splitPages = preferences.automaticSplitsPage().get() && !viewer.config.doublePages
        }
    }

    private fun handleIntentAction(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        if (!viewModel.canLoadUrl(uri)) {
            openInBrowser(intent.data!!.toString(), true)
            finishAfterTransition()
            return true
        }
        setMenuVisibility(visible = false, animate = true)
        scope.launch(Dispatchers.IO) {
            try {
                intentPageNumber = viewModel.intentPageNumber(uri)
                viewModel.loadChapterURL(uri)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setInitialChapterError(e)
                }
            }
        }
        return true
    }

    private fun openMangaInBrowser() {
        val source = viewModel.getSource() ?: return
        val chapterUrl = viewModel.getChapterUrl() ?: return

        val intent =
            WebViewActivity.newIntent(
                applicationContext,
                chapterUrl,
                source.id,
                viewModel.manga!!.title,
            )
        startActivity(intent)
    }

    /**
     * Forces the user preferred [orientation] on the activity.
     */
    fun setOrientation(orientation: Int) {
        val newOrientation = OrientationType.fromPreference(orientation)
        if (newOrientation.flag != requestedOrientation) {
            requestedOrientation = newOrientation.flag
        }
    }

    /**
     * Class that handles the user preferences of the reader.
     */
    private inner class ReaderConfig {
        var showNewChapter = false

        /**
         * Initializes the reader subscriptions.
         */
        init {
            preferences
                .defaultOrientationType()
                .asFlow()
                .drop(1)
                .onEach {
                    delay(250.milliseconds)
                    setOrientation(viewModel.getMangaOrientationType())
                }.launchIn(scope)

            preferences.showPageNumber().asImmediateFlowIn(scope) { setPageNumberVisibility(it) }

            preferences
                .landscapeCutoutBehavior()
                .asFlow()
                .drop(1)
                .onEach { setCutoutMode() }
                .launchIn(scope)

            preferences.fullscreen().asImmediateFlowIn(scope) { setFullscreen() }

            preferences.keepScreenOn().asImmediateFlowIn(scope) { setKeepScreenOn(it) }

            preferences.customBrightness().asImmediateFlowIn(scope) { setCustomBrightness(it) }

            preferences.colorFilter().asImmediateFlowIn(scope) { setColorFilter(it) }

            preferences.colorFilterMode().asImmediateFlowIn(scope) {
                setColorFilter(preferences.colorFilter().get())
            }

            merge(preferences.grayscale().asFlow(), preferences.invertedColors().asFlow())
                .onEach { setLayerPaint(preferences.grayscale().get(), preferences.invertedColors().get()) }
                .launchIn(lifecycleScope)

            preferences.alwaysShowChapterTransition().asImmediateFlowIn(scope) {
                showNewChapter = it
            }

            preferences.pageLayout().asImmediateFlowIn(scope) { setBottomNavButtons(it) }

            preferences
                .automaticSplitsPage()
                .asFlow()
                .drop(1)
                .onEach {
                    val isPaused = !this@ReaderActivity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    if (isPaused) {
                        (viewer as? PagerViewer)?.config?.let { config ->
                            reloadChapters(config.doublePages, true)
                        }
                    }
                }.launchIn(scope)

            preferences.readerBottomButtons().asImmediateFlowIn(scope) { updateBottomShortcuts() }
        }

        /**
         * Sets the visibility of the bottom page indicator according to [visible].
         */
        private fun setPageNumberVisibility(visible: Boolean) {
            binding.pageNumber.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        }

        /**
         * Sets the fullscreen reading mode (immersive)
         */
        fun setFullscreen() {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            binding.root.rootWindowInsetsCompat?.let { setNavColor(it) }
            binding.root.requestApplyInsets()
        }

        /**
         * Sets the keep screen on mode according to [enabled].
         */
        private fun setKeepScreenOn(enabled: Boolean) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        /**
         * Sets the custom brightness overlay according to [enabled].
         */
        private fun setCustomBrightness(enabled: Boolean) {
            if (enabled) {
                preferences
                    .customBrightnessValue()
                    .asFlow()
                    .sample(100.milliseconds)
                    .onEach { setCustomBrightnessValue(it) }
                    .launchIn(scope)
            } else {
                setCustomBrightnessValue(0)
            }
        }

        /**
         * Sets the color filter overlay according to [enabled].
         */
        private fun setColorFilter(enabled: Boolean) {
            if (enabled) {
                preferences
                    .colorFilterValue()
                    .asFlow()
                    .sample(100.milliseconds)
                    .onEach { setColorFilterValue(it) }
                    .launchIn(scope)
            } else {
                binding.colorOverlay.isVisible = false
            }
        }

        private fun getCombinedPaint(
            grayscale: Boolean,
            invertedColors: Boolean,
        ): Paint =
            Paint().apply {
                colorFilter =
                    ColorMatrixColorFilter(
                        ColorMatrix().apply {
                            if (grayscale) {
                                setSaturation(0f)
                            }
                            if (invertedColors) {
                                postConcat(
                                    ColorMatrix(
                                        floatArrayOf(
                                            -1f,
                                            0f,
                                            0f,
                                            0f,
                                            255f,
                                            0f,
                                            -1f,
                                            0f,
                                            0f,
                                            255f,
                                            0f,
                                            0f,
                                            -1f,
                                            0f,
                                            255f,
                                            0f,
                                            0f,
                                            0f,
                                            1f,
                                            0f,
                                        ),
                                    ),
                                )
                            }
                        },
                    )
            }

        /**
         * Sets the brightness of the screen. Range is [-75, 100].
         * From -75 to -1 a semi-transparent black view is overlaid with the minimum brightness.
         * From 1 to 100 it sets that value as brightness.
         * 0 sets system brightness and hides the overlay.
         */
        private fun setCustomBrightnessValue(value: Int) {
            // Calculate and set reader brightness.
            val readerBrightness =
                when {
                    value > 0 -> {
                        value / 100f
                    }
                    value < 0 -> {
                        0.01f
                    }
                    else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }

            window.attributes = window.attributes.apply { screenBrightness = readerBrightness }

            // Set black overlay visibility.
            if (value < 0) {
                binding.brightnessOverlay.isVisible = true
                val alpha = (abs(value) * 2.56).toInt()
                binding.brightnessOverlay.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
            } else {
                binding.brightnessOverlay.isVisible = false
            }
        }

        /**
         * Sets the color filter [value].
         */
        private fun setColorFilterValue(value: Int) {
            binding.colorOverlay.isVisible = true
            binding.colorOverlay.setFilterColor(value, preferences.colorFilterMode().get())
        }

        private fun setLayerPaint(
            grayscale: Boolean,
            invertedColors: Boolean,
        ) {
            val paint = if (grayscale || invertedColors) getCombinedPaint(grayscale, invertedColors) else null
            binding.viewerContainer.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        }
    }
}

private fun isConfirmKeyCode(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_BUTTON_A ||
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
        keyCode == KeyEvent.KEYCODE_SPACE
