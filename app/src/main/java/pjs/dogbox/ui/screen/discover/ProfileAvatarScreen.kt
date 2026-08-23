package pjs.dogbox.ui.screen.discover

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import pjs.dogbox.data.AppData
import pjs.dogbox.utils.HttpUtil
import pjs.dogbox.ui.components.SimpleDragHandle

internal class ProfileAvatarScreenState {
    val qqInput = mutableStateOf("")
    val douyinInput = mutableStateOf("")
    val fetchingDouyin = mutableStateOf(false)
    val currentTab = mutableIntStateOf(0)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ProfileAvatarScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onViewImage: (String, String, String) -> Unit = { _, _, _ -> },
    state: ProfileAvatarScreenState = remember { ProfileAvatarScreenState() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var qqInput by state.qqInput
    var douyinInput by state.douyinInput
    var fetchingDouyin by state.fetchingDouyin
    var currentTab by state.currentTab
    val tabs = listOf("QQ", "抖音")
    val pagerState = rememberPagerState(
        initialPage = currentTab,
        pageCount = { tabs.size },
    )
    LaunchedEffect(pagerState.currentPage) {
        currentTab = pagerState.currentPage
    }
    var showTabSheet by remember { mutableStateOf(false) }
    val tabSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun fetchQQ() {
        val qq = qqInput.trim()
        if (qq.isEmpty()) {
            Toast.makeText(context, "请输入QQ号码", Toast.LENGTH_SHORT).show()
            return
        }
        focusManager.clearFocus()
        onViewImage("https://q1.qlogo.cn/g?b=qq&nk=$qq&s=0", "", qq)
    }

    fun fetchDouyin() {
        val input = douyinInput.trim()
        if (input.isEmpty()) {
            Toast.makeText(context, "请输入抖音主页链接", Toast.LENGTH_SHORT).show()
            return
        }
        val link = extractShareUrl(input)
        if (link == null || !link.contains("douyin", ignoreCase = true)) {
            Toast.makeText(context, "请输入抖音主页链接", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            douyinInput = link
            fetchingDouyin = true
            focusManager.clearFocus()
            val info = resolveDouyinAvatar(link)
            fetchingDouyin = false
            if (info != null) {
                onViewImage(info.url, "https://www.douyin.com/", info.nickname)
            } else {
                Toast.makeText(context, "获取失败，请检查链接", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("头像获取", fontWeight = FontWeight.Bold) },
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
                }
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(tabs) { index, tab ->
                        val selected = pagerState.currentPage == index
                        val chipColor by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            animationSpec = tween(200),
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(200),
                        )
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.large)
                                .background(chipColor)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    },
                                )
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = tab,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                maxLines = 1,
                            )
                        }
                    }
                }

                FilledTonalIconButton(
                    onClick = { showTabSheet = true },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.keyboard_double_arrow_right_outlined),
                        contentDescription = "展开",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (page == 0) {
                        AvatarFetchCard(
                            title = "QQ头像",
                            label = "QQ号码",
                            placeholder = "请输入QQ号码",
                            value = qqInput,
                            onValueChange = { qqInput = it.filter(Char::isDigit).take(20) },
                            keyboardType = KeyboardType.Number,
                            isFetching = false,
                            onFetch = ::fetchQQ,
                            onClear = { qqInput = "" },
                            onPaste = {
                                val pasted = pasteFromClipboard(context)
                                if (pasted != null) {
                                    qqInput = pasted.filter(Char::isDigit).take(20)
                                } else {
                                    Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    } else {
                        AvatarFetchCard(
                            title = "抖音头像",
                            label = "抖音主页链接",
                            placeholder = "请输入抖音主页链接",
                            value = douyinInput,
                            onValueChange = { douyinInput = it },
                            isFetching = fetchingDouyin,
                            onFetch = ::fetchDouyin,
                            multiline = true,
                            onClear = { douyinInput = "" },
                            onPaste = {
                                val pasted = pasteFromClipboard(context)
                                if (pasted != null) {
                                    douyinInput = pasted
                                } else {
                                    Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    if (showTabSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTabSheet = false },
            sheetState = tabSheetState,
            dragHandle = { SimpleDragHandle() },
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    tabs.forEachIndexed { index, label ->
                        val isSelected = pagerState.currentPage == index
                        val bgColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        val txtColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .background(bgColor)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                        showTabSheet = false
                                    },
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = txtColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarFetchCard(
    title: String,
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    isFetching: Boolean,
    onFetch: () -> Unit,
    onClear: () -> Unit,
    onPaste: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    multiline: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                singleLine = !multiline,
                maxLines = if (multiline) 100 else 1,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = if (multiline) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                } else {
                    Modifier.fillMaxWidth()
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            ClipboardActionRow(
                onClear = onClear,
                onPaste = onPaste,
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onFetch,
                enabled = !isFetching,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = if (isFetching) "获取中..." else "开始获取",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private data class DouyinAvatarInfo(val url: String, val nickname: String)

private suspend fun resolveDouyinAvatar(link: String): DouyinAvatarInfo? = withContext(Dispatchers.IO) {
    val finalUrl = HttpUtil.getFinalUrl(link, mapOf("User-Agent" to AppData.UA_CHROME_M)).getOrNull()
        ?: return@withContext null
    val secUid = Regex("""/user/([0-9A-Za-z_-]+)""").find(finalUrl)?.groupValues?.get(1)
        ?: Regex("""[?&]sec_uid=([0-9A-Za-z_-]+)""").find(finalUrl)?.groupValues?.get(1)
        ?: return@withContext null
    val api = "https://www.iesdouyin.com/web/api/v2/user/info/?sec_uid=$secUid"
    val body = HttpUtil.get(api, mapOf("User-Agent" to AppData.UA_CHROME_M)).getOrNull()
        ?: return@withContext null
    val userInfo = runCatching { Json.parseToJsonElement(body).jsonObject["user_info"]?.jsonObject }
        .getOrNull() ?: return@withContext null
    val nickname = userInfo["nickname"]?.jsonPrimitive?.contentOrNull ?: "抖音头像"
    val avatar = userInfo["avatar_thumb"]?.jsonObject
        ?.get("url_list")?.jsonArray
        ?.firstOrNull()?.jsonPrimitive?.contentOrNull
        ?: return@withContext null
    DouyinAvatarInfo(avatar.replace("100x100", "1080x1080").replace(".jpeg", ".png"), nickname)
}