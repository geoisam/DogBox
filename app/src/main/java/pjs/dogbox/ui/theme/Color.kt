package pjs.dogbox.ui.theme

import androidx.compose.ui.graphics.Color

// MD3 Dark scheme (fallback when dynamic color unavailable)
val DarkPrimary = Color(0xFFD0BCFF)
val DarkOnPrimary = Color(0xFF381E72)
val DarkPrimaryContainer = Color(0xFF4F378B)
val DarkOnPrimaryContainer = Color(0xFFEADDFF)
val DarkSecondary = Color(0xFFCCC2DC)
val DarkOnSecondary = Color(0xFF332D41)
val DarkSecondaryContainer = Color(0xFF4A4458)
val DarkOnSecondaryContainer = Color(0xFFE8DEF8)
val DarkTertiary = Color(0xFFEFB8C8)
val DarkOnTertiary = Color(0xFF492532)
val DarkTertiaryContainer = Color(0xFF633B48)
val DarkOnTertiaryContainer = Color(0xFFFFD8E4)
val DarkError = Color(0xFFF2B8B5)
val DarkOnError = Color(0xFF601410)
val DarkErrorContainer = Color(0xFF8C1D18)
val DarkOnErrorContainer = Color(0xFFF9DEDC)
val DarkBackground = Color(0xFF121218)
val DarkOnBackground = Color(0xFFE6E1E5)
val DarkSurface = Color(0xFF1C1B1F)
val DarkOnSurface = Color(0xFFE6E1E5)
val DarkSurfaceVariant = Color(0xFF49454F)
val DarkOnSurfaceVariant = Color(0xFFCAC4D0)
val DarkOutline = Color(0xFF938F99)
val DarkOutlineVariant = Color(0xFF49454F)
val DarkSurfaceTint = Color(0xFFD0BCFF)
val DarkInverseSurface = Color(0xFFE6E1E5)
val DarkInverseOnSurface = Color(0xFF313033)
val DarkInversePrimary = Color(0xFF6750A4)
val DarkSurfaceDim = Color(0xFF1C1B1F)
val DarkSurfaceBright = Color(0xFF413F45)
val DarkSurfaceContainerLow = Color(0xFF201F23)
val DarkSurfaceContainer = Color(0xFF242328)
val DarkSurfaceContainerHigh = Color(0xFF2F2E33)
val DarkSurfaceContainerHighest = Color(0xFF3A383E)

// MD3 Light scheme (fallback when dynamic color unavailable)
val LightPrimary = Color(0xFF6750A4)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFEADDFF)
val LightOnPrimaryContainer = Color(0xFF21005D)
val LightSecondary = Color(0xFF625B71)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE8DEF8)
val LightOnSecondaryContainer = Color(0xFF1D192B)
val LightTertiary = Color(0xFF7D5260)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF9DEDC)
val LightOnTertiaryContainer = Color(0xFF410E0B)
val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFD8E4)
val LightOnErrorContainer = Color(0xFF31111D)
val LightBackground = Color(0xFFFFFBFE)
val LightOnBackground = Color(0xFF1C1B1F)
val LightSurface = Color(0xFFFFFBFE)
val LightOnSurface = Color(0xFF1C1B1F)
val LightSurfaceVariant = Color(0xFFE7E0EC)
val LightOnSurfaceVariant = Color(0xFF49454F)
val LightOutline = Color(0xFF79747E)
val LightOutlineVariant = Color(0xFFCAC4D0)
val LightSurfaceTint = Color(0xFF6750A4)
val LightInverseSurface = Color(0xFF313033)
val LightInverseOnSurface = Color(0xFFF4EFF4)
val LightInversePrimary = Color(0xFFD0BCFF)
val LightSurfaceDim = Color(0xFFDED8E1)
val LightSurfaceBright = Color(0xFFFFFBFE)
val LightSurfaceContainerLow = Color(0xFFF7F2FA)
val LightSurfaceContainer = Color(0xFFF3EDF7)
val LightSurfaceContainerHigh = Color(0xFFECE6F0)
val LightSurfaceContainerHighest = Color(0xFFE6E0E9)

// Theme color definitions
data class AppColorTheme(
    val id: Int,
    val name: String,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
)

val AppColorThemes = listOf(
    AppColorTheme(1, "基佬紫",
        primary = Color(0xFF6750A4), onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D),
        darkPrimary = Color(0xFFD0BCFF), darkOnPrimary = Color(0xFF381E72),
        darkPrimaryContainer = Color(0xFF4F378B), darkOnPrimaryContainer = Color(0xFFEADDFF),
    ),
    AppColorTheme(2, "雀巢棕",
        primary = Color(0xFF63513D), onPrimary = Color.White,
        primaryContainer = Color(0xFFE8DFD0), onPrimaryContainer = Color(0xFF1F1609),
        darkPrimary = Color(0xFFD4C5B2), darkOnPrimary = Color(0xFF362A19),
        darkPrimaryContainer = Color(0xFF4E4030), darkOnPrimaryContainer = Color(0xFFE8DFD0),
    ),
    AppColorTheme(3, "苹果灰",
        primary = Color(0xFFA5AEB7), onPrimary = Color.White,
        primaryContainer = Color(0xFFD8DFE5), onPrimaryContainer = Color(0xFF1C2128),
        darkPrimary = Color(0xFFC5CCD3), darkOnPrimary = Color(0xFF2B323A),
        darkPrimaryContainer = Color(0xFF3C444C), darkOnPrimaryContainer = Color(0xFFD8DFE5),
    ),
    AppColorTheme(4, "小米橙",
        primary = Color(0xFFFF6700), onPrimary = Color.Black,
        primaryContainer = Color(0xFFFFDBCF), onPrimaryContainer = Color(0xFF360A00),
        darkPrimary = Color(0xFFFFB598), darkOnPrimary = Color(0xFF5C1900),
        darkPrimaryContainer = Color(0xFF7D3014), darkOnPrimaryContainer = Color(0xFFFFDBCF),
    ),
    AppColorTheme(5, "豆瓣绿",
        primary = Color(0xFF2BA245), onPrimary = Color.White,
        primaryContainer = Color(0xFFB7F3BD), onPrimaryContainer = Color(0xFF002110),
        darkPrimary = Color(0xFF8AD891), darkOnPrimary = Color(0xFF00391A),
        darkPrimaryContainer = Color(0xFF005329), darkOnPrimaryContainer = Color(0xFFB7F3BD),
    ),
    AppColorTheme(6, "哔哩粉",
        primary = Color(0xFFFB7299), onPrimary = Color.Black,
        primaryContainer = Color(0xFFFFD9E2), onPrimaryContainer = Color(0xFF3E001E),
        darkPrimary = Color(0xFFFFB0CB), darkOnPrimary = Color(0xFF5E1133),
        darkPrimaryContainer = Color(0xFF7C1B44), darkOnPrimaryContainer = Color(0xFFFFD9E2),
    ),
    AppColorTheme(7, "知乎蓝",
        primary = Color(0xFF1772F6), onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF001B3E),
        darkPrimary = Color(0xFFA9C7FF), darkOnPrimary = Color(0xFF003063),
        darkPrimaryContainer = Color(0xFF00468C), darkOnPrimaryContainer = Color(0xFFD6E3FF),
    ),
    AppColorTheme(8, "网易红",
        primary = Color(0xFFFF0F23), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDAD6), onPrimaryContainer = Color(0xFF410002),
        darkPrimary = Color(0xFFFFB3AF), darkOnPrimary = Color(0xFF680008),
        darkPrimaryContainer = Color(0xFF93000F), darkOnPrimaryContainer = Color(0xFFFFDAD6),
    ),
    AppColorTheme(9, "美团黄",
        primary = Color(0xFFFFD100), onPrimary = Color.Black,
        primaryContainer = Color(0xFFFFF0C2), onPrimaryContainer = Color(0xFF3D3000),
        darkPrimary = Color(0xFFE5B800), darkOnPrimary = Color(0xFF3D3000),
        darkPrimaryContainer = Color(0xFF5A4700), darkOnPrimaryContainer = Color(0xFFFFF0C2),
    ),
    AppColorTheme(10, "得物青",
        primary = Color(0xFF01C2C3), onPrimary = Color.Black,
        primaryContainer = Color(0xFFB3F0F0), onPrimaryContainer = Color(0xFF003636),
        darkPrimary = Color(0xFF62D5D6), darkOnPrimary = Color(0xFF003A3A),
        darkPrimaryContainer = Color(0xFF005252), darkOnPrimaryContainer = Color(0xFFB3F0F0),
    ),
    AppColorTheme(11, "夸克靛",
        primary = Color(0xFF0D53FF), onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E0FF), onPrimaryContainer = Color(0xFF00184C),
        darkPrimary = Color(0xFFADC1FF), darkOnPrimary = Color(0xFF002D80),
        darkPrimaryContainer = Color(0xFF0042B5), darkOnPrimaryContainer = Color(0xFFD6E0FF),
    ),
    AppColorTheme(12, "抖音黑",
        primary = Color(0xFF191B24), onPrimary = Color.White,
        primaryContainer = Color(0xFFC4C6D0), onPrimaryContainer = Color(0xFF12131A),
        darkPrimary = Color(0xFFB0B3BD), darkOnPrimary = Color(0xFF20222B),
        darkPrimaryContainer = Color(0xFF363842), darkOnPrimaryContainer = Color(0xFFC4C6D0),
    ),
)