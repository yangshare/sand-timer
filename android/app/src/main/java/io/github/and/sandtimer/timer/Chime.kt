package io.github.and.sandtimer.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import io.github.and.sandtimer.R

/**
 * Rings the chime itself, on the alarm stream, rather than leaving it to the notification: phones differ in whether a
 * notification's own sound is played at all, and several play none, but none of them silence a MediaPlayer. Rings to
 * the end, then frees itself.
 */
object Chime {
    private var player: MediaPlayer? = null
    private var done: (() -> Unit)? = null

    /** Rings the chime, stopping one still ringing. */
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
            player.setOnCompletionListener { rung(it) }
            player.setOnErrorListener { mp, _, _ -> rung(mp); true }
            player.prepare()
            player.start()
            this.player = player
        }.onFailure {
            player.release()
        }
    }

    /** Quiets a chime still ringing, as when the glass is flipped again before the last one ended. */
    fun stop() {
        val player = player ?: return
        this.player = null
        done = null
        runCatching { player.stop() }
        player.release()
    }

    /** Keeps a broadcast from ending before the chime has rung, so the phone doesn't drop the app mid-note. */
    fun thenFinish(pending: BroadcastReceiver.PendingResult) {
        if (player == null) {
            pending.finish()
            return
        }
        done = { pending.finish() }
    }

    private fun rung(player: MediaPlayer) {
        player.release()
        if (this.player === player) this.player = null
        done?.invoke()
        done = null
    }
}
