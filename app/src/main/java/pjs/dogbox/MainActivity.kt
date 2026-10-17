package pjs.dogbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsControllerCompat
import pjs.dogbox.ui.navigation.BaseScreen
import pjs.dogbox.ui.screen.SplashScreen
import pjs.dogbox.ui.screen.movie.GlobalPlayerConfig
import pjs.dogbox.ui.theme.DogBoxTheme
import pjs.dogbox.ui.theme.resolveDarkTheme
import pjs.dogbox.data.SharedData
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        SharedData.resetAutoCheckDone()
        GlobalPlayerConfig.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            val context = this@MainActivity
            var themeMode by remember { mutableIntStateOf(SharedData.getThemeMode(context)) }
            var dynamicColor by remember { mutableStateOf(SharedData.getDynamicColor(context)) }
            var dynamicColorStyle by remember { mutableStateOf(SharedData.getDynamicColorStyle(context)) }
            val isSystemDark = isSystemInDarkTheme()
            var showSplash by remember { mutableStateOf(true) }

            // 与 DogBoxTheme.resolveDarkTheme 同源，避免主题与状态栏明暗打架
            // （参考 lyricon AppTheme: isAppearanceLightStatusBars = !colors.isDark）
            val darkTheme = resolveDarkTheme(themeMode, isSystemDark)

            LaunchedEffect(darkTheme, showSplash) {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                // 开屏为深色背景，固定用浅色图标；进入主界面后跟随主题
                controller.isAppearanceLightStatusBars = if (showSplash) false else !darkTheme
                controller.isAppearanceLightNavigationBars = if (showSplash) false else !darkTheme
            }

            DogBoxTheme(
                dynamicColor = dynamicColor,
                darkTheme = isSystemDark,
                themeMode = themeMode,
                dynamicColorStyle = dynamicColorStyle,
            ) {
                // 顶层 MiuixScaffold 仅用于提供 Overlay 弹窗所需的 MiuixPopupHost。
                // contentWindowInsets 置零，避免外层再消费一次 systemBars inset
                //（否则内层 SmallTopAppBar 会重复叠加状态栏高度，出现双重 inset）。
                if (showSplash) {
                    SplashScreen(onTimeout = { showSplash = false })
                } else {
                    MiuixScaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    ) { _ ->
                        BaseScreen(
                            onThemeChanged = { mode, dynamic, dStyle ->
                                themeMode = mode
                                dynamicColor = dynamic
                                dynamicColorStyle = dStyle
                            }
                        )
                    }
                }
            }
        }
    }
}
