package eu.kanade.tachiyomi.ui.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.app.assist.AssistContent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.transition.addListener
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.data.preference.toggle
import eu.kanade.tachiyomi.databinding.ReaderActivityBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet
import eu.kanade.tachiyomi.ui.base.activity.BaseRxActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.SearchActivity
import eu.kanade.tachiyomi.ui.reader.ReaderPresenter.SetAsCoverResult.AddToLibraryFirst
import eu.kanade.tachiyomi.ui.reader.ReaderPresenter.SetAsCoverResult.Error
import eu.kanade.tachiyomi.ui.reader.ReaderPresenter.SetAsCoverResult.Success
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
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
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getBottomGestureInsets
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.hasSideNavBar
import eu.kanade.tachiyomi.util.system.ignoredSystemInsets
import eu.kanade.tachiyomi.util.system.isBottomTappable
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.spToPx
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.compatToolTipText
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsetsCompat
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nucleus.factory.RequiresPresenter
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Activity containing the reader of Tachiyomi. This activity is mostly a container of the
 * viewers, to which calls from the presenter or UI events are delegated.
 */
@RequiresPresenter(ReaderPresenter::class)
class ReaderActivity : BaseRxActivity<ReaderPresenter>() {

    lateinit var binding: ReaderActivityBinding

    /**
     * Preferences helper.
     */
    private val preferences by injectLazy<PreferencesHelper>()

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
    private var menuStickyVisible = false

    private var coroutine: Job? = null

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

    private val wic by lazy { WindowInsetsControllerCompat(window, binding.root) }
    var lastVis = false

    private var snackbar: Snackbar? = null

    private var intentPageNumber: Int? = null

    var isLoading = false

    private var lastShiftDoubleState: Boolean? = null
    private var indexPageToShift: Int? = null
    private var indexChapterToShift: Long? = null

    private var lastCropRes = 0

    val isSplitScreen: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode

    var didTransistionFromChapter = false
    var visibleChapterRange = longArrayOf()

    companion object {

        const val SHIFT_DOUBLE_PAGES = "shiftingDoublePages"
        const val SHIFTED_PAGE_INDEX = "shiftedPageIndex"
        const val SHIFTED_CHAP_INDEX = "shiftedChapterIndex"

        const val TRANSITION_NAME = "${BuildConfig.APPLICATION_ID}.TRANSITION_NAME"
        const val VISIBLE_CHAPTERS = "${BuildConfig.APPLICATION_ID}.VISIBLE_CHAPTERS"

        fun newIntent(context: Context, manga: Manga, chapter: Chapter): Intent {
            MainActivity.chapterIdToExitTo = 0L
            val intent = Intent(context, ReaderActivity::class.java)
            intent.putExtra("manga", manga.id)
            intent.putExtra("chapter", chapter.id)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }

        fun newIntentWithTransitionOptions(activity: Activity, manga: Manga, chapter: Chapter, sharedElement: View): Pair<Intent, Bundle?> {
            MainActivity.chapterIdToExitTo = 0L
            val intent = newIntent(activity, manga, chapter)
            intent.putExtra(TRANSITION_NAME, sharedElement.transitionName)
            val activityOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(
                activity, sharedElement, sharedElement.transitionName,
            )
            return intent to activityOptions.toBundle()
        }
    }

