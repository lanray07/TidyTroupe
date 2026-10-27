package com.tidytroupe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource as s
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tidytroupe.data.*
import com.tidytroupe.core.VoiceCommand
import com.tidytroupe.core.VoiceParser
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun Onboarding(vm: TidyViewModel) {
    var name by remember { mutableStateOf("") }; var nickname by remember { mutableStateOf("") }; var pin by remember { mutableStateOf("") }; var confirmPin by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) { Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("tidytroupe", color = MaterialTheme.colorScheme.primary, fontSize = 28.sp)
        Image(painterResource(R.drawable.onboarding_art), null, Modifier.fillMaxWidth().heightIn(max = 260.dp))
        Text(s(R.string.welcome), fontFamily = FontFamily.Serif, fontSize = 38.sp, lineHeight = 42.sp)
        Text(s(R.string.welcome_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(s(R.string.language_pending), style = MaterialTheme.typography.bodySmall)
        Field(name, { name = it.take(80) }, s(R.string.household_name))
        Field(nickname, { nickname = it.take(40) }, s(R.string.nickname))
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(12) }, label = { Text(s(R.string.pin)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true)
        OutlinedTextField(confirmPin, { confirmPin = it.filter(Char::isDigit).take(12) }, label = { Text(s(R.string.confirm_pin)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Text(s(R.string.pin_help), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { vm.setup(name, nickname, pin) }, enabled = name.isNotBlank() && nickname.isNotBlank() && pin.length >= 6 && pin == confirmPin, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(s(R.string.start)) }
        Text(s(R.string.local_private), color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp, fontSize = 10.sp)
    } }
}

@Composable internal fun PinDialog(vm: TidyViewModel, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.Lock, null) }, title = { Text(s(R.string.parent_area)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Text(s(R.string.unlock_help)); OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(12) }, label = { Text(s(R.string.pin)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation()) } }, confirmButton = { TextButton(enabled = pin.length >= 6, onClick = { vm.unlock(pin, onSuccess); pin = "" }) { Text(s(R.string.unlock)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(s(R.string.cancel)) } })
}

@Composable internal fun BackupPasswordDialog(export: Boolean, dismiss: () -> Unit, submit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }; var confirmation by remember { mutableStateOf("") }
    Editor(s(if (export) R.string.encrypted_export else R.string.encrypted_import), dismiss, { submit(password); password = ""; confirmation = "" }, password.length >= 12 && (!export || password == confirmation)) {
        Text(s(R.string.backup_help))
        OutlinedTextField(password, { password = it.take(1024) }, label = { Text(s(R.string.backup_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        if (export) OutlinedTextField(confirmation, { confirmation = it.take(1024) }, label = { Text(s(R.string.backup_password_confirm)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    }
}

@Composable internal fun Field(value: String, change: (String) -> Unit, label: String, numeric: Boolean = false) { OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text)) }
@Composable internal fun Editor(title: String, dismiss: () -> Unit, save: () -> Unit, enabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss) { Surface(shape = RoundedCornerShape(26.dp)) { Column(Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif); content(); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = dismiss) { Text(s(R.string.cancel)) }; Button(onClick = save, enabled = enabled) { Text(s(R.string.save)) } } } } }
}
@Composable internal fun <T> Dropdown(label: String, selected: T, options: List<Pair<T, String>>, change: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Box { OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) { Text(options.find { it.first == selected }?.second ?: "", Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null) }; DropdownMenu(open, { open = false }) { options.forEach { (key, value) -> DropdownMenuItem(text = { Text(value) }, onClick = { change(key); open = false }) } } } }
}

