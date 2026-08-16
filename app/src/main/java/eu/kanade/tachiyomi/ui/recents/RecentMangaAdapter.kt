package eu.kanade.tachiyomi.ui.recents

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.fredporciuncula.flow.preferences.Preference
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterHistory
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.databinding.RecentSubChapterItemBinding
import eu.kanade.tachiyomi.ui.manga.chapter.BaseChapterAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale

class RecentMangaAdapter(
    val delegate: RecentsInterface,
) : BaseChapterAdapter<IFlexible<*>>(delegate) {
    val preferences: PreferencesHelper by injectLazy()

    var showDownloads = preferences.showRecentsDownloads().get()
    var showRemoveHistory = preferences.showRecentsRemHistory().get()
    var showTitleFirst = preferences.showTitleFirstInRecents().get()
    var showUpdatedTime = preferences.showUpdatedTime().get()
    var uniformCovers = preferences.uniformGrid().get()
    var showOutline = preferences.outlineOnCovers().get()
    var sortByFetched = preferences.sortFetchedTime().get()
    var lastUpdatedTime = preferences.libraryUpdateLastTimestamp().get()
    private var collapseGroupedUpdates = preferences.collapseGroupedUpdates().get()
    private var collapseGroupedHistory = preferences.collapseGroupedHistory().get()
    val collapseGrouped: Boolean
        get() =
            if (viewType.isHistory) {
                collapseGroupedHistory
            } else {
                collapseGroupedUpdates
            }

    val viewType: RecentsViewType
        get() = delegate.getViewType()

    val decimalFormat =
        DecimalFormat(
            "#.###",
            DecimalFormatSymbols()
                .apply { decimalSeparator = '.' },
        )
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    init {
        setDisplayHeadersAtStartUp(true)
    }

    fun setPreferenceFlows() {
        preferences.showRecentsDownloads().register { showDownloads = it }
        preferences.showRecentsRemHistory().register { showRemoveHistory = it }
        preferences.showTitleFirstInRecents().register { showTitleFirst = it }
        preferences.showUpdatedTime().register { showUpdatedTime = it }
        preferences.uniformGrid().register { uniformCovers = it }
        preferences.collapseGroupedUpdates().register { collapseGroupedUpdates = it }
        preferences.collapseGroupedHistory().register { collapseGroupedHistory = it }
        preferences.sortFetchedTime().asImmediateFlowIn(delegate.scope()) { sortByFetched = it }
        preferences.outlineOnCovers().register(false) {
            showOutline = it
            (0 until itemCount).forEach { i ->
                (recyclerView.findViewHolderForAdapterPosition(i) as? RecentMangaHolder)?.updateCards()
            }
        }
        preferences
            .libraryUpdateLastTimestamp()
            .asFlow()
            .onEach {
                lastUpdatedTime = it
                if (viewType.isUpdates) {
                    notifyItemChanged(0)
                }
            }.launchIn(delegate.scope())
    }

    /**
     * Sub chapter rows are handed back here when a group collapses or its holder is recycled, so a
     * holder that needs rows can take them from groups that have scrolled away instead of
     * inflating its own. Reuse used to be per holder, which meant a recycled holder that happened
     * to come in with no rows paid to inflate every one of them.
     */
    private val subChapterPool = ArrayDeque<View>()

    fun obtainSubChapterView(
        context: Context,
        parent: ViewGroup,
    ): View =
        subChapterPool.removeLastOrNull()
            ?: RecentSubChapterItemBinding.inflate(LayoutInflater.from(context), parent, false).root

    fun recycleSubChapterView(view: View) {
        if (subChapterPool.size < SUB_CHAPTER_POOL_SIZE) {
            subChapterPool.addLast(view)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? RecentMangaHolder)?.releaseSubChapters()
    }

    fun getItemByChapterId(id: Long): RecentMangaItem? {
        return currentItems.find {
            val item = (it as? RecentMangaItem) ?: return@find false
            return@find id == item.chapter.id || id in item.mch.extraChapters.map { ch -> ch.id }
        } as? RecentMangaItem
    }

    private fun <T> Preference<T>.register(
        notify: Boolean = true,
        onChanged: (T) -> Unit,
    ) {
        asFlow()
            .drop(1)
            .onEach {
                onChanged(it)
                if (notify) {
                    notifyDataSetChanged()
                }
            }.launchIn(delegate.scope())
    }

    interface RecentsInterface : GroupedDownloadInterface {
        fun onCoverClick(position: Int)

        fun onRemoveHistoryClicked(position: Int)

        fun onSubChapterClicked(
            position: Int,
            chapter: Chapter,
            view: View,
        )

        fun updateExpandedExtraChapters(
            position: Int,
            expanded: Boolean,
        )

        fun areExtraChaptersExpanded(position: Int): Boolean

        fun markAsRead(position: Int)

        fun alwaysExpanded(): Boolean

        fun scope(): CoroutineScope

        fun getViewType(): RecentsViewType

        fun onItemLongClick(
            position: Int,
            chapter: ChapterHistory,
        ): Boolean
    }

    override fun onItemSwiped(
        position: Int,
        direction: Int,
    ) {
        super.onItemSwiped(position, direction)
        when (direction) {
            ItemTouchHelper.LEFT -> delegate.markAsRead(position)
            ItemTouchHelper.RIGHT -> delegate.markAsRead(position)
        }
    }

    enum class ShowRecentsDLs {
        None,
        OnlyUnread,
        OnlyDownloaded,
        UnreadOrDownloaded,
        All,
    }

    companion object {
        /** Roughly two fully expanded history groups' worth of rows. */
        private const val SUB_CHAPTER_POOL_SIZE = 40
    }
}
