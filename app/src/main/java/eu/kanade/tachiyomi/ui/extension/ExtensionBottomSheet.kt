package eu.kanade.tachiyomi.ui.extension

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.ExtensionsBottomSheetBinding
import eu.kanade.tachiyomi.databinding.RecyclerWithScrollerBinding
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.InstalledExtensionsOrder
import eu.kanade.tachiyomi.ui.extension.details.ExtensionDetailsController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.migration.BaseMigrationInterface
import eu.kanade.tachiyomi.ui.migration.MangaAdapter
import eu.kanade.tachiyomi.ui.migration.MangaItem
import eu.kanade.tachiyomi.ui.migration.SourceAdapter
import eu.kanade.tachiyomi.ui.migration.SourceItem
import eu.kanade.tachiyomi.ui.migration.manga.design.PreMigrationController
import eu.kanade.tachiyomi.ui.source.BrowseController
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.popupMenu
import eu.kanade.tachiyomi.util.view.smoothScrollToTop
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionBottomSheet @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs),
    ExtensionAdapter.OnButtonClickListener,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    ExtensionTrustDialog.Listener,
    SourceAdapter.OnAllClickListener,
    BaseMigrationInterface {

    var sheetBehavior: BottomSheetBehavior<*>? = null

    var shouldCallApi = false

    /**
     * Adapter containing the list of extensions
     */
    private var extAdapter: ExtensionAdapter? = null
    private var migAdapter: FlexibleAdapter<IFlexible<*>>? = null

    val adapters
        get() = listOf(extAdapter, migAdapter)

    val presenter = ExtensionBottomPresenter()
    var currentSourceTitle: String? = null

    private var extensions: List<ExtensionItem> = emptyList()
    var canExpand = false
    private lateinit var binding: ExtensionsBottomSheetBinding

    lateinit var controller: BrowseController
    var boundViews = arrayListOf<RecyclerWithScrollerView>()

    val extensionFrameLayout: RecyclerWithScrollerView?
        get() = binding.pager.findViewWithTag("TabbedRecycler0") as? RecyclerWithScrollerView
    val migrationFrameLayout: RecyclerWithScrollerView?
        get() = binding.pager.findViewWithTag("TabbedRecycler1") as? RecyclerWithScrollerView

    var isExpanding = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = ExtensionsBottomSheetBinding.bind(this)
    }

    fun onCreate(controller: BrowseController) {
        // Initialize adapter, scroll listener and recycler views
        presenter.attachView(this)
        extAdapter = ExtensionAdapter(this)
        extAdapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        if (migAdapter == null) {
            migAdapter = SourceAdapter(this)
        }
        migAdapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        sheetBehavior = BottomSheetBehavior.from(this)
        // Create recycler and set adapter.

        binding.pager.adapter = TabbedSheetAdapter()
        binding.tabs.setupWithViewPager(binding.pager)
        this.controller = controller
        binding.pager.doOnApplyWindowInsetsCompat { _, insets, _ ->
            val bottomBar = controller.activityBinding?.bottomNav
            val bottomH = bottomBar?.height ?: insets.getInsets(systemBars()).bottom
            extensionFrameLayout?.binding?.recycler?.updatePaddingRelative(bottom = bottomH)
            migrationFrameLayout?.binding?.recycler?.updatePaddingRelative(bottom = bottomH)
        }
        binding.tabs.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    isExpanding = !sheetBehavior.isExpanded()
                    if (canExpand) {
                        this@ExtensionBottomSheet.sheetBehavior?.expand()
                    }
                    this@ExtensionBottomSheet.controller.updateTitleAndMenu()
                    when (tab?.position) {
                        0 -> extensionFrameLayout
                        else -> migrationFrameLayout
                    }?.binding?.recycler?.isNestedScrollingEnabled = true
                    when (tab?.position) {
                        0 -> extensionFrameLayout
                        else -> migrationFrameLayout
                    }?.binding?.recycler?.requestLayout()
                    sheetBehavior?.isDraggable = true
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {
                    when (tab?.position) {
                        0 -> extensionFrameLayout
                        else -> migrationFrameLayout
                    }?.binding?.recycler?.isNestedScrollingEnabled = false
                    if (tab?.position == 1) {
                        presenter.deselectSource()
                    }
                }

                override fun onTabReselected(tab: TabLayout.Tab?) {
                    isExpanding = !sheetBehavior.isExpanded()
                    this@ExtensionBottomSheet.sheetBehavior?.expand()
                    when (tab?.position) {
                        0 -> extensionFrameLayout
                        else -> migrationFrameLayout
                    }?.binding?.recycler?.isNestedScrollingEnabled = true
                    sheetBehavior?.isDraggable = true
                    if (!isExpanding) {
                        when (tab?.position) {
                            0 -> extensionFrameLayout
                            else -> migrationFrameLayout
                        }?.binding?.recycler?.smoothScrollToTop()
                    }
                }
            },
        )
        presenter.onCreate()
        updateExtTitle()

        binding.sheetLayout.setOnClickListener {
            if (!sheetBehavior.isExpanded()) {
                sheetBehavior?.expand()
                fetchOnlineExtensionsIfNeeded()
            } else {
                sheetBehavior?.collapse()
            }
        }
        presenter.getExtensionUpdateCount()
    }

    fun isOnView(view: View): Boolean {
        return "TabbedRecycler${binding.pager.currentItem}" == view.tag
    }

    fun updatedNestedRecyclers() {
        listOf(extensionFrameLayout, migrationFrameLayout).forEachIndexed { index, recyclerWithScrollerBinding ->
            recyclerWithScrollerBinding?.binding?.recycler?.isNestedScrollingEnabled = binding.pager.currentItem == index
        }
    }

    fun fetchOnlineExtensionsIfNeeded() {
        if (shouldCallApi) {
            presenter.findAvailableExtensions()
            shouldCallApi = false
        }
    }

    fun updateExtTitle() {
        val extCount = presenter.getExtensionUpdateCount()
        if (extCount > 0) {
            binding.tabs.getTabAt(0)?.orCreateBadge
        } else {
            binding.tabs.getTabAt(0)?.removeBadge()
        }
    }

    override fun onButtonClick(position: Int) {
        val extension = (extAdapter?.getItem(position) as? ExtensionItem)?.extension ?: return
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

    override fun onCancelClick(position: Int) {
        val extension = (extAdapter?.getItem(position) as? ExtensionItem) ?: return
        presenter.cancelExtensionInstall(extension)
    }

    override fun onUpdateAllClicked(position: Int) {
        (controller.activity as? MainActivity)?.showNotificationPermissionPrompt()
        if (!presenter.preferences.useShizukuForExtensions() &&
            !presenter.preferences.hasPromptedBeforeUpdateAll().get()
        ) {
            controller.activity!!.materialAlertDialog()
                .setTitle(R.string.update_all)
                .setMessage(R.string.some_extensions_may_prompt)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    presenter.preferences.hasPromptedBeforeUpdateAll().set(true)
                    updateAllExtensions(position)
                }
                .show()
        } else {
            updateAllExtensions(position)
        }
    }

    override fun onExtSortClicked(view: TextView, position: Int) {
        view.popupMenu(
            InstalledExtensionsOrder.values().map { it.value to it.nameRes },
            presenter.preferences.installedExtensionsOrder().get(),
        ) {
            presenter.preferences.installedExtensionsOrder().set(itemId)
            extAdapter?.installedSortOrder = itemId
            view.setText(InstalledExtensionsOrder.fromValue(itemId).nameRes)
            presenter.refreshExtensions()
        }
    }

    private fun updateAllExtensions(position: Int) {
        val header = (extAdapter?.getSectionHeader(position)) as? ExtensionGroupItem ?: return
        val items = extAdapter?.getSectionItemPositions(header)
        val extensions = items?.mapNotNull {
            val extItem = (extAdapter?.getItem(it) as? ExtensionItem) ?: return
            val extension = (extAdapter?.getItem(it) as? ExtensionItem)?.extension ?: return
            if ((extItem.installStep == null || extItem.installStep == InstallStep.Error) &&
                extension is Extension.Installed && extension.hasUpdate
            ) {
                extension
            } else {
                null
            }
        }.orEmpty()
        presenter.updateExtensions(extensions)
    }

    override fun onItemClick(view: View?, position: Int): Boolean {
        when (binding.tabs.selectedTabPosition) {
            0 -> {
                val extension =
                    (extAdapter?.getItem(position) as? ExtensionItem)?.extension ?: return false
                if (extension is Extension.Installed) {
                    openDetails(extension)
                } else if (extension is Extension.Untrusted) {
                    openTrustDialog(extension)
                }
            }
            else -> {
                val item = migAdapter?.getItem(position) ?: return false

                if (item is MangaItem) {
                    PreMigrationController.navigateToMigration(
                        Injekt.get<PreferencesHelper>().skipPreMigration().get(),
                        controller.router,
                        listOf(item.manga.id!!),
                    )
                } else if (item is SourceItem) {
                    presenter.setSelectedSource(item.source)
                }
            }
        }
        return false
    }

    override fun onItemLongClick(position: Int) {
        if (binding.tabs.selectedTabPosition == 0) {
            val extension = (extAdapter?.getItem(position) as? ExtensionItem)?.extension ?: return
            if (extension is Extension.Installed || extension is Extension.Untrusted) {
                uninstallExtension(extension.pkgName)
            }
        }
    }

    override fun onAllClick(position: Int) {
        val item = migAdapter?.getItem(position) as? SourceItem ?: return

        val sourceMangas =
            presenter.mangaItems[item.source.id]?.mapNotNull { it.manga.id }?.toList()
                ?: emptyList()
        PreMigrationController.navigateToMigration(
            Injekt.get<PreferencesHelper>().skipPreMigration().get(),
            controller.router,
            sourceMangas,
        )
    }

    private fun openDetails(extension: Extension.Installed) {
        val controller = ExtensionDetailsController(extension.pkgName)
        this.controller.router.pushController(controller.withFadeTransaction())
    }

    private fun openTrustDialog(extension: Extension.Untrusted) {
        ExtensionTrustDialog(this, extension.signatureHash, extension.pkgName)
            .showDialog(controller.router)
    }

    fun setExtensions(extensions: List<ExtensionItem>, updateController: Boolean = true) {
        this.extensions = extensions
        if (updateController) {
            controller.presenter.updateSources()
        }
        drawExtensions()
    }

    override fun setMigrationSources(sources: List<SourceItem>) {
        currentSourceTitle = null
        val changingAdapters = migAdapter !is SourceAdapter
        if (migAdapter !is SourceAdapter) {
            migAdapter = SourceAdapter(this)
            migrationFrameLayout?.onBind(migAdapter!!)
            migAdapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
        migAdapter?.updateDataSet(sources, changingAdapters)
        controller.updateTitleAndMenu()
    }

    override fun setMigrationManga(title: String, manga: List<MangaItem>?) {
        currentSourceTitle = title
        val changingAdapters = migAdapter !is MangaAdapter
        if (migAdapter !is MangaAdapter) {
            migAdapter = MangaAdapter(this, presenter.preferences.outlineOnCovers().get())
            migrationFrameLayout?.onBind(migAdapter!!)
            migAdapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
        migAdapter?.updateDataSet(manga, changingAdapters)
        controller.updateTitleAndMenu()
    }

    fun drawExtensions() {
        if (controller.extQuery.isNotBlank()) {
            extAdapter?.updateDataSet(
                extensions.filter {
                    it.extension.name.contains(controller.extQuery, ignoreCase = true)
                },
            )
        } else {
            extAdapter?.updateDataSet(extensions)
        }
        updateExtTitle()
        updateExtUpdateAllButton()
    }

    fun canGoBack(): Boolean {
        if (binding.tabs.selectedTabPosition == 1 && migAdapter is MangaAdapter) {
            presenter.deselectSource()
            return false
        }
        if (binding.sheetToolbar.hasExpandedActionView()) {
            binding.sheetToolbar.collapseActionView()
            return false
        }
        return true
    }

    fun downloadUpdate(item: ExtensionItem) {
        extAdapter?.updateItem(item, item.installStep)
        updateExtUpdateAllButton()
    }

    private fun updateExtUpdateAllButton() {
        val updateHeader =
            extAdapter?.headerItems?.find { it is ExtensionGroupItem && it.canUpdate != null } as? ExtensionGroupItem
                ?: return
        val items = extAdapter?.getSectionItemPositions(updateHeader) ?: return
        updateHeader.canUpdate = items.any {
            val extItem = (extAdapter?.getItem(it) as? ExtensionItem) ?: return
            extItem.installStep == null || extItem.installStep == InstallStep.Error
        }
        extAdapter?.updateItem(updateHeader)
    }

    override fun trustSignature(signatureHash: String) {
        presenter.trustSignature(signatureHash)
    }

    override fun uninstallExtension(pkgName: String) {
        presenter.uninstallExtension(pkgName)
    }

    fun onDestroy() {
        presenter.onDestroy()
    }

    private inner class TabbedSheetAdapter : RecyclerViewPagerAdapter() {

        override fun getCount(): Int {
            return 2
        }

        override fun getPageTitle(position: Int): CharSequence {
            return context.getString(
                when (position) {
                    0 -> R.string.extensions
                    else -> R.string.migration
                },
            )
        }

        /**
         * Creates a new view for this adapter.
         *
         * @return a new view.
         */
        override fun createView(container: ViewGroup): View {
            val binding = RecyclerWithScrollerBinding.inflate(
                LayoutInflater.from(container.context),
                container,
                false,
            )
            val view: RecyclerWithScrollerView = binding.root
            val height = this@ExtensionBottomSheet.controller.activityBinding?.bottomNav?.height
                ?: view.rootWindowInsetsCompat?.getInsets(systemBars())?.bottom ?: 0
            view.setUp(this@ExtensionBottomSheet, binding, height)

            return view
        }

        /**
         * Binds a view with a position.
         *
         * @param view the view to bind.
         * @param position the position in the adapter.
         */
        override fun bindView(view: View, position: Int) {
            (view as RecyclerWithScrollerView).onBind(adapters[position]!!)
            view.setTag("TabbedRecycler$position")
            boundViews.add(view)
        }

        /**
         * Recycles a view.
         *
         * @param view the view to recycle.
         * @param position the position in the adapter.
         */
        override fun recycleView(view: View, position: Int) {
            // (view as RecyclerWithScrollerView).onRecycle()
            boundViews.remove(view)
        }

        /**
         * Returns the position of the view.
         */
        override fun getItemPosition(obj: Any): Int {
            val view = (obj as? RecyclerWithScrollerView) ?: return POSITION_NONE
            val index = adapters.indexOfFirst { it == view.binding?.recycler?.adapter }
            return if (index == -1) POSITION_NONE else index
        }
    }
}
