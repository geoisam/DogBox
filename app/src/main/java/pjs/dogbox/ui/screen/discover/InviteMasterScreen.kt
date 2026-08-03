package pjs.dogbox.ui.screen.discover

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import pjs.dogbox.ui.screen.InviteExecutor
import pjs.dogbox.util.CryptUtil
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
private fun ts() = LocalDateTime.now().format(timeFormatter)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteMasterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    logs: SnapshotStateList<String> = mutableStateListOf(),
    onNavigateToLogs: () -> Unit = {},
) {
    val context = LocalContext.current
    val notice = "仅供测试使用，禁止用于非法用途"
    var configItems by remember { mutableStateOf(emptyList<JsonObject>()) }
    var configArray by remember { mutableStateOf<JsonArray?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val text = context.assets.open("json/config.jsonb")
                    .bufferedReader().use { it.readText() }
                val decoded = CryptUtil.base64Decode(text)
                val array = Json.parseToJsonElement(decoded).jsonArray
                configArray = array
                configItems = array.map { it.jsonObject }
            } catch (_: Exception) {
                configItems = emptyList()
            }
        }
    }

    var inputText by remember { mutableStateOf("") }
    var minIntervalTime by remember { mutableStateOf("10") }
    var maxIntervalTime by remember { mutableStateOf("50") }
    var inviteCount by remember { mutableStateOf("5") }
    var isRunning by remember { mutableStateOf(false) }
    var runningJob by remember { mutableStateOf<Job?>(null) }
    var selectedType by remember(configItems) {
        mutableStateOf(
            configItems.firstOrNull()?.get("name")?.jsonPrimitive?.contentOrNull ?: "尚未配置"
        )
    }
    var showTypeSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("邀请达人", fontWeight = FontWeight.Bold) },
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
                actions = {
                    FilledTonalIconButton(
                        onClick = onNavigateToLogs,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.bug_report_outlined),
                            contentDescription = "运行日志",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.app_badging_outlined),
                        contentDescription = "通知",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = notice,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showTypeSheet = true },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent,
                ),
                border = BorderStroke(
                    if (showTypeSheet) 2.dp else 1.dp,
                    if (showTypeSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.app_registration_outlined),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = selectedType,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            if (showTypeSheet) {
                focusManager.clearFocus()
                ModalBottomSheet(
                    onDismissRequest = { showTypeSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        if (configItems.isEmpty()) {
                            Text(
                                "尚未配置",
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                            )
                        } else {
                            configItems.forEach { item ->
                                val name = item["name"]?.jsonPrimitive?.contentOrNull ?: ""
                                val desc = item["desc"]?.jsonPrimitive?.contentOrNull ?: ""
                                val vip = item["vip"]?.jsonPrimitive?.contentOrNull ?: ""
                                val isSelected = name == selectedType
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedType = name
                                            showTypeSheet = false
                                        }
                                        .then(
                                            if (isSelected) Modifier.background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.shapes.medium
                                            ) else Modifier
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$desc · 每邀请一人得 $vip 天会员",
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = minIntervalTime,
                    onValueChange = {
                        minIntervalTime = it.filter { c -> c.isDigit() }.take(3).let { s ->
                            s.toIntOrNull()?.let { v ->
                                if (v > 999) s.take(3) else s
                            } ?: s
                        }
                    },
                    label = { Text("最小间隔") },
                    placeholder = { Text("1~999") },
                    suffix = { Text("秒") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = maxIntervalTime,
                    onValueChange = {
                        maxIntervalTime = it.filter { c -> c.isDigit() }.take(3).let { s ->
                            s.toIntOrNull()?.let { v ->
                                if (v > 999) s.take(3) else s
                            } ?: s
                        }
                    },
                    label = { Text("最大间隔") },
                    placeholder = { Text("1~999") },
                    suffix = { Text("秒") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = inviteCount,
                    onValueChange = {
                        inviteCount = it.filter { c -> c.isDigit() }.take(2).let { s ->
                            s.toIntOrNull()?.let { v ->
                                if (v > 99) s.take(2) else s
                            } ?: s
                        }
                    },
                    label = { Text("邀请数量") },
                    placeholder = { Text("1~99") },
                    suffix = { Text("次") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("邀请码") },
                    placeholder = { Text("请输入邀请码") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = {
                    if (isRunning) {
                        runningJob?.cancel()
                        logs.add("${ts()} 邀请任务已停止")
                        Toast.makeText(context, "邀请任务已停止", Toast.LENGTH_SHORT).show()
                    } else {
                        focusManager.clearFocus()
                        onNavigateToLogs()
                        runningJob = scope.launch {
                            isRunning = true
                            try {
                                logs.clear()
                                InviteExecutor.execute(
                                    configArray = configArray ?: JsonArray(emptyList()),
                                    params = InviteExecutor.Params(
                                        inviteCode = inputText.trim(),
                                        minInterval = minIntervalTime.toIntOrNull() ?: 0,
                                        maxInterval = maxIntervalTime.toIntOrNull() ?: 0,
                                        count = inviteCount.toIntOrNull() ?: 0,
                                        selectedType = selectedType,
                                    ),
                                    onLog = { logs.add("${ts()} $it") },
                                )
                            } finally {
                                isRunning = false
                                runningJob = null
                            }
                        }
                    }
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    if (isRunning) "停止运行" else "开始运行",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
