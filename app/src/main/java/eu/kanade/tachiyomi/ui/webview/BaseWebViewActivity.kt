package eu.kanade.tachiyomi.ui.webview

import android.annotation.SuppressLint
import android.app.assist.AssistContent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.WebviewActivityBinding
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.system.ThemeUtil
import eu.kanade.tachiyomi.util.system.getPrefTheme
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.view.marginBottom
import eu.kanade.tachiyomi.util.view.setStyle
import eu.kanade.tachiyomi.util.view.updatePadding

open class BaseWebViewActivity : BaseActivity<WebviewActivityBinding>() {

    private var bundle: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = WebviewActivityBinding.inflate(layoutInflater)
        delegate.localNightMode = preferences.nightMode().get()
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            super.onBackPressed()
        }
        val tintColor = getResourceColor(R.attr.actionBarTintColor)
        binding.toolbar.navigationIcon?.setTint(tintColor)
        binding.toolbar.navigationIcon?.setTint(tintColor)
        binding.toolbar.overflowIcon?.mutate()
        binding.toolbar.overflowIcon?.setTint(tintColor)

        val container: ViewGroup = findViewById(R.id.web_view_layout)
        val content: LinearLayout = binding.webLinearLayout
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
                0,
                insets.systemWindowInsetTop,
                0,
                insets.systemWindowInsetBottom
            )
        }
        binding.swipeRefresh.setStyle()
        binding.swipeRefresh.setOnRefreshListener {
            refreshPage()
        }

        window.statusBarColor = ColorUtils.setAlphaComponent(
            getResourceColor(R.attr.colorSurface),
            255
        )

        content.setOnApplyWindowInsetsListener { v, insets ->
            // if pure white theme on a device that does not support dark status bar
            /*if (getResourceColor(android.R.attr.statusBarColor) != Color.TRANSPARENT)
                window.statusBarColor = Color.BLACK
            else window.statusBarColor = getResourceColor(R.attr.colorPrimary)*/
            window.navigationBarColor = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Color.BLACK
            } else {
                getResourceColor(R.attr.colorPrimaryVariant)
            }
            v.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                0
            )
            if (Build.VERSION.SDK_INT >= 26 && !isInNightMode()) {
                content.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            insets
        }

        binding.swipeRefresh.isEnabled = false

        if (bundle == null) {
            binding.webview.setDefaultSettings()

            binding.webview.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    binding.progressBar.isVisible = true
                    binding.progressBar.progress = newProgress
                    if (newProgress == 100) {
                        binding.progressBar.isInvisible = true
                    }
                    super.onProgressChanged(view, newProgress)
                }
            }
            val marginB = binding.webview.marginBottom
            binding.swipeRefresh.setOnApplyWindowInsetsListener { v, insets ->
                val bottomInset = insets.systemWindowInsetBottom
                v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = marginB + bottomInset
                }
                insets
            }
        } else {
            bundle?.let {
                binding.webview.restoreState(it)
            }
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent?) {
        super.onProvideAssistContent(outContent)
        binding.webview.url?.let { outContent?.webUri = it.toUri() }
    }

    private fun refreshPage() {
        binding.swipeRefresh.isRefreshing = true
        binding.webview.reload()
    }

    @SuppressLint("ResourceType")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val lightMode = !isInNightMode()
        val prefTheme = getPrefTheme(preferences)
        setTheme(prefTheme.styleRes)
        if (!lightMode && preferences.themeDarkAmoled().get()) {
            setTheme(R.style.ThemeOverlay_Tachiyomi_Amoled)
            if (ThemeUtil.isColoredTheme(prefTheme)) {
                setTheme(R.style.ThemeOverlay_Tachiyomi_AllBlue)
            }
        }
        val themeValue = TypedValue()
        theme.resolveAttribute(android.R.attr.windowLightStatusBar, themeValue, true)

        val wic = WindowInsetsControllerCompat(window, window.decorView)
        wic.isAppearanceLightStatusBars = themeValue.data == -1
        wic.isAppearanceLightNavigationBars = themeValue.data == -1

        val attrs = theme.obtainStyledAttributes(
            intArrayOf(
                R.attr.colorSurface,
                R.attr.actionBarTintColor,
                R.attr.colorPrimaryVariant,
            )
        )
        val colorSurface = attrs.getColor(0, 0)
        val actionBarTintColor = attrs.getColor(1, 0)
        val colorPrimaryVariant = attrs.getColor(2, 0)
        attrs.recycle()

        window.statusBarColor = ColorUtils.setAlphaComponent(colorSurface, 255)
        binding.toolbar.setBackgroundColor(colorSurface)
        binding.toolbar.popupTheme =
            if (lightMode) R.style.ThemeOverlay_Material3
            else R.style.ThemeOverlay_Material3_Dark
        binding.toolbar.setNavigationIconTint(actionBarTintColor)
        binding.toolbar.overflowIcon?.mutate()
        binding.toolbar.setTitleTextColor(actionBarTintColor)
        binding.toolbar.overflowIcon?.setTint(actionBarTintColor)
        binding.swipeRefresh.setColorSchemeColors(actionBarTintColor)
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(colorPrimaryVariant)

        window.navigationBarColor =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || !lightMode) colorPrimaryVariant
            else Color.BLACK

        binding.webLinearLayout.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    override fun onBackPressed() {
        if (binding.webview.canGoBack()) binding.webview.goBack()
        else super.onBackPressed()
    }
}