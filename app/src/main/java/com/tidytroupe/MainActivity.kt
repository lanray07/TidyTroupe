package com.tidytroupe

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource as s
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tidytroupe.data.*
import com.tidytroupe.core.VoiceCommand
import com.tidytroupe.core.VoiceParser
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Teal = Color(0xFF246A73)
private val Cream = Color(0xFFFAF7F0)
private val Coral = Color(0xFFE68A72)
private val Sage = Color(0xFF8EB69B)
private val Ink = Color(0xFF293236)
private val Gold = Color(0xFFE8BB63)

class MainActivity : ComponentActivity() {
    private val vm: TidyViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { TidyApp(vm) } }
    override fun onResume() { super.onResume(); vm.refreshLock(); vm.billing.onResume(); vm.rescheduleReminders() }
    override fun onStop() { super.onStop(); vm.lock() }
}

private enum class Destination(val label: Int, val icon: ImageVector) {
    TODAY(R.string.today, Icons.Outlined.WbSunny), CHORES(R.string.chores, Icons.AutoMirrored.Outlined.ListAlt),
    ROUTINES(R.string.routines, Icons.Outlined.AutoAwesome), REWARDS(R.string.rewards, Icons.Outlined.StarOutline),
    FAMILY(R.string.family, Icons.Outlined.PeopleOutline), PROGRESS(R.string.progress, Icons.Outlined.BarChart), SETTINGS(R.string.settings, Icons.Outlined.Tune)
}

