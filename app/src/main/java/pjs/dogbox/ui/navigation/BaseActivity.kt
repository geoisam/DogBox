package pjs.dogbox.ui.navigation

import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import pjs.dogbox.R
import pjs.dogbox.ui.screen.DiscoverPage
import pjs.dogbox.ui.screen.DiscoverScreen
import pjs.dogbox.ui.screen.HomeScreen
import pjs.dogbox.ui.screen.MineScreen
import pjs.dogbox.ui.screen.about.ContributorScreen
import pjs.dogbox.ui.screen.about.LicenseScreen
import pjs.dogbox.ui.screen.discover.AnimeRankScreen
import pjs.dogbox.ui.screen.discover.BoxOfficeRealtimeScreen
import pjs.dogbox.ui.screen.discover.BoxOfficeTotalScreen
import pjs.dogbox.ui.screen.discover.EncAndDecScreen
import pjs.dogbox.ui.screen.discover.HotNewsRankScreen
import pjs.dogbox.ui.screen.discover.InviteMasterScreen
import pjs.dogbox.ui.screen.discover.MediaAnalyzeScreen
import pjs.dogbox.ui.screen.discover.MediaLinkTestScreen
import pjs.dogbox.ui.screen.discover.MovieRatingScreen
import pjs.dogbox.ui.screen.discover.MovieWorldScreen
import pjs.dogbox.ui.screen.discover.ScrollingTextScreen
import pjs.dogbox.ui.screen.discover.TvLiveScreen
import pjs.dogbox.ui.screen.mine.AboutMeScreen
import pjs.dogbox.ui.screen.mine.MyThemeScreen
import pjs.dogbox.ui.screen.mine.SponsorScreen
import pjs.dogbox.ui.screen.mine.WatchHistoryScreen
import pjs.dogbox.ui.screen.movie.MovieDetailScreen
import pjs.dogbox.ui.screen.movie.MovieSearchScreen
import pjs.dogbox.ui.screen.movie.extractHost
import pjs.dogbox.ui.screen.movie.extractVideoId
import pjs.dogbox.utils.DisclaimerDialog
import pjs.dogbox.utils.SharedData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseScreen(
    onThemeChanged: (mode: Int, dynamic: Boolean, colorTheme: Int, dynamicStyle: String) -> Unit = { _, _, _, _ -> },
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val logs = remember { mutableStateListOf<String>() }

    var showTipsDialog by remember { mutableStateOf(SharedData.isFirstLaunch(context)) }

    NavHost(
        navController = navController,
        startDestination = AppRoutes.MAIN,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(AppRoutes.MAIN) {
            MainScaffold(
                onThemeChanged = onThemeChanged,
                onSearchMovie = { navController.navigate(AppRoutes.search(it)) },
                onNavigateToDetail = { navController.navigate(AppRoutes.discoverDetail(it.name)) },
                onNavigateToHistory = { navController.navigate(AppRoutes.WATCH_HISTORY) },
                onNavigateToTheme = { navController.navigate(AppRoutes.MY_THEME) },
                onNavigateToAboutMe = { navController.navigate(AppRoutes.ABOUT_ME) },
                onNavigateToSponsor = { navController.navigate(AppRoutes.SPONSOR) },
            )
        }

        composable(
            route = AppRoutes.DISCOVER_DETAIL,
            arguments = listOf(navArgument("page") { type = NavType.StringType }),
        ) { entry ->
            val page = entry.arguments?.getString("page")?.let {
                runCatching { DiscoverPage.valueOf(it) }.getOrNull()
            }
            val onBack: () -> Unit = { navController.popBackStack() }
            val onSearchMovie: (String) -> Unit = { navController.navigate(AppRoutes.search(it)) }
            when (page) {
                DiscoverPage.TV_LIVE -> TvLiveScreen(onBack = onBack)
                DiscoverPage.ENC_AND_DEC -> EncAndDecScreen(onBack = onBack)
                DiscoverPage.HOT_NEWS_RANK -> HotNewsRankScreen(onBack = onBack)
                DiscoverPage.ANIME_RANK -> AnimeRankScreen(
                    onBack = onBack,
                    onSearchMovie = onSearchMovie,
                )

                DiscoverPage.BOX_OFFICE_TOTAL -> BoxOfficeTotalScreen(
                    onBack = onBack,
                    onSearchMovie = onSearchMovie,
                )

                DiscoverPage.BOX_OFFICE_REALTIME -> BoxOfficeRealtimeScreen(onBack = onBack)
                DiscoverPage.MOVIE_RATING -> MovieRatingScreen(
                    onBack = onBack,
                    onSearchMovie = onSearchMovie,
                )

                DiscoverPage.MOVIE_WORLD -> MovieWorldScreen(
                    onBack = onBack,
                    onSearchMovie = onSearchMovie,
                )

                DiscoverPage.MEDIA_PLAYER -> MediaLinkTestScreen(onBack = onBack)
                DiscoverPage.MEDIA_ANALYZE -> MediaAnalyzeScreen(onBack = onBack)
                DiscoverPage.SCROLLING_TEXT -> ScrollingTextScreen(onBack = onBack)
                DiscoverPage.INVITE_MASTER -> InviteMasterScreen(
                    onBack = onBack,
                    logs = logs,
                )

                null -> {}
            }
        }

        composable(
            route = AppRoutes.SEARCH,
            arguments = listOf(navArgument("query") {
                type = NavType.StringType; defaultValue = ""
            }),
        ) { entry ->
            val query = entry.arguments?.getString("query") ?: ""
            MovieSearchScreen(
                initialQuery = query,
                onBack = { navController.popBackStack() },
                onPlayVideo = { url ->
                    val videoId = extractVideoId(url)
                    val host = extractHost(url)
                    val historyItem = SharedData.getWatchHistory(context)
                        .find { it.id == videoId && it.host == host }
                    val episodeUrl = historyItem?.let {
                        if (it.url.startsWith("http")) it.url else "${it.host}${it.url}"
                    }
                    navController.navigate(
                        AppRoutes.play(url, historyItem?.progress ?: 0L, episodeUrl)
                    )
                },
            )
        }

        composable(
            route = AppRoutes.PLAY,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("position") { type = NavType.LongType; defaultValue = 0L },
                navArgument("episode") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val url = entry.arguments?.getString("url") ?: ""
            val position = entry.arguments?.getLong("position") ?: 0L
            val episode = entry.arguments?.getString("episode")?.takeIf { it.isNotEmpty() }
            MovieDetailScreen(
                videoUrl = url,
                initialPosition = position,
                initialEpisodeUrl = episode,
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.WATCH_HISTORY) {
            WatchHistoryScreen(
                onBack = { navController.popBackStack() },
                onPlayMovie = { url, pos, host ->
                    val absUrl = if (url.startsWith("http")) url else "$host$url"
                    navController.navigate(AppRoutes.play(absUrl, pos, absUrl))
                },
            )
        }

        composable(AppRoutes.MY_THEME) {
            MyThemeScreen(
                onBack = { navController.popBackStack() },
                onThemeChanged = onThemeChanged,
            )
        }

        composable(AppRoutes.ABOUT_ME) {
            AboutMeScreen(
                onBack = { navController.popBackStack() },
                onNavigateToContributor = { navController.navigate(AppRoutes.CONTRIBUTOR) },
                onNavigateToLicense = { navController.navigate(AppRoutes.LICENSE) },
            )
        }

        composable(AppRoutes.SPONSOR) {
            SponsorScreen(onBack = { navController.popBackStack() })
        }

        composable(AppRoutes.CONTRIBUTOR) {
            ContributorScreen(onBack = { navController.popBackStack() })
        }

        composable(AppRoutes.LICENSE) {
            LicenseScreen(onBack = { navController.popBackStack() })
        }
    }

    DisclaimerDialog(
        isOpen = showTipsDialog,
        onDismiss = {
            showTipsDialog = false
            SharedData.markTipsDialogShown(context)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    onThemeChanged: (mode: Int, dynamic: Boolean, colorTheme: Int, dynamicStyle: String) -> Unit,
    onSearchMovie: (String) -> Unit,
    onNavigateToDetail: (DiscoverPage) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToAboutMe: () -> Unit,
    onNavigateToSponsor: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentPageIndex by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = currentPageIndex,
        pageCount = { AppDestinations.entries.size },
    )
    val currentDestination = AppDestinations.entries[currentPageIndex]

    LaunchedEffect(pagerState.currentPage) {
        currentPageIndex = pagerState.currentPage
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
                                onSearchMovie("")
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
                        onNavigateToDetail = onNavigateToDetail,
                    )

                    AppDestinations.MINE -> MineScreen(
                        onNavigateToHistory = onNavigateToHistory,
                        onNavigateToTheme = onNavigateToTheme,
                        onNavigateToAboutMe = onNavigateToAboutMe,
                        onNavigateToSponsor = onNavigateToSponsor,
                    )
                }
            }
        }
    }
}
