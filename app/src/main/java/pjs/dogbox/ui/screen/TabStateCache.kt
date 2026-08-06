package pjs.dogbox.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

class TabStateCache<T : Any>(private val factory: () -> T) {
    private val states = mutableMapOf<String, T>()

    fun get(key: String): T = states.getOrPut(key) { factory() }
}

@Composable
fun <T : Any> rememberTabStateCache(factory: () -> T): TabStateCache<T> {
    return remember { TabStateCache(factory) }
}
