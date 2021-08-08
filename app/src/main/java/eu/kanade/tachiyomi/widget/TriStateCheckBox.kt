package eu.kanade.tachiyomi.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.TriStateCheckBoxBinding
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.setAnimVectorCompat
import eu.kanade.tachiyomi.util.view.setVectorCompat

class TriStateCheckBox constructor(context: Context, attrs: AttributeSet?) :
    FrameLayout(context, attrs) {

    var text: CharSequence
        get() {
            return binding.textView.text
        }
        set(value) {
            binding.textView.text = value
        }

    var state: State = State.UNCHECKED
        set(value) {
            field = value
            updateDrawable()
        }

    var isUnchecked: Boolean
        get() = state == State.UNCHECKED
        set(value) {
            state = if (value) State.UNCHECKED else State.CHECKED
        }

    var isChecked: Boolean
        get() = state == State.UNCHECKED
        set(value) {
            state = if (value) State.CHECKED else State.UNCHECKED
        }

    private val binding = TriStateCheckBoxBinding.inflate(
        LayoutInflater.from(context),
        this,
        false
    )
    private var mOnCheckedChangeListener: OnCheckedChangeListener? = null

    init {
        addView(binding.root)
        val a = context.obtainStyledAttributes(attrs, R.styleable.TriStateCheckBox, 0, 0)

        val str = a.getString(R.styleable.TriStateCheckBox_android_text) ?: ""
        text = str

        val maxLines = a.getInt(R.styleable.TriStateCheckBox_android_maxLines, Int.MAX_VALUE)
        binding.textView.maxLines = maxLines

        a.recycle()

        setOnClickListener {
            setState(
                when (state) {
                    State.CHECKED -> State.INVERSED
                    State.UNCHECKED -> State.CHECKED
                    else -> State.UNCHECKED
                },
                true
            )
            mOnCheckedChangeListener?.onCheckedChanged(this, state)
        }
    }

    fun setState(state: State, animated: Boolean = false) {
        if (animated) {
            animateDrawableToState(state)
        } else {
            this.state = state
        }
    }

    /**
     * Register a callback to be invoked when the checked state of this button
     * changes.
     *
     * @param listener the callback to call on checked state change
     */
    fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        mOnCheckedChangeListener = listener
    }

    fun animateDrawableToState(state: State) {
        val oldState = this.state
        if (state == oldState) return
        this.state = state
        with(binding.triStateBox) {
            when (state) {
                State.UNCHECKED -> {
                    setAnimVectorCompat(
                        when (oldState) {
                            State.INVERSED -> R.drawable.anim_check_box_x_to_blank_24dp
                            else -> R.drawable.anim_check_box_checked_to_blank_24dp
                        },
                        R.attr.colorControlNormal
                    )
                    backgroundTintList = ColorStateList.valueOf(context.getResourceColor(R.attr.colorControlNormal))
                }
                State.CHECKED -> {
                    setAnimVectorCompat(
                        R.drawable.anim_check_box_blank_to_checked_24dp,
                        R.attr.colorSecondary
                    )
                    backgroundTintList = ColorStateList.valueOf(context.getResourceColor(R.attr.colorSecondary))
                }
                State.INVERSED -> {
                    setAnimVectorCompat(
                        R.drawable.anim_check_box_checked_to_x_24dp,
                        R.attr.colorOnSurface
                    )
                    backgroundTintList = ColorStateList.valueOf(context.getResourceColor(R.attr.colorOnSurface))
                }
            }
        }
    }

    fun updateDrawable() {
        with(binding.triStateBox) {
            when (state) {
                State.UNCHECKED -> {
                    setVectorCompat(
                        R.drawable.ic_check_box_outline_blank_24dp,
                        R.attr.colorControlNormal
                    )
                    backgroundTintList = ColorStateList.valueOf(context.getResourceColor(R.attr.colorControlNormal))
                }
                State.CHECKED -> {
                    setVectorCompat(R.drawable.ic_check_box_24dp, R.attr.colorSecondary)
                    backgroundTintList = ColorStateList.valueOf(context.getResourceColor(R.attr.colorSecondary))
                }
                State.INVERSED -> {
                    setVectorCompat(
                        R.drawable.ic_check_box_x_24dp,
                        R.attr.colorOnSurface
                    )
                    backgroundTintList = ColorStateList.valueOf(context.getResourceColor(R.attr.colorOnSurface))
                }
            }
        }
    }

    enum class State {
        UNCHECKED,
        CHECKED,
        INVERSED,
        ;
    }

    /**
     * Interface definition for a callback to be invoked when the checked state
     * of a compound button changed.
     */
    fun interface OnCheckedChangeListener {
        /**
         * Called when the checked state of a compound button has changed.
         *
         * @param buttonView The compound button view whose state has changed.
         * @param state The new checked state of buttonView.
         */
        fun onCheckedChanged(buttonView: TriStateCheckBox, state: State)
    }
}
