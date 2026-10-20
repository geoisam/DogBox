package pjs.dogbox.ui.screen.movie

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import pjs.dogbox.data.ApiSite
import pjs.dogbox.data.ApiSites
import pjs.dogbox.data.AppData
import pjs.dogbox.ui.components.AppBottomSheet
import pjs.dogbox.ui.components.CoverImage
import pjs.dogbox.ui.components.PagerTabFollowEffect
import pjs.dogbox.ui.theme.MiuixIcons
import pjs.dogbox.utils.HttpUtil
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.IOException
import java.net.URLEncoder
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

internal data class SearchSource(
    val key: String,
    val label: String,
    val host: String,
    val searchUrl: String,
    /** 非空表示 CMS JSON 接口片源（assets/json/sites.json），值为接口基址 api */
    val api: String? = null,
) {
    val isApi: Boolean get() = api != null

    companion object {
        fun defaults(): List<SearchSource> = listOf(
            SearchSource(AppData.YXXQ_KEY, "映像星球", AppData.YXXQ_HOST, AppData.YXXQ_SEARCH),
            SearchSource(AppData.FDZYS_KEY, "饭搭子", AppData.FDZYS_HOST, ""),
        )

        fun fromApiSite(site: ApiSite): SearchSource = SearchSource(
            key = site.key,
            label = site.name,
            host = ApiSites.originOf(site.api),
            searchUrl = "",
            api = site.api,
        )
    }
}

internal data class SearchItem(
    val link: String,
    val cover: String,
    val title: String,
    val note: String,
    val year: String,
    val subtitle: String,
    val host: String,
)

private data class PageInfo(
    val hasNext: Boolean,
    val nextUrl: String?,
)

private fun checkPageInfo(doc: Document, currentPage: Int): PageInfo {
    // 已改无限页逻辑：不再解析分页元素，由调用方按 items 是否为空判断 hasNext
    return PageInfo(hasNext = true, nextUrl = null)
}

private fun parseSearchResults(
    html: String,
    host: String,
    currentPage: Int = 1,
): Pair<List<SearchItem>, PageInfo> {
    val doc = Ksoup.parse(html)
    val cardElements = doc.select("div.module-card-item").filterNot { it.hasClass("mac-ad-card") }
    val items = cardElements.mapNotNull { card ->
        // 映像星球统一取详情页：.module-card-item 第一个 a 的 href
        val linkEl = card.select("a").firstOrNull()
            ?: return@mapNotNull null
        val imgEl = card.select("img[data-original]").firstOrNull()
            ?: card.select("img[data-src]").firstOrNull()
            ?: card.select("img").firstOrNull()
            ?: return@mapNotNull null
        val titleEl = card.select("div.module-card-item-title").firstOrNull()
            ?: card.select("a.module-card-item-poster").firstOrNull()
            ?: return@mapNotNull null

        val linkText = linkEl.attr("href").trim()
        if (linkText.isBlank()) return@mapNotNull null
        // 过滤广告跳转
        if (linkText.contains("/go/ad/")) return@mapNotNull null
        val link = if (linkText.startsWith("http")) linkText else "$host$linkText"
        val coverText = imgEl.attr("data-original").ifBlank { imgEl.attr("data-src") }
            .ifBlank { imgEl.attr("src") }.trim()
        if (coverText.isBlank() || coverText.startsWith("data:") || coverText.contains("loading.png") || coverText.contains(
                "load.gif"
            )
        ) return@mapNotNull null
        val cover = when {
            coverText.startsWith("http") -> coverText
            coverText.startsWith("//") -> "https:$coverText"
            else -> "$host$coverText"
        }
        val title = titleEl.text().trim().ifBlank { linkEl.attr("title").trim() }
        if (title.isBlank()) return@mapNotNull null
        val note = card.select("div.module-item-note").firstOrNull()?.text()?.trim() ?: ""

        val infoText =
            card.select("div.module-info-item-content:has(span.slash)").firstOrNull()?.text()
                ?.trim()?.replace(" ", "") ?: ""

        val year = infoText.split("/").firstOrNull()?.trim() ?: ""
        val subtitle = infoText.split("/").drop(1).joinToString("/").trim().replace(",", "/")

        SearchItem(
            link = link,
            cover = cover,
            title = title,
            note = note,
            year = year,
            subtitle = subtitle,
            host = host,
        )
    }
    // 无限页：只要本页有数据就认为还有下一页，空页则截止
    val pageInfo = PageInfo(hasNext = items.isNotEmpty(), nextUrl = null)
    return items to pageInfo
}