@Composable internal fun ChoreEditor(original: Chore?, members: List<Member>, onDismiss: () -> Unit, save: (Chore) -> Unit) {
    val chore = remember { original ?: Chore(title = "") }
    var title by remember { mutableStateOf(chore.title) }; var notes by remember { mutableStateOf(chore.notes) }; var stars by remember { mutableStateOf(chore.points.toString()) }; var due by remember { mutableStateOf(chore.due) }; var category by remember { mutableStateOf(chore.category) }; var member by remember { mutableStateOf(chore.memberId) }; var repeat by remember { mutableStateOf(chore.repeat) }; var approval by remember { mutableStateOf(chore.approval) }
    var interval by remember { mutableStateOf(chore.repeatInterval.toString()) }; var weekdays by remember { mutableStateOf(chore.weekdays) }; var effort by remember { mutableStateOf(chore.effort) }
    val context = LocalContext.current
    var reminder by remember { mutableStateOf(chore.reminderMinutes != null) }
    var reminderTime by remember { mutableStateOf(chore.reminderMinutes?.let { "%02d:%02d".format(Locale.ROOT, it / 60, it % 60) } ?: "18:00") }
    val reminderMinute = runCatching { java.time.LocalTime.parse(reminderTime).let { it.hour * 60 + it.minute } }.getOrNull()
    var reminderDenied by remember { mutableStateOf(false) }
    val reminderPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> reminder = granted; reminderDenied = !granted }
    val categories = listOf("Bedroom" to R.string.bedroom,"Kitchen" to R.string.kitchen,"Bathroom" to R.string.bathroom,"Laundry" to R.string.laundry,"Living Room" to R.string.living_room,"Garden" to R.string.garden,"Pets" to R.string.pets,"School Preparation" to R.string.school,"General Household" to R.string.general)
    Editor(s(if (original == null) R.string.add_chore else R.string.edit), onDismiss, { save(chore.copy(title = title.trim(), notes = notes, points = stars.toInt(), due = due, category = category, memberId = member, repeat = repeat, approval = approval, repeatInterval = interval.toInt(), weekdays = weekdays, effort = effort, reminderMinutes = if (reminder) reminderMinute else null)) }, title.isNotBlank() && stars.toIntOrNull() in 0..1000 && runCatching { LocalDate.parse(due) }.isSuccess && interval.toIntOrNull() in 1..365 && (repeat != "SELECTED" || weekdays.isNotBlank()) && (!reminder || reminderMinute in 480..1199)) {
        Field(title, { title = it.take(160) }, s(R.string.title))
        Dropdown(s(R.string.assigned_to), member, listOf<String?>(null).map { it to s(R.string.unassigned) } + members.map { it.id as String? to it.nickname }) { member = it }
        Dropdown(s(R.string.category), category, categories.map { it.first to s(it.second) } + if (categories.none { it.first == category }) listOf(category to category) else emptyList()) { category = it }
        Field(category, { category = it.take(80) }, s(R.string.custom_category))
        Field(due, { due = it }, s(R.string.due_date))
        Dropdown(s(R.string.repeat), repeat, listOf("NONE" to s(R.string.once), "DAILY" to s(R.string.daily), "WEEKLY" to s(R.string.weekly_repeat), "MONTHLY" to s(R.string.monthly), "WEEKDAYS" to s(R.string.weekdays), "SELECTED" to s(R.string.selected_days), "CUSTOM" to s(R.string.custom_days))) { repeat = it }
        if (repeat != "NONE" && repeat != "WEEKDAYS" && repeat != "SELECTED") Field(interval, { interval = it }, s(R.string.repeat_interval), true)
        if (repeat == "SELECTED") {
            Text(s(R.string.selected_days))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) { (1..7).forEach { day -> val values = weekdays.split(',').filter { it.isNotBlank() }.toMutableSet(); FilterChip(selected = day.toString() in values, onClick = { if (!values.add(day.toString())) values.remove(day.toString()); weekdays = values.sorted().joinToString(",") }, label = { Text(java.time.DayOfWeek.of(day).getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())) }) } }
        }
        Dropdown(s(R.string.effort), effort, listOf(1 to s(R.string.effort_small), 2 to s(R.string.effort_medium), 3 to s(R.string.effort_large))) { effort = it }
        Field(stars, { stars = it }, s(R.string.points), true)
        Field(notes, { notes = it.take(4000) }, s(R.string.notes))
        Row(verticalAlignment = Alignment.CenterVertically) { Text(s(R.string.require_approval), Modifier.weight(1f)); Switch(approval, { approval = it }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text(s(R.string.chore_reminder), Modifier.weight(1f)); Switch(reminder, { enabled -> if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) reminderPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else reminder = enabled }) }
        if (reminder) { Field(reminderTime, { reminderTime = it }, s(R.string.reminder_time)); Text(s(R.string.reminder_time_help), style = MaterialTheme.typography.bodySmall) }
        if (reminderDenied) Text(s(R.string.notifications_denied), color = MaterialTheme.colorScheme.error)
    }
}

