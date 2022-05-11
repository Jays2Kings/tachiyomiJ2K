package eu.kanade.tachiyomi.ui.base.controller

import android.app.Activity
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.removeQueryListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import timber.log.Timber

abstract class BaseController<VB : ViewBinding>(bundle: Bundle? = null) :
    Controller(bundle) {

    lateinit var binding: VB
    lateinit var viewScope: CoroutineScope
    var isDragging = false

    val isBindingInitialized get() = this::binding.isInitialized
    init {
        addLifecycleListener(
            object : LifecycleListener() {
                override fun postCreateView(controller: Controller, view: View) {
                    onViewCreated(view)
                }

                override fun preCreateView(controller: Controller) {
                    viewScope = MainScope()
                    Timber.d("Create view for ${controller.instance()}")
                }

                override fun preAttach(controller: Controller, view: View) {
                    Timber.d("Attach view for ${controller.instance()}")
                }

                override fun preDetach(controller: Controller, view: View) {
                    Timber.d("Detach view for ${controller.instance()}")
                }

                override fun preDestroyView(controller: Controller, view: View) {
                    viewScope.cancel()
                    Timber.d("Destroy view for ${controller.instance()}")
                }
            },
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedViewState: Bundle?): View {
        binding = createBinding(inflater)
        return binding.root
    }

    abstract fun createBinding(inflater: LayoutInflater): VB

    open fun onViewCreated(view: View) { }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        if (type.isEnter) {
            setTitle()
        } else {
            removeQueryListener()
        }
        setHasOptionsMenu(type.isEnter)
        super.onChangeStarted(handler, type)
    }

    open fun getTitle(): String? {
        return null
    }

    open fun getSearchTitle(): String? {
        return null
    }

    open fun getBigIcon(): Drawable? {
        return null
    }

    open val mainRecycler: RecyclerView?
        get() = null

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        removeQueryListener(false)
    }

    fun setTitle() {
        var parentController = parentController
        while (parentController != null) {
            if (parentController is BaseController<*> && parentController.getTitle() != null) {
                return
            }
            parentController = parentController.parentController
        }

        if (isControllerVisible) {
            (activity as? AppCompatActivity)?.title = getTitle()
            (activity as? MainActivity)?.searchTitle = getSearchTitle()
            val icon = getBigIcon()
            activityBinding?.bigIconLayout?.isVisible = icon != null
            if (icon != null) {
                activityBinding?.bigIcon?.setImageDrawable(getBigIcon())
            } else {
                activityBinding?.bigIcon?.setImageDrawable(getBigIcon())
            }
        }
    }

    private fun Controller.instance(): String {
        return "${javaClass.simpleName}@${Integer.toHexString(hashCode())}"
    }

    /**
     * Workaround for buggy menu item layout after expanding/collapsing an expandable item like a SearchView.
     * This method should be removed when fixed upstream.
     * Issue link: https://issuetracker.google.com/issues/37657375
     */
    var expandActionViewFromInteraction = false
    fun MenuItem.fixExpand(onExpand: ((MenuItem) -> Boolean)? = null, onCollapse: ((MenuItem) -> Boolean)? = null) {
        setOnActionExpandListener(
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    hideItemsIfExpanded(item, activityBinding?.searchToolbar?.menu, true)
                    return onExpand?.invoke(item) ?: true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    activity?.invalidateOptionsMenu()

                    return onCollapse?.invoke(item) ?: true
                }
            },
        )

        if (expandActionViewFromInteraction) {
            expandActionViewFromInteraction = false
            expandActionView()
        }
    }

    open fun onActionViewExpand(item: MenuItem?) { }
    open fun onActionViewCollapse(item: MenuItem?) { }
    open fun onSearchActionViewLongClickQuery(): String? = null

    fun hideItemsIfExpanded(searchItem: MenuItem?, menu: Menu?, isExpanded: Boolean = false) {
        menu ?: return
        searchItem ?: return
        if (searchItem.isActionViewExpanded || isExpanded) {
            menu.forEach { it.isVisible = false }
        }
    }

    fun MenuItem.fixExpandInvalidate() {
        fixExpand { invalidateMenuOnExpand() }
    }

    /**
     * Workaround for menu items not disappearing when expanding an expandable item like a SearchView.
     * [expandActionViewFromInteraction] should be set to true in [onOptionsItemSelected] when the expandable item is selected
     * This method should be called as part of [MenuItem.OnActionExpandListener.onMenuItemActionExpand]
     */
    fun invalidateMenuOnExpand(): Boolean {
        return if (expandActionViewFromInteraction) {
            activity?.invalidateOptionsMenu()
            false
        } else {
            true
        }
    }
}
