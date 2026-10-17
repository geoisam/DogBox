package pjs.dogbox.ui.screen.discover

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fleeksoft.ksoup.Ksoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pjs.dogbox.R
import pjs.dogbox.ui.components.MiuixEmptyText
import pjs.dogbox.ui.components.MiuixLoading
import pjs.dogbox.ui.components.MiuixSubScreen
import pjs.dogbox.utils.HttpUtil
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFab
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.nio.charset.Charset

internal data class GlobalBoxOfficeItem(
    val rank: Int,
    val name: String,
    val year: String,
    val director: String,
    val boxOfficeRaw: Long,
)

internal class MovieWorldScreenState {
    val isLoading = mutableStateOf(true)
    val isRefreshing = mutableStateOf(false)
    val items = mutableStateOf<List<GlobalBoxOfficeItem>>(emptyList())
    val refreshKey = mutableIntStateOf(0)
    val scrollState = LazyListState()
}

private const val SCRAPE_URL = "http://www.piaofang.biz"

@SuppressLint("DefaultLocale")
private fun formatBoxOffice(raw: Long): String {
    val inYi = raw * 7 / 100000000.00
    return String.format("%.2f亿", inYi)
}

private fun parsePiaofangHtml(html: String): List<GlobalBoxOfficeItem> {
    val doc = Ksoup.parse(html)
    val items = mutableListOf<GlobalBoxOfficeItem>()
    var rank = 0
    for (row in doc.select("tr")) {
        val titleTd = row.select("td.title").first() ?: continue
        titleTd.select("span").remove()
        val yearTd = row.select("td.year").first() ?: continue
        val directorTd = row.select("td.daoyan").first() ?: continue
        val piaofangTd = row.select("td.piaofang span").first() ?: continue
        val name = titleTd.text().trim().replace("《", "").replace("》", "")
        val year = yearTd.text().trim()
        val director = directorTd.text().trim()
        val boxOfficeStr = piaofangTd.text().trim().replace(",", "")
        val boxOffice = boxOfficeStr.toLongOrNull() ?: 0L
        rank++
        items.add(GlobalBoxOfficeItem(rank, name, year, director, boxOffice))
    }
    return items
}

@Composable
internal fun MovieWorldScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchMovie: (String) -> Unit = {},
    state: MovieWorldScreenState = remember { MovieWorldScreenState() },
) {
    var isLoading by state.isLoading
    var isRefreshing by state.isRefreshing
    var items by state.items
    var refreshKey by state.refreshKey

    LaunchedEffect(refreshKey) {
        val isPull = refreshKey > 0
        if (isPull) isRefreshing = true else isLoading = true
        val result = withContext(Dispatchers.IO) {
            HttpUtil.getBytes(SCRAPE_URL, connectTimeout = 10, readTimeout = 10)
        }
        result.onSuccess { bytes ->
            val html = String(bytes, Charset.forName("gb2312"))
            items = parsePiaofangHtml(html)
        }
        isLoading = false
        isRefreshing = false
    }

    val scrollState = state.scrollState
    var showFab by remember { mutableStateOf(false) }
    val fabScope = rememberCoroutineScope()

    LaunchedEffect(scrollState) {
        var prevIndex = 0
        var prevOffset = 0
        snapshotFlow {
            scrollState.firstVisibleItemIndex to scrollState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val scrolledDown = index > prevIndex || (index == prevIndex && offset > prevOffset)
            val pastThreshold = index > 0 || offset > 200
            showFab = !(scrolledDown || !pastThreshold)
            prevIndex = index
            prevOffset = offset
        }
    }

    MiuixSubScreen(
        title = "全球票房",
        onBack = onBack,
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MiuixPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = { refreshKey++ },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    isLoading && items.isEmpty() -> {
                        MiuixLoading(modifier = Modifier.fillMaxSize())
                    }

                    items.isEmpty() -> {
                        MiuixEmptyText(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        )
                    }

                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            MiuixCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                colors = MiuixCardDefaults.defaultColors(
                                    color = MiuixTheme.colorScheme.primaryContainer,
                                ),
                            ) {
                                MiuixText(
                                    text = "全球票房影史榜",
                                    style = MiuixTheme.textStyles.title4,
                                    color = MiuixTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                MiuixText(
                                    text = $$"汇率采用固定汇率，$1 USD≈￥7 CNY (仅供参考)",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                )
                            }

                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = scrollState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 12.dp,
                                        end = 12.dp,
                                        top = 4.dp,
                                        bottom = 32.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    items(items, key = { it.rank }) { item ->
                                        GlobalBoxOfficeItemCard(
                                            item = item,
                                            onClick = { onSearchMovie(item.name) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showFab) {
                MiuixFab(
                    onClick = { fabScope.launch { scrollState.animateScrollToItem(0) } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
                ) {
                    MiuixIcon(
                        painter = painterResource(R.drawable.arrow_upward_outlined),
                        contentDescription = "回到顶部",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GlobalBoxOfficeItemCard(item: GlobalBoxOfficeItem, onClick: () -> Unit) {
    MiuixCard(
        onClick = onClick,
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        modifier = Modifier.fillMaxWidth(),
        colors = MiuixCardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixText(
                text = "${item.rank}",
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Bold,
                color = when (item.rank - 1) {
                    0 -> Color(0xFFFF3B30)
                    1 -> Color(0xFFFF8A00)
                    2 -> Color(0xFFFFB224)
                    else -> MiuixTheme.colorScheme.onSurfaceSecondary
                },
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                MiuixText(
                    text = item.name,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(4.dp))

                val yearDirector = buildString {
                    append(item.year)
                    if (item.director.isNotEmpty()) {
                        append(" · ")
                        append(item.director)
                    }
                }
                MiuixText(
                    text = yearDirector,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            MiuixText(
                text = formatBoxOffice(item.boxOfficeRaw),
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
        }
    }
}
