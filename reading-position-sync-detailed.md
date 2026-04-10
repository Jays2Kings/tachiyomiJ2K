# Reading Position Sync System - Detailed Implementation Plan

## 🎯 Overview

This document provides a detailed implementation plan for the Reading Position Sync System, enabling precise tracking and synchronization of reading positions across multiple devices, including mid-page scroll positions, zoom levels, and reading context.

## 📊 Detailed Data Models

### Core Reading Position Model

```kotlin
@Entity(
    tableName = "detailed_reading_positions",
    indices = [
        Index(value = ["manga_id", "chapter_id", "device_id"], unique = true),
        Index(value = ["session_id"]),
        Index(value = ["timestamp"]),
        Index(value = ["sync_status"])
    ]
)
data class DetailedReadingPosition(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "manga_id")
    val mangaId: Long,

    @ColumnInfo(name = "chapter_id")
    val chapterId: Long,

    @ColumnInfo(name = "page_index")
    val pageIndex: Int,

    @ColumnInfo(name = "scroll_progress")
    val scrollProgress: Float, // 0.0 to 1.0 for vertical scroll position within page

    @ColumnInfo(name = "horizontal_progress")
    val horizontalProgress: Float = 0.0f, // For horizontal manga/webtoons

    @ColumnInfo(name = "panel_index")
    val panelIndex: Int? = null, // For panel-by-panel reading mode

    @ColumnInfo(name = "zoom_level")
    val zoomLevel: Float = 1.0f,

    @ColumnInfo(name = "viewport_center_x")
    val viewportCenterX: Float = 0.5f, // Normalized viewport center

    @ColumnInfo(name = "viewport_center_y")
    val viewportCenterY: Float = 0.5f,

    @ColumnInfo(name = "reading_mode")
    val readingMode: Int, // VERTICAL, HORIZONTAL, WEBTOON, etc.

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "context_data")
    val contextData: String? = null, // JSON for additional context

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float = 1.0f, // How confident we are about this position

    @ColumnInfo(name = "is_auto_save")
    val isAutoSave: Boolean = true // Auto-saved vs manually bookmarked
) {
    companion object {
        fun fromReaderState(
            mangaId: Long,
            chapterId: Long,
            readerState: ReaderState,
            deviceId: String,
            sessionId: String
        ): DetailedReadingPosition {
            return DetailedReadingPosition(
                mangaId = mangaId,
                chapterId = chapterId,
                pageIndex = readerState.currentPageIndex,
                scrollProgress = readerState.scrollProgress,
                horizontalProgress = readerState.horizontalProgress,
                panelIndex = readerState.currentPanelIndex,
                zoomLevel = readerState.zoomLevel,
                viewportCenterX = readerState.viewportCenterX,
                viewportCenterY = readerState.viewportCenterY,
                readingMode = readerState.readingMode,
                timestamp = System.currentTimeMillis(),
                deviceId = deviceId,
                sessionId = sessionId,
                contextData = readerState.toContextJson()
            )
        }
    }
}

enum class SyncStatus {
    PENDING,    // Waiting to be synced
    SYNCING,    // Currently being synced
    SYNCED,     // Successfully synced
    CONFLICT,   // Conflict detected
    FAILED,     // Sync failed
    LOCAL_ONLY  // Marked as local-only
}
```

### Reading Session Management