    /**
     * Called when the activity is created. Initializes the presenter and configuration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Setup shared element transitions
        if (intent.extras?.getString(TRANSITION_NAME) != null) {
            window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
            findViewById<View>(android.R.id.content)?.let { contentView ->
                MainActivity.chapterIdToExitTo = 0L
                contentView.transitionName = intent.extras?.getString(TRANSITION_NAME)
                visibleChapterRange = intent.extras?.getLongArray(VISIBLE_CHAPTERS) ?: longArrayOf()
                didTransistionFromChapter = contentView.transitionName.contains("details chapter")
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
        val a = obtainStyledAttributes(intArrayOf(android.R.attr.windowLightStatusBar))
        val lightStatusBar = a.getBoolean(0, false)
        a.recycle()
        setNotchCutoutMode()

        wic.isAppearanceLightStatusBars = lightStatusBar
        wic.isAppearanceLightNavigationBars = lightStatusBar

        binding.appBar.setBackgroundColor(contextCompatColor(R.color.surface_alpha))
        ViewCompat.setBackgroundTintList(
            binding.readerNav.root,
            ColorStateList.valueOf(contextCompatColor(R.color.surface_alpha)),
        )

        if (presenter.needsInit()) {
            fromUrl = handleIntentAction(intent)
            if (!fromUrl) {
                val manga = intent.extras!!.getLong("manga", -1)
                val chapter = intent.extras!!.getLong("chapter", -1)
                if (manga == -1L || chapter == -1L) {
                    finish()
                    return
                }
                presenter.init(manga, chapter)
            } else {
                binding.pleaseWait.isVisible = true
            }
        }

        if (savedInstanceState != null) {
            menuVisible = savedInstanceState.getBoolean(::menuVisible.name)
            lastShiftDoubleState = savedInstanceState.get(SHIFT_DOUBLE_PAGES) as? Boolean
            indexPageToShift = savedInstanceState.get(SHIFTED_PAGE_INDEX) as? Int
            indexChapterToShift = savedInstanceState.get(SHIFTED_CHAP_INDEX) as? Long
            binding.readerNav.root.isInvisible = !menuVisible
        } else {
            binding.readerNav.root.isInvisible = true
        }

        binding.chaptersSheet.chaptersBottomSheet.setup(this)
        config = ReaderConfig()
        initializeMenu()

        preferences.incognitoMode()
            .asImmediateFlowIn(lifecycleScope) {
                SecureActivityDelegate.setSecure(this)
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
            outState.putBoolean(SHIFT_DOUBLE_PAGES, config.shiftDoublePage)
            if (config.shiftDoublePage && config.doublePages) {
                pViewer.getShiftedPage()?.let {
                    outState.putInt(SHIFTED_PAGE_INDEX, it.index)
                    outState.putLong(SHIFTED_CHAP_INDEX, it.chapter.chapter.id ?: 0L)
                }
            }
        }
        if (!isChangingConfigurations) {
            presenter.onSaveInstanceStateNonConfigurationChange()
        }
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
        (viewer as? PagerViewer)?.config?.let { config ->
            val icon = ContextCompat.getDrawable(
                this,
                if ((!config.shiftDoublePage).xor(viewer is R2LPagerViewer)) R.drawable.ic_page_previous_outline_24dp else R.drawable.ic_page_next_outline_24dp,
            )
            splitItem?.icon = icon
            binding.chaptersSheet.shiftPageButton.setImageDrawable(icon)
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

    private fun canShowSplitAtBottom(): Boolean {
        return if (preferences.readerBottomButtons().isNotSet()) {
            isTablet()
        } else {
            ReaderBottomButton.ShiftDoublePage.isIn(preferences.readerBottomButtons().get())
        }
    }

    fun setBottomNavButtons(pageLayout: Int) {
        val isDoublePage = pageLayout == PageLayout.DOUBLE_PAGES.value ||
            (pageLayout == PageLayout.AUTOMATIC.value && (viewer as? PagerViewer)?.config?.doublePages ?: false)
        binding.chaptersSheet.doublePage.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                when {
                    isDoublePage -> R.drawable.ic_book_open_variant_24dp
                    (viewer as? PagerViewer)?.config?.splitPages == true -> R.drawable.ic_book_open_split_24dp
                    else -> R.drawable.ic_single_page_24dp
                },
            ),
        )
        with(binding.readerNav) {
            listOf(leftPageText, rightPageText).forEach {
                it.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    val isCurrent = (viewer is R2LPagerViewer).xor(it === leftPageText)
                    width = if (isDoublePage && isCurrent) 48.spToPx else 32.spToPx
                }
            }
        }
    }

    private fun updateOrientationShortcut(preference: Int) {
        val orientation = OrientationType.fromPreference(preference)
        binding.chaptersSheet.rotationSheetButton.setImageResource(orientation.iconRes)
    }

    private fun updateCropBordersShortcut() {
        val isPagerType = viewer is PagerViewer || (viewer as? WebtoonViewer)?.hasMargins == true
        val enabled = if (isPagerType) {
            preferences.cropBorders().get()
        } else {
            preferences.cropBordersWebtoon().get()
        }

        with(binding.chaptersSheet.cropBordersSheetButton) {
            val drawableRes = if (enabled) {
                R.drawable.anim_free_to_crop
            } else {
                R.drawable.anim_crop_to_free
            }
            if (lastCropRes != drawableRes) {
                val drawable = AnimatedVectorDrawableCompat.create(context, drawableRes)
                setImageDrawable(drawable)
                drawable?.start()
                lastCropRes = drawableRes
            }
            compatToolTipText =
                getString(
                    if (enabled) R.string.remove_crop
                    else R.string.crop_borders,
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
                ((viewer as? PagerViewer)?.config?.doublePages ?: false) && canShowSplitAtBottom()
            binding.toolbar.menu.findItem(R.id.action_shift_double_page)?.isVisible =
                ((viewer as? PagerViewer)?.config?.doublePages ?: false) && !canShowSplitAtBottom()
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
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun shiftDoublePages() {
        (viewer as? PagerViewer)?.config?.let { config ->
            config.shiftDoublePage = !config.shiftDoublePage
            presenter.viewerChapters?.let {
                (viewer as? PagerViewer)?.updateShifting()
                (viewer as? PagerViewer)?.setChaptersDoubleShift(it)
                invalidateOptionsMenu()
            }
        }
    }

    private fun popToMain() {
        presenter.onBackPressed()
        if (fromUrl) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finishAfterTransition()
        } else {
            finish()
        }
    }

    /**
     * Called when the user clicks the back key or the button on the binding.toolbar. The call is
     * delegated to the presenter.
     */
    override fun onBackPressed() {
        if (binding.chaptersSheet.chaptersBottomSheet.sheetBehavior.isExpanded()) {
            binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.collapse()
            return
        }
        presenter.onBackPressed()
        if (didTransistionFromChapter && visibleChapterRange.isNotEmpty() && MainActivity.chapterIdToExitTo !in visibleChapterRange) {
            finish()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Dispatches a key event. If the viewer doesn't handle it, call the default implementation.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = viewer?.handleKeyEvent(event) ?: false
        return handled || super.dispatchKeyEvent(event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_N -> {
                if (viewer is R2LPagerViewer) {
                    binding.readerNav.leftChapter.performClick()
                } else {
                    binding.readerNav.rightChapter.performClick()
                }
                return true
            }
            KeyEvent.KEYCODE_P -> {
                if (viewer !is R2LPagerViewer) {
                    binding.readerNav.leftChapter.performClick()
                } else {
                    binding.readerNav.rightChapter.performClick()
                }
                return true
            }
            KeyEvent.KEYCODE_L -> {
                binding.readerNav.leftChapter.performClick()
                return true
            }
            KeyEvent.KEYCODE_R -> {
                binding.readerNav.rightChapter.performClick()
                return true
            }
            KeyEvent.KEYCODE_E -> {
                viewer?.moveToNext()
                return true
            }
            KeyEvent.KEYCODE_Q -> {
                viewer?.moveToPrevious()
                return true
            }
            else -> return super.onKeyUp(keyCode, event)
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

    private fun buildContainerTransform(entering: Boolean): MaterialContainerTransform {
        return MaterialContainerTransform(this, entering).apply {
            duration = (
                resources?.getInteger(
                    if (entering) android.R.integer.config_longAnimTime
                    else android.R.integer.config_mediumAnimTime,
                ) ?: 500
                ).toLong()
            addTarget(android.R.id.content)
        }
    }

    /**
     * Initializes the reader menu. It sets up click listeners and the initial visibility.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initializeMenu() {
        // Set binding.toolbar
        setSupportActionBar(binding.toolbar)
        val primaryColor = ColorUtils.setAlphaComponent(
            getResourceColor(R.attr.colorSurface),
            200,
        )
        binding.appBar.setBackgroundColor(primaryColor)
        window.statusBarColor = Color.TRANSPARENT
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.navigationIcon?.setTint(getResourceColor(R.attr.actionBarTintColor))
        binding.toolbar.setNavigationOnClickListener {
            popToMain()
        }

        binding.toolbar.setOnClickListener {
            presenter.manga?.id?.let { id ->
                val intent = SearchActivity.openMangaIntent(this, id)
                startActivity(intent)
            }
        }

        with(binding.chaptersSheet) {
            with(doublePage) {
                compatToolTipText = getString(R.string.page_layout)
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
                    ) preferences.cropBorders() else preferences.cropBordersWebtoon()
                pref.toggle()
            }

            with(rotationSheetButton) {
                compatToolTipText = getString(R.string.rotation)

                setOnClickListener {
                    popupMenu(
                        items = OrientationType.values().map { it.flagValue to it.stringRes },
                        selectedItemId = presenter.manga?.orientationType
                            ?: preferences.defaultOrientationType().get(),
                    ) {
                        val newOrientation = OrientationType.fromPreference(itemId)

                        presenter.setMangaOrientationType(newOrientation.flagValue)

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
                    items = ReadingModeType.values().map { it.flagValue to it.stringRes },
                    selectedItemId = presenter.manga?.readingModeType,
                ) {
                    presenter.setMangaReadingMode(itemId)
                }
            }
        }

        listOf(preferences.cropBorders(), preferences.cropBordersWebtoon())
            .forEach { pref ->
                pref.asFlow()
                    .onEach { updateCropBordersShortcut() }
                    .launchIn(scope)
            }

        binding.chaptersSheet.shiftPageButton.setOnClickListener {
            shiftDoublePages()
        }
        binding.readerNav.leftChapter.setOnClickListener {
            if (isLoading) {
                return@setOnClickListener
            }
            val result = if (viewer is R2LPagerViewer) {
                presenter.loadNextChapter()
            } else {
                presenter.loadPreviousChapter()
            }
            if (result) {
                binding.readerNav.leftChapter.isInvisible = true
                binding.readerNav.leftProgress.isVisible = true
            } else {
                toast(
                    if (viewer is R2LPagerViewer) {
                        R.string.theres_no_next_chapter
                    } else {
                        R.string.theres_no_previous_chapter
                    },
                )
            }
        }

        binding.readerNav.rightChapter.setOnClickListener {
            if (isLoading) {
                return@setOnClickListener
            }
            val result = if (viewer !is R2LPagerViewer) {
                presenter.loadNextChapter()
            } else {
                presenter.loadPreviousChapter()
            }
            if (result) {
                binding.readerNav.rightChapter.isInvisible = true
                binding.readerNav.rightProgress.isVisible = true
            } else {
                toast(
                    if (viewer !is R2LPagerViewer) {
                        R.string.theres_no_next_chapter
                    } else {
                        R.string.theres_no_previous_chapter
                    },
                )
            }
        }

        binding.touchView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (binding.chaptersSheet.chaptersBottomSheet.sheetBehavior.isExpanded()) {
                    binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.collapse()
                }
            }
            false
        }
        val readerNavGestureDetector = ReaderNavGestureDetector(this)
        val gestureDetector = GestureDetectorCompat(this, readerNavGestureDetector)
        with(binding.readerNav) {
            binding.readerNav.pageSeekbar.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    readerNavGestureDetector.lockVertical = false
                    readerNavGestureDetector.hasScrollHorizontal = true
                }

                override fun onStopTrackingTouch(slider: Slider) {
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
                            // event.action = MotionEvent.ACTION_CANCEL
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
                val prevValue = (viewer as? PagerViewer)?.pager?.currentItem ?: -1
                moveToPageIndex(value.roundToInt())
                val newValue = (viewer as? PagerViewer)?.pager?.currentItem ?: -1
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
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
                    if (it.hasExtraPage(value.roundToInt(), presenter.getCurrentChapter())) {
                        val invertDoublePage = (viewer as? PagerViewer)?.config?.invertDoublePages ?: false
                        return@setLabelFormatter if (!binding.readerNav.pageSeekbar.isRTL.xor(invertDoublePage)) {
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
        binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.isHideable = !menuVisible
        if (!menuVisible) binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.hide()
        binding.chaptersSheet.root.sheetBehavior?.isGestureInsetBottomIgnored = true
        val peek = 50.dpToPx
        lastVis = window.decorView.rootWindowInsetsCompat?.isVisible(statusBars()) ?: false
        var firstPass = true
        binding.readerLayout.doOnApplyWindowInsetsCompat { _, insets, _ ->
            setNavColor(insets)
            val systemInsets = insets.ignoredSystemInsets
            val vis = insets.isVisible(statusBars())
            val fullscreen = preferences.fullscreen().get() && !isSplitScreen
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!firstPass && lastVis != vis && fullscreen) {
                    onVisibilityChange(vis)
                }
                firstPass = false
                lastVis = vis
            }
            wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
            if (!fullscreen && sheetManageNavColor) {
                window.navigationBarColor = getResourceColor(R.attr.colorSurface)
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
            binding.navLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = 12.dpToPx + systemInsets.left
                rightMargin = 12.dpToPx + systemInsets.right
            }
            binding.chaptersSheet.root.sheetBehavior?.peekHeight =
                peek + if (fullscreen) {
                insets.getBottomGestureInsets()
            } else {
                val rootInsets = binding.root.rootWindowInsetsCompat ?: insets
                max(
                    0,
                    (rootInsets.getBottomGestureInsets()) -
                        rootInsets.getInsetsIgnoringVisibility(systemBars()).bottom,
                )
            }
            binding.chaptersSheet.chapterRecycler.updatePaddingRelative(bottom = systemInsets.bottom)
            binding.viewerContainer.requestLayout()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            binding.readerLayout.setOnSystemUiVisibilityChangeListener {
                if (preferences.fullscreen().get()) {
                    onVisibilityChange((it and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0)
                }
            }
        }
    }

    fun setNavColor(insets: WindowInsetsCompat) {
        sheetManageNavColor = when {
            isSplitScreen -> {
                window.statusBarColor = getResourceColor(R.attr.colorPrimaryVariant)
                window.navigationBarColor = getResourceColor(R.attr.colorPrimaryVariant)
                false
            }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 -> {
                // basically if in landscape on a phone
                // For lollipop, draw opaque nav bar
                window.navigationBarColor = when {
                    insets.hasSideNavBar() -> Color.BLACK
                    isInNightMode() -> {
                        ColorUtils.setAlphaComponent(
                            getResourceColor(R.attr.colorPrimaryVariant),
                            179,
                        )
                    }
                    else -> Color.argb(179, 0, 0, 0)
                }
                !insets.hasSideNavBar()
            }
            insets.isBottomTappable() -> {
                window.navigationBarColor = Color.TRANSPARENT
                false
            }
            insets.hasSideNavBar() -> {
                window.navigationBarColor = getResourceColor(R.attr.colorSurface)
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
            val selectedId = when {
                config?.doublePages == true -> PageLayout.DOUBLE_PAGES
                config?.splitPages == true -> PageLayout.SPLIT_PAGES
                else -> PageLayout.SINGLE_PAGE
            }
            popupMenu(
                items = listOf(
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

    /**
     * Sets the visibility of the menu according to [visible] and with an optional parameter to
     * [animate] the views.
     */
    private fun setMenuVisibility(visible: Boolean, animate: Boolean = true) {
        val oldVisibility = menuVisible
        menuVisible = visible
        if (visible) coroutine?.cancel()
        binding.viewerContainer.requestLayout()
        if (visible) {
            snackbar?.dismiss()
            wic.show(systemBars())
            binding.readerMenu.isVisible = true

            if (binding.chaptersSheet.chaptersBottomSheet.sheetBehavior.isExpanded()) {
                binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.isHideable = false
            }
            if (!binding.chaptersSheet.chaptersBottomSheet.sheetBehavior.isExpanded() && sheetManageNavColor) {
                window.navigationBarColor = Color.TRANSPARENT
            }
            if (animate && oldVisibility != menuVisible) {
                if (!menuStickyVisible) {
                    val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_top)
                    toolbarAnimation.doOnStart {
                        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                    }
                    binding.appBar.startAnimation(toolbarAnimation)
                }
                binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.collapse()
            }
        } else {
            if (preferences.fullscreen().get()) {
                wic.hide(systemBars())
                wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
            }

            if (animate && binding.readerMenu.isVisible) {
                val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.exit_to_top)
                toolbarAnimation.doOnEnd { binding.readerMenu.isVisible = false }
                binding.appBar.startAnimation(toolbarAnimation)
                BottomSheetBehavior.from(binding.chaptersSheet.chaptersBottomSheet).isHideable = true
                binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.hide()
            } else if (!animate) {
                binding.readerMenu.isVisible = false
            }
        }
        menuStickyVisible = false
    }

    /**
     * Called from the presenter when a manga is ready. Used to instantiate the appropriate viewer
     * and the binding.toolbar title.
     */
    fun setManga(manga: Manga) {
        val prevViewer = viewer
        val noDefault = manga.viewer_flags == -1
        val mangaViewer = presenter.getMangaReadingMode()
        val newViewer = when (mangaViewer) {
            ReadingModeType.LEFT_TO_RIGHT.flagValue -> L2RPagerViewer(this)
            ReadingModeType.VERTICAL.flagValue -> VerticalPagerViewer(this)
            ReadingModeType.WEBTOON.flagValue -> WebtoonViewer(this)
            ReadingModeType.CONTINUOUS_VERTICAL.flagValue -> WebtoonViewer(this, hasMargins = true)
            else -> R2LPagerViewer(this)
        }

        if (noDefault && presenter.manga?.readingModeType!! > 0 &&
            presenter.manga?.readingModeType!! != preferences.defaultReadingMode()
        ) {
            snackbar = binding.readerLayout.snack(
                getString(
                    R.string.reading_,
                    getString(
                        when (mangaViewer) {
                            ReadingModeType.RIGHT_TO_LEFT.flagValue -> R.string.right_to_left_viewer
                            ReadingModeType.VERTICAL.flagValue -> R.string.vertical_viewer
                            ReadingModeType.WEBTOON.flagValue -> R.string.webtoon_style
                            else -> R.string.left_to_right_viewer
                        },
                    ).lowercase(Locale.getDefault()),
                ),
                4000,
            ) {
                setAction(R.string.use_default) {
                    presenter.setMangaReadingMode(0)
                }
            }
        }

        if (window.sharedElementEnterTransition is MaterialContainerTransform &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            // Wait until transition is complete to avoid crash on API 26
            window.sharedElementEnterTransition.addListener(
                onEnd = { setOrientation(presenter.getMangaOrientationType()) },
            )
        } else {
            setOrientation(presenter.getMangaOrientationType())
        }

        // Destroy previous viewer if there was one
        if (prevViewer != null) {
            prevViewer.destroy()
            binding.viewerContainer.removeAllViews()
        }
        viewer = newViewer
        binding.viewerContainer.addView(newViewer.getView())

        if (newViewer is R2LPagerViewer) {
            binding.readerNav.leftChapter.compatToolTipText = getString(R.string.next_chapter)
            binding.readerNav.rightChapter.compatToolTipText = getString(R.string.previous_chapter)
        } else {
            binding.readerNav.leftChapter.compatToolTipText = getString(R.string.previous_chapter)
            binding.readerNav.rightChapter.compatToolTipText = getString(R.string.next_chapter)
        }

        if (newViewer is PagerViewer) {
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

        binding.toolbar.title = manga.title

        binding.readerNav.pageSeekbar.isRTL = newViewer is R2LPagerViewer

        binding.pleaseWait.isVisible = true
        binding.pleaseWait.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in_long))
        invalidateOptionsMenu()
        updateCropBordersShortcut()
        updateBottomShortcuts()
        val viewerMode = ReadingModeType.fromPreference(presenter?.manga?.readingModeType ?: 0)
        binding.chaptersSheet.readingMode.setImageResource(viewerMode.iconRes)
        startPostponedEnterTransition()
    }

