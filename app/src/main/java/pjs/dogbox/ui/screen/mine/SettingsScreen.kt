package pjs.dogbox.ui.screen.mine

import pjs.dogbox.utils.openPermissionManager

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import pjs.dogbox.R
import pjs.dogbox.ui.screen.movie.GlobalPlayerConfig
import pjs.dogbox.ui.screen.movie.PlaybackOrder
import pjs.dogbox.data.SharedData
import pjs.dogbox.ui.components.SimpleDragHandle
import pjs.dogbox.ui.components.SheetSectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onNavigateToCacheManage: () -> Unit = {},
) {
    val context = LocalContext.current

    var downloadMode by remember { mutableIntStateOf(SharedData.getDownloadMode(context)) }
    var showSpeedSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text("设置", fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_outlined),
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSectionHeader("通用")

            SettingsSectionCard {
                SettingsRowItem(
                    title = "主题模式",
                    onClick = onNavigateToTheme,
                )
                SettingsMenuRow(
                    title = "下载方式",
                    valueText = if (downloadMode == 0) "内置下载器" else "系统下载器",
                    options = listOf("内置下载器", "系统下载器"),
                    selectedIndex = downloadMode,
                    onSelect = { mode ->
                        downloadMode = mode
                        SharedData.setDownloadMode(context, mode)
                    },
                )
                SettingsRowItem(
                    title = "缓存管理",
                    onClick = onNavigateToCacheManage,
                )
            }

            SettingsSectionHeader("播放")

            SettingsSectionCard {
                SettingsRowItem(
                    title = "长按倍速",
                    valueText = "${"%.2f".format(GlobalPlayerConfig.longPressSpeed)}x",
                    onClick = { showSpeedSheet = true },
                )
                SettingsMenuRow(
                    title = "播放顺序",
                    valueText = GlobalPlayerConfig.playbackOrder.label,
                    options = PlaybackOrder.entries.map { it.label },
                    selectedIndex = PlaybackOrder.entries.indexOf(GlobalPlayerConfig.playbackOrder),
                    onSelect = { index ->
                        GlobalPlayerConfig.setPlaybackOrder(context, PlaybackOrder.entries[index])
                    },
                )
                SettingsSwitchRow(
                    title = "后台播放",
                    checked = GlobalPlayerConfig.backgroundPlayback,
                    onCheckedChange = {
                        GlobalPlayerConfig.setBackgroundPlayback(context, it)
                    },
                )
                SettingsSwitchRow(
                    title = "过滤广告",
                    checked = GlobalPlayerConfig.adsFilter,
                    onCheckedChange = {
                        GlobalPlayerConfig.setAdsFilter(context, it)
                    },
                )
            }

            SettingsSectionHeader("其他")

            SettingsSectionCard {
                SettingsRowItem(
                    title = "通知管理",
                    onClick = { openNotificationSettings(context) },
                )
                SettingsRowItem(
                    title = "权限管理",
                    onClick = { openPermissionManager(context) },
                )
            }
        }
    }

    if (showSpeedSheet) {
        LongPressSpeedBottomSheet(
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SettingsSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRowValue(valueText: String?) {
    if (valueText == null) return
    Text(
        text = valueText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.size(6.dp))
}

@Composable
private fun SettingsChevronIcon() {
    Icon(
        painter = painterResource(R.drawable.keyboard_arrow_right_outlined),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsRowItem(
    title: String,
    valueText: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SettingsRowValue(valueText)
        SettingsChevronIcon()
    }
}

@Composable
private fun SettingsMenuRow(
    title: String,
    valueText: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SettingsRowValue(valueText)
        Box(contentAlignment = Alignment.Center) {
            SettingsChevronIcon()
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                color = if (index == selectedIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LongPressSpeedBottomSheet(
    currentSpeed: Float,
    onSpeedConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sliderSpeed by remember(currentSpeed) { mutableStateOf(currentSpeed) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SimpleDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            SheetSectionTitle("长按倍速")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "自定义倍速",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${"%.2f".format(sliderSpeed)}x",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = sliderSpeed,
                onValueChange = { sliderSpeed = (it * 4).toInt() / 4f },
                onValueChangeFinished = { onSpeedConfirm(sliderSpeed) },
                valueRange = 0.25f..5.0f,
                steps = 18,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            sliderSpeed = 3.0f
                            onSpeedConfirm(3.0f)
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "恢复默认",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
