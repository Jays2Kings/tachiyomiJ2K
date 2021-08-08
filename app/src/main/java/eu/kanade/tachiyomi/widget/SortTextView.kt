package eu.kanade.tachiyomi.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SortTextViewBinding
import eu.kanade.tachiyomi.util.view.setVectorCompat

class SortTextView constructor(context: Context, attrs: AttributeSet?) :
    FrameLayout(context, attrs) {

    var text: CharSequence
        get() {
            return binding.textView.text
        }
        set(value) {
            binding.textView.text = value
        }

    var state: State = State.NONE
        set(value) {
            field = value
            updateDrawable()
        }

    val isSorting: Boolean
        get() = state != State.NONE

    private val binding = SortTextViewBinding.inflate(
        LayoutInflater.from(context),
        this,
        false
    )
    private var mOnSortChangeListener: OnSortChangeListener? = null

    init {
        addView(binding.root)
        val a = context.obtainStyledAttributes(attrs, R.styleable.SortTextView, 0, 0)

        val str = a.getString(R.styleable.SortTextView_android_text) ?: ""
        text = str

        val maxLines = a.getInt(R.styleable.SortTextView_android_maxLines, Int.MAX_VALUE)
        binding.textView.maxLines = maxLines

        a.recycle()

        setOnClickListener {
            state =
                when (state) {
                    State.DESCENDING -> State.ASCENDING
                    else -> State.DESCENDING
                }
            mOnSortChangeListener?.onSortChanged(this, state)
        }
    }

    /**
     * Register a callback to be invoked when the checked state of this button
     * changes.
     *
     * @param listener the callback to call on checked state change
     */
    fun setOnSortChangeListener(listener: OnSortChangeListener?) {
        mOnSortChangeListener = listener
    }

    fun updateDrawable() {
        with(binding.sortImageView) {
            when (state) {
                State.ASCENDING -> {
                    setVectorCompat(R.drawable.ic_arrow_upward_24dp, R.attr.colorSecondary)
                }
                State.DESCENDING -> {
                    setVectorCompat(R.drawable.ic_arrow_downward_24dp, R.attr.colorSecondary)
                }
                State.NONE -> {
                    setVectorCompat(R.drawable.ic_blank_24dp, R.attr.colorOnSurface)
                }
            }
        }
    }

    enum class State {
        ASCENDING,
        DESCENDING,
        NONE,
        ;
    }

    /**
     * Interface definition for a callback to be invoked when the checked state
     * of a compound button changed.
     */
    fun interface OnSortChangeListener {
        /**
         * Called when the checked state of a compound button has changed.
         *
         * @param buttonView The compound button view whose state has changed.
         * @param state The new checked state of buttonView.
         */
        fun onSortChanged(buttonView: SortTextView, state: State)
    }
}
