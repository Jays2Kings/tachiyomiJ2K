package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import coil.load
import coil.request.CachePolicy
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.github.chrisbanes.photoview.PhotoView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressBar
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig.Companion.CUTOUT_IGNORE
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig.Companion.CUTOUT_START_EXTENDED
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig.ZoomType
import eu.kanade.tachiyomi.util.system.GLUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.ThemeUtil
import eu.kanade.tachiyomi.util.system.bottomCutoutInset
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.topCutoutInset
import eu.kanade.tachiyomi.util.view.backgroundColor
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import eu.kanade.tachiyomi.widget.GifViewTarget
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * View of the ViewPager that contains a page of a chapter.
 */
@SuppressLint("ViewConstructor")
class PagerPageHolder(
    val viewer: PagerViewer,
    val page: ReaderPage,
    private var extraPage: ReaderPage? = null,
) : FrameLayout(viewer.activity), ViewPagerAdapter.PositionableView {

    /**
     * Item that identifies this view. Needed by the adapter to not recreate views.
     */
    override val item
        get() = page to extraPage

    /**
     * Loading progress bar to indicate the current progress.
     */
    private val progressBar = createProgressBar()

    /**
     * Image view that supports subsampling on zoom.
     */
    private var subsamplingImageView: SubsamplingScaleImageView? = null

    /**
     * Simple image view only used on GIFs.
     */
    private var imageView: ImageView? = null

    /**
     * Retry button used to allow retrying.
     */
    private var retryButton: PagerButton? = null

    /**
     * Error layout to show when the image fails to decode.
     */
    private var decodeErrorLayout: ViewGroup? = null

    /**
     * Subscription for status changes of the page.
     */
    private var statusSubscription: Subscription? = null

    /**
     * Subscription for progress changes of the page.
     */
    private var progressSubscription: Subscription? = null

    /**
     * Subscription for status changes of the page.
     */
    private var extraStatusSubscription: Subscription? = null

    /**
     * Subscription for progress changes of the page.
     */
    private var extraProgressSubscription: Subscription? = null

    /**
     * Subscription used to read the header of the image. This is needed in order to instantiate
     * the appropiate image view depending if the image is animated (GIF).
     */
    private var readImageHeaderSubscription: Subscription? = null

    var status: Int = 0
    var extraStatus: Int = 0
    var progress: Int = 0
    var extraProgress: Int = 0

    var scope: CoroutineScope? = null

    init {
        addView(progressBar)
        scope = CoroutineScope(Job() + Default)
        observeStatus()
        setBackgroundColor(
            when (val theme = viewer.config.readerTheme) {
                3 -> Color.TRANSPARENT
                else -> ThemeUtil.readerBackgroundColor(theme)
            },
        )
        progressBar.foregroundTintList = ColorStateList.valueOf(
            context.getResourceColor(
                if (isInvertedFromTheme()) R.attr.colorPrimaryInverse
                else R.attr.colorPrimary,
            ),
        )
    }

    /**
     * Called when this view is detached from the window. Unsubscribes any active subscription.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unsubscribeProgress(1)
        unsubscribeStatus(1)
        unsubscribeProgress(2)
        unsubscribeStatus(2)
        unsubscribeReadImageHeader()
        scope?.cancel()
        scope = null
        subsamplingImageView?.setOnImageEventListener(null)
    }

    fun onPageSelected(forward: Boolean?) {
        subsamplingImageView?.apply {
            if (isReady) {
                landscapeZoom(forward)
            } else {
                forward ?: return@apply
                setOnImageEventListener(
                    object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        override fun onImageLoaded() {
                            if (this@PagerPageHolder.extraPage == null &&
                                this@PagerPageHolder.page.longPage == null &&
                                sHeight < sWidth
                            ) {
                                this@PagerPageHolder.page.longPage = true
                            }
                        }

                        override fun onReady() {
                            setupZoom()
                            landscapeZoom(forward)
                            onImageDecoded()
                        }

                        override fun onImageLoadError(e: Exception) {
                            onImageDecodeError()
                        }
                    },
                )
            }
        }
    }

    /**
     * Check if the image can be panned to the left
     */
    fun canPanLeft(): Boolean = canPan { it.left }

    /**
     * Check if the image can be panned to the right
     */
    fun canPanRight(): Boolean = canPan { it.right }

    /**
     * Check whether the image can be panned.
     * @param fn a function that returns the direction to check for
     */
    private fun canPan(fn: (RectF) -> Float): Boolean {
        subsamplingImageView?.let { view ->
            RectF().let {
                view.getPanRemaining(it)
                return fn(it) > 0.01f
            }
        }
        return false
    }

    /**
     * Pans the image to the left by a screen's width worth.
     */
    fun panLeft() {
        pan { center, view -> center.also { it.x -= view.width / view.scale } }
    }

    /**
     * Pans the image to the right by a screen's width worth.
     */
    fun panRight() {
        pan { center, view -> center.also { it.x += view.width / view.scale } }
    }

    /**
     * Pans the image.
     * @param fn a function that computes the new center of the image
     */
    private fun pan(fn: (PointF, SubsamplingScaleImageView) -> PointF) {
        subsamplingImageView?.let { view ->
            val target = fn(view.center ?: return, view)
            view.animateCenter(target)!!
                .withEasing(SubsamplingScaleImageView.EASE_OUT_QUAD)
                .withDuration(250)
                .withInterruptible(true)
                .start()
        }
    }

    private fun SubsamplingScaleImageView.landscapeZoom(forward: Boolean?) {
        forward ?: return
        if (viewer.config.landscapeZoom && viewer.config.imageScaleType == SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE && sWidth > sHeight && scale == minScale) {
            handler.postDelayed(
                {
                    val point = when (viewer.config.imageZoomType) {
                        ZoomType.Left -> if (forward) PointF(0F, 0F) else PointF(sWidth.toFloat(), 0F)
                        ZoomType.Right -> if (forward) PointF(sWidth.toFloat(), 0F) else PointF(0F, 0F)
                        ZoomType.Center -> center.also { it?.y = 0F }
                    }

                    val topInsets = if (viewer.activity.isSplitScreen) 0f else {
                        viewer.activity.window.decorView.rootWindowInsets.topCutoutInset().toFloat()
                    }
                    val bottomInsets = if (viewer.activity.isSplitScreen) 0f else {
                        viewer.activity.window.decorView.rootWindowInsets.bottomCutoutInset().toFloat()
                    }
                    val targetScale = (height.toFloat() - topInsets - bottomInsets) / sHeight.toFloat()
                    animateScaleAndCenter(min(targetScale, minScale * 2), point)!!
                        .withDuration(500)
                        .withEasing(SubsamplingScaleImageView.EASE_IN_OUT_QUAD)
                        .withInterruptible(true)
                        .start()
                },
                500,
            )
        }
    }

    private fun SubsamplingScaleImageView.setupZoom() {
        // 5x zoom
        maxScale = scale * MAX_ZOOM_SCALE
        setDoubleTapZoomScale(scale * 2)

        var centerV = 0f
        val config = viewer.config
        when (config.imageZoomType) {
            ZoomType.Left -> {
                setScaleAndCenter(scale, PointF(0f, 0f))
            }
            ZoomType.Right -> {
                setScaleAndCenter(scale, PointF(sWidth.toFloat(), 0f))
                centerV = sWidth.toFloat()
            }
            ZoomType.Center -> {
                setScaleAndCenter(scale, center.also { it?.y = 0f })
                centerV = center?.x ?: 0f
            }
        }
        val topInsets = viewer.activity.window.decorView.rootWindowInsets.topCutoutInset().toFloat()
        val bottomInsets = viewer.activity.window.decorView.rootWindowInsets.bottomCutoutInset().toFloat()
        if (config.cutoutBehavior == CUTOUT_START_EXTENDED &&
            topInsets + bottomInsets > 0 &&
            config.scaleTypeIsFullFit()
        ) {
            setScaleAndCenter(
                scale,
                PointF(centerV, (center?.y?.plus(topInsets)?.minus(bottomInsets) ?: 0f)),
            )
        }
    }

    /**
     * Observes the status of the page and notify the changes.
     *
     * @see processStatus
     */
    private fun observeStatus() {
        statusSubscription?.unsubscribe()

        val loader = page.chapter.pageLoader ?: return
        statusSubscription = loader.getPage(page)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                status = it
                processStatus(it)
            }
        val extraPage = extraPage ?: return
        val loader2 = extraPage.chapter.pageLoader ?: return
        extraStatusSubscription = loader2.getPage(extraPage)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                extraStatus = it
                processStatus2(it)
            }
    }

    /**
     * Observes the progress of the page and updates view.
     */
    private fun observeProgress() {
        progressSubscription?.unsubscribe()

        progressSubscription = Observable.interval(100, TimeUnit.MILLISECONDS)
            .map { page.progress }
            .distinctUntilChanged()
            .onBackpressureLatest()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { value ->
                progress = value
                if (extraPage == null) {
                    progressBar.setProgress(progress)
                } else {
                    progressBar.setProgress(((progress + extraProgress) / 2 * 0.95f).roundToInt())
                }
            }
    }

    private fun observeProgress2() {
        extraProgressSubscription?.unsubscribe()
        val extraPage = extraPage ?: return
        extraProgressSubscription = Observable.interval(100, TimeUnit.MILLISECONDS)
            .map { extraPage.progress }
            .distinctUntilChanged()
            .onBackpressureLatest()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { value ->
                extraProgress = value
                progressBar.setProgress(((progress + extraProgress) / 2 * 0.95f).roundToInt())
            }
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private fun processStatus(status: Int) {
        when (status) {
            Page.QUEUE -> setQueued()
            Page.LOAD_PAGE -> setLoading()
            Page.DOWNLOAD_IMAGE -> {
                observeProgress()
                setDownloading()
            }
            Page.READY -> {
                if (extraStatus == Page.READY || extraPage == null) {
                    setImage()
                }
                unsubscribeProgress(1)
            }
            Page.ERROR -> {
                setError()
                unsubscribeProgress(1)
            }
        }
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private fun processStatus2(status: Int) {
        when (status) {
            Page.QUEUE -> setQueued()
            Page.LOAD_PAGE -> setLoading()
            Page.DOWNLOAD_IMAGE -> {
                observeProgress2()
                setDownloading()
            }
            Page.READY -> {
                if (this.status == Page.READY) {
                    setImage()
                }
                unsubscribeProgress(2)
            }
            Page.ERROR -> {
                setError()
                unsubscribeProgress(2)
            }
        }
    }

    /**
     * Unsubscribes from the status subscription.
     */
    private fun unsubscribeStatus(page: Int) {
        val subscription = if (page == 1) statusSubscription else extraStatusSubscription
        subscription?.unsubscribe()
        if (page == 1) statusSubscription = null else extraStatusSubscription = null
    }

    /**
     * Unsubscribes from the progress subscription.
     */
    private fun unsubscribeProgress(page: Int) {
        val subscription = if (page == 1) progressSubscription else extraProgressSubscription
        subscription?.unsubscribe()
        if (page == 1) progressSubscription = null else extraProgressSubscription = null
    }

    /**
     * Unsubscribes from the read image header subscription.
     */
    private fun unsubscribeReadImageHeader() {
        readImageHeaderSubscription?.unsubscribe()
        readImageHeaderSubscription = null
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        progressBar.isVisible = true
        retryButton?.isVisible = false
        decodeErrorLayout?.isVisible = false
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progressBar.isVisible = true
        retryButton?.isVisible = false
        decodeErrorLayout?.isVisible = false
    }

    /**
     * Called when the page is downloading.
     */
    private fun setDownloading() {
        progressBar.isVisible = true
        retryButton?.isVisible = false
        decodeErrorLayout?.isVisible = false
    }

    /**
     * Called when the page is ready.
     */
    private fun setImage() {
        progressBar.isVisible = true
        if (extraPage == null) {
            progressBar.completeAndFadeOut()
        } else {
            progressBar.setProgress(95)
        }
        retryButton?.isVisible = false
        decodeErrorLayout?.isVisible = false

        unsubscribeReadImageHeader()
        val streamFn = page.stream ?: return
        val streamFn2 = extraPage?.stream

        var openStream: InputStream? = null

        readImageHeaderSubscription = Observable
            .fromCallable {
                val stream = streamFn().buffered(16)

                val stream2 = if (extraPage != null) streamFn2?.invoke()?.buffered(16) else null
                openStream = this@PagerPageHolder.mergeOrSplitPages(stream, stream2)
                ImageUtil.isAnimatedAndSupported(stream) ||
                    if (stream2 != null) ImageUtil.isAnimatedAndSupported(stream2) else false
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { isAnimated ->
                if (!isAnimated) {
                    if (viewer.config.readerTheme >= 2) {
                        val imageView = initSubsamplingImageView()
                        if (page.bg != null &&
                            page.bgType == getBGType(viewer.config.readerTheme, context) + item.hashCode()
                        ) {
                            imageView.setImage(ImageSource.inputStream(openStream!!))
                            imageView.background = page.bg
                        }
                        // if the user switches to automatic when pages are already cached, the bg needs to be loaded
                        else {
                            val bytesArray = openStream!!.readBytes()
                            val bytesStream = bytesArray.inputStream()
                            imageView.setImage(ImageSource.inputStream(bytesStream))
                            bytesStream.close()

                            launchUI {
                                try {
                                    imageView.background = setBG(bytesArray)
                                } catch (e: Exception) {
                                    Timber.e(e.localizedMessage)
                                    imageView.background = ColorDrawable(Color.WHITE)
                                } finally {
                                    page.bg = imageView.background
                                    page.bgType = getBGType(
                                        viewer.config.readerTheme,
                                        context,
                                    ) + item.hashCode()
                                }
                            }
                        }
                    } else {
                        val imageView = initSubsamplingImageView()
                        imageView.setImage(ImageSource.inputStream(openStream!!))
                    }
                } else {
                    val imageView = initImageView()
                    imageView.setImage(openStream!!)
                    if (viewer.config.readerTheme >= 2 && page.bg != null) {
                        imageView.background = page.bg
                    }
                }
            }
            // Keep the Rx stream alive to close the input stream only when unsubscribed
            .flatMap { Observable.never<Unit>() }
            .doOnUnsubscribe {
                try {
                    openStream?.close()
                } catch (e: Exception) {}
            }
            .doOnError {
                try {
                    openStream?.close()
                } catch (e: Exception) {}
            }
            .subscribe({}, {})
    }

    private suspend fun setBG(bytesArray: ByteArray): Drawable {
        return withContext(Default) {
            val preferences by injectLazy<PreferencesHelper>()
            ImageUtil.autoSetBackground(
                BitmapFactory.decodeByteArray(
                    bytesArray,
                    0,
                    bytesArray.size,
                ),
                preferences.readerTheme().get() == 2,
                context,
            )
        }
    }

    /**
     * Called when the page has an error.
     */
    private fun setError() {
        progressBar.isVisible = false
        initRetryButton().isVisible = true
    }

    /**
     * Called when the image is decoded and going to be displayed.
     */
    private fun onImageDecoded() {
        progressBar.isVisible = false
    }

    /**
     * Called when an image fails to decode.
     */
    private fun onImageDecodeError() {
        progressBar.isVisible = false
        initDecodeErrorLayout().isVisible = true
    }

    /**
     * Creates a new progress bar.
     */
    private fun createProgressBar(): ReaderProgressBar {
        return ReaderProgressBar(context, null).apply {
            val size = 48.dpToPx
            layoutParams = LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
        }
    }

    private fun isInvertedFromTheme(): Boolean {
        return when (backgroundColor) {
            Color.WHITE -> context.isInNightMode()
            Color.BLACK -> !context.isInNightMode()
            else -> false
        }
    }

    /**
     * Initializes a subsampling scale view.
     */
    private fun initSubsamplingImageView(): SubsamplingScaleImageView {
        if (subsamplingImageView != null) return subsamplingImageView!!

        val config = viewer.config

        subsamplingImageView = SubsamplingScaleImageView(context).apply {
            setMaxTileSize(GLUtil.maxTextureSize)
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setDoubleTapZoomDuration(config.doubleTapAnimDuration)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumScaleType(config.imageScaleType)
            setMinimumTileDpi(180)
            setMinimumDpi(1)
            setCropBorders(config.imageCropBorders)
            val topInsets = viewer.activity.window.decorView.rootWindowInsets.topCutoutInset().toFloat()
            val bottomInsets = viewer.activity.window.decorView.rootWindowInsets.bottomCutoutInset().toFloat()
            setExtendPastCutout(config.cutoutBehavior == CUTOUT_START_EXTENDED && config.scaleTypeIsFullFit() && topInsets + bottomInsets > 0)
            if ((config.cutoutBehavior != CUTOUT_IGNORE || !config.scaleTypeIsFullFit()) &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                config.isFullscreen &&
                !viewer.activity.isInMultiWindowMode
            ) {
                val insets: WindowInsets? = viewer.activity.window.decorView.rootWindowInsets
                setExtraSpace(
                    0f,
                    insets?.displayCutout?.boundingRectTop?.height()?.toFloat() ?: 0f,
                    0f,
                    insets?.displayCutout?.boundingRectBottom?.height()?.toFloat() ?: 0f,
                )
            }
            setOnImageEventListener(
                object : SubsamplingScaleImageView.DefaultOnImageEventListener() {

                    override fun onImageLoaded() {
                        if (this@PagerPageHolder.extraPage == null &&
                            this@PagerPageHolder.page.longPage == null &&
                            sHeight < sWidth
                        ) {
                            this@PagerPageHolder.page.longPage = true
                        }
                    }
                    override fun onReady() {
                        setupZoom()
                        if (viewer.heldForwardZoom?.first == page.index) {
                            landscapeZoom(viewer.heldForwardZoom?.second)
                            viewer.heldForwardZoom = null
                        } else if (isVisibleOnScreen()) {
                            landscapeZoom(true)
                        }
                        onImageDecoded()
                    }

                    override fun onImageLoadError(e: Exception) {
                        onImageDecodeError()
                    }
                },
            )
        }
        addView(subsamplingImageView, MATCH_PARENT, MATCH_PARENT)
        return subsamplingImageView!!
    }

    /**
     * Initializes an image view, used for GIFs.
     */
    private fun initImageView(): ImageView {
        if (imageView != null) return imageView!!

        imageView = PhotoView(context, null).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
            adjustViewBounds = true
            setZoomTransitionDuration(viewer.config.doubleTapAnimDuration)
            setScaleLevels(1f, 2f, 3f)
            // Force 2 scale levels on double tap
            setOnDoubleTapListener(
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        if (scale > 1f) {
                            setScale(1f, e.x, e.y, true)
                        } else {
                            setScale(2f, e.x, e.y, true)
                        }
                        return true
                    }
                },
            )
        }
        addView(imageView)
        return imageView!!
    }

    /**
     * Initializes a button to retry pages.
     */
    private fun initRetryButton(): PagerButton {
        if (retryButton != null) return retryButton!!

        retryButton = PagerButton(context, viewer).apply {
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            setText(R.string.retry)
            setOnClickListener {
                page.chapter.pageLoader?.retryPage(page)
                extraPage?.let {
                    it.chapter.pageLoader?.retryPage(it)
                }
            }
        }
        addView(retryButton)
        return retryButton!!
    }

    /**
     * Initializes a decode error layout.
     */
    private fun initDecodeErrorLayout(): ViewGroup {
        if (decodeErrorLayout != null) return decodeErrorLayout!!

        val margins = 8.dpToPx

        val decodeLayout = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
        }
        decodeErrorLayout = decodeLayout

        TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(margins, margins, margins, margins)
            }
            gravity = Gravity.CENTER
            setText(R.string.decode_image_error)

            decodeLayout.addView(this)
        }

        PagerButton(context, viewer).apply {
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(margins, margins, margins, margins)
            }
            setText(R.string.retry)
            setOnClickListener {
                page.chapter.pageLoader?.retryPage(page)
            }

            decodeLayout.addView(this)
        }

        val imageUrl = page.imageUrl
        if (imageUrl != null && imageUrl.startsWith("http", true)) {
            PagerButton(context, viewer).apply {
                layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    setMargins(margins, margins, margins, margins)
                }
                setText(R.string.open_in_browser)
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, imageUrl.toUri())
                    context.startActivity(intent)
                }

                decodeLayout.addView(this)
            }
        }

        addView(decodeLayout)
        return decodeLayout
    }

    private fun mergeOrSplitPages(imageStream: InputStream, imageStream2: InputStream?): InputStream {
        if (page.longPage == true && viewer.config.splitPages) {
            val imageBytes = imageStream.readBytes()
            val imageBitmap = try {
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } catch (e: Exception) {
                imageStream.close()
                Timber.e("Cannot split page ${e.message}")
                return imageBytes.inputStream()
            }
            val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)
            return ImageUtil.splitBitmap(imageBitmap, (page.firstHalf == false).xor(!isLTR)) {
                scope?.launchUI {
                    if (it == 100) {
                        progressBar.completeAndFadeOut()
                    } else {
                        progressBar.setProgress(it)
                    }
                }
            }
        }
        if (imageStream2 == null) {
            if (viewer.config.splitPages && page.longPage == null) {
                val imageBytes = imageStream.readBytes()
                val imageBitmap = try {
                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                } catch (e: Exception) {
                    imageStream.close()
                    page.longPage = true
                    splitDoublePages()
                    Timber.e("Cannot split page ${e.message}")
                    return imageBytes.inputStream()
                }
                val height = imageBitmap.height
                val width = imageBitmap.width
                return if (height < width) {
                    imageStream.close()
                    page.longPage = true
                    splitDoublePages()
                    val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)
                    return ImageUtil.splitBitmap(imageBitmap, !isLTR) {
                        scope?.launchUI {
                            if (it == 100) {
                                progressBar.completeAndFadeOut()
                            } else {
                                progressBar.setProgress(it)
                            }
                        }
                    }
                } else {
                    page.longPage = false
                    imageBytes.inputStream()
                }
            }
            return imageStream
        }
        if (page.fullPage == true) return imageStream
        if (ImageUtil.isAnimatedAndSupported(imageStream)) {
            page.fullPage = true
            splitDoublePages()
            return imageStream
        } else if (ImageUtil.isAnimatedAndSupported(imageStream)) {
            page.isolatedPage = true
            extraPage?.fullPage = true
            splitDoublePages()
            return imageStream
        }
        val imageBytes = imageStream.readBytes()
        val imageBitmap = try {
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            imageStream2.close()
            imageStream.close()
            page.fullPage = true
            splitDoublePages()
            Timber.e("Cannot combine pages ${e.message}")
            return imageBytes.inputStream()
        }
        scope?.launchUI { progressBar.setProgress(96) }
        val height = imageBitmap.height
        val width = imageBitmap.width

        if (height < width) {
            imageStream2.close()
            imageStream.close()
            page.fullPage = true
            splitDoublePages()
            return imageBytes.inputStream()
        }

        val imageBytes2 = imageStream2.readBytes()
        val imageBitmap2 = try {
            BitmapFactory.decodeByteArray(imageBytes2, 0, imageBytes2.size)
        } catch (e: Exception) {
            imageStream2.close()
            imageStream.close()
            extraPage?.fullPage = true
            page.isolatedPage = true
            splitDoublePages()
            Timber.e("Cannot combine pages ${e.message}")
            return imageBytes.inputStream()
        }
        scope?.launchUI { progressBar.setProgress(97) }
        val height2 = imageBitmap2.height
        val width2 = imageBitmap2.width

        if (height2 < width2) {
            imageStream2.close()
            imageStream.close()
            extraPage?.fullPage = true
            page.isolatedPage = true
            splitDoublePages()
            return imageBytes.inputStream()
        }
        val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)
        val bg = if (viewer.config.readerTheme >= 2 || viewer.config.readerTheme == 0) {
            Color.WHITE
        } else {
            Color.BLACK
        }

        imageStream.close()
        imageStream2.close()
        return ImageUtil.mergeBitmaps(imageBitmap, imageBitmap2, isLTR, bg) {
            scope?.launchUI {
                if (it == 100) {
                    progressBar.completeAndFadeOut()
                } else {
                    progressBar.setProgress(it)
                }
            }
        }
    }

    private fun splitDoublePages() {
        // extraPage ?: return
        scope?.launchUI {
            delay(100)
            viewer.splitDoublePages(page)
            if (extraPage?.fullPage == true || page.fullPage == true) {
                extraPage = null
            }
        }
    }

    /**
     * Extension method to set a [stream] into this ImageView.
     */
    private fun ImageView.setImage(stream: InputStream) {
        this.load(stream.readBytes()) {
            memoryCachePolicy(CachePolicy.DISABLED)
            diskCachePolicy(CachePolicy.DISABLED)
            target(GifViewTarget(this@setImage, progressBar, decodeErrorLayout))
        }
    }

    companion object {
        fun getBGType(readerTheme: Int, context: Context): Int {
            return if (readerTheme == 3) {
                if (context.isInNightMode()) 2 else 1
            } else 0 + (context.resources.configuration?.orientation ?: 0) * 10
        }
    }
}

private const val MAX_ZOOM_SCALE = 5F
