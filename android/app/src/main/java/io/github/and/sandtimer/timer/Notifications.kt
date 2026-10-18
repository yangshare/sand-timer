package io.github.and.sandtimer.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import io.github.and.sandtimer.R
import io.github.and.sandtimer.data.ProjectList
import io.github.and.sandtimer.data.Store
import io.github.and.sandtimer.data.TimerState
import io.github.and.sandtimer.ui.MainActivity

/**
 * The notification that stands in for the timer while you're elsewhere — counting down, with Pause and End — and
 * the one that chimes when the sand runs out.
 */
object Notifications {
    private const val RUNNING = "running"
    private const val FINISHED = "finished"
    private const val FINISHED_QUIET = "finished-quiet"
    private const val ONGOING_ID = 1
    private const val FINISHED_ID = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(RUNNING, context.getString(R.string.notif_channel_running), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.notif_channel_running_desc)
            setShowBadge(false)
        })
        manager.createNotificationChannel(NotificationChannel(FINISHED, context.getString(R.string.notif_times_up), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.notif_channel_finished_desc)
            setSound(
                Uri.parse("android.resource://${context.packageName}/${R.raw.chime}"),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
        })
        manager.createNotificationChannel(NotificationChannel(FINISHED_QUIET, context.getString(R.string.notif_channel_quiet), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.notif_channel_quiet_desc)
            setSound(null, null)
        })
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

    /** The sand ran out: a chime, unless it's switched off, and a word to say so. */
    fun finished(context: Context, state: TimerState) {
        val s = Store.state.value
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(ONGOING_ID)
        val project = s.projects.project(state.project)?.name
        val span = context.resources.getQuantityString(R.plurals.notif_minutes, state.minutes, state.minutes)
        manager.notify(FINISHED_ID, Notification.Builder(context, if (s.settings.chime) FINISHED else FINISHED_QUIET)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(color(s.projects, state.project))
            .setContentTitle(context.getString(R.string.notif_times_up))
            .setContentText(project?.let { context.getString(R.string.notif_finished_of, span, it) } ?: context.getString(R.string.notif_finished, span))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .build())
    }

    fun clock(ms: Long): String {
        val total = (ms + 999) / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }
}
