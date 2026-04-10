# Device-Specific Settings Framework
## Comprehensive Architecture for Multi-Device Preference Management

## 🎯 Overview

The Device-Specific Settings Framework enables TachiyomiJ2K to maintain different reading preferences and UI configurations per device while intelligently sharing core library data. This system recognizes that optimal settings for a phone differ significantly from a tablet or foldable device.

## 📱 Device Classification System

### Device Type Detection

```kotlin
enum class DeviceType(val id: String) {
    PHONE("phone"),
    TABLET("tablet"),
    FOLDABLE("foldable"),
    CHROMEBOOK("chromebook"),
    DESKTOP("desktop"),
    TV("tv"),
    AUTOMOTIVE("automotive");

    companion object {
        fun detect(context: Context): DeviceType {
            val configuration = context.resources.configuration
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            return when {
                isAndroidTV(context) -> TV
                isAutomotive(context) -> AUTOMOTIVE
                isChromebook(context) -> CHROMEBOOK
                isFoldable(context) -> FOLDABLE
                isTablet(configuration) -> TABLET
                else -> PHONE
            }
        }

        private fun isTablet(config: Configuration): Boolean {
            return (config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        }

        private fun isFoldable(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowInfoTracker = WindowInfoTracker.getOrCreate(context)
                // Check for folding features
                try {
                    val layoutInfo = windowInfoTracker.windowLayoutInfo(context as Activity)
                    layoutInfo.displayFeatures.any { it is FoldingFeature }
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }
        }

        private fun isChromebook(context: Context): Boolean {
            return context.packageManager.hasSystemFeature("org.chromium.arc") ||
                   Build.BRAND.contains("chromium", true) ||
                   Build.MODEL.contains("chromebook", true)
        }

        private fun isAndroidTV(context: Context): Boolean {
            return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                   context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        }

        private fun isAutomotive(context: Context): Boolean {
            return context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        }
    }
}

data class DeviceProfile(
    val deviceId: String,
    val deviceType: DeviceType,
    val displayMetrics: DisplayMetrics,
    val capabilities: Set<DeviceCapability>,
    val orientation: DeviceOrientation,
    val inputMethods: Set<InputMethod>,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis()
) {
    data class DisplayMetrics(
        val widthPx: Int,
        val heightPx: Int,
        val densityDpi: Int,
        val density: Float,
        val scaledDensity: Float,
        val widthDp: Int,
        val heightDp: Int,
        val smallestWidthDp: Int,
        val isPortrait: Boolean,
        val hasNotch: Boolean = false,
        val safeAreaInsets: Rect = Rect()
    )
}

enum class DeviceCapability {
    STYLUS_SUPPORT,
    EXTERNAL_KEYBOARD,
    MOUSE_SUPPORT,
    MULTI_WINDOW,
    PICTURE_IN_PICTURE,
    SPLIT_SCREEN,
    FOLDABLE_SCREEN,
    HIGH_REFRESH_RATE,
    HDR_SUPPORT,
    ALWAYS_ON_DISPLAY,
    FINGERPRINT_READER,
    FACE_UNLOCK,
    NFC,
    WIRELESS_CHARGING
}

enum class DeviceOrientation {
    PORTRAIT_ONLY,
    LANDSCAPE_ONLY,
    BOTH,
    AUTO_ROTATE
}

enum class InputMethod {
    TOUCH,
    STYLUS,
    MOUSE,
    KEYBOARD,
    GAMEPAD,
    VOICE,
    GESTURE
}
```

### Device Registration & Management

```kotlin
class DeviceManager(
    private val context: Context,
    private val database: AppDatabase,
    private val preferences: PreferencesHelper
) {
    private var _currentDevice: DeviceProfile? = null
    val currentDevice: DeviceProfile
        get() = _currentDevice ?: registerCurrentDevice()

    fun registerCurrentDevice(): DeviceProfile {
        val deviceId = getOrCreateDeviceId()
        val deviceType = DeviceType.detect(context)
        val displayMetrics = collectDisplayMetrics()
        val capabilities = detectCapabilities()
        val orientation = detectOrientationCapability()
        val inputMethods = detectInputMethods()

        val profile = DeviceProfile(
            deviceId = deviceId,
            deviceType = deviceType,
            displayMetrics = displayMetrics,
            capabilities = capabilities,
            orientation = orientation,
            inputMethods = inputMethods
        )

        // Save to database
        GlobalScope.launch {
            database.deviceDao().upsert(profile)
        }

        _currentDevice = profile
        return profile
    }

    private fun getOrCreateDeviceId(): String {
        val existingId = preferences.deviceId().get()
        if (existingId.isNotEmpty()) return existingId

        // Generate unique device ID
        val deviceId = generateDeviceId()
        preferences.deviceId().set(deviceId)
        return deviceId
    }

    private fun generateDeviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val model = Build.MODEL.replace(" ", "_")
        val brand = Build.BRAND
        val timestamp = System.currentTimeMillis()

        return "$brand-$model-${androidId.take(8)}-$timestamp".lowercase()
    }

    private fun collectDisplayMetrics(): DeviceProfile.DisplayMetrics {
        val displayMetrics = context.resources.displayMetrics
        val configuration = context.resources.configuration

        return DeviceProfile.DisplayMetrics(
            widthPx = displayMetrics.widthPixels,
            heightPx = displayMetrics.heightPixels,
            densityDpi = displayMetrics.densityDpi,
            density = displayMetrics.density,
            scaledDensity = displayMetrics.scaledDensity,
            widthDp = configuration.screenWidthDp,
            heightDp = configuration.screenHeightDp,
            smallestWidthDp = configuration.smallestScreenWidthDp,
            isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT,
            hasNotch = detectNotch(),
            safeAreaInsets = getSafeAreaInsets()
        )
    }

    suspend fun getAllDevices(): List<DeviceProfile> {
        return database.deviceDao().getAllDevices()
    }

    suspend fun getDeviceStats(): DeviceUsageStats {
        val devices = getAllDevices()
        val currentDeviceId = currentDevice.deviceId

        return DeviceUsageStats(
            totalDevices = devices.size,
            activeDevices = devices.count { it.lastUsed > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7) },
            primaryDevice = devices.maxByOrNull { it.lastUsed },
            currentDevice = devices.find { it.deviceId == currentDeviceId },
            deviceTypes = devices.groupBy { it.deviceType }.mapValues { it.value.size }
        )
    }
}

data class DeviceUsageStats(
    val totalDevices: Int,
    val activeDevices: Int,
    val primaryDevice: DeviceProfile?,
    val currentDevice: DeviceProfile?,
    val deviceTypes: Map<DeviceType, Int>
)
```

## ⚙️ Device-Aware Preference System

### Layered Preference Architecture

