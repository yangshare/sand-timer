package io.github.and.sandtimer.timer

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import io.github.and.sandtimer.R
import java.io.File
import java.io.IOException

/**
 * Keeps the sound of the falling sand going while the glass runs, the way a real hourglass murmurs, and stops it the
 * moment the sand doesn't fall. The person may choose a sound of their own from the phone, which is copied under the
 * app's own roof so it stays whatever becomes of the original; without one, a built-in rush of sand plays, in a loop
 * the ear can't find the join of. Plays on the media stream, quietly. Unlike [Chime] this is a screen's sound: it
 * plays only while the timer is showing, and stops when the phone is put away.
 */
object SandSound {
    /** The chosen sound lives under a name of its own, replaced at each new choosing. */
    private const val FILE = "sand-sound"
    /** A sound bigger than this is surely not a sound. */
    private const val MAX_BYTES = 30_000_000L

    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

    private var player: MediaPlayer? = null
    private var previewer: MediaPlayer? = null

    /** Starts the sand, or stops it, to match whether the glass is running in front of the person. */
    fun update(context: Context, playing: Boolean) {
        if (!playing) {
            stop()
            return
        }
        if (player != null) return
        val player = MediaPlayer()
        runCatching {
            player.setAudioAttributes(attrs)
            setSource(player, context)
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

    /** Plays the sound once through, to try it: the chosen one if there is one, the built-in sand if not. */
    fun preview(context: Context) {
        stopPreview()
        val player = MediaPlayer()
        runCatching {
            player.setAudioAttributes(attrs)
            setSource(player, context)
            player.setOnCompletionListener { finished ->
                runCatching { finished.release() }
                if (previewer === finished) previewer = null
            }
            player.prepare()
            player.start()
            previewer = player
        }.onFailure {
            player.release()
        }
    }

    /** Stops a preview still playing, as when the settings screen is left or another begins. */
    fun stopPreview() {
        val player = previewer ?: return
        previewer = null
        runCatching { if (player.isPlaying) player.stop() }
        player.release()
    }

    /** The sound the person chose, kept here; gone if none was ever chosen or it was given up. */
    fun chosen(context: Context): File? = File(context.applicationContext.filesDir, FILE).takeIf { it.length() > 0 }

    /**
     * Takes a copy of the sound picked from the phone's files and answers with its name for showing. From then on the
     * original can move or go, and the sound stays. Too big or unreadable a pick is refused.
     */
    fun choose(context: Context, uri: Uri): String {
        val app = context.applicationContext
        val resolver = app.contentResolver
        val name = column(resolver, uri, OpenableColumns.DISPLAY_NAME) ?: "sound"
        val size = column(resolver, uri, OpenableColumns.SIZE)?.toLongOrNull() ?: 0L
        val temp = File(app.filesDir, "$FILE.tmp")
        try {
            if (size > MAX_BYTES) throw IOException(name)
            resolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException(name)
            if (temp.length() == 0L || temp.length() > MAX_BYTES) throw IOException(name)
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
        if (!temp.renameTo(File(app.filesDir, FILE))) {
            temp.delete()
            throw IOException(name)
        }
        return name
    }

    /** Lets the chosen sound go, and the built-in sand takes its place. */
    fun giveUp(context: Context) {
        stop()
        stopPreview()
        chosen(context)?.delete()
    }

    private fun column(resolver: ContentResolver, uri: Uri, column: String): String? =
        resolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /** Points a player at the chosen sound, or at the built-in sand where there is none. */
    private fun setSource(player: MediaPlayer, context: Context) {
        val app = context.applicationContext
        val chosen = chosen(app)
        if (chosen != null) {
            player.setDataSource(chosen.absolutePath)
        } else {
            app.resources.openRawResourceFd(R.raw.sand).use { wav ->
                player.setDataSource(wav.fileDescriptor, wav.startOffset, wav.length)
            }
        }
    }
}
