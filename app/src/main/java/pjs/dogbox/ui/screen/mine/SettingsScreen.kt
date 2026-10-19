package pjs.dogbox.ui.screen.mine

import pjs.dogbox.utils.openPermissionManager

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import pjs.dogbox.R
import pjs.dogbox.ui.screen.movie.GlobalPlayerConfig
import pjs.dogbox.ui.screen.movie.PlaybackOrder
import pjs.dogbox.ui.screen.movie.SPEED_KEY_POINTS
import pjs.dogbox.data.SharedData
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayDialog

private val ThemeModeLabels = listOf("跟随系统", "浅色模式", "深色模式")

private val DownloadModeLabels = listOf("内置下载器", "系统下载器")

private val PaletteStyleOptions = listOf(
    "TonalSpot" to "经典色调",
    "Neutral" to "柔和中性",
    "Vibrant" to "高饱和度",
    "Expressive" to "撞色组合",
    "Rainbow" to "色相遍历",
    "FruitSalad" to "多彩高亮",
    "Monochrome" to "极简单色",
    "Fidelity" to "真实还原",
    "Content" to "内容原色",
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onThemeChanged: (mode: Int, dynamic: Boolean, dynamicStyle: String) -> Unit = { _, _, _ -> },
    onNavigateToCacheManage: () -> Unit = {},
) {
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var downloadMode by remember { mutableIntStateOf(SharedData.getDownloadMode(context)) }
    var themeMode by remember { mutableIntStateOf(SharedData.getThemeMode(context).takeIf { it in 0..2 } ?: 0) }
    var monetEnable by remember { mutableStateOf(SharedData.getDynamicColor(context)) }
    var dynamicColorStyle by remember { mutableStateOf(SharedData.getDynamicColorStyle(context)) }
    var showSpeedSheet by remember { mutableStateOf(false) }

    fun applyTheme(
        mode: Int = themeMode,
        monet: Boolean = monetEnable,
        dStyle: String = dynamicColorStyle,
    ) {
        themeMode = mode
        monetEnable = monet
        dynamicColorStyle = dStyle
        onThemeChanged(mode, monet, dStyle)
        SharedData.setThemeMode(context, mode)
        SharedData.setDynamicColor(context, monet)
        SharedData.setDynamicColorStyle(context, dStyle)
    }

    // 配色风格：默认经典色调（TonalSpot，与系统默认一致）
    val paletteStyleIds = remember { PaletteStyleOptions.map { it.first } }
    val paletteStyleLabels = remember { PaletteStyleOptions.map { it.second } }
    val paletteStyleIndex = paletteStyleIds.indexOf(dynamicColorStyle)
        .takeIf { it >= 0 } ?: paletteStyleIds.indexOf("TonalSpot").coerceAtLeast(0)

    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = "设置",
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsSectionHeader("外观")

            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = "主题模式",
                    items = ThemeModeLabels,
                    selectedIndex = themeMode,
                    onSelectedIndexChange = { applyTheme(mode = it) },
                )
                SwitchPreference(
                    title = "莫奈取色",
                    summary = if (supportsDynamicColor) "跟随桌面壁纸动态取色" else "当前系统不支持动态取色",
                    checked = monetEnable && supportsDynamicColor,
                    enabled = supportsDynamicColor,
                    onCheckedChange = { applyTheme(monet = it) },
                )
                OverlayDropdownPreference(
                    title = "配色风格",
                    items = paletteStyleLabels,
                    selectedIndex = paletteStyleIndex,
                    enabled = monetEnable && supportsDynamicColor,
                    onSelectedIndexChange = { applyTheme(dStyle = paletteStyleIds[it]) },
                )
            }

            SettingsSectionHeader("通用")

            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = "下载方式",
                    items = DownloadModeLabels,
                    selectedIndex = downloadMode.coerceIn(0, DownloadModeLabels.lastIndex),
                    onSelectedIndexChange = { mode ->
                        downloadMode = mode
                        SharedData.setDownloadMode(context, mode)
                    },
                )
                ArrowPreference(
                    title = "缓存管理",
                    onClick = onNavigateToCacheManage,
                )
            }

            SettingsSectionHeader("播放")

            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "长按倍速",
                    endActions = {
                        MiuixText(
                            text = "${"%.2f".format(GlobalPlayerConfig.longPressSpeed)}x",
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .padding(end = 8.dp),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    },
                    onClick = { showSpeedSheet = true },
                )
                OverlayDropdownPreference(
                    title = "播放顺序",
                    items = PlaybackOrder.entries.map { it.label },
                    selectedIndex = PlaybackOrder.entries
                        .indexOf(GlobalPlayerConfig.playbackOrder)
                        .coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        GlobalPlayerConfig.setPlaybackOrder(context, PlaybackOrder.entries[index])
                    },
                )
                SwitchPreference(
                    title = "后台播放",
                    checked = GlobalPlayerConfig.backgroundPlayback,
                    onCheckedChange = {
                        GlobalPlayerConfig.setBackgroundPlayback(context, it)
                    },
                )
                SwitchPreference(
                    title = "过滤广告",
                    checked = GlobalPlayerConfig.adsFilter,
                    onCheckedChange = {
                        GlobalPlayerConfig.setAdsFilter(context, it)
                    },
                )
            }

            SettingsSectionHeader("其他")

            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "通知管理",
                    onClick = { openNotificationSettings(context) },
                )
                ArrowPreference(
                    title = "权限管理",
                    onClick = { openPermissionManager(context) },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showSpeedSheet) {
        LongPressSpeedDialog(
            currentSpeed = GlobalPlayerConfig.longPressSpeed,
            onSpeedConfirm = { speed ->
                GlobalPlayerConfig.setLongPressSpeed(context, speed)
            },
            onDismiss = { showSpeedSheet = false },
        )
    }
}

internal fun openNotificationSettings(context: android.content.Context) {
    val pkg = context.packageName
    val candidates = mutableListOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra("app_package", pkg),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) {
        }
    }
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$pkg".toUri())
        )
    } catch (_: Exception) {
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    SmallTitle(
        text = title,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun LongPressSpeedDialog(
    currentSpeed: Float,
    onSpeedConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var sliderSpeed by remember(currentSpeed) { mutableStateOf(currentSpeed) }

    OverlayDialog(
        show = true,
        title = "自定义倍速",
        insideMargin = DpSize(0.dp, 24.dp),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixText(
                    text = "长按倍速",
                    style = MiuixTheme.textStyles.body1,
                )
                MiuixText(
                    text = "${"%.2f".format(sliderSpeed)}x",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Slider(
                value = sliderSpeed,
                onValueChange = { sliderSpeed = (it * 4).toInt() / 4f },
                onValueChangeFinished = { onSpeedConfirm(sliderSpeed) },
                valueRange = 0.25f..5.0f,
                steps = 18,
                showKeyPoints = true,
                keyPoints = SPEED_KEY_POINTS,
                magnetThreshold = 0.03f,
                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "恢复默认",
                    onClick = {
                        sliderSpeed = 3.0f
                        onSpeedConfirm(3.0f)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