```kotlin
@Entity(tableName = "reading_sessions")
data class ReadingSession(
    @PrimaryKey
    val sessionId: String,

    @ColumnInfo(name = "start_time")
    val startTime: Long,

    @ColumnInfo(name = "end_time")
    var endTime: Long? = null,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "device_type")
    val deviceType: DeviceType,

    @ColumnInfo(name = "app_version")
    val appVersion: String,

    @ColumnInfo(name = "is_active")
    var isActive: Boolean = true,

    @ColumnInfo(name = "total_pages_read")
    var totalPagesRead: Int = 0,

    @ColumnInfo(name = "unique_chapters")
    var uniqueChapters: Int = 0,

    @ColumnInfo(name = "reading_streak")
    var readingStreak: Int = 0 // consecutive days
)

class ReadingSessionManager(
    private val db: AppDatabase,
    private val deviceManager: DeviceManager
) {
    private var currentSession: ReadingSession? = null

    fun startNewSession(): String {
        endCurrentSession()

        val sessionId = generateSessionId()
        currentSession = ReadingSession(
            sessionId = sessionId,
            startTime = System.currentTimeMillis(),
            deviceId = deviceManager.getDeviceId(),
            deviceType = deviceManager.getDeviceType(),
            appVersion = BuildConfig.VERSION_NAME
        )

        GlobalScope.launch {
            db.readingSessionDao().insert(currentSession!!)
        }

        return sessionId
    }

    fun updateSessionStats(pagesRead: Int, chaptersRead: Set<Long>) {
        currentSession?.let { session ->
            session.totalPagesRead += pagesRead
            session.uniqueChapters = chaptersRead.size

            GlobalScope.launch {
                db.readingSessionDao().update(session)
            }
        }
    }

    private fun endCurrentSession() {
        currentSession?.let { session ->
            session.endTime = System.currentTimeMillis()
            session.isActive = false

            GlobalScope.launch {
                db.readingSessionDao().update(session)
            }
        }
        currentSession = null
    }

    private fun generateSessionId(): String {
        return "${deviceManager.getDeviceId()}_${System.currentTimeMillis()}_${Random.nextInt(1000)}"
    }
}
```

### Enhanced Context Data

```kotlin
data class ReadingContext(
    val readerSettings: ReaderSettings,
    val displayMetrics: DisplayMetrics,
    val orientation: Int,
    val batteryLevel: Int?,
    val networkType: String?,
    val timeOfDay: Int, // Hour of day (0-23)
    val ambientLight: Float? = null, // If available from sensors
    val readingSpeed: Float? = null, // Pages per minute
    val averageTimePerPage: Long? = null // Milliseconds
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): ReadingContext? {
            return try {
                Gson().fromJson(json, ReadingContext::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class ReaderSettings(
    val readingMode: Int,
    val orientation: Int,
    val backgroundColor: Int,
    val brightness: Float,
    val keepScreenOn: Boolean,
    val showPageNumber: Boolean,
    val cropBorders: Boolean,
    val dualPageSplit: Boolean,
    val doublePages: Boolean
)
```

## 🔄 Sync Mechanism Implementation

### Reading Position Tracker

```kotlin
class ReadingPositionTracker(
    private val context: Context,
    private val db: AppDatabase,
    private val sessionManager: ReadingSessionManager,
    private val syncManager: SyncManager
) {
    private val positionUpdateSubject = PublishSubject.create<PositionUpdate>()
    private val batchProcessor = BatchProcessor()

    init {
        setupPositionUpdateStream()
    }

    fun trackPosition(
        mangaId: Long,
        chapterId: Long,
        readerState: ReaderState,
        confidence: Float = 1.0f
    ) {
        val update = PositionUpdate(
            mangaId = mangaId,
            chapterId = chapterId,
            readerState = readerState,
            confidence = confidence,
            timestamp = System.currentTimeMillis()
        )

        positionUpdateSubject.onNext(update)
    }

    private fun setupPositionUpdateStream() {
        positionUpdateSubject
            .debounce(1, TimeUnit.SECONDS) // Avoid too frequent updates
            .observeOn(Schedulers.io())
            .subscribe { update ->
                processPositionUpdate(update)
            }

        // Batch process for sync every 5 seconds
        positionUpdateSubject
            .buffer(5, TimeUnit.SECONDS)
            .filter { it.isNotEmpty() }
            .observeOn(Schedulers.io())
            .subscribe { updates ->
                batchProcessor.processBatch(updates)
            }
    }

    private fun processPositionUpdate(update: PositionUpdate) {
        val position = DetailedReadingPosition.fromReaderState(
            mangaId = update.mangaId,
            chapterId = update.chapterId,
            readerState = update.readerState,
            deviceId = deviceManager.getDeviceId(),
            sessionId = sessionManager.getCurrentSessionId()
        ).copy(confidenceScore = update.confidence)

        // Save locally
        db.readingPositionDao().upsert(position)

        // Queue for sync if high confidence
        if (update.confidence > 0.7f) {
            syncManager.queuePositionSync(position)
        }

        // Update session stats
        sessionManager.updatePosition(update)
    }
}

data class PositionUpdate(
    val mangaId: Long,
    val chapterId: Long,
    val readerState: ReaderState,
    val confidence: Float,
    val timestamp: Long
)

class BatchProcessor {
    fun processBatch(updates: List<PositionUpdate>) {
        // Group by manga/chapter for efficient processing
        val grouped = updates.groupBy { "${it.mangaId}_${it.chapterId}" }

        grouped.values.forEach { chapterUpdates ->
            // Take the most recent and confident update for each chapter
            val bestUpdate = chapterUpdates
                .maxWithOrNull(compareBy<PositionUpdate> { it.confidence }
                    .thenBy { it.timestamp })

            bestUpdate?.let { update ->
                // Process the best update for this chapter
                // This helps reduce redundant syncing
            }
        }
    }
}
```

### Smart Position Sync

```kotlin
class SmartPositionSync(
    private val cloudProvider: CloudProvider,
    private val conflictResolver: ConflictResolver,
    private val db: AppDatabase
) {

    suspend fun syncPosition(position: DetailedReadingPosition): SyncResult {
        try {
            // Check for existing cloud position
            val cloudPosition = cloudProvider.getPosition(
                position.mangaId,
                position.chapterId
            )

            val result = when {
                cloudPosition == null -> uploadNewPosition(position)
                cloudPosition.timestamp < position.timestamp -> updateCloudPosition(position)
                cloudPosition.timestamp > position.timestamp -> handleNewerCloudPosition(position, cloudPosition)
                else -> checkForConflicts(position, cloudPosition)
            }

            // Update local sync status
            db.readingPositionDao().updateSyncStatus(
                position.id,
                if (result.isSuccess) SyncStatus.SYNCED else SyncStatus.FAILED
            )

            return result

        } catch (e: Exception) {
            return SyncResult.Error(e)
        }
    }

    private suspend fun uploadNewPosition(position: DetailedReadingPosition): SyncResult {
        return cloudProvider.uploadPosition(position)
    }

    private suspend fun updateCloudPosition(position: DetailedReadingPosition): SyncResult {
        return cloudProvider.updatePosition(position)
    }

    private suspend fun handleNewerCloudPosition(
        local: DetailedReadingPosition,
        cloud: DetailedReadingPosition
    ): SyncResult {
        // Cloud has newer position - should we override local?
        val resolution = conflictResolver.resolvePositionConflict(local, cloud)

        return when (resolution) {
            is ConflictResolution.UseRemote -> {
                db.readingPositionDao().update(cloud.copy(id = local.id))
                SyncResult.Success(cloud)
            }
            is ConflictResolution.UseLocal -> {
                cloudProvider.updatePosition(local)
            }
            is ConflictResolution.Merge -> {
                val merged = resolution.mergedPosition
                db.readingPositionDao().update(merged.copy(id = local.id))
                cloudProvider.updatePosition(merged)
                SyncResult.Success(merged)
            }
            is ConflictResolution.RequiresUserInput -> {
                db.readingPositionDao().updateSyncStatus(local.id, SyncStatus.CONFLICT)
                SyncResult.Conflict(local, cloud)
            }
        }
    }

    private suspend fun checkForConflicts(
        local: DetailedReadingPosition,
        cloud: DetailedReadingPosition
    ): SyncResult {
        // Same timestamp but potentially different data
        val hasConflict = !positionsAreEquivalent(local, cloud)

        return if (hasConflict) {
            val resolution = conflictResolver.resolvePositionConflict(local, cloud)
            handleConflictResolution(local, cloud, resolution)
        } else {
            SyncResult.Success(local) // Already in sync
        }
    }

    private fun positionsAreEquivalent(
        pos1: DetailedReadingPosition,
        pos2: DetailedReadingPosition
    ): Boolean {
        return pos1.pageIndex == pos2.pageIndex &&
                abs(pos1.scrollProgress - pos2.scrollProgress) < 0.05f &&
                abs(pos1.zoomLevel - pos2.zoomLevel) < 0.1f &&
                abs(pos1.viewportCenterX - pos2.viewportCenterX) < 0.1f &&
                abs(pos1.viewportCenterY - pos2.viewportCenterY) < 0.1f
    }
}

sealed class SyncResult {
    data class Success(val position: DetailedReadingPosition) : SyncResult()
    data class Conflict(val local: DetailedReadingPosition, val remote: DetailedReadingPosition) : SyncResult()
    data class Error(val exception: Exception) : SyncResult()

    val isSuccess: Boolean get() = this is Success
}
```