    override fun onPause() {
        presenter.saveProgress()
        super.onPause()
    }

    fun reloadChapters(doublePages: Boolean, force: Boolean = false) {
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
        val currentChapter = presenter.getCurrentChapter()
        if (doublePages) {
            // If we're moving from singe to double, we want the current page to be the first page
            pViewer.config.shiftDoublePage = (
                binding.readerNav.pageSeekbar.value.roundToInt() +
                    (
                        currentChapter?.pages?.take(binding.readerNav.pageSeekbar.value.roundToInt())
                            ?.count { it.fullPage == true || it.isolatedPage } ?: 0
                        )
                ) % 2 != 0
        }
        presenter.viewerChapters?.let {
            pViewer.setChaptersDoubleShift(it)
        }
        invalidateOptionsMenu()
    }

    /**
     * Called from the presenter whenever a new [viewerChapters] have been set. It delegates the
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
        } else if (lastShiftDoubleState != null) {
            val currentChapter = viewerChapters.currChapter
            (viewer as? PagerViewer)?.config?.shiftDoublePage = (
                currentChapter.requestedPage +
                    (
                        currentChapter.pages?.take(currentChapter.requestedPage)
                            ?.count { it.fullPage == true || it.isolatedPage } ?: 0
                        )
                ) % 2 != 0
        }
        val currentChapterPageCount = viewerChapters.currChapter.pages?.size ?: 1
        binding.readerNav.root.visibility = when {
            currentChapterPageCount == 1 -> View.GONE
            binding.chaptersSheet.root.sheetBehavior.isCollapsed() -> View.VISIBLE
            else -> View.INVISIBLE
        }
        lastShiftDoubleState = null
        viewer?.setChapters(viewerChapters)
        intentPageNumber?.let { moveToPageIndex(it) }
        intentPageNumber = null
        binding.toolbar.subtitle = viewerChapters.currChapter.chapter.name
        if (viewerChapters.nextChapter == null && viewerChapters.prevChapter == null) {
            binding.readerNav.leftChapter.isVisible = false
            binding.readerNav.rightChapter.isVisible = false
        } else if (viewer is R2LPagerViewer) {
            binding.readerNav.leftChapter.alpha = if (viewerChapters.nextChapter != null) 1f else 0.5f
            binding.readerNav.rightChapter.alpha = if (viewerChapters.prevChapter != null) 1f else 0.5f
        } else {
            binding.readerNav.rightChapter.alpha = if (viewerChapters.nextChapter != null) 1f else 0.5f
            binding.readerNav.leftChapter.alpha = if (viewerChapters.prevChapter != null) 1f else 0.5f
        }
        if (didTransistionFromChapter) {
            MainActivity.chapterIdToExitTo = viewerChapters.currChapter.chapter.id ?: 0L
        }
    }

    /**
     * Called from the presenter if the initial load couldn't load the pages of the chapter. In
     * this case the activity is closed and a toast is shown to the user.
     */
    fun setInitialChapterError(error: Throwable) {
        Timber.e(error)
        finish()
        toast(error.message)
    }

