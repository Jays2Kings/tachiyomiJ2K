package eu.kanade.tachiyomi.ui.manga.track

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrackRemoveDialog<T> : DialogController
    where T : TrackRemoveDialog.Listener {

    private val item: TrackItem
    private lateinit var listener: Listener

    constructor(target: T, item: TrackItem) : super(Bundle().apply {
        putSerializable(KEY_ITEM_TRACK, item.track)
    }) {
        listener = target
        this.item = item
    }

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        val track = bundle.getSerializable(KEY_ITEM_TRACK) as Track
        val service = Injekt.get<TrackManager>().getService(track.sync_id)!!
        item = TrackItem(track, service)
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val item = item
        val mutableList = mutableListOf(
            activity!!.getString(
                R.string.remove_tracking_from_app
            )
        )

        if (item.service.canRemoveFromService()) {
            mutableList.add(
                activity!!.getString(
                    R.string.remove_tracking_from_, item.service.name
                )
            )
        }

        val dialog = MaterialDialog(activity!!)
            .title(R.string.remove_tracking)
            .listItemsMultiChoice(
                items = mutableList.toList(),
                initialSelection = intArrayOf(0),
                disabledIndices = intArrayOf(0)
            ) { _, index, _ ->
                listener.removeTracker(item, index.size > 1)
            }
            .negativeButton(android.R.string.cancel)
            .positiveButton(android.R.string.ok)

        return dialog
    }

    interface Listener {
        fun removeTracker(item: TrackItem, fromServiceAlso: Boolean)
    }

    private companion object {
        const val KEY_ITEM_TRACK = "TrackRemoveDialog.item.track"
    }
}
