package pjs.dogbox.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pjs.dogbox.R
import pjs.dogbox.data.model.AppConfig
import pjs.dogbox.ui.components.AppBottomSheet
import pjs.dogbox.utils.system.FestivalItem
import pjs.dogbox.utils.system.LunarSnapshot
import pjs.dogbox.utils.system.LunarUtil
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Calendar
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun MineScreen(
    modifier: Modifier = Modifier,
    onNavigateToHistory: () -> Unit = {},
    onNavigateToLocalVideos: () -> Unit = {},
    onNavigateToAboutMe: () -> Unit = {},
    onNavigateToSponsor: () -> Unit = {},
    contentBottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateState = remember { mutableStateMapOf<String, String>() }
    var showSheet by remember { mutableStateOf(false) }
    val nextFestival = remember { mutableStateOf(emptyList<FestivalItem>()) }

    fun applySnapshot(snapshot: LunarSnapshot) {
        dateState["YearMonth"] = snapshot.yearMonth
        dateState["ShiChen"] = snapshot.shiChen
        dateState["Week"] = snapshot.week
        dateState["Day"] = snapshot.day
        dateState["MonthDay"] = snapshot.monthDay
        dateState["GanZhi"] = snapshot.ganZhi
        dateState["JieQi"] = snapshot.jieQi
        dateState["DayYi"] = snapshot.dayYi
        dateState["DayJi"] = snapshot.dayJi
        dateState["DayChong"] = snapshot.dayChong
        dateState["PengZu"] = snapshot.pengZu
        dateState["DayXi"] = snapshot.dayXi
        dateState["DayCai"] = snapshot.dayCai
        dateState["DayFu"] = snapshot.dayFu
        nextFestival.value = snapshot.festivals
    }

    LaunchedEffect(Unit) {
        try {
            if (dateState.isEmpty()) {
                val preloaded = AppConfig.snapshot
                if (preloaded != null) {
                    applySnapshot(preloaded)
                } else {
                    applySnapshot(withContext(Dispatchers.Default) { LunarUtil.takeSnapshot() })
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        while (true) {
            delay(calculateTimeToNextHour())
            try {
                applySnapshot(withContext(Dispatchers.Default) { LunarUtil.takeSnapshot() })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                scope.launch {
                    try {
                        applySnapshot(withContext(Dispatchers.Default) { LunarUtil.takeSnapshot() })
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(bottom = contentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showSheet = true },
                showIndication = true,
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MiuixText(
                                text = dateState["YearMonth"] ?: "2002 年 1 月",
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Bold,
                            )
                            Box(
                                modifier = Modifier
                                    .background(
                                        MiuixTheme.colorScheme.primary.copy(alpha = 0.75f),
                                        CircleShape
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                MiuixText(
                                    text = dateState["ShiChen"] ?: "未时",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onPrimary,
                                )
                            }
                            if (dateState["JieQi"]?.isNotBlank() == true) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                                            CircleShape
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    MiuixText(
                                        text = dateState["JieQi"] ?: "大寒",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                        MiuixText(
                            text = dateState["Week"] ?: "星期二",
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    MiuixText(
                        text = dateState["Day"] ?: "29",
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 59.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MiuixText(
                        text = dateState["MonthDay"] ?: "腊月十七",
                        style = MiuixTheme.textStyles.title2,
                        fontWeight = FontWeight.Bold,
                    )
                    MiuixText(
                        text = dateState["GanZhi"] ?: "辛巳蛇年 辛丑牛月 丁酉鸡日",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MineCard(
                    iconRes = R.drawable.kid_star_outlined,
                    text = "收藏夹",
                    onClick = {
                        Toast.makeText(
                            context,
                            "功能开发中",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                )
                MineCard(
                    iconRes = R.drawable.history_outlined,
                    text = "观看历史",
                    onClick = { onNavigateToHistory() },
                    modifier = Modifier.weight(1f),
                )
                MineCard(
                    iconRes = R.drawable.cloud_download_outlined,
                    text = "离线缓存",
                    onClick = {
                        Toast.makeText(
                            context,
                            "功能开发中",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MineItem(
                        iconRes = R.drawable.database_outlined,
                        text = "订阅管理",
                        onClick = {
                            Toast.makeText(
                                context,
                                "功能开发中",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    MineItem(
                        iconRes = R.drawable.animated_images_outlined,
                        text = "本地视频",
                        onClick = { onNavigateToLocalVideos() },
                        modifier = Modifier.weight(1f),
                    )
                    MineItem(
                        iconRes = R.drawable.dataset_linked_outlined,
                        text = "数据推送",
                        onClick = {
                            Toast.makeText(
                                context,
                                "功能开发中",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    MineItem(
                        iconRes = R.drawable.home_storage_outlined,
                        text = "备份与恢复",
                        onClick = {
                            Toast.makeText(
                                context,
                                "功能开发中",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    MineListItem(
                        iconRes = R.drawable.favorite_outlined,
                        text = "捐赠支持",
                        onClick = { onNavigateToSponsor() }
                    )
                    MineListItem(
                        iconRes = R.drawable.info_outlined,
                        text = "关于应用",
                        onClick = { onNavigateToAboutMe() }
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    if (showSheet) {
        AppBottomSheet(
            show = true,
            onDismissRequest = { showSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column {
                        nextFestival.value.forEach { jieri ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    MiuixText(
                                        text = jieri.name,
                                        style = MiuixTheme.textStyles.title4,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    MiuixText(
                                        text = "${jieri.date.monthValue}月${jieri.date.dayOfMonth}日 (周${jieri.weekday})",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    )
                                }
                                MiuixText(
                                    text = buildAnnotatedString {
                                        withStyle(
                                            SpanStyle(
                                                fontSize = MiuixTheme.textStyles.title2.fontSize,
                                                color = MiuixTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        ) {
                                            append(
                                                if (jieri.daysLeft.toInt() == 0) "今" else jieri.daysLeft.toString()
                                            )
                                        }
                                        append(" ")
                                        withStyle(
                                            SpanStyle(
                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                            )
                                        ) {
                                            append("天")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                LunarInfoCard(
                    label = "宜",
                    value = dateState["DayYi"] ?: "无",
                )
                LunarInfoCard(
                    label = "忌",
                    value = dateState["DayJi"] ?: "无",
                )
                LunarInfoCard(
                    label = "冲煞",
                    value = dateState["DayChong"] ?: "无",
                )
                LunarInfoCard(
                    label = "彭祖",
                    value = dateState["PengZu"] ?: "无",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DirectionCard(
                        label = "喜",
                        containerColor = MiuixTheme.colorScheme.errorContainer,
                        contentColor = MiuixTheme.colorScheme.onErrorContainer,
                        value = dateState["DayXi"] ?: "喜神方位",
                        modifier = Modifier.weight(1f)
                    )
                    DirectionCard(
                        label = "财",
                        containerColor = MiuixTheme.colorScheme.tertiaryContainer,
                        contentColor = MiuixTheme.colorScheme.onTertiaryContainer,
                        value = dateState["DayCai"] ?: "财神方位",
                        modifier = Modifier.weight(1f)
                    )
                    DirectionCard(
                        label = "福",
                        containerColor = MiuixTheme.colorScheme.primaryContainer,
                        contentColor = MiuixTheme.colorScheme.onPrimaryContainer,
                        value = dateState["DayFu"] ?: "福神方位",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

fun calculateTimeToNextHour(): Long {
    val cal = Calendar.getInstance()
    cal.add(Calendar.HOUR_OF_DAY, 1)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis - System.currentTimeMillis()
}

@Composable
fun MineCard(
    iconRes: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        showIndication = true,
        insideMargin = PaddingValues(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiuixIcon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            MiuixText(
                text = text,
                style = MiuixTheme.textStyles.body2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun MineItem(
    iconRes: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.defaultColors(color = Color.Transparent),
        showIndication = true,
        insideMargin = PaddingValues(vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiuixIcon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            MiuixText(
                text = text,
                style = MiuixTheme.textStyles.body2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun MineListItem(
    iconRes: Int,
    text: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.defaultColors(color = Color.Transparent),
        showIndication = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixIcon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.size(16.dp))
            MiuixText(
                text = text,
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.body2,
            )
            MiuixIcon(
                painter = painterResource(R.drawable.keyboard_arrow_right_outlined),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

@Composable
private fun LunarInfoCard(
    label: String,
    value: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiuixText(
                text = label,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            MiuixText(
                text = value,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}

@Composable
fun DirectionCard(
    label: String,
    containerColor: Color,
    contentColor: Color,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.background(containerColor, CircleShape),
            ) {
                MiuixText(
                    text = label,
                    style = MiuixTheme.textStyles.title2,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            MiuixText(
                text = value,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
