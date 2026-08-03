package pjs.dogbox.ui.screen.discover

import android.annotation.SuppressLint
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import pjs.dogbox.ui.screen.ChrysanthemumLoading
import pjs.dogbox.util.OkHttpUtil
import java.nio.charset.Charset

private data class GlobalBoxOfficeItem(
    val rank: Int,
    val name: String,
    val year: String,
    val director: String,
    val boxOfficeRaw: Long,
)

private const val SCRAPE_URL = "http://www.piaofang.biz"

@SuppressLint("DefaultLocale")
private fun formatBoxOffice(raw: Long): String {
    val inYi = raw * 7 / 100000000.00
    return String.format("%.2f亿", inYi)
}

private fun parsePiaofangHtml(html: String): List<GlobalBoxOfficeItem> {
    val doc = Ksoup.parse(html)
    val items = mutableListOf<GlobalBoxOfficeItem>()
    var rank = -1
    for (row in doc.select("tr")) {
        rank++
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
        items.add(GlobalBoxOfficeItem(rank, name, year, director, boxOffice))
    }
    return items
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieWorldScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchMovie: (String) -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<GlobalBoxOfficeItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        isLoading = true
        val result = withContext(Dispatchers.IO) {
            OkHttpUtil.getBytes(SCRAPE_URL, connectTimeout = 10, readTimeout = 10)
        }
        result.onSuccess { bytes ->
            val html = String(bytes, Charset.forName("gb2312"))
            items = parsePiaofangHtml(html)
        }
        isLoading = false
    }

    val scrollState = rememberLazyListState()
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

    Box(modifier = modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("全球票房", fontWeight = FontWeight.Bold) },
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
                },
            )

            if (isLoading && items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ChrysanthemumLoading()
                }
            } else if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "全球票房影史榜",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = $$"汇率采用固定汇率，$1 USD≈￥7 CNY (仅供参考)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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

        if (showFab) {
            FloatingActionButton(
                onClick = { fabScope.launch { scrollState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_upward_outlined),
                    contentDescription = "回到顶部",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun GlobalBoxOfficeItemCard(item: GlobalBoxOfficeItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${item.rank}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(4.dp))

                val yearDirector = buildString {
                    append(item.year)
                    if (item.director.isNotEmpty()) {
                        append(" · ")
                        append(item.director)
                    }
                }
                Text(
                    text = yearDirector,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = formatBoxOffice(item.boxOfficeRaw),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
        }
    }
}
