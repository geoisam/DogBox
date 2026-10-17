package pjs.dogbox.ui.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pjs.dogbox.R
import pjs.dogbox.ui.components.SheetSectionTitle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

enum class DiscoverPage {
    ANIME_RANK,
    BOX_OFFICE_REALTIME,
    BOX_OFFICE_TOTAL,
    HOT_NEWS_RANK,
    INVITE_MASTER,
    MEDIA_PLAYER,
    MEDIA_ANALYZE,
    MOVIE_RATING,
    MOVIE_WORLD,
    PROFILE_AVATAR,
    SCROLLING_TEXT,
    TV_LIVE,
}

private data class DiscoverItem(
    val title: String,
    val subtitle: String,
    val icon: Int,
    val page: DiscoverPage,
)

private data class DiscoverGroup(
    val name: String,
    val items: List<DiscoverItem>,
)

private val groups = listOf(
    DiscoverGroup(
        "推荐", listOf(
            DiscoverItem(
                "电视直播",
                "全球电视台网络直播",
                R.drawable.tv_guide_outlined,
                DiscoverPage.TV_LIVE
            ),
            DiscoverItem(
                "播放链接",
                "支持大部分点播和直播",
                R.drawable.media_link_outlined,
                DiscoverPage.MEDIA_PLAYER
            ),
            DiscoverItem(
                "邀请达人",
                "每邀请一人得1次会员",
                R.drawable.person_raised_hand_outlined,
                DiscoverPage.INVITE_MASTER
            ),
            DiscoverItem(
                "素材解析",
                "视频图文无水印保存",
                R.drawable.psychiatry_outlined,
                DiscoverPage.MEDIA_ANALYZE
            ),
        )
    ),
    DiscoverGroup(
        "榜单", listOf(
            DiscoverItem(
                "今日热榜",
                "热门平台实时热搜榜单",
                R.drawable.newsmode_outlined,
                DiscoverPage.HOT_NEWS_RANK
            ),
            DiscoverItem(
                "实时票房",
                "中国大陆实时电影票房",
                R.drawable.local_atm_outlined,
                DiscoverPage.BOX_OFFICE_REALTIME
            ),
            DiscoverItem(
                "高分电影",
                "豆瓣高分与冷门佳片",
                R.drawable.award_star_outlined,
                DiscoverPage.MOVIE_RATING
            ),
            DiscoverItem(
                "全球票房",
                "全球电影票房TOP100",
                R.drawable.deceased_outlined,
                DiscoverPage.MOVIE_WORLD
            ),
            DiscoverItem(
                "中国票房",
                "中国大陆电影票房总榜",
                R.drawable.movie_outlined,
                DiscoverPage.BOX_OFFICE_TOTAL
            ),
            DiscoverItem(
                "哔哩番剧",
                "哔哩哔哩番剧分类索引",
                R.drawable.manga_outlined,
                DiscoverPage.ANIME_RANK
            ),
        )
    ),
    DiscoverGroup(
        "其他", listOf(
            DiscoverItem(
                "头像获取",
                "QQ头像与抖音头像",
                R.drawable.hive_outlined,
                DiscoverPage.PROFILE_AVATAR
            ),
            DiscoverItem(
                "滚动字幕",
                "自定义循环滚动字幕",
                R.drawable.subtitles_outlined,
                DiscoverPage.SCROLLING_TEXT
            ),
        )
    ),
)

@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    onNavigateToDetail: (DiscoverPage) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groups.forEach { group ->
            SheetSectionTitle(group.name)
            group.items.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { it ->
                        Card(
                            modifier = Modifier.weight(1f),
                            pressFeedbackType = PressFeedbackType.Sink,
                            showIndication = true,
                            onClick = { onNavigateToDetail(it.page) },
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            MiuixTheme.colorScheme.primaryContainer,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    MiuixIcon(
                                        painter = painterResource(it.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MiuixTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                MiuixText(
                                    text = it.title,
                                    style = MiuixTheme.textStyles.title4,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                MiuixText(
                                    text = it.subtitle,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                )
                            }
                        }
                    }
                    // 补齐双列对齐
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
