package pjs.dogbox.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.BookFill
import top.yukonga.miuix.kmp.icon.glass.BookOutline
import top.yukonga.miuix.kmp.icon.glass.ContactsBook
import top.yukonga.miuix.kmp.icon.glass.ContactsCircle
import top.yukonga.miuix.kmp.icon.glass.HomeFill
import top.yukonga.miuix.kmp.icon.glass.HomeOutline

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME("首页", MiuixIcons.Glass.HomeOutline, MiuixIcons.Glass.HomeFill),
    DISCOVER("发现", MiuixIcons.Glass.BookOutline, MiuixIcons.Glass.BookFill),
    MINE("我的", MiuixIcons.Glass.ContactsBook, MiuixIcons.Glass.ContactsCircle),
}
