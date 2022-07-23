package eu.kanade.tachiyomi.ui.more.stats.details

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.chip.Chip
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.databinding.StatsDetailsControllerBinding
import eu.kanade.tachiyomi.ui.base.SmallToolbarInterface
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper
import eu.kanade.tachiyomi.ui.more.stats.details.StatsDetailsPresenter.Stats
import eu.kanade.tachiyomi.ui.more.stats.details.StatsDetailsPresenter.StatsSort
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toInt
import eu.kanade.tachiyomi.util.view.liftAppbarWith
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.setStyle
import timber.log.Timber

class StatsDetailsController :
    BaseController<StatsDetailsControllerBinding>(),
    SmallToolbarInterface {

    private val presenter = StatsDetailsPresenter()
    private var query = ""
    private var adapter: StatsDetailsAdapter? = null
    lateinit var searchView: SearchView
    lateinit var searchItem: MenuItem

    private val defaultStat = Stats.SERIE_TYPE
    private val defaultSort = StatsSort.COUNT_DESC

    /**
     * Returns the toolbar title to show when this controller is attached.
     */
    override fun getTitle() = resources?.getString(R.string.statistics_details)

    override fun createBinding(inflater: LayoutInflater) = StatsDetailsControllerBinding.inflate(inflater)

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        liftAppbarWith(binding.statsDetailsScrollView, false)
        setHasOptionsMenu(true)

        if (presenter.selectedStat == null) {
            resetFilters()
        }

        resetLayout()
        with(binding) {
            statsDetailsRefreshLayout.setStyle()
            statsDetailsRefreshLayout.setOnRefreshListener {
                statsDetailsRefreshLayout.isRefreshing = false
                searchView.clearFocus()
                presenter.libraryMangas = presenter.getLibrary()
                collapseAndReset()
            }

            statsClearButton.setOnClickListener {
                resetFilters()
                searchView.clearFocus()
                collapseAndReset()
            }

            statsHorizontalScroll.setOnTouchListener { _, motionEvent ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_MOVE -> statsDetailsRefreshLayout.isEnabled = false
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> statsDetailsRefreshLayout.isEnabled = true
                }
                false
            }

            chipStat.setOnClickListener {
                searchView.clearFocus()
                activity?.materialAlertDialog()?.setSingleChoiceItems(
                    presenter.getStatsArray(),
                    Stats.values().indexOf(presenter.selectedStat),
                ) { dialog, which ->
                    val newSelection = Stats.values()[which]
                    if (newSelection == presenter.selectedStat) return@setSingleChoiceItems
                    chipStat.text =
                        activity?.getString(R.string.stat_, activity?.getString(newSelection.resourceId))
                    presenter.selectedStat = newSelection

                    dialog.dismiss()
                    collapseAndReset()
                }
                    ?.show()
            }
            chipStat.setOnCloseIconClickListener {
                if (presenter.selectedStat != defaultStat) {
                    presenter.selectedStat = defaultStat
                    chipStat.text = resetTextChip(R.string.stat_, defaultStat.resourceId)
                } else chipStat.callOnClick()
            }
            chipSerieType.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.serieTypeStats,
                    presenter.selectedSerieType,
                    R.string.serie_type,
                    R.string.serie_types_,
                )
            }
            chipSerieType.setOnCloseIconClickListener {
                if (presenter.selectedSerieType.isNotEmpty()) {
                    presenter.selectedSerieType = mutableSetOf()
                    chipSerieType.text = resetTextChip(R.string.serie_type)
                } else chipSerieType.callOnClick()
            }
            chipSource.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.sources.toTypedArray(),
                    presenter.selectedSource,
                    R.string.source,
                    R.string.sources_,
                )
            }
            chipSource.setOnCloseIconClickListener {
                if (presenter.selectedSource.isNotEmpty()) {
                    presenter.selectedSource = mutableSetOf()
                    chipSource.text = resetTextChip(R.string.source)
                } else chipSource.callOnClick()
            }
            chipStatus.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.statusStats,
                    presenter.selectedStatus,
                    R.string.status,
                    R.string.status_,
                )
            }
            chipStatus.setOnCloseIconClickListener {
                if (presenter.selectedStatus.isNotEmpty()) {
                    presenter.selectedStatus = mutableSetOf()
                    chipStatus.text = resetTextChip(R.string.status)
                } else chipStatus.callOnClick()
            }
            chipLanguage.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.languagesStats,
                    presenter.selectedLanguage,
                    R.string.language,
                    R.string.languages_,
                )
            }
            chipLanguage.setOnCloseIconClickListener {
                if (presenter.selectedLanguage.isNotEmpty()) {
                    presenter.selectedLanguage = mutableSetOf()
                    chipLanguage.text = resetTextChip(R.string.language)
                } else chipLanguage.callOnClick()
            }
            chipCategory.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.categoriesStats,
                    presenter.selectedCategory,
                    R.string.category,
                    R.string.categories_,
                )
            }
            chipCategory.setOnCloseIconClickListener {
                if (presenter.selectedCategory.isNotEmpty()) {
                    presenter.selectedCategory = mutableSetOf()
                    chipCategory.text = resetTextChip(R.string.category)
                } else chipCategory.callOnClick()
            }
            chipSort.setOnClickListener {
                searchView.clearFocus()
                activity!!.materialAlertDialog().setSingleChoiceItems(
                    presenter.getSortDataArray(),
                    StatsSort.values().indexOf(presenter.selectedStatsSort),
                ) { dialog, which ->
                    val newSelection = StatsSort.values()[which]
                    if (newSelection == presenter.selectedStatsSort) return@setSingleChoiceItems
                    chipSort.text =
                        activity?.getString(R.string.sort_, activity?.getString(newSelection.resourceId))
                    presenter.selectedStatsSort = newSelection
                    dialog.dismiss()
                    presenter.sortCurrentStats()
                    collapseAndReset(false)
                }
                    .show()
            }
            chipSort.setOnCloseIconClickListener {
                if (presenter.selectedStatsSort != defaultSort) {
                    presenter.selectedStatsSort = defaultSort
                    chipSort.text = resetTextChip(R.string.sort_, defaultSort.resourceId)
                } else chipSort.callOnClick()
            }
        }

        setupStatistic(presenter.currentStats == null)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.stats_bar, menu)
        searchItem = menu.findItem(R.id.action_search)
        searchView = searchItem.actionView as SearchView
        searchView.queryHint = activity?.getString(R.string.search_statistics)
        if (query.isNotBlank() && (!searchItem.isActionViewExpanded || searchView.query != query)) {
            searchItem.expandActionView()
            setSearchViewListener(searchView)
            searchView.setQuery(query, true)
            searchView.clearFocus()
        } else {
            setSearchViewListener(searchView)
        }

        searchItem.fixExpand(onExpand = { invalidateMenuOnExpand() })
    }

    private fun setSearchViewListener(searchView: SearchView?) {
        setOnQueryTextChangeListener(searchView) {
            query = it ?: ""
            adapter?.filter(query)
            true
        }
    }

    private fun <T> Chip.setMultiChoiceItemsDialog(
        statsList: Array<T>,
        selectedStats: MutableSet<T>,
        resourceId: Int,
        resourceIdWithParam: Int,
    ) {
        val isCategory = statsList.isArrayOf<Category>()
        val items = statsList.map { if (isCategory) (it as Category).name else it.toString() }.toTypedArray()
        searchView.clearFocus()
        activity!!.materialAlertDialog().setMultiChoiceItems(
            items,
            statsList.map { it in selectedStats }.toBooleanArray(),
        ) { _, which, checked ->
            val newSelection = statsList[which]
            if (checked) {
                selectedStats.add(newSelection)
            } else {
                selectedStats.remove(newSelection)
            }
            this.text = when (selectedStats.size) {
                0 -> activity?.getString(resourceId)
                1 -> if (isCategory) (selectedStats.first() as Category).name else selectedStats.first().toString()
                else -> activity?.getString(resourceIdWithParam, selectedStats.size)
            }
            resetChips()
        }.setOnDismissListener {
            binding.progress.isVisible = true
            collapseAndReset(resetChips = false)
        }
            .show()
    }

    private fun collapseAndReset(updateStats: Boolean = true, resetChips: Boolean = true) {
        searchItem.collapseActionView()
        resetLayout(resetChips)
        setupStatistic(updateStats)
    }

    private fun resetTextChip(resourceId: Int, resourceIdArg: Int? = null): String? {
        collapseAndReset()
        return if (resourceIdArg == null) activity?.getString(resourceId) else {
            activity?.getString(resourceId, activity?.getString(resourceIdArg))
        }
    }

    private fun resetLayout(resetChips: Boolean = true) {
        with(binding) {
            progress.isVisible = true
            statsDetailsScrollView.isVisible = false
            statsDetailsScrollView.scrollTo(0, 0)
            statsPieChart.visibility = View.GONE
            statsBarChart.visibility = View.GONE
            statsLineChart.visibility = View.GONE

            if (resetChips) resetChips()
        }
    }

    private fun resetChips() {
        with(binding) {
            statsClearButton.isVisible = hasActiveFilters()
            chipStat.setColors((presenter.selectedStat != defaultStat).toInt())
            chipSerieType.isVisible = presenter.selectedStat != Stats.SERIE_TYPE
            chipSerieType.setColors(presenter.selectedSerieType.size)
            chipSource.isVisible = presenter.selectedStat !in listOf(Stats.LANGUAGE, Stats.SOURCE) &&
                presenter.selectedLanguage.isEmpty()
            chipSource.setColors(presenter.selectedSource.size)
            chipStatus.isVisible = presenter.selectedStat != Stats.STATUS
            chipStatus.setColors(presenter.selectedStatus.size)
            chipLanguage.isVisible = presenter.selectedStat != Stats.LANGUAGE &&
                (presenter.selectedStat == Stats.SOURCE || presenter.selectedSource.isEmpty())
            chipLanguage.setColors(presenter.selectedLanguage.size)
            chipCategory.isVisible = presenter.selectedStat != Stats.CATEGORY
            chipCategory.setColors(presenter.selectedCategory.size)
            chipSort.isVisible = presenter.selectedStat !in listOf(
                Stats.SCORE, Stats.LENGTH, Stats.START_YEAR,
            )
            chipSort.setColors((presenter.selectedStatsSort != defaultSort).toInt())
        }
    }

    private fun resetFilters() {
        with(binding) {
            presenter.selectedStat = defaultStat
            chipStat.text = activity?.getString(R.string.stat_, activity?.getString(defaultStat.resourceId))
            presenter.selectedSerieType = mutableSetOf()
            chipSerieType.text = activity?.getString(R.string.serie_type)
            presenter.selectedSource = mutableSetOf()
            chipSource.text = activity?.getString(R.string.source)
            presenter.selectedStatus = mutableSetOf()
            chipStatus.text = activity?.getString(R.string.status)
            presenter.selectedLanguage = mutableSetOf()
            chipLanguage.text = activity?.getString(R.string.language)
            presenter.selectedCategory = mutableSetOf()
            chipCategory.text = activity?.getString(R.string.category)
            presenter.selectedStatsSort = defaultSort
            chipSort.text = activity?.getString(R.string.sort_, activity?.getString(defaultSort.resourceId))
        }
    }

    private fun hasActiveFilters() = with(presenter) {
        listOf(selectedStat, selectedStatsSort).any { it !in listOf(null, defaultStat, defaultSort) } ||
            listOf(selectedSerieType, selectedSource, selectedStatus, selectedLanguage, selectedCategory).any {
                it.isNotEmpty()
            }
    }

    fun Chip.setColors(sizeStat: Int?) {
        val emptyTextColor = activity!!.getResourceColor(R.attr.colorOnBackground)
        val filteredBackColor = activity!!.getResourceColor(R.attr.colorSecondary)
        val emptyBackColor = activity!!.getResourceColor(R.attr.colorSurface)
        setTextColor(if (sizeStat == 0) emptyTextColor else emptyBackColor)
        chipBackgroundColor = ColorStateList.valueOf(if (sizeStat == 0) emptyBackColor else filteredBackColor)
        closeIcon = if (sizeStat == 0) context.contextCompatDrawable(R.drawable.ic_arrow_drop_down_24dp) else {
            context.contextCompatDrawable(R.drawable.ic_close_24dp)
        }
        closeIconTint = ColorStateList.valueOf(if (sizeStat == 0) emptyTextColor else emptyBackColor)
    }

    private fun setupStatistic(updateStats: Boolean = true) {
        if (updateStats) presenter.getStatisticData()
        with(binding) {
            if (presenter.currentStats.isNullOrEmpty() || presenter.currentStats!!.all { it.count == 0 }) {
                binding.noChartData.show(R.drawable.ic_heart_off_24dp, R.string.no_data_for_filters)
                presenter.currentStats?.removeAll { it.count == 0 }
                handleNoChartLayout()
            } else {
                binding.noChartData.hide()
                handleLayout()
            }
            statsDetailsScrollView.isVisible = true
            progress.isVisible = false
        }
    }

    private fun handleLayout() {
        when (presenter.selectedStat) {
            Stats.SERIE_TYPE, Stats.STATUS, Stats.LANGUAGE, Stats.TRACKER, Stats.CATEGORY -> handlePieChart()
            Stats.SCORE -> handleScoreLayout()
            Stats.LENGTH -> handleLengthLayout()
            Stats.SOURCE, Stats.TAG -> handleNoChartLayout()
            Stats.START_YEAR -> handleStartYearLayout()
            else -> {}
        }
    }

    private fun handlePieChart() {
        if (presenter.selectedStatsSort == StatsSort.MEAN_SCORE_DESC) {
            assignAdapter()
            return
        }

        val pieEntries = presenter.currentStats?.map {
            val progress = if (presenter.selectedStatsSort == StatsSort.COUNT_DESC) {
                it.count
            } else it.chaptersRead
            PieEntry(progress.toFloat(), it.label)
        }

        assignAdapter()
        if (pieEntries?.all { it.value == 0f } == true) return
        val pieDataSet = PieDataSet(pieEntries, "Pie Chart Distribution")
        pieDataSet.colors = presenter.currentStats?.map { it.color }
        setupPieChart(pieDataSet)
    }

    private fun handleScoreLayout() {
        val scoreMap = StatsHelper.SCORE_COLOR_MAP

        val barEntries = scoreMap.map { (score, _) ->
            BarEntry(
                score.toFloat(),
                presenter.currentStats?.find { it.label == score.toString() }?.count?.toFloat() ?: 0f,
            )
        }
        presenter.currentStats?.removeAll { it.count == 0 }
        assignAdapter()
        if (barEntries.all { it.y == 0f }) return
        val barDataSet = BarDataSet(barEntries, "Score Distribution")
        barDataSet.colors = scoreMap.values.toList()
        setupBarChart(barDataSet)
    }

    private fun handleLengthLayout() {
        val barEntries = ArrayList<BarEntry>()

        presenter.currentStats?.forEachIndexed { index, stats ->
            barEntries.add(BarEntry(index.toFloat(), stats.count.toFloat()))
        }

        val barDataSet = BarDataSet(barEntries, "Length Distribution")
        barDataSet.colors = presenter.currentStats?.map { it.color }
        setupBarChart(barDataSet, presenter.currentStats?.mapNotNull { it.label })
        presenter.currentStats?.removeAll { it.count == 0 }
        assignAdapter()
    }

    private fun handleNoChartLayout() {
        assignAdapter()
    }

    private fun handleStartYearLayout() {
        presenter.currentStats?.sortBy { it.label }

        val lineEntries = presenter.currentStats?.filterNot { it.label?.toFloatOrNull() == null }
            ?.map { Entry(it.label?.toFloat()!!, it.count.toFloat()) }

        assignAdapter()
        if (lineEntries.isNullOrEmpty()) return

        val lineDataSet = LineDataSet(lineEntries, "Start Year Distribution")
        lineDataSet.color = activity!!.getResourceColor(R.attr.colorOnBackground)
        lineDataSet.setDrawFilled(true)
        lineDataSet.fillDrawable = ContextCompat.getDrawable(activity!!, R.drawable.line_chart_fill)
        setupLineChart(lineDataSet)
    }

    private fun assignAdapter() {
        binding.statsRecyclerView.adapter = StatsDetailsAdapter(
            activity!!,
            presenter.currentStats ?: ArrayList(),
            presenter.selectedStat!!,
        ).also { adapter = it }
    }

    private fun setupPieChart(pieDataSet: PieDataSet) {
        with(binding) {
            statsPieChart.clear()
            statsPieChart.invalidate()

            statsPieChart.visibility = View.VISIBLE
            statsBarChart.visibility = View.GONE
            statsLineChart.visibility = View.GONE

            try {
                val pieData = PieData(pieDataSet)
                pieData.setDrawValues(false)

                statsPieChart.apply {
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
    }

    private fun setupBarChart(barDataSet: BarDataSet, xAxisLabel: List<String>? = null) {
        with(binding) {
            statsBarChart.data?.clearValues()
            statsBarChart.xAxis.valueFormatter = null
            statsBarChart.notifyDataSetChanged()
            statsBarChart.clear()
            statsBarChart.invalidate()

            statsPieChart.visibility = View.GONE
            statsBarChart.visibility = View.VISIBLE
            statsLineChart.visibility = View.GONE

            try {
                val newValueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = value.toInt().toString()
                }

                val barData = BarData(barDataSet)
                barData.setValueTextColor(activity!!.getResourceColor(R.attr.colorOnBackground))
                barData.barWidth = 0.6F
                barData.setValueFormatter(newValueFormatter)
                barData.setValueTextSize(10f)
                statsBarChart.axisLeft.isEnabled = false
                statsBarChart.axisRight.isEnabled = false

                statsBarChart.xAxis.apply {
                    setDrawGridLines(false)
                    position = XAxis.XAxisPosition.BOTTOM
                    setLabelCount(barDataSet.entryCount, false)
                    textColor = activity!!.getResourceColor(R.attr.colorOnBackground)

                    if (!xAxisLabel.isNullOrEmpty()) {
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return if (value < xAxisLabel.size) xAxisLabel[value.toInt()] else ""
                            }
                        }
                    }
                }

                statsBarChart.apply {
                    setTouchEnabled(false)
                    description.isEnabled = false
                    legend.isEnabled = false
                    data = barData
                    invalidate()
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    private fun setupLineChart(lineDataSet: LineDataSet) {
        with(binding) {
            statsLineChart.data?.clearValues()
            statsLineChart.notifyDataSetChanged()
            statsLineChart.clear()
            statsLineChart.invalidate()

            statsPieChart.visibility = View.GONE
            statsBarChart.visibility = View.GONE
            statsLineChart.visibility = View.VISIBLE

            try {
                val newValueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }

                val lineData = LineData(lineDataSet)
                lineData.setValueTextColor(activity!!.getResourceColor(R.attr.colorOnBackground))
                lineData.setValueFormatter(newValueFormatter)
                statsLineChart.axisLeft.isEnabled = false
                statsLineChart.axisRight.isEnabled = false

                statsLineChart.xAxis.apply {
                    setDrawGridLines(false)
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    textColor = activity!!.getResourceColor(R.attr.colorOnBackground)

                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return value.toInt().toString()
                        }
                    }
                }

                statsLineChart.apply {
                    description.isEnabled = false
                    legend.isEnabled = false
                    data = lineData
                    invalidate()
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }
}