private val vodListJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonObject.optStr(name: String): String =
    this[name]?.jsonPrimitive?.contentOrNull?.trim() ?: ""

/**
 * CMS JSON 接口搜索解析（sites.json 片源）。
 * vod_id→详情ID，vod_name→标题，vod_class→封面底部阴影，
 * vod_year→年份，vod_remarks→封面右上角，vod_pic→封面。
 */
private fun parseApiSearchResults(
    json: String,
    source: SearchSource,
    currentPage: Int = 1,
): Pair<List<SearchItem>, PageInfo> {
    val api =
        source.api ?: return emptyList<SearchItem>() to PageInfo(hasNext = false, nextUrl = null)
    val origin = ApiSites.originOf(api)
    val root = vodListJson.parseToJsonElement(json).jsonObject
    val arr = root["list"] as? JsonArray ?: JsonArray(emptyList())
    val items = arr.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val vodId = o.optStr("vod_id")
        if (vodId.isBlank()) return@mapNotNull null
        val title = o.optStr("vod_name")
        if (title.isBlank()) return@mapNotNull null
        val coverRaw = o.optStr("vod_pic")
        val cover = when {
            coverRaw.startsWith("http") -> coverRaw
            coverRaw.startsWith("//") -> "https:$coverRaw"
            coverRaw.startsWith("/") -> origin + coverRaw
            coverRaw.isNotBlank() -> "$origin/$coverRaw"
            else -> ""
        }
        SearchItem(
            link = ApiSites.detailUrl(api, vodId),
            cover = cover,
            title = title,
            note = o.optStr("vod_remarks"),
            year = o.optStr("vod_year"),
            subtitle = o.optStr("vod_class"),
            host = origin,
        )
    }
    val pageNo = root["page"]?.jsonPrimitive?.intOrNull ?: currentPage
    val pageCount = root["pagecount"]?.jsonPrimitive?.intOrNull ?: 0
    val hasNext = if (pageCount > 0) pageNo < pageCount else items.isNotEmpty()
    return items to PageInfo(hasNext = hasNext, nextUrl = null)
}

private suspend fun fetchPage(
    source: SearchSource,
    keyword: String,
    page: Int,
): Result<Pair<List<SearchItem>, PageInfo>> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")
    val url = if (source.isApi) {
        ApiSites.searchUrl(source.api!!, keyword, page)
    } else when (source.key) {
        AppData.FDZYS_KEY -> {
            val base = "${AppData.FDZYS_HOST}/yu-${encoded}-xianguan-de-yingpian-shippin-zhibo"
            if (page <= 1) base else "$base?page=$page"
        }

        else -> {
            if (page <= 1) "${source.searchUrl}$encoded"
            else "${AppData.YXXQ_HOST}/search/${encoded}----------${page}---.html"
        }
    }


    var lastError: Throwable? = null
    repeat(2) { attempt ->
        try {
            val html = HttpUtil.get(
                url, mapOf(
                    "User-Agent" to AppData.UA_CHROME_PC,
                    "Referer" to "${source.host}/",
                )
            ).getOrThrow()
            val result = if (source.isApi) {
                parseApiSearchResults(html, source, page)
            } else when (source.key) {
                AppData.FDZYS_KEY -> parseFdzysSearchResults(html, source.host, page)
                else -> parseSearchResults(html, source.host, page)
            }
            return@withContext Result.success(result)
        } catch (e: Throwable) {
            lastError = e
        }
        if (attempt == 0) {
            try {
                delay(666)
            } catch (_: Throwable) {
            }
        }
    }
    Result.failure(lastError ?: IOException("未知网络错误"))
}

