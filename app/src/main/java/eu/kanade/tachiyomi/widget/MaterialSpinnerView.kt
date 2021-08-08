package eu.kanade.tachiyomi.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.FrameLayout
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.forEach
import androidx.core.view.get
import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.MaterialSpinnerViewBinding
import eu.kanade.tachiyomi.util.lang.tintText
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.toast
import kotlin.math.max

class MaterialSpinnerView @JvmOverloads constructor(context: Context, attrs: AttributeSet?) :
    FrameLayout(context, attrs) {

    private var entries = emptyList<String>()
    var selectedPosition = 0
        private set
    private var pref: Preference<Int>? = null
    private var prefOffset = 0
    private var popup: PopupMenu? = null
    var title: CharSequence
        get() {
            return binding.titleView.text
        }
        set(value) {
            binding.titleView.text = value
        }

    private val blendedAccent = ColorUtils.blendARGB(
        context.getResourceColor(android.R.attr.colorSecondary),
        context.getResourceColor(R.attr.colorOnBackground),
        0.5f
    )

    var onItemSelectedListener: ((Int) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) {
                popup = makeSettingsPopup()
                setOnTouchListener(popup?.dragToOpenListener)
                setOnClickListener {
                    popup?.show()
                }
            }
        }

    private val binding = MaterialSpinnerViewBinding.inflate(
        LayoutInflater.from(context),
        this,
        false
    )

    init {
        addView(binding.root)
        val a = context.obtainStyledAttributes(attrs, R.styleable.MaterialSpinnerView, 0, 0)

        val str = a.getString(R.styleable.MaterialSpinnerView_title) ?: ""
        title = str

        val entries = (a.getTextArray(R.styleable.MaterialSpinnerView_android_entries) ?: emptyArray()).map { it.toString() }
        this.entries = entries

        val maxLines = a.getInt(R.styleable.MaterialSpinnerView_android_maxLines, Int.MAX_VALUE)
        binding.titleView.maxLines = maxLines
        this.entries = entries

        binding.detailView.text = entries.firstOrNull().orEmpty()

        if (entries.isNotEmpty()) {
            popup = makeSettingsPopup()
            setOnTouchListener(popup?.dragToOpenListener)
            setOnClickListener {
                popup?.show()
            }
        }

        a.recycle()
    }

    fun setEntries(entries: List<String>) {
        this.entries = entries
        popup = makeSettingsPopup()
        setOnTouchListener(popup?.dragToOpenListener)
        setOnClickListener {
            popup?.show()
        }
    }

    fun setSelection(selection: Int) {
        if (selectedPosition >= 0 && selectedPosition < popup?.menu?.size() ?: 0) {
            popup?.menu?.get(selectedPosition)?.let {
                it.icon = ContextCompat.getDrawable(context, R.drawable.ic_blank_24dp)
                it.title = entries[selectedPosition]
            }
        }
        selectedPosition = selection
        if (selectedPosition >= 0 && selectedPosition < popup?.menu?.size() ?: 0) {
            popup?.menu?.get(selectedPosition)?.let {
                it.icon = tintedCheck()
                it.title = it.title?.tintText(blendedAccent)
            }
        }
        binding.detailView.text = entries.getOrNull(selection).orEmpty()
    }

    fun setDisabledState(@StringRes messageRes: Int = 0) {
        alpha = 0.5f
        popup = null
        setOnTouchListener(null)
        setOnClickListener {
            if (messageRes != 0) {
                context.toast(messageRes)
            }
        }
    }

    fun bindToPreference(pref: Preference<Int>, offset: Int = 0, block: ((Int) -> Unit)? = null) {
        setSelection(pref.get() - offset)
        this.pref = pref
        prefOffset = offset
        popup = makeSettingsPopup(pref, prefOffset, block)
        setOnTouchListener(popup?.dragToOpenListener)
        setOnClickListener {
            popup?.show()
        }
    }

    inline fun <reified T : Enum<T>> bindToPreference(pref: Preference<T>) {
        val enumConstants = T::class.java.enumConstants
        enumConstants?.indexOf(pref.get())?.let { setSelection(it) }
        val popup = makeSettingsPopup(pref)
        setOnTouchListener(popup.dragToOpenListener)
        setOnClickListener {
            popup.show()
        }
    }

    fun bindToIntPreference(
        pref: Preference<Int>,
        @ArrayRes intValuesResource: Int,
        block: ((Int) -> Unit)? = null
    ) {
        this.pref = pref
        prefOffset = 0
        val intValues = resources.getStringArray(intValuesResource).map { it.toIntOrNull() }
        setSelection(max(0, intValues.indexOf(pref.get())))
        popup = makeSettingsPopup(pref, intValues, block)
        setOnTouchListener(popup?.dragToOpenListener)
        setOnClickListener {
            popup?.show()
        }
    }

    inline fun <reified T : Enum<T>> makeSettingsPopup(preference: Preference<T>): PopupMenu {
        val popup = popup()

        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            val pos = menuClicked(menuItem)
            onItemSelectedListener?.invoke(pos)
            true
        }
        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            val enumConstants = T::class.java.enumConstants
            val pos = menuClicked(menuItem)
            enumConstants?.get(pos)?.let { preference.set(it) }
            true
        }
        return popup
    }

    private fun makeSettingsPopup(
        preference: Preference<Int>,
        intValues: List<Int?>,
        block: ((Int) -> Unit)? = null
    ): PopupMenu {
        val popup = popup()
        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            val pos = menuClicked(menuItem)
            preference.set(intValues[pos] ?: 0)
            block?.invoke(pos)
            true
        }
        return popup
    }

    private fun makeSettingsPopup(
        preference: Preference<Int>,
        offset: Int = 0,
        block: ((Int) -> Unit)? = null
    ): PopupMenu {
        val popup = popup()
        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            val pos = menuClicked(menuItem)
            preference.set(pos + offset)
            block?.invoke(pos)
            true
        }
        return popup
    }

    private fun makeSettingsPopup(): PopupMenu {
        val popup = popup()

        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            val pos = menuClicked(menuItem)
            onItemSelectedListener?.invoke(pos)
            true
        }
        return popup
    }

    fun menuClicked(menuItem: MenuItem): Int {
        val pos = menuItem.itemId
        setSelection(pos)
        return pos
    }

    @SuppressLint("RestrictedApi")
    fun popup(): PopupMenu {
        val popup = PopupMenu(context, this, Gravity.END)
        entries.forEachIndexed { index, entry ->
            popup.menu.add(0, index, 0, entry)
        }
        if (popup.menu is MenuBuilder) {
            val m = popup.menu as MenuBuilder
            m.setOptionalIconsVisible(true)
        }
        popup.menu.forEach {
            it.icon = ContextCompat.getDrawable(context, R.drawable.ic_blank_24dp)
        }
        popup.menu.getItem(selectedPosition)?.let { menuItem ->
            menuItem.icon = tintedCheck()
            menuItem.title =
                menuItem.title?.tintText(blendedAccent)
        }
        this.popup = popup
        return popup
    }

    private fun tintedCheck(): Drawable? {
        return ContextCompat.getDrawable(context, R.drawable.ic_check_24dp)?.mutate()?.apply {
            setTint(blendedAccent)
        }
    }
}
