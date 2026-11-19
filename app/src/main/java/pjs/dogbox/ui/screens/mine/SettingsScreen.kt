package pjs.dogbox.ui.screens.mine

import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.glass.GlassIconButton
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import pjs.dogbox.R
import pjs.dogbox.data.repository.AppRepository
import pjs.dogbox.ui.screens.movie.GlobalPlayerConfig
import pjs.dogbox.ui.screens.movie.PlaybackOrder
import pjs.dogbox.ui.screens.movie.SPEED_KEY_POINTS
import pjs.dogbox.utils.player.DecoderMode
import pjs.dogbox.utils.system.openPermissionManager
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

private val ThemeModeLabels = listOf("跟随系统", "浅色模式", "深色模式")

private val SearchThreadKeyPoints =
    List(AppRepository.MAX_SEARCH_THREADS - AppRepository.MIN_SEARCH_THREADS + 1) {
        (AppRepository.MIN_SEARCH_THREADS + it).toFloat()
    }

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
    onThemeChanged: (mode: Int, dynamic: Boolean, dynamicStyle: String, pureBlack: Boolean) -> Unit = { _, _, _, _ -> },
    onNavigateToCacheManage: () -> Unit = {},
) {
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var themeMode by remember {
        mutableIntStateOf(
            AppRepository.getThemeMode(context).takeIf { it in 0..2 } ?: 0)
    }
    var monetEnable by remember { mutableStateOf(AppRepository.getDynamicColor(context)) }
    var dynamicColorStyle by remember { mutableStateOf(AppRepository.getDynamicColorStyle(context)) }
    var pureBlackBackground by remember {
        mutableStateOf(AppRepository.isPureBlackBackground(context))
    }
    var searchThreads by remember { mutableIntStateOf(AppRepository.getSearchThreads(context)) }
    var longPressSpeed by remember { mutableFloatStateOf(AppRepository.getLongPressSpeed(context)) }

    // 轻透磨砂 / 柔光玻璃依赖运行时着色器（Android 13+），不支持时只能使用标准材质
    val supportsNavBarEffect = isRuntimeShaderSupported()

    var floatingNavBar by remember { mutableStateOf(AppRepository.isFloatingNavBarEnabled(context)) }
    var hideNavBarDivider by remember { mutableStateOf(AppRepository.isNavBarDividerHidden(context)) }
    var navBarEffect by remember {
        mutableStateOf(
            if (supportsNavBarEffect) AppRepository.getNavBarEffect(
                context
            ) else 0
        )
    }
    // 顶栏高级材质：Android 13 以下依赖的运行时着色器不可用，固定关闭
    var advancedMaterial by remember { mutableStateOf(AppRepository.getAdvancedMaterial(context)) }

    val navBarEffectLabels = listOf("标准材质", "轻透磨砂", "柔光玻璃")
    val navBarEffectEnabled = supportsNavBarEffect
    val navBarEffectIndex = navBarEffect.coerceIn(0, navBarEffectLabels.lastIndex)

    fun applyTheme(
        mode: Int = themeMode,
        monet: Boolean = monetEnable,
        dStyle: String = dynamicColorStyle,
        pureBlack: Boolean = pureBlackBackground,
    ) {
        themeMode = mode
        monetEnable = monet
        dynamicColorStyle = dStyle
        pureBlackBackground = pureBlack
        onThemeChanged(mode, monet, dStyle, pureBlack)
        AppRepository.setThemeMode(context, mode)
        AppRepository.setDynamicColor(context, monet)
        AppRepository.setDynamicColorStyle(context, dStyle)
        AppRepository.setPureBlackBackground(context, pureBlack)
    }

    // 配色风格：默认经典色调（TonalSpot，与系统默认一致）
    val paletteStyleIds = remember { PaletteStyleOptions.map { it.first } }
    val paletteStyleLabels = remember { PaletteStyleOptions.map { it.second } }
    val paletteStyleIndex = paletteStyleIds.indexOf(dynamicColorStyle)
        .takeIf { it >= 0 } ?: paletteStyleIds.indexOf("TonalSpot").coerceAtLeast(0)

    MiuixScaffold(
        topBar = {
            GlassTopAppBar(
                expandable = false,
                bandBrush = if (advancedMaterial) {
                    GlassTopAppBarDefaults.bandBrush(MiuixTheme.colorScheme.surface)
                } else {
                    SolidColor(MiuixTheme.colorScheme.surface)
                },

                fill = if (advancedMaterial) GlassTopAppBarDefaults.buttonFill() else Color.Transparent,

                title = "设置",
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Glass.ChevronBackward,
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
                    summary = "浅色、深色或跟随系统",
                    items = ThemeModeLabels,
                    selectedIndex = themeMode,
                    onSelectedIndexChange = { applyTheme(mode = it) },
                )
                // 莫奈取色依赖系统动态取色（Android 12+），更低版本不可选且保持关闭
                SwitchPreference(
                    title = "莫奈取色",
                    summary = "从壁纸中提取主题色",
                    checked = monetEnable,
                    enabled = supportsDynamicColor,
                    onCheckedChange = { applyTheme(monet = it) },
                )
                if (supportsDynamicColor) {
                    // 仅在开启莫奈取色时提供配色选项
                    if (monetEnable) {
                        OverlayDropdownPreference(
                            title = "配色风格",
                            summary = "取色生成的色彩倾向",
                            items = paletteStyleLabels,
                            selectedIndex = paletteStyleIndex,
                            onSelectedIndexChange = { applyTheme(dStyle = paletteStyleIds[it]) },
                        )
                        // 浅色外观下也可以开关，设置会保留到深色模式生效
                        SwitchPreference(
                            title = "纯黑背景",
                            summary = "仅在深色模式下生效",
                            checked = pureBlackBackground,
                            enabled = true,
                            onCheckedChange = { applyTheme(pureBlack = it) },
                        )
                    }
                }
                SwitchPreference(
                    title = "悬浮底栏",
                    summary = "底栏以悬浮胶囊样式显示",
                    checked = floatingNavBar,
                    enabled = true,
                    onCheckedChange = { enabled ->
                        floatingNavBar = enabled
                        AppRepository.setFloatingNavBarEnabled(context, enabled)
                    },
                )
                // 悬浮底栏开启时显示特效选项；关闭后该行换成“隐藏分割线”（作用于停靠式导航栏）
                if (floatingNavBar) {
                    OverlayDropdownPreference(
                        title = "底栏效果",
                        summary = "调整底栏的材质与通透感",
                        items = navBarEffectLabels,
                        selectedIndex = navBarEffectIndex,
                        enabled = navBarEffectEnabled,
                        onSelectedIndexChange = { index ->
                            navBarEffect = index
                            AppRepository.setNavBarEffect(context, index)
                        },
                    )
                } else {
                    SwitchPreference(
                        title = "隐藏分割线",
                        summary = "隐藏导航栏顶部的分割线",
                        checked = hideNavBarDivider,
                        enabled = true,
                        onCheckedChange = { enabled ->
                            hideNavBarDivider = enabled
                            AppRepository.setNavBarDividerHidden(context, enabled)
                        },
                    )
                }
                // 高级材质依赖运行时着色器（Android 13+），更低版本固定关闭且不可选
                SwitchPreference(
                    title = "高级材质",
                    summary = "顶栏使用玻璃质感，关闭后为纯色",
                    checked = advancedMaterial,
                    enabled = supportsNavBarEffect,
                    onCheckedChange = { enabled ->
                        advancedMaterial = enabled
                        AppRepository.setAdvancedMaterial(context, enabled)
                    },
                )
            }

            SettingsSectionHeader("通用")

            Card(modifier = Modifier.fillMaxWidth()) {
                SliderPreference(
                    value = searchThreads.toFloat(),
                    onValueChange = {
                        searchThreads = it.roundToInt().coerceIn(
                            AppRepository.MIN_SEARCH_THREADS,
                            AppRepository.MAX_SEARCH_THREADS,
                        )
                    },
                    onValueChangeFinished = {
                        AppRepository.setSearchThreads(context, searchThreads)
                    },
                    title = "搜索线程",
                    summary = "同时搜索的片源数，过高易卡顿",
                    valueText = searchThreads.toString(),
                    onClick = {},
                    valueRange = AppRepository.MIN_SEARCH_THREADS.toFloat()..
                            AppRepository.MAX_SEARCH_THREADS.toFloat(),
                    steps = AppRepository.MAX_SEARCH_THREADS - AppRepository.MIN_SEARCH_THREADS - 1,
                    showKeyPoints = true,
                    keyPoints = SearchThreadKeyPoints,
                    magnetThreshold = 0.4f,
                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                )
            }

            SettingsSectionHeader("播放")

            Card(modifier = Modifier.fillMaxWidth()) {
                SliderPreference(
                    value = longPressSpeed,
                    onValueChange = {
                        longPressSpeed = ((it * 4).roundToInt() / 4f).coerceIn(0.25f, 5.0f)
                    },
                    onValueChangeFinished = {
                        GlobalPlayerConfig.setLongPressSpeed(context, longPressSpeed)
                    },
                    title = "长按倍速",
                    summary = "长按播放界面时的临时倍速",
                    valueText = "${"%.2f".format(longPressSpeed)}x",
                    onClick = {},
                    valueRange = 0.25f..5.0f,
                    steps = 18,
                    showKeyPoints = true,
                    keyPoints = SPEED_KEY_POINTS,
                    magnetThreshold = 0.03f,
                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                )
                OverlayDropdownPreference(
                    title = "播放顺序",
                    summary = "剧集播放结束后的续播方式",
                    items = PlaybackOrder.entries.map { it.label },
                    selectedIndex = PlaybackOrder.entries
                        .indexOf(GlobalPlayerConfig.playbackOrder)
                        .coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        GlobalPlayerConfig.setPlaybackOrder(context, PlaybackOrder.entries[index])
                    },
                )
                OverlayDropdownPreference(
                    title = "解码方式",
                    summary = "硬解更省电，软解更兼容",
                    items = DecoderMode.entries.map { it.label },
                    selectedIndex = DecoderMode.entries
                        .indexOf(GlobalPlayerConfig.decoderMode)
                        .coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        GlobalPlayerConfig.setDecoderMode(context, DecoderMode.entries[index])
                    },
                )
                SwitchPreference(
                    title = "后台播放",
                    summary = "切到后台后继续播放声音",
                    checked = GlobalPlayerConfig.backgroundPlayback,
                    onCheckedChange = {
                        GlobalPlayerConfig.setBackgroundPlayback(context, it)
                    },
                )
                SwitchPreference(
                    title = "过滤广告",
                    summary = "自动过滤播放源中的广告分片",
                    checked = GlobalPlayerConfig.adsFilter,
                    onCheckedChange = {
                        GlobalPlayerConfig.setAdsFilter(context, it)
                    },
                )
            }

            SettingsSectionHeader("其他")

            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "缓存管理",
                    summary = "查看占用并清理缓存文件",
                    onClick = onNavigateToCacheManage,
                )
                ArrowPreference(
                    title = "通知管理",
                    summary = "前往系统设置管理应用通知",
                    onClick = { openNotificationSettings(context) },
                )
                ArrowPreference(
                    title = "权限管理",
                    summary = "查看并授予应用所需权限",
                    onClick = { openPermissionManager(context) },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

internal fun openNotificationSettings(context: Context) {
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