@Composable internal fun MemberEditor(original: Member?, onDismiss: () -> Unit, onDelete: (Member) -> Unit, save: (Member) -> Unit) {
    val member = remember { original ?: Member(nickname = "") }; var nickname by remember { mutableStateOf(member.nickname) }; var child by remember { mutableStateOf(member.child) }; var avatar by remember { mutableStateOf(member.avatar) }
    Editor(s(R.string.member_add), onDismiss, { save(member.copy(nickname = nickname, child = child, avatar = avatar)) }, nickname.isNotBlank()) { Field(nickname, { nickname = it.take(40) }, s(R.string.nickname)); Row(verticalAlignment = Alignment.CenterVertically) { Text(s(R.string.child), Modifier.weight(1f)); Switch(child, { child = it }) }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(4) { index -> FilterChip(selected = avatar == index, onClick = { avatar = index }, label = { Avatar(index, 40) }) } }; if (original != null) TextButton(onClick = { onDelete(original) }) { Text(s(R.string.delete), color = MaterialTheme.colorScheme.error) } }
}
@Composable internal fun RewardEditor(dismiss: () -> Unit, save: (Reward) -> Unit) { var title by remember { mutableStateOf("") }; var cost by remember { mutableStateOf("25") }; Editor(s(R.string.reward_add), dismiss, { save(Reward(title = title, cost = cost.toInt())) }, title.isNotBlank() && cost.toIntOrNull() in 1..100000) { Field(title, { title = it.take(160) }, s(R.string.title)); Field(cost, { cost = it }, s(R.string.reward_cost), true) } }
@Composable internal fun RoutineEditor(chores: List<Chore>, dismiss: () -> Unit, original: Routine? = null, save: (Routine) -> Unit) {
    val routine = remember { original ?: Routine(title = "") }; var title by remember { mutableStateOf(routine.title) }; var ids by remember { mutableStateOf(JSONArray(routine.choreIdsJson).let { a -> (0 until a.length()).map { a.getString(it) } }) }
    Editor(s(R.string.routine_add), dismiss, { save(routine.copy(title = title, choreIdsJson = JSONArray(ids).toString())) }, title.isNotBlank() && ids.isNotEmpty()) { Field(title, { title = it.take(160) }, s(R.string.routine_name)); Text(s(R.string.routine_select)); chores.filter { !it.archived || it.id in ids }.forEach { chore -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(chore.id in ids, { checked -> ids = if (checked) ids + chore.id else ids - chore.id }); Text(chore.title, Modifier.weight(1f)); if (chore.id in ids) Text((ids.indexOf(chore.id) + 1).toString(), color = MaterialTheme.colorScheme.primary) } } }
}

