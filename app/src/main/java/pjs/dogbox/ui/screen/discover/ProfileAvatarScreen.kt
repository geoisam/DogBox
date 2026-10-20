package pjs.dogbox.ui.screen.discover

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import pjs.dogbox.data.AppData
import pjs.dogbox.ui.components.MiuixSubScreen
import pjs.dogbox.utils.HttpUtil
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

internal class ProfileAvatarScreenState {
    val qqInput = mutableStateOf("")
    val douyinInput = mutableStateOf("")
    val fetchingDouyin = mutableStateOf(false)
    val currentTab = mutableIntStateOf(0)
}

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
    val tabs = listOf("QQ头像", "抖音头像")
    val qqFocusRequester = remember { FocusRequester() }
    val douyinFocusRequester = remember { FocusRequester() }
    val pagerState = rememberPagerState(
        initialPage = currentTab,
        pageCount = { tabs.size },
    )
    LaunchedEffect(pagerState.currentPage) {
        currentTab = pagerState.currentPage
    }
    LaunchedEffect(pagerState.settledPage) {
        val requester =
            if (pagerState.settledPage == 0) qqFocusRequester else douyinFocusRequester
        runCatching { requester.requestFocus() }
    }

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

    MiuixSubScreen(
        title = "头像获取",
        onBack = onBack,
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                insideMargin = PaddingValues(4.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tabs.forEachIndexed { index, title ->
                        val selected = pagerState.currentPage == index
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.defaultColors(
                                color = if (selected) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (selected) MiuixTheme.colorScheme.onPrimary
                                else MiuixTheme.colorScheme.onSurfaceSecondary,
                            ),
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                MiuixText(
                                    text = title,
                                    style = MiuixTheme.textStyles.body1,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
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
                            label = "QQ号码",
                            placeholder = "请输入QQ号码",
                            value = qqInput,
                            onValueChange = { qqInput = it.filter(Char::isDigit).take(20) },
                            keyboardType = KeyboardType.Number,
                            isFetching = false,
                            onFetch = ::fetchQQ,
                            focusRequester = qqFocusRequester,
                        )
                    } else {
                        AvatarFetchCard(
                            label = "抖音主页链接",
                            placeholder = "请输入抖音主页链接",
                            value = douyinInput,
                            onValueChange = { douyinInput = it },
                            isFetching = fetchingDouyin,
                            onFetch = ::fetchDouyin,
                            multiline = true,
                            focusRequester = douyinFocusRequester,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AvatarFetchCard(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    isFetching: Boolean,
    onFetch: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    multiline: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                label = if (value.isBlank()) placeholder else label,
                singleLine = !multiline,
                maxLines = if (multiline) 6 else 1,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = if (multiline) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                } else {
                    Modifier.fillMaxWidth()
                }.then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
                ),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onFetch,
                enabled = !isFetching,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                MiuixText(
                    text = if (isFetching) "获取中..." else "开始获取",
                    style = MiuixTheme.textStyles.title4,
                )
            }
        }
    }
}

private data class DouyinAvatarInfo(val url: String, val nickname: String)

private suspend fun resolveDouyinAvatar(link: String): DouyinAvatarInfo? =
    withContext(Dispatchers.IO) {
        val finalUrl =
            HttpUtil.getFinalUrl(link, mapOf("User-Agent" to AppData.UA_CHROME_M)).getOrNull()
                ?: return@withContext null
        val secUid = Regex("""/user/([0-9A-Za-z_-]+)""").find(finalUrl)?.groupValues?.get(1)
            ?: Regex("""[?&]sec_uid=([0-9A-Za-z_-]+)""").find(finalUrl)?.groupValues?.get(1)
            ?: return@withContext null
        val api = "https://www.iesdouyin.com/web/api/v2/user/info/?sec_uid=$secUid"
        val body = HttpUtil.get(api, mapOf("User-Agent" to AppData.UA_CHROME_M)).getOrNull()
            ?: return@withContext null
        val userInfo =
            runCatching { Json.parseToJsonElement(body).jsonObject["user_info"]?.jsonObject }
                .getOrNull() ?: return@withContext null
        val nickname = userInfo["nickname"]?.jsonPrimitive?.contentOrNull ?: "抖音头像"
        val avatar = userInfo["avatar_thumb"]?.jsonObject
            ?.get("url_list")?.jsonArray
            ?.firstOrNull()?.jsonPrimitive?.contentOrNull
            ?: return@withContext null
        DouyinAvatarInfo(avatar.replace("100x100", "1080x1080").replace(".jpeg", ".png"), nickname)
    }
