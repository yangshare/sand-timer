package io.github.and.sandtimer.ui

import android.Manifest
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.github.and.sandtimer.BuildConfig
import io.github.and.sandtimer.R
import io.github.and.sandtimer.data.Project
import io.github.and.sandtimer.data.ProjectList
import io.github.and.sandtimer.data.Store
import io.github.and.sandtimer.link.BadCodeException
import io.github.and.sandtimer.link.Link
import io.github.and.sandtimer.timer.Chime
import io.github.and.sandtimer.timer.Engine
import io.github.and.sandtimer.timer.Focus
import io.github.and.sandtimer.timer.SandSound

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val app by Store.state.collectAsState()
    val res = LocalContext.current.resources
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        LinkSection()
        Divider()
        Heading(stringResource(R.string.settings_projects_title), stringResource(R.string.settings_projects_hint))
        ProjectsEditor(app.projects)
        Divider()
        Heading(stringResource(R.string.settings_target_title), stringResource(R.string.settings_target_hint))
        val target = app.settings.targetMinutes
        SwitchRow(stringResource(R.string.settings_target_toggle), target > 0) { on -> Store.updateSettings { it.copy(targetMinutes = if (on) 60 else 0) } }
        if (target > 0) {
            Text(stringResource(R.string.settings_target_value, durationLabel(res, target * 60.0)), color = Color.White.copy(alpha = 0.75f))
            Slider(
                value = target.toFloat(), valueRange = 15f..720f, steps = 46,
                onValueChange = { v -> Store.updateSettings { it.copy(targetMinutes = (v / 15).toInt() * 15) } },
            )
        }
        Divider()
        Heading(stringResource(R.string.settings_timer_title), null)
        SwitchRow(stringResource(R.string.settings_chime), app.settings.chime) { on ->
            if (!on) Chime.stop()  // turning the bell off quiets one still ringing
            Store.updateSettings { it.copy(chime = on) }
        }
        SandSoundRow(app.settings.sandSound, app.settings.sandSoundName)
        SwitchRow(stringResource(R.string.settings_one_thing), app.settings.oneThingAtATime) { on -> Store.updateSettings { it.copy(oneThingAtATime = on) } }
        Note(stringResource(R.string.settings_one_thing_summary))
        Spacer(Modifier.height(8.dp))
        SwitchRow(stringResource(R.string.settings_keep_screen_on), app.settings.keepScreenOn) { on -> Store.updateSettings { it.copy(keepScreenOn = on) } }
        Note(stringResource(R.string.settings_keep_screen_on_summary))
        Spacer(Modifier.height(8.dp))
        SwitchRow(stringResource(R.string.settings_clean_view), app.settings.calm) { on -> Store.updateSettings { it.copy(calm = on) } }
        Note(stringResource(R.string.settings_clean_view_summary))
        Spacer(Modifier.height(8.dp))
        FocusRow(app.settings.focus)
        Divider()
        Note(stringResource(R.string.settings_about, BuildConfig.VERSION_NAME))
    }
}

/** Linking to a Mac, like linking a phone to a messaging account: scan the Mac's code, and share from then on. */
@Composable
private fun LinkSection() {
    val context = LocalContext.current
    val app by Store.state.collectAsState()
    var problem by remember { mutableStateOf<String?>(null) }
    val link = app.link

    fun scan() {
        problem = null
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code ->
                try {
                    Link.link(code.rawValue.orEmpty())
                } catch (e: BadCodeException) {
                    problem = context.getString(e.messageRes)
                } catch (e: Exception) {
                    problem = e.message ?: context.getString(R.string.link_problem_generic)
                }
            }
            .addOnFailureListener { problem = it.message ?: context.getString(R.string.link_problem_scanner) }
    }
    // Bluetooth is asked for only when linking is turned on.
    val turnOn = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) Link.setEnabled(true) else problem = context.getString(R.string.link_problem_bluetooth_permission)
    }
    val bluetooth = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

    Heading(stringResource(R.string.settings_link_title), null)
    Note(stringResource(R.string.settings_link_intro))
    SwitchRow(stringResource(R.string.settings_link_toggle), link.enabled) { on ->
        problem = null
        when {
            !on -> Link.setEnabled(false)
            Link.radio.allowed -> Link.setEnabled(true)
            else -> turnOn.launch(bluetooth)
        }
    }
    if (link.enabled && !link.linked) {
        Note(stringResource(R.string.settings_link_instructions))
        Spacer(Modifier.height(8.dp))
        Button(onClick = ::scan) { Text(stringResource(R.string.settings_link_scan)) }
    } else if (link.enabled) {
        if (link.devices.isEmpty()) Note(stringResource(R.string.settings_link_searching))
        for (device in link.devices) {
            Text(device.name, color = Color.White, fontWeight = FontWeight.Medium)
            Note(
                if (device.id in link.connected) stringResource(R.string.settings_link_connected)
                else stringResource(
                    R.string.settings_link_last_seen,
                    DateUtils.getRelativeTimeSpanString((device.seen * 1000).toLong()).toString(),
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(8.dp))
        OpenOnMacStartRow(app.settings.openOnMacStart)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!Link.radio.allowed) OutlinedButton(onClick = { turnOn.launch(bluetooth) }) { Text(stringResource(R.string.settings_link_allow_bluetooth)) }
            TextButton(onClick = { Link.unlink() }) { Text(stringResource(R.string.settings_link_unlink)) }
        }
    }
    (problem ?: link.problem)?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp) }
}

