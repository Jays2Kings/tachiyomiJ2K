package eu.kanade.tachiyomi.ui.library.filter

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.f2prateek.rx.preferences.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.databinding.FilterTagGroupBinding
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor

class FilterTagGroup@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout
(context, attrs) {

    private var listener: FilterTagGroupListener? = null

    var itemCount = 0
        private set

    private var root: ViewGroup? = null

    private val buttons by lazy {
        arrayOf(
            binding.firstButton,
            binding.secondButton,
            binding.thirdButton,
            binding.fourthButton
        )
    }

    private val separators by lazy {
        arrayOf(
            binding.separator1,
            binding.separator2,
            binding.separator3
        )
    }

    override fun isActivated(): Boolean {
        return buttons.any { it.isActivated }
    }

    lateinit var binding: FilterTagGroupBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FilterTagGroupBinding.bind(this)
    }

    fun nameOf(index: Int): String? = buttons.getOrNull(index)?.text as? String

    fun setup(root: ViewGroup, firstText: Int, vararg extra: Int?) {
        val text1 = context.getString(firstText)
        val strings = extra.mapNotNull { if (it != null) context.getString(it) else null }
        setup(root, text1, extra = strings.toTypedArray())
    }

    fun setup(root: ViewGroup, firstText: String, vararg extra: String?) {
        listener = root as? FilterTagGroupListener
        (layoutParams as? MarginLayoutParams)?.rightMargin = 5.dpToPx
        (layoutParams as? MarginLayoutParams)?.leftMargin = 5.dpToPx
        binding.firstButton.text = firstText
        val extras = (extra.toList() + listOf<String?>(null, null, null)).take(separators.size)
        extras.forEachIndexed { index, text ->
            buttons[index + 1].text = text
            separators[index].isVisible = text != null
            buttons[index + 1].isVisible = text != null
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
            buttons[i].isVisible = true
            buttons[i].setTextColor(context.getResourceColor(R.attr.colorOnBackground))
        }
        for (i in 0 until (itemCount - 1)) separators[i].isVisible = true
    }

    private fun toggleButton(index: Int, callBack: Boolean = true) {
        if (index < 0 || itemCount == 0 ||
            (isActivated && index != buttons.indexOfFirst { it.isActivated })
        ) {
            return
        }
        if (callBack) {
            val transition = androidx.transition.AutoTransition()
            transition.duration = 150
            androidx.transition.TransitionManager.beginDelayedTransition(
                parent.parent as ViewGroup,
                transition
            )
        }
        if (itemCount == 1) {
            binding.firstButton.isActivated = !binding.firstButton.isActivated
            binding.firstButton.setTextColor(
                context.getResourceColor(
                    if (binding.firstButton.isActivated) R.attr.colorOnSecondary
                    else R.attr.colorOnBackground
                )
            )
            listener?.onFilterClicked(
                this,
                if (binding.firstButton.isActivated) index else -1,
                callBack
            )
            return
        }
        val mainButton = buttons[index]

        if (mainButton.isActivated) {
            mainButton.isActivated = false
            listener?.onFilterClicked(this, -1, callBack)
            buttons.forEachIndexed { viewIndex, textView ->
                if (!textView.text.isNullOrBlank()) {
                    textView.isVisible = true
                    if (viewIndex > 0) {
                        separators[viewIndex - 1].isVisible = true
                    }
                }
            }
        } else {
            mainButton.isActivated = true
            listener?.onFilterClicked(this, index, callBack)
            buttons.forEach { if (it != mainButton) it.isVisible = false }
            separators.forEach { it.isVisible = false }
        }
        mainButton.setTextColor(
            context.getResourceColor(
                if (mainButton.isActivated) R.attr.colorOnSecondary
                else R.attr.colorOnBackground
            )
        )
    }
}

interface FilterTagGroupListener {
    fun onFilterClicked(view: FilterTagGroup, index: Int, updatePreference: Boolean)
}
