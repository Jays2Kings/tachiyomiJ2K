package eu.kanade.tachiyomi.ui.more.stats.details

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.github.mikephil.charting.components.MarkerView
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
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.MPPointF
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.databinding.StatsDetailsControllerBinding
import eu.kanade.tachiyomi.ui.base.SmallToolbarInterface
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper.getReadDuration
import eu.kanade.tachiyomi.ui.more.stats.details.StatsDetailsPresenter.Stats
import eu.kanade.tachiyomi.ui.more.stats.details.StatsDetailsPresenter.StatsSort
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toInt
import eu.kanade.tachiyomi.util.system.toUtcCalendar
import eu.kanade.tachiyomi.util.view.liftAppbarWith
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.setStyle
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class StatsDetailsController :
    BaseController<StatsDetailsControllerBinding>(),
    SmallToolbarInterface {

    private val presenter = StatsDetailsPresenter()
    private var query = ""
    private var adapter: StatsDetailsAdapter? = null
    lateinit var searchView: SearchView
    lateinit var searchItem: MenuItem

    private val defaultStat = Stats.SERIES_TYPE
    private val defaultSort = StatsSort.COUNT_DESC

    /**
     * Selected day in the read duration stat
     */
    private var highlightedDay: Calendar? = null

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

        resetAndSetup()
        initializeChips()
        with(binding) {
            statsDetailsRefreshLayout.setStyle()
            statsDetailsRefreshLayout.setOnRefreshListener {
                statsDetailsRefreshLayout.isRefreshing = false
                searchView.clearFocus()
                searchItem.collapseActionView()
                presenter.libraryMangas = presenter.getLibrary()
                resetAndSetup()
                initializeChips()
            }

            statsClearButton.setOnClickListener {
                resetFilters()
                searchView.clearFocus()
                searchItem.collapseActionView()
                resetAndSetup()
                initializeChips()
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
                    chipStat.text = activity?.getString(newSelection.resourceId)
                    presenter.selectedStat = newSelection
                    chipStat.setColors((presenter.selectedStat != defaultStat).toInt())

                    dialog.dismiss()
                    searchItem.collapseActionView()
                    resetAndSetup()
                }
                    ?.show()
            }
            chipStat.setOnCloseIconClickListener {
                if (presenter.selectedStat != defaultStat) {
                    presenter.selectedStat = defaultStat
                    searchItem.collapseActionView()
                    chipStat.reset(defaultStat.resourceId)
                } else chipStat.callOnClick()
            }
            chipSeriesType.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.seriesTypeStats,
                    presenter.selectedSeriesType,
                    R.string.series_type,
                    R.string.series_types,
                )
            }
            chipSeriesType.setOnCloseIconClickListener {
                if (presenter.selectedSeriesType.isNotEmpty()) {
                    presenter.selectedSeriesType = mutableSetOf()
                    chipSeriesType.reset(R.string.series_type)
                } else chipSeriesType.callOnClick()
            }
            chipSource.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.sources.toTypedArray(),
                    presenter.selectedSource,
                    R.string.source,
                    R.string.sources,
                )
            }
            chipSource.setOnCloseIconClickListener {
                if (presenter.selectedSource.isNotEmpty()) {
                    presenter.selectedSource = mutableSetOf()
                    chipSource.reset(R.string.source)
                } else chipSource.callOnClick()
            }
            chipStatus.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.statusStats,
                    presenter.selectedStatus,
                    R.string.status,
                    R.string.status,
                )
            }
            chipStatus.setOnCloseIconClickListener {
                if (presenter.selectedStatus.isNotEmpty()) {
                    presenter.selectedStatus = mutableSetOf()
                    chipStatus.reset(R.string.status)
                } else chipStatus.callOnClick()
            }
            chipLanguage.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.languagesStats,
                    presenter.selectedLanguage,
                    R.string.language,
                    R.string.languages,
                )
            }
            chipLanguage.setOnCloseIconClickListener {
                if (presenter.selectedLanguage.isNotEmpty()) {
                    presenter.selectedLanguage = mutableSetOf()
                    chipLanguage.reset(R.string.language)
                } else chipLanguage.callOnClick()
            }
            chipCategory.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.categoriesStats,
                    presenter.selectedCategory,
                    R.string.category,
                    R.string.categories,
                )
            }
            chipCategory.setOnCloseIconClickListener {
                if (presenter.selectedCategory.isNotEmpty()) {
                    presenter.selectedCategory = mutableSetOf()
                    chipCategory.reset(R.string.category)
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
                    chipSort.text = activity?.getString(newSelection.resourceId)
                    presenter.selectedStatsSort = newSelection
                    chipSort.setColors((presenter.selectedStatsSort != defaultSort).toInt())

                    dialog.dismiss()
                    presenter.sortCurrentStats()
                    resetAndSetup(updateStats = false, updateChipsVisibility = false)
                }
                    .show()
            }
            chipSort.setOnCloseIconClickListener {
                if (presenter.selectedStatsSort != defaultSort) {
                    presenter.selectedStatsSort = defaultSort
                    chipSort.reset(defaultSort.resourceId)
                } else chipSort.callOnClick()
            }
            statsDateText.setOnClickListener {
                val dialog = MaterialDatePicker.Builder.datePicker()
                    .setTitleText(R.string.read_duration_week)
                    .setSelection(presenter.startDate.timeInMillis.toUtcCalendar()?.timeInMillis)
                    .build()

                dialog.addOnPositiveButtonClickListener { utcMillis ->
                    presenter.updateReadDurationPeriod(utcMillis)
                    statsDateText.text = presenter.getPeriodString()
                    statsBarChart.highlightValues(null)
                    highlightedDay = null
                    resetAndSetup()
                    totalDurationStatsText.text = adapter?.list?.sumOf { it.readDuration }?.getReadDuration()
                }
                dialog.show((activity as AppCompatActivity).supportFragmentManager, activity?.getString(R.string.read_duration_week))
            }
            statsDateStartArrow.setOnClickListener {
                changeDatesReadDurationWithArrow(presenter.startDate, -1)
            }
            statsDateEndArrow.setOnClickListener {
                changeDatesReadDurationWithArrow(presenter.endDate, 1)
            }
        }
    }

    /**
     * Initialize the chips state
     */
    private fun initializeChips() {
        with(binding) {
            chipStat.text = activity?.getString(presenter.selectedStat?.resourceId ?: defaultStat.resourceId)
            chipStat.setColors((presenter.selectedStat != defaultStat).toInt())
            chipSeriesType.setState(presenter.selectedSeriesType, R.string.series_type, R.string.series_type)
            chipSource.setState(presenter.selectedSource, R.string.source, R.string.sources)
            chipStatus.setState(presenter.selectedStatus, R.string.status, R.string.status)
            chipLanguage.setState(presenter.selectedLanguage, R.string.language, R.string.languages)
            chipCategory.setState(presenter.selectedCategory, R.string.category, R.string.categories, true)
            chipSort.text = activity?.getString(presenter.selectedStatsSort?.resourceId ?: defaultSort.resourceId)
            chipSort.setColors((presenter.selectedStatsSort != defaultSort).toInt())
        }
    }

    /**
     * Changes dates of the read duration stat with the arrows
     * @param referenceDate date used to determine if should change week
     * @param weeksToAdd number of weeks to add or remove
     */
    private fun changeDatesReadDurationWithArrow(referenceDate: Calendar, weeksToAdd: Int) {
        with(binding) {
            if (highlightedDay == null) {
                changeWeekReadDuration(weeksToAdd)
                statsDateText.text = presenter.getPeriodString()
            } else {
                val newDaySelected = highlightedDay?.get(Calendar.DAY_OF_MONTH)
                val endDay = referenceDate.get(Calendar.DAY_OF_MONTH)
                statsBarChart.highlightValues(null)
                if (newDaySelected == endDay) {
                    changeWeekReadDuration(weeksToAdd)
                    if (!statsBarChart.isVisible) {
                        highlightedDay = null
                        statsDateText.text = presenter.getPeriodString()
                        return
                    }
                }
                highlightedDay = Calendar.getInstance().apply {
                    timeInMillis = highlightedDay!!.timeInMillis
                    add(Calendar.DAY_OF_WEEK, weeksToAdd)
                }
                val highlightValue = presenter.historyByDayAndManga.keys.toTypedArray()
                    .indexOfFirst { it.get(Calendar.DAY_OF_MONTH) == highlightedDay?.get(Calendar.DAY_OF_MONTH) }
                statsBarChart.highlightValue(highlightValue.toFloat(), 0)
                statsBarChart.marker.refreshContent(
                    statsBarChart.data.dataSets[0].getEntryForXValue(highlightValue.toFloat(), 0f),
                    statsBarChart.getHighlightByTouchPoint(highlightValue.toFloat(), 0f),
                )
            }
        }
    }

    /**
     * Changes week of the read duration stat
     * @param weeksToAdd number of weeks to add or remove
     */
    private fun changeWeekReadDuration(weeksToAdd: Int) {
        presenter.startDate.apply { add(Calendar.WEEK_OF_YEAR, weeksToAdd) }
        presenter.endDate.apply { add(Calendar.WEEK_OF_YEAR, weeksToAdd) }
        presenter.history = presenter.getMangaHistoryGroupedByDay()
        resetAndSetup(updateChipsVisibility = false)
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

    /**
     * Listener to update adapter when searchView text changes
     */
    private fun setSearchViewListener(searchView: SearchView?) {
        setOnQueryTextChangeListener(searchView) {
            query = it ?: ""
            adapter?.filter(query)
            true
        }
    }

    /**
     * Displays a multi choice dialog according to the chip selected
     * @param statsList list of values depending of the stat chip
     * @param selectedValues list of already selected values
     * @param resourceId default string resource when no values are selected
     * @param resourceIdPlural string resource when more than 2 values are selected
     */
    private fun <T> Chip.setMultiChoiceItemsDialog(
        statsList: Array<T>,
        selectedValues: MutableSet<T>,
        resourceId: Int,
        resourceIdPlural: Int,
    ) {
        val isCategory = statsList.isArrayOf<Category>()
        val items = statsList.map { if (isCategory) (it as Category).name else it.toString() }.toTypedArray()
        searchView.clearFocus()
        activity!!.materialAlertDialog().setMultiChoiceItems(
            items,
            statsList.map { it in selectedValues }.toBooleanArray(),
        ) { _, which, checked ->
            val newSelection = statsList[which]
            if (checked) {
                selectedValues.add(newSelection)
            } else {
                selectedValues.remove(newSelection)
            }
            setState(selectedValues, resourceId, resourceIdPlural, isCategory)
            updateChipsVisibility()
        }.setOnDismissListener {
            binding.progress.isVisible = true
            resetAndSetup(updateChipsVisibility = false)
        }
            .show()
    }

    /**
     * Reset the layout and setup the chart to display
     * @param updateStats whether to recalculate the displayed stats
     * @param updateChipsVisibility whether to update the chips visibility
     */
    private fun resetAndSetup(updateStats: Boolean = true, updateChipsVisibility: Boolean = true) {
        resetLayout(updateChipsVisibility)
        setupStatistic(updateStats)
    }

    /**
     * Reset the text of the chip selected and reset layout
     * @param resourceId string resource of the stat name
     */
    private fun Chip.reset(resourceId: Int) {
        resetAndSetup()
        this.setColors(0)
        this.text = activity?.getString(resourceId)
    }

    /**
     * Reset the layout to the default state
     * @param updateChipsVisibility whether to update the chips visibility
     */
    private fun resetLayout(updateChipsVisibility: Boolean = false) {
        with(binding) {
            progress.isVisible = true
            statsDetailsScrollView.isVisible = false
            statsDetailsScrollView.scrollTo(0, 0)
            statsPieChart.visibility = View.GONE
            statsBarChart.visibility = View.GONE
            statsLineChart.visibility = View.GONE

            if (updateChipsVisibility) {
                highlightedDay = null
                statsDateLayout.isVisible = presenter.selectedStat == Stats.READ_DURATION
                totalDurationStatsText.isVisible = presenter.selectedStat == Stats.READ_DURATION
                statsDateText.text = presenter.getPeriodString()
                updateChipsVisibility()
            }
        }
    }

    /**
     * Setup the statistics and charts
     * @param updateStats whether to recalculate the displayed stats
     */
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
            totalDurationStatsText.text = adapter?.list?.sumOf { it.readDuration }?.getReadDuration()
        }
    }

    /**
     * Update the chips visibility according to the selected stat
     */
    private fun updateChipsVisibility() {
        with(binding) {
            statsClearButton.isVisible = hasActiveFilters()
            chipSeriesType.isVisible = presenter.selectedStat !in listOf(Stats.SERIES_TYPE, Stats.READ_DURATION)
            chipSource.isVisible =
                presenter.selectedStat !in listOf(Stats.LANGUAGE, Stats.SOURCE, Stats.READ_DURATION) &&
                presenter.selectedLanguage.isEmpty()
            chipStatus.isVisible = presenter.selectedStat !in listOf(Stats.STATUS, Stats.READ_DURATION)
            chipLanguage.isVisible = presenter.selectedStat !in listOf(Stats.LANGUAGE, Stats.READ_DURATION) &&
                (presenter.selectedStat == Stats.SOURCE || presenter.selectedSource.isEmpty())
            chipCategory.isVisible = presenter.selectedStat !in listOf(Stats.CATEGORY, Stats.READ_DURATION)
            chipSort.isVisible = presenter.selectedStat !in listOf(
                Stats.SCORE, Stats.LENGTH, Stats.START_YEAR, Stats.READ_DURATION,
            )
        }
    }

    /**
     * Update the chip state according to the number of selected values
     */
    private fun <T> Chip.setState(
        selectedValues: MutableSet<T>,
        resourceId: Int,
        resourceIdPlural: Int,
        isCategory: Boolean = false,
    ) {
        this.setColors(selectedValues.size)
        this.text = when (selectedValues.size) {
            0 -> activity?.getString(resourceId)
            1 -> if (isCategory) (selectedValues.first() as Category).name else selectedValues.first().toString()
            else -> "${selectedValues.size} ${activity?.getString(resourceIdPlural)}"
        }
    }

    /**
     * Reset all the filters selected
     */
    private fun resetFilters() {
        with(binding) {
            presenter.selectedStat = defaultStat
            chipStat.text = activity?.getString(defaultStat.resourceId)
            presenter.selectedSeriesType = mutableSetOf()
            chipSeriesType.text = activity?.getString(R.string.series_type)
            presenter.selectedSource = mutableSetOf()
            chipSource.text = activity?.getString(R.string.source)
            presenter.selectedStatus = mutableSetOf()
            chipStatus.text = activity?.getString(R.string.status)
            presenter.selectedLanguage = mutableSetOf()
            chipLanguage.text = activity?.getString(R.string.language)
            presenter.selectedCategory = mutableSetOf()
            chipCategory.text = activity?.getString(R.string.category)
            presenter.selectedStatsSort = defaultSort
            chipSort.text = activity?.getString(defaultSort.resourceId)
        }
    }

    private fun hasActiveFilters() = with(presenter) {
        listOf(selectedStat, selectedStatsSort).any { it !in listOf(null, defaultStat, defaultSort) } ||
            listOf(selectedSeriesType, selectedSource, selectedStatus, selectedLanguage, selectedCategory).any {
                it.isNotEmpty()
            }
    }

    fun Chip.setColors(sizeStat: Int) {
        val emptyTextColor = activity!!.getResourceColor(R.attr.colorOnBackground)
        val filteredBackColor = activity!!.getResourceColor(R.attr.colorSecondary)
        val emptyBackColor = activity!!.getResourceColor(R.attr.colorSurface)
        setTextColor(if (sizeStat == 0) emptyTextColor else emptyBackColor)
        chipBackgroundColor = ColorStateList.valueOf(if (sizeStat == 0) emptyBackColor else filteredBackColor)
        closeIcon = if (sizeStat == 0) context.contextCompatDrawable(R.drawable.ic_arrow_drop_down_24dp) else {
            context.contextCompatDrawable(R.drawable.ic_close_24dp)
        }
        closeIconTint = ColorStateList.valueOf(if (sizeStat == 0) emptyTextColor else emptyBackColor)
        isChipIconVisible = this in listOf(binding.chipStat, binding.chipSort) || sizeStat == 1
        chipIconTint = ColorStateList.valueOf(if (sizeStat == 0) emptyTextColor else emptyBackColor)
    }

    /**
     * Handle which layout should be displayed according to the selected stat
     */
    private fun handleLayout() {
        when (presenter.selectedStat) {
            Stats.SERIES_TYPE, Stats.STATUS, Stats.LANGUAGE, Stats.TRACKER, Stats.CATEGORY -> handlePieChart()
            Stats.SCORE -> handleScoreLayout()
            Stats.LENGTH -> handleLengthLayout()
            Stats.SOURCE, Stats.TAG -> handleNoChartLayout()
            Stats.START_YEAR -> handleStartYearLayout()
            Stats.READ_DURATION -> handleReadDurationLayout()
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

    private fun handleReadDurationLayout() {
        val barEntries = ArrayList<BarEntry>()

        presenter.historyByDayAndManga.entries.forEachIndexed { index, entry ->
            barEntries.add(
                BarEntry(
                    index.toFloat(),
                    entry.value.values.sumOf { it.sumOf { h -> h.time_read } }.toFloat(),
                ),
            )
        }

        assignAdapter()
        if (barEntries.all { it.y == 0f }) return
        val barDataSet = BarDataSet(barEntries, "Read Duration Distribution")
        barDataSet.color = StatsHelper.PIE_CHART_COLOR_LIST[1]
        setupBarChart(
            barDataSet,
            presenter.historyByDayAndManga.keys.map { presenter.getCalendarShortDay(it) }.toList(),
            true,
        )
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
        if (query.isNotBlank()) adapter?.filter(query)
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

    private fun setupBarChart(barDataSet: BarDataSet, xAxisLabel: List<String>? = null, touchEnabled: Boolean = false) {
        with(binding) {
            statsBarChart.data?.clearValues()
            statsBarChart.xAxis.valueFormatter = null
            statsBarChart.notifyDataSetChanged()
            statsBarChart.clear()
            statsBarChart.invalidate()
            statsBarChart.axisLeft.resetAxisMinimum()
            statsBarChart.axisLeft.resetAxisMaximum()

            statsPieChart.visibility = View.GONE
            statsBarChart.visibility = View.VISIBLE
            statsLineChart.visibility = View.GONE

            try {
                val newValueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) =
                        if (touchEnabled) value.toLong().getReadDuration() else value.toInt().toString()
                }

                val barData = BarData(barDataSet)
                barData.setValueTextColor(activity!!.getResourceColor(R.attr.colorOnBackground))
                barData.barWidth = 0.6F
                barData.setValueFormatter(newValueFormatter)
                barData.setValueTextSize(10f)
                barData.setDrawValues(!touchEnabled)
                statsBarChart.axisLeft.isEnabled = touchEnabled
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
                    setTouchEnabled(touchEnabled)
                    isDragEnabled = false
                    isDoubleTapToZoomEnabled = false
                    description.isEnabled = false
                    legend.isEnabled = false

                    if (touchEnabled) {
                        val mv = MyMarkerView(activity, R.layout.custom_marker_view)
                        mv.chartView = this
                        marker = mv

                        axisLeft.apply {
                            textColor = activity!!.getResourceColor(R.attr.colorOnBackground)
                            axisLineColor = activity!!.getResourceColor(R.attr.colorOnBackground)
                            valueFormatter = newValueFormatter
                            val topValue = barData.yMax.getRoundedMaxLabel()
                            axisMaximum = topValue
                            axisMinimum = 0f
                            setLabelCount(4, true)
                        }

                        setOnChartValueSelectedListener(
                            object : OnChartValueSelectedListener {
                                override fun onValueSelected(e: Entry, h: Highlight) {
                                    highlightValue(h)
                                    highlightedDay = presenter.historyByDayAndManga.keys.toTypedArray()[e.x.toInt()]
                                    statsDateText.text = presenter.convertCalendarToString(highlightedDay!!)
                                    presenter.setupReadDuration(highlightedDay)
                                    assignAdapter()
                                    totalDurationStatsText.text =
                                        adapter?.list?.sumOf { it.readDuration }?.getReadDuration()
                                }

                                override fun onNothingSelected() {
                                    presenter.setupReadDuration()
                                    highlightedDay = null
                                    statsDateText.text = presenter.getPeriodString()
                                    assignAdapter()
                                    totalDurationStatsText.text =
                                        adapter?.list?.sumOf { it.readDuration }?.getReadDuration()
                                }
                            },
                        )
                    }
                    data = barData
                    invalidate()
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    /**
     * Round the rounded max label of the bar chart to avoid weird values
     */
    private fun Float.getRoundedMaxLabel(): Float {
        val longValue = toLong()
        val hours = TimeUnit.MILLISECONDS.toHours(longValue) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(longValue) % 60

        val multiple = when {
            hours > 1L -> 3600 / 2 // 30min
            minutes >= 15L || hours == 1L -> 300 * 3 // 15min
            else -> 60 * 3 // 3min
        } * 1000
        return ceil(this / multiple) * multiple
    }

    private fun setupLineChart(lineDataSet: LineDataSet) {
        with(binding) {
            statsLineChart.data?.clearValues()
            statsLineChart.fitScreen()
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
                lineData.setValueTextSize(10f)
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

    /**
     * Custom MarkerView displayed when a bar is selected in the bar chart
     */
    inner class MyMarkerView(context: Context?, layoutResource: Int) : MarkerView(context, layoutResource) {

        private val markerText: TextView = findViewById(R.id.marker_text)

        override fun refreshContent(e: Entry, highlight: Highlight) {
            markerText.text = e.y.toLong().getReadDuration()
            super.refreshContent(e, highlight)
        }

        override fun getOffset(): MPPointF {
            return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
        }
    }
}
