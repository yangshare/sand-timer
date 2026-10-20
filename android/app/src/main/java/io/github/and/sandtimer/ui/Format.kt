package io.github.and.sandtimer.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.and.sandtimer.R
import io.github.and.sandtimer.data.ProjectList

/** "12s", "45m", "3h 20m", "3h", said the way the app's language says it. */
fun durationLabel(res: Resources, seconds: Double): String {
    val total = Math.round(seconds).toInt()
    if (total < 60) return res.getQuantityString(R.plurals.duration_seconds, total, total)
    val minutes = total / 60
    if (minutes < 60) return res.getQuantityString(R.plurals.duration_minutes, minutes, minutes)
    val rest = minutes % 60
    return if (rest == 0) res.getString(R.string.duration_hours, minutes / 60)
    else res.getString(R.string.duration_hours_minutes, minutes / 60, rest)
}

/** "1 timer", "3 timers". */
fun timersLabel(res: Resources, count: Int): String =
    res.getQuantityString(R.plurals.timers_count, count, count)

/** A project's name as shown: the stored name, or a word for the missing ones. */
@Composable
fun ProjectList.displayName(id: String?): String =
    if (id.isNullOrEmpty()) stringResource(R.string.project_none)
    else project(id)?.name ?: stringResource(R.string.project_unknown)
