package eu.kanade.tachiyomi.ui.recents.options

import android.content.Context
import android.util.AttributeSet
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.RecentsGeneralViewBinding
import eu.kanade.tachiyomi.util.bindToPreference
import eu.kanade.tachiyomi.util.lang.withSubtitle
import eu.kanade.tachiyomi.widget.BaseRecentsDisplayView

class RecentsGeneralView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    BaseRecentsDisplayView<RecentsGeneralViewBinding>(context, attrs) {

    override fun inflateBinding() = RecentsGeneralViewBinding.bind(this)
    override fun initGeneralPreferences() {
        val titleText = context.getString(R.string.show_reset_history_button)
        val uniformText = context.getString(R.string.uniform_covers)
        binding.showRemoveHistory.text = titleText
            .withSubtitle(binding.showRemoveHistory.context, R.string.press_and_hold_to_also_reset)
        binding.uniformCovers.text = uniformText
            .withSubtitle(binding.uniformCovers.context, R.string.affects_library_grid)
        binding.showRecentsDownload.bindToPreference(preferences.showRecentsDownloads())
        binding.showRemoveHistory.bindToPreference(preferences.showRecentsRemHistory())
        binding.showReadInAll.bindToPreference(preferences.showReadInAllRecents())
        binding.showTitleFirst.bindToPreference(preferences.showTitleFirstInRecents())
        binding.uniformCovers.bindToPreference(preferences.uniformGrid())
    }
}
