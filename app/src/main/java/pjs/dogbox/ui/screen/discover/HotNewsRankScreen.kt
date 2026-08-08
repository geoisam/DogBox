package pjs.dogbox.ui.screen.discover

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import pjs.dogbox.ui.screen.ChrysanthemumLoading
import pjs.dogbox.ui.screen.PagerTabFollowEffect
import pjs.dogbox.ui.screen.rememberTabStateCache
import pjs.dogbox.utils.HttpUtil
import pjs.dogbox.utils.SimpleDragHandle
import java.net.URLEncoder

private data class HotItem(val title: String, val url: String)

private class HotTabState {
    var items by mutableStateOf(emptyList<HotItem>())
    var isLoading by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    val listState = LazyListState()
}

private class HotPlatform(
    val label: String,
    val url: String,
    val referer: String,
    val parse: (JsonElement) -> List<HotItem>,
)

private suspend fun HotPlatform.fetchItems(): List<HotItem> = withContext(Dispatchers.IO) {
    HttpUtil.get(url, mapOf("Referer" to referer)).fold(
        onSuccess = { text ->
            try {
                parse(Json.parseToJsonElement(text))
            } catch (_: Exception) {
                emptyList()
            }
        },
        onFailure = { emptyList() },
    )
}

private val hotPlatforms = listOf(
    // 微博
    HotPlatform("微博", "https://weibo.com/ajax/side/hotSearch", "https://www.weibo.com/", { element ->
        element.jsonObject["data"]?.jsonObject?.get("realtime")?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            val word = o["word"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            HotItem(word, "https://m.weibo.cn/search?containerid=100103type%3D1%26q%3D%23${URLEncoder.encode(word, "UTF-8")}%23")
        } ?: emptyList()
    }),
    // 抖音
    HotPlatform("抖音", "https://aweme-hl.snssdk.com/aweme/v1/hot/search/list/", "https://www.douyin.com/", { element ->
        element.jsonObject["data"]?.jsonObject?.get("word_list")?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            val word = o["word"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val sentenceId = o["sentence_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            HotItem(word, "https://www.douyin.com/hot/$sentenceId")
        } ?: emptyList()
    }),
    // 必应
    HotPlatform("必应", "https://www.bing.com/hp/api/v1/carousel?&format=json&ecount=50", "https://www.bing.com/", { element ->
        element.jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("items")?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            val title = o["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val rawUrl = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = if (rawUrl.startsWith("http")) rawUrl else "https://www.bing.com/$rawUrl"
            HotItem(title, url)
        } ?: emptyList()
    }),
    // 夸克
    HotPlatform("夸克", "https://iflow.quark.cn/iflow/api/v1/article/aggregation?aggregation_id=16665090098771297825&count=50", "https://123.quark.cn/", { element ->
        element.jsonObject["data"]?.jsonObject?.get("articles")?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = o["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            HotItem(title, "https://br.quark.cn/apps/qknewsiflow/routes/article?item_id=$id")
        } ?: emptyList()
    }),
    // 百度
    HotPlatform("百度", "https://top.baidu.com/api/board?platform=wise&tab=realtime", "https://www.baidu.com/", { element ->
        val cards = element.jsonObject["data"]?.jsonObject?.get("cards")?.jsonArray
        if (cards == null || cards.isEmpty()) return@HotPlatform emptyList<HotItem>()
        val contents = cards[0].jsonObject["content"]?.jsonArray ?: return@HotPlatform emptyList()
        val list = contents[0].jsonObject["content"]?.jsonArray ?: return@HotPlatform emptyList()
        list.mapNotNull { e ->
            val o = e.jsonObject
            val word = o["word"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            HotItem(word, url)
        }
    }),
    // 贴吧
    HotPlatform("贴吧", "https://tieba.baidu.com/hottopic/browse/topicList", "https://tieba.baidu.com/", { element ->
        element.jsonObject["data"]?.jsonObject?.get("bang_topic")?.jsonObject?.get("topic_list")?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            val title = o["abstract"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = o["topic_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            HotItem(title, url)
        } ?: emptyList()
    }),
    // 知乎
    HotPlatform("知乎", "https://api.zhihu.com/topstory/hot-list", "https://www.zhihu.com/", { element ->
        element.jsonObject["data"]?.jsonArray?.mapNotNull { e ->
            val t = e.jsonObject["target"]?.jsonObject ?: return@mapNotNull null
            val title = t["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val rawUrl = t["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val id = rawUrl.substringAfterLast("/", "").filter { it.isDigit() }
            if (id.isEmpty()) return@mapNotNull null
            HotItem(title, "https://www.zhihu.com/question/$id")
        } ?: emptyList()
    }),
    // 头条
    HotPlatform("头条", "https://www.toutiao.com/hot-event/hot-board/?origin=toutiao_pc", "https://www.toutiao.com/", { element ->
        element.jsonObject["data"]?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            val title = o["Title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = o["Url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            HotItem(title, url)
        } ?: emptyList()
    }),
    // 澎湃
    HotPlatform("澎湃", "https://cache.thepaper.cn/contentapi/wwwIndex/rightSidebar", "https://www.thepaper.cn/", { element ->
        element.jsonObject["data"]?.jsonObject?.get("hotNews")?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val contId = o["contId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            HotItem(name, "https://www.thepaper.cn/newsDetail_forward_$contId")
        } ?: emptyList()
    }),
    // 新浪
    HotPlatform("新浪", "https://interface.sina.cn/app.news/today_news.d.json?conf=page&order=desc&page=1&custom=%7B%22content_tag_id%22:%22%E7%83%AD%E9%97%A8,%E5%9B%BD%E9%99%85,%E6%97%B6%E6%94%BF,%E8%B4%A2%E7%BB%8F,%E7%A7%91%E6%8A%80,%E5%86%9B%E4%BA%8B,AI%22%7D&pageType=newsFlashv2", "https://sinanews.sina.cn/today/24news.d.html", { element ->
        element.jsonObject["data"]?.jsonObject?.get("components")?.jsonArray?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "timeline_list" }
            ?.jsonObject?.get("data")?.jsonArray?.mapNotNull { e ->
                val si = e.jsonObject["item"]?.jsonObject?.get("info")?.jsonObject?.get("interactionInfo")?.jsonObject?.get("shareInfo")?.jsonObject
                    ?: return@mapNotNull null
                val title = si["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val link = si["link"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                HotItem(title, link)
            } ?: emptyList()
    }),
    // 腾讯
    HotPlatform("腾讯", "https://r.inews.qq.com/gw/event/hot_ranking_list", "https://news.qq.com/", { element ->
        element.jsonObject["idlist"]?.jsonArray?.firstOrNull()?.jsonObject?.get("newslist")?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            val title = o["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            HotItem(title, url)
        } ?: emptyList()
    }),
    // 网易
    HotPlatform("网易", "https://m.163.com/fe/api/hot/news/flow", "https://news.163.com/", { element ->
        element.jsonObject["data"]?.jsonObject?.get("list")?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            val title = o["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            HotItem(title, url)
        } ?: emptyList()
    }),
    // 财联社
    HotPlatform("财联社", "https://www.cls.cn/api/cache?name=telegraph", "https://www.cls.cn/", { element ->
        element.jsonObject["data"]?.jsonObject?.get("roll_data")?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            val title = o["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            HotItem(title, "https://www.cls.cn/detail/$id")
        } ?: emptyList()
    }),
    // 华尔街
    HotPlatform("华尔街", "https://api-one-wscn.awtmt.com/apiv1/content/lives?channel=global-channel&limit=50", "https://wallstreetcn.com/", { element ->
        element.jsonObject["data"]?.jsonObject?.get("items")?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            val contentText = o["content_text"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val uri = o["uri"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            HotItem(contentText, uri)
        } ?: emptyList()
    }),
    // 虎嗅
    HotPlatform("虎嗅", "https://moment-api.huxiu.com/web-v3/moment/feed?platform=www", "https://www.huxiu.com/", { element ->
        element.jsonObject["data"]?.jsonObject?.get("moment_list")?.jsonObject?.get("datalist")?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            val content = o["content"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val objectId = o["object_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            HotItem(content.substringBefore("<br>"), "https://m.huxiu.com/moment/$objectId.html")
        } ?: emptyList()
    }),
    // 搜狗
    HotPlatform("搜狗", "https://so.html5.qq.com/landingpage/api/hotlist", "https://m.sogou.com/", { element ->
        element.jsonObject["data"]?.jsonObject?.get("mainBandData")?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            if (o["badge"]?.jsonPrimitive?.contentOrNull == "adv") return@mapNotNull null
            val title = o["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            HotItem(title, url)
        } ?: emptyList()
    }),
    // 360
    HotPlatform("360", "https://ranks.hao.360.com/mbsug-api/hotnewsquery?type=news&realhot_limit=50", "https://hao.360.com/", { element ->
        element.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            val title = o["long_title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            HotItem(title, url)
        } ?: emptyList()
    }),
    // 哔哩哔哩
    HotPlatform("哔哩哔哩", "https://api.bilibili.com/x/web-interface/ranking/v2?type=all", "https://www.bilibili.com/", { element ->
        element.jsonObject["data"]?.jsonObject?.get("list")?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            val title = o["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val bvid = o["bvid"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            HotItem(title, "https://www.bilibili.com/video/$bvid")
        } ?: emptyList()
    }),
)

private suspend fun HotTabState.refresh(platform: HotPlatform) {
    if (isRefreshing || isLoading) return
    if (items.isEmpty()) {
        isLoading = true
    } else {
        isRefreshing = true
    }
    val newItems = platform.fetchItems()
    items = newItems
    isLoading = false
    isRefreshing = false
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HotNewsRankScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val platforms = remember { hotPlatforms }
    val pagerState = rememberPagerState(pageCount = { platforms.size })
    val tabCache = rememberTabStateCache { HotTabState() }
    val context = LocalContext.current

    var showTabSheet by rememberSaveable { mutableStateOf(false) }
    val tabSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        TopAppBar(
            title = { Text("今日热榜", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            navigationIcon = {
                FilledTonalIconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back_outlined),
                        contentDescription = "返回",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        if (platforms.isNotEmpty()) {
            val tabScrollState = rememberLazyListState()
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
                    itemsIndexed(platforms) { index, platform ->
                        val selected = pagerState.currentPage == index
                        val chipColor by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            animationSpec = tween(200),
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(200),
                        )
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.large)
                                .background(chipColor)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                )
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = platform.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                maxLines = 1,
                            )
                        }
                    }
                }

                FilledTonalIconButton(
                    onClick = { showTabSheet = true },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.keyboard_double_arrow_right_outlined),
                        contentDescription = "展开",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                beyondViewportPageCount = 0,
            ) { page ->
                HotTabContent(
                    platform = platforms.getOrNull(page) ?: return@HorizontalPager,
                    tabState = tabCache.get(platforms[page].label),
                    context = context,
                )
            }
        }
    }

    if (showTabSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTabSheet = false },
            sheetState = tabSheetState,
            dragHandle = { SimpleDragHandle() },
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    platforms.forEachIndexed { index, platform ->
                        val isSelected = pagerState.currentPage == index
                        val bgColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        val txtColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .background(bgColor)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(index)
                                            showTabSheet = false
                                        }
                                    },
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = platform.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = txtColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HotTabContent(
    platform: HotPlatform,
    tabState: HotTabState,
    context: android.content.Context,
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (tabState.items.isEmpty()) {
            tabState.refresh(platform)
        }
    }

    val currentItems = tabState.items

    val listState = tabState.listState
    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 50
        }
    }
    var lastKnownOffset by remember { mutableIntStateOf(0) }
    var isScrollingDown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow {
            listState.isScrollInProgress to (listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
        }.collect { (isScrolling, info) ->
            if (isScrolling) {
                val (index, offset) = info
                val currentTotal = index * 10000 + offset
                isScrollingDown = currentTotal > lastKnownOffset
                lastKnownOffset = currentTotal
            }
        }
    }
    val showFab by remember { derivedStateOf { !isAtTop && !isScrollingDown } }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = tabState.isRefreshing,
            onRefresh = {
                scope.launch { tabState.refresh(platform) }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (tabState.isLoading && currentItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ChrysanthemumLoading()
                }
            } else if (currentItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(currentItems) { index, item ->
                        HotRankItem(item = item, index = index, onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, item.url.toUri())
                            context.startActivity(intent)
                        })
                    }
                }
            }
        }

        if (showFab) {
            FloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_upward_outlined),
                    contentDescription = "回到顶部",
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun HotRankItem(item: HotItem, index: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${index + 1}",
                    color = when (index) {
                        0 -> Color(0xFFFF3B30)
                        1 -> Color(0xFFFF8A00)
                        2 -> Color(0xFFFFB224)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}