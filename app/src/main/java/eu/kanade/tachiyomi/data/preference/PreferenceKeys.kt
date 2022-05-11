package eu.kanade.tachiyomi.data.preference

/**
 * This class stores the keys for the preferences in the application.
 */
object PreferenceKeys {

    const val theme = "pref_theme_key"

    const val nightMode = "night_mode"
    const val lightTheme = "light_theme"
    const val darkTheme = "dark_theme"
    const val themeDarkAmoled = "pref_theme_dark_amoled_key"

    const val startingTab = "starting_tab"

    const val backToStart = "back_to_start"

    const val deniedA11FilePermission = "denied_a11_file_permission"

    const val enableTransitions = "pref_enable_transitions_key"

    const val pagerCutoutBehavior = "pager_cutout_behavior"

    const val doubleTapAnimationSpeed = "pref_double_tap_anim_speed"

    const val showPageNumber = "pref_show_page_number_key"

    const val trueColor = "pref_true_color_key"

    const val fullscreen = "fullscreen"

    const val keepScreenOn = "pref_keep_screen_on_key"

    const val customBrightness = "pref_custom_brightness_key"

    const val customBrightnessValue = "custom_brightness_value"

    const val colorFilter = "pref_color_filter_key"

    const val colorFilterValue = "color_filter_value"

    const val colorFilterMode = "color_filter_mode"

    const val defaultReadingMode = "pref_default_reading_mode_key"

    const val defaultOrientationType = "pref_default_orientation_type_key"

    const val imageScaleType = "pref_image_scale_type_key"

    const val zoomStart = "pref_zoom_start_key"

    const val readerTheme = "pref_reader_theme_key"

    const val cropBorders = "crop_borders"

    const val cropBordersWebtoon = "crop_borders_webtoon"

    const val readWithLongTap = "reader_long_tap"

    const val readWithVolumeKeys = "reader_volume_keys"

    const val readWithVolumeKeysInverted = "reader_volume_keys_inverted"

    const val navigationModePager = "reader_navigation_mode_pager"

    const val navigationModeWebtoon = "reader_navigation_mode_webtoon"

    const val pagerNavInverted = "reader_tapping_inverted"

    const val webtoonNavInverted = "reader_tapping_inverted_webtoon"

    const val pageLayout = "page_layout"

    const val automaticSplitsPage = "automatic_splits_page"

    const val invertDoublePages = "invert_double_pages"

    const val webtoonPageLayout = "webtoon_page_layout"

    const val webtoonInvertDoublePages = "webtoon_invert_double_pages"

    const val readerBottomButtons = "reader_bottom_buttons"

    const val showNavigationOverlayNewUser = "reader_navigation_overlay_new_user"
    const val showNavigationOverlayNewUserWebtoon = "reader_navigation_overlay_new_user_webtoon"

    const val preloadSize = "preload_size"

    const val webtoonSidePadding = "webtoon_side_padding"

    const val webtoonEnableZoomOut = "webtoon_enable_zoom_out"

    const val autoUpdateTrack = "pref_auto_update_manga_sync_key"

    const val trackMarkedAsRead = "track_marked_as_read"

    const val trackingsToAddOnline = "pref_tracking_for_online"

    const val lastUsedCatalogueSource = "last_catalogue_source"

    const val lastUsedCategory = "last_used_category"

    const val catalogueAsList = "pref_display_catalogue_as_list"

    const val enabledLanguages = "source_languages"

    const val sourcesSort = "sources_sort"

    const val backupDirectory = "backup_directory"

    const val downloadsDirectory = "download_directory"

    const val downloadOnlyOverWifi = "pref_download_only_over_wifi_key"

    const val folderPerManga = "create_folder_per_manga"

    const val showLibrarySearchSuggestions = "show_library_search_suggestions"

    const val librarySearchSuggestion = "library_search_suggestion"

    const val numberOfBackups = "backup_slots"

    const val backupInterval = "backup_interval"

    const val removeAfterReadSlots = "remove_after_read_slots"

    const val deleteRemovedChapters = "delete_removed_chapters"