@Composable private fun TidyApp(vm: TidyViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("appearance", 0) }
    var theme by remember { mutableStateOf(prefs.getString("theme", "system") ?: "system") }
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    val colors = if (dark) darkColorScheme(primary = Color(0xFFA9D9DD), secondary = Coral, background = Color(0xFF19282B), surface = Color(0xFF213337), onSurface = Cream, onBackground = Cream) else lightColorScheme(primary = Teal, secondary = Color(0xFFA24F39), background = Cream, surface = Color.White, onSurface = Ink, onBackground = Ink, outlineVariant = Color(0xFFE5E9E3))
    MaterialTheme(colorScheme = colors, shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))) {
        val ready by vm.ready.collectAsStateWithLifecycle()
        val setup by vm.needsSetup.collectAsStateWithLifecycle()
        val members by vm.members.collectAsStateWithLifecycle()
        val chores by vm.chores.collectAsStateWithLifecycle()
        val completions by vm.completions.collectAsStateWithLifecycle()
        val rewards by vm.rewards.collectAsStateWithLifecycle()
        val routines by vm.routines.collectAsStateWithLifecycle()
        val household by vm.household.collectAsStateWithLifecycle()
        val unlocked by vm.unlocked.collectAsStateWithLifecycle()
        val childId by vm.childId.collectAsStateWithLifecycle()
        val message by vm.message.collectAsStateWithLifecycle()
        val billing by vm.billing.state.collectAsStateWithLifecycle()
        var destination by remember { mutableStateOf(Destination.TODAY) }
        var gate by remember { mutableStateOf(false) }
        var afterGate by remember { mutableStateOf<() -> Unit>({}) }
        fun protect(action: () -> Unit) { vm.refreshLock(); if (vm.repo.guard.authorized()) action() else { afterGate = action; gate = true } }
        var editChore by remember { mutableStateOf<Chore?>(null) }
        var choreDialog by remember { mutableStateOf(false) }
        var memberDialog by remember { mutableStateOf<Member?>(null) }
        var addMember by remember { mutableStateOf(false) }
        var rewardDialog by remember { mutableStateOf(false) }
        var routineDialog by remember { mutableStateOf(false) }
        var editRoutine by remember { mutableStateOf<Routine?>(null) }
        var templateDialog by remember { mutableStateOf(false) }
        var renameDialog by remember { mutableStateOf(false) }
        var pinChangeDialog by remember { mutableStateOf(false) }
        var choreView by remember { mutableStateOf("schedule") }
        var voiceDialog by remember { mutableStateOf(false) }
        var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        var confirmText by remember { mutableStateOf("") }
        var selectedMember by remember { mutableStateOf<String?>(null) }
        val today = LocalDate.now()
        var date by remember { mutableStateOf(today) }
        var exportBytes by remember { mutableStateOf(ByteArray(0)) }
        var backupMode by remember { mutableStateOf<String?>(null) }
        var encryptedInput by remember { mutableStateOf(ByteArray(0)) }
        val exporter = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) vm.action { try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).use { require(exportBytes.isNotEmpty()) { context.getString(R.string.backup_retry) }; it.write(exportBytes) } } } finally { exportBytes.fill(0); exportBytes = ByteArray(0) } } else { exportBytes.fill(0); exportBytes = ByteArray(0) }
        }
        val encryptedExporter = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) vm.action { try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).use { require(exportBytes.isNotEmpty()) { context.getString(R.string.backup_retry) }; it.write(exportBytes) } } } finally { exportBytes.fill(0); exportBytes = ByteArray(0) } } else { exportBytes.fill(0); exportBytes = ByteArray(0) }
        }
        val encryptedImporter = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) protect { vm.action { encryptedInput = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { requireNotNull(context.contentResolver.openInputStream(uri)).use { it.boundedBytes(5_000_100) } }; backupMode = "import" } }
        }
        val importer = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) { protect { vm.action { val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.boundedBytes(5_000_000).toString(Charsets.UTF_8) } ?: error("Could not open backup.") }; vm.repo.importJson(text) } } }
        }
        LaunchedEffect(Unit) { while (true) { delay(2000); vm.refreshLock() } }
        LaunchedEffect(childId) { destination = Destination.TODAY; selectedMember = null }
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            if (!ready) Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (setup) Onboarding(vm)
            else BoxWithConstraints {
                val wide = maxWidth >= 840.dp
                Scaffold(containerColor = colors.background,
                    bottomBar = { if (!wide && childId == null) NavigationBar(containerColor = colors.surface) { Destination.entries.take(5).forEach { d -> NavigationBarItem(selected = destination == d, onClick = { destination = d }, icon = { Icon(d.icon, null) }, label = { Text(s(d.label), maxLines = 1, fontSize = 10.sp) }) } } }
                ) { padding ->
                    Row(Modifier.fillMaxSize().padding(padding)) {
                        if (wide && childId == null) Column(Modifier.width(224.dp).fillMaxHeight().background(colors.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Brand()
                            Spacer(Modifier.height(32.dp))
                            Destination.entries.forEach { d -> NavigationDrawerItem(label = { Text(s(d.label)) }, selected = destination == d, onClick = { destination = d }, icon = { Icon(d.icon, null) }, colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = colors.primary.copy(alpha = .10f)), modifier = Modifier.height(52.dp)) }
                            Spacer(Modifier.weight(1f))
                            Text(s(R.string.tagline), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { if (unlocked) vm.lock() else protect {} }, modifier = Modifier.fillMaxWidth()) { Icon(if (unlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(s(if (unlocked) R.string.lock else R.string.parent_area), fontSize = 11.sp) }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(if (wide) 32.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(household, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                    Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                                if (childId == null) {
                                    IconButton(onClick = { protect { voiceDialog = true } }) { Icon(Icons.Outlined.MicNone, s(R.string.voice)) }
                                    if (!wide) {
                                        IconButton(onClick = { destination = Destination.PROGRESS }) { Icon(Icons.Outlined.BarChart, s(R.string.progress)) }
                                        IconButton(onClick = { protect { destination = Destination.SETTINGS } }) { Icon(Icons.Outlined.Tune, s(R.string.settings)) }
                                    }
                                }
                                IconButton(onClick = { if (unlocked && childId == null) vm.lock() else protect {} }) { Icon(if (unlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock, s(R.string.parent_area)) }
                            }
                            if (childId != null) {
                                val child = members.find { it.id == childId }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { Avatar(child?.avatar ?: 0, 76); Column { Text(child?.nickname ?: "", style = MaterialTheme.typography.headlineMedium); Text("★ ${vm.balance(childId!!)}", color = colors.primary, fontWeight = FontWeight.Bold) } }
                                Heading(s(R.string.child_tasks))
                                val tasks = chores.filter { it.memberId == childId && it.isDueOn(today) }
                                if (tasks.isEmpty()) EmptyState(s(R.string.child_empty), "", Icons.Outlined.WbSunny)
                                val childSpeech = remember { if (prefs.getBoolean("childSpeech", false)) VoiceController(context) else null }
                                DisposableEffect(childSpeech) { onDispose { childSpeech?.close() } }
                                tasks.forEach { task -> ChoreCard(task, child, completions.find { it.choreId == task.id && it.date == today.toString() }, true,
                                    complete = { vm.action { vm.repo.complete(task.id, actorId = childId) } }, undo = { vm.action { vm.repo.undo(task.id, today.toString(), childId) } }, edit = {}, approve = {}, menu = null) }
                                if (childSpeech != null) tasks.firstOrNull { task -> completions.none { it.choreId == task.id && it.date == today.toString() } }?.let { next -> OutlinedButton(onClick = { if (!childSpeech.speak("${next.title}. ${next.notes}")) vm.message.value = context.getString(R.string.tts_unavailable) }) { Icon(Icons.AutoMirrored.Outlined.VolumeUp, null); Text(s(R.string.speak)) } }
                                if (completions.count { it.memberId == childId && it.status == "APPROVED" } >= 5) Panel { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { BadgeArt(); Text(s(R.string.private_badge)) } }
                                rewards.forEach { reward -> Panel { Text(reward.title, style = MaterialTheme.typography.titleMedium); val balance = vm.balance(childId!!); Text("★ $balance / ${reward.cost}", color = colors.primary); LinearProgressIndicator(progress = { (balance.toFloat() / reward.cost).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) } }
                                OutlinedButton(onClick = { protect {} }) { Text(s(R.string.parent_view)) }
                            } else when (destination) {
                                Destination.TODAY -> {
                                    Hero(chores.count { it.isDueOn(today) }, chores.count { task -> task.isDueOn(today) && completions.any { it.choreId == task.id && it.date == today.toString() && it.status == "APPROVED" } }, wide)
                                    val due = chores.filter { it.isDueOn(today) }
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        StatCard(due.count { task -> completions.any { it.choreId == task.id && it.date == today.toString() && it.status == "APPROVED" } }.toString(), s(R.string.completed), Icons.Outlined.CheckCircleOutline, Modifier.weight(1f))
                                        StatCard(due.count { task -> completions.none { it.choreId == task.id && it.date == today.toString() } }.toString(), s(R.string.remaining), Icons.Outlined.Schedule, Modifier.weight(1f))
                                        if (wide) StatCard(completions.filter { it.date == today.toString() && it.status == "APPROVED" }.sumOf { it.points }.toString(), s(R.string.stars_earned), Icons.Outlined.StarOutline, Modifier.weight(1f))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text(s(R.string.todays_teamwork), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); FilledTonalButton(onClick = { protect { editChore = null; choreDialog = true } }) { Icon(Icons.Outlined.Add, null); Text(s(R.string.add_chore)) } }
                                    MemberFilters(members, selectedMember) { selectedMember = it }
                                    val tasks = due.filter { selectedMember == null || it.memberId == selectedMember }
                                    if (tasks.isEmpty()) EmptyState(s(R.string.empty_title), s(R.string.empty_body), Icons.Outlined.Checklist)
                                    tasks.forEach { task -> ChoreCard(task, members.find { it.id == task.memberId }, completions.find { it.choreId == task.id && it.date == today.toString() }, false,
                                        complete = { protect { vm.action { vm.repo.complete(task.id) } } }, undo = { protect { vm.action { vm.repo.undo(task.id, today.toString()) } } }, edit = { protect { editChore = task; choreDialog = true } }, approve = { protect { vm.action { vm.repo.approve(task.id, today.toString()) } } }, menu = null) }
                                    TextButton(onClick = { destination = Destination.CHORES }) { Text(s(R.string.view_all)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 8.dp).size(18.dp)) }
                                    val upcoming = chores.filter { !it.archived && !it.paused }.mapNotNull { task -> com.tidytroupe.core.ChoreEngine.nextDue(task.toDomain(), today.plusDays(1))?.let { task to it } }.sortedBy { it.second }.take(4)
                                    if (upcoming.isNotEmpty()) Panel { Text(s(R.string.upcoming), style = MaterialTheme.typography.titleLarge); upcoming.forEach { (task, day) -> Row(verticalAlignment = Alignment.CenterVertically) { Icon(categoryIcon(task.category), null, tint = colors.primary); Text(task.title, Modifier.padding(horizontal = 12.dp).weight(1f)); Text(day.format(DateTimeFormatter.ofPattern("EEE d MMM")), style = MaterialTheme.typography.bodySmall) } } }
                                }
                                Destination.CHORES -> {
                                    ScreenTitle(s(R.string.chores), s(R.string.dashboard_intro))
                                    Button(onClick = { protect { editChore = null; choreDialog = true } }) { Icon(Icons.Outlined.Add, null); Text(s(R.string.add_chore)) }
                                    WeekStrip(date) { date = it }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("schedule" to R.string.schedule_view, "all" to R.string.all_chores, "archived" to R.string.archived).forEach { (view, label) -> FilterChip(selected = choreView == view, onClick = { choreView = view }, label = { Text(s(label)) }) } }
                                    MemberFilters(members, selectedMember) { selectedMember = it }
                                    val tasks = chores.filter { (selectedMember == null || it.memberId == selectedMember) && when (choreView) { "archived" -> it.archived; "all" -> !it.archived; else -> it.isDueOn(date) } }
                                    if (tasks.isEmpty()) EmptyState(s(R.string.empty_title), s(R.string.empty_body), Icons.Outlined.Checklist)
                                    tasks.forEach { task -> ChoreCard(task, members.find { it.id == task.memberId }, completions.find { it.choreId == task.id && it.date == date.toString() }, false,
                                        complete = { protect { vm.action { vm.repo.complete(task.id, date.toString()) } } }, undo = { protect { vm.action { vm.repo.undo(task.id, date.toString()) } } }, edit = { protect { editChore = task; choreDialog = true } }, approve = { protect { vm.action { vm.repo.approve(task.id, date.toString()) } } }, menu = { action -> protect { when (action) { "duplicate" -> vm.action { vm.repo.duplicateChore(task.id) }; "pause" -> vm.action { vm.repo.saveChore(task.copy(paused = !task.paused)) }; "archive" -> vm.action { vm.repo.saveChore(task.copy(archived = !task.archived)) }; "delete" -> { confirmText = context.getString(R.string.delete) + ": " + task.title; confirmAction = { vm.action { vm.repo.deleteChore(task.id) } } } } } }, canComplete = task.isDueOn(date) && date <= today) }
                                }
                                Destination.FAMILY -> {
                                    ScreenTitle(s(R.string.family), s(R.string.tagline))
                                    Button(onClick = { protect { memberDialog = null; addMember = true } }) { Icon(Icons.Outlined.Add, null); Text(s(R.string.member_add)) }
                                    members.forEach { member -> Panel {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { Avatar(member.avatar, 64); Column(Modifier.weight(1f)) { Text(member.nickname, style = MaterialTheme.typography.titleLarge); Text(s(if (member.child) R.string.child else R.string.adult), color = colors.onSurfaceVariant); Text("★ ${vm.balance(member.id)}", color = colors.primary) }; IconButton(onClick = { protect { memberDialog = member; addMember = true } }) { Icon(Icons.Outlined.Edit, s(R.string.edit)) } }
                                        if (member.child) OutlinedButton(onClick = { protect { vm.openChild(member.id) } }) { Text(s(R.string.child_view)) }
                                    } }
                                }
                                Destination.REWARDS -> {
                                    ScreenTitle(s(R.string.rewards), s(R.string.rewards_intro))
                                    Button(onClick = { protect { rewardDialog = true } }) { Icon(Icons.Outlined.Add, null); Text(s(R.string.reward_add)) }
                                    if (rewards.isEmpty()) EmptyState(s(R.string.reward_add), s(R.string.rewards_empty), Icons.Outlined.CardGiftcard)
                                    rewards.forEach { reward -> Panel {
                                        Row(verticalAlignment = Alignment.CenterVertically) { BadgeArt(); Column(Modifier.padding(start = 16.dp).weight(1f)) { Text(reward.title, style = MaterialTheme.typography.titleLarge); Text("★ ${reward.cost}", color = colors.primary) }; IconButton(onClick = { protect { confirmText = context.getString(R.string.delete) + ": " + reward.title; confirmAction = { vm.action { vm.repo.deleteReward(reward.id) } } } }) { Icon(Icons.Outlined.DeleteOutline, s(R.string.delete)) } }
                                        members.forEach { member -> Row(verticalAlignment = Alignment.CenterVertically) { Text("${member.nickname} · ★ ${vm.balance(member.id)}", Modifier.weight(1f)); TextButton(enabled = vm.balance(member.id) >= reward.cost, onClick = { protect { confirmText = "${context.getString(R.string.redeem)} ${reward.title} · ${member.nickname} · ★ ${reward.cost}"; confirmAction = { vm.action { vm.repo.redeem(reward.id, member.id) } } } }) { Text(s(R.string.redeem)) } } }
                                    } }
                                }
                                Destination.ROUTINES -> {
                                    ScreenTitle(s(R.string.routines), s(R.string.routine_intro))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { protect { editRoutine = null; routineDialog = true } }) { Icon(Icons.Outlined.Add, null); Text(s(R.string.routine_add)) }; OutlinedButton(onClick = { protect { templateDialog = true } }) { Text(s(R.string.templates)) } }
                                    if (routines.isEmpty()) EmptyState(s(R.string.routines), s(R.string.routine_empty), Icons.Outlined.AutoAwesome)
                                    routines.forEach { routine -> Panel {
                                        Row(verticalAlignment = Alignment.CenterVertically) { Text(routine.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); IconButton(onClick = { protect { editRoutine = routine; routineDialog = true } }) { Icon(Icons.Outlined.Edit, s(R.string.edit)) }; IconButton(onClick = { protect { vm.action { vm.repo.duplicateRoutine(routine) } } }) { Icon(Icons.Outlined.ContentCopy, s(R.string.duplicate)) }; IconButton(onClick = { protect { confirmText = context.getString(R.string.delete) + ": " + routine.title; confirmAction = { vm.action { vm.repo.deleteRoutine(routine.id) } } } }) { Icon(Icons.Outlined.DeleteOutline, s(R.string.delete)) } }
                                        val ids = JSONArray(routine.choreIdsJson)
                                        (0 until ids.length()).mapNotNull { i -> chores.find { it.id == ids.getString(i) } }.forEachIndexed { i, task -> Row(verticalAlignment = Alignment.CenterVertically) { Text("${i + 1}.", color = colors.primary, modifier = Modifier.width(32.dp)); Text(task.title, Modifier.weight(1f)); Checkbox(checked = completions.any { it.choreId == task.id && it.date == today.toString() }, onCheckedChange = { protect { vm.action { if (it) vm.repo.complete(task.id) else vm.repo.undo(task.id, today.toString()) } } }, enabled = task.isDueOn(today)) } }
                                    } }
                                }
                                Destination.PROGRESS -> ProgressScreen(chores, completions, members)
                                Destination.SETTINGS -> {
                                    ScreenTitle(s(R.string.settings), s(R.string.privacy))
                                    Panel {
                                        Text(household, style = MaterialTheme.typography.titleLarge)
                                        OutlinedButton(onClick = { protect { renameDialog = true } }) { Text(s(R.string.rename_household)) }
                                        OutlinedButton(onClick = { protect { pinChangeDialog = true } }) { Text(s(R.string.change_pin)) }
                                        var childSpeech by remember { mutableStateOf(prefs.getBoolean("childSpeech", false)) }
                                        Text(s(R.string.child_speech_help), style = MaterialTheme.typography.bodySmall)
                                        Row(verticalAlignment = Alignment.CenterVertically) { Text(s(R.string.child_speech), Modifier.weight(1f)); Switch(childSpeech, { value -> protect { childSpeech = value; prefs.edit().putBoolean("childSpeech", value).apply() } }) }
                                    }
                                    Panel { Text(s(R.string.theme), style = MaterialTheme.typography.titleMedium); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("system" to R.string.system_theme, "light" to R.string.light_theme, "dark" to R.string.dark_theme).forEach { (key, label) -> FilterChip(selected = theme == key, onClick = { protect { theme = key; prefs.edit().putString("theme", key).apply() } }, label = { Text(s(label)) }) } } }
                                    Panel { Text(s(R.string.language), style = MaterialTheme.typography.titleMedium); Text(s(R.string.language_pending)) }
                                    Panel { Text(s(R.string.reminders), style = MaterialTheme.typography.titleMedium); Text(s(R.string.reminder_detail)); ReminderToggle(vm) }
                                    Panel {
                                        Text(s(R.string.privacy), style = MaterialTheme.typography.titleMedium)
                                        Text(s(R.string.privacy_detail), style = MaterialTheme.typography.bodyMedium)
                                        OutlinedButton(onClick = { protect { vm.action { exportBytes = vm.repo.exportJson().toByteArray(Charsets.UTF_8); exporter.launch("tidytroupe-${today}.json") } } }) { Icon(Icons.Outlined.FileDownload, null); Text(s(R.string.export)) }
                                        OutlinedButton(onClick = { protect { confirmText = context.getString(R.string.import_warning); confirmAction = { importer.launch(arrayOf("application/json", "text/plain")) } } }) { Icon(Icons.Outlined.FileUpload, null); Text(s(R.string.import_data)) }
                                        OutlinedButton(onClick = { protect { backupMode = "export" } }) { Icon(Icons.Outlined.Lock, null); Text(s(R.string.encrypted_export)) }
                                        OutlinedButton(onClick = { protect { confirmText = context.getString(R.string.import_warning); confirmAction = { encryptedImporter.launch(arrayOf("application/octet-stream", "*/*")) } } }) { Icon(Icons.Outlined.LockOpen, null); Text(s(R.string.encrypted_import)) }
                                    }
                                    Panel {
                                        Text(s(R.string.premium), style = MaterialTheme.typography.titleLarge)
                                        Text(if (billing.available) billing.message else s(R.string.premium_pending))
                                        if (billing.premium) Text(s(R.string.premium_active), color = colors.primary)
                                        billing.price?.let { price -> Button(enabled = billing.available && !billing.busy && !billing.premium, onClick = { protect { vm.action { vm.billing.purchase() } } }) { Text("${s(R.string.upgrade)} · $price") } }
                                        OutlinedButton(onClick = { protect { vm.action { vm.billing.restore() } } }, enabled = !billing.busy) { Text(s(R.string.restore_purchases)) }
                                    }
                                    OutlinedButton(onClick = { protect { confirmText = context.getString(R.string.delete_warning); confirmAction = { vm.action { vm.repo.deleteAll(); ChoreReminders.cancelAll(context); ReminderWorker.configure(context, false); prefs.edit().putBoolean("reminder", false).apply(); vm.needsSetup.value = true } } } }) { Text(s(R.string.delete_household), color = colors.error) }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
        if (gate) PinDialog(vm, onDismiss = { gate = false }, onSuccess = { gate = false; afterGate(); afterGate = {} })
        if (choreDialog && unlocked) ChoreEditor(editChore, members, onDismiss = { choreDialog = false }) { vm.action { vm.repo.saveChore(it); choreDialog = false } }
        if (addMember && unlocked) MemberEditor(memberDialog, onDismiss = { addMember = false }, onDelete = { person -> confirmText = context.getString(R.string.delete) + ": " + person.nickname; confirmAction = { vm.action { vm.repo.deleteMember(person.id); addMember = false } } }) { vm.action { vm.repo.saveMember(it); addMember = false } }
        if (rewardDialog && unlocked) RewardEditor({ rewardDialog = false }) { vm.action { vm.repo.saveReward(it); rewardDialog = false } }
        if (routineDialog && unlocked) RoutineEditor(chores, { routineDialog = false }, editRoutine) { vm.action { vm.repo.saveRoutine(it); routineDialog = false } }
        if (renameDialog && unlocked) RenameDialog(household, { renameDialog = false }) { vm.action { vm.repo.setHouseholdName(it); renameDialog = false } }
        if (pinChangeDialog && unlocked) ChangePinDialog({ pinChangeDialog = false }) { current, next -> vm.action { vm.repo.guard.changePin(current, next); pinChangeDialog = false } }
        if (templateDialog && unlocked) RoutineTemplateDialog(members, { templateDialog = false }) { title, tasks, member, weekly -> vm.action { val created = tasks.map { Chore(title = it, memberId = member, repeat = if (weekly) "WEEKLY" else "DAILY") }; created.forEach { vm.repo.saveChore(it) }; vm.repo.saveRoutine(Routine(title = title, choreIdsJson = JSONArray(created.map { it.id }).toString())); templateDialog = false } }
        if (backupMode != null && unlocked) BackupPasswordDialog(backupMode == "export", { backupMode = null; encryptedInput.fill(0); encryptedInput = ByteArray(0) }) { password ->
            val mode = backupMode
            vm.action {
                vm.repo.guard.requireAdult()
                if (mode == "export") {
                    exportBytes = EncryptedBackup().encrypt(vm.repo.exportJson(), password)
                    backupMode = null
                    encryptedExporter.launch("tidytroupe-${today}.tidybackup")
                } else {
                    val json = EncryptedBackup().decrypt(encryptedInput, password)
                    vm.repo.importJson(json)
                    encryptedInput.fill(0); encryptedInput = ByteArray(0); backupMode = null
                }
            }
        }
        if (voiceDialog && unlocked) VoiceSheet(vm, chores, members, { voiceDialog = false }, { destination = Destination.PROGRESS; voiceDialog = false }, { chore -> editChore = chore; choreDialog = true; voiceDialog = false })
        if (confirmAction != null) AlertDialog(onDismissRequest = { confirmAction = null }, title = { Text(s(R.string.confirm)) }, text = { Text(confirmText) }, confirmButton = { TextButton(onClick = { val action = confirmAction; confirmAction = null; protect { action?.invoke() } }) { Text(s(R.string.confirm)) } }, dismissButton = { TextButton(onClick = { confirmAction = null }) { Text(s(R.string.cancel)) } })
        if (message != null) AlertDialog(onDismissRequest = { vm.message.value = null }, text = { Text(message!!) }, confirmButton = { TextButton(onClick = { vm.message.value = null }) { Text(s(R.string.done)) } })
    }
}

@Composable private fun Brand() { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) { Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(40.dp)); Text("tidytroupe", fontSize = 23.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp) } }
@Composable private fun Heading(text: String) { Text(text, style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Serif, letterSpacing = (-1).sp, lineHeight = 40.sp) }
@Composable internal fun ScreenTitle(title: String, subtitle: String) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Heading(title); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable internal fun Panel(content: @Composable ColumnScope.() -> Unit) { Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) } }
@Composable private fun Hero(total: Int, complete: Int, wide: Boolean) {
    Surface(shape = RoundedCornerShape(28.dp), color = Teal) { Row(Modifier.fillMaxWidth().padding(if (wide) 30.dp else 24.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text(s(R.string.hero_eyebrow), color = Color(0xFFC0DDD6), fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold); Text(s(R.string.greeting), color = Cream, fontSize = if (wide) 38.sp else 30.sp, lineHeight = if (wide) 43.sp else 35.sp, fontFamily = FontFamily.Serif); Text(s(R.string.tagline), color = Color(0xFFE1EFE8), fontSize = 13.sp); LinearProgressIndicator(progress = { if (total == 0) 0f else complete.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Gold, trackColor = Color.White.copy(alpha = .2f)); Text("$complete / $total ${s(R.string.completed).lowercase()}", color = Cream, fontSize = 12.sp) }; if (wide) { Spacer(Modifier.width(24.dp)); FamilyArt(Modifier.size(220.dp, 180.dp)) } } }
}
@Composable private fun FamilyArt(modifier: Modifier) { Canvas(modifier) {
    val w = size.width; val h = size.height
    drawCircle(Color.White.copy(alpha = .06f), h * .49f, androidx.compose.ui.geometry.Offset(w * .52f, h * .5f))
    drawRoundRect(Sage, topLeft = androidx.compose.ui.geometry.Offset(w * .13f, h * .3f), size = androidx.compose.ui.geometry.Size(w * .35f, h * .60f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f))
    val roof = Path().apply { moveTo(w * .08f, h * .35f); lineTo(w * .30f, h * .07f); lineTo(w * .53f, h * .35f); close() }; drawPath(roof, Sage)
    drawRoundRect(Coral, topLeft = androidx.compose.ui.geometry.Offset(w * .49f, h * .20f), size = androidx.compose.ui.geometry.Size(w * .33f, h * .65f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(60f))
    drawCircle(Gold, w * .19f, androidx.compose.ui.geometry.Offset(w * .50f, h * .76f))
    listOf(.26f to .42f, .61f to .36f, .46f to .73f).forEach { (x,y) -> drawCircle(Ink, 3.5f, androidx.compose.ui.geometry.Offset(w*x,h*y)); drawCircle(Ink,3.5f,androidx.compose.ui.geometry.Offset(w*(x+.075f),h*y)) }
    drawLine(Cream, androidx.compose.ui.geometry.Offset(w*.80f,h*.08f), androidx.compose.ui.geometry.Offset(w*.80f,h*.18f), 4f); drawLine(Cream, androidx.compose.ui.geometry.Offset(w*.76f,h*.13f), androidx.compose.ui.geometry.Offset(w*.84f,h*.13f), 4f)
} }
@Composable internal fun Avatar(seed: Int, size: Int = 40) { val color = listOf(Sage, Coral, Gold, Color(0xFF98BCC8))[seed.mod(4)]; Canvas(Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = .23f))) { val w = this.size.width; val h = this.size.height; drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(w*.21f,h*.18f), size = androidx.compose.ui.geometry.Size(w*.58f,h*.67f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w*.24f)); drawCircle(Ink,w*.025f,androidx.compose.ui.geometry.Offset(w*.40f,h*.46f)); drawCircle(Ink,w*.025f,androidx.compose.ui.geometry.Offset(w*.60f,h*.46f)); drawArc(Ink,0f,180f,false,androidx.compose.ui.geometry.Offset(w*.43f,h*.50f),androidx.compose.ui.geometry.Size(w*.14f,h*.12f),style=androidx.compose.ui.graphics.drawscope.Stroke(w*.025f)) } }
@Composable private fun BadgeArt() { Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(Gold.copy(alpha = .25f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.StarOutline, null, Modifier.size(34.dp), tint = Color(0xFF936312)) } }
@Composable internal fun StatCard(number: String, label: String, icon: ImageVector, modifier: Modifier) { Surface(modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(number, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold); Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } } }
@Composable private fun EmptyState(title: String, body: String, icon: ImageVector) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleMedium); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) } }
@Composable private fun MemberFilters(members: List<Member>, selected: String?, select: (String?) -> Unit) { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected == null, { select(null) }, { Text(s(R.string.all_members)) }); members.forEach { member -> FilterChip(selected == member.id, { select(member.id) }, { Text(member.nickname) }, leadingIcon = { Avatar(member.avatar, 24) }) } } }
@Composable private fun WeekStrip(date: LocalDate, select: (LocalDate) -> Unit) { val monday = date.minusDays((date.dayOfWeek.value - 1).toLong()); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { IconButton(onClick = { select(date.minusWeeks(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, s(R.string.previous_week)) }; repeat(7) { i -> val day = monday.plusDays(i.toLong()); Surface(onClick = { select(day) }, shape = RoundedCornerShape(18.dp), color = if (day == date) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface) { Column(Modifier.width(52.dp).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(day.format(DateTimeFormatter.ofPattern("EEE")), fontSize = 11.sp); Text(day.dayOfMonth.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold) } } }; IconButton(onClick = { select(date.plusWeeks(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, s(R.string.next_week)) } } }

private fun categoryIcon(category: String): ImageVector = when (category) { "Kitchen" -> Icons.Outlined.Restaurant; "Bedroom" -> Icons.Outlined.Bed; "Pets" -> Icons.Outlined.Pets; "Garden" -> Icons.Outlined.LocalFlorist; "Laundry" -> Icons.Outlined.LocalLaundryService; "Bathroom" -> Icons.Outlined.WaterDrop; "School Preparation" -> Icons.Outlined.School; else -> Icons.Outlined.Home }
@Composable private fun ChoreCard(task: Chore, member: Member?, completion: Completion?, child: Boolean, complete: () -> Unit, undo: () -> Unit, edit: () -> Unit, approve: () -> Unit, menu: ((String) -> Unit)?, canComplete: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Sage.copy(alpha = .20f)), contentAlignment = Alignment.Center) { Icon(categoryIcon(task.category), null, tint = MaterialTheme.colorScheme.primary) }
                Column(Modifier.weight(1f)) { Text(task.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) { if (member != null) Avatar(member.avatar, 20); Text(member?.nickname ?: s(R.string.unassigned), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("· ★ ${task.points}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }; if (!child) Text("${task.category} · ${task.due}${if (task.paused) " · ${s(R.string.pause)}" else ""}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (completion?.status == "PENDING") { if (child) Icon(Icons.Outlined.HourglassEmpty, s(R.string.pending), tint = MaterialTheme.colorScheme.primary) else TextButton(onClick = approve) { Text(s(R.string.approve)) } }
                else if (completion != null) IconButton(onClick = undo, enabled = !child) { Icon(Icons.Outlined.CheckCircle, s(R.string.undo), tint = MaterialTheme.colorScheme.primary) }
                else IconButton(onClick = complete, enabled = canComplete) { Icon(Icons.Outlined.RadioButtonUnchecked, s(R.string.done), tint = MaterialTheme.colorScheme.primary) }
                if (!child) Box { IconButton(onClick = { if (menu == null) edit() else expanded = true }) { Icon(if (menu == null) Icons.Outlined.Edit else Icons.Outlined.MoreVert, s(R.string.edit)) }; DropdownMenu(expanded, { expanded = false }) { DropdownMenuItem(text = { Text(s(R.string.edit)) }, onClick = { expanded = false; edit() }); listOf("duplicate" to R.string.duplicate, "pause" to if (task.paused) R.string.resume else R.string.pause, "archive" to if (task.archived) R.string.restore else R.string.archive, "delete" to R.string.delete).forEach { (key, label) -> DropdownMenuItem(text = { Text(s(label)) }, onClick = { expanded = false; menu?.invoke(key) }) } } }
            }
            if (task.notes.isNotBlank()) Text(task.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (child && completion == null) Button(onClick = complete, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(s(R.string.done)) }
            if (child && completion?.status == "PENDING") Row(verticalAlignment = Alignment.CenterVertically) { Text(s(R.string.pending), Modifier.weight(1f)); TextButton(onClick = undo) { Text(s(R.string.undo)) } }
        }
    }
}





