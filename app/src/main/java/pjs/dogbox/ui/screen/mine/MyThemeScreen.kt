package pjs.dogbox.ui.screen.mine

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pjs.dogbox.R
import pjs.dogbox.ui.theme.AppColorThemes
import pjs.dogbox.data.SharedData
import pjs.dogbox.ui.components.SimpleDragHandle

data class ColorStyle(
    val id: String,
    val name: String,
    val description: String,
)

val ColorStyles = listOf(
    ColorStyle("TonalSpot", "经典色调", "默认风格"),
    ColorStyle("Neutral", "柔和中性", "极致克制"),
    ColorStyle("Vibrant", "鲜艳活力", "高饱和度"),
    ColorStyle("Expressive", "生动表现", "撞色组合"),
    ColorStyle("Rainbow", "彩虹糖果", "色相遍历"),
    ColorStyle("FruitSalad", "缤纷果盘", "多彩高亮"),
    ColorStyle("Monochrome", "极简单色", "纯黑白灰"),
    ColorStyle("Fidelity", "真实还原", "原色追随"),
    ColorStyle("Content", "内容原色", "沉浸体验"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyThemeScreen(
    onBack: () -> Unit = {},
    onThemeChanged: (mode: Int, dynamic: Boolean, colorTheme: Int, dynamicStyle: String) -> Unit = { _, _, _, _ -> },
) {
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var themeMode by remember { mutableIntStateOf(SharedData.getThemeMode(context)) }
    var dynamicColor by remember {
        mutableStateOf(
            if (supportsDynamicColor) SharedData.getDynamicColor(context) else false
        )
    }
    var colorTheme by remember {
        mutableIntStateOf(
            if (supportsDynamicColor) SharedData.getColorTheme(context) else 1
        )
    }
    var dynamicColorStyle by remember { mutableStateOf(SharedData.getDynamicColorStyle(context)) }
    var showStyleSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun updateTheme(
        mode: Int = themeMode,
        dynamic: Boolean = dynamicColor,
        cTheme: Int = colorTheme,
        dStyle: String = dynamicColorStyle,
    ) {
        themeMode = mode
        dynamicColor = dynamic
        colorTheme = cTheme
        dynamicColorStyle = dStyle
        onThemeChanged(mode, dynamic, cTheme, dStyle)
        scope.launch(Dispatchers.IO) {
            SharedData.setThemeMode(context, mode)
            SharedData.setDynamicColor(context, dynamic)
            SharedData.setColorTheme(context, cTheme)
            SharedData.setDynamicColorStyle(context, dStyle)
        }
    }

    if (showStyleSheet) {
        ColorStyleBottomSheet(
            currentStyle = dynamicColorStyle,
            sheetState = sheetState,
            onStyleSelected = { style ->
                updateTheme(dStyle = style)
                showStyleSheet = false
            },
            onDismiss = { showStyleSheet = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("主题模式", fontWeight = FontWeight.Bold) },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "显示模式",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ThemeModeCard(
                    modifier = Modifier.weight(1f),
                    iconRes = R.drawable.contrast_outlined,
                    label = "跟随系统",
                    selected = themeMode == 0,
                    onClick = { updateTheme(mode = 0) },
                )
                ThemeModeCard(
                    modifier = Modifier.weight(1f),
                    iconRes = R.drawable.light_mode_outlined,
                    label = "浅色模式",
                    selected = themeMode == 1,
                    onClick = { updateTheme(mode = 1) },
                )
                ThemeModeCard(
                    modifier = Modifier.weight(1f),
                    iconRes = R.drawable.dark_mode_outlined,
                    label = "深色模式",
                    selected = themeMode == 2,
                    onClick = { updateTheme(mode = 2) },
                )
            }

            Text(
                text = "主题方案",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.colorize_outlined),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "莫奈取色",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "仅支持 Android12+ 系统版本",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = { updateTheme(dynamic = it) },
                        enabled = supportsDynamicColor,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStyleSheet = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.palette_outlined),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "主题风格",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "调用 MD3 调色板生成风格",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val currentStyleName =
                        ColorStyles.find { it.id == dynamicColorStyle }?.name ?: "经典色调"
                    Text(
                        text = currentStyleName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Icon(
                        painter = painterResource(R.drawable.arrow_drop_down_outlined),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val gridColors = AppColorThemes.map { theme ->
                val bgColor by animateColorAsState(
                    targetValue = theme.primary,
                    animationSpec = tween(200),
                )
                theme to bgColor
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((AppColorThemes.size + 1) / 2 * 96).dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
            ) {
                items(gridColors) { (theme, bgColor) ->
                    val isSelected = !dynamicColor && colorTheme == theme.id
                    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
                    else Color.Transparent

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2.2f)
                            .shadow(
                                elevation = if (isSelected) 4.dp else 0.dp,
                                shape = MaterialTheme.shapes.medium,
                            )
                            .clip(MaterialTheme.shapes.medium)
                            .background(bgColor)
                            .border(2.5.dp, borderColor, MaterialTheme.shapes.medium)
                            .clickable {
                                updateTheme(dynamic = false, cTheme = theme.id)
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = theme.name,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterStart),
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.9f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    color = bgColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(bgColor),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorStyleBottomSheet(
    currentStyle: String,
    sheetState: SheetState,
    onStyleSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SimpleDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {

            ColorStyles.forEach { style ->
                val isSelected = currentStyle == style.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStyleSelected(style.id) }
                        .then(
                            if (isSelected) Modifier.background(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.shapes.medium
                            ) else Modifier
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = style.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${style.id} · ${style.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isSelected) {
                        Icon(
                            painter = painterResource(R.drawable.check_outlined),
                            contentDescription = "已选择",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeModeCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier
            .border(2.dp, borderColor, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.size(32.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}
