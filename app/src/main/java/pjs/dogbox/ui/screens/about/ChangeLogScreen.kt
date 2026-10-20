package pjs.dogbox.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

private data class ChangeLogItem(
    val name: String,
    val code: String,
    val note: String,
)

@Composable
fun ChangeLogScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    var loadState by remember { mutableIntStateOf(STATE_LOADING) }
    var changeLogs by remember { mutableStateOf<List<ChangeLogItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        var attempts = 0
        while (attempts < 2) {
            attempts++
            try {
                val text = context.assets.open("json/change.json")
                    .bufferedReader().use { it.readText() }
                changeLogs = Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
                    val o = e.jsonObject
                    val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val code = o["code"]?.jsonPrimitive?.contentOrNull ?: ""
                    val note = o["note"]?.jsonPrimitive?.contentOrNull ?: ""
                    ChangeLogItem(name, code, note)
                }
                loadState = STATE_LOADED
                break
            } catch (_: Exception) {
                if (attempts >= 2) loadState = STATE_ERROR
            }
        }
    }

    MiuixSubScreen(title = "更新日志", onBack = onBack) { padding ->
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
                    items(changeLogs, key = { "${it.name}_${it.code}" }) { item ->
                        ChangeLogCard(item = item)
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
private fun ChangeLogCard(item: ChangeLogItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixText(
                    text = "v${item.name}",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary,
                )
                MiuixText(
                    text = item.code,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            MiuixText(
                text = item.note,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}
