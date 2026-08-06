package pjs.dogbox.ui.screen.discover

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import pjs.dogbox.ui.screen.ChrysanthemumLoading
import pjs.dogbox.utils.HttpUtil
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val API_BASE = "https://zgdypf.zgdypw.cn/getDayData"

private data class RealtimeMovieItem(
    val name: String,
    val releaseDesc: String,
    val releaseDays: Int,
    val totalBoxOffice: String,
    val dailyBoxOffice: String,
    val boxOfficeShare: String,
    val screeningShare: String,
    val onlineShare: String,
    val occupancyRate: String,
)

private data class RealtimeData(
    val nationalTotal: Double,
    val movies: List<RealtimeMovieItem>,
)

private data class ColumnDef(
    val label: String,
    val width: Dp,
    val align: TextAlign = TextAlign.Start
)

private val fixedColumns = listOf(
    ColumnDef("#", 40.dp, TextAlign.Center),
    ColumnDef("影片", 120.dp, TextAlign.Start),
)

private val scrollableColumns = listOf(
    ColumnDef("总票房", 100.dp, TextAlign.End),
    ColumnDef("今日票房", 100.dp, TextAlign.End),
    ColumnDef("票房占比", 90.dp, TextAlign.End),
    ColumnDef("排片占比", 90.dp, TextAlign.End),
    ColumnDef("网售占比", 90.dp, TextAlign.End),
    ColumnDef("上座率", 80.dp, TextAlign.End),
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

@SuppressLint("DefaultLocale")
private fun formatWanYi(value: Double): String {
    return if (value >= 10000) {
        String.format("%.2f亿", value / 10000)
    } else {
        String.format("%.2f万", value)
    }
}

private fun formatSales(raw: String): String {
    val value = raw.toDoubleOrNull() ?: return "${raw}万"
    return formatWanYi(value)
}

private fun releaseLabel(item: RealtimeMovieItem): String {
    if (item.releaseDesc.isNotEmpty() && !item.releaseDesc.contains("点映")) {
        return "${item.releaseDesc}上映"
    }
    return when (item.releaseDays) {
        -1 -> "明日上映"
        1 -> "上映首日"
        else -> if (item.releaseDays < 0) "还有 ${-item.releaseDays} 天上映" else "已上映 ${item.releaseDays} 天"
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxOfficeRealtimeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var selectedDate by remember { mutableStateOf("") }
    var currentDateStr by remember { mutableStateOf("") }
    var currentTime by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var realtimeData by remember { mutableStateOf<RealtimeData?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            currentDateStr = String.format(
                "%04d-%02d-%02d", cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
            currentTime = String.format(
                "%02d:%02d:%02d", cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND)
            )
            if (selectedDate.isEmpty()) {
                selectedDate = currentDateStr
            }
            delay(1000L)
        }
    }

    LaunchedEffect(selectedDate) {
        if (selectedDate.isEmpty()) return@LaunchedEffect
        while (true) {
            isLoading = true
            withContext(Dispatchers.IO) {
                HttpUtil.get("$API_BASE?date=$selectedDate&withSvcFee=true")
            }.onSuccess { response ->
                try {
                    val json = Json.parseToJsonElement(response).jsonObject
                    val nationalSales = json["nationalSales"]?.jsonObject
                    val nationalValue = nationalSales?.get("salesDesc")?.jsonObject
                        ?.get("value")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val list = json["list"]?.jsonArray ?: JsonArray(emptyList())
                    val items = list.mapNotNull { element ->
                        val obj = element.jsonObject
                        RealtimeMovieItem(
                            name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "未知影片",
                            releaseDesc = obj["releaseDesc"]?.jsonPrimitive?.contentOrNull ?: "",
                            releaseDays = obj["releaseDays"]?.jsonPrimitive?.int ?: -1,
                            totalBoxOffice = obj["sumSalesDesc"]?.jsonPrimitive?.contentOrNull
                                ?: "<0.1万",
                            dailyBoxOffice = obj["salesInWanDesc"]?.jsonPrimitive?.contentOrNull
                                ?: "<0.1万",
                            boxOfficeShare = obj["salesRateDesc"]?.jsonPrimitive?.contentOrNull
                                ?: "0.00%",
                            screeningShare = obj["sessionRateDesc"]?.jsonPrimitive?.contentOrNull
                                ?: "0.00%",
                            onlineShare = obj["onlineSalesRateDesc"]?.jsonPrimitive?.contentOrNull
                                ?: "0.00%",
                            occupancyRate = obj["seatRateDesc"]?.jsonPrimitive?.contentOrNull
                                ?: "0.00%",
                        )
                    }
                    realtimeData = RealtimeData(nationalTotal = nationalValue, movies = items)
                } catch (_: Exception) {
                }
            }.onFailure { }
            isLoading = false
            delay(8888L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        TopAppBar(
            title = { Text("实时票房", fontWeight = FontWeight.Bold) },
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
            actions = {
                FilledTonalIconButton(
                    onClick = { showDatePicker = true },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.calendar_month_outlined),
                        contentDescription = "选择日期",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        if (isLoading && realtimeData == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ChrysanthemumLoading()
            }
        } else {
            val data = realtimeData
            if (data != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val isToday = selectedDate == currentDateStr
                        Column {
                            Text(
                                text = if (isToday) currentDateStr else selectedDate,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (isToday) "截止 $currentTime" else "截止 23:59:59",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = formatWanYi(data.nationalTotal),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 32.dp)
            ) {
                val d = realtimeData
                val showLoad = isLoading && d == null
                val showEmpty = !isLoading && (d == null || d.movies.isEmpty())
                val showTable = d != null && d.movies.isNotEmpty()

                if (showLoad) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChrysanthemumLoading()
                    }
                } else if (showEmpty) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "暂无数据",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (showTable) {
                    BoxTable(data = d)
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = remember(showDatePicker) {
            if (!showDatePicker) null
            else try {
                val parsed = dateFormat.parse(selectedDate)
                val cal = Calendar.getInstance().apply {
                    if (parsed != null) time = parsed
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            } catch (_: Exception) {
                null
            }
        }

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        val selectableDates = remember {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val minDate = Calendar.getInstance().apply {
                        set(2017, Calendar.JANUARY, 1, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val maxDate = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.timeInMillis
                    return utcTimeMillis in minDate..maxDate
                }
            }
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = selectableDates,
            yearRange = 2017..currentYear,
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = dateFormat.format(Date(millis))
                    }
                    showDatePicker = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedDate = currentDateStr
                    showDatePicker = false
                }) {
                    Text("回到今日")
                }
            },
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false,
            )
        }
    }
}

@Composable
private fun BoxTable(data: RealtimeData) {
    val scrollState = rememberScrollState()

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TableHeader(scrollState = scrollState)

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(data.movies) { index, item ->
                    TableRow(
                        rank = index + 1,
                        item = item,
                        scrollState = scrollState,
                        isEven = index % 2 == 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun TableHeader(scrollState: androidx.compose.foundation.ScrollState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        fixedColumns.forEach { col ->
            Box(
                modifier = Modifier
                    .width(col.width)
                    .padding(top = 10.dp, bottom = 10.dp),
                contentAlignment = when (col.align) {
                    TextAlign.Center -> Alignment.Center
                    else -> Alignment.CenterStart
                },
            ) {
                Text(
                    text = col.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = col.align,
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.05f),
                                Color.Transparent,
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier.horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                scrollableColumns.forEach { col ->
                    Box(
                        modifier = Modifier
                            .width(col.width)
                            .padding(top = 10.dp, bottom = 10.dp, end = 12.dp),
                        contentAlignment = when (col.align) {
                            TextAlign.End -> Alignment.CenterEnd
                            else -> Alignment.CenterStart
                        },
                    ) {
                        Text(
                            text = col.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = col.align,
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun TableRow(
    rank: Int,
    item: RealtimeMovieItem,
    scrollState: androidx.compose.foundation.ScrollState,
    isEven: Boolean,
) {
    val bg =
        if (isEven) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(bg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankCell(rank = rank)
        MovieCell(item = item)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
            )

            ScrollableCells(item = item, scrollState = scrollState)
        }
    }
}


@Composable
private fun RankCell(rank: Int) {
    Box(
        modifier = Modifier
            .width(fixedColumns[0].width)
            .padding(top = 10.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun MovieCell(item: RealtimeMovieItem) {
    Box(
        modifier = Modifier
            .width(fixedColumns[1].width)
            .padding(top = 10.dp, bottom = 10.dp, end = 4.dp),
    ) {
        Column {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = releaseLabel(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScrollableCells(
    item: RealtimeMovieItem,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    val values = listOf(
        item.totalBoxOffice,
        formatSales(item.dailyBoxOffice),
        item.boxOfficeShare,
        item.screeningShare,
        item.onlineShare,
        item.occupancyRate,
    )

    Row(
        modifier = Modifier.horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        values.forEachIndexed { ci, text ->
            Text(
                text = text,
                modifier = Modifier
                    .width(scrollableColumns[ci].width)
                    .padding(top = 10.dp, bottom = 10.dp, end = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}