private fun parseFdzysSearchResults(
    html: String,
    host: String,
    currentPage: Int = 1,
): Pair<List<SearchItem>, PageInfo> {
    val doc = Ksoup.parse(html)
    val cardElements = doc.select("div.myui-vodbox-content")
    val items = cardElements.mapNotNull { card ->
        val aEl = card.select("a").firstOrNull() ?: return@mapNotNull null
        val linkText = aEl.attr("href").trim()
        if (linkText.isBlank()) return@mapNotNull null
        val rawLink = if (linkText.startsWith("http")) linkText else "$host$linkText"

        val link = rawLink.trim().removeSuffix("/")

        val imgEl = card.select("img[data-src]").firstOrNull()
            ?: card.select("img[data-original]").firstOrNull()
            ?: card.select("img").firstOrNull()
            ?: return@mapNotNull null
        var coverText = imgEl.attr("data-src").ifBlank { imgEl.attr("data-original") }
            .ifBlank { imgEl.attr("src") }
            .trim()
        if (coverText.isBlank() || coverText.startsWith("data:")) return@mapNotNull null
        val cover = when {
            coverText.startsWith("http") -> coverText
            coverText.startsWith("//") -> "https:$coverText"
            else -> "$host$coverText"
        }
        val title = card.select(".card-info .title").firstOrNull()?.text()?.trim()
            ?: card.select(".vod-detail .title").firstOrNull()?.text()?.trim()
            ?: aEl.attr("title").trim().ifBlank { null }
            ?: imgEl.attr("alt").trim().ifBlank { null }
            ?: return@mapNotNull null
        if (title.isBlank()) return@mapNotNull null
        val note = card.select(".vod-detail .info-title-box .score").firstOrNull()?.text()?.trim()
            ?: card.select(".score").firstOrNull()?.text()?.trim() ?: ""
        val subtitle = card.select(".tag-box .tag").firstOrNull()?.text()?.trim() ?: ""
        val year = card.select(".vod-detail .info-bottom .right").firstOrNull()?.text()?.trim()
            ?: card.select(".info-bottom .right").firstOrNull()?.text()?.trim() ?: ""
        SearchItem(
            link = link,
            cover = cover,
            title = title,
            note = note,
            year = year,
            subtitle = subtitle,
            host = host,
        )
    }
    // 无限页：只要本页有数据就认为还有下一页
    return items to PageInfo(hasNext = items.isNotEmpty(), nextUrl = null)
}

internal class SourceState {
    var itemList by mutableStateOf<List<SearchItem>>(emptyList())
    var isLoading by mutableStateOf(false)
    var currentPage by mutableIntStateOf(1)
    var hasMore by mutableStateOf(true)
    var hasSearched by mutableStateOf(false)
    var loadError by mutableStateOf(false)
    var loadMoreRetryKey by mutableIntStateOf(0)
    val gridState = LazyGridState()

    fun reset() {
        itemList = emptyList()
        currentPage = 1
        hasMore = true
        isLoading = false
        hasSearched = false
        loadError = false
        loadMoreRetryKey = 0
    }
}

