package eu.kanade.tachiyomi.ui.extension

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.source.SourceController
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.RecyclerWindowInsetsListener
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsets
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.android.synthetic.main.extensions_bottom_sheet.view.*

class ExtensionBottomSheet @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs),
    ExtensionAdapter.OnButtonClickListener,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    ExtensionTrustDialog.Listener {

    var sheetBehavior: BottomSheetBehavior<*>? = null

    var shouldCallApi = false

    /**
     * Adapter containing the list of extensions
     */
    private var adapter: FlexibleAdapter<IFlexible<*>>? = null

    val presenter = ExtensionBottomPresenter(this)

    private var extensions: List<ExtensionItem> = emptyList()

    lateinit var controller: SourceController

    fun onCreate(controller: SourceController) {
        // Initialize adapter, scroll listener and recycler views
        adapter = ExtensionAdapter(this)
        sheetBehavior = BottomSheetBehavior.from(this)
        // Create recycler and set adapter.
        ext_recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        ext_recycler.adapter = adapter
        ext_recycler.setHasFixedSize(true)
        ext_recycler.addItemDecoration(ExtensionDividerItemDecoration(context))
        ext_recycler.setOnApplyWindowInsetsListener(RecyclerWindowInsetsListener)
        adapter?.fastScroller = fast_scroller
        this.controller = controller
        presenter.onCreate()
        updateExtTitle()

        val attrsArray = intArrayOf(android.R.attr.actionBarSize)
        val array = context.obtainStyledAttributes(attrsArray)
        val headerHeight = array.getDimensionPixelSize(0, 0)
        array.recycle()
        ext_recycler_layout.doOnApplyWindowInsets { v, windowInsets, _ ->
            v.updateLayoutParams<MarginLayoutParams> {
                topMargin = windowInsets.systemWindowInsetTop + headerHeight -
                    (sheet_layout.height)
            }
        }
        sheet_layout.setOnClickListener {
            if (!sheetBehavior.isExpanded()) {
                sheetBehavior?.expand()
                fetchOnlineExtensionsIfNeeded()
            } else {
                sheetBehavior?.collapse()
            }
        }
        presenter.getExtensionUpdateCount()
    }

    fun fetchOnlineExtensionsIfNeeded() {
        if (shouldCallApi) {
            presenter.findAvailableExtensions()
            shouldCallApi = false
        }
    }

    fun updateExtTitle() {
        val extCount = presenter.getExtensionUpdateCount()
        title_text.text = if (extCount == 0) context.getString(R.string.extensions)
        else resources.getQuantityString(
            R.plurals.extension_updates_available,
            extCount,
            extCount
        )

        title_text.setTextColor(
            context.getResourceColor(
                if (extCount == 0) R.attr.actionBarTintColor else R.attr.colorAccent
            )
        )
    }

    override fun onButtonClick(position: Int) {
        val extension = (adapter?.getItem(position) as? ExtensionItem)?.extension ?: return
        when (extension) {
            is Extension.Installed -> {
                if (!extension.hasUpdate) {
                    openDetails(extension)
                } else {
                    presenter.updateExtension(extension)
                }
            }
            is Extension.Available -> {
                presenter.installExtension(extension)
            }
            is Extension.Untrusted -> {
                openTrustDialog(extension)
            }
        }
    }

    override fun onItemClick(view: View?, position: Int): Boolean {
        val extension = (adapter?.getItem(position) as? ExtensionItem)?.extension ?: return false
        if (extension is Extension.Installed) {
            openDetails(extension)
        } else if (extension is Extension.Untrusted) {
            openTrustDialog(extension)
        }

        return false
    }

    override fun onItemLongClick(position: Int) {
        val extension = (adapter?.getItem(position) as? ExtensionItem)?.extension ?: return
        if (extension is Extension.Installed || extension is Extension.Untrusted) {
            uninstallExtension(extension.pkgName)
        }
    }

    private fun openDetails(extension: Extension.Installed) {
        val controller = ExtensionDetailsController(extension.pkgName)
        this.controller.router.pushController(controller.withFadeTransaction())
    }

    private fun openTrustDialog(extension: Extension.Untrusted) {
        ExtensionTrustDialog(this, extension.signatureHash, extension.pkgName)
            .showDialog(controller.router)
    }

    fun setExtensions(extensions: List<ExtensionItem>) {
        this.extensions = extensions
        controller.presenter.updateSources()
        drawExtensions()
    }

    fun drawExtensions() {
        if (!controller.extQuery.isBlank()) {
            adapter?.updateDataSet(
                extensions.filter {
                    it.extension.name.contains(controller.extQuery, ignoreCase = true)
                }
            )
        } else {
            adapter?.updateDataSet(extensions)
        }
        updateExtTitle()
    }

    fun downloadUpdate(item: ExtensionItem) {
        adapter?.updateItem(item, item.installStep)
    }

    override fun trustSignature(signatureHash: String) {
        presenter.trustSignature(signatureHash)
    }

    override fun uninstallExtension(pkgName: String) {
        presenter.uninstallExtension(pkgName)
    }
}