```kotlin
enum class PreferenceScope {
    GLOBAL,        // Shared across all devices (library, tracking)
    DEVICE_TYPE,   // Shared among devices of same type (phone settings for all phones)
    DEVICE,        // Specific to individual device
    SESSION        // Temporary, current session only
}

data class PreferenceLayer(
    val scope: PreferenceScope,
    val identifier: String, // deviceId, deviceType, "global", or sessionId
    val preferences: Map<String, Any>
)

class DeviceAwarePreferenceStore(
    private val deviceManager: DeviceManager,
    private val globalPrefs: PreferencesHelper,
    private val database: AppDatabase
) {

    private val sessionPrefs = mutableMapOf<String, Any>()
    private val preferenceLayering = mapOf(
        // Reading preferences - device-specific
        "default_reading_mode" to PreferenceScope.DEVICE,
        "page_layout" to PreferenceScope.DEVICE,
        "orientation_type" to PreferenceScope.DEVICE,
        "zoom_start_type" to PreferenceScope.DEVICE,
        "crop_borders" to PreferenceScope.DEVICE,
        "custom_brightness" to PreferenceScope.DEVICE,
        "fullscreen" to PreferenceScope.DEVICE,
        "keep_screen_on" to PreferenceScope.DEVICE,
        "show_page_number" to PreferenceScope.DEVICE,
        "dual_page_split" to PreferenceScope.DEVICE,
        "dual_page_invert" to PreferenceScope.DEVICE,

        // UI preferences - device type specific
        "library_layout" to PreferenceScope.DEVICE_TYPE,
        "grid_size" to PreferenceScope.DEVICE_TYPE,
        "uniform_grid" to PreferenceScope.DEVICE_TYPE,
        "nav_style" to PreferenceScope.DEVICE_TYPE,
        "theme" to PreferenceScope.DEVICE_TYPE,

        // Library data - global
        "library_sorting" to PreferenceScope.GLOBAL,
        "library_filters" to PreferenceScope.GLOBAL,
        "categories" to PreferenceScope.GLOBAL,
        "library_update_interval" to PreferenceScope.GLOBAL,

        // Downloads - device-specific for storage
        "download_path" to PreferenceScope.DEVICE,
        "download_wifi_only" to PreferenceScope.DEVICE,
        "download_threads" to PreferenceScope.DEVICE,

        // Tracking - global
        "auto_update_track" to PreferenceScope.GLOBAL,
        "track_marked_as_read" to PreferenceScope.GLOBAL
    )

    fun <T> getPreference(key: String, defaultValue: T): T {
        val scope = preferenceLayering[key] ?: PreferenceScope.GLOBAL

        return when (scope) {
            PreferenceScope.SESSION -> {
                @Suppress("UNCHECKED_CAST")
                sessionPrefs[key] as? T ?: defaultValue
            }
            PreferenceScope.DEVICE -> {
                getDevicePreference(key, deviceManager.currentDevice.deviceId, defaultValue)
            }
            PreferenceScope.DEVICE_TYPE -> {
                getDeviceTypePreference(key, deviceManager.currentDevice.deviceType, defaultValue)
            }
            PreferenceScope.GLOBAL -> {
                getGlobalPreference(key, defaultValue)
            }
        }
    }

    fun <T> setPreference(key: String, value: T, scope: PreferenceScope? = null) {
        val targetScope = scope ?: preferenceLayering[key] ?: PreferenceScope.GLOBAL

        when (targetScope) {
            PreferenceScope.SESSION -> {
                sessionPrefs[key] = value as Any
            }
            PreferenceScope.DEVICE -> {
                setDevicePreference(key, deviceManager.currentDevice.deviceId, value)
            }
            PreferenceScope.DEVICE_TYPE -> {
                setDeviceTypePreference(key, deviceManager.currentDevice.deviceType, value)
            }
            PreferenceScope.GLOBAL -> {
                setGlobalPreference(key, value)
            }
        }
    }

    private fun <T> getDevicePreference(key: String, deviceId: String, defaultValue: T): T {
        return runBlocking {
            val pref = database.devicePreferenceDao().getPreference(deviceId, key)
            pref?.let { parsePreferenceValue(it.value, defaultValue) } ?: defaultValue
        }
    }

    private fun <T> getDeviceTypePreference(key: String, deviceType: DeviceType, defaultValue: T): T {
        return runBlocking {
            val pref = database.deviceTypePreferenceDao().getPreference(deviceType.id, key)
            pref?.let { parsePreferenceValue(it.value, defaultValue) } ?: defaultValue
        }
    }

    private fun <T> getGlobalPreference(key: String, defaultValue: T): T {
        return when (defaultValue) {
            is String -> globalPrefs.getString(key, defaultValue as String) as T
            is Int -> globalPrefs.getInt(key, defaultValue as Int) as T
            is Boolean -> globalPrefs.getBoolean(key, defaultValue as Boolean) as T
            is Float -> globalPrefs.getFloat(key, defaultValue as Float) as T
            is Long -> globalPrefs.getLong(key, defaultValue as Long) as T
            else -> defaultValue
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> parsePreferenceValue(value: String, defaultValue: T): T {
        return try {
            when (defaultValue) {
                is String -> value as T
                is Int -> value.toInt() as T
                is Boolean -> value.toBoolean() as T
                is Float -> value.toFloat() as T
                is Long -> value.toLong() as T
                else -> Gson().fromJson(value, defaultValue!!::class.java) as T
            }
        } catch (e: Exception) {
            defaultValue
        }
    }
}
```

### Smart Default Preferences

