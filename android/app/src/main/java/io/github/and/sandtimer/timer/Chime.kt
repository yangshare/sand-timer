package io.github.and.sandtimer.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import io.github.and.sandtimer.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Rings the chime itself, on the alarm stream, rather than leaving it to the notification: phones differ in whether a
 * notification's own sound is played at all, and several play none, but none of them silence a MediaPlayer. Rings over
 * and over until [stop] quiets it — the stop button, the glass flipped again, or the bell switched off in Settings.
 */
object Chime {
    private var player: MediaPlayer? = null
    private var pending: BroadcastReceiver.PendingResult? = null
    private val main = Handler(Looper.getMainLooper())
    private val sounding = MutableStateFlow(false)

    /** True while the chime is ringing, so the screen and the notification can offer a way to quiet it. */
    val ringing: StateFlow<Boolean> = sounding.asStateFlow()

    /** How long the broadcast that began the chime is held: the first ring, before the rings after it stand alone. */
    private const val FIRST_RING_MS = 2_500L

    /** Rings the chime, stopping one still ringing. Rings until [stop] quiets it. */
    fun play(context: Context) {
        stop()
        val player = MediaPlayer()
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            val app = context.applicationContext
            val wav = app.resources.openRawResourceFd(R.raw.chime)
            player.setDataSource(wav.fileDescriptor, wav.startOffset, wav.length)
            wav.close()
            player.isLooping = true
            // A looping player never completes; these only quiet a player that gave out on its own.
            player.setOnCompletionListener { rung(it) }
            player.setOnErrorListener { mp, _, _ -> rung(mp); true }
            player.prepare()
            player.start()
            this.player = player
            sounding.value = true
        }.onFailure {
            player.release()
        }
    }

    /** Quiets a chime still ringing: the stop button, the glass flipped again, or the player given out. */
    fun stop() {
        finishBroadcast()
        sounding.value = false
        val player = player ?: return
        this.player = null
        runCatching { player.stop() }
        player.release()
    }

    /**
     * Keeps a broadcast from ending before the first ring has sounded, so the phone doesn't drop the app mid-note. A
     * looping player never completes, so the broadcast is let go once the first ring is out; the rings after it keep
     * sounding on their own, the way a music app's do.
     */
    fun thenFinish(pending: BroadcastReceiver.PendingResult) {
        if (player == null) {
            pending.finish()
            return
        }
        this.pending = pending
        main.postDelayed({ finishBroadcast() }, FIRST_RING_MS)
    }

    private fun rung(player: MediaPlayer) {
        player.release()
        sounding.value = false
        if (this.player === player) this.player = null
        finishBroadcast()
    }

    private fun finishBroadcast() {
        main.removeCallbacksAndMessages(null)
        pending?.finish()
        pending = null
    }
}
