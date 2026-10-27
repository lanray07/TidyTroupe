package com.tidytroupe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tidytroupe.data.*
import com.tidytroupe.billing.BillingManager
import com.tidytroupe.billing.BillingConfig
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TidyViewModel(application: Application) : AndroidViewModel(application) {
    @Volatile private var verifiedPremium = false
    val repo = TidyRepository(application) { verifiedPremium }
    val billing = BillingManager(application, repo.guard, runCatching {
        val json = application.assets.open("billing-public.json").bufferedReader().use { JSONObject(it.readText()) }
        BillingConfig(json.getString("sku"), json.getString("verifierUrl"), json.getString("verifierPublicKeyPem"))
    }.getOrDefault(BillingConfig()))
    val members = repo.members.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val chores = repo.chores.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val completions = repo.completions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rewards = repo.rewards.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val routines = repo.routines.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val redemptions = repo.redemptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val household = repo.householdName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "TidyTroupe")
    val ready = MutableStateFlow(false)
    val needsSetup = MutableStateFlow(false)
    val unlocked = MutableStateFlow(false)
    val childId = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    init {
        viewModelScope.launch { needsSetup.value = !repo.guard.hasPin() || repo.members.first().isEmpty(); ready.value = true }
        viewModelScope.launch { billing.state.collect { verifiedPremium = it.premium } }
        viewModelScope.launch { repo.chores.collect { stored -> runCatching { ChoreReminders.sync(application, stored) } } }
    }
    fun action(block: suspend () -> Unit) { viewModelScope.launch { runCatching { block() }.onFailure { message.value = it.message ?: "Could not complete that action." }; refreshLock() } }
    fun refreshLock() { unlocked.value = repo.guard.authorized() }
    fun rescheduleReminders() { viewModelScope.launch { runCatching { ChoreReminders.sync(getApplication(), repo.chores.first()) } } }
    fun lock() { repo.guard.lock(); refreshLock() }
    fun openChild(id: String) { repo.guard.requireAdult(); repo.selectChild(id); childId.value = id; refreshLock() }
    fun setup(name: String, nickname: String, pin: String) = action {
        require(name.trim().length in 1..80 && nickname.trim().length in 1..40) { "Use a household name of 1–80 characters and a nickname of 1–40 characters." }
        if (repo.guard.hasPin()) check(repo.guard.unlock(pin)) { "Enter the existing parent PIN to finish household setup." } else repo.guard.setupPin(pin)
        repo.setHouseholdName(name.trim())
        repo.saveMember(Member(nickname = nickname.trim(), child = false))
        needsSetup.value = false
    }
    fun unlock(pin: String, success: () -> Unit) = action {
        check(repo.guard.unlock(pin)) { getApplication<Application>().getString(R.string.pin_failed) }
        childId.value = null; success()
    }
    val voiceEnabled = repo.voiceEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun balance(id: String) = completions.value.filter { it.memberId == id && it.status == "APPROVED" }.sumOf { it.points } - redemptions.value.filter { it.memberId == id }.sumOf { it.cost }
    override fun onCleared() { billing.close(); repo.close(); super.onCleared() }
}
