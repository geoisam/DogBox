package pjs.dogbox.ui.screen.discover

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pjs.dogbox.ui.components.MiuixEmptyText
import pjs.dogbox.ui.components.MiuixSubScreen

@Composable
fun TvLiveScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    MiuixSubScreen(title = "电视直播", onBack = onBack) { padding ->
        MiuixEmptyText(
            "全球电视台网络直播线路",
            modifier = modifier.padding(padding),
        )
    }
}
