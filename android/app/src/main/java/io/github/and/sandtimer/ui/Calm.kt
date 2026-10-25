package io.github.and.sandtimer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The clean view: while the sand runs, everything but the glass fades away after a few seconds — the
 * controls, the tabs and the phone's own bars — and comes back when the screen is tapped. Bumping or
 * picking the phone up leaves it alone: a timer sitting on the desk shouldn't be woken by a nudge.
 */
object Calm {
    /** Everything but the glass is hidden. */
    val hidden = MutableStateFlow(false)
    /** When the screen was last tapped. */
    private val tapped = MutableStateFlow(System.currentTimeMillis())
    private const val HIDE_AFTER = 3_000L

    /** The screen was tapped: show everything, and start counting the seconds again. */
    fun wake() {
        tapped.value = System.currentTimeMillis()
        hidden.value = false
    }

    /** Watches while [on]: the sand running, with the clean view turned on and the timer showing. */
    @Composable
    fun Watch(on: Boolean) {
        val last by tapped.collectAsState()
        DisposableEffect(on) {
            if (!on) {
                hidden.value = false
                return@DisposableEffect onDispose {}
            }
            onDispose { hidden.value = false }
        }
        LaunchedEffect(on, last) {
            if (!on) return@LaunchedEffect
            delay(HIDE_AFTER)
            hidden.value = true
        }
    }
}