    /**
     * Called from the presenter whenever it's loading the next or previous chapter. It shows or
     * dismisses a non-cancellable dialog to prevent user interaction according to the value of
     * [show]. This is only used when the next/previous buttons on the binding.toolbar are clicked; the
     * other cases are handled with chapter transitions on the viewers and chapter preloading.
     */
    fun setProgressDialog(show: Boolean) {
        if (!show) {
            binding.readerNav.leftChapter.isVisible = true
            binding.readerNav.rightChapter.isVisible = true

            binding.readerNav.leftProgress.isVisible = false
            binding.readerNav.rightProgress.isVisible = false
            binding.chaptersSheet.root.resetChapter()
        }
        if (show) {
            isLoading = show
        } else {
            scope.launchIO {
                delay(100)
                isLoading = show
            }
        }
    }

    /**
     * Moves the viewer to the given page [index]. It does nothing if the viewer is null or the
     * page is not found.
     */
    fun moveToPageIndex(index: Int, animated: Boolean = true) {
        val viewer = viewer ?: return
        val currentChapter = presenter.getCurrentChapter() ?: return
        val page = currentChapter.pages?.getOrNull(index) ?: return
        viewer.moveToPage(page, animated)
    }

    fun refreshChapters() {
        binding.chaptersSheet.chaptersBottomSheet.refreshList()
    }

    /**
     * Called from the viewer whenever a [page] is marked as active. It updates the values of the
     * bottom menu and delegates the change to the presenter.
     */
    @SuppressLint("SetTextI18n")
    fun onPageSelected(page: ReaderPage, hasExtraPage: Boolean) {
        presenter.onPageSelected(page, hasExtraPage)
        val pages = page.chapter.pages ?: return

        val currentPage = if (hasExtraPage) {
            val invertDoublePage = (viewer as? PagerViewer)?.config?.invertDoublePages ?: false
            if (!binding.readerNav.pageSeekbar.isRTL.xor(invertDoublePage)) {
                "${page.number}-${page.number + 1}"
            } else {
                "${page.number + 1}-${page.number}"
            }
        } else {
            "${page.number}${if (page.firstHalf == false) "*" else ""}"
        }

        val totalPages = pages.size.toString()
        binding.pageNumber.text = if (resources.isLTR) "$currentPage/$totalPages" else "$totalPages/$currentPage"
        if (viewer is R2LPagerViewer) {
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
    fun onPageLongTap(page: ReaderPage, extraPage: ReaderPage? = null) {
        val items = if (extraPage != null) {
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
                6, 7 -> extraPage?.let { secondPage ->
                    (viewer as? PagerViewer)?.let { viewer ->
                        val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)
                        val bg =
                            if (viewer.config.readerTheme >= 2 || viewer.config.readerTheme == 0) {
                                Color.WHITE
                            } else {
                                Color.BLACK
                            }
                        if (item == 6) {
                            presenter.shareImages(page, secondPage, isLTR, bg)
                        } else {
                            presenter.saveImages(page, secondPage, isLTR, bg)
                        }
                    }
                }
            }
            true
        }.show()
        if (binding.chaptersSheet.root.sheetBehavior.isExpanded()) {
            binding.chaptersSheet.root.sheetBehavior?.collapse()
        }
    }

    /**
     * Called from the viewer when the given [chapter] should be preloaded. It should be called when
     * the viewer is reaching the beginning or end of a chapter or the transition page is active.
     */
    fun requestPreloadChapter(chapter: ReaderChapter) {
        presenter.preloadChapter(chapter)
    }

    /**
     * Called from the viewer to toggle the visibility of the menu. It's implemented on the
     * viewer because each one implements its own touch and key events.
     */
    fun toggleMenu() {
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
     * Called from the page sheet. It delegates the call to the presenter to do some IO, which
     * will call [onShareImageResult] with the path the image was saved on when it's ready.
     */
    private fun shareImage(page: ReaderPage) {
        presenter.shareImage(page)
    }

    private fun showSetCoverPrompt(page: ReaderPage) {
        if (page.status != Page.READY) return

        materialAlertDialog()
            .setMessage(R.string.use_image_as_cover)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                setAsCover(page)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Called from the presenter when a page is ready to be shared. It shows Android's default
     * sharing tool.
     */
    fun onShareImageResult(file: File, page: ReaderPage, secondPage: ReaderPage? = null) {
        val manga = presenter.manga ?: return
        val chapter = page.chapter.chapter

        val decimalFormat =
            DecimalFormat("#.###", DecimalFormatSymbols().apply { decimalSeparator = '.' })

        val pageNumber = if (secondPage != null) {
            getString(R.string.pages_, if (resources.isLTR) "${page.number}-${page.number + 1}" else "${page.number + 1}-${page.number}")
        } else {
            getString(R.string.page_, page.number)
        }
        val text = "${manga.title}: ${getString(
            R.string.chapter_,
            decimalFormat.format(chapter.chapter_number),
        )}, $pageNumber"

        val stream = file.getUriCompat(this)
        val intent = Intent(Intent.ACTION_SEND).apply {
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
        val chapterUrl = presenter.getChapterUrl() ?: return
        outContent.webUri = Uri.parse(chapterUrl)
    }

    /**
     * Called from the page sheet. It delegates saving the image of the given [page] on external
     * storage to the presenter.
     */
    private fun saveImage(page: ReaderPage) {
        presenter.saveImage(page)
    }

    /**
     * Called from the presenter when a page is saved or fails. It shows a message or logs the
     * event depending on the [result].
     */
    fun onSaveImageResult(result: ReaderPresenter.SaveImageResult) {
        when (result) {
            is ReaderPresenter.SaveImageResult.Success -> {
                toast(R.string.picture_saved)
            }
            is ReaderPresenter.SaveImageResult.Error -> {
                Timber.e(result.error)
            }
        }
    }

    /**
     * Called from the page sheet. It delegates setting the image of the given [page] as the
     * cover to the presenter.
     */
    private fun setAsCover(page: ReaderPage) {
        presenter.setAsCover(page)
    }

    /**
     * Called from the presenter when a page is set as cover or fails. It shows a different message
     * depending on the [result].
     */
    fun onSetAsCoverResult(result: ReaderPresenter.SetAsCoverResult) {
        toast(
            when (result) {
                Success -> R.string.cover_updated
                AddToLibraryFirst -> R.string.must_be_in_library_to_edit
                Error -> R.string.failed_to_update_cover
            },
        )
    }

    private fun onVisibilityChange(visible: Boolean) {
        if (visible && !menuStickyVisible && !menuVisible && !binding.readerMenu.isVisible) {
            menuStickyVisible = visible
            if (visible) {
                coroutine = launchUI {
                    delay(2000)
                    if (window.decorView.rootWindowInsetsCompat?.isVisible(statusBars()) == true) {
                        menuStickyVisible = false
                        setMenuVisibility(false)
                    }
                }
                if (sheetManageNavColor) {
                    window.navigationBarColor =
                        ColorUtils.setAlphaComponent(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 || isInNightMode()) {
                                getResourceColor(R.attr.colorSurface)
                            } else Color.BLACK,
                            if (binding.root.rootWindowInsetsCompat?.hasSideNavBar() == true) {
                                255
                            } else {
                                179
                            },
                        )
                }
                binding.readerMenu.isVisible = true
                val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_top)
                toolbarAnimation.doOnStart {
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                }
                binding.appBar.startAnimation(toolbarAnimation)
            }
        } else if (!visible && (menuStickyVisible || menuVisible)) {
            if (menuStickyVisible && !menuVisible) {
                setMenuVisibility(false)
            }
            coroutine?.cancel()
        }
    }

    /**
     * Sets notch cutout mode to "NEVER", if mobile is in a landscape view
     */
    private fun setNotchCutoutMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val currentOrientation = resources.configuration.orientation

            val params = window.attributes
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            } else {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
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
        if (!presenter.canLoadUrl(uri)) {
            openInBrowser(intent.data!!.toString(), true)
            finishAfterTransition()
            return true
        }
        setMenuVisibility(visible = false, animate = true)
        scope.launch(Dispatchers.IO) {
            try {
                intentPageNumber = presenter.intentPageNumber(uri)
                presenter.loadChapterURL(uri)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setInitialChapterError(e)
                }
            }
        }
        return true
    }

    private fun openMangaInBrowser() {
        val source = presenter.getSource() ?: return
        val chapterUrl = presenter.getChapterUrl() ?: return

        val intent = WebViewActivity.newIntent(
            applicationContext,
            source.id,
            chapterUrl,
            presenter.manga!!.title,
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
            preferences.defaultOrientationType().asFlow()
                .drop(1)
                .onEach {
                    delay(250)
                    setOrientation(presenter.getMangaOrientationType())
                }
                .launchIn(scope)

            preferences.showPageNumber().asImmediateFlowIn(scope) { setPageNumberVisibility(it) }

            preferences.trueColor().asImmediateFlowIn(scope) { setTrueColor(it) }

            preferences.fullscreen().asImmediateFlowIn(scope) { setFullscreen(it) }

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

            preferences.automaticSplitsPage().asFlow()
                .drop(1)
                .onEach {
                    val isPaused = !this@ReaderActivity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    if (isPaused) {
                        (viewer as? PagerViewer)?.config?.let { config ->
                            reloadChapters(config.doublePages, true)
                        }
                    }
                }
                .launchIn(scope)

            preferences.readerBottomButtons().asImmediateFlowIn(scope) { updateBottomShortcuts() }
        }

        /**
         * Sets the visibility of the bottom page indicator according to [visible].
         */
        private fun setPageNumberVisibility(visible: Boolean) {
            binding.pageNumber.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        }

        /**
         * Sets the 32-bit color mode according to [enabled].
         */
        private fun setTrueColor(enabled: Boolean) {
            if (enabled) {
                SubsamplingScaleImageView.setPreferredBitmapConfig(Bitmap.Config.ARGB_8888)
            } else {
                SubsamplingScaleImageView.setPreferredBitmapConfig(Bitmap.Config.RGB_565)
            }
        }

        /**
         * Sets the fullscreen reading mode (immersive) according to [enabled].
         */
        private fun setFullscreen(enabled: Boolean) {
            WindowCompat.setDecorFitsSystemWindows(window, !enabled || isSplitScreen)
            wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
            binding.root.rootWindowInsetsCompat?.let { setNavColor(it) }
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
                preferences.customBrightnessValue().asFlow()
                    .sample(100)
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
                preferences.colorFilterValue().asFlow()
                    .sample(100)
                    .onEach { setColorFilterValue(it) }
                    .launchIn(scope)
            } else {
                binding.colorOverlay.isVisible = false
            }
        }

        private fun getCombinedPaint(grayscale: Boolean, invertedColors: Boolean): Paint {
            return Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix().apply {
                        if (grayscale) {
                            setSaturation(0f)
                        }
                        if (invertedColors) {
                            postConcat(
                                ColorMatrix(
                                    floatArrayOf(
                                        -1f, 0f, 0f, 0f, 255f,
                                        0f, -1f, 0f, 0f, 255f,
                                        0f, 0f, -1f, 0f, 255f,
                                        0f, 0f, 0f, 1f, 0f,
                                    ),
                                ),
                            )
                        }
                    },
                )
            }
        }

        /**
         * Sets the brightness of the screen. Range is [-75, 100].
         * From -75 to -1 a semi-transparent black view is overlaid with the minimum brightness.
         * From 1 to 100 it sets that value as brightness.
         * 0 sets system brightness and hides the overlay.
         */
        private fun setCustomBrightnessValue(value: Int) {
            // Calculate and set reader brightness.
            val readerBrightness = when {
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

        private fun setLayerPaint(grayscale: Boolean, invertedColors: Boolean) {
            val paint = if (grayscale || invertedColors) getCombinedPaint(grayscale, invertedColors) else null
            binding.viewerContainer.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        }
    }
}
