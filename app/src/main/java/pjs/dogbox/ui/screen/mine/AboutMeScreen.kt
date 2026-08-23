package pjs.dogbox.ui.screen.mine

import pjs.dogbox.utils.openPermissionManager

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import pjs.dogbox.ui.components.DisclaimerDialog
import pjs.dogbox.data.SharedData
import pjs.dogbox.ui.components.UpdateBottomSheet
import pjs.dogbox.data.fetchRemoteConfig
import pjs.dogbox.data.parseUpdateInfo

private enum class UpdateCheckState {
    NOT_CHECKED, CHECKING, HAS_UPDATE, UP_TO_DATE, CHECK_FAILED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutMeScreen(
    onBack: () -> Unit = {},
    onNavigateToContributor: () -> Unit = {},
    onNavigateToLicense: () -> Unit = {},
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
    var updateDown by remember { mutableStateOf(cachedInfo?.downloadUrl ?: AppData.GITHUB_REPO) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showDisclaimerDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("关于应用", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_outlined),
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                "https://github.com/geoisam/DogBox".toUri()
                            )
                            context.startActivity(intent)
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.github_logo),
                            contentDescription = "GitHub",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

                Column(
                    modifier = Modifier.fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Card(
                        modifier = Modifier.size(100.dp)
                            .combinedClickable(
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
                                onLongClick = {
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
                            ),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = bgColor,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.dogbox_logo_fill),
                            tint = fgColor,
                            contentDescription = "LOGO",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        fontFamily = scienceGothic,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
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
                                        Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT)
                                            .show()
                                    } else {
                                        SharedData.setCachedUpdateInfo(null)
                                        SharedData.setCachedUpToDate(false)
                                        SharedData.setCachedCheckFailed(true)
                                        manualOverride = null
                                        Toast.makeText(context, "更新检测失败", Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                }
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "版本更新",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    when (checkState) {
                        UpdateCheckState.HAS_UPDATE -> {
                            Box(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "有新版本",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }

                        UpdateCheckState.UP_TO_DATE -> Text(
                            text = "已是最新版本",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )

                        UpdateCheckState.CHECK_FAILED -> Text(
                            text = "检测失败",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )

                        UpdateCheckState.CHECKING -> Text(
                            text = "检测中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )

                        UpdateCheckState.NOT_CHECKED -> Text(
                            text = "尚未检测",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Icon(
                        painter = painterResource(R.drawable.autorenew_outlined),
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .then(
                                if (checking) Modifier.graphicsLayer { rotationZ = rotateAngle }
                                else Modifier
                            ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自动检测更新",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "每次启动时检测是否有更新",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoCheckUpdate,
                        onCheckedChange = {
                            autoCheckUpdate = it
                            SharedData.setAutoCheckUpdate(context, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }

            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AboutListItem(
                        text = "历史版本",
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                "https://share.feijipan.com/s/f9dwRFYe".toUri()
                            )
                            context.startActivity(intent)
                        }
                    )
                    AboutListItem(
                        text = "更新记录",
                        onClick = {}
                    )
                    AboutListItem(
                        text = "特别鸣谢",
                        onClick = onNavigateToContributor
                    )
                    AboutListItem(
                        text = "免责声明",
                        onClick = { showDisclaimerDialog = true }
                    )
                    AboutListItem(
                        text = "权限清单",
                        onClick = { openPermissionManager(context) }
                    )
                    AboutListItem(
                        text = "开源许可",
                        onClick = onNavigateToLicense
                    )
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


@Composable
fun AboutListItem(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            painter = painterResource(R.drawable.keyboard_arrow_right_outlined),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

