package pjs.dogbox.ui.screens.discover

import android.annotation.SuppressLint
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import pjs.dogbox.ui.components.MiuixEmptyText
import pjs.dogbox.ui.components.MiuixLoading
import pjs.dogbox.utils.network.HttpUtil
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Calendar
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.NumberPicker as MiuixNumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton

private const val API_BASE = "https://zgdypf.zgdypw.cn/getDayData"

private const val MIN_DATE_YEAR = 2017

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

private fun daysInMonth(year: Int, month: Int): Int {
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }.getActualMaximum(Calendar.DAY_OF_MONTH)
}


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

    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = "实时票房",
                navigationIcon = {
                    MiuixIconButton(onClick = onBack) {
                        MiuixIcon(
                            painter = painterResource(R.drawable.arrow_back_outlined),
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                actions = {
                    MiuixIconButton(onClick = { showDatePicker = true }) {
                        MiuixIcon(
                            painter = painterResource(R.drawable.calendar_month_outlined),
                            contentDescription = "选择日期",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && realtimeData == null) {
                MiuixLoading(modifier = Modifier.fillMaxSize())
            } else {
                val data = realtimeData
                if (data != null) {
                    MiuixCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        colors = MiuixCardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.surfaceContainer,
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
                                MiuixText(
                                    text = if (isToday) currentDateStr else selectedDate,
                                    style = MiuixTheme.textStyles.title4,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                MiuixText(
                                    text = if (isToday) "截止 $currentTime" else "截止 23:59:59",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            MiuixText(
                                text = formatWanYi(data.nationalTotal),
                                style = MiuixTheme.textStyles.title1,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.primary,
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
                        MiuixLoading(modifier = Modifier.fillMaxSize())
                    } else if (showEmpty) {
                        MiuixEmptyText(modifier = Modifier.fillMaxSize())
                    }

                    if (showTable) {
                        BoxTable(data = d)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        RealtimeDatePickerDialog(
            selectedDate = selectedDate,
            currentDateStr = currentDateStr,
            onConfirm = { date ->
                selectedDate = date
                showDatePicker = false
            },
            onToday = {
                selectedDate = currentDateStr
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun RealtimeDatePickerDialog(
    selectedDate: String,
    currentDateStr: String,
    onConfirm: (String) -> Unit,
    onToday: () -> Unit,
    onDismiss: () -> Unit,
) {
    val todayParts = remember(currentDateStr) {
        val p = currentDateStr.split("-").mapNotNull { it.toIntOrNull() }
        Triple(p.getOrNull(0) ?: MIN_DATE_YEAR, p.getOrNull(1) ?: 1, p.getOrNull(2) ?: 1)
    }
    val initParts = remember(selectedDate) {
        val p = selectedDate.split("-").mapNotNull { it.toIntOrNull() }
        Triple(p.getOrNull(0) ?: todayParts.first, p.getOrNull(1) ?: 1, p.getOrNull(2) ?: 1)
    }
    var pickYear by remember {
        mutableIntStateOf(
            initParts.first.coerceIn(
                MIN_DATE_YEAR,
                todayParts.first
            )
        )
    }
    var pickMonth by remember { mutableIntStateOf(initParts.second.coerceIn(1, 12)) }
    var pickDay by remember {
        mutableIntStateOf(initParts.third.coerceIn(1, daysInMonth(pickYear, pickMonth)))
    }

    val maxDay = daysInMonth(pickYear, pickMonth)
    if (pickDay > maxDay) pickDay = maxDay

    OverlayDialog(
        show = true,
        title = "选择日期",
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixNumberPicker(
                    value = pickYear,
                    onValueChange = {
                        pickYear = it
                        val m = daysInMonth(pickYear, pickMonth)
                        if (pickDay > m) pickDay = m
                    },
                    range = MIN_DATE_YEAR..todayParts.first,
                    label = { "${it}年" },
                    modifier = Modifier.weight(1.2f),
                )
                MiuixNumberPicker(
                    value = pickMonth,
                    onValueChange = {
                        pickMonth = it
                        val m = daysInMonth(pickYear, pickMonth)
                        if (pickDay > m) pickDay = m
                    },
                    range = 1..12,
                    label = { "%02d月".format(it) },
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
                MiuixNumberPicker(
                    value = pickDay,
                    onValueChange = { pickDay = it },
                    range = 1..maxDay,
                    label = { "%02d日".format(it) },
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixTextButton(
                    text = "回到今日",
                    onClick = onToday,
                )
                MiuixTextButton(
                    text = "确定",
                    onClick = {
                        var picked = String.format("%04d-%02d-%02d", pickYear, pickMonth, pickDay)
                        if (currentDateStr.isNotEmpty() && picked > currentDateStr) {
                            picked = currentDateStr
                        }
                        if (picked < "$MIN_DATE_YEAR-01-01") {
                            picked = "$MIN_DATE_YEAR-01-01"
                        }
                        onConfirm(picked)
                    },
                )
            }
        }
    }
}

@Composable
private fun BoxTable(data: RealtimeData?) {
    val scrollState = rememberScrollState()

    MiuixCard(
        modifier = Modifier.fillMaxSize(),
        colors = MiuixCardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TableHeader(scrollState = scrollState)

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(data?.movies ?: emptyList()) { index, item ->
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
private fun TableHeader(scrollState: ScrollState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
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
                MiuixText(
                    text = col.label,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
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
                        MiuixText(
                            text = col.label,
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
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
    scrollState: ScrollState,
    isEven: Boolean,
) {
    val bg =
        if (isEven) MiuixTheme.colorScheme.surfaceContainer else MiuixTheme.colorScheme.surfaceContainerHigh

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
        MiuixText(
            text = "$rank",
            style = MiuixTheme.textStyles.title4,
            fontWeight = FontWeight.Bold,
            color = when (rank - 1) {
                0 -> Color(0xFFFF3B30)
                1 -> Color(0xFFFF8A00)
                2 -> Color(0xFFFFB224)
                else -> MiuixTheme.colorScheme.onSurfaceSecondary
            },
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
            MiuixText(
                text = item.name,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.size(2.dp))
            MiuixText(
                text = releaseLabel(item),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScrollableCells(
    item: RealtimeMovieItem,
    scrollState: ScrollState,
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
            MiuixText(
                text = text,
                modifier = Modifier
                    .width(scrollableColumns[ci].width)
                    .padding(top = 10.dp, bottom = 10.dp, end = 12.dp),
                style = MiuixTheme.textStyles.body2,
                textAlign = TextAlign.End,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}
