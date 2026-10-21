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

    MiuixTheme(controller = controller, content = content)
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
