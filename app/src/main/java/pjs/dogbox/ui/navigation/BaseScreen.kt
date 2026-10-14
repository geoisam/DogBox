package pjs.dogbox.ui.navigation

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
import pjs.dogbox.BuildConfig
import pjs.dogbox.R
import pjs.dogbox.ui.screen.DiscoverPage
import pjs.dogbox.ui.screen.DiscoverScreen
import pjs.dogbox.ui.screen.HomeScreen
import pjs.dogbox.ui.screen.HomeScreenState
import pjs.dogbox.ui.screen.ImageViewerScreen
import pjs.dogbox.ui.screen.MineScreen
import pjs.dogbox.ui.screen.about.ContributorScreen
import pjs.dogbox.ui.screen.about.ChangeLogScreen
import pjs.dogbox.ui.screen.about.LicenseScreen
import pjs.dogbox.ui.screen.discover.AnimeRankScreen
import pjs.dogbox.ui.screen.discover.AnimeRankScreenState
import pjs.dogbox.ui.screen.discover.BoxOfficeRealtimeScreen
import pjs.dogbox.ui.screen.discover.BoxOfficeTotalScreen
import pjs.dogbox.ui.screen.discover.BoxOfficeTotalScreenState
import pjs.dogbox.ui.screen.discover.MovieWorldScreenState
import pjs.dogbox.ui.screen.discover.ProfileAvatarScreen
import pjs.dogbox.ui.screen.discover.ProfileAvatarScreenState
import pjs.dogbox.ui.screen.discover.HotNewsRankScreen
import pjs.dogbox.ui.screen.discover.InviteMasterScreen
import pjs.dogbox.ui.screen.discover.MediaAnalyzeImageGallery
import pjs.dogbox.ui.screen.discover.MediaAnalyzeResultScreen
import pjs.dogbox.ui.screen.discover.MediaAnalyzeScreen
import pjs.dogbox.ui.screen.discover.MediaAnalyzePreview
import pjs.dogbox.ui.screen.discover.MediaLinkTestScreen
import pjs.dogbox.ui.screen.discover.MovieRatingScreen
import pjs.dogbox.ui.screen.discover.MovieRatingScreenState
import pjs.dogbox.ui.screen.discover.MovieWorldScreen
import pjs.dogbox.ui.screen.discover.ScrollingTextScreen
import pjs.dogbox.ui.screen.discover.TvLiveScreen
import pjs.dogbox.ui.screen.mine.AboutMeScreen
import pjs.dogbox.ui.screen.mine.CacheManageScreen
import pjs.dogbox.ui.screen.mine.LocalVideoPreview
import pjs.dogbox.ui.screen.mine.LocalVideosScreen
import pjs.dogbox.ui.screen.mine.MyThemeScreen
import pjs.dogbox.ui.screen.mine.SettingsScreen
import pjs.dogbox.ui.screen.mine.SponsorScreen
import pjs.dogbox.ui.screen.mine.WatchHistoryScreen
import pjs.dogbox.ui.screen.movie.MovieDetailScreen
import pjs.dogbox.ui.screen.movie.MovieDetailScreenState
import pjs.dogbox.ui.screen.movie.MovieSearchScreen
import pjs.dogbox.ui.screen.movie.MovieSearchScreenState
import androidx.core.net.toUri
import pjs.dogbox.ui.screen.movie.extractHost
import pjs.dogbox.ui.screen.movie.extractPath
import pjs.dogbox.ui.screen.movie.extractPathWithQuery
import pjs.dogbox.data.ApiSites
import pjs.dogbox.data.AppData
import pjs.dogbox.ui.components.DisclaimerDialog
import pjs.dogbox.data.SharedData
import pjs.dogbox.ui.components.UpdateBottomSheet
import pjs.dogbox.data.fetchRemoteConfig
import pjs.dogbox.data.parseUpdateInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseScreen(
    onThemeChanged: (mode: Int, dynamic: Boolean, colorTheme: Int, dynamicStyle: String) -> Unit = { _, _, _, _ -> },
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val logs = remember { mutableStateListOf<String>() }
    val registry = remember { ScreenStateRegistry() }

    var showTipsDialog by remember { mutableStateOf(SharedData.isFirstLaunch(context)) }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateVer by remember { mutableStateOf("1.0.0") }
    var updateNote by remember { mutableStateOf("修复了一些已知问题") }
    var updateDown by remember { mutableStateOf(AppData.GITHUB_PAGE) }

    LaunchedEffect(Unit) {
        if (!SharedData.isAutoCheckUpdate(context)) return@LaunchedEffect
        if (SharedData.isAutoCheckDoneThisLaunch()) return@LaunchedEffect
        val configJson = fetchRemoteConfig()
        val updateInfo = parseUpdateInfo(configJson)
        if (updateInfo != null) {
            if (updateInfo.code > BuildConfig.VERSION_CODE) {
                SharedData.setCachedCheckFailed(false)
                SharedData.setCachedUpToDate(false)
                SharedData.setCachedUpdateInfo(
                    SharedData.CachedUpdateInfo(
                        versionCode = updateInfo.code,
                        versionName = updateInfo.ver,
                        note = updateInfo.note,
                        downloadUrl = updateInfo.down,
                    )
                )
                updateVer = updateInfo.ver
                updateNote = updateInfo.note
                updateDown = updateInfo.down
                showUpdateDialog = true
            } else {
                SharedData.setCachedUpdateInfo(null)
                SharedData.setCachedCheckFailed(false)
                SharedData.setCachedUpToDate(true)
            }
        } else {
            SharedData.setCachedUpdateInfo(null)
            SharedData.setCachedUpToDate(false)
            SharedData.setCachedCheckFailed(true)
        }
        SharedData.markAutoCheckDone()
    }

    NavHost(
        navController = navController,
        startDestination = AppRoutes.MAIN,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(AppRoutes.MAIN) { entry ->
            val homeState = rememberEntryState(registry, entry) { HomeScreenState() }
            MainScaffold(
                homeState = homeState,
                onThemeChanged = onThemeChanged,
                onSearchMovie = { navController.navigate(AppRoutes.search(it)) },
                onNavigateToDetail = { navController.navigate(AppRoutes.discoverDetail(it.name)) },
                onNavigateToHistory = { navController.navigate(AppRoutes.WATCH_HISTORY) },
                onNavigateToLocalVideos = { navController.navigate(AppRoutes.LOCAL_VIDEOS) },
                onNavigateToAboutMe = { navController.navigate(AppRoutes.ABOUT_ME) },
                onNavigateToSponsor = { navController.navigate(AppRoutes.SPONSOR) },
                onNavigateToSettings = { navController.navigate(AppRoutes.SETTINGS) },
                onViewImage = { url, referer, title ->
                    navController.navigate(AppRoutes.imageViewer(url, referer, title))
                },
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
            val onViewImage: (String, String, String) -> Unit = { url, referer, title ->
                navController.navigate(AppRoutes.imageViewer(url, referer, title))
            }
            when (page) {
                DiscoverPage.TV_LIVE -> TvLiveScreen(onBack = onBack)
                DiscoverPage.PROFILE_AVATAR -> {
                    val avatarState = rememberEntryState(registry, entry) { ProfileAvatarScreenState() }
                    ProfileAvatarScreen(
                        onBack = onBack,
                        onViewImage = onViewImage,
                        state = avatarState,
                    )
                }
                DiscoverPage.HOT_NEWS_RANK -> HotNewsRankScreen(onBack = onBack)
                DiscoverPage.ANIME_RANK -> {
                    val animeState = rememberEntryState(registry, entry) { AnimeRankScreenState() }
                    AnimeRankScreen(
                        state = animeState,
                        onBack = onBack,
                        onSearchMovie = onSearchMovie,
                        onViewImage = onViewImage,
                    )
                }

                DiscoverPage.BOX_OFFICE_TOTAL -> {
                    val totalState = rememberEntryState(registry, entry) { BoxOfficeTotalScreenState() }
                    BoxOfficeTotalScreen(
                        onBack = onBack,
                        onSearchMovie = onSearchMovie,
                        state = totalState,
                    )
                }

                DiscoverPage.BOX_OFFICE_REALTIME -> BoxOfficeRealtimeScreen(onBack = onBack)
                DiscoverPage.MOVIE_RATING -> {
                    val ratingState = rememberEntryState(registry, entry) { MovieRatingScreenState() }
                    MovieRatingScreen(
                        state = ratingState,
                        onBack = onBack,
                        onSearchMovie = onSearchMovie,
                        onViewImage = onViewImage,
                    )
                }

                DiscoverPage.MOVIE_WORLD -> {
                    val worldState = rememberEntryState(registry, entry) { MovieWorldScreenState() }
                    MovieWorldScreen(
                        onBack = onBack,
                        onSearchMovie = onSearchMovie,
                        state = worldState,
                    )
                }

                DiscoverPage.MEDIA_PLAYER -> MediaLinkTestScreen(onBack = onBack)
                DiscoverPage.MEDIA_ANALYZE -> MediaAnalyzeScreen(
                    onBack = onBack,
                    onOpenResult = { type, urls, referer, title ->
                        navController.navigate(
                            AppRoutes.analyzeResult(type, urls, referer, title)
                        )
                    },
                )
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
            val searchState = rememberEntryState(registry, entry) {
                MovieSearchScreenState(query, ApiSites.ensureLoaded(context))
            }
            val onViewImage: (String, String, String) -> Unit = { url, referer, title ->
                navController.navigate(AppRoutes.imageViewer(url, referer, title))
            }
            MovieSearchScreen(
                state = searchState,
                onBack = { navController.popBackStack() },
                onPlayVideo = { url ->
                    ApiSites.ensureLoaded(context)
                    val host = extractHost(url)
                    val key = AppData.sourceKey(host)
                    // API 详情链接 path 相同，仅 query(ids=) 能区分影片，需 path+query 定位历史
                    val detailPath = if (ApiSites.isApiDetailUrl(url)) extractPathWithQuery(url) else extractPath(url)
                    val historyItem = SharedData.getWatchHistory(context)
                        .find { it.id == detailPath && it.key == key }
                    val episodeUrl = historyItem?.let {
                        // API 选集存的是完整直链，原样使用；旧数据存 path，需拼 host
                        if (it.url.startsWith("http")) it.url
                        else "${AppData.hostForKey(it.key)}${it.url}"
                    }
                    navController.navigate(
                        AppRoutes.play(url, historyItem?.progress ?: 0L, episodeUrl)
                    )
                },
                onViewImage = onViewImage,
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
            val detailState = rememberEntryState(registry, entry) {
                MovieDetailScreenState(context.applicationContext, url, position, episode)
            }
            MovieDetailScreen(
                state = detailState,
                videoUrl = url,
                onBack = { navController.popBackStack() },
                onViewImage = { imgUrl, referer, title ->
                    navController.navigate(AppRoutes.imageViewer(imgUrl, referer, title))
                },
            )
        }

        composable(AppRoutes.WATCH_HISTORY) {
            WatchHistoryScreen(
                onBack = { navController.popBackStack() },
                onPlayMovie = { item ->
                    ApiSites.ensureLoaded(context)
                    val origin = AppData.hostForKey(item.key)
                    // 兼容旧数据（存 path）与 API 数据（url 可能是完整直链）
                    val detailAbs = if (item.id.startsWith("http")) item.id else "$origin${item.id}"
                    val episodeAbs = if (item.url.startsWith("http")) item.url else "$origin${item.url}"
                    navController.navigate(AppRoutes.play(detailAbs, item.progress, episodeAbs))
                },
            )
        }

        composable(AppRoutes.LOCAL_VIDEOS) {
            LocalVideosScreen(
                onBack = { navController.popBackStack() },
                onPlayVideo = { uri, title ->
                    navController.navigate(AppRoutes.localPlay(uri, title))
                },
            )
        }

        composable(
            route = AppRoutes.LOCAL_PLAY,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val uri = entry.arguments?.getString("url") ?: ""
            val title = entry.arguments?.getString("title") ?: ""
            LocalVideoPreview(
                videoUri = uri,
                title = title,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppRoutes.ANALYZE_PLAY,
            arguments = listOf(
                navArgument("urls") { type = NavType.StringType; defaultValue = "" },
                navArgument("index") { type = NavType.IntType; defaultValue = 0 },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("referer") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val urls = (entry.arguments?.getString("urls") ?: "")
                .split("\n")
                .filter { it.isNotBlank() }
            val index = (entry.arguments?.getInt("index") ?: 0)
                .coerceIn(0, (urls.size - 1).coerceAtLeast(0))
            val title = entry.arguments?.getString("title") ?: ""
            val referer = entry.arguments?.getString("referer") ?: ""
            if (urls.isNotEmpty()) {
                MediaAnalyzePreview(
                    videoUrls = urls,
                    initialIndex = index,
                    title = title,
                    referer = referer,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = AppRoutes.ANALYZE_IMAGE_GALLERY,
            arguments = listOf(
                navArgument("urls") { type = NavType.StringType; defaultValue = "" },
                navArgument("index") { type = NavType.IntType; defaultValue = 0 },
                navArgument("referer") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val urls = (entry.arguments?.getString("urls") ?: "")
                .split("\n")
                .filter { it.isNotBlank() }
            val index = (entry.arguments?.getInt("index") ?: 0)
                .coerceIn(0, (urls.size - 1).coerceAtLeast(0))
            val referer = entry.arguments?.getString("referer") ?: ""
            val title = entry.arguments?.getString("title") ?: ""
            if (urls.isNotEmpty()) {
                MediaAnalyzeImageGallery(
                    imageUrls = urls,
                    initialIndex = index,
                    referer = referer,
                    title = title,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = AppRoutes.ANALYZE_RESULT,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType; defaultValue = "" },
                navArgument("urls") { type = NavType.StringType; defaultValue = "" },
                navArgument("referer") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val type = entry.arguments?.getString("type") ?: ""
            val urls = (entry.arguments?.getString("urls") ?: "")
                .split("\n")
                .filter { it.isNotBlank() }
            val referer = entry.arguments?.getString("referer") ?: ""
            val title = entry.arguments?.getString("title") ?: ""
            if (urls.isNotEmpty()) {
                MediaAnalyzeResultScreen(
                    type = type,
                    urls = urls,
                    referer = referer,
                    resultTitle = title,
                    onBack = { navController.popBackStack() },
                    onOpen = { index ->
                        if (type == "image") {
                            navController.navigate(
                                AppRoutes.analyzeImageGallery(urls, index, referer, title)
                            )
                        } else {
                            navController.navigate(
                                AppRoutes.analyzePlay(urls, index, title, referer)
                            )
                        }
                    },
                )
            }
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
                onNavigateToChangeLog = { navController.navigate(AppRoutes.CHANGE_LOG) },
            )
        }

        composable(AppRoutes.SPONSOR) {
            SponsorScreen(
                onBack = { navController.popBackStack() },
                onViewImage = { url, referer, title ->
                    navController.navigate(AppRoutes.imageViewer(url, referer, title))
                },
            )
        }

        composable(AppRoutes.CONTRIBUTOR) {
            ContributorScreen(onBack = { navController.popBackStack() })
        }

        composable(AppRoutes.LICENSE) {
            LicenseScreen(onBack = { navController.popBackStack() })
        }

        composable(AppRoutes.CHANGE_LOG) {
            ChangeLogScreen(onBack = { navController.popBackStack() })
        }

        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToTheme = { navController.navigate(AppRoutes.MY_THEME) },
                onNavigateToCacheManage = { navController.navigate(AppRoutes.CACHE_MANAGE) },
            )
        }

        composable(AppRoutes.CACHE_MANAGE) {
            CacheManageScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppRoutes.IMAGE_VIEWER,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("referer") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val url = entry.arguments?.getString("url") ?: ""
            val referer = entry.arguments?.getString("referer") ?: ""
            val title = entry.arguments?.getString("title") ?: ""
            ImageViewerScreen(
                url = url,
                referer = referer,
                title = title,
                onBack = { navController.popBackStack() },
            )
        }
    }

    DisclaimerDialog(
        isOpen = showTipsDialog,
        onDismiss = {
            showTipsDialog = false
            SharedData.markTipsDialogShown(context)
        },
    )

    UpdateBottomSheet(
        isOpen = showUpdateDialog,
        onDismiss = { showUpdateDialog = false },
        latestVersion = updateVer,
        updateNote = updateNote,
        downloadUrl = updateDown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    homeState: HomeScreenState,
    onThemeChanged: (mode: Int, dynamic: Boolean, colorTheme: Int, dynamicStyle: String) -> Unit,
    onSearchMovie: (String) -> Unit,
    onNavigateToDetail: (DiscoverPage) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToLocalVideos: () -> Unit,
    onNavigateToAboutMe: () -> Unit,
    onNavigateToSponsor: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onViewImage: (String, String, String) -> Unit = { _, _, _ -> },
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
                                onNavigateToSettings()
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
                        state = homeState,
                        onSearchMovie = onSearchMovie,
                        onViewImage = onViewImage,
                    )

                    AppDestinations.DISCOVER -> DiscoverScreen(
                        onNavigateToDetail = onNavigateToDetail,
                    )

                    AppDestinations.MINE -> MineScreen(
                        onNavigateToHistory = onNavigateToHistory,
                        onNavigateToLocalVideos = onNavigateToLocalVideos,
                        onNavigateToAboutMe = onNavigateToAboutMe,
                        onNavigateToSponsor = onNavigateToSponsor,
                    )
                }
            }
        }
    }
}
