package pjs.dogbox.ui.components

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun grayTextFieldColors(): TextFieldColors = TextFieldDefaults.textFieldColors(
    backgroundColor = MiuixTheme.colorScheme.surfaceContainerHigh,
    labelColor = MiuixTheme.colorScheme.onSurfaceContainerHigh,
    borderColor = MiuixTheme.colorScheme.outline,
)