## ⚔️ Advanced Conflict Resolution

### Intelligent Conflict Resolver

```kotlin
class IntelligentConflictResolver(
    private val preferences: PreferencesHelper,
    private val userBehaviorAnalyzer: UserBehaviorAnalyzer
) {

    fun resolvePositionConflict(
        local: DetailedReadingPosition,
        remote: DetailedReadingPosition
    ): ConflictResolution {

        // Check for obvious resolution scenarios
        val quickResolution = checkQuickResolution(local, remote)
        if (quickResolution != null) return quickResolution

        // Analyze user behavior patterns
        val behaviorInsight = userBehaviorAnalyzer.analyzeConflict(local, remote)

        return when (behaviorInsight.recommendation) {
            RecommendationType.USE_LOCAL -> ConflictResolution.UseLocal(local)
            RecommendationType.USE_REMOTE -> ConflictResolution.UseRemote(remote)
            RecommendationType.SMART_MERGE -> {
                val merged = createSmartMerge(local, remote, behaviorInsight)
                ConflictResolution.Merge(merged)
            }
            RecommendationType.USER_DECISION -> {
                ConflictResolution.RequiresUserInput(local, remote, behaviorInsight)
            }
        }
    }

    private fun checkQuickResolution(
        local: DetailedReadingPosition,
        remote: DetailedReadingPosition
    ): ConflictResolution? {

        // Same session - use most recent
        if (local.sessionId == remote.sessionId) {
            return if (local.timestamp > remote.timestamp) {
                ConflictResolution.UseLocal(local)
            } else {
                ConflictResolution.UseRemote(remote)
            }
        }

        // Significant page difference - use furthest
        val pageDiff = abs(local.pageIndex - remote.pageIndex)
        if (pageDiff > 5) {
            return if (local.pageIndex > remote.pageIndex) {
                ConflictResolution.UseLocal(local)
            } else {
                ConflictResolution.UseRemote(remote)
            }
        }

        // Very recent conflict (< 5 minutes) - use device preference
        val timeDiff = abs(local.timestamp - remote.timestamp)
        if (timeDiff < TimeUnit.MINUTES.toMillis(5)) {
            val preferredDevice = preferences.preferredSyncDevice().get()
            return when {
                preferredDevice == local.deviceId -> ConflictResolution.UseLocal(local)
                preferredDevice == remote.deviceId -> ConflictResolution.UseRemote(remote)
                else -> null // Continue to behavioral analysis
            }
        }

        return null
    }

    private fun createSmartMerge(
        local: DetailedReadingPosition,
        remote: DetailedReadingPosition,
        insight: BehaviorInsight
    ): DetailedReadingPosition {
        return DetailedReadingPosition(
            mangaId = local.mangaId,
            chapterId = local.chapterId,
            pageIndex = maxOf(local.pageIndex, remote.pageIndex), // Furthest page
            scrollProgress = when (insight.preferredScrollBehavior) {
                ScrollBehavior.CONSERVATIVE -> minOf(local.scrollProgress, remote.scrollProgress)
                ScrollBehavior.PROGRESSIVE -> maxOf(local.scrollProgress, remote.scrollProgress)
                ScrollBehavior.AVERAGE -> (local.scrollProgress + remote.scrollProgress) / 2f
            },
            horizontalProgress = maxOf(local.horizontalProgress, remote.horizontalProgress),
            panelIndex = maxOf(local.panelIndex ?: 0, remote.panelIndex ?: 0).takeIf { it > 0 },
            zoomLevel = insight.preferredZoomLevel ?:
                       if (local.deviceType == DeviceType.PHONE) local.zoomLevel else remote.zoomLevel,
            viewportCenterX = (local.viewportCenterX + remote.viewportCenterX) / 2f,
            viewportCenterY = (local.viewportCenterY + remote.viewportCenterY) / 2f,
            readingMode = insight.preferredReadingMode ?: local.readingMode,
            timestamp = maxOf(local.timestamp, remote.timestamp),
            deviceId = local.deviceId, // Keep current device as source
            sessionId = generateMergedSessionId(local.sessionId, remote.sessionId),
            contextData = mergeContextData(local.contextData, remote.contextData),
            confidenceScore = (local.confidenceScore + remote.confidenceScore) / 2f * 0.9f // Slightly lower confidence for merged
        )
    }
}

sealed class ConflictResolution {
    data class UseLocal(val position: DetailedReadingPosition) : ConflictResolution()
    data class UseRemote(val position: DetailedReadingPosition) : ConflictResolution()
    data class Merge(val mergedPosition: DetailedReadingPosition) : ConflictResolution()
    data class RequiresUserInput(
        val local: DetailedReadingPosition,
        val remote: DetailedReadingPosition,
        val insight: BehaviorInsight
    ) : ConflictResolution()
}

class UserBehaviorAnalyzer(private val db: AppDatabase) {

    fun analyzeConflict(
        local: DetailedReadingPosition,
        remote: DetailedReadingPosition
    ): BehaviorInsight {

        val recentHistory = getRecentReadingHistory(local.mangaId)
        val deviceUsagePattern = analyzeDeviceUsagePattern(local.deviceId, remote.deviceId)
        val readingPattern = analyzeReadingPattern(local.chapterId, recentHistory)

        return BehaviorInsight(
            recommendation = determineRecommendation(local, remote, deviceUsagePattern, readingPattern),
            confidence = calculateConfidence(deviceUsagePattern, readingPattern),
            reasoning = generateReasoning(deviceUsagePattern, readingPattern),
            preferredScrollBehavior = determineScrollBehavior(recentHistory),
            preferredZoomLevel = determinePreferredZoom(local, remote, deviceUsagePattern),
            preferredReadingMode = determinePreferredReadingMode(recentHistory)
        )
    }

    private fun determineRecommendation(
        local: DetailedReadingPosition,
        remote: DetailedReadingPosition,
        devicePattern: DeviceUsagePattern,
        readingPattern: ReadingPattern
    ): RecommendationType {

        // Strong device preference
        if (devicePattern.primaryDeviceConfidence > 0.8f) {
            return if (devicePattern.primaryDevice == local.deviceId) {
                RecommendationType.USE_LOCAL
            } else {
                RecommendationType.USE_REMOTE
            }
        }

        // Reading pattern suggests progression
        if (readingPattern.isProgressiveReader && local.pageIndex != remote.pageIndex) {
            return RecommendationType.USE_LOCAL // Assume local is more recent for progressive readers
        }

        // Similar positions - safe to merge
        val positionSimilarity = calculatePositionSimilarity(local, remote)
        if (positionSimilarity > 0.7f) {
            return RecommendationType.SMART_MERGE
        }

        // Default to user decision for complex conflicts
        return RecommendationType.USER_DECISION
    }
}

enum class RecommendationType {
    USE_LOCAL, USE_REMOTE, SMART_MERGE, USER_DECISION
}

enum class ScrollBehavior {
    CONSERVATIVE, PROGRESSIVE, AVERAGE
}

data class BehaviorInsight(
    val recommendation: RecommendationType,
    val confidence: Float,
    val reasoning: String,
    val preferredScrollBehavior: ScrollBehavior,
    val preferredZoomLevel: Float?,
    val preferredReadingMode: Int?
)
```

