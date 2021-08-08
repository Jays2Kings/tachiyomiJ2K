package eu.kanade.tachiyomi.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.core.widget.TextViewCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.MenuSheetItemBinding
import eu.kanade.tachiyomi.util.system.getResourceColor

class MenuSheetItemView @JvmOverloads constructor(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs) {
    private val mText: String
    private val mIconRes: Int
    private val mEndIconRes: Int
    private val mMaxLines: Int

    private var binding: MenuSheetItemBinding? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.MenuSheetItemView, 0, 0)

        val str = a.getString(R.styleable.MenuSheetItemView_android_text) ?: ""
        mText = str

        val d = a.getResourceId(R.styleable.MenuSheetItemView_icon, 0)
        mIconRes = d

        val e = a.getResourceId(R.styleable.MenuSheetItemView_endIcon, 0)
        mEndIconRes = e

        val m = a.getInt(R.styleable.MenuSheetItemView_android_maxLines, Int.MAX_VALUE)
        mMaxLines = m

        a.recycle()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = try {
            MenuSheetItemBinding.bind(this)
        } catch (e: Exception) {
            MenuSheetItemBinding.inflate(LayoutInflater.from(context), this, true)
        }
        text = mText
        setIcon(mIconRes)
        setEndIcon(mEndIconRes)
        binding?.itemTextView?.maxLines = mMaxLines
    }

    var text: CharSequence?
        get() = binding?.itemTextView?.text
        set(value) {
            binding?.itemTextView?.text = value
        }

    var textSize: Float
        get() = binding?.itemTextView?.textSize ?: 0f
        set(value) {
            binding?.itemTextView?.textSize = value
        }

    fun setText(@StringRes res: Int) {
        text = context.getString(res)
    }

    fun selectWithEndIcon(@DrawableRes endDrawableRes: Int) {
        isSelected = true
        setEndIcon(endDrawableRes)
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        if (isSelected) {
            setIconColor(context.getResourceColor(R.attr.colorSecondary))
            setTextColor(context.getResourceColor(R.attr.colorSecondary))
        } else {
            setTextColor(context.getResourceColor(R.attr.colorOnBackground))
            setIconColor(context.getResourceColor(R.attr.colorOnBackground))
            setEndIcon(0)
        }
    }

    fun setTextColor(@ColorInt color: Int) {
        binding?.itemTextView?.setTextColor(color)
    }

    fun setIconColor(@ColorInt color: Int) = binding?.itemTextView?.let {
        TextViewCompat.setCompoundDrawableTintList(
            it,
            ColorStateList.valueOf(color)
        )
    }

    fun setIcon(@DrawableRes res: Int) {
        binding?.itemTextView?.setCompoundDrawablesRelativeWithIntrinsicBounds(
            res,
            0,
            0,
            0
        )
    }

    fun setIcon(drawable: Drawable?) {
        binding?.itemTextView?.setCompoundDrawablesRelativeWithIntrinsicBounds(
            drawable,
            null,
            null,
            null,
        )
    }

    fun getIcon(): Drawable? {
        return binding?.itemTextView?.compoundDrawablesRelative?.getOrNull(0)
    }

    fun setEndIcon(@DrawableRes res: Int) {
        binding?.menuEndItem?.isGone = res == 0
        binding?.menuEndItem?.setImageResource(res)
    }

    fun setEndIcon(drawable: Drawable?) {
        binding?.menuEndItem?.isGone = drawable == null
        binding?.menuEndItem?.setImageDrawable(drawable)
    }
}
