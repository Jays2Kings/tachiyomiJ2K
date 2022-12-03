package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumedWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import eu.kanade.core.prefs.asState
import eu.kanade.core.util.fastFilter
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.NavigationBar
import eu.kanade.presentation.components.NavigationRail
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.util.Tab
import eu.kanade.presentation.util.Transition
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object HomeScreen : Screen {

    private val librarySearchEvent = Channel<String>()
    private val openTabEvent = Channel<Tab>()
    private val showBottomNavEvent = Channel<Boolean>()

    private val tabs = listOf(
        LibraryTab,
        UpdatesTab,
        HistoryTab,
        BrowseTab(),
        MoreTab(),
    )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        // SY -->
        val scope = rememberCoroutineScope()
        val alwaysShowLabel by remember {
            Injekt.get<UiPreferences>().bottomBarLabels().asState(scope)
        }
        // SY <--
        TabNavigator(
            tab = LibraryTab,
        ) { tabNavigator ->
            // Provide usable navigator to content screen
            CompositionLocalProvider(LocalNavigator provides navigator) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isTabletUi()) {
                        NavigationRail {
                            tabs
                                // SY -->
                                .fastFilter { it.isEnabled() }
                                // SY <--
                                .fastForEach {
                                    NavigationRailItem(it/* SY --> */, alwaysShowLabel/* SY <-- */)
                                }
                        }
                    }
                    Scaffold(
                        bottomBar = {
                            if (!isTabletUi()) {
                                val bottomNavVisible by produceState(initialValue = true) {
                                    showBottomNavEvent.receiveAsFlow().collectLatest { value = it }
                                }
                                AnimatedVisibility(
                                    visible = bottomNavVisible,
                                    enter = expandVertically(),
                                    exit = shrinkVertically(),
                                ) {
                                    NavigationBar {
                                        tabs
                                            // SY -->
                                            .fastFilter { it.isEnabled() }
                                            // SY <--
                                            .fastForEach {
                                                NavigationBarItem(it/* SY --> */, alwaysShowLabel/* SY <-- */)
                                            }
                                    }
                                }
                            }
                        },
                        contentWindowInsets = WindowInsets(0),
                    ) { contentPadding ->
                        Box(
                            modifier = Modifier
                                .padding(contentPadding)
                                .consumedWindowInsets(contentPadding),
                        ) {
                            AnimatedContent(
                                targetState = tabNavigator.current,
                                transitionSpec = { Transition.OneWayFade },
                                content = {
                                    tabNavigator.saveableState(key = "currentTab", it) {
                                        it.Content()
                                    }
                                },
                            )
                        }
                    }
                }
            }

            val goToLibraryTab = { tabNavigator.current = LibraryTab }
            BackHandler(
                enabled = tabNavigator.current != LibraryTab,
                onBack = goToLibraryTab,
            )

            LaunchedEffect(Unit) {
                launch {
                    librarySearchEvent.receiveAsFlow().collectLatest {
                        goToLibraryTab()
                        LibraryTab.search(it)
                    }
                }
                launch {
                    openTabEvent.receiveAsFlow().collectLatest {
                        tabNavigator.current = when (it) {
                            is Tab.Library -> LibraryTab
                            Tab.Updates -> UpdatesTab
                            Tab.History -> HistoryTab
                            is Tab.Browse -> BrowseTab(it.toExtensions)
                            is Tab.More -> MoreTab(it.toDownloads)
                        }

                        if (it is Tab.Library && it.mangaIdToOpen != null) {
                            navigator.push(MangaScreen(it.mangaIdToOpen))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RowScope.NavigationBarItem(tab: eu.kanade.presentation.util.Tab/* SY --> */, alwaysShowLabel: Boolean/* SY <-- */) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationBarItem(
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab) },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                )
            },
            alwaysShowLabel = /* SY --> */alwaysShowLabel, /* SY <-- */
        )
    }

    @Composable
    fun NavigationRailItem(tab: eu.kanade.presentation.util.Tab/* SY --> */, alwaysShowLabel: Boolean/* SY <-- */) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationRailItem(
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab) },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                )
            },
            alwaysShowLabel = /* SY --> */alwaysShowLabel, /* SY <-- */
        )
    }

    @Composable
    private fun NavigationIconItem(tab: eu.kanade.presentation.util.Tab) {
        BadgedBox(
            badge = {
                when {
                    tab is UpdatesTab -> {
                        val count by produceState(initialValue = 0) {
                            val pref = Injekt.get<LibraryPreferences>()
                            combine(
                                pref.showUpdatesNavBadge().changes(),
                                pref.unreadUpdatesCount().changes(),
                            ) { show, count -> if (show) count else 0 }
                                .collectLatest { value = it }
                        }
                        if (count > 0) {
                            Badge {
                                val desc = pluralStringResource(
                                    id = R.plurals.notification_chapters_generic,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                    BrowseTab::class.isInstance(tab) -> {
                        val count by produceState(initialValue = 0) {
                            Injekt.get<SourcePreferences>().extensionUpdatesCount().changes()
                                .collectLatest { value = it }
                        }
                        if (count > 0) {
                            Badge {
                                val desc = pluralStringResource(
                                    id = R.plurals.update_check_notification_ext_updates,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                }
            },
        ) {
            Icon(painter = tab.options.icon!!, contentDescription = tab.options.title)
        }
    }

    suspend fun search(query: String) {
        librarySearchEvent.send(query)
    }

    suspend fun openTab(tab: Tab) {
        openTabEvent.send(tab)
    }

    suspend fun showBottomNav(show: Boolean) {
        showBottomNavEvent.send(show)
    }

    sealed class Tab {
        data class Library(val mangaIdToOpen: Long? = null) : Tab()
        object Updates : Tab()
        object History : Tab()
        data class Browse(val toExtensions: Boolean = false) : Tab()
        data class More(val toDownloads: Boolean) : Tab()
    }
}