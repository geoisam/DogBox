package pjs.dogbox.ui.screens.discover

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
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
import pjs.dogbox.data.remote.InviteExecutor
import pjs.dogbox.ui.components.AppBottomSheet
import pjs.dogbox.utils.security.CryptUtil
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
private fun ts() = LocalDateTime.now().format(timeFormatter)

@Composable
fun InviteMasterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    logs: SnapshotStateList<String> = mutableStateListOf(),
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
    var maxIntervalTime by remember { mutableStateOf("30") }
    var inviteCount by remember { mutableStateOf("5") }
    var isRunning by remember { mutableStateOf(false) }
    var runningJob by remember { mutableStateOf<Job?>(null) }
    var selectedType by remember(configItems) {
        mutableStateOf(
            configItems.firstOrNull()?.get("name")?.jsonPrimitive?.contentOrNull ?: "尚未配置"
        )
    }
    val typeItems = remember(configItems) {
        configItems
            .map { item ->
                val name = item["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val desc = item["desc"]?.jsonPrimitive?.contentOrNull ?: ""
                val vip = item["vip"]?.jsonPrimitive?.contentOrNull ?: ""
                DropdownItem(text = name, summary = "$desc · 每邀请一人得 $vip 天会员")
            }
            .ifEmpty { listOf(DropdownItem(text = "尚未配置")) }
    }
    var showLogSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    MiuixScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "邀请达人",
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
                            focusManager.clearFocus()
                            showLogSheet = true
                        },
                    ) {
                        MiuixIcon(
                            painter = painterResource(R.drawable.bug_report_outlined),
                            contentDescription = "运行日志",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primaryContainer),
                insideMargin = PaddingValues(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MiuixIcon(
                        painter = painterResource(R.drawable.app_badging_outlined),
                        contentDescription = "通知",
                        tint = MiuixTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    MiuixText(
                        text = notice,
                        color = MiuixTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                OverlaySpinnerPreference(
                    title = "邀请应用",
                    dialogButtonString = "取消",
                    items = typeItems,
                    selectedIndex = configItems
                        .indexOfFirst { it["name"]?.jsonPrimitive?.contentOrNull == selectedType }
                        .coerceAtLeast(0),
                    enabled = configItems.isNotEmpty(),
                    onExpandedChange = { expanded -> if (expanded) focusManager.clearFocus() },
                    onSelectedIndexChange = { index ->
                        configItems.getOrNull(index)?.let { item ->
                            selectedType = item["name"]?.jsonPrimitive?.contentOrNull
                                ?: selectedType
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextField(
                                value = minIntervalTime,
                                onValueChange = {
                                    minIntervalTime =
                                        it.filter { c -> c.isDigit() }.take(3).let { s ->
                                            s.toIntOrNull()?.let { v ->
                                                if (v > 999) s.take(3) else s
                                            } ?: s
                                        }
                                },
                                label = "最小间隔(秒)",
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextField(
                                value = maxIntervalTime,
                                onValueChange = {
                                    maxIntervalTime =
                                        it.filter { c -> c.isDigit() }.take(3).let { s ->
                                            s.toIntOrNull()?.let { v ->
                                                if (v > 999) s.take(3) else s
                                            } ?: s
                                        }
                                },
                                label = "最大间隔(秒)",
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextField(
                                value = inviteCount,
                                onValueChange = {
                                    inviteCount = it.filter { c -> c.isDigit() }.take(2).let { s ->
                                        s.toIntOrNull()?.let { v ->
                                            if (v > 99) s.take(2) else s
                                        } ?: s
                                    }
                                },
                                label = "邀请数量(次)",
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                label = "邀请码",
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (isRunning) {
                            runningJob?.cancel()
                            logs.add("${ts()} 邀请任务已停止")
                            Toast.makeText(context, "邀请任务已停止", Toast.LENGTH_SHORT).show()
                        } else {
                            focusManager.clearFocus()
                            showLogSheet = true
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
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    MiuixText(
                        if (isRunning) "停止运行" else "开始运行",
                        style = MiuixTheme.textStyles.title4,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showLogSheet) {
        InviteLogSheet(
            logs = logs,
            onDismiss = { showLogSheet = false },
        )
    }
}

@Composable
private fun InviteLogSheet(
    logs: SnapshotStateList<String>,
    onDismiss: () -> Unit,
) {
    val logScrollState = rememberScrollState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logScrollState.animateScrollTo(logScrollState.maxValue)
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sheetMaxHeight = (LocalConfiguration.current.screenHeightDp.dp - statusBarTop - 20.dp)

    AppBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetMaxHeight)
                .padding(horizontal = 12.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 16.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            MiuixText("暂无日志", color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        }
                    } else {
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .verticalScroll(logScrollState)
                            ) {
                                logs.forEach { log ->
                                    MiuixText(
                                        text = log,
                                        modifier = Modifier.padding(vertical = 2.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