## 📱 User Interface Components

### Position Conflict Dialog

```kotlin
class PositionConflictDialog : DialogFragment() {

    private lateinit var binding: DialogPositionConflictBinding
    private lateinit var local: DetailedReadingPosition
    private lateinit var remote: DetailedReadingPosition
    private var onResolved: ((DetailedReadingPosition) -> Unit)? = null

    fun show(
        fragmentManager: FragmentManager,
        local: DetailedReadingPosition,
        remote: DetailedReadingPosition,
        insight: BehaviorInsight,
        onResolved: (DetailedReadingPosition) -> Unit
    ) {
        this.local = local
        this.remote = remote
        this.onResolved = onResolved

        val args = Bundle().apply {
            putString("local_json", Gson().toJson(local))
            putString("remote_json", Gson().toJson(remote))
            putString("insight_json", Gson().toJson(insight))
        }
        arguments = args

        show(fragmentManager, "position_conflict")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogPositionConflictBinding.inflate(layoutInflater)

        setupConflictViews()
        setupButtons()

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .setTitle(R.string.reading_position_conflict)
            .create()
    }

    private fun setupConflictViews() {
        // Local position preview
        binding.localPositionCard.apply {
            setDeviceName(getDeviceName(local.deviceId))
            setTimestamp(local.timestamp)
            setPageInfo(local.pageIndex, local.scrollProgress)
            setReadingMode(local.readingMode)

            // Show preview thumbnail if available
            loadPagePreview(local.mangaId, local.chapterId, local.pageIndex) { bitmap ->
                setPreviewImage(bitmap)
                highlightPosition(local.scrollProgress, local.viewportCenterX, local.viewportCenterY)
            }
        }

        // Remote position preview
        binding.remotePositionCard.apply {
            setDeviceName(getDeviceName(remote.deviceId))
            setTimestamp(remote.timestamp)
            setPageInfo(remote.pageIndex, remote.scrollProgress)
            setReadingMode(remote.readingMode)

            loadPagePreview(remote.mangaId, remote.chapterId, remote.pageIndex) { bitmap ->
                setPreviewImage(bitmap)
                highlightPosition(remote.scrollProgress, remote.viewportCenterX, remote.viewportCenterY)
            }
        }

        // Show AI recommendation if available
        arguments?.getString("insight_json")?.let { insightJson ->
            val insight = Gson().fromJson(insightJson, BehaviorInsight::class.java)
            if (insight.confidence > 0.6f) {
                binding.aiRecommendation.apply {
                    isVisible = true
                    text = insight.reasoning
                    setRecommendation(insight.recommendation)
                }
            }
        }
    }

    private fun setupButtons() {
        binding.useLocalButton.setOnClickListener {
            onResolved?.invoke(local)
            dismiss()
        }

        binding.useRemoteButton.setOnClickListener {
            onResolved?.invoke(remote)
            dismiss()
        }

        binding.mergeButton.setOnClickListener {
            val merged = createUserGuidedMerge()
            onResolved?.invoke(merged)
            dismiss()
        }

        binding.alwaysPreferDevice.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Save device preference for future conflicts
                saveDevicePreference()
            }
        }
    }

    private fun createUserGuidedMerge(): DetailedReadingPosition {
        // Allow user to customize the merge
        return DetailedReadingPosition(
            mangaId = local.mangaId,
            chapterId = local.chapterId,
            pageIndex = binding.pageSelector.selectedPage,
            scrollProgress = binding.scrollSelector.selectedProgress,
            horizontalProgress = binding.horizontalSelector.selectedProgress,
            zoomLevel = binding.zoomSelector.selectedZoom,
            viewportCenterX = binding.viewportSelector.selectedX,
            viewportCenterY = binding.viewportSelector.selectedY,
            readingMode = binding.modeSelector.selectedMode,
            timestamp = System.currentTimeMillis(),
            deviceId = local.deviceId,
            sessionId = local.sessionId,
            confidenceScore = 1.0f // User-guided merge has high confidence
        )
    }
}
```

