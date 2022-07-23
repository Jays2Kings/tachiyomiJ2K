package eu.kanade.tachiyomi.ui.more.stats.details

import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.mapSerieType
import eu.kanade.tachiyomi.util.mapStatus
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.roundToTwoDecimal
import eu.kanade.tachiyomi.util.system.toUtcCalendar
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import kotlin.math.roundToInt

class StatsDetailsPresenter(
    private val db: DatabaseHelper = Injekt.get(),
    prefs: PreferencesHelper = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) {

    private var context = prefs.context
    var libraryMangas = getLibrary()
        set(value) {
            field = value
            mangasDistinct = field.distinct()
        }
    private var mangasDistinct = libraryMangas.distinct()
    val sources = getEnabledSources()

    var selectedStat: Stats? = null
    var selectedSerieType = mutableSetOf<String>()
    var selectedSource = mutableSetOf<Source>()
    var selectedStatus = mutableSetOf<String>()
    var selectedLanguage = mutableSetOf<String>()
    var selectedCategory = mutableSetOf<Category>()
    var selectedStatsSort: StatsSort? = null

    var currentStats: ArrayList<StatsData>? = null
    val serieTypeStats = arrayOf(
        context.getString(R.string.manga),
        context.getString(R.string.manhwa),
        context.getString(R.string.manhua),
        context.getString(R.string.comic),
        context.getString(R.string.webtoon),
    )
    val statusStats = arrayOf(
        context.getString(R.string.ongoing),
        context.getString(R.string.completed),
        context.getString(R.string.licensed),
        context.getString(R.string.publishing_finished),
        context.getString(R.string.cancelled),
        context.getString(R.string.on_hiatus),
    )
    private val defaultCategory =
        if (libraryMangas.any { it.category == 0 }) arrayOf(Category.createDefault(context)) else emptyArray()
    val categoriesStats = defaultCategory + getCategories().sortedBy { it.name }.toTypedArray()
    val languagesStats = prefs.enabledLanguages().get().map { lang -> LocaleHelper.getSourceDisplayName(lang, context) }
        .sorted().toTypedArray()

    private val pieColorList = StatsHelper.PIE_CHART_COLOR_LIST

    fun getStatisticData() {
        if (selectedStat == null || selectedStatsSort == null) {
            return
        }

        when (selectedStat) {
            Stats.SERIE_TYPE -> setupSeriesType()
            Stats.STATUS -> setupStatus()
            Stats.SCORE -> setupScores()
            Stats.LANGUAGE -> setupLanguages()
            Stats.LENGTH -> setupLength()
            Stats.TRACKER -> setupTrackers()
            Stats.SOURCE -> setupSources()
            Stats.CATEGORY -> setupCategories()
            Stats.TAG -> setupTags()
            Stats.START_YEAR -> setupStartYear()
            else -> {}
        }
    }

    private fun setupSeriesType() {
        currentStats = ArrayList()
        val libraryFormat = mangasDistinct.filterByChip().groupBy { it.seriesType() }

        libraryFormat.forEach { (seriesType, mangaList) ->
            currentStats?.add(
                StatsData(
                    color = pieColorList[currentStats?.size!!],
                    count = mangaList.count(),
                    meanScore = mangaList.getMeanScoreRounded(),
                    chaptersRead = mangaList.sumOf { it.read },
                    totalChapters = mangaList.sumOf { it.totalChapters },
                    label = context.mapSerieType(seriesType).uppercase(),
                ),
            )
        }
        sortCurrentStats()
    }

    private fun setupStatus() {
        currentStats = ArrayList()
        val libraryFormat = mangasDistinct.filterByChip().groupBy { it.status }

        libraryFormat.forEach { (status, mangaList) ->
            currentStats?.add(
                StatsData(
                    color = StatsHelper.STATUS_COLOR_MAP[status],
                    count = mangaList.count(),
                    meanScore = mangaList.getMeanScoreRounded(),
                    chaptersRead = mangaList.sumOf { it.read },
                    totalChapters = mangaList.sumOf { it.totalChapters },
                    label = context.mapStatus(status).uppercase(),
                ),
            )
        }
        sortCurrentStats()
    }

    private fun setupScores() {
        currentStats = ArrayList()
        val libraryFormat = mangasDistinct.filterByChip().groupBy { it.getMeanScoreToInt() }
        val scoreMap = StatsHelper.SCORE_COLOR_MAP.plus(null to pieColorList[1])

        scoreMap.forEach { (score, color) ->
            val mangaList = libraryFormat[score]
            currentStats?.add(
                StatsData(
                    color = color,
                    count = mangaList?.count() ?: 0,
                    meanScore = score?.toDouble() ?: 0.0,
                    chaptersRead = mangaList?.sumOf { it.read } ?: 0,
                    totalChapters = mangaList?.sumOf { it.totalChapters } ?: 0,
                    label = score?.toString() ?: context.getString(R.string.not_rated).uppercase(),
                ),
            )
        }
    }

    private fun setupLanguages() {
        currentStats = ArrayList()
        val libraryFormat = mangasDistinct.filterByChip().groupBy { it.getLanguage() }

        libraryFormat.forEach { (language, mangaList) ->
            currentStats?.add(
                StatsData(
                    color = pieColorList[currentStats?.size!! % 12],
                    count = mangaList.count(),
                    meanScore = mangaList.getMeanScoreRounded(),
                    chaptersRead = mangaList.sumOf { it.read },
                    totalChapters = mangaList.sumOf { it.totalChapters },
                    label = language.uppercase(),
                ),
            )
        }
        sortCurrentStats()
    }

    private fun setupLength() {
        currentStats = ArrayList()
        var mangaFiltered = mangasDistinct.filterByChip()
        StatsHelper.STATS_LENGTH.forEach { (min, max) ->
            val (match, unmatch) = mangaFiltered.partition { it.totalChapters >= min && (max == null || it.totalChapters <= max) }
            mangaFiltered = unmatch
            currentStats?.add(
                StatsData(
                    color = StatsHelper.SCORE_COLOR_LIST[currentStats?.size!!],
                    count = match.count(),
                    meanScore = match.getMeanScoreRounded(),
                    chaptersRead = match.sumOf { it.read },
                    totalChapters = match.sumOf { it.totalChapters },
                    label = if (min == max) min.toString() else {
                        listOf(min.toString(), max?.toString()).joinToString("-")
                            .replace("-null", "+")
                    },
                ),
            )
        }
    }

    private fun setupTrackers() {
        currentStats = ArrayList()
        val libraryFormat = mangasDistinct.filterByChip()
            .map { it to getTracks(it).ifEmpty { listOf(null) } }
            .flatMap { it.second.map { track -> it.first to track } }
        val loggedServices = trackManager.services.filter { it.isLogged }

        val serviceWithTrackedManga = libraryFormat.groupBy { it.second?.sync_id }

        serviceWithTrackedManga.forEach { (serviceId, mangaAndTrack) ->
            val service = loggedServices.find { it.id == serviceId }
            val label = context.getString(service?.nameRes() ?: R.string.not_tracked)
            currentStats?.add(
                StatsData(
                    color = pieColorList[currentStats?.size!!],
                    count = mangaAndTrack.count(),
                    meanScore = mangaAndTrack.map { it.second }.getMeanScoreByTracker()?.roundToTwoDecimal(),
                    chaptersRead = mangaAndTrack.sumOf { it.first.read },
                    totalChapters = mangaAndTrack.sumOf { it.first.totalChapters },
                    label = label.uppercase(),
                ),
            )
        }
        sortCurrentStats()
    }

    private fun setupSources() {
        currentStats = ArrayList()
        val libraryFormat = mangasDistinct.filterByChip().groupBy { it.source }

        libraryFormat.forEach { (source, mangaList) ->
            val sourceName = sources.find { it.id == source }?.toString() ?: source.toString()
            currentStats?.add(
                StatsData(
                    color = pieColorList[1],
                    count = mangaList.count(),
                    meanScore = mangaList.getMeanScoreRounded(),
                    chaptersRead = mangaList.sumOf { it.read },
                    totalChapters = mangaList.sumOf { it.totalChapters },
                    label = sourceName.uppercase(),
                ),
            )
        }
        sortCurrentStats()
    }

    private fun setupCategories() {
        currentStats = ArrayList()
        val libraryFormat = libraryMangas.filterByChip().groupBy { it.category }
        val categories = getCategories()

        libraryFormat.forEach { (category, mangaList) ->
            val label = categories.find { it.id == category }?.name ?: context.getString(R.string.default_value)
            currentStats?.add(
                StatsData(
                    color = pieColorList[currentStats?.size!! % 12],
                    count = mangaList.count(),
                    meanScore = mangaList.getMeanScoreRounded(),
                    chaptersRead = mangaList.sumOf { it.read },
                    totalChapters = mangaList.sumOf { it.totalChapters },
                    label = label.uppercase(),
                ),
            )
        }
        sortCurrentStats()
    }

    private fun setupTags() {
        currentStats = ArrayList()
        val mangaFiltered = mangasDistinct.filterByChip()
        val tags = mangaFiltered.flatMap { it.getTags() }.distinct()
        val libraryFormat = tags.map { tag -> tag to mangaFiltered.filter { tag in it.getTags() } }

        libraryFormat.forEach { (tag, mangaList) ->
            currentStats?.add(
                StatsData(
                    color = pieColorList[1],
                    count = mangaList.count(),
                    meanScore = mangaList.getMeanScoreRounded(),
                    chaptersRead = mangaList.sumOf { it.read },
                    totalChapters = mangaList.sumOf { it.totalChapters },
                    label = tag.uppercase(),
                ),
            )
        }
        sortCurrentStats()
        currentStats = currentStats?.take(100)?.let { ArrayList(it) }
    }

    private fun setupStartYear() {
        currentStats = ArrayList()
        val libraryFormat = mangasDistinct.filterByChip().groupBy { it.getStartYear() }

        libraryFormat.forEach { (year, mangaList) ->
            currentStats?.add(
                StatsData(
                    color = if (year == null) pieColorList[0] else pieColorList[1],
                    count = mangaList.count(),
                    meanScore = mangaList.getMeanScoreRounded(),
                    chaptersRead = mangaList.sumOf { it.read },
                    totalChapters = mangaList.sumOf { it.totalChapters },
                    label = year?.toString() ?: context.getString(R.string.not_started).uppercase(),
                ),
            )
        }
    }

    private fun List<LibraryManga>.filterByChip(): List<LibraryManga> {
        return this.filterBySerieType(selectedStat == Stats.SERIE_TYPE)
            .filterByStatus(selectedStat == Stats.STATUS)
            .filterByLanguage(selectedStat == Stats.LANGUAGE || (selectedStat != Stats.SOURCE && selectedSource.isNotEmpty()))
            .filterBySource(selectedStat in listOf(Stats.SOURCE, Stats.LANGUAGE) || selectedLanguage.isNotEmpty())
            .filterByCategory(selectedStat == Stats.CATEGORY)
    }

    private fun List<LibraryManga>.filterBySerieType(noFilter: Boolean = false): List<LibraryManga> {
        return if (noFilter || selectedSerieType.isEmpty()) this else filter { manga ->
            context.mapSerieType(manga.seriesType()) in selectedSerieType
        }
    }

    private fun List<LibraryManga>.filterByStatus(noFilter: Boolean = false): List<LibraryManga> {
        return if (noFilter || selectedStatus.isEmpty()) this else filter { manga ->
            context.mapStatus(manga.status) in selectedStatus
        }
    }

    private fun List<LibraryManga>.filterByLanguage(noFilter: Boolean = false): List<LibraryManga> {
        return if (noFilter || selectedLanguage.isEmpty()) this else filter { manga ->
            manga.getLanguage() in selectedLanguage
        }
    }

    private fun List<LibraryManga>.filterBySource(noFilter: Boolean = false): List<LibraryManga> {
        return if (noFilter || selectedSource.isEmpty()) this else filter { manga ->
            manga.source in selectedSource.map { it.id }
        }
    }

    private fun List<LibraryManga>.filterByCategory(noFilter: Boolean = false): List<LibraryManga> {
        return if (noFilter || selectedCategory.isEmpty()) this else filter { manga ->
            manga.category in selectedCategory.map { it.id }
        }
    }

    fun sortCurrentStats() {
        when (selectedStatsSort) {
            StatsSort.COUNT_DESC -> currentStats?.sortWith(
                compareByDescending<StatsData> { it.count }.thenByDescending { it.chaptersRead }
                    .thenByDescending { it.meanScore },
            )
            StatsSort.MEAN_SCORE_DESC -> currentStats?.sortWith(
                compareByDescending<StatsData> { it.meanScore }.thenByDescending { it.count }
                    .thenByDescending { it.chaptersRead },
            )
            StatsSort.PROGRESS_DESC -> currentStats?.sortWith(
                compareByDescending<StatsData> { it.chaptersRead }.thenByDescending { it.count }
                    .thenByDescending { it.meanScore },
            )
            else -> {}
        }
    }

    private fun Manga.getTags(): List<String> {
        return getGenres()?.map { it.uppercase() } ?: emptyList()
    }

    private fun LibraryManga.getLanguage(): String {
        val code = if (isLocal()) {
            LocalSource.getMangaLang(this, context)
        } else {
            sourceManager.get(source)?.lang
        } ?: return context.getString(R.string.unknown)
        return LocaleHelper.getDisplayName(code)
    }

    private fun List<LibraryManga>.getMeanScoreRounded(): Double? {
        val mangaTracks = this.map { it to getTracks(it) }
        val scoresList = mangaTracks.filter { it.second.isNotEmpty() }
            .mapNotNull { it.second.getMeanScoreByTracker() }
        return if (scoresList.isEmpty()) null else scoresList.average().roundToTwoDecimal()
    }

    private fun LibraryManga.getMeanScoreToInt(): Int? {
        val mangaTracks = getTracks(this)
        val scoresList = mangaTracks.filter { it.score > 0 }
            .mapNotNull { it.get10PointScore() }
        return if (scoresList.isEmpty()) null else scoresList.average().roundToInt().coerceIn(1..10)
    }

    private fun List<Track?>.getMeanScoreByTracker(): Double? {
        val scoresList = this.filter { (it?.score ?: 0f) > 0 }
            .mapNotNull { it?.get10PointScore() }
        return if (scoresList.isEmpty()) null else scoresList.average()
    }

    private fun Track.get10PointScore(): Float? {
        val service = trackManager.getService(this.sync_id)
        return service?.get10PointScore(this.score)
    }

    private fun LibraryManga.getStartYear(): Int? {
        if (db.getChapters(id).executeAsBlocking().any { it.read }) {
            val chapters = db.getHistoryByMangaId(id!!).executeAsBlocking().filter { it.last_read > 0 }
            val date = chapters.minOfOrNull { it.last_read } ?: return null
            return if (date <= 0L) null else date.toUtcCalendar()?.get(Calendar.YEAR)
        }
        return null
    }

    fun getStatsArray(): Array<String> {
        return Stats.values().map { context.getString(it.resourceId) }.toTypedArray()
    }

    private fun getEnabledSources(): List<Source> {
        return mangasDistinct.mapNotNull { sourceManager.get(it.source) }
            .distinct().sortedBy { it.name }
    }

    fun getSortDataArray(): Array<String> {
        return StatsSort.values().sortedArray().map { context.getString(it.resourceId) }.toTypedArray()
    }

    fun getTracks(manga: Manga): MutableList<Track> {
        return db.getTracks(manga).executeAsBlocking()
    }

    fun getLibrary(): MutableList<LibraryManga> {
        return db.getLibraryMangas().executeAsBlocking()
    }

    private fun getCategories(): MutableList<Category> {
        return db.getCategories().executeAsBlocking()
    }

    enum class Stats(val resourceId: Int) {
        SERIE_TYPE(R.string.serie_type),
        STATUS(R.string.status),
        SCORE(R.string.score),
        LANGUAGE(R.string.language),
        LENGTH(R.string.length),
        TRACKER(R.string.tracker),
        SOURCE(R.string.source),
        CATEGORY(R.string.category),
        TAG(R.string.tag),
        START_YEAR(R.string.start_year)
    }

    enum class StatsSort(val resourceId: Int) {
        COUNT_DESC(R.string.title_count),
        PROGRESS_DESC(R.string.chapters_read),
        MEAN_SCORE_DESC(R.string.mean_score),
    }

    class StatsData(
        var color: Int? = null,
        val count: Int = 0,
        val meanScore: Double? = null,
        val chaptersRead: Int = 0,
        val totalChapters: Int = 0,
        var label: String? = null,
    )
}
