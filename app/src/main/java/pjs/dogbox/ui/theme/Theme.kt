package pjs.dogbox.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.theme.platformDynamicColors


@Composable
fun DogBoxTheme(
    dynamicColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: Int = -1,
    dynamicColorStyle: String = "TonalSpot",
    pureBlackBackground: Boolean = false,
    content: @Composable () -> Unit
) {
    val resolvedMode = resolveThemeMode(themeMode)
    val isDark = resolveDarkTheme(themeMode, darkTheme)

    val mode = when {
        dynamicColor && resolvedMode == 1 -> ColorSchemeMode.MonetLight
        dynamicColor && resolvedMode == 2 -> ColorSchemeMode.MonetDark
        dynamicColor -> ColorSchemeMode.MonetSystem
        resolvedMode == 1 -> ColorSchemeMode.Light
        resolvedMode == 2 -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }


    // miuix 静态方案中 background 与卡片色(surfaceContainer)一致(浅色均为纯白、深色均为 #242424)，
    // 会造成 bottom sheet / 对话框内卡片与背景融为一体，这里手动拉开层次(与 Monet 行为一致)
    val staticLightColors = remember {
        lightColorScheme(background = Color(0xFFF7F7F7))
    }
    val staticDarkColors = remember {
        darkColorScheme(background = Color(0xFF181818))
    }

    val context = LocalContext.current
    val keyColor: Color? = if (dynamicColor) rememberWallpaperSeedColor(context, isDark) else null

    val paletteStyle = resolvePaletteStyle(dynamicColorStyle)

    val followSystem = resolvedMode == 0
    // ThemeController 字段不可变，设置变化时重建
    val controller = remember(mode, keyColor, paletteStyle, followSystem, isDark) {
        ThemeController(
            colorSchemeMode = mode,
            keyColor = keyColor,
            paletteStyle = paletteStyle,
            lightColors = staticLightColors,
            darkColors = staticDarkColors,
            isDark = if (followSystem) null else isDark,
        )
    }

    // 纯黑背景：仅“深色 + 莫奈取色”时生效，把窗口底色压到 #000000，卡片等容器保持原有层次
    val baseColors = controller.currentColors()
    val themeColors = if (pureBlackBackground && dynamicColor && isDark) {
        baseColors.copy(
            background = Color.Black,
            surface = Color.Black,
        )
    } else {
        baseColors
    }
    // colors 重载只提供配色，不提供 LocalContentColor / LocalColorSchemeMode；
    // 图标默认用 LocalContentColor 作 tint（默认黑色），若直接套用会让顶栏图标在全黑底上“消失”。
    // 这里始终先套 controller 版本补全这些 CompositionLocal，再在其内部覆盖配色。
    // 注意：不要根据纯黑开关切换组合层级，否则开关时整棵界面会被重建，
    // NavHost 会回到首页。始终保留相同的两层 MiuixTheme 结构。
    MiuixTheme(controller = controller) {
        MiuixTheme(colors = themeColors, content = content)
    }
}


fun resolveThemeMode(themeMode: Int): Int = when (themeMode) {
    1, 2, 0 -> themeMode
    else -> 0
}


fun resolveDarkTheme(themeMode: Int, isSystemDark: Boolean): Boolean = when (themeMode) {
    1 -> false
    2 -> true
    else -> isSystemDark
}


fun resolvePaletteStyle(style: String): ThemePaletteStyle = when (style) {
    "TonalSpot" -> ThemePaletteStyle.TonalSpot
    "Neutral" -> ThemePaletteStyle.Neutral
    "Vibrant" -> ThemePaletteStyle.Vibrant
    "Expressive" -> ThemePaletteStyle.Expressive
    "Rainbow" -> ThemePaletteStyle.Rainbow
    "FruitSalad" -> ThemePaletteStyle.FruitSalad
    "Monochrome" -> ThemePaletteStyle.Monochrome
    "Content" -> ThemePaletteStyle.Content
    "Fidelity" -> ThemePaletteStyle.Fidelity
    else -> ThemePaletteStyle.TonalSpot
}

@Composable
private fun rememberWallpaperSeedColor(context: Context, dark: Boolean): Color {
    val systemSeed = remember(context) { readSystemWallpaperSeedColor(context) }

    return systemSeed ?: platformDynamicColors(dark).primary
}


private fun readSystemWallpaperSeedColor(context: Context): Color? {
    return try {
        val json = Settings.Secure.getString(
            context.contentResolver,
            "theme_customization_overlay_packages",
        )
        if (json.isNullOrBlank()) return null
        val palette = JSONObject(json)
            .optString("android.theme.customization.system_palette", "")
        if (palette.isBlank()) return null
        val hex = if (palette.startsWith("#")) palette else "#$palette"
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        null
    }
}
