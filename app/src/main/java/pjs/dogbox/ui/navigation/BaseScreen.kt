package pjs.dogbox.ui.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pjs.dogbox.R
import pjs.dogbox.ui.screen.DiscoverPage
import pjs.dogbox.ui.screen.DiscoverScreen
import pjs.dogbox.ui.screen.HomeScreen
import pjs.dogbox.ui.screen.LogPage
import pjs.dogbox.ui.screen.MineScreen
import pjs.dogbox.ui.screen.discover.AnimeRankScreen
import pjs.dogbox.ui.screen.discover.BoxOfficeRealtimeScreen
import pjs.dogbox.ui.screen.discover.BoxOfficeTotalScreen
import pjs.dogbox.ui.screen.discover.EncAndDecScreen
import pjs.dogbox.ui.screen.discover.HotNewsRankScreen
import pjs.dogbox.ui.screen.discover.InviteMasterScreen
import pjs.dogbox.ui.screen.discover.MediaAnalyzeScreen
import pjs.dogbox.ui.screen.discover.MediaPlayerScreen
import pjs.dogbox.ui.screen.discover.MovieRatingScreen
import pjs.dogbox.ui.screen.discover.MovieWorldScreen
import pjs.dogbox.ui.screen.discover.ScrollingTextScreen
import pjs.dogbox.ui.screen.discover.TvLiveScreen
import pjs.dogbox.ui.screen.about.ContributorScreen
import pjs.dogbox.ui.screen.about.LicenseScreen
import pjs.dogbox.ui.screen.mine.AboutMeScreen
import pjs.dogbox.ui.screen.mine.MyThemeScreen
import pjs.dogbox.ui.screen.mine.SponsorScreen
import pjs.dogbox.ui.screen.mine.WatchHistoryScreen
import pjs.dogbox.ui.screen.movie.MovieDetailScreen
import pjs.dogbox.ui.screen.movie.MovieSearchScreen
import pjs.dogbox.util.SharedData
import pjs.dogbox.util.AppData
import pjs.dogbox.util.DisclaimerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseScreen(
    onThemeChanged: (mode: Int, dynamic: Boolean, colorTheme: Int, dynamicStyle: String) -> Unit = { _, _, _, _ -> },
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { AppDestinations.entries.size })

    var currentDiscoverDetail by remember { mutableStateOf<DiscoverPage?>(null) }
    val logs = remember { mutableStateListOf<String>() }
    var showLogPage by remember { mutableStateOf(false) }
    var currentSearchQuery by remember { mutableStateOf<String?>(null) }
    var currentPlayVideoUrl by remember { mutableStateOf<String?>(null) }
    var currentPlayInitialPosition by remember { mutableStateOf(0L) }
    var currentPlayInitialEpisodeUrl by remember { mutableStateOf<String?>(null) }
    var showWatchHistory by remember { mutableStateOf(false) }
    var showMyTheme by remember { mutableStateOf(false) }
    var showAboutMe by remember { mutableStateOf(false) }
    var showSponsor by remember { mutableStateOf(false) }
    var showContributor by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }

    val onSearchMovie: (String) -> Unit = { currentSearchQuery = it }

    val showAnyOverlay =
        showLogPage || currentDiscoverDetail != null || currentSearchQuery != null || currentPlayVideoUrl != null || showWatchHistory || showMyTheme || showAboutMe || showSponsor || showContributor || showLicense
    var showTipsDialog by remember { mutableStateOf(SharedData.isFirstLaunch(context)) }

    LaunchedEffect(pagerState.currentPage) {
        currentDestination = AppDestinations.entries[pagerState.currentPage]
    }

    BackHandler(showLogPage) {
        showLogPage = false
    }

    BackHandler(currentDiscoverDetail != null && !showLogPage && currentSearchQuery == null) {
        currentDiscoverDetail = null
    }

    BackHandler(currentSearchQuery != null) {
        currentSearchQuery = null
    }

    BackHandler(showWatchHistory) {
        showWatchHistory = false
    }

    BackHandler(currentPlayVideoUrl != null) {
        currentPlayVideoUrl = null
        currentPlayInitialPosition = 0L
        currentPlayInitialEpisodeUrl = null
    }

    BackHandler(showMyTheme) {
        showMyTheme = false
    }

    BackHandler(showAboutMe) {
        showAboutMe = false
    }

    BackHandler(showSponsor) {
        showSponsor = false
    }

    BackHandler(showContributor) {
        showContributor = false
    }

    BackHandler(showLicense) {
        showLicense = false
    }

    val navigationItemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            navigationSuiteItems = {
                AppDestinations.entries.forEachIndexed { index, dest ->
                    item(
                        icon = {
                            Icon(
                                painter = painterResource(
                                    if (dest == currentDestination) dest.selectedIcon else dest.icon
                                ),
                                contentDescription = dest.label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text(dest.label) },
                        selected = dest == currentDestination,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        colors = navigationItemColors
                    )
                }
            }
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                currentDestination.label,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        actions = {
                            FilledTonalIconButton(
                                onClick = {
                                    currentSearchQuery = ""
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.search_outlined),
                                    contentDescription = "搜索",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    Toast.makeText(context, "设置", Toast.LENGTH_SHORT).show()
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.settings_outlined),
                                    contentDescription = "设置",
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                        },
                            modifier = Modifier.alpha(if (showAnyOverlay) 0f else 1f),
                        )
                    },
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    beyondViewportPageCount = 1,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) { page ->
                    when (AppDestinations.entries[page]) {
                        AppDestinations.HOME -> HomeScreen(
                            onSearchMovie = onSearchMovie,
                        )

                        AppDestinations.DISCOVER -> DiscoverScreen(
                            onNavigateToDetail = { currentDiscoverDetail = it }
                        )

                        AppDestinations.MINE -> MineScreen(
                            onNavigateToHistory = { showWatchHistory = true },
                            onNavigateToTheme = { showMyTheme = true },
                            onNavigateToAboutMe = { showAboutMe = true },
                            onNavigateToSponsor = { showSponsor = true },
                        )
                    }
                }
            }
        }
        DisclaimerDialog(
            isOpen = showTipsDialog,
            onDismiss = {
                showTipsDialog = false
                SharedData.markTipsDialogShown(context)
            },
        )

        if (currentDiscoverDetail != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                )
                val onBack: () -> Unit = { currentDiscoverDetail = null }
                when (currentDiscoverDetail) {
                    DiscoverPage.TV_LIVE -> TvLiveScreen(onBack = onBack)
                    DiscoverPage.ENC_AND_DEC -> EncAndDecScreen(onBack = onBack)
                    DiscoverPage.HOT_NEWS_RANK -> HotNewsRankScreen(onBack = onBack)
                    DiscoverPage.ANIME_RANK -> AnimeRankScreen(
                        onBack = onBack,
                        onSearchMovie = onSearchMovie
                    )

                    DiscoverPage.BOX_OFFICE_TOTAL -> BoxOfficeTotalScreen(
                        onBack = onBack,
                        onSearchMovie = onSearchMovie
                    )

                    DiscoverPage.BOX_OFFICE_REALTIME -> BoxOfficeRealtimeScreen(onBack = onBack)
                    DiscoverPage.MOVIE_RATING -> MovieRatingScreen(
                        onBack = onBack,
                        onSearchMovie = onSearchMovie
                    )

                    DiscoverPage.MOVIE_WORLD -> MovieWorldScreen(
                        onBack = onBack,
                        onSearchMovie = onSearchMovie
                    )

                    DiscoverPage.MEDIA_PLAYER -> MediaPlayerScreen(
                        onBack = onBack,
                    )
                    DiscoverPage.MEDIA_ANALYZE -> MediaAnalyzeScreen(onBack = onBack)
                    DiscoverPage.SCROLLING_TEXT -> ScrollingTextScreen(onBack = onBack)
                    DiscoverPage.INVITE_MASTER -> InviteMasterScreen(
                        onBack = onBack,
                        logs = logs,
                        onNavigateToLogs = { showLogPage = true },
                    )

                    null -> {}
                }
            }
        }

        if (currentSearchQuery != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                )
                MovieSearchScreen(
                    initialQuery = currentSearchQuery ?: "",
                    onBack = { currentSearchQuery = null },
                    onPlayVideo = { url ->
                        val regex = Regex("""/(\d+)[-/]""")
                        val videoId = regex.find(url)?.groupValues?.getOrNull(1) ?: ""
                        val history = SharedData.getWatchHistory(context)
                        val historyItem = history.find { it.id == videoId }
                        currentPlayVideoUrl = url
                        currentPlayInitialPosition = historyItem?.progress ?: 0L
                        currentPlayInitialEpisodeUrl = historyItem?.let {
                            if (it.url.startsWith("http")) it.url else "${AppData.NIVOD_HOST}${it.url}"
                        }
                    },
                )
            }
        }

        if (showWatchHistory) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                )
                WatchHistoryScreen(
                    onBack = { showWatchHistory = false },
                    onPlayMovie = { url, pos ->
                        currentPlayVideoUrl = if (url.startsWith("http")) url else "${AppData.NIVOD_HOST}$url"
                        currentPlayInitialPosition = pos
                        currentPlayInitialEpisodeUrl = if (url.startsWith("http")) url else "${AppData.NIVOD_HOST}$url"
                    },
                )
            }
        }

        if (currentPlayVideoUrl != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                )
                MovieDetailScreen(
                    videoUrl = currentPlayVideoUrl!!,
                    initialPosition = currentPlayInitialPosition,
                    initialEpisodeUrl = currentPlayInitialEpisodeUrl,
                    onBack = {
                        currentPlayVideoUrl = null
                        currentPlayInitialPosition = 0L
                        currentPlayInitialEpisodeUrl = null
                    },
                )
            }
        }

        if (showMyTheme) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                )
                MyThemeScreen(
                    onBack = { showMyTheme = false },
                    onThemeChanged = onThemeChanged,
                )
            }
        }

        if (showAboutMe) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                )
                AboutMeScreen(
                    onBack = { showAboutMe = false },
                    onNavigateToContributor = { showContributor = true },
                    onNavigateToLicense = { showLicense = true },
                )
            }
        }

        if (showSponsor) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                )
                SponsorScreen(
                    onBack = { showSponsor = false },
                )
            }
        }

        if (showLogPage) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                )
                LogPage(logs = logs, onBack = { showLogPage = false })
            }
        }

        if (showContributor) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                )
                ContributorScreen(
                    onBack = { showContributor = false },
                )
            }
        }

        if (showLicense) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                )
                LicenseScreen(
                    onBack = { showLicense = false },
                )
            }
        }
    }
}