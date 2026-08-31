package pjs.dogbox.ui.screen.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R

private const val STATE_LOADING = 0
private const val STATE_LOADED = 1
private const val STATE_ERROR = 2

private data class ChangeLogItem(
    val name: String,
    val code: String,
    val note: String,
)

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("更新日志", fontWeight = FontWeight.Bold) },
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
        when (loadState) {
            STATE_LOADING -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(56.dp))
                }
            }

            STATE_ERROR -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "暂无数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .padding(paddingValues),
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
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "v${item.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = item.code,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = item.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
