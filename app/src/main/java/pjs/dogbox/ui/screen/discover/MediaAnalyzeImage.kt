package pjs.dogbox.ui.screen.discover

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.SubcomposeAsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pjs.dogbox.R
import pjs.dogbox.ui.screen.mine.openPermissionManager
import pjs.dogbox.utils.AppData
import pjs.dogbox.utils.CryptUtil
import pjs.dogbox.utils.HttpUtil
import pjs.dogbox.utils.SimpleDragHandle
import java.io.File
import java.util.Locale

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

private fun guessImageExt(url: String): String {
    val path = url.substringBefore("?").substringAfterLast("/")
    val dot = path.lastIndexOf(".")
    if (dot >= 0) {
        val ext = path.substring(dot + 1).lowercase(Locale.ROOT)
        if (ext in IMAGE_EXTENSIONS) return ext
    }
    return "jpg"
}

private fun mimeFor(ext: String): String = when (ext.lowercase(Locale.ROOT)) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "bmp" -> "image/bmp"
    "webp" -> "image/webp"
    else -> "image/jpeg"
}

private fun sanitizeFileName(title: String): String =
    title.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "DogBox" }

private fun saveImageBytesToGallery(
    context: Context,
    bytes: ByteArray,
    ext: String,
    title: String,
): Boolean =
    try {
        val fileName = "${sanitizeFileName(title)}_${System.currentTimeMillis()}.$ext"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeFor(ext))
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DogBox/Material/${CryptUtil.md5(title, 16)}/Pictures")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "DogBox/Material/${CryptUtil.md5(title, 16)}/Pictures"
            )
            if (!dir.exists()) dir.mkdirs()
            File(dir, fileName).writeBytes(bytes)
            true
        }
    } catch (_: Exception) {
        false
    }


