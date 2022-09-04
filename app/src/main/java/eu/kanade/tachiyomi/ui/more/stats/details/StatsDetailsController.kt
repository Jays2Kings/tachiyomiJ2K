package eu.kanade.tachiyomi.ui.more.stats.details

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.annotation.PluralsRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.util.Pair
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
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
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper.getReadDuration
import eu.kanade.tachiyomi.ui.more.stats.details.StatsDetailsPresenter.Stats
import eu.kanade.tachiyomi.ui.more.stats.details.StatsDetailsPresenter.StatsSort
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toInt
import eu.kanade.tachiyomi.util.system.toLocalCalendar
import eu.kanade.tachiyomi.util.system.toUtcCalendar
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.view.backgroundColor
import eu.kanade.tachiyomi.util.view.compatToolTipText
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.liftAppbarWith
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class StatsDetailsController :
    BaseCoroutineController<StatsDetailsControllerBinding, StatsDetailsPresenter>(),
    SmallToolbarInterface {

    override val presenter = StatsDetailsPresenter()
    private var query = ""
    private var adapter: StatsDetailsAdapter? = null
    lateinit var searchView: SearchView
    lateinit var searchItem: MenuItem

    private val defaultStat = Stats.SERIES_TYPE
    private val defaultSort = StatsSort.COUNT_DESC
    private var toolbarIsColored = false
    private var colorAnimator: ValueAnimator? = null

    /**
     * Selected day in the read duration stat
     */
    private var highlightedDay: Calendar? = null
    private var jobReadDuration: Job? = null

    /**
     * Returns the toolbar title to show when this controller is attached.
     */
    override fun getTitle() = resources?.getString(R.string.statistics_details)

    override fun createBinding(inflater: LayoutInflater) = StatsDetailsControllerBinding.inflate(inflater)

    val scrollView: View
        get() = binding.statsDetailsScrollView ?: binding.statsRecyclerView

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        liftAppbarWith(scrollView, false, liftOnScroll = { colorToolbar(it) })
        setHasOptionsMenu(true)

        if (presenter.selectedStat == null) {
            resetFilters(true)
        }

        resetAndSetup()
        initializeChips()
        with(binding) {
            chartLinearLayout?.doOnApplyWindowInsetsCompat { view, insets, _ ->
                view.updatePaddingRelative(
                    bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom,
                )
            }

            statsClearButtonContainer.compatToolTipText = activity?.getString(R.string.clear_filters)
            statsClearButtonContainer.setOnClickListener {
                resetFilters()
                searchView.clearFocus()
                searchItem.collapseActionView()
                resetAndSetup()
                initializeChips()
            }

            chipStat.setOnClickListener {
                searchView.clearFocus()
                activity?.materialAlertDialog()
                    ?.setTitle(R.string.stat)
                    ?.setSingleChoiceItems(
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
                    ?.setNegativeButton(android.R.string.cancel, null)
                    ?.show()
            }
            chipSeriesType.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.seriesTypeStats,
                    presenter.selectedSeriesType,
                    R.string.series_type,
                    R.plurals._series_types,
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
                    R.plurals._sources,
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
                    R.plurals._statuses,
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
                    R.plurals._languages,
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
                    R.plurals.category_plural,
                )
            }
            chipCategory.setOnCloseIconClickListener {
                if (presenter.selectedCategory.isNotEmpty()) {
                    presenter.selectedCategory = mutableSetOf()
                    chipCategory.reset(R.string.category)
                } else chipCategory.callOnClick()
            }
            statSort.setOnClickListener {
                searchView.clearFocus()
                activity!!.materialAlertDialog()
                    .setTitle(R.string.sort_by)
                    .setSingleChoiceItems(
                        presenter.getSortDataArray(),
                        StatsSort.values().indexOf(presenter.selectedStatsSort),
                    ) { dialog, which ->
                        val newSelection = StatsSort.values()[which]
                        if (newSelection == presenter.selectedStatsSort) return@setSingleChoiceItems
                        statSort.text = activity?.getString(newSelection.resourceId)
                        presenter.selectedStatsSort = newSelection

                        dialog.dismiss()
                        presenter.sortCurrentStats()
                        resetLayout()
                        updateStats()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            statsDateText.setOnClickListener {
                val dialog = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText(R.string.read_duration)
                    .setSelection(
                        Pair(
                            presenter.startDate.timeInMillis.toUtcCalendar()?.timeInMillis,
                            presenter.endDate.timeInMillis.toUtcCalendar()?.timeInMillis,
                        ),
                    )
                    .build()

                dialog.addOnPositiveButtonClickListener { utcMillis ->
                    binding.progress.isVisible = true
                    viewScope.launch {
                        presenter.updateReadDurationPeriod(
                            utcMillis.first.toLocalCalendar()?.timeInMillis ?: utcMillis.first,
                            utcMillis.second.toLocalCalendar()?.timeInMillis
                                ?: utcMillis.second,
                        )
                        withIOContext { presenter.updateMangaHistory() }
                        binding.statsDateText.text = presenter.getPeriodString()
                        statsBarChart.highlightValues(null)
                        highlightedDay = null
                        presenter.getStatisticData()
                    }
                }
                dialog.show((activity as AppCompatActivity).supportFragmentManager, activity?.getString(R.string.read_duration))
            }
            statsDateStartArrow.setOnClickListener {
                changeDatesReadDurationWithArrow(presenter.startDate, -1)
            }
            statsDateEndArrow.setOnClickListener {
                changeDatesReadDurationWithArrow(presenter.endDate, 1)
            }
        }
    }

    /** Set the toolbar to fully transparent or colored and translucent */
    private fun colorToolbar(isColor: Boolean) {
        if (isColor == toolbarIsColored) return
        val activity = activity ?: return
        toolbarIsColored = isColor
        if (isControllerVisible) setTitle()
        val scrollingColor = activity.getResourceColor(R.attr.colorPrimaryVariant)
        val topColor = activity.getResourceColor(R.attr.colorSurface)
        colorAnimator?.cancel()
        val view = binding.filterConstraintLayout ?: binding.statsHorizontalScroll
        val percent = ImageUtil.getPercentOfColor(
            view.backgroundColor ?: Color.TRANSPARENT,
            activity.getResourceColor(R.attr.colorSurface),
            activity.getResourceColor(R.attr.colorPrimaryVariant),
        )
        val cA = ValueAnimator.ofFloat(
            percent,
            toolbarIsColored.toInt().toFloat(),
        )
        colorAnimator = cA
        colorAnimator?.addUpdateListener { animator ->
            view.setBackgroundColor(
                ColorUtils.blendARGB(
                    topColor,
                    scrollingColor,
                    animator.animatedValue as Float,
                ),
            )
        }
        cA.start()
    }

    /**
     * Initialize the chips state
     */
    private fun initializeChips() {
        with(binding) {
            chipStat.text = activity?.getString(presenter.selectedStat?.resourceId ?: defaultStat.resourceId)
            chipStat.setColors((presenter.selectedStat != defaultStat).toInt())
            chipSeriesType.setState(presenter.selectedSeriesType, R.string.series_type, R.plurals._series_types)
            chipSource.setState(presenter.selectedSource, R.string.source, R.plurals._sources)
            chipStatus.setState(presenter.selectedStatus, R.string.status, R.plurals._statuses)
            chipLanguage.setState(presenter.selectedLanguage, R.string.language, R.plurals._languages)
            chipCategory.setState(presenter.selectedCategory, R.string.category, R.plurals.category_plural, true)
            statSort.text = activity?.getString(presenter.selectedStatsSort?.resourceId ?: defaultSort.resourceId)
        }
    }

    /**
     * Changes dates of the read duration stat with the arrows
     * @param referenceDate date used to determine if should change week
     * @param toAdd whether to add or remove
     */
    private fun changeDatesReadDurationWithArrow(referenceDate: Calendar, toAdd: Int) {
        jobReadDuration?.cancel()
        with(binding) {
            if (highlightedDay == null) {
                changeReadDurationPeriod(toAdd)
            } else {
                val daySelected = highlightedDay?.get(Calendar.DAY_OF_YEAR)
                val endDay = referenceDate.get(Calendar.DAY_OF_YEAR)
                statsBarChart.highlightValues(null)
                highlightedDay = Calendar.getInstance().apply {
                    timeInMillis = highlightedDay!!.timeInMillis
                    add(Calendar.DAY_OF_YEAR, toAdd)
                }
                if (daySelected == endDay) {
                    changeReadDurationPeriod(toAdd)
                } else updateHighlightedValue()
            }
        }
    }

    /**
     * Changes week of the read duration stat
     * @param toAdd whether to add or remove
     */
    private fun changeReadDurationPeriod(toAdd: Int) {
        val millionSeconds = presenter.endDate.timeInMillis - presenter.startDate.timeInMillis
        val days = TimeUnit.MILLISECONDS.toDays(millionSeconds) + 1
        presenter.startDate.add(Calendar.DAY_OF_YEAR, toAdd * days.toInt())
        presenter.updateReadDurationPeriod(presenter.startDate.timeInMillis, days.toInt())
        binding.progress.isVisible = true
        jobReadDuration = viewScope.launchIO {
            presenter.updateMangaHistory()
            presenter.getStatisticData()
        }
    }

    private fun updateHighlightedValue() {
        with(binding) {
            val highlightValue = presenter.historyByDayAndManga.keys.toTypedArray().indexOfFirst {
                it.get(Calendar.DAY_OF_YEAR) == highlightedDay?.get(Calendar.DAY_OF_YEAR) &&
                    it.get(Calendar.YEAR) == highlightedDay?.get(Calendar.YEAR)
            }
            if (highlightValue == -1) return
            statsBarChart.highlightValue(highlightValue.toFloat(), 0)
            statsBarChart.marker.refreshContent(
                statsBarChart.data.dataSets[0].getEntryForXValue(highlightValue.toFloat(), 0f),
                statsBarChart.getHighlightByTouchPoint(highlightValue.toFloat(), 0f),
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.stats_bar, menu)
        searchItem = menu.findItem(R.id.action_search)
        searchView = searchItem.actionView as SearchView
        searchView.queryHint = activity?.getString(R.string.search_, activity?.getString(R.string.statistics)?.lowercase(Locale.ROOT))
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
        @PluralsRes
        resourceIdPlural: Int,
    ) {
        val tempValues = selectedValues.toMutableSet()
        val isCategory = statsList.isArrayOf<Category>()
        val items = statsList.map { if (isCategory) (it as Category).name else it.toString() }
            .toTypedArray()
        searchView.clearFocus()
        activity!!.materialAlertDialog()
            .setTitle(resourceId)
            .setMultiChoiceItems(
                items,
                statsList.map { it in selectedValues }.toBooleanArray(),
            ) { _, which, checked ->
                val newSelection = statsList[which]
                if (checked) {
                    tempValues.add(newSelection)
                } else {
                    tempValues.remove(newSelection)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                selectedValues.clear()
                selectedValues.addAll(tempValues)
                setState(selectedValues, resourceId, resourceIdPlural, isCategory)
                binding.progress.isVisible = true
                resetAndSetup(resetReadDuration = false)
            }
            .show()
    }

    /**
     * Reset the layout and setup the chart to display
     * @param updateChipsVisibility whether to update the chips visibility
     * @param resetReadDuration whether to reset the read duration values
     */
    private fun resetAndSetup(
        updateChipsVisibility: Boolean = true,
        resetReadDuration: Boolean = true,
    ) {
        resetLayout(updateChipsVisibility, resetReadDuration)
        presenter.getStatisticData()
    }

    /**
     * Reset the text of the chip selected and reset layout
     * @param resourceId string resource of the stat name
     */
    private fun Chip.reset(resourceId: Int) {
        resetAndSetup(resetReadDuration = false)
        this.setColors(0)
        this.text = activity?.getString(resourceId)
    }

    /**
     * Reset the layout to the default state
     * @param updateChipsVisibility whether to update the chips visibility
     * @param resetReadDuration whether to reset the read duration values
     */
    private fun resetLayout(updateChipsVisibility: Boolean = false, resetReadDuration: Boolean = false) {
        with(binding) {
            progress.isVisible = true
            scrollView.isInvisible = true
            scrollView.scrollTo(0, 0)
            chartLinearLayout?.isVisible = false
            statsPieChart.isVisible = false
            statsBarChart.isVisible = false
            statsLineChart.isVisible = false

            if (resetReadDuration) {
                highlightedDay = null
                statsDateLayout.isVisible = presenter.selectedStat == Stats.READ_DURATION
                totalDurationStatsText.isVisible = presenter.selectedStat == Stats.READ_DURATION
                statsDateText.text = presenter.getPeriodString()
            }
            if (updateChipsVisibility) updateChipsVisibility()
        }
    }

    fun updateStats() {
        val currentStats = presenter.currentStats
        with(binding) {
            val hasNoData = currentStats.isNullOrEmpty() || currentStats.all { it.count == 0 }
            if (hasNoData) {
                binding.noChartData.show(R.drawable.ic_heart_off_24dp, R.string.no_data_for_filters)
                presenter.currentStats?.removeAll { it.count == 0 }
                handleNoChartLayout()
                chartLinearLayout?.isVisible = false
                statsPieChart.isVisible = false
                statsBarChart.isVisible = false
                statsLineChart.isVisible = false
                highlightedDay = null
            } else {
                binding.noChartData.hide()
                handleLayout()
            }
            scrollView.isVisible = true
            progress.isVisible = false
            totalDurationStatsText.text = adapter?.list?.sumOf { it.readDuration }?.getReadDuration()
            if (highlightedDay != null) updateHighlightedValue() else statsDateText.text = presenter.getPeriodString()
        }
    }

    /**
     * Update the chips visibility according to the selected stat
     */
    private fun updateChipsVisibility() {
        with(binding) {
            statsClearButtonContainer.isVisible = hasActiveFilters()
            chipSeriesType.isVisible = presenter.selectedStat !in listOf(Stats.SERIES_TYPE, Stats.READ_DURATION)
            chipSource.isVisible =
                presenter.selectedStat !in listOf(Stats.LANGUAGE, Stats.SOURCE, Stats.READ_DURATION) &&
                presenter.selectedLanguage.isEmpty()
            chipStatus.isVisible = presenter.selectedStat !in listOf(Stats.STATUS, Stats.READ_DURATION)
            chipLanguage.isVisible = presenter.selectedStat !in listOf(Stats.LANGUAGE, Stats.READ_DURATION) &&
                (presenter.selectedStat == Stats.SOURCE || presenter.selectedSource.isEmpty())
            chipCategory.isVisible = presenter.selectedStat !in listOf(Stats.CATEGORY, Stats.READ_DURATION) &&
                presenter.categoriesStats.size > 1
            statSort.isVisible = presenter.selectedStat !in listOf(
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
        @PluralsRes
        resourceIdPlural: Int,
        isCategory: Boolean = false,
    ) {
        this.setColors(selectedValues.size)
        this.text = when (selectedValues.size) {
            0 -> activity?.getString(resourceId)
            1 -> if (isCategory) (selectedValues.first() as Category).name else selectedValues.first().toString()
            else -> activity?.resources?.getQuantityString(resourceIdPlural, selectedValues.size, selectedValues.size)
        }
    }

    /**
     * Reset all the filters selected
     */
    private fun resetFilters(init: Boolean = false) {
        with(binding) {
            if (init) {
                presenter.selectedStat = defaultStat
                chipStat.text = activity?.getString(defaultStat.resourceId)
                presenter.selectedStatsSort = defaultSort
                statSort.text = activity?.getString(defaultSort.resourceId)
            }
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
        }
    }

    private fun hasActiveFilters() = with(presenter) {
        listOf(selectedSeriesType, selectedSource, selectedStatus, selectedLanguage, selectedCategory).any {
            it.isNotEmpty()
        }
    }

    fun Chip.setColors(sizeStat: Int) {
        val emptyTextColor = activity!!.getResourceColor(R.attr.colorOnBackground)
        val filteredBackColor = activity!!.getResourceColor(R.attr.colorSecondary)
        val emptyBackColor = activity!!.getResourceColor(R.attr.colorSurface)
        val alwaysShowIcon = this == binding.chipStat
        val neverSelect = alwaysShowIcon || sizeStat == 0
        setTextColor(if (neverSelect) emptyTextColor else emptyBackColor)
        chipBackgroundColor = ColorStateList.valueOf(if (neverSelect) emptyBackColor else filteredBackColor)
        closeIcon = if (neverSelect) context.contextCompatDrawable(R.drawable.ic_arrow_drop_down_24dp) else {
            context.contextCompatDrawable(R.drawable.ic_close_24dp)
        }
        closeIconTint = ColorStateList.valueOf(if (neverSelect) emptyTextColor else emptyBackColor)
        isChipIconVisible = alwaysShowIcon || sizeStat == 1
        chipIconTint = ColorStateList.valueOf(if (neverSelect) emptyTextColor else emptyBackColor)
    }

    /**
     * Handle which layout should be displayed according to the selected stat
     */
    private fun handleLayout() {
        binding.chartLinearLayout?.updateLayoutParams<ConstraintLayout.LayoutParams> {
            matchConstraintPercentWidth = 0.5f
        }
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
        if (barEntries.all { it.y == 0f }) {
            binding.statsBarChart.isVisible = false
            return
        }
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

            chartLinearLayout?.isVisible = true
            statsPieChart.isVisible = true
            statsBarChart.isVisible = false
            statsLineChart.isVisible = false
            chartLinearLayout?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                matchConstraintPercentWidth = 0.33f
            }

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

            chartLinearLayout?.isVisible = true
            statsPieChart.isVisible = false
            statsBarChart.isVisible = true
            statsLineChart.isVisible = false

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
                                    statsDateText.text = presenter.convertCalendarToLongString(highlightedDay!!)
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

            chartLinearLayout?.isVisible = true
            statsPieChart.isVisible = false
            statsBarChart.isVisible = false
            statsLineChart.isVisible = true

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
