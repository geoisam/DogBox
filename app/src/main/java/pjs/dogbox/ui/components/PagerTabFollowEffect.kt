package pjs.dogbox.ui.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun PagerTabFollowEffect(
    pagerState: PagerState,
    tabScrollState: LazyListState,
) {
    LaunchedEffect(pagerState.currentPage) {
        val targetIndex = pagerState.currentPage
        val layoutInfo = tabScrollState.layoutInfo
        val target = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
        if (target == null) {
            tabScrollState.animateScrollToItem(targetIndex)
        } else {
            val viewportStart = layoutInfo.viewportStartOffset
            val viewportEnd = layoutInfo.viewportEndOffset
            val delta = when {
                target.offset < viewportStart -> (target.offset - viewportStart).toFloat()
                target.offset + target.size > viewportEnd ->
                    (target.offset + target.size - viewportEnd).toFloat()

                else -> 0f
            }
            if (delta != 0f) {
                tabScrollState.animateScrollBy(delta)
            }
        }
    }
}
