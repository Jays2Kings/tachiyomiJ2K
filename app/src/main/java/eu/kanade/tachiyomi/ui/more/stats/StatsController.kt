package eu.kanade.tachiyomi.ui.more.stats

import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.databinding.StatsControllerBinding
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.more.stats.details.StatsDetailsController
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.mapStatus
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.roundToTwoDecimal
import eu.kanade.tachiyomi.util.view.compatToolTipText
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import timber.log.Timber
import kotlin.math.roundToInt

class StatsController : BaseController<StatsControllerBinding>() {

    val presenter = StatsPresenter()

    private val mangaDistinct = presenter.mangaDistinct
    private var scoresList = emptyList<Double>()

    /**
     * Returns the toolbar title to show when this controller is attached.
     */
    override fun getTitle() = resources?.getString(R.string.statistics)

    override fun createBinding(inflater: LayoutInflater) = StatsControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        scrollViewWith(binding.statsScrollView, true)
        handleGeneralStats()
        if (mangaDistinct.isNotEmpty()) {
            binding.viewDetailLayout.setOnClickListener {
                router.pushController(StatsDetailsController().withFadeTransaction())
            }
            handleStatusDistribution()
        }
        if (scoresList.isNotEmpty()) handleScoreDistribution()
    }

    private fun handleGeneralStats() {
        val mangaTracks = mangaDistinct.map { it to presenter.getTracks(it) }
        scoresList = getScoresList(mangaTracks)
        with(binding) {
            viewDetailLayout.isVisible = mangaDistinct.isNotEmpty()
            statsTotalMangaText.text = mangaDistinct.count().toString()
            statsTotalChaptersText.text = mangaDistinct.sumOf { it.totalChapters }.toString()
            statsChaptersReadText.text = mangaDistinct.sumOf { it.read }.toString()
            statsMangaMeanScoreText.text = if (scoresList.isEmpty()) {
                statsMangaMeanScoreText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                activity?.getString(R.string.none)
            } else scoresList.average().roundToTwoDecimal().toString()
            statsMangaMeanScoreLayout.compatToolTipText = activity?.getString(R.string.mean_score_info)
            statsTrackedMangaText.text = mangaTracks.count { it.second.isNotEmpty() }.toString()
            statsChaptersDownloadedText.text = mangaDistinct.sumOf { presenter.getDownloadCount(it) }.toString()
            statsTotalTagsText.text = mangaDistinct.flatMap { it.getTags() }.distinct().count().toString()
            statsMangaLocalText.text = mangaDistinct.count { it.isLocal() }.toString()
            statsGlobalUpdateMangaText.text = presenter.getGlobalUpdateManga().count().toString()
            statsSourcesText.text = presenter.getSources().count().toString()
            statsTrackersText.text = presenter.getLoggedTrackers().count().toString()
            statsReadDurationText.text = presenter.getReadDuration()
            statsReadDurationLayout.compatToolTipText = activity?.getString(R.string.read_duration_info)
        }
    }

    private fun getScoresList(mangaTracks: List<Pair<LibraryManga, MutableList<Track>>>): List<Double> {
        return mangaTracks.filter { it.second.isNotEmpty() }
            .map {
                it.second.filter { track -> track.score > 0 }
                    .mapNotNull { track -> presenter.get10PointScore(track) }
                    .average()
            }.filter { it > 0.0 }
    }

    private fun Manga.getTags(): List<String> {
        return getGenres()?.map { it.uppercase() } ?: emptyList()
    }

    private fun handleStatusDistribution() {
        binding.mangaStatsStatusLayout.isVisible = true
        val statusMap = StatsHelper.STATUS_COLOR_MAP
        val pieEntries = ArrayList<PieEntry>()

        val mangaStatusDistributionList = statusMap.mapNotNull { (status, color) ->
            val libraryCount = mangaDistinct.count { it.status == status }
            if (status == SManga.UNKNOWN && libraryCount == 0) return@mapNotNull null
            pieEntries.add(PieEntry(libraryCount.toFloat(), activity!!.mapStatus(status)))
            StatusDistributionItem(activity!!.mapStatus(status), libraryCount, color)
        }

        val pieDataSet = PieDataSet(pieEntries, activity!!.getString(R.string.manga_status_distribution))
        pieDataSet.colors = mangaStatusDistributionList.map { it.color }
        showMangaStatsStatusChart(pieDataSet)
        binding.mangaStatsStatusRecyclerView.adapter =
            StatsLegendAdapter(mangaStatusDistributionList)
    }

    private fun showMangaStatsStatusChart(pieDataSet: PieDataSet) {
        try {
            val pieData = PieData(pieDataSet)
            pieData.setDrawValues(false)

            binding.mangaStatsStatusPieChart.apply {
                setHoleColor(ContextCompat.getColor(context, android.R.color.transparent))
                setDrawEntryLabels(false)
                setTouchEnabled(false)
                description.isEnabled = false
                legend.isEnabled = false
                data = pieData
                invalidate()
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun handleScoreDistribution() {
        binding.mangaStatsScoreLayout.isVisible = true
        val scoreMap = StatsHelper.SCORE_COLOR_MAP
        val userScoreList = scoresList.groupingBy { it.roundToInt().coerceIn(1..10) }
            .eachCount().toSortedMap()

        val barEntries = scoreMap.map { (score, _) ->
            BarEntry(score.toFloat(), userScoreList[score]?.toFloat() ?: 0f)
        }
        val barDataSet = BarDataSet(barEntries, activity!!.getString(R.string.manga_score_distribution))
        barDataSet.colors = scoreMap.values.toList()
        showMangaStatsScoreChart(barDataSet)
    }

    private fun showMangaStatsScoreChart(barDataSet: BarDataSet) {
        val valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float) = value.toInt().toString()
        }

        try {
            with(binding.mangaStatsScoreBarChart) {
                val barData = BarData(barDataSet)
                barData.setValueTextColor(activity!!.getResourceColor(R.attr.colorOnBackground))
                barData.setValueFormatter(valueFormatter)
                barData.barWidth = 0.6f
                barData.setValueTextSize(10f)
                axisLeft.isEnabled = false
                axisRight.isEnabled = false

                xAxis.apply {
                    setDrawGridLines(false)
                    position = XAxis.XAxisPosition.BOTTOM
                    setLabelCount(barDataSet.entryCount, false)
                    textColor = activity!!.getResourceColor(R.attr.colorOnBackground)
                }

                apply {
                    setTouchEnabled(false)
                    description.isEnabled = false
                    legend.isEnabled = false
                    data = barData
                    invalidate()
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    class StatusDistributionItem(
        val status: String,
        val amount: Int,
        val color: Int,
    )
}