    const val removeAfterMarkedAsRead = "pref_remove_after_marked_as_read_key"

    const val libraryUpdateInterval = "pref_library_update_interval_key"

    const val filterDownloaded = "pref_filter_downloaded_key"

    const val filterUnread = "pref_filter_unread_key"

    const val filterCompleted = "pref_filter_completed_key"

    const val filterTracked = "pref_filter_tracked_key"

    const val filterMangaType = "pref_filter_manga_type_key"

    const val showEmptyCategoriesFiltering = "show_empty_categories_filtering"

    const val librarySortingMode = "library_sorting_mode"

    const val automaticExtUpdates = "automatic_ext_updates"

    const val installedExtensionsOrder = "installed_extensions_order"

    const val autoHideHopper = "autohide_hopper"

    const val hopperLongPress = "hopper_long_press"

    const val onlySearchPinned = "only_search_pinned"

    const val downloadNew = "download_new"

    const val libraryLayout = "pref_display_library_layout"

    const val gridSize = "grid_size_float"

    const val uniformGrid = "uniform_grid"

    const val outlineOnCovers = "outline_on_covers"

    const val dateFormat = "app_date_format"

    const val defaultCategory = "default_category"

    const val skipRead = "skip_read"

    const val skipFiltered = "skip_filtered"

    const val downloadBadge = "display_download_badge"

    const val languageBadge = "display_language_badge"

    const val useBiometrics = "use_biometrics"

    const val lockAfter = "lock_after"

    const val lastUnlock = "last_unlock"

    const val hideNotificationContent = "hide_notification_content"

    const val removeArticles = "remove_articles"

    const val skipPreMigration = "skip_pre_migration"

    const val refreshCoversToo = "refresh_covers_too"

    const val showDLsInRecents = "show_dls_in_recents"
    const val showRemHistoryInRecents = "show_rem_history_in_recents"
    const val showReadInAllRecents = "show_read_in_all_recents"
    const val showTitleFirstInRecents = "show_title_first_in_recents"

    const val groupChaptersHistory = "group_chapters_history"

    const val showUpdatedTime = "show_updated_time"
    const val groupChaptersUpdates = "group_chapters_updates"

    const val categoryNumberOfItems = "display_number_of_items"

    const val alwaysShowChapterTransition = "always_show_chapter_transition"

    const val hideBottomNavOnScroll = "hide_bottom_nav_on_scroll"

    const val sideNavIconAlignment = "pref_side_nav_icon_alignment"

    const val showSeriesInShortcuts = "show_series_shortcuts"
    const val showSourcesInShortcuts = "show_sources_shortcuts"
    const val openChapterInShortcuts = "open_chapter_shortcuts"

    const val dohProvider = "doh_provider"

    const val useShizuku = "use_shizuku"

    const val showNsfwSource = "show_nsfw_source"

    const val themeMangaDetails = "theme_manga_details"

    const val incognitoMode = "incognito_mode"

    const val sideNavMode = "side_nav_mode"

    const val checkForBetas = "check_for_betas"

    const val shouldAutoUpdate = "should_auto_update"

    const val autoUpdateExtensions = "auto_update_extensions"

    const val defaultChapterFilterByRead = "default_chapter_filter_by_read"

    const val defaultChapterFilterByDownloaded = "default_chapter_filter_by_downloaded"

    const val defaultChapterFilterByBookmarked = "default_chapter_filter_by_bookmarked"

    const val defaultChapterSortBySourceOrNumber = "default_chapter_sort_by_source_or_number" // and upload date

    const val defaultChapterSortByAscendingOrDescending = "default_chapter_sort_by_ascending_or_descending"

    const val coverRatios = "cover_ratio"

    const val coverColors = "cover_colors"

    const val hideChapterTitles = "hide_chapter_titles"

    const val chaptersDescAsDefault = "chapters_desc_as_default"

    fun trackUsername(syncId: Int) = "pref_mangasync_username_$syncId"

    fun trackPassword(syncId: Int) = "pref_mangasync_password_$syncId"

    fun trackToken(syncId: Int) = "track_token_$syncId"
}