### Sync Status Indicator

```kotlin
class SyncStatusIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val binding: SyncStatusIndicatorBinding
    private var animator: ObjectAnimator? = null

    init {
        binding = SyncStatusIndicatorBinding.inflate(LayoutInflater.from(context), this, true)
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun updateStatus(status: SyncStatus, details: SyncDetails? = null) {
        when (status) {
            SyncStatus.SYNCED -> showSyncedState()
            SyncStatus.SYNCING -> showSyncingState(details?.progress ?: 0f)
            SyncStatus.CONFLICT -> showConflictState(details?.conflictCount ?: 0)
            SyncStatus.FAILED -> showErrorState(details?.error)
            SyncStatus.PENDING -> showPendingState()
            SyncStatus.LOCAL_ONLY -> showLocalOnlyState()
        }
    }

    private fun showSyncedState() {
        binding.apply {
            statusIcon.setImageResource(R.drawable.ic_sync_done)
            statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.success))
            statusText.text = context.getString(R.string.sync_status_synced)
            progressBar.isVisible = false
            stopAnimation()
        }
    }

    private fun showSyncingState(progress: Float) {
        binding.apply {
            statusIcon.setImageResource(R.drawable.ic_sync)
            statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.primary))
            statusText.text = context.getString(R.string.sync_status_syncing, (progress * 100).toInt())
            progressBar.isVisible = true
            progressBar.progress = (progress * 100).toInt()
            startRotationAnimation()
        }
    }

    private fun showConflictState(conflictCount: Int) {
        binding.apply {
            statusIcon.setImageResource(R.drawable.ic_sync_problem)
            statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.warning))
            statusText.text = context.resources.getQuantityString(
                R.plurals.sync_conflicts, conflictCount, conflictCount
            )
            progressBar.isVisible = false
            stopAnimation()
        }

        // Add pulse animation for conflicts
        startPulseAnimation()
    }

    private fun startRotationAnimation() {
        animator?.cancel()
        animator = ObjectAnimator.ofFloat(binding.statusIcon, "rotation", 0f, 360f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun startPulseAnimation() {
        animator?.cancel()
        animator = ObjectAnimator.ofFloat(binding.statusIcon, "alpha", 1f, 0.4f, 1f).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        binding.statusIcon.alpha = 1f
        binding.statusIcon.rotation = 0f
    }
}

data class SyncDetails(
    val progress: Float = 0f,
    val conflictCount: Int = 0,
    val error: String? = null,
    val lastSyncTime: Long? = null,
    val pendingChanges: Int = 0
)
```

