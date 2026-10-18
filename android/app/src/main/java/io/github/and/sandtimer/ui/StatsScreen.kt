package io.github.and.sandtimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.and.sandtimer.R
import io.github.and.sandtimer.data.ProjectList
import io.github.and.sandtimer.data.SandLog
import io.github.and.sandtimer.data.Store
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The record, by hour, day, week, month or year, each bar split by project — this phone's and any linked Mac's. */
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val app by Store.state.collectAsState()
    val res = LocalContext.current.resources
    var periodIndex by rememberSaveable { mutableIntStateOf(1) }
    val period = SandLog.Period.entries[periodIndex]
    val log = app.wholeLog
    val buckets = remember(log, period) { log.buckets(period, LocalDateTime.now()) }
    var selected by remember(period) { mutableStateOf(buckets.lastIndex) }
    val bucket = buckets.getOrNull(selected) ?: buckets.last()

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SandLog.Period.entries.forEachIndexed { index, p ->
                SegmentedButton(
                    selected = index == periodIndex, onClick = { periodIndex = index },
                    shape = SegmentedButtonDefaults.itemShape(index, SandLog.Period.entries.size),
                    label = { Text(stringResource(periodName(p)), fontSize = 12.sp, maxLines = 1) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(bucketTitle(bucket, period), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        Text(durationLabel(res, bucket.total.seconds), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Medium)
        Text(stringResource(R.string.stats_finished, timersLabel(res, bucket.total.finished)), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Breakdown(bucket.projects, app.projects)
        Spacer(Modifier.height(16.dp))
        Chart(buckets, app.projects, selected) { selected = it }
        Spacer(Modifier.height(16.dp))
        Legend(log.projectIds, app.projects)
        Spacer(Modifier.height(16.dp))

        val all = log.allTime
        val since = log.firstDay()?.format(DateTimeFormatter.ofPattern(stringResource(R.string.stats_pattern_date_full)))
        if (since != null) {
            Text(
                stringResource(R.string.stats_since, since, durationLabel(res, all.seconds), timersLabel(res, all.finished)),
                color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
            )
        }
        if (app.link.linked) {
            Text(stringResource(R.string.stats_includes_mac), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

/** The word for a span of the record, as the segmented buttons say it. */
private fun periodName(p: SandLog.Period) = when (p) {
    SandLog.Period.HOURLY -> R.string.stats_period_hourly
    SandLog.Period.DAILY -> R.string.stats_period_daily
    SandLog.Period.WEEKLY -> R.string.stats_period_weekly
    SandLog.Period.MONTHLY -> R.string.stats_period_monthly
    SandLog.Period.YEARLY -> R.string.stats_period_yearly
}

/** The selected bar in words: a named span, or its start as the date the app's language writes it. */
@Composable
private fun bucketTitle(bucket: SandLog.Bucket, period: SandLog.Period): String = when (bucket.title) {
    SandLog.Bucket.Title.THIS_HOUR -> stringResource(R.string.stats_title_this_hour)
    SandLog.Bucket.Title.LAST_HOUR -> stringResource(R.string.stats_title_last_hour)
    SandLog.Bucket.Title.TODAY -> stringResource(R.string.stats_title_today)
    SandLog.Bucket.Title.YESTERDAY -> stringResource(R.string.stats_title_yesterday)
    SandLog.Bucket.Title.THIS_WEEK -> stringResource(R.string.stats_title_this_week)
    SandLog.Bucket.Title.THIS_MONTH -> stringResource(R.string.stats_title_this_month)
    SandLog.Bucket.Title.THIS_YEAR -> stringResource(R.string.stats_title_this_year)
    SandLog.Bucket.Title.DATED -> when (period) {
        SandLog.Period.HOURLY -> bucket.start.format(DateTimeFormatter.ofPattern(stringResource(R.string.stats_pattern_hourly)))
        SandLog.Period.DAILY -> bucket.start.format(DateTimeFormatter.ofPattern(stringResource(R.string.stats_pattern_daily)))
        SandLog.Period.WEEKLY -> stringResource(
            R.string.stats_title_week_of,
            bucket.start.format(DateTimeFormatter.ofPattern(stringResource(R.string.stats_pattern_day_short))),
        )
        SandLog.Period.MONTHLY -> bucket.start.format(DateTimeFormatter.ofPattern(stringResource(R.string.stats_pattern_month)))
        SandLog.Period.YEARLY -> bucket.start.year.toString()
    }
}

@Composable
private fun Breakdown(projects: Map<String, io.github.and.sandtimer.data.Tally>, list: ProjectList) {
    val res = LocalContext.current.resources
    val parts = list.ordered(projects.keys).mapNotNull { id -> projects[id]?.takeIf { it.seconds >= 1 }?.let { id to it } }
    if (parts.size < 2 && parts.firstOrNull()?.first.isNullOrEmpty()) return
    Spacer(Modifier.height(6.dp))
    for ((id, tally) in parts) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(projectColor(id, list)))
            Text(
                stringResource(R.string.stats_legend_item, list.displayName(id), durationLabel(res, tally.seconds)),
                color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp,
            )
        }
    }
}

private fun projectColor(id: String, list: ProjectList): Color =
    if (id.isEmpty()) Color(0xFF8A8F98) else sandColor(list.project(id)?.color ?: "#8A8F98")

@Composable
private fun Chart(buckets: List<SandLog.Bucket>, list: ProjectList, selected: Int, select: (Int) -> Unit) {
    val most = buckets.maxOf { it.total.seconds }.coerceAtLeast(60.0)
    Canvas(
        Modifier.fillMaxWidth().height(180.dp).pointerInput(buckets.size) {
            detectTapGestures { offset -> select((offset.x / (size.width / buckets.size)).toInt().coerceIn(0, buckets.lastIndex)) }
        },
    ) {
        val slot = size.width / buckets.size
        val barWidth = slot * 0.62f
        val chartHeight = size.height - 18.dp.toPx()
        // Round amounts of time to read the bars against.
        val step = listOf(300.0, 600.0, 900.0, 1800.0, 3600.0, 7200.0, 10800.0, 21600.0, 43200.0, 86400.0)
            .firstOrNull { most / it <= 4 } ?: 86400.0
        var line = step
        while (line <= most) {
            val y = chartHeight - (line / most * chartHeight).toFloat()
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            line += step
        }
        buckets.forEachIndexed { index, bucket ->
            val x = index * slot + (slot - barWidth) / 2
            var top = chartHeight
            val dim = if (index == selected) 1f else 0.7f
            for (id in list.ordered(bucket.projects.keys)) {
                val seconds = bucket.projects[id]?.seconds ?: continue
                val h = (seconds / most * chartHeight).toFloat()
                if (h < 0.5f) continue
                top -= h
                drawRoundRect(
                    projectColor(id, list).copy(alpha = dim), topLeft = Offset(x, top), size = Size(barWidth, h),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            if (index == selected) {
                drawRoundRect(
                    Color.White.copy(alpha = 0.06f), topLeft = Offset(index * slot, 0f), size = Size(slot, chartHeight),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            val every = if (buckets.size > 14) 3 else 1
            if (index % every == 0 || index == buckets.lastIndex) {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(if (index == selected) 230 else 130, 255, 255, 255)
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(bucket.label, index * slot + slot / 2, size.height - 2.dp.toPx(), paint)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(ids: Set<String>, list: ProjectList) {
    if (ids.subtract(setOf("")).isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (id in list.ordered(ids)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(projectColor(id, list)))
                Text(" " + list.displayName(id), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
    }
}
