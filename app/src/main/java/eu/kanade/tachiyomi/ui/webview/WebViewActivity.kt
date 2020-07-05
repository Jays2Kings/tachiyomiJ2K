package eu.kanade.tachiyomi.ui.webview

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.system.WebViewClientCompat
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.invisible
import eu.kanade.tachiyomi.util.view.marginBottom
import eu.kanade.tachiyomi.util.view.setStyle
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.util.view.updatePadding
import eu.kanade.tachiyomi.util.view.visible
import kotlinx.android.synthetic.main.webview_activity.*
import uy.kohesive.injekt.injectLazy

class WebViewActivity : BaseActivity() {

    private val sourceManager by injectLazy<SourceManager>()
    private var bundle: Bundle? = null

    companion object {
        const val SOURCE_KEY = "source_key"
        const val URL_KEY = "url_key"
        const val TITLE_KEY = "title_key"

        fun newIntent(context: Context, sourceId: Long, url: String, title: String?): Intent {
            val intent = Intent(context, WebViewActivity::class.java)
            intent.putExtra(SOURCE_KEY, sourceId)
            intent.putExtra(URL_KEY, url)
            intent.putExtra(TITLE_KEY, title)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        delegate.localNightMode = when (preferences.theme()) {
            1, 8 -> AppCompatDelegate.MODE_NIGHT_NO
            2, 3, 4 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        setContentView(R.layout.webview_activity)
        title = intent.extras?.getString(TITLE_KEY)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            super.onBackPressed()
        }
        toolbar.navigationIcon?.setTint(getResourceColor(R.attr.actionBarTintColor))

        val container: ViewGroup = findViewById(R.id.web_view_layout)
        val content: LinearLayout = findViewById(R.id.web_linear_layout)
        container.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        content.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        container.setOnApplyWindowInsetsListener { v, insets ->
            val contextView = window?.decorView?.findViewById<View>(R.id.action_mode_bar)
            contextView?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.systemWindowInsetLeft
                rightMargin = insets.systemWindowInsetRight
            }
            // Consume any horizontal insets and pad all content in. There's not much we can do
            // with horizontal insets
            v.updatePadding(
                left = insets.systemWindowInsetLeft,
                right = insets.systemWindowInsetRight
            )
            insets.replaceSystemWindowInsets(
                0, insets.systemWindowInsetTop,
                0, insets.systemWindowInsetBottom
            )
        }
        swipe_refresh.setStyle()
        swipe_refresh.setOnRefreshListener {
            refreshPage()
        }

        window.statusBarColor = ColorUtils.setAlphaComponent(getResourceColor(R.attr
            .colorSecondary), 255)

        content.setOnApplyWindowInsetsListener { v, insets ->
            // if pure white theme on a device that does not support dark status bar
            /*if (getResourceColor(android.R.attr.statusBarColor) != Color.TRANSPARENT)
                window.statusBarColor = Color.BLACK
            else window.statusBarColor = getResourceColor(R.attr.colorPrimary)*/
            window.navigationBarColor = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                val colorPrimary = getResourceColor(R.attr.colorPrimaryVariant)
                if (colorPrimary == Color.WHITE) Color.BLACK
                else getResourceColor(android.R.attr.colorPrimary)
            }
            // if the android q+ device has gesture nav, transparent nav bar
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                (v.rootWindowInsets.systemWindowInsetBottom != v.rootWindowInsets.tappableElementInsets.bottom)) {
                getColor(android.R.color.transparent)
            } else {
                getResourceColor(android.R.attr.colorBackground)
            }
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, 0)
            if (Build.VERSION.SDK_INT >= 26 && !isInNightMode()) {
                content.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            insets
        }

        swipe_refresh.isEnabled = false

        if (bundle == null) {
            val source = sourceManager.get(intent.extras!!.getLong(SOURCE_KEY)) as? HttpSource ?: return
            val url = intent.extras!!.getString(URL_KEY) ?: return
            val headers = source.headers.toMultimap().mapValues { it.value.getOrNull(0) ?: "" }
            webview.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.visible()
                    progressBar.progress = newProgress
                    if (newProgress == 100)
                        progressBar.invisible()
                    super.onProgressChanged(view, newProgress)
                }
            }

            webview.webViewClient = object : WebViewClientCompat() {
                override fun shouldOverrideUrlCompat(view: WebView, url: String): Boolean {
                    view.loadUrl(url)
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    invalidateOptionsMenu()
                    title = view?.title
                    swipe_refresh.isEnabled = true
                    swipe_refresh?.isRefreshing = false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    invalidateOptionsMenu()
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    nested_view.scrollTo(0, 0)
                }
            }
            val marginB = webview.marginBottom
            webview.setOnApplyWindowInsetsListener { v, insets ->
                val bottomInset =
                    if (Build.VERSION.SDK_INT >= 29) insets.tappableElementInsets.bottom
                    else insets.systemWindowInsetBottom
                v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = marginB + bottomInset
                }
                insets
            }
            webview.settings.javaScriptEnabled = true
            webview.settings.domStorageEnabled = true

            webview.settings.userAgentString = source.headers["User-Agent"]
            webview.loadUrl(url, headers)
        } else {
            webview.restoreState(bundle)
        }
    }
    private fun refreshPage() {
        swipe_refresh.isRefreshing = true
        webview.reload()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val lightMode = !isInNightMode()
        window.statusBarColor = ColorUtils.setAlphaComponent(getResourceColor(R.attr
            .colorSecondary), 255)
        toolbar.setBackgroundColor(getResourceColor(R.attr.colorSecondary))
        toolbar.popupTheme = if (lightMode) R.style.ThemeOverlay_MaterialComponents else R
            .style.ThemeOverlay_MaterialComponents_Dark
        val tintColor = getResourceColor(R.attr.actionBarTintColor)
        toolbar.navigationIcon?.setTint(tintColor)
        toolbar.overflowIcon?.mutate()
        toolbar.setTitleTextColor(tintColor)
        toolbar.overflowIcon?.setTint(tintColor)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            window.navigationBarColor = getResourceColor(R.attr.colorPrimaryVariant)
        else if (window.navigationBarColor != getColor(android.R.color.transparent))
            window.navigationBarColor = getResourceColor(android.R.attr.colorBackground)

        web_linear_layout.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && lightMode) {
            web_linear_layout.systemUiVisibility = web_linear_layout.systemUiVisibility.or(View
                .SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        }
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.windowLightStatusBar, typedValue, true)

        if (typedValue.data == -1)
            web_linear_layout.systemUiVisibility = web_linear_layout.systemUiVisibility
                .or(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        else
            web_linear_layout.systemUiVisibility = web_linear_layout.systemUiVisibility
                .rem(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        invalidateOptionsMenu()
    }

    /**
     * Called when the options menu of the toolbar is being created. It adds our custom menu.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.webview, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val backItem = toolbar.menu.findItem(R.id.action_web_back)
        val forwardItem = toolbar.menu.findItem(R.id.action_web_forward)
        backItem?.isEnabled = webview.canGoBack()
        forwardItem?.isEnabled = webview.canGoForward()
        val hasHistory = webview.canGoBack() || webview.canGoForward()
        backItem?.isVisible = hasHistory
        forwardItem?.isVisible = hasHistory
        val tintColor = getResourceColor(R.attr.actionBarTintColor)
        val translucentWhite = ColorUtils.setAlphaComponent(tintColor, 127)
        backItem.icon?.setTint(if (webview.canGoBack()) tintColor else translucentWhite)
        forwardItem?.icon?.setTint(if (webview.canGoForward()) tintColor else translucentWhite)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onBackPressed() {
        if (webview.canGoBack()) webview.goBack()
        else super.onBackPressed()
    }

    /**
     * Called when an item of the options menu was clicked. Used to handle clicks on our menu
     * entries.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_web_back -> webview.goBack()
            R.id.action_web_forward -> webview.goForward()
            R.id.action_web_share -> shareWebpage()
            R.id.action_web_browser -> openInBrowser()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun shareWebpage() {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, webview.url)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        } catch (e: Exception) {
            toast(e.message)
        }
    }

    private fun openInBrowser() {
        openInBrowser(webview.url)
    }
}
