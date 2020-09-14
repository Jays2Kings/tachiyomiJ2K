package eu.kanade.tachiyomi.ui.extension

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import androidx.core.content.ContextCompat
import coil.api.clear
import coil.api.load
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.resetStrokeColor
import eu.kanade.tachiyomi.data.image.coil.CoverViewTarget
import kotlinx.android.synthetic.main.extension_card_item.*
import kotlinx.android.synthetic.main.source_global_search_controller_card_item.*

class ExtensionHolder(view: View, val adapter: ExtensionAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    init {
        ext_button.setOnClickListener {
            adapter.buttonClickListener.onButtonClick(adapterPosition)
        }
    }

    fun bind(item: ExtensionItem) {
        val extension = item.extension

        // Set source name
        ext_title.text = extension.name
        version.text = extension.versionName
        lang.text = if (extension !is Extension.Untrusted) {
            LocaleHelper.getDisplayName(extension.lang, itemView.context)
        } else {
            itemView.context.getString(R.string.untrusted).toUpperCase()
        }

        edit_button.clear()

        if (extension is Extension.Available) {
            edit_button.load(extension.iconUrl) {
                target(CoverViewTarget(edit_button, progress))
            }
        } else {
            extension.getApplicationIcon(itemView.context)?.let { edit_button.setImageDrawable(it) }
        }
        bindButton(item)
    }

    @Suppress("ResourceType")
    fun bindButton(item: ExtensionItem) = with(ext_button) {
        isEnabled = true
        isClickable = true
        isActivated = false

        setTextColor(ContextCompat.getColorStateList(context, R.drawable.button_text_state))
        backgroundTintList = ContextCompat.getColorStateList(context, android.R.color.transparent)

        resetStrokeColor()
        val extension = item.extension
        val installStep = item.installStep
        if (installStep != null) {
            setText(
                when (installStep) {
                    InstallStep.Pending -> R.string.pending
                    InstallStep.Downloading -> R.string.downloading
                    InstallStep.Installing -> R.string.installing
                    InstallStep.Installed -> R.string.installed
                    InstallStep.Error -> R.string.retry
                }
            )
            if (installStep != InstallStep.Error) {
                isEnabled = false
                isClickable = false
            }
        } else if (extension is Extension.Installed) {
            when {
                extension.hasUpdate -> {
                    isActivated = true
                    backgroundTintList = ColorStateList.valueOf(
                        context.getResourceColor(R.attr.colorAccent)
                    )
                    strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
                    setText(R.string.update)
                }
                extension.isObsolete -> {
                    // Red outline
                    setTextColor(ContextCompat.getColorStateList(context, R.drawable.button_bg_error))

                    setText(R.string.obsolete)
                }
                else -> {
                    setText(R.string.details)
                }
            }
        } else if (extension is Extension.Untrusted) {
            setText(R.string.trust)
        } else {
            setText(R.string.install)
        }
    }
}
