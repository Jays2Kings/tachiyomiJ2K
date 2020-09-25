package eu.kanade.tachiyomi.ui.recently_read

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.checkbox.checkBoxPrompt
import com.afollestad.materialdialogs.checkbox.isCheckPromptChecked
import com.bluelinelabs.conductor.Controller
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.base.controller.DialogController

class RemoveHistoryDialog<T>(bundle: Bundle? = null) : DialogController(bundle)
        where T : Controller, T : RemoveHistoryDialog.Listener {

    private var manga: Manga? = null

    private var history: History? = null

    private var chapter: Chapter? = null

    constructor(target: T, manga: Manga, history: History, chapter: Chapter? = null) : this() {
        this.manga = manga
        this.history = history
        this.chapter = chapter
        targetController = target
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val activity = activity!!

        return MaterialDialog(activity).title(R.string.reset_chapter_question).message(
            text = if (chapter?.name != null) activity.getString(
                R.string.this_will_remove_the_read_date_for_x_question,
                chapter?.name ?: ""
            )
            else activity.getString(R.string.this_will_remove_the_read_date_question)
        ).checkBoxPrompt(
            text = activity.getString(
                R.string.reset_all_chapters_for_this_,
                manga!!.mangaType(activity)
            )
        ) {}.negativeButton(android.R.string.cancel).positiveButton(R.string.reset) {
            onPositive(it.isCheckPromptChecked())
        }
    }

    private fun onPositive(checked: Boolean) {
        val target = targetController as? Listener ?: return
        val manga = manga ?: return
        val history = history ?: return

        target.removeHistory(manga, history, checked)
    }

    interface Listener {
        fun removeHistory(manga: Manga, history: History, all: Boolean)
    }
}
