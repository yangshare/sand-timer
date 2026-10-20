package io.github.and.sandtimer.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import io.github.and.sandtimer.R
import io.github.and.sandtimer.data.ProjectList
import io.github.and.sandtimer.data.Store
import io.github.and.sandtimer.data.TimerState
import io.github.and.sandtimer.ui.MainActivity

/**
 * The notification that stands in for the timer while you're elsewhere — counting down, with Pause and End — and the
 * one that says the sand has run out. The chime is rung by [Chime] rather than the notification: some phones play no
 * notification sound at all, and a channel's sound, once made, can never be changed.
 */
object Notifications {
    private const val RUNNING = "running"
    private const val DONE = "done"
    private const val LEGACY_FINISHED = "finished"
    private const val LEGACY_FINISHED_QUIET = "finished-quiet"
    private const val ONGOING_ID = 1
    private const val FINISHED_ID = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(RUNNING, context.getString(R.string.notif_channel_running), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.notif_channel_running_desc)
            setShowBadge(false)
        })
        manager.createNotificationChannel(NotificationChannel(DONE, context.getString(R.string.notif_times_up), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.notif_channel_done_desc)
            setSound(null, null)
            setShowBadge(false)
        })
        // The channels of earlier builds, whose chime lived in the notification where phones could silence it.
        manager.deleteNotificationChannel(LEGACY_FINISHED)
        manager.deleteNotificationChannel(LEGACY_FINISHED_QUIET)
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(context: Context, name: String, title: String): Notification.Action {
        val intent = PendingIntent.getBroadcast(
            context, name.hashCode(), Intent(context, ActionReceiver::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(null, title, intent).build()
    }

    private fun color(projects: ProjectList, id: String?): Int =
        runCatching { Color.parseColor(projects.project(id)?.color ?: "#C8963E") }.getOrDefault(Color.rgb(200, 150, 62))

    /** Shows the timer as it is, or takes the notification away when it is waiting to be flipped. */
    fun update(context: Context, state: TimerState, projects: ProjectList) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val now = System.currentTimeMillis()
        if (!state.inSession(now)) {
            manager.cancel(ONGOING_ID)
            return
        }
        val project = projects.project(state.project)?.name
        val builder = Notification.Builder(context, RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(color(projects, state.project))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setContentIntent(openApp(context))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (state.isRunning(now)) {
            builder.setContentTitle(project ?: context.getString(R.string.notif_title_running))
                .setContentText(context.resources.getQuantityString(R.plurals.notif_minutes_timer, state.minutes, state.minutes))
                .setWhen(state.runningUntil!!)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(action(context, ActionReceiver.ACTION_PAUSE, context.getString(R.string.timer_pause)))
        } else {
            builder.setContentTitle(context.getString(R.string.status_paused_left, clock(state.remaining(now))))
                .setContentText(project ?: context.resources.getQuantityString(R.plurals.notif_minutes_timer, state.minutes, state.minutes))
                .setShowWhen(false)
                .addAction(action(context, ActionReceiver.ACTION_RESUME, context.getString(R.string.timer_resume)))
        }
        builder.addAction(action(context, ActionReceiver.ACTION_END, context.getString(R.string.notif_action_end)))
        manager.notify(ONGOING_ID, builder.build())
    }

    /** The sand ran out: the chime, rung by the app itself unless it's switched off, and a word to say so. */
    fun finished(context: Context, state: TimerState) {
        val s = Store.state.value
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(ONGOING_ID)
        val project = s.projects.project(state.project)?.name
        val span = context.resources.getQuantityString(R.plurals.notif_minutes, state.minutes, state.minutes)
        val builder = Notification.Builder(context, DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(color(s.projects, state.project))
            .setContentTitle(context.getString(R.string.notif_times_up))
            .setContentText(project?.let { context.getString(R.string.notif_finished_of, span, it) }
                ?: context.getString(R.string.notif_finished, span))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
        if (s.settings.chime) {
            Chime.play(context)
            // The button is worth its room only while something is sounding to stop.
            if (Chime.ringing.value) {
                builder.addAction(action(context, ActionReceiver.ACTION_STOP_CHIME, context.getString(R.string.notif_action_stop_chime)))
            }
        }
        manager.notify(FINISHED_ID, builder.build())
    }

    fun clock(ms: Long): String {
        val total = (ms + 999) / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }
}
