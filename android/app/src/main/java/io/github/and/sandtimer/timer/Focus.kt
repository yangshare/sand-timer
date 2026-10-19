package io.github.and.sandtimer.timer

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import io.github.and.sandtimer.R
import io.github.and.sandtimer.data.Store
import io.github.and.sandtimer.ui.MainActivity

/**
 * Focus while the sand runs: Sand Timer's own Do Not Disturb mode, on while the sand runs and off at a pause, an end
 * or the sand running out. It appears among the phone's modes as "Sand Timer", where what it lets through can be
 * changed; until then it follows the phone's own Do Not Disturb settings. Android asks once for permission to
 * manage Do Not Disturb.
 */
object Focus {
    private val condition: Uri = Uri.parse("condition://io.github.and.sandtimer/running")

    fun allowed(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    /** Android's page for allowing it. */
    fun ask(context: Context) =
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** Matches the mode to the timer: on while the sand runs, if Focus is turned on and allowed. */
    fun update(context: Context, running: Boolean) {
        if (!allowed(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val on = running && Store.state.value.settings.focus
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Before Android 10 a rule can't be switched by hand; the phone's filter itself is set instead.
            filter(context, manager, on)
            return
        }
        val id = rule(context, manager, create = on) ?: return
        runCatching {
            manager.setAutomaticZenRuleState(
                id, Condition(condition, if (on) context.getString(R.string.focus_condition_running) else "", if (on) Condition.STATE_TRUE else Condition.STATE_FALSE),
            )
        }
    }

    /** The old way: Do Not Disturb's priority filter on while the sand runs, and what the phone had before back after. */
    private fun filter(context: Context, manager: NotificationManager, on: Boolean) {
        val prefs = context.getSharedPreferences("sandtimer", Context.MODE_PRIVATE)
        if (on) {
            val current = manager.currentInterruptionFilter
            if (current != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                prefs.edit().putInt("focus.filter", current).apply()
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
        } else if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
            val back = prefs.getInt("focus.filter", NotificationManager.INTERRUPTION_FILTER_ALL)
            manager.setInterruptionFilter(back)
        }
    }

    /** The mode's id, made the first time it's needed. */
    private fun rule(context: Context, manager: NotificationManager, create: Boolean): String? {
        val prefs = context.getSharedPreferences("sandtimer", Context.MODE_PRIVATE)
        prefs.getString("focus.rule", null)?.let { if (manager.getAutomaticZenRule(it) != null) return it }
        if (!create) return null
        val rule = AutomaticZenRule(
            context.getString(R.string.app_name), null, ComponentName(context, MainActivity::class.java), condition, null,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY, true,
        )
        val id = runCatching { manager.addAutomaticZenRule(rule) }.getOrNull() ?: return null
        prefs.edit().putString("focus.rule", id).apply()
        return id
    }
}