internal class MovieSearchScreenState(
    initialQuery: String,
    apiSites: List<ApiSite> = emptyList(),
) {
    val searchText = mutableStateOf(initialQuery)
    val activeQuery = mutableStateOf(initialQuery)
    val sources: List<SearchSource> =
        SearchSource.defaults() + apiSites.map { SearchSource.fromApiSite(it) }
    val sourceStates = sources.associateWith { SourceState() }
    val orderedSources = mutableStateOf<List<SearchSource>>(sources)
    val retryKey = mutableIntStateOf(0)
    val lastSearchKey = mutableStateOf("")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MovieSearchScreen(
    state: MovieSearchScreenState,
    onBack: () -> Unit = {},
    onPlayVideo: (String) -> Unit = {},
    onViewImage: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    var searchText by state.searchText
    var activeQuery by state.activeQuery

    val sources = state.sources
    val sourceStates = state.sourceStates
    var orderedSources by state.orderedSources
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { orderedSources.size })
    val tabScrollState = rememberLazyListState()
    var showSourceSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var retryKey by state.retryKey
    var lastSearchKey by state.lastSearchKey

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(activeQuery, retryKey) {
        val searchKey = "$activeQuery|$retryKey"
        if (lastSearchKey == searchKey) return@LaunchedEffect
        lastSearchKey = searchKey
        if (activeQuery.isNotEmpty()) {
            sourceStates.values.forEach { it.reset() }
            orderedSources = sources
            val completed = mutableListOf<SearchSource>()
            coroutineScope {
                sources.forEach { source ->
                    launch {
                        val state = sourceStates.getValue(source)
                        state.isLoading = true
                        state.loadError = false
                        try {
                            fetchPage(source, activeQuery, 1).fold(
                                onSuccess = { (items, pageInfo) ->
                                    state.itemList = items.distinctBy { it.link }
                                    state.hasMore = pageInfo.hasNext
                                },
                                onFailure = {
                                    state.itemList = emptyList()
                                    state.hasMore = false
                                    state.loadError = true
                                },
                            )
                        } catch (e: Throwable) {
                            state.itemList = emptyList()
                            state.hasMore = false
                            state.loadError = true
                        } finally {
                            state.isLoading = false
                            state.hasSearched = true
                        }
                        completed += source
                        orderedSources = completed + sources.filterNot { it in completed }
                    }
                }
            }
        }
    }

    fun triggerSearch(query: String) {
        if (query.isNotBlank()) {
            focusManager.clearFocus()
            activeQuery = query
        }
    }

    fun retrySearch() {
        if (activeQuery.isNotBlank()) {
            retryKey++
        }
    }

    fun retrySource(source: SearchSource) {
        if (activeQuery.isBlank()) return
        val s = sourceStates.getValue(source)
        if (s.isLoading) return
        scope.launch {
            s.isLoading = true
            s.loadError = false
            // 仅重置当前源的分页，不影响其它源
            s.currentPage = 1
            s.hasMore = true
            try {
                fetchPage(source, activeQuery, 1).fold(
                    onSuccess = { (items, pageInfo) ->
                        s.itemList = items.distinctBy { it.link }
                        s.hasMore = pageInfo.hasNext
                        s.loadError = false
                    },
                    onFailure = {
                        s.itemList = emptyList()
                        s.hasMore = false
                        s.loadError = true
                    },
                )
            } catch (e: Throwable) {
                s.itemList = emptyList()
                s.hasMore = false
                s.loadError = true
            } finally {
                s.isLoading = false
                s.hasSearched = true
            }
        }
    }

    fun retryLoadMore(source: SearchSource) {
        val state = sourceStates.getValue(source)
        if (activeQuery.isNotEmpty() && state.itemList.isNotEmpty() && !state.isLoading) {
            state.loadError = false
            state.loadMoreRetryKey++
        }
    }

    fun loadMore(source: SearchSource) {
        val state = sourceStates.getValue(source)
        if (state.hasMore && !state.isLoading) {
            state.currentPage++
        }
    }

    MiuixScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "影片搜索",
                navigationIcon = {
                    MiuixIconButton(onClick = onBack) {
                        MiuixIcon(
                            painter = painterResource(R.drawable.arrow_back_outlined),
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            MiuixTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = "输入关键词搜索",
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { triggerSearch(searchText) }),
                leadingIcon = {
                    MiuixIcon(
                        imageVector = MiuixIcons.Regular.Search,
                        contentDescription = "搜索",
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier
                            .padding(start = 16.dp, end = 8.dp)
                            .size(20.dp),
                    )
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        MiuixIconButton(onClick = { searchText = "" }) {
                            MiuixIcon(
                                imageVector = MiuixIcons.Regular.SearchCleanup,
                                contentDescription = "清空",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
            )
            PagerTabFollowEffect(pagerState = pagerState, tabScrollState = tabScrollState)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    state = tabScrollState,
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(orderedSources) { index, source ->
                        val selected = pagerState.currentPage == index
                        val chipColor by animateColorAsState(
                            targetValue = if (selected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.surfaceContainerHigh,
                            animationSpec = tween(200),
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (selected) MiuixTheme.colorScheme.onPrimary
                            else MiuixTheme.colorScheme.onSurfaceSecondary,
                            animationSpec = tween(200),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(chipColor)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    },
                                )
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            MiuixText(
                                text = source.label,
                                style = MiuixTheme.textStyles.footnote1,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                maxLines = 1,
                            )
                        }
                    }
                }

                MiuixIconButton(
                    onClick = { showSourceSheet = true },
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(32.dp),
                ) {
                    MiuixIcon(
                        painter = painterResource(R.drawable.keyboard_double_arrow_right_outlined),
                        contentDescription = "展开",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 0,
                ) { page ->
                    val source = orderedSources[page]
                    SourceTabContent(
                        source = source,
                        state = sourceStates.getValue(source),
                        activeQuery = activeQuery,
                        onRetrySearch = { retrySource(source) },
                        onRetryLoadMore = { retryLoadMore(source) },
                        onLoadMore = { loadMore(source) },
                        onPlayVideo = onPlayVideo,
                        onViewImage = onViewImage,
                    )
                }
            }
        }
    }

    AppBottomSheet(
        show = showSourceSheet,
        onDismissRequest = { showSourceSheet = false },
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            orderedSources.forEach { source ->
                val pageIndex = orderedSources.indexOf(source)
                val isSelected = pageIndex == pagerState.currentPage
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.surfaceContainerHigh,
                )
                val txtColor by animateColorAsState(
                    targetValue = if (isSelected) MiuixTheme.colorScheme.onPrimary
                    else MiuixTheme.colorScheme.onSurface,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pageIndex)
                                }
                                showSourceSheet = false
                            },
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    MiuixText(
                        text = source.label,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = txtColor,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SourceTabContent(
    source: SearchSource,
    state: SourceState,
    activeQuery: String,
    onRetrySearch: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onLoadMore: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onViewImage: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val gridState = state.gridState

    LaunchedEffect(source, state.currentPage, activeQuery, state.loadMoreRetryKey) {
        if (activeQuery.isEmpty() || state.currentPage <= 1) return@LaunchedEffect
        state.isLoading = true
        try {
            fetchPage(source, activeQuery, state.currentPage).fold(
                onSuccess = { (items, pageInfo) ->
                    // 无限页：空页即止；有去重后无新增也视为止，避免重复数据死循环
                    if (items.isEmpty()) {
                        state.hasMore = false
                    } else {
                        val newList = (state.itemList + items).distinctBy { it.link }
                        val hasNew = newList.size > state.itemList.size
                        state.itemList = newList
                        // pageInfo.hasNext 已按无限页置为 items.isNotEmpty()，再结合去重结果
                        state.hasMore = pageInfo.hasNext && hasNew
                        // 若无新增但仍有数据，仍更新为空判断已在上层处理，此分支不重复赋值
                        if (!hasNew) {
                            // 已无新增，去重后未增长，视为到达末页
                        }
                    }
                },
                onFailure = {
                    state.hasMore = false
                    state.loadError = true
                },
            )
        } catch (e: Throwable) {
            state.hasMore = false
            state.loadError = true
        } finally {
            state.isLoading = false
        }
    }

    LaunchedEffect(source) {
        gridState.scrollToItem(0)
        snapshotFlow {
            val total = gridState.layoutInfo.totalItemsCount
            if (total == 0) return@snapshotFlow false
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 5 && state.hasMore && !state.isLoading
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    var lastKnownOffset by remember(source) { mutableIntStateOf(0) }
    var isScrollingDown by remember(source) { mutableStateOf(false) }

    LaunchedEffect(source) {
        isScrollingDown = false
        lastKnownOffset = 0
        snapshotFlow {
            gridState.isScrollInProgress to
                    (gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset)
        }.collect { (isScrolling, offsetState) ->
            if (isScrolling) {
                val (index, offset) = offsetState
                val currentTotal = index * 10000 + offset
                isScrollingDown = currentTotal > lastKnownOffset
                lastKnownOffset = currentTotal
            }
        }
    }

    val isAtTop by remember(source) {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset < 50
        }
    }

    val showFab by remember(source) {
        derivedStateOf {
            !isAtTop && !isScrollingDown
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        if (state.isLoading && state.itemList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(size = 56.dp)
            }
        } else if (state.loadError && state.itemList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MiuixTheme.colorScheme.primaryContainer)
                        .clickable { onRetrySearch() }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                ) {
                    MiuixText(
                        text = "点击重试",
                        color = MiuixTheme.colorScheme.onPrimaryContainer,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        } else if (state.itemList.isEmpty() && state.hasSearched) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MiuixText("未找到相关结果", color = MiuixTheme.colorScheme.onSurfaceSecondary)
            }
        } else if (state.itemList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MiuixText("输入关键词开始搜索", color = MiuixTheme.colorScheme.onSurfaceSecondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 12.dp,
                    bottom = 32.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.itemList, key = { it.link }) { item ->
                    SearchGridItem(
                        item = item,
                        onClick = {
                            focusManager.clearFocus()
                            onPlayVideo(item.link)
                        },
                        onViewImage = { _, _, _ -> onViewImage(item.cover, item.host, item.title) },
                    )
                }
                if (state.isLoading) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(size = 36.dp)
                        }
                    }
                } else if (state.loadError) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MiuixTheme.colorScheme.primaryContainer)
                                    .clickable { onRetryLoadMore() }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                            ) {
                                MiuixText(
                                    text = "点击重试",
                                    color = MiuixTheme.colorScheme.onPrimaryContainer,
                                    style = MiuixTheme.textStyles.footnote1,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showFab) {
            FloatingActionButton(
                onClick = { scope.launch { gridState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            ) {
                MiuixIcon(
                    painter = painterResource(R.drawable.arrow_upward_outlined),
                    contentDescription = "回到顶部",
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchGridItem(
    item: SearchItem,
    onClick: () -> Unit,
    onViewImage: (String, String, String) -> Unit
) {
    Card(
        onClick = onClick,
        onLongPress = { onViewImage(item.cover, item.host, item.title) },
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                CoverImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.cover)
                        .crossfade(true)
                        .httpHeaders(
                            NetworkHeaders.Builder()
                                .set("Referer", item.host)
                                .build()
                        ).build(),
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(8.dp)),
                )

                if (item.note.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(
                                color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        MiuixText(
                            text = item.note,
                            color = MiuixTheme.colorScheme.onPrimaryContainer,
                            style = MiuixTheme.textStyles.footnote1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (item.subtitle.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black
                                    )
                                )
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MiuixText(
                            text = item.subtitle,
                            color = Color.White,
                            style = MiuixTheme.textStyles.footnote1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                MiuixText(
                    text = item.title,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                if (item.year.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    MiuixText(
                        text = item.year,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }
    }
}
