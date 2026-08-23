package pjs.dogbox.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry

class TabStateCache<T : Any>(private val factory: () -> T) {
    private val states = mutableMapOf<String, T>()

    fun get(key: String): T = states.getOrPut(key) { factory() }
}

@Composable
fun <T : Any> rememberTabStateCache(factory: () -> T): TabStateCache<T> {
    return remember { TabStateCache(factory) }
}

fun interface DisposableState {
    fun dispose()
}

class ScreenStateRegistry {
    private val states = mutableMapOf<String, Any>()

    fun <T : Any> get(entryId: String, create: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return states.getOrPut(entryId) { create() } as T
    }

    fun remove(entryId: String) {
        val removed = states.remove(entryId)
        (removed as? DisposableState)?.dispose()
    }
}

@Composable
fun <T : Any> rememberEntryState(
    registry: ScreenStateRegistry,
    entry: NavBackStackEntry,
    create: () -> T,
): T {
    val state = remember(entry.id) { registry.get(entry.id, create) }
    DisposableEffect(entry) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                registry.remove(entry.id)
            }
        }
        entry.lifecycle.addObserver(observer)
        onDispose {
            if (entry.lifecycle.currentState == Lifecycle.State.DESTROYED) {
                registry.remove(entry.id)
            }
            entry.lifecycle.removeObserver(observer)
        }
    }
    return state
}