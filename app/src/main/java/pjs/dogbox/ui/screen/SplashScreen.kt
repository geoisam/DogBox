package pjs.dogbox.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pjs.dogbox.R
import pjs.dogbox.utils.AppData
import pjs.dogbox.utils.LunarUtil

@Composable
fun SplashScreen(
    onTimeout: () -> Unit = {},
) {
    val scienceGothic = FontFamily(Font(R.font.science_gothic))
    val bgColor = colorResource(R.color.splash_background)
    val fgColor = colorResource(R.color.splash_foreground)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.dogbox_logo_fill),
            contentDescription = null,
            tint = fgColor,
            modifier = Modifier.size(200.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.app_name),
            fontFamily = scienceGothic,
            style = MaterialTheme.typography.headlineLarge,
            color = fgColor,
            fontWeight = FontWeight.Bold,
        )
    }

    LaunchedEffect(Unit) {
        try {
            AppData.snapshot = withContext(Dispatchers.Default) { LunarUtil.takeSnapshot() }
        } catch (e: Exception) {
            e.printStackTrace()
            AppData.snapshot = null
        }
        delay(666L)
        onTimeout()
    }
}
