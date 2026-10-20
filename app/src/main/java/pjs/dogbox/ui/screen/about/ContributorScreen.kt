package pjs.dogbox.ui.screen.about

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import pjs.dogbox.ui.components.MiuixEmptyText
import pjs.dogbox.ui.components.MiuixLoading
import pjs.dogbox.ui.components.MiuixSubScreen
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

private const val STATE_LOADING = 0
private const val STATE_LOADED = 1
private const val STATE_ERROR = 2

private data class ContributorItem(
    val name: String,
    val desc: String,
    val icon: String,
    val link: String,
)

@Composable
fun ContributorScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    var loadState by remember { mutableIntStateOf(STATE_LOADING) }
    var contributors by remember { mutableStateOf<List<ContributorItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        var attempts = 0
        while (attempts < 2) {
            attempts++
            try {
                val text = context.assets.open("json/contributor.json")
                    .bufferedReader().use { it.readText() }
                contributors = Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
                    val o = e.jsonObject
                    val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val desc = o["desc"]?.jsonPrimitive?.contentOrNull ?: ""
                    val icon = o["icon"]?.jsonPrimitive?.contentOrNull ?: ""
                    val link = o["link"]?.jsonPrimitive?.contentOrNull ?: ""
                    ContributorItem(name, desc, icon, link)
                }
                loadState = STATE_LOADED
                break
            } catch (_: Exception) {
                if (attempts >= 2) loadState = STATE_ERROR
            }
        }
    }

    MiuixSubScreen(title = "特别鸣谢", onBack = onBack) { padding ->
        when (loadState) {
            STATE_LOADING -> MiuixLoading(modifier = Modifier.padding(padding))
            STATE_ERROR -> MiuixEmptyText(modifier = Modifier.padding(padding))
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    items(contributors, key = { it.name }) { item ->
                        ContributorCard(item = item)
                    }
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributorCard(item: ContributorItem) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (item.link.isNotEmpty()) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, item.link.toUri())
                )
            }
        },
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.icon)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.dogbox_logo_round),
                    error = painterResource(R.drawable.dogbox_logo_round),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                MiuixText(
                    text = item.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                MiuixText(
                    text = item.desc,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
    }
}
