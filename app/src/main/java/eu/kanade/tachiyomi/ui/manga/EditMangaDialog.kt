package eu.kanade.tachiyomi.ui.manga

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import coil.api.loadAny
import coil.request.Parameters
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.MangaFetcher
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.edit_manga_dialog.view.*
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class EditMangaDialog : DialogController {

    private var dialogView: View? = null

    private val manga: Manga

    private var customCoverUri: Uri? = null

    private var willResetCover = false

    private val infoController
        get() = targetController as MangaDetailsController

    constructor(target: MangaDetailsController, manga: Manga) : super(
        Bundle()
            .apply {
                putLong(KEY_MANGA, manga.id!!)
            }
    ) {
        targetController = target
        this.manga = manga
    }

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        manga = Injekt.get<DatabaseHelper>().getManga(bundle.getLong(KEY_MANGA))
            .executeAsBlocking()!!
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val dialog = MaterialDialog(activity!!).apply {
            customView(viewRes = R.layout.edit_manga_dialog, scrollable = true)
            negativeButton(android.R.string.cancel)
            positiveButton(R.string.save) { onPositiveButtonClick() }
        }
        dialogView = dialog.view
        onViewCreated(dialog.view)
        dialog.setOnShowListener {
            val dView = (it as? MaterialDialog)?.view
            dView?.contentLayout?.scrollView?.scrollTo(0, 0)
        }
        return dialog
    }

    fun onViewCreated(view: View) {
        view.manga_cover.loadAny(manga)
        val isLocal = manga.source == LocalSource.ID

        if (isLocal) {
            if (manga.title != manga.url)
                view.title.append(manga.title)
            view.title.hint = "${resources?.getString(R.string.title)}: ${manga.url}"
            view.manga_author.append(manga.author ?: "")
            view.manga_artist.append(manga.artist ?: "")
            view.manga_description.append(manga.description ?: "")
            view.manga_genres_tags.setTags(manga.genre?.split(", ") ?: emptyList())
        } else {
            if (manga.title != manga.originalTitle) {
                view.title.append(manga.title)
            }
            if (manga.author != manga.originalAuthor) {
                view.manga_author.append(manga.author ?: "")
            }
            if (manga.artist != manga.originalArtist) {
                view.manga_artist.append(manga.artist ?: "")
            }
            if (manga.description != manga.originalDescription) {
                view.manga_description.append(manga.description ?: "")
            }
            view.manga_genres_tags.setTags(manga.genre?.split(", ") ?: emptyList())

            view.title.hint = "${resources?.getString(R.string.title)}: ${manga.originalTitle}"
            if (manga.originalAuthor != null) {
                view.manga_author.hint = "${resources?.getString(R.string.author)}: ${manga.originalAuthor}"
            }
            if (manga.originalArtist != null) {
                view.manga_artist.hint = "${resources?.getString(R.string.artist)}: ${manga.originalArtist}"
            }
            if (manga.originalDescription != null) {
                view.manga_description.hint =
                    "${resources?.getString(R.string.description)}: ${manga.originalDescription?.replace(
                        "\n",
                        " "
                    )?.chop(20)}"
            }
        }
        view.manga_genres_tags.clearFocus()
        view.cover_layout.setOnClickListener {
            infoController.changeCover()
        }
        view.reset_tags.setOnClickListener { resetTags() }
        view.reset_cover.visibleIf(!isLocal)
        view.reset_cover.setOnClickListener {
            view.manga_cover.loadAny(
                manga,
                builder = {
                    parameters(Parameters.Builder().set(MangaFetcher.realCover, true).build())
                }
            )
            willResetCover = true
        }
    }

    private fun resetTags() {
        if (manga.genre.isNullOrBlank() || manga.source == LocalSource.ID) dialogView?.manga_genres_tags?.setTags(
            emptyList()
        )
        else dialogView?.manga_genres_tags?.setTags(manga.originalGenre?.split(", "))
    }

    fun updateCover(uri: Uri) {
        willResetCover = false
        dialogView!!.manga_cover.loadAny(uri)
        customCoverUri = uri
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        dialogView = null
    }

    private fun onPositiveButtonClick() {
        infoController.presenter.updateManga(
            dialogView?.title?.text.toString(),
            dialogView?.manga_author?.text.toString(),
            dialogView?.manga_artist?.text.toString(),
            customCoverUri,
            dialogView?.manga_description?.text.toString(),
            dialogView?.manga_genres_tags?.tags,
            willResetCover
        )
    }

    private companion object {
        const val KEY_MANGA = "manga_id"
    }
}