## 🔧 Database Access Layer

### Reading Position DAO

```kotlin
@Dao
interface ReadingPositionDao {

    @Query("SELECT * FROM detailed_reading_positions WHERE manga_id = :mangaId AND chapter_id = :chapterId AND device_id = :deviceId")
    suspend fun getPosition(mangaId: Long, chapterId: Long, deviceId: String): DetailedReadingPosition?

    @Query("SELECT * FROM detailed_reading_positions WHERE manga_id = :mangaId AND chapter_id = :chapterId ORDER BY timestamp DESC")
    suspend fun getAllPositionsForChapter(mangaId: Long, chapterId: Long): List<DetailedReadingPosition>

    @Query("SELECT * FROM detailed_reading_positions WHERE sync_status = :status ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPositionsByStatus(status: SyncStatus, limit: Int = 100): List<DetailedReadingPosition>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: DetailedReadingPosition): Long

    @Update
    suspend fun update(position: DetailedReadingPosition)

    @Query("UPDATE detailed_reading_positions SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: SyncStatus)

    @Query("DELETE FROM detailed_reading_positions WHERE manga_id = :mangaId")
    suspend fun deleteByManga(mangaId: Long)

    @Query("""
        SELECT * FROM detailed_reading_positions
        WHERE device_id = :deviceId
        AND timestamp > :since
        ORDER BY timestamp DESC
    """)
    suspend fun getRecentPositions(deviceId: String, since: Long): List<DetailedReadingPosition>

    @Query("""
        SELECT COUNT(*) FROM detailed_reading_positions
        WHERE sync_status = 'CONFLICT' AND device_id = :deviceId
    """)
    suspend fun getConflictCount(deviceId: String): Int

    @Query("""
        SELECT DISTINCT device_id, COUNT(*) as position_count, MAX(timestamp) as last_update
        FROM detailed_reading_positions
        WHERE manga_id = :mangaId
        GROUP BY device_id
    """)
    suspend fun getDeviceStats(mangaId: Long): List<DevicePositionStats>

    @Transaction
    suspend fun syncBatch(positions: List<DetailedReadingPosition>) {
        positions.forEach { position ->
            // Check for existing position
            val existing = getPosition(position.mangaId, position.chapterId, position.deviceId)

            if (existing == null || existing.timestamp < position.timestamp) {
                upsert(position)
            } else if (existing.timestamp == position.timestamp && existing != position) {
                // Same timestamp but different data - mark as conflict
                updateSyncStatus(existing.id, SyncStatus.CONFLICT)
            }
        }
    }
}

data class DevicePositionStats(
    val deviceId: String,
    val positionCount: Int,
    val lastUpdate: Long
)
```

## 📊 Performance Optimizations

### Efficient Sync Batching

```kotlin
class EfficientSyncBatcher(
    private val db: AppDatabase,
    private val cloudProvider: CloudProvider
) {

    companion object {
        private const val MAX_BATCH_SIZE = 50
        private const val MAX_BATCH_AGE_MS = 30_000L // 30 seconds
        private const val HIGH_PRIORITY_BATCH_SIZE = 10
    }

    private val pendingBatch = mutableListOf<DetailedReadingPosition>()
    private var lastBatchTime = 0L
    private val batchLock = Mutex()

    suspend fun addToSyncQueue(position: DetailedReadingPosition, priority: SyncPriority = SyncPriority.NORMAL) {
        batchLock.withLock {
            pendingBatch.add(position)

            val shouldFlush = when (priority) {
                SyncPriority.HIGH -> pendingBatch.size >= HIGH_PRIORITY_BATCH_SIZE
                SyncPriority.NORMAL -> pendingBatch.size >= MAX_BATCH_SIZE
                SyncPriority.LOW -> false
            } || (System.currentTimeMillis() - lastBatchTime) > MAX_BATCH_AGE_MS

            if (shouldFlush) {
                flushBatch()
            }
        }
    }

    private suspend fun flushBatch() {
        if (pendingBatch.isEmpty()) return

        val batchToSync = pendingBatch.toList()
        pendingBatch.clear()
        lastBatchTime = System.currentTimeMillis()

        // Process batch in background
        GlobalScope.launch(Dispatchers.IO) {
            try {
                processSyncBatch(batchToSync)
            } catch (e: Exception) {
                // Re-queue failed items
                batchLock.withLock {
                    pendingBatch.addAll(0, batchToSync) // Add to front for priority
                }
            }
        }
    }

    private suspend fun processSyncBatch(batch: List<DetailedReadingPosition>) {
        // Group by manga for efficient cloud operations
        val groupedBatch = batch.groupBy { it.mangaId }

        groupedBatch.forEach { (mangaId, positions) ->
            try {
                val syncResult = cloudProvider.syncPositionsForManga(mangaId, positions)
                handleBatchSyncResult(positions, syncResult)
            } catch (e: Exception) {
                // Mark all as failed and schedule retry
                positions.forEach { position ->
                    db.readingPositionDao().updateSyncStatus(position.id, SyncStatus.FAILED)
                }
                scheduleRetry(positions)
            }
        }
    }

    private suspend fun handleBatchSyncResult(
        positions: List<DetailedReadingPosition>,
        result: BatchSyncResult
    ) {
        result.successes.forEach { position ->
            db.readingPositionDao().updateSyncStatus(position.id, SyncStatus.SYNCED)
        }

        result.conflicts.forEach { (local, remote) ->
            db.readingPositionDao().updateSyncStatus(local.id, SyncStatus.CONFLICT)
            // Store conflict for user resolution
            storeConflictForResolution(local, remote)
        }

        result.failures.forEach { position ->
            db.readingPositionDao().updateSyncStatus(position.id, SyncStatus.FAILED)
        }
    }
}

enum class SyncPriority {
    HIGH,    // User just finished reading
    NORMAL,  // Regular position updates
    LOW      // Background cleanup
}

data class BatchSyncResult(
    val successes: List<DetailedReadingPosition>,
    val conflicts: List<Pair<DetailedReadingPosition, DetailedReadingPosition>>,
    val failures: List<DetailedReadingPosition>
)
```

This detailed implementation plan provides a comprehensive foundation for building a robust reading position sync system that can handle complex scenarios, provide intelligent conflict resolution, and maintain excellent performance across multiple devices.
