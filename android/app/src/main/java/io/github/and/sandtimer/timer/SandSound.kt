package io.github.and.sandtimer.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import io.github.and.sandtimer.R

/**
 * Keeps a soft rush of falling sand going while the glass runs, the way a real hourglass murmurs, and stops it the
 * moment the sand doesn't fall. Plays on the media stream, quietly, in a loop the ear can't find the join of. Unlike
 * [Chime] this is a screen's sound: it plays only while the timer is showing, and stops when the phone is put away.
 */
object SandSound {
    private var player: MediaPlayer? = null

    /** Starts the sand, or stops it, to match whether the glass is running in front of the person. */
    fun update(context: Context, playing: Boolean) {
        if (!playing) {
            stop()
            return
        }
        if (player != null) return
        val player = MediaPlayer()
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            )
            val app = context.applicationContext
            val wav = app.resources.openRawResourceFd(R.raw.sand)
            player.setDataSource(wav.fileDescriptor, wav.startOffset, wav.length)
            wav.close()
            player.isLooping = true
            player.setVolume(0.8f, 0.8f)
            player.prepare()
            player.start()
            this.player = player
        }.onFailure {
            player.release()
        }
    }

    /** Silences the sand, as when the glass is paused, put away, or the sand has run out. */
    fun stop() {
        val player = player ?: return
        this.player = null
        runCatching { player.stop() }
        player.release()
    }
}