private fun setSystemBarsVisible(
    controller: WindowInsetsControllerCompat?,
    visible: Boolean,
) {
    if (controller == null) return
    controller.systemBarsBehavior = if (visible)
        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    else
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (visible) controller.show(WindowInsetsCompat.Type.systemBars())
    else controller.hide(WindowInsetsCompat.Type.systemBars())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaAnalyzeImageGallery(
    imageUrls: List<String>,
    initialIndex: Int,
    referer: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val activity = context as? Activity
    val insetsController = remember {
        activity?.window?.let { WindowInsetsControllerCompat(activity.window, activity.window.decorView) }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val ctrl = ViewCompat.getWindowInsetsController(view)
        val savedLightStatus = ctrl?.isAppearanceLightStatusBars
        val savedLightNav = ctrl?.isAppearanceLightNavigationBars
        ctrl?.isAppearanceLightStatusBars = false
        ctrl?.isAppearanceLightNavigationBars = false
        onDispose {
            setSystemBarsVisible(ctrl, true)
            if (savedLightStatus != null) ctrl.isAppearanceLightStatusBars = savedLightStatus
            if (savedLightNav != null) ctrl.isAppearanceLightNavigationBars = savedLightNav
        }
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (imageUrls.size - 1).coerceAtLeast(0)),
        pageCount = { imageUrls.size },
    )
    val currentUrl = imageUrls[pagerState.currentPage]

    var showTopBar by remember { mutableStateOf(true) }
    var pendingShow by remember { mutableStateOf(false) }

    LaunchedEffect(pendingShow) {
        if (pendingShow) {
            setSystemBarsVisible(insetsController, true)
            showTopBar = true
            pendingShow = false
        }
    }
    val bgColors = listOf(Color.Black, Color.White, Color.Gray)
    var bgColorIndex by remember { mutableIntStateOf(0) }

    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var imageBytesUrl by remember { mutableStateOf<String?>(null) }
    var imageWidth by remember { mutableIntStateOf(0) }
    var imageHeight by remember { mutableIntStateOf(0) }
    var loadingInfo by remember { mutableStateOf(false) }
    var infoError by remember { mutableStateOf(false) }

    suspend fun fetchImageBytes(url: String): ByteArray? {
        val result = withContext(Dispatchers.IO) {
            HttpUtil.getBytes(
                url,
                mapOf("Referer" to referer, "User-Agent" to AppData.UA_CHROME_M),
            )
        }
        return result.getOrNull()
    }

    LaunchedEffect(showSheet, pagerState.currentPage) {
        if (showSheet) {
            setSystemBarsVisible(insetsController, true)
            showTopBar = true
            val url = imageUrls[pagerState.currentPage]
            if (imageBytesUrl != url || imageBytes == null) {
                loadingInfo = true
                infoError = false
                val bytes = fetchImageBytes(url)
                if (bytes != null) {
                    imageBytes = bytes
                    imageBytesUrl = url
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    imageWidth = options.outWidth
                    imageHeight = options.outHeight
                } else {
                    infoError = true
                }
                loadingInfo = false
            }
        }
    }

    fun downloadImage() {
        scope.launch {
            val bytes = imageBytes ?: fetchImageBytes(currentUrl)
            if (bytes == null) {
                Toast.makeText(context, "图片获取失败，无法下载保存", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                saveImageBytesToGallery(context, bytes, guessImageExt(currentUrl), title)
            }
            Toast.makeText(
                context,
                if (ok) "已保存至 /Download/DogBox/Material/Pictures/" else "保存失败",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun hasFileAccessPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED

    fun openFileAccessPermissionPage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                )
            }.onFailure {
                openPermissionManager(context)
            }
        } else {
            openPermissionManager(context)
        }
    }

    fun requestDownloadPermission() {
        if (!hasFileAccessPermission()) {
            Toast.makeText(context, "权限不足", Toast.LENGTH_SHORT).show()
            openFileAccessPermissionPage()
            return
        }
        downloadImage()
    }

    fun copyLink() {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("图片链接", currentUrl))
        Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
    }

    fun copyTitle() {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("标题", title))
        Toast.makeText(context, "标题已复制", Toast.LENGTH_SHORT).show()
    }

    fun shareLink() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, currentUrl)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "分享链接")) }
            .onFailure { Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show() }
    }

    fun shareImage() {
        scope.launch {
            val bytes = imageBytes ?: fetchImageBytes(currentUrl)
            if (bytes == null) {
                Toast.makeText(context, "图片获取失败，无法分享", Toast.LENGTH_SHORT).show()
                return@launch
            }
            withContext(Dispatchers.IO) {
                try {
                    val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
                    val ext = guessImageExt(currentUrl)
                    val file = File(dir, "share_${System.currentTimeMillis()}.$ext")
                    file.writeBytes(bytes)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeFor(ext)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享图片"))
                } catch (_: Exception) {
                    Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun openInBrowser() {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, currentUrl.toUri())) }
            .onFailure { Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show() }
    }

    val resolutionText = when {
        loadingInfo -> "加载中..."
        imageWidth > 0 && imageHeight > 0 -> "$imageHeight x $imageWidth"
        infoError -> "分辨率未知"
        else -> "加载中..."
    }
    val sizeText = when {
        loadingInfo -> "加载中..."
        imageBytes != null -> {
            val size = imageBytes!!.size
            when {
                size >= 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.2f GB", size / 1024f / 1024f / 1024f)
                size >= 1024 * 1024 -> String.format(Locale.ROOT, "%.2f MB", size / 1024f / 1024f)
                else -> String.format(Locale.ROOT, "%.2f KB", size / 1024f)
            }
        }
        infoError -> "大小未知"
        else -> "加载中..."
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColors[bgColorIndex]),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ZoomableAnalyzeImage(
                url = imageUrls[page],
                referer = referer,
                onTap = {
                    if (showTopBar) {
                        showTopBar = false
                        setSystemBarsVisible(insetsController, false)
                    } else {
                        pendingShow = true
                    }
                },
                onLongPress = { showSheet = true },
            )
        }

        AnimatedVisibility(
            visible = showTopBar,
            enter = slideInVertically(
                animationSpec = tween(200),
                initialOffsetY = { -it },
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                animationSpec = tween(200),
                targetOffsetY = { -it },
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black, Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back_outlined),
                        contentDescription = "返回",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(title) {
                            detectTapGestures(onDoubleTap = { copyTitle() })
                        },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { bgColorIndex = (bgColorIndex + 1) % bgColors.size }) {
                    Icon(
                        painter = painterResource(R.drawable.sync_alt_outlined),
                        contentDescription = "切换背景色",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = { showSheet = true }) {
                    Icon(
                        painter = painterResource(R.drawable.more_horiz_filled),
                        contentDescription = "更多设置",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                )
                .navigationBarsPadding()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${pagerState.currentPage + 1} / ${imageUrls.size}",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            dragHandle = { SimpleDragHandle() },
        ) {
            val sheetView = LocalView.current
            SideEffect {
                val ctrl = ViewCompat.getWindowInsetsController(sheetView)
                val light = bgColors[bgColorIndex] == Color.White
                ctrl?.isAppearanceLightStatusBars = light
                ctrl?.isAppearanceLightNavigationBars = light
            }
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = resolutionText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
                ImageSheetItem(
                    icon = R.drawable.download_outlined,
                    label = "保存图片",
                    onClick = {
                        showSheet = false
                        requestDownloadPermission()
                    },
                )
                ImageSheetItem(
                    icon = R.drawable.content_copy_outlined,
                    label = "复制链接",
                    onClick = {
                        showSheet = false
                        copyLink()
                    },
                )
                ImageSheetItem(
                    icon = R.drawable.share_filled,
                    label = "分享链接",
                    onClick = {
                        showSheet = false
                        shareLink()
                    },
                )
                ImageSheetItem(
                    icon = R.drawable.image_arrow_up_outlined,
                    label = "以图片分享",
                    onClick = {
                        showSheet = false
                        shareImage()
                    },
                )
                ImageSheetItem(
                    icon = R.drawable.open_in_browser_outlined,
                    label = "浏览器打开",
                    onClick = {
                        showSheet = false
                        openInBrowser()
                    },
                )
            }
        }
    }
}

@Composable
private fun ZoomableAnalyzeImage(
    url: String,
    referer: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        if (newScale == 1f) {
            offset = Offset.Zero
        } else {
            offset += panChange
        }
        scale = newScale
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                        }
                    },
                    onLongPress = { onLongPress() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .httpHeaders(
                    NetworkHeaders.Builder()
                        .set("Referer", referer)
                        .build()
                ).build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (scale > 1f) Modifier.transformable(transformableState)
                    else Modifier
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit,
            loading = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            },
            error = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "图片加载失败",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            },
        )
    }
}

@Composable
private fun ImageSheetItem(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