```kotlin
class SmartDefaultProvider(
    private val deviceManager: DeviceManager
) {

    fun getSmartDefaults(deviceProfile: DeviceProfile): Map<String, Any> {
        val defaults = mutableMapOf<String, Any>()

        // Reading mode defaults based on device type
        defaults["default_reading_mode"] = when (deviceProfile.deviceType) {
            DeviceType.PHONE -> ReadingModeType.VERTICAL.flagValue
            DeviceType.TABLET -> ReadingModeType.LEFT_TO_RIGHT.flagValue
            DeviceType.FOLDABLE -> ReadingModeType.LEFT_TO_RIGHT.flagValue
            DeviceType.DESKTOP -> ReadingModeType.LEFT_TO_RIGHT.flagValue
            else -> ReadingModeType.VERTICAL.flagValue
        }

        // Page layout based on screen size
        defaults["page_layout"] = when {
            deviceProfile.displayMetrics.smallestWidthDp >= 720 -> PageLayout.DOUBLE_PAGES.value
            deviceProfile.displayMetrics.smallestWidthDp >= 600 -> PageLayout.AUTOMATIC.value
            else -> PageLayout.SINGLE_PAGE.value
        }

        // Orientation based on device capabilities
        defaults["orientation_type"] = when {
            deviceProfile.orientation == DeviceOrientation.BOTH -> OrientationType.AUTO.value
            deviceProfile.deviceType == DeviceType.PHONE -> OrientationType.PORTRAIT.value
            deviceProfile.deviceType == DeviceType.TABLET -> OrientationType.AUTOMATIC.value
            else -> OrientationType.AUTOMATIC.value
        }

        // Grid size for library based on screen size
        defaults["grid_size"] = when {
            deviceProfile.displayMetrics.smallestWidthDp >= 840 -> 6f // Desktop/large tablet
            deviceProfile.displayMetrics.smallestWidthDp >= 720 -> 5f // Large tablet
            deviceProfile.displayMetrics.smallestWidthDp >= 600 -> 4f // Small tablet
            deviceProfile.displayMetrics.smallestWidthDp >= 480 -> 3f // Large phone
            else -> 2f // Small phone
        }

        // Navigation style based on input methods
        defaults["nav_style"] = when {
            InputMethod.MOUSE in deviceProfile.inputMethods -> "desktop"
            InputMethod.STYLUS in deviceProfile.inputMethods -> "stylus"
            deviceProfile.deviceType == DeviceType.TV -> "tv"
            else -> "touch"
        }

        // Fullscreen behavior
        defaults["fullscreen"] = when (deviceProfile.deviceType) {
            DeviceType.PHONE -> true
            DeviceType.TV -> true
            else -> false
        }

        // Custom brightness
        defaults["custom_brightness"] = deviceProfile.capabilities.contains(DeviceCapability.ALWAYS_ON_DISPLAY)

        // Download settings based on device storage and type
        defaults["download_wifi_only"] = when (deviceProfile.deviceType) {
            DeviceType.PHONE -> true // Preserve mobile data
            DeviceType.AUTOMOTIVE -> true // Limit data usage
            else -> false
        }

        defaults["download_threads"] = when {
            deviceProfile.capabilities.contains(DeviceCapability.HIGH_REFRESH_RATE) -> 4
            deviceProfile.deviceType == DeviceType.DESKTOP -> 6
            deviceProfile.deviceType == DeviceType.TABLET -> 3
            else -> 2
        }

        return defaults
    }

    fun getContextualDefaults(
        deviceProfile: DeviceProfile,
        usageContext: UsageContext
    ): Map<String, Any> {
        val defaults = mutableMapOf<String, Any>()

        when (usageContext.environment) {
            Environment.HOME -> {
                defaults["keep_screen_on"] = true
                defaults["custom_brightness"] = false
            }
            Environment.COMMUTE -> {
                defaults["keep_screen_on"] = false
                defaults["custom_brightness"] = true
                defaults["download_wifi_only"] = true
            }
            Environment.WORK -> {
                defaults["fullscreen"] = false
                defaults["show_page_number"] = false
            }
            Environment.BED -> {
                defaults["custom_brightness"] = true
                defaults["theme"] = "dark"
            }
        }

        when (usageContext.timeOfDay) {
            in 22..23, in 0..6 -> {
                defaults["theme"] = "dark"
                defaults["custom_brightness"] = true
            }
            in 6..18 -> {
                defaults["theme"] = "light"
                defaults["custom_brightness"] = false
            }
        }

        return defaults
    }
}

data class UsageContext(
    val environment: Environment,
    val timeOfDay: Int, // 0-23
    val networkType: NetworkType,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val ambientLight: Float?
)

enum class Environment {
    HOME, WORK, COMMUTE, BED, OUTDOOR, UNKNOWN
}

enum class NetworkType {
    WIFI, MOBILE, ETHERNET, NONE
}
```

## 🔄 Preference Synchronization

### Selective Sync Rules

```kotlin
class PreferenceSyncManager(
    private val deviceAwarePrefs: DeviceAwarePreferenceStore,
    private val cloudProvider: CloudProvider,
    private val conflictResolver: PreferenceConflictResolver
) {

    private val syncRules = mapOf(
        // Never sync - always device-specific
        "download_path" to SyncRule.NEVER,
        "custom_brightness" to SyncRule.NEVER,
        "device_id" to SyncRule.NEVER,

        // Conditional sync - based on device type compatibility
        "default_reading_mode" to SyncRule.CONDITIONAL { from, to ->
            from.deviceType == to.deviceType ||
            (from.deviceType.isTabletClass() && to.deviceType.isTabletClass())
        },
        "page_layout" to SyncRule.CONDITIONAL { from, to ->
            from.displayMetrics.smallestWidthDp.similarTo(to.displayMetrics.smallestWidthDp)
        },
        "orientation_type" to SyncRule.CONDITIONAL { from, to ->
            from.orientation == to.orientation
        },

        // Always sync - global preferences
        "library_sorting" to SyncRule.ALWAYS,
        "library_filters" to SyncRule.ALWAYS,
        "auto_update_track" to SyncRule.ALWAYS,
        "backup_interval" to SyncRule.ALWAYS,

        // User choice - ask user on first sync
        "theme" to SyncRule.USER_CHOICE,
        "grid_size" to SyncRule.USER_CHOICE,
        "library_layout" to SyncRule.USER_CHOICE
    )

    suspend fun syncPreferencesToDevice(
        sourceDevice: DeviceProfile,
        targetDevice: DeviceProfile,
        userChoices: Map<String, SyncChoice> = emptyMap()
    ): SyncResult {
        val preferences = getAllPreferences(sourceDevice.deviceId)
        val syncablePrefs = mutableMapOf<String, Any>()
        val conflicts = mutableListOf<PreferenceConflict>()
        val skipped = mutableListOf<String>()

        preferences.forEach { (key, value) ->
            val rule = syncRules[key] ?: SyncRule.CONDITIONAL { _, _ -> false }

            when (rule) {
                SyncRule.NEVER -> skipped.add(key)
                SyncRule.ALWAYS -> syncablePrefs[key] = value
                SyncRule.USER_CHOICE -> {
                    when (userChoices[key] ?: SyncChoice.ASK) {
                        SyncChoice.SYNC -> syncablePrefs[key] = value
                        SyncChoice.SKIP -> skipped.add(key)
                        SyncChoice.ASK -> {
                            // Add to conflicts for user decision
                            val targetValue = getTargetPreference(targetDevice.deviceId, key)
                            if (targetValue != null && targetValue != value) {
                                conflicts.add(PreferenceConflict(key, value, targetValue))
                            } else {
                                syncablePrefs[key] = value
                            }
                        }
                    }
                }
                is SyncRule.CONDITIONAL -> {
                    if (rule.condition(sourceDevice, targetDevice)) {
                        syncablePrefs[key] = value
                    } else {
                        skipped.add(key)
                    }
                }
            }
        }

        // Apply syncable preferences
        syncablePrefs.forEach { (key, value) ->
            deviceAwarePrefs.setPreference(key, value)
        }

        return SyncResult(
            synced = syncablePrefs.keys.toList(),
            skipped = skipped,
            conflicts = conflicts
        )
    }

    private fun DeviceType.isTabletClass(): Boolean {
        return this in setOf(DeviceType.TABLET, DeviceType.FOLDABLE, DeviceType.CHROMEBOOK, DeviceType.DESKTOP)
    }

    private fun Int.similarTo(other: Int): Boolean {
        return abs(this - other) <= 100 // Within 100dp is considered similar
    }
}

sealed class SyncRule {
    object ALWAYS : SyncRule()
    object NEVER : SyncRule()
    object USER_CHOICE : SyncRule()
    data class CONDITIONAL(val condition: (DeviceProfile, DeviceProfile) -> Boolean) : SyncRule()
}

enum class SyncChoice {
    SYNC, SKIP, ASK
}

data class PreferenceConflict(
    val key: String,
    val sourceValue: Any,
    val targetValue: Any
)

data class SyncResult(
    val synced: List<String>,
    val skipped: List<String>,
    val conflicts: List<PreferenceConflict>
)
```

### Preference Migration System

```kotlin
class PreferenceMigrationManager(
    private val deviceManager: DeviceManager,
    private val database: AppDatabase,
    private val oldPrefs: SharedPreferences
) {

    suspend fun migrateToDeviceAware() {
        val currentDevice = deviceManager.currentDevice
        val allPreferences = oldPrefs.all

        allPreferences.forEach { (key, value) ->
            val targetScope = determineScope(key)

            when (targetScope) {
                PreferenceScope.GLOBAL -> {
                    // Keep in global preferences - no migration needed
                }
                PreferenceScope.DEVICE -> {
                    migrateToDevicePreference(key, value, currentDevice.deviceId)
                }
                PreferenceScope.DEVICE_TYPE -> {
                    migrateToDeviceTypePreference(key, value, currentDevice.deviceType)
                }
                PreferenceScope.SESSION -> {
                    // Session preferences don't need migration
                }
            }
        }

        // Mark migration as complete
        oldPrefs.edit().putBoolean("migrated_to_device_aware", true).apply()
    }

    private fun determineScope(key: String): PreferenceScope {
        return when {
            key.startsWith("library_") -> PreferenceScope.GLOBAL
            key.startsWith("track_") -> PreferenceScope.GLOBAL
            key.startsWith("reader_") -> PreferenceScope.DEVICE
            key.startsWith("download_") -> PreferenceScope.DEVICE
            key.contains("theme") -> PreferenceScope.DEVICE_TYPE
            key.contains("layout") -> PreferenceScope.DEVICE_TYPE
            else -> PreferenceScope.GLOBAL
        }
    }

    private suspend fun migrateToDevicePreference(key: String, value: Any, deviceId: String) {
        val devicePref = DevicePreference(
            deviceId = deviceId,
            key = key,
            value = value.toString(),
            type = value::class.simpleName ?: "String",
            createdAt = System.currentTimeMillis()
        )
        database.devicePreferenceDao().insert(devicePref)
    }

    private suspend fun migrateToDeviceTypePreference(key: String, value: Any, deviceType: DeviceType) {
        val deviceTypePref = DeviceTypePreference(
            deviceType = deviceType.id,
            key = key,
            value = value.toString(),
            type = value::class.simpleName ?: "String",
            createdAt = System.currentTimeMillis()
        )
        database.deviceTypePreferenceDao().insert(deviceTypePref)
    }
}
```

## 📊 Database Schema for Device-Specific Preferences

### Database Entities

```kotlin
@Entity(
    tableName = "device_profiles",
    indices = [Index(value = ["device_id"], unique = true)]
)
data class DeviceProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "device_type")
    val deviceType: String,

    @ColumnInfo(name = "display_metrics")
    val displayMetrics: String, // JSON

    @ColumnInfo(name = "capabilities")
    val capabilities: String, // JSON array

    @ColumnInfo(name = "orientation")
    val orientation: String,

    @ColumnInfo(name = "input_methods")
    val inputMethods: String, // JSON array

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "last_used")
    val lastUsed: Long,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "custom_name")
    val customName: String? = null
)

@Entity(
    tableName = "device_preferences",
    primaryKeys = ["device_id", "key"],
    indices = [
        Index(value = ["device_id"]),
        Index(value = ["key"]),
        Index(value = ["updated_at"])
    ]
)
data class DevicePreference(
    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "value")
    val value: String,

    @ColumnInfo(name = "type")
    val type: String, // "String", "Int", "Boolean", etc.

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "pending"
)

@Entity(
    tableName = "device_type_preferences",
    primaryKeys = ["device_type", "key"],
    indices = [
        Index(value = ["device_type"]),
        Index(value = ["key"])
    ]
)
data class DeviceTypePreference(
    @ColumnInfo(name = "device_type")
    val deviceType: String,

    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "value")
    val value: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "preference_sync_log",
    indices = [Index(value = ["timestamp"], unique = false)]
)
data class PreferenceSyncLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "source_device")
    val sourceDevice: String,

    @ColumnInfo(name = "target_device")
    val targetDevice: String,

    @ColumnInfo(name = "preferences_synced")
    val preferencesSynced: String, // JSON array of keys

    @ColumnInfo(name = "conflicts_resolved")
    val conflictsResolved: String, // JSON array of conflicts

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "sync_trigger")
    val syncTrigger: String // "manual", "auto", "migration"
)
```

### Data Access Objects

```kotlin
@Dao
interface DeviceDao {

    @Query("SELECT * FROM device_profiles WHERE device_id = :deviceId")
    suspend fun getDevice(deviceId: String): DeviceProfileEntity?

    @Query("SELECT * FROM device_profiles WHERE is_active = 1 ORDER BY last_used DESC")
    suspend fun getAllDevices(): List<DeviceProfileEntity>

    @Query("SELECT * FROM device_profiles WHERE device_type = :deviceType AND is_active = 1")
    suspend fun getDevicesByType(deviceType: String): List<DeviceProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceProfile): Long

    @Query("UPDATE device_profiles SET last_used = :timestamp WHERE device_id = :deviceId")
    suspend fun updateLastUsed(deviceId: String, timestamp: Long)

    @Query("UPDATE device_profiles SET is_active = 0 WHERE device_id = :deviceId")
    suspend fun deactivateDevice(deviceId: String)

    @Query("UPDATE device_profiles SET custom_name = :name WHERE device_id = :deviceId")
    suspend fun setCustomName(deviceId: String, name: String)
}

@Dao
interface DevicePreferenceDao {

    @Query("SELECT * FROM device_preferences WHERE device_id = :deviceId AND key = :key")
    suspend fun getPreference(deviceId: String, key: String): DevicePreference?

    @Query("SELECT * FROM device_preferences WHERE device_id = :deviceId")
    suspend fun getAllPreferences(deviceId: String): List<DevicePreference>

    @Query("SELECT * FROM device_preferences WHERE key = :key")
    suspend fun getPreferenceAcrossDevices(key: String): List<DevicePreference>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: DevicePreference)

    @Query("DELETE FROM device_preferences WHERE device_id = :deviceId AND key = :key")
    suspend fun deletePreference(deviceId: String, key: String)

    @Query("DELETE FROM device_preferences WHERE device_id = :deviceId")
    suspend fun deleteAllPreferences(deviceId: String)

    @Query("SELECT COUNT(*) FROM device_preferences WHERE device_id = :deviceId")
    suspend fun getPreferenceCount(deviceId: String): Int

    @Transaction
    suspend fun bulkUpsert(preferences: List<DevicePreference>) {
        preferences.forEach { upsert(it) }
    }
}

@Dao
interface DeviceTypePreferenceDao {

    @Query("SELECT * FROM device_type_preferences WHERE device_type = :deviceType AND key = :key")
    suspend fun getPreference(deviceType: String, key: String): DeviceTypePreference?

    @Query("SELECT * FROM device_type_preferences WHERE device_type = :deviceType")
    suspend fun getAllPreferences(deviceType: String): List<DeviceTypePreference>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: DeviceTypePreference)

    @Query("DELETE FROM device_type_preferences WHERE device_type = :deviceType AND key = :key")
    suspend fun deletePreference(deviceType: String, key: String)
}
```

## 🎛️ Settings UI Enhancements

### Device Management Interface

