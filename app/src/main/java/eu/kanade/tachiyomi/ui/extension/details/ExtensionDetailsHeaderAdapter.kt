package eu.kanade.tachiyomi.ui.extension.details

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ExtensionDetailHeaderBinding
import eu.kanade.tachiyomi.ui.extension.getApplicationIcon
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.view.inflate

class ExtensionDetailsHeaderAdapter(private val presenter: ExtensionDetailsPresenter) :
    RecyclerView.Adapter<ExtensionDetailsHeaderAdapter.HeaderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val view = parent.inflate(R.layout.extension_detail_header)
        return HeaderViewHolder(view)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind()
    }

    override fun getItemViewType(position: Int): Int {
        return R.layout.extension_detail_header
    }

    override fun getItemId(position: Int): Long {
        return presenter.pkgName.hashCode().toLong()
    }

    inner class HeaderViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val binding = ExtensionDetailHeaderBinding.bind(view)
            val extension = presenter.extension ?: return
            val context = view.context

            extension.getApplicationIcon(context)?.let { binding.extensionIcon.setImageDrawable(it) }
            binding.extensionTitle.text = extension.name
            binding.extensionVersion.text = context.getString(R.string.version_, extension.versionName)
            binding.extensionLang.text = context.getString(R.string.language_, LocaleHelper.getSourceDisplayName(extension.lang, context))
            binding.extensionNsfw.isVisible = extension.isNsfw
            binding.extensionPkg.text = extension.pkgName

            binding.extensionUninstallButton.setOnClickListener {
                presenter.uninstallExtension()
            }

            binding.extensionAppInfoButton.setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", presenter.pkgName, null)
                }
                it.context.startActivity(intent)
            }

            if (extension.isUnofficial) {
                binding.extensionWarningBanner.isVisible = true
                binding.extensionWarningBanner.setText(R.string.unofficial_extension_message)
            } else if (extension.isObsolete) {
                binding.extensionWarningBanner.isVisible = true
                binding.extensionWarningBanner.setText(R.string.obsolete_extension_message)
            }
        }
    }
}
