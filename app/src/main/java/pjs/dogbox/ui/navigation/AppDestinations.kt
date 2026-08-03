package pjs.dogbox.ui.navigation

import androidx.annotation.DrawableRes
import pjs.dogbox.R

enum class AppDestinations(
    val label: String,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
) {
    HOME("首页", R.drawable.home_app_logo_outlined, R.drawable.home_app_logo_filled),
    DISCOVER("发现", R.drawable.explore_outlined, R.drawable.explore_filled),
    MINE("我的", R.drawable.person_outlined, R.drawable.person_filled),
}
