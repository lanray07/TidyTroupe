package com.tidytroupe.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class TidyRepository(context: Context, private val premiumEntitlement: () -> Boolean = { false }) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(appContext, TidyDatabase::class.java, "tidytroupe.db").build()
    private val dao = database.dao()
    val guard = ParentGuard(appContext)
    /** Temporary background readers should release their Room instance when finished. */
    fun close() { guard.lock(); database.close() }
    val members: Flow<List<Member>> = dao.members()
    val chores: Flow<List<Chore>> = dao.chores()
    val completions: Flow<List<Completion>> = dao.completions()
    val rewards: Flow<List<Reward>> = dao.rewards()
    val routines: Flow<List<Routine>> = dao.routines()
    val redemptions: Flow<List<Redemption>> = dao.redemptions()
    private val householdKey = stringPreferencesKey("household_name")
    private val voiceKey = booleanPreferencesKey("adult_voice_enabled")
    val householdName: Flow<String> = appContext.tidyPreferences.data.map { it[householdKey] ?: "Our household" }
    val voiceEnabled: Flow<Boolean> = appContext.tidyPreferences.data.map { it[voiceKey] ?: false }
    @Volatile private var activeChildId: String? = null

    fun selectChild(memberId: String) { guard.requireAdult(); guard.lock(); activeChildId = memberId }
    fun leaveChild() { activeChildId = null; guard.lock() }
    suspend fun setHouseholdName(name: String) {
        guard.requireAdult(); require(name.trim().length in 1..80)
        appContext.tidyPreferences.edit { it[householdKey] = name.trim() }
    }
    suspend fun setVoiceEnabled(enabled: Boolean) {
        guard.requireAdult(); appContext.tidyPreferences.edit { it[voiceKey] = enabled }
    }
    suspend fun saveMember(member: Member) = database.withTransaction {
        guard.requireAdult()
        require(member.nickname.trim().length in 1..40) { "Use a nickname of 1–40 characters." }
        check(dao.member(member.id) != null || dao.memberCount() < 3 || premiumEntitlement()) { "The free household supports up to three profiles. A verified Premium purchase unlocks more profiles." }
        dao.put(member.copy(nickname = member.nickname.trim(), avatar = member.avatar.coerceIn(0, 11)))
    }
    suspend fun deleteMember(id: String) = database.withTransaction {
        guard.requireAdult(); dao.unassign(id); dao.deleteMemberCompletions(id); dao.deleteMemberRedemptions(id); dao.deleteMember(id)
        if (activeChildId == id) activeChildId = null
    }
    suspend fun saveChore(chore: Chore) = database.withTransaction {
        guard.requireAdult(); validate(chore)
        if (chore.memberId != null) requireNotNull(dao.member(chore.memberId)) { "Choose an existing profile." }
        dao.put(chore.copy(title = chore.title.trim()))
    }
    suspend fun duplicateChore(id: String) = database.withTransaction {
        guard.requireAdult(); val source = requireNotNull(dao.chore(id))
        dao.put(source.copy(id = UUID.randomUUID().toString(), title = (source.title + " (copy)").take(160)))
    }
    /** Deleting a chore retains earned completion history and reward accounting. */
    suspend fun deleteChore(id: String) = database.withTransaction {
        guard.requireAdult()
        dao.routines().first().forEach { routine ->
            val ids = JSONArray(routine.choreIdsJson)
            val kept = JSONArray()
            for (i in 0 until ids.length()) if (ids.getString(i) != id) kept.put(ids.getString(i))
            dao.put(routine.copy(choreIdsJson = kept.toString()))
        }
        dao.deleteChore(id)
    }

    suspend fun complete(choreId: String, date: String = LocalDate.now().toString(), actorId: String? = null) = database.withTransaction {
        val chore = requireNotNull(dao.chore(choreId)) { "This chore no longer exists." }
        val parsed = LocalDate.parse(date)
        require(parsed <= LocalDate.now() && chore.isDueOn(parsed)) { "This chore is not scheduled on that date." }
        if (!guard.authorized()) {
            check(actorId != null && actorId == activeChildId && chore.memberId == actorId) { "Choose your profile or unlock the parent area." }
            check(dao.member(actorId)?.child == true) { "Unlock the parent area first." }
            require(parsed == LocalDate.now()) { "Children can complete today's tasks." }
        }
        if (dao.completion(choreId, date) == null) {
            val status = if (guard.authorized() || !chore.approval) "APPROVED" else "PENDING"
            dao.put(Completion(choreId, date, chore.memberId, chore.points, status))
        }
    }
    suspend fun approve(choreId: String, date: String) = database.withTransaction {
        guard.requireAdult(); val completion = requireNotNull(dao.completion(choreId, date))
        dao.put(completion.copy(status = "APPROVED"))
    }
    suspend fun undo(choreId: String, date: String, actorId: String? = null) = database.withTransaction {
        val completion = requireNotNull(dao.completion(choreId, date))
        if (!guard.authorized()) {
            check(actorId != null && actorId == activeChildId && completion.memberId == actorId && completion.status == "PENDING") { "A parent must undo approved completions." }
        }
        if (completion.status == "APPROVED" && completion.memberId != null) {
            check(dao.earned(completion.memberId) - dao.spent(completion.memberId) >= completion.points) { "These stars have already been used for a reward." }
        }
        dao.deleteCompletion(choreId, date)
    }
    suspend fun saveReward(reward: Reward) = database.withTransaction {
        guard.requireAdult(); require(reward.title.trim().length in 1..160 && reward.cost in 1..100_000)
        dao.put(reward.copy(title = reward.title.trim()))
    }
    suspend fun deleteReward(id: String) = database.withTransaction { guard.requireAdult(); dao.deleteReward(id) }
    suspend fun redeem(rewardId: String, memberId: String) = database.withTransaction {
        guard.requireAdult(); requireNotNull(dao.member(memberId))
        val reward = requireNotNull(dao.reward(rewardId))
        check(dao.earned(memberId) - dao.spent(memberId) >= reward.cost) { "A few more stars are needed for this reward." }
        dao.put(Redemption(rewardId = rewardId, memberId = memberId, cost = reward.cost, title = reward.title))
    }
    suspend fun saveRoutine(routine: Routine) = database.withTransaction {
        guard.requireAdult(); require(routine.title.trim().length in 1..160)
        val ids = JSONArray(routine.choreIdsJson)
        require(ids.length() <= 100)
        for (i in 0 until ids.length()) requireNotNull(dao.chore(ids.getString(i))) { "A routine task no longer exists." }
        dao.put(routine.copy(title = routine.title.trim()))
    }
    suspend fun deleteRoutine(id: String) = database.withTransaction { guard.requireAdult(); dao.deleteRoutine(id) }
    suspend fun duplicateRoutine(routine: Routine) = saveRoutine(routine.copy(id = UUID.randomUUID().toString(), title = (routine.title + " (copy)").take(160)))

    private fun validate(chore: Chore) {
        require(chore.title.trim().length in 1..160) { "Enter a chore title of 1–160 characters." }
        require(chore.notes.length <= 4000 && chore.category.length in 1..80)
        require(chore.points in 0..1000) { "Choose between 0 and 1,000 stars." }
        LocalDate.parse(chore.due)
        require(chore.repeatInterval in 1..365) { "Choose a recurrence interval of 1–365." }
        require(chore.effort in 1..3) { "Choose an effort level of 1–3." }
        require(chore.reminderMinutes == null || chore.reminderMinutes in 480..1199) { "Choose a reminder between 08:00 and 19:59, outside quiet hours." }
        require(chore.weekdays.length <= 30) { "Choose valid weekdays." }
        chore.toDomain() // Validates the recurrence identifier, selected weekdays and domain contract.
    }

    /** Export intentionally excludes PIN material. Android's document picker controls the destination. */
    suspend fun exportJson(): String {
        guard.requireAdult()
        return database.withTransaction {
            guard.requireAdult()
            JSONObject().put("format", "tidytroupe").put("version", 1)
                .put("household", householdName.first())
                .put("members", JSONArray().apply { dao.members().first().forEach { put(JSONObject().put("id", it.id).put("nickname", it.nickname).put("child", it.child).put("avatar", it.avatar)) } })
                .put("chores", JSONArray().apply { dao.chores().first().forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("memberId", it.memberId ?: JSONObject.NULL).put("category", it.category).put("notes", it.notes).put("points", it.points).put("due", it.due).put("repeat", it.repeat).put("paused", it.paused).put("archived", it.archived).put("approval", it.approval).put("repeatInterval", it.repeatInterval).put("weekdays", it.weekdays).put("effort", it.effort).put("reminderMinutes", it.reminderMinutes ?: JSONObject.NULL)) } })
                .put("completions", JSONArray().apply { dao.completions().first().forEach { put(JSONObject().put("choreId", it.choreId).put("date", it.date).put("memberId", it.memberId ?: JSONObject.NULL).put("points", it.points).put("status", it.status)) } })
                .put("rewards", JSONArray().apply { dao.rewards().first().forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("cost", it.cost)) } })
                .put("redemptions", JSONArray().apply { dao.redemptions().first().forEach { put(JSONObject().put("id", it.id).put("rewardId", it.rewardId).put("memberId", it.memberId).put("cost", it.cost).put("date", it.date).put("title", it.title)) } })
                .put("routines", JSONArray().apply { dao.routines().first().forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("choreIdsJson", it.choreIdsJson)) } }).toString(2)
        }
    }

    /** Parse and validate the complete snapshot before replacing rows, in a single Room transaction. */
    suspend fun importJson(json: String) {
        guard.requireAdult(); require(json.length <= 5_000_000) { "Backup is too large." }
        val root = JSONObject(json)
        require(root.getString("format") == "tidytroupe" && root.getInt("version") == 1) { "Unsupported backup format." }
        val name = root.getString("household"); require(name.length in 1..80)
        fun JSONObject.nullable(key: String): String? = if (isNull(key)) null else getString(key)
        fun array(key: String): List<JSONObject> {
            val arr = root.getJSONArray(key); require(arr.length() <= 20_000)
            return (0 until arr.length()).map { arr.getJSONObject(it) }
        }
        val people = array("members").map { Member(it.getString("id"), it.getString("nickname"), it.getBoolean("child"), it.getInt("avatar")) }
        require((people.size <= 3 || premiumEntitlement()) && people.all { it.nickname.length in 1..40 && it.id.length in 1..100 && it.avatar in 0..11 })
        require(people.map { it.id }.distinct().size == people.size)
        val peopleIds = people.map { it.id }.toSet()
        val tasks = array("chores").map { Chore(it.getString("id"), it.getString("title"), it.nullable("memberId"), it.getString("category"), it.getString("notes"), it.getInt("points"), it.getString("due"), it.getString("repeat"), it.getBoolean("paused"), it.getBoolean("archived"), it.getBoolean("approval"), if (it.has("repeatInterval")) it.getInt("repeatInterval") else 1, if (it.has("weekdays")) it.getString("weekdays") else "", if (it.has("effort")) it.getInt("effort") else 1, if (it.has("reminderMinutes") && !it.isNull("reminderMinutes")) it.getInt("reminderMinutes") else null) }
        tasks.forEach { validate(it); require(it.memberId == null || it.memberId in peopleIds); require(it.id.length in 1..100) }
        require(tasks.map { it.id }.distinct().size == tasks.size)
        val history = array("completions").map { Completion(it.getString("choreId"), it.getString("date"), it.nullable("memberId"), it.getInt("points"), it.getString("status")) }
        history.forEach { require(it.choreId.length in 1..100 && it.points in 0..1000 && it.status in setOf("APPROVED", "PENDING") && (it.memberId == null || it.memberId in peopleIds)); require(LocalDate.parse(it.date) <= LocalDate.now()) }
        require(history.map { it.choreId to it.date }.distinct().size == history.size)
        val prizes = array("rewards").map { Reward(it.getString("id"), it.getString("title"), it.getInt("cost")) }
        prizes.forEach { require(it.title.length in 1..160 && it.cost in 1..100_000 && it.id.length in 1..100) }
        require(prizes.map { it.id }.distinct().size == prizes.size)
        val uses = array("redemptions").map { Redemption(it.getString("id"), it.getString("rewardId"), it.getString("memberId"), it.getInt("cost"), it.getString("date"), it.optString("title")) }
        uses.forEach { require(it.memberId in peopleIds && it.cost in 1..100_000 && it.id.length in 1..100 && it.rewardId.length in 1..100 && it.title.length <= 160); require(LocalDate.parse(it.date) <= LocalDate.now()) }
        require(uses.map { it.id }.distinct().size == uses.size)
        peopleIds.forEach { id -> require(history.filter { it.memberId == id && it.status == "APPROVED" }.sumOf { it.points.toLong() } >= uses.filter { it.memberId == id }.sumOf { it.cost.toLong() }) { "Backup contains an invalid reward balance." } }
        val plans = array("routines").map { Routine(it.getString("id"), it.getString("title"), it.getString("choreIdsJson")) }
        plans.forEach { routine ->
            require(routine.title.length in 1..160 && routine.id.length in 1..100)
            val ids = JSONArray(routine.choreIdsJson); require(ids.length() <= 100)
            for (i in 0 until ids.length()) require(tasks.any { it.id == ids.getString(i) })
        }
        require(plans.map { it.id }.distinct().size == plans.size)
        database.withTransaction {
            guard.requireAdult(); clear()
            people.forEach { dao.put(it) }; tasks.forEach { dao.put(it) }; history.forEach { dao.put(it) }
            prizes.forEach { dao.put(it) }; uses.forEach { dao.put(it) }; plans.forEach { dao.put(it) }
        }
        appContext.tidyPreferences.edit { it[householdKey] = name }
        activeChildId = null
    }
    suspend fun deleteAll() {
        database.withTransaction { guard.requireAdult(); clear() }
        appContext.tidyPreferences.edit { it[householdKey] = "Our household"; it[voiceKey] = false }
        activeChildId = null; guard.lock()
    }
    private suspend fun clear() { dao.clearCompletions(); dao.clearRedemptions(); dao.clearRoutines(); dao.clearChores(); dao.clearMembers(); dao.clearRewards() }
}
