package pjs.dogbox.ui.screen.mine

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import pjs.dogbox.BuildConfig
import pjs.dogbox.R
import pjs.dogbox.data.AppData
import pjs.dogbox.data.SharedData
import pjs.dogbox.data.fetchRemoteConfig
import pjs.dogbox.data.parseUpdateInfo
import pjs.dogbox.ui.components.DisclaimerDialog
import pjs.dogbox.ui.components.UpdateBottomSheet
import pjs.dogbox.utils.openPermissionManager
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText

private enum class UpdateCheckState {
    NOT_CHECKED, CHECKING, HAS_UPDATE, UP_TO_DATE, CHECK_FAILED
}

@Composable
fun AboutMeScreen(
    onBack: () -> Unit = {},
    onNavigateToContributor: () -> Unit = {},
    onNavigateToLicense: () -> Unit = {},
    onNavigateToChangeLog: () -> Unit = {},
) {
    val context = LocalContext.current
    val scienceGothic = FontFamily(Font(R.font.science_gothic))
    val bgColor = colorResource(R.color.icon_background)
    val fgColor = colorResource(R.color.icon_foreground)
    val scope = rememberCoroutineScope()

    var autoCheckUpdate by remember { mutableStateOf(SharedData.isAutoCheckUpdate(context)) }
    val cachedInfo = SharedData.getCachedUpdateInfo()
    val upToDate = SharedData.isCachedUpToDate()
    val checkFailed = SharedData.isCachedCheckFailed()
    var manualOverride by remember { mutableStateOf<UpdateCheckState?>(null) }
    val checkState = manualOverride ?: when {
        cachedInfo != null -> UpdateCheckState.HAS_UPDATE
        upToDate -> UpdateCheckState.UP_TO_DATE
        checkFailed -> UpdateCheckState.CHECK_FAILED
        else -> UpdateCheckState.NOT_CHECKED
    }
    val checking = checkState == UpdateCheckState.CHECKING
    var latestVersion by remember { mutableStateOf(cachedInfo?.versionName ?: "") }
    var updateNote by remember { mutableStateOf(cachedInfo?.note ?: "") }
    var updateDown by remember { mutableStateOf(cachedInfo?.downloadUrl ?: AppData.GITHUB_PAGE) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showDisclaimerDialog by remember { mutableStateOf(false) }

    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = "关于应用",
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
                    MiuixIconButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                AppData.GITHUB_PAGE.toUri()
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        MiuixIcon(
                            painter = painterResource(R.drawable.github_logo),
                            contentDescription = "GitHub",
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Card(
                    modifier = Modifier.size(100.dp),
                    cornerRadius = 28.dp,
                    colors = CardDefaults.defaultColors(color = bgColor),
                    onClick = {
                        try {
                            val intent = Intent().apply {
                                component = ComponentName(
                                    "com.android.settings",
                                    $$"com.android.settings.Settings$AppDashboardActivity"
                                )
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
                        }
                    },
                    onLongPress = {
                        try {
                            val intent = Intent().apply {
                                component = ComponentName(
                                    "com.android.settings.intelligence",
                                    $$"com.android.settings.intelligence.search.SearchActivity"
                                )
                            }
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            context.startActivity(Intent(Settings.ACTION_SEARCH_SETTINGS))
                        }
                    },
                ) {
                    MiuixIcon(
                        painter = painterResource(R.drawable.dogbox_logo_fill),
                        tint = fgColor,
                        contentDescription = "LOGO",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                MiuixText(
                    text = stringResource(R.string.app_name),
                    fontFamily = scienceGothic,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                MiuixText(
                    text = "version ${BuildConfig.VERSION_NAME}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !checking) {
                                val cached = SharedData.getCachedUpdateInfo()
                                if (cached != null) {
                                    latestVersion = cached.versionName
                                    updateNote = cached.note
                                    updateDown = cached.downloadUrl
                                    showUpdateDialog = true
                                } else {
                                    scope.launch {
                                        manualOverride = UpdateCheckState.CHECKING
                                        val configJson = fetchRemoteConfig()
                                        val updateInfo = parseUpdateInfo(configJson)
                                        if (updateInfo != null && updateInfo.code > BuildConfig.VERSION_CODE) {
                                            latestVersion = updateInfo.ver
                                            updateNote = updateInfo.note
                                            updateDown = updateInfo.down
                                            SharedData.setCachedUpdateInfo(
                                                SharedData.CachedUpdateInfo(
                                                    versionCode = updateInfo.code,
                                                    versionName = updateInfo.ver,
                                                    note = updateInfo.note,
                                                    downloadUrl = updateInfo.down,
                                                )
                                            )
                                            SharedData.setCachedUpToDate(false)
                                            SharedData.setCachedCheckFailed(false)
                                            manualOverride = null
                                            showUpdateDialog = true
                                        } else if (updateInfo != null && updateInfo.code <= BuildConfig.VERSION_CODE) {
                                            SharedData.setCachedUpdateInfo(null)
                                            SharedData.setCachedUpToDate(true)
                                            SharedData.setCachedCheckFailed(false)
                                            manualOverride = null
                                            Toast.makeText(
                                                context,
                                                "已是最新版本",
                                                Toast.LENGTH_SHORT
                                            )
                                                .show()
                                        } else {
                                            SharedData.setCachedUpdateInfo(null)
                                            SharedData.setCachedUpToDate(false)
                                            SharedData.setCachedCheckFailed(true)
                                            manualOverride = null
                                            Toast.makeText(
                                                context,
                                                "更新检测失败",
                                                Toast.LENGTH_SHORT
                                            )
                                                .show()
                                        }
                                    }
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiuixText(
                            text = "版本更新",
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        when (checkState) {
                            UpdateCheckState.HAS_UPDATE -> {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MiuixTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                ) {
                                    MiuixText(
                                        text = "有新版本",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }

                            UpdateCheckState.UP_TO_DATE -> MiuixText(
                                text = "已是最新版本",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.padding(end = 4.dp),
                            )

                            UpdateCheckState.CHECK_FAILED -> MiuixText(
                                text = "检测失败",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.padding(end = 4.dp),
                            )

                            UpdateCheckState.CHECKING -> MiuixText(
                                text = "检测中...",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.padding(end = 4.dp),
                            )

                            UpdateCheckState.NOT_CHECKED -> MiuixText(
                                text = "尚未检测",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                        val infiniteTransition = rememberInfiniteTransition(label = "rotate")
                        val rotateAngle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "rotateAngle",
                        )
                        MiuixIcon(
                            painter = painterResource(R.drawable.autorenew_outlined),
                            contentDescription = null,
                            modifier = Modifier
                                .size(20.dp)
                                .then(
                                    if (checking) Modifier.graphicsLayer { rotationZ = rotateAngle }
                                    else Modifier
                                ),
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                    SwitchPreference(
                        title = "自动检测更新",
                        summary = "每次启动时检测是否有更新",
                        checked = autoCheckUpdate,
                        onCheckedChange = {
                            autoCheckUpdate = it
                            SharedData.setAutoCheckUpdate(context, it)
                        },
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(title = "更新日志", onClick = onNavigateToChangeLog)
                    ArrowPreference(title = "特别鸣谢", onClick = onNavigateToContributor)
                    ArrowPreference(title = "免责声明", onClick = { showDisclaimerDialog = true })
                    ArrowPreference(
                        title = "权限清单",
                        onClick = { openPermissionManager(context) })
                    ArrowPreference(title = "开源许可", onClick = onNavigateToLicense)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    DisclaimerDialog(
        isOpen = showDisclaimerDialog,
        onDismiss = { showDisclaimerDialog = false },
    )

    UpdateBottomSheet(
        isOpen = showUpdateDialog,
        onDismiss = { showUpdateDialog = false },
        latestVersion = latestVersion,
        updateNote = updateNote,
        downloadUrl = updateDown,
    )
}