```kotlin
class DeviceManagementFragment : PreferenceFragmentCompat() {

    private lateinit var deviceManager: DeviceManager
    private lateinit var deviceAwarePrefs: DeviceAwarePreferenceStore

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.device_preferences)

        deviceManager = DeviceManager(requireContext(), database, preferences)
        deviceAwarePrefs = DeviceAwarePreferenceStore(deviceManager, preferences, database)

        setupDeviceList()
        setupSyncOptions()
        setupMigrationOptions()
    }

    private fun setupDeviceList() {
        val deviceCategory = findPreference<PreferenceCategory>("device_list")!!

        lifecycleScope.launch {
            val devices = deviceManager.getAllDevices()
            val currentDeviceId = deviceManager.currentDevice.deviceId

            devices.forEach { device ->
                val devicePref = createDevicePreference(device, device.deviceId == currentDeviceId)
                deviceCategory.addPreference(devicePref)
            }
        }
    }

    private fun createDevicePreference(device: DeviceProfile, isCurrent: Boolean): Preference {
        return Preference(requireContext()).apply {
            title = device.customName ?: "${device.deviceType.name.lowercase().capitalize()} (${device.deviceId.takeLast(6)})"
            summary = buildString {
                append("${device.displayMetrics.widthDp}×${device.displayMetrics.heightDp}dp")
                if (isCurrent) append(" • Current device")
                append(" • Last used: ${formatLastUsed(device.lastUsed)}")
            }

            icon = ContextCompat.getDrawable(context, getDeviceIcon(device.deviceType))

            setOnPreferenceClickListener {
                showDeviceDetails(device)
                true
            }

            if (isCurrent) {
                summary = "$summary\n${getString(R.string.current_device)}"
                isEnabled = true
            }
        }
    }

    private fun showDeviceDetails(device: DeviceProfile) {
        DeviceDetailDialog().show(childFragmentManager, device) { action ->
            when (action) {
                DeviceAction.RENAME -> showRenameDialog(device)
                DeviceAction.SYNC_FROM -> showSyncFromDialog(device)
                DeviceAction.SYNC_TO -> showSyncToDialog(device)
                DeviceAction.REMOVE -> showRemoveDeviceDialog(device)
                DeviceAction.SET_PRIMARY -> setPrimaryDevice(device)
            }
        }
    }

    private fun setupSyncOptions() {
        findPreference<SwitchPreferenceCompat>("auto_sync_preferences")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (enabled) {
                    showAutoSyncSetupDialog()
                }
                true
            }
        }

        findPreference<ListPreference>("sync_frequency")?.apply {
            entries = arrayOf("Immediately", "Every 5 minutes", "Every hour", "Daily", "Manual only")
            entryValues = arrayOf("0", "300", "3600", "86400", "-1")
        }

        findPreference<MultiSelectListPreference>("sync_exclude_keys")?.apply {
            entries = getPreferenceDisplayNames()
            entryValues = getPreferenceKeys()
            setDefaultValue(setOf("download_path", "custom_brightness"))
        }
    }

    private fun getDeviceIcon(deviceType: DeviceType): Int {
        return when (deviceType) {
            DeviceType.PHONE -> R.drawable.ic_smartphone
            DeviceType.TABLET -> R.drawable.ic_tablet
            DeviceType.FOLDABLE -> R.drawable.ic_foldable
            DeviceType.CHROMEBOOK -> R.drawable.ic_laptop
            DeviceType.DESKTOP -> R.drawable.ic_desktop
            DeviceType.TV -> R.drawable.ic_tv
            DeviceType.AUTOMOTIVE -> R.drawable.ic_car
        }
    }
}

class DeviceDetailDialog : DialogFragment() {

    fun show(
        fragmentManager: FragmentManager,
        device: DeviceProfile,
        onAction: (DeviceAction) -> Unit
    ) {
        val args = Bundle().apply {
            putString("device_json", Gson().toJson(device))
        }
        arguments = args
        show(fragmentManager, "device_detail")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val device = Gson().fromJson(
            arguments?.getString("device_json"),
            DeviceProfile::class.java
        )

        val binding = DialogDeviceDetailBinding.inflate(layoutInflater)

        // Populate device details
        binding.deviceName.text = device.customName ?: "Unnamed ${device.deviceType.name}"
        binding.deviceType.text = device.deviceType.name.lowercase().capitalize()
        binding.deviceId.text = device.deviceId
        binding.displayInfo.text = "${device.displayMetrics.widthDp}×${device.displayMetrics.heightDp}dp"
        binding.lastUsed.text = formatLastUsed(device.lastUsed)

        // Capabilities
        binding.capabilitiesList.text = device.capabilities.joinToString(", ") {
            it.name.lowercase().replace("_", " ").capitalize()
        }

        // Setup action buttons
        binding.renameButton.setOnClickListener {
            onAction(DeviceAction.RENAME)
            dismiss()
        }

        binding.syncFromButton.setOnClickListener {
            onAction(DeviceAction.SYNC_FROM)
            dismiss()
        }

        binding.syncToButton.setOnClickListener {
            onAction(DeviceAction.SYNC_TO)
            dismiss()
        }

        binding.removeButton.setOnClickListener {
            onAction(DeviceAction.REMOVE)
            dismiss()
        }

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .setTitle("Device Details")
            .setNegativeButton("Close", null)
            .create()
    }
}

enum class DeviceAction {
    RENAME, SYNC_FROM, SYNC_TO, REMOVE, SET_PRIMARY
}
```

This comprehensive Device-Specific Settings Framework provides the foundation for intelligent preference management across multiple devices while maintaining the flexibility to customize settings per device type and individual devices. The system intelligently syncs appropriate settings while respecting device-specific requirements and user preferences.
