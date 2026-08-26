package pjs.dogbox.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pjs.dogbox.R

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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        groups.forEach { group ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(18.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            group.items.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { it ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onNavigateToDetail(it.page) },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Surface(
                                    modifier = Modifier.size(56.dp),
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(it.icon),
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = it.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = it.subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
