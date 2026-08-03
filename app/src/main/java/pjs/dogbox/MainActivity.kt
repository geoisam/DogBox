package pjs.dogbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowInsetsControllerCompat
import pjs.dogbox.ui.navigation.BaseScreen
import pjs.dogbox.ui.screen.SplashScreen
import pjs.dogbox.ui.theme.DogBoxTheme
import pjs.dogbox.util.SharedData

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = this@MainActivity
            var themeMode by remember { mutableIntStateOf(SharedData.getThemeMode(context)) }
            var dynamicColor by remember { mutableStateOf(SharedData.getDynamicColor(context)) }
            var colorTheme by remember { mutableIntStateOf(SharedData.getColorTheme(context)) }
            var dynamicColorStyle by remember { mutableStateOf(SharedData.getDynamicColorStyle(context)) }
            val isSystemDark = isSystemInDarkTheme()
            var showSplash by remember { mutableStateOf(true) }

            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemDark
            }

            LaunchedEffect(darkTheme, showSplash) {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = if (showSplash) false else !darkTheme
                controller.isAppearanceLightNavigationBars = if (showSplash) false else !darkTheme
            }

            DogBoxTheme(
                dynamicColor = dynamicColor,
                darkTheme = darkTheme,
                colorThemeId = colorTheme,
                dynamicColorStyle = dynamicColorStyle,
            ) {
                if (showSplash) {
                    SplashScreen(onTimeout = { showSplash = false })
                } else {
                    BaseScreen(
                        onThemeChanged = { mode, dynamic, cTheme, dStyle ->
                            themeMode = mode
                            dynamicColor = dynamic
                            colorTheme = cTheme
                            dynamicColorStyle = dStyle
                        }
                    )
                }
            }
        }
    }
}
