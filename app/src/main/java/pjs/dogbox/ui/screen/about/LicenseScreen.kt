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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.ui.components.MiuixEmptyText
import pjs.dogbox.ui.components.MiuixLoading
import pjs.dogbox.ui.components.MiuixSubScreen
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

private const val STATE_LOADING = 0
private const val STATE_LOADED = 1
private const val STATE_ERROR = 2

private data class LicenseItem(
    val name: String,
    val module: String,
    val version: String,
    val license: String,
    val homepage: String,
)

@Composable
fun LicenseScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    var loadState by remember { mutableIntStateOf(STATE_LOADING) }
    var licenses by remember { mutableStateOf<List<LicenseItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        var attempts = 0
        while (attempts < 2) {
            attempts++
            try {
                val text = context.assets.open("json/license.json")
                    .bufferedReader().use { it.readText() }
                licenses = Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
                    val o = e.jsonObject
                    val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val module = o["module"]?.jsonPrimitive?.contentOrNull ?: ""
                    val version = o["version"]?.jsonPrimitive?.contentOrNull ?: ""
                    val license = o["license"]?.jsonPrimitive?.contentOrNull ?: ""
                    val homepage = o["homepage"]?.jsonPrimitive?.contentOrNull ?: ""
                    LicenseItem(name, module, version, license, homepage)
                }
                loadState = STATE_LOADED
                break
            } catch (_: Exception) {
                if (attempts >= 2) loadState = STATE_ERROR
            }
        }
    }

    MiuixSubScreen(title = "开源许可", onBack = onBack) { padding ->
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
                    items(licenses, key = { it.module }) { item ->
                        LicenseCard(item = item)
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
private fun LicenseCard(item: LicenseItem) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (item.homepage.isNotEmpty()) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, item.homepage.toUri())
                )
            }
        },
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MiuixText(
                text = item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            MiuixText(
                text = item.module,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillText(text = item.license)
            }
        }
    }
}

@Composable
private fun PillText(text: String) {
    if (text.isEmpty()) return
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.primary)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        MiuixText(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onPrimary,
        )
    }
}
