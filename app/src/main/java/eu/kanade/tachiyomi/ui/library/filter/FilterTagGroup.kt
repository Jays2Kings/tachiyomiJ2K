package eu.kanade.tachiyomi.ui.library.filter

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.f2prateek.rx.preferences.Preference
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.filter_buttons.view.*

class FilterTagGroup@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout
(context, attrs) {

    private var listener: FilterTagGroupListener? = null

    var itemCount = 0
        private set

    private var root: ViewGroup? = null

    private val buttons by lazy { arrayOf(firstButton, secondButton, thirdButton, fourthButton) }
    private val separators by lazy { arrayOf(separator1, separator2, separator3) }

    override fun isActivated(): Boolean {
        return buttons.any { it.isActivated }
    }

    fun nameOf(index: Int): String? = buttons.getOrNull(index)?.text as? String

    fun setup(root: ViewGroup, firstText: Int, vararg extra: Int?) {
        val text1 = context.getString(firstText)
        val strings = extra.mapNotNull { if (it != null) context.getString(it) else null }
        setup(root, text1, extra = *strings.toTypedArray())
    }

    fun setup(root: ViewGroup, firstText: String, vararg extra: String?) {
        listener = root as? FilterTagGroupListener
        (layoutParams as? MarginLayoutParams)?.rightMargin = 5.dpToPx
        (layoutParams as? MarginLayoutParams)?.leftMargin = 5.dpToPx
        firstButton.text = firstText
        val extras = (extra.toList() + listOf<String?>(null, null, null)).take(separators.size)
        extras.forEachIndexed { index, text ->
            buttons[index + 1].text = text
            separators[index].visibleIf(text != null)
            buttons[index + 1].visibleIf(text != null)
        }
        itemCount = buttons.count { !it.text.isNullOrBlank() }
        this.root = root
        buttons.forEachIndexed { index, textView ->
            textView.setOnClickListener { toggleButton(index) }
        }
    }

    var state: Int
        get() = buttons.indexOfFirst { it.isActivated }
        set(index) = toggleButton(index, false)

    fun setState(preference: Preference<Int>) {
        state = preference.getOrDefault() - 1
    }

    fun setState(text: String) {
        state = buttons.indexOfFirst { it.text == text && it.visibility == View.VISIBLE }
    }

    fun reset() {
        buttons.forEach {
            it.isActivated = false
        }
        for (i in 0 until itemCount) {
            buttons[i].visible()
            buttons[i].setTextColor(context.getResourceColor(android.R.attr.textColorPrimary))
        }
        for (i in 0 until (itemCount - 1)) separators[i].visible()
    }

    private fun toggleButton(index: Int, callBack: Boolean = true) {
        if (index < 0 || itemCount == 0 ||
            (isActivated && index != buttons.indexOfFirst { it.isActivated })
        )
            return
        if (callBack) {
            val transition = androidx.transition.AutoTransition()
            transition.duration = 150
            androidx.transition.TransitionManager.beginDelayedTransition(
                parent.parent as ViewGroup,
                transition
            )
        }
        if (itemCount == 1) {
            firstButton.isActivated = !firstButton.isActivated
            firstButton.setTextColor(
                if (firstButton.isActivated) Color.WHITE else context
                    .getResourceColor(android.R.attr.textColorPrimary)
            )
            listener?.onFilterClicked(this, if (firstButton.isActivated) index else -1, callBack)
            return
        }
        val mainButton = buttons[index]

        if (mainButton.isActivated) {
            mainButton.isActivated = false
            listener?.onFilterClicked(this, -1, callBack)
            buttons.forEachIndexed { viewIndex, textView ->
                if (!textView.text.isNullOrBlank()) {
                    textView.visible()
                    if (viewIndex > 0) {
                        separators[viewIndex - 1].visible()
                    }
                }
            }
        } else {
            mainButton.isActivated = true
            listener?.onFilterClicked(this, index, callBack)
            buttons.forEach { if (it != mainButton) it.gone() }
            separators.forEach { it.gone() }
        }
        mainButton.setTextColor(
            if (mainButton.isActivated) Color.WHITE else context
                .getResourceColor(android.R.attr.textColorPrimary)
        )
    }
}

interface FilterTagGroupListener {
    fun onFilterClicked(view: FilterTagGroup, index: Int, updatePreference: Boolean)
}
