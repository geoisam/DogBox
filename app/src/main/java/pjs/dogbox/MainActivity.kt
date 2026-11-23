package pjs.dogbox

import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
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
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
import pjs.dogbox.data.repository.AppRepository
import pjs.dogbox.ui.navigation.BaseScreen
import pjs.dogbox.ui.screens.SplashScreen
import pjs.dogbox.ui.screens.movie.GlobalPlayerConfig
import pjs.dogbox.ui.theme.DogBoxTheme
import pjs.dogbox.ui.theme.resolveDarkTheme
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

class MainActivity : ComponentActivity() {
    // 系统级兜底回调：清单已显式声明 enableOnBackInvokedCallback="false" 关闭预测性返回。
    // 若未来系统版本移除该清单豁免并强制开启预测性返回，此常驻注册的回调会让系统
    // 始终判定"返回由应用自行处理"，从而永不播放任何预测性返回动画
    // （back-to-home / cross-activity / cross-task），返回逻辑经
    // OnBackPressedDispatcher 原样分发给各页面的 BackHandler。
    private val backInvokedCallback by lazy {
        object : OnBackInvokedCallback {
            override fun onBackInvoked() {
                // 优先触发当前页面的 BackHandler；无启用项时按默认逻辑结束 Activity
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        disablePredictiveBack()
        AppRepository.resetAutoCheckDone()
        GlobalPlayerConfig.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            val context = this@MainActivity
            var themeMode by remember { mutableIntStateOf(AppRepository.getThemeMode(context)) }
            var dynamicColor by remember { mutableStateOf(AppRepository.getDynamicColor(context)) }
            var dynamicColorStyle by remember {
                mutableStateOf(
                    AppRepository.getDynamicColorStyle(
                        context
                    )
                )
            }
            var pureBlackBackground by remember {
                mutableStateOf(AppRepository.isPureBlackBackground(context))
            }
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
                pureBlackBackground = pureBlackBackground,
            ) {
                // 顶层 MiuixScaffold 仅用于提供 Overlay 弹窗所需的 MiuixPopupHost。
                // contentWindowInsets 置零，避免外层再消费一次 systemBars inset
                //（否则内层顶栏会重复叠加状态栏高度，出现双重 inset）。
                if (showSplash) {
                    SplashScreen(onTimeout = { showSplash = false })
                } else {
                    MiuixScaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    ) { _ ->
                        BaseScreen(
                            onThemeChanged = { mode, dynamic, dStyle, pureBlack ->
                                themeMode = mode
                                dynamicColor = dynamic
                                dynamicColorStyle = dStyle
                                pureBlackBackground = pureBlack
                            }
                        )
                    }
                }
            }
        }
    }

    /** 全面禁用预测性返回：注册系统级返回回调，保证返回手势永远走应用自身处理路径 */
    private fun disablePredictiveBack() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            backInvokedCallback
        )
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backInvokedCallback)
        }
        super.onDestroy()
    }
}