/** Coming to the front when the Mac starts the timer: Android asks for "Display over other apps" first. */
@Composable
private fun OpenOnMacStartRow(on: Boolean) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val watcher = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) allowed = android.provider.Settings.canDrawOverlays(context)
        }
        lifecycle.lifecycle.addObserver(watcher)
        onDispose { lifecycle.lifecycle.removeObserver(watcher) }
    }
    SwitchRow(stringResource(R.string.settings_overlay_title), on && allowed) { turnOn ->
        Store.updateSettings { it.copy(openOnMacStart = turnOn) }
        if (turnOn && !android.provider.Settings.canDrawOverlays(context)) {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}")),
            )
        }
    }
    Note(
        if (on && !allowed) stringResource(R.string.settings_overlay_pending)
        else stringResource(R.string.settings_overlay_summary),
    )
}

/** The sound of the falling sand: on or off, and which sound — the built-in one, or a file of the person's choosing. */
@Composable
private fun SandSoundRow(on: Boolean, name: String?) {
    val context = LocalContext.current
    var problem by remember { mutableStateOf<String?>(null) }
    // A preview still playing is taken along when the settings are left.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { SandSound.stopPreview() }
    }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        problem = runCatching {
            val picked = SandSound.choose(context, uri)
            Store.updateSettings { it.copy(sandSoundName = picked) }
            SandSound.preview(context)  // so the person hears what they picked
            null
        }.getOrElse { context.getString(R.string.settings_sand_sound_failed) }
    }
    SwitchRow(stringResource(R.string.settings_sand_sound), on) { turn ->
        problem = null
        Store.updateSettings { it.copy(sandSound = turn) }
    }
    Note(stringResource(R.string.settings_sand_sound_summary))
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { problem = null; choose.launch(arrayOf("audio/*")) }) {
            Text(stringResource(R.string.settings_sand_sound_choose))
        }
        OutlinedButton(onClick = { SandSound.preview(context) }) {
            Text(stringResource(R.string.settings_sand_sound_preview))
        }
    }
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Note(stringResource(R.string.settings_sand_sound_current, name ?: stringResource(R.string.settings_sand_sound_default)))
        if (name != null) {
            TextButton(onClick = {
                SandSound.giveUp(context)
                Store.updateSettings { it.copy(sandSoundName = null) }
            }) { Text(stringResource(R.string.settings_sand_sound_reset)) }
        }
    }
    problem?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp) }
}

/** Do Not Disturb while the sand runs. Turning it on asks Android for permission to manage it, once. */
@Composable
private fun FocusRow(on: Boolean) {
    val context = LocalContext.current
    // Coming back from Android's permission page: the switch shows what was decided there.
    var allowed by remember { mutableStateOf(Focus.allowed(context)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val watcher = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                allowed = Focus.allowed(context)
                Engine.sync(context)
            }
        }
        lifecycle.lifecycle.addObserver(watcher)
        onDispose { lifecycle.lifecycle.removeObserver(watcher) }
    }
    SwitchRow(stringResource(R.string.settings_focus_title), on && allowed) { turnOn ->
        Store.updateSettings { it.copy(focus = turnOn) }
        if (turnOn && !Focus.allowed(context)) Focus.ask(context)
        Engine.sync(context)
    }
    Note(
        if (on && !allowed) stringResource(R.string.settings_focus_pending)
        else stringResource(R.string.settings_focus_summary),
    )
}

@Composable
private fun ProjectsEditor(projects: ProjectList) {
    for (project in projects.visible) ProjectRow(project, projects)
    Spacer(Modifier.height(6.dp))
    // The name is said in the app's language now, and kept like any name a person typed.
    val newName = stringResource(R.string.project_new_name, projects.visible.size + 1)
    OutlinedButton(onClick = {
        val used = projects.visible.map { it.color }.toSet()
        val color = ProjectList.palette.firstOrNull { it !in used } ?: ProjectList.palette[projects.all.size % ProjectList.palette.size]
        Store.updateProjects(projects.copy(all = projects.all + Project(name = newName, color = color)))
    }) { Text(stringResource(R.string.project_add)) }
}

@Composable
private fun ProjectRow(project: Project, projects: ProjectList) {
    var name by remember(project.id) { mutableStateOf(project.name) }
    var choosing by remember { mutableStateOf(false) }
    fun change(edit: (Project) -> Project) =
        Store.updateProjects(projects.copy(all = projects.all.map { if (it.id == project.id) edit(it) else it }))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(sandColor(project.color)).clickable { choosing = !choosing })
        Spacer(Modifier.width(10.dp))
        OutlinedTextField(
            value = name, singleLine = true, modifier = Modifier.weight(1f),
            onValueChange = { text ->
                name = text
                if (text.isNotBlank()) change { it.copy(name = text.trim()) }
            },
        )
        IconButton(onClick = { change { it.copy(archived = true) } }) {
            Icon(Icons.Filled.RemoveCircleOutline, contentDescription = stringResource(R.string.project_remove, project.name), tint = Color.White.copy(alpha = 0.6f))
        }
    }
    if (choosing) {
        Row(Modifier.padding(start = 38.dp, top = 6.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (hex in ProjectList.palette) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(sandColor(hex))
                        .border(2.dp, if (hex.equals(project.color, true)) Color.White else Color.Transparent, CircleShape)
                        .clickable { change { it.copy(color = hex) }; choosing = false },
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Heading(title: String, hint: String?) {
    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    if (hint != null) Note(hint)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Note(text: String) = Text(text, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)

@Composable
private fun SwitchRow(title: String, on: Boolean, toggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = toggle)
    }
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    Spacer(Modifier.height(16.dp))
}