@Composable internal fun RenameDialog(original: String, dismiss: () -> Unit, save: (String) -> Unit) { var name by remember { mutableStateOf(original) }; Editor(s(R.string.rename_household), dismiss, { save(name.trim()) }, name.isNotBlank()) { Field(name, { name = it.take(80) }, s(R.string.household_name)) } }
@Composable internal fun ChangePinDialog(dismiss: () -> Unit, save: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }; var next by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
    Editor(s(R.string.change_pin), dismiss, { save(current, next); current = ""; next = ""; confirm = "" }, current.length >= 6 && next.length >= 6 && next == confirm) {
        listOf(Triple(current, R.string.current_pin, { value: String -> current = value }), Triple(next, R.string.new_pin, { value: String -> next = value }), Triple(confirm, R.string.confirm_pin, { value: String -> confirm = value })).forEach { (value, label, change) -> OutlinedTextField(value, { change(it.filter(Char::isDigit).take(12)) }, label = { Text(s(label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation()) }
    }
}
@Composable internal fun RoutineTemplateDialog(members: List<Member>, dismiss: () -> Unit, create: (String, List<String>, String?, Boolean) -> Unit) {
    var template by remember { mutableStateOf(0) }; var member by remember { mutableStateOf<String?>(null) }
    val titles = listOf(s(R.string.morning), s(R.string.evening), s(R.string.weekend))
    val tasks = listOf(listOf(s(R.string.make_bed), s(R.string.pack_bag), s(R.string.fill_bottle)), listOf(s(R.string.put_toys_away), s(R.string.prepare_clothes), s(R.string.clear_table)), listOf(s(R.string.tidy_room), s(R.string.wipe_surfaces), s(R.string.water_plants)))
    Editor(s(R.string.templates), dismiss, { create(titles[template], tasks[template], member, template == 2) }) {
        Text(s(R.string.choose_template)); Dropdown(s(R.string.templates), template, titles.mapIndexed { index, title -> index to title }) { template = it }; Dropdown(s(R.string.assigned_to), member, listOf(null to s(R.string.unassigned)) + members.map { it.id as String? to it.nickname }) { member = it }; tasks[template].forEach { Text("• $it") }
    }
}

@Composable internal fun ProgressScreen(chores: List<Chore>, completions: List<Completion>, members: List<Member>) {
    val today = LocalDate.now(); val week = today.minusDays(6); val approved = completions.filter { it.status == "APPROVED" }
    ScreenTitle(s(R.string.progress), s(R.string.progress_intro))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { StatCard(approved.size.toString(), s(R.string.completed), Icons.Outlined.CheckCircleOutline, Modifier.weight(1f)); StatCard(approved.sumOf { it.points }.toString(), s(R.string.stars_earned), Icons.Outlined.StarOutline, Modifier.weight(1f)) }
    Panel { Text(s(R.string.weekly), style = MaterialTheme.typography.titleLarge); repeat(7) { i -> val day = week.plusDays(i.toLong()); val count = approved.count { it.date == day.toString() }; val maximum = maxOf(1, (0..6).maxOf { offset -> approved.count { it.date == week.plusDays(offset.toLong()).toString() } }); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text(day.format(DateTimeFormatter.ofPattern("EEE d")), Modifier.width(70.dp), fontSize = 12.sp); LinearProgressIndicator(progress = { count.toFloat() / maximum }, modifier = Modifier.weight(1f).height(10.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round); Text(count.toString(), Modifier.width(24.dp)) } } }
    Panel { Text(s(R.string.family), style = MaterialTheme.typography.titleLarge); members.forEach { member -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Avatar(member.avatar); Text(member.nickname, Modifier.weight(1f)); Text("${approved.count { it.memberId == member.id }} ${s(R.string.completed).lowercase()}", style = MaterialTheme.typography.bodySmall) } } }
    Panel { Text(s(R.string.history), style = MaterialTheme.typography.titleLarge); if (completions.isEmpty()) Text(s(R.string.no_history)); completions.take(30).forEach { item -> Text("${item.date} · ${chores.find { it.id == item.choreId }?.title ?: s(R.string.archived_chore)} · ${members.find { it.id == item.memberId }?.nickname ?: s(R.string.all_members)} · ${s(if (item.status == "APPROVED") R.string.completed else R.string.pending)}", style = MaterialTheme.typography.bodySmall) } }
}

@Composable internal fun ReminderToggle(vm: TidyViewModel) {
    val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("appearance", 0) }; var enabled by remember { mutableStateOf(prefs.getBoolean("reminder", false)) }
    fun set(value: Boolean) { if (!vm.repo.guard.authorized()) { vm.message.value = context.getString(R.string.unlock_help); return }; enabled = value; prefs.edit().putBoolean("reminder", value).apply(); ReminderWorker.configure(context, value) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) set(true) else vm.message.value = context.getString(R.string.notifications_denied) }
    Row(verticalAlignment = Alignment.CenterVertically) { Text(s(R.string.reminders), Modifier.weight(1f)); Switch(enabled, { value -> if (value && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) request.launch(Manifest.permission.POST_NOTIFICATIONS) else set(value) }) }
}

@Composable internal fun VoiceSheet(vm: TidyViewModel, chores: List<Chore>, members: List<Member>, dismiss: () -> Unit, progress: () -> Unit, addChore: (Chore) -> Unit) {
    val context = LocalContext.current; val voice = remember { VoiceController(context) }; var transcript by remember { mutableStateOf("") }; var listening by remember { mutableStateOf(false) }; var response by remember { mutableStateOf("") }; var pending by remember { mutableStateOf<VoiceCommand?>(null) }; val enabled by vm.voiceEnabled.collectAsStateWithLifecycle()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) voice.close() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); voice.close() }
    }
    fun start() { if (!enabled || !vm.repo.guard.authorized()) return; listening = true; voice.listen({ transcript = it }, { response = it }, { listening = false }) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) start() else response = context.getString(R.string.mic_denied) }
    fun execute(command: VoiceCommand) {
        if (!vm.repo.guard.authorized()) return
        fun unique(name: String?): Chore { val matches = chores.filter { !it.archived && (name == null || it.title.equals(name, true)) }; check(matches.size == 1) { context.getString(R.string.voice_ambiguous) }; return matches.single() }
        when (command) {
            is VoiceCommand.Add -> addChore(Chore(title = command.title))
            is VoiceCommand.Complete -> vm.action { vm.repo.complete(unique(command.chore).id); response = context.getString(R.string.completed) }
            is VoiceCommand.Assign -> vm.action { val person = members.filter { it.nickname.equals(command.member, true) }; check(person.size == 1) { context.getString(R.string.voice_ambiguous) }; vm.repo.saveChore(unique(command.chore).copy(memberId = person.single().id)); response = context.getString(R.string.saved) }
            is VoiceCommand.Reschedule -> vm.action { vm.repo.saveChore(unique(command.chore).copy(due = command.date.toString())); response = context.getString(R.string.saved) }
            is VoiceCommand.Reminder -> addChore(Chore(title = command.title, due = command.date.toString(), reminderMinutes = 18 * 60))
            VoiceCommand.Progress -> progress()
            VoiceCommand.Today, VoiceCommand.Checklist, VoiceCommand.MyChores, VoiceCommand.NextTask -> { val tasks = chores.filter { it.isDueOn(LocalDate.now()) }; response = tasks.joinToString(". ") { it.title }.ifBlank { context.getString(R.string.child_empty) }; voice.speak(response) }
            VoiceCommand.Stars -> { response = members.joinToString(". ") { "${it.nickname}: ${vm.balance(it.id)} ${context.getString(R.string.points)}" }; voice.speak(response) }
            VoiceCommand.Repeat -> voice.speak(response)
            VoiceCommand.Back -> dismiss()
            else -> response = context.getString(R.string.voice_unknown)
        }
    }
    Dialog(onDismissRequest = dismiss) { Surface(shape = RoundedCornerShape(28.dp)) { Column(Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Outlined.MicNone, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Text(s(R.string.voice_intro), fontFamily = FontFamily.Serif, fontSize = 30.sp)
        Text(s(R.string.voice_disclosure), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) { Text(s(R.string.enable_voice), Modifier.weight(1f)); Switch(enabled, { vm.action { vm.repo.setVoiceEnabled(it) } }) }
        if (!voice.available) Text(s(R.string.voice_unavailable), style = MaterialTheme.typography.bodySmall)
        Button(enabled = enabled && voice.available && !listening, onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start() else permission.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth()) { Text(s(if (listening) R.string.listening else R.string.listen)) }
        Field(transcript, { transcript = it; pending = null }, s(R.string.voice_command))
        Text(s(R.string.voice_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        OutlinedButton(onClick = { val command = VoiceParser.parse(transcript, Locale.UK); if (command.requiresConfirmation) pending = command else execute(command) }, enabled = transcript.isNotBlank()) { Text(s(R.string.run_command)) }
        if (pending != null) { Text(transcript); Text(s(R.string.voice_confirm)); Row { TextButton(onClick = { pending = null }) { Text(s(R.string.cancel)) }; Button(onClick = { val command = pending; pending = null; command?.let { execute(it) } }) { Text(s(R.string.confirm)) } } }
        if (response.isNotBlank()) { Text(response); TextButton(onClick = { if (!voice.speak(response)) response = context.getString(R.string.tts_unavailable) }) { Text(s(R.string.speak)) } }
        TextButton(onClick = dismiss, modifier = Modifier.align(Alignment.End)) { Text(s(R.string.done)) }
    } } }
}
