package com.sonzzk.claudebite.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonzzk.claudebite.ai.Pricing
import com.sonzzk.claudebite.data.HealthRepository
import com.sonzzk.claudebite.data.db.TokenUsage
import com.sonzzk.claudebite.data.prefs.ModelMode
import com.sonzzk.claudebite.data.prefs.SecureSettings
import com.sonzzk.claudebite.data.prefs.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Token counts for one model plus its estimated cost. */
data class UsageRow(val usage: TokenUsage, val costUsd: Double)

data class SettingsUiState(
    val loaded: Boolean = false,
    val profile: UserProfile = UserProfile(),
    val apiKey: String = "",
    val modelMode: ModelMode = ModelMode.AUTO,
    val usage: List<UsageRow> = emptyList(),
) {
    val totalCostUsd: Double get() = usage.sumOf { it.costUsd }
    val totalInput: Long get() = usage.sumOf { it.usage.inputTokens }
    val totalOutput: Long get() = usage.sumOf { it.usage.outputTokens }
    val totalRequests: Long get() = usage.sumOf { it.usage.requests }
}

class SettingsViewModel(
    private val repository: HealthRepository,
    private val settings: SecureSettings,
) : ViewModel() {

    /**
     * Local copy of the profile while editing. Sliders update it instantly (smooth UI) and every change
     * is also written to DataStore, so there is no "Save" button to forget.
     */
    private val draft = MutableStateFlow<UserProfile?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        combine(repository.profiles.profile, draft) { stored, local -> local ?: stored },
        settings.apiKey,
        settings.modelMode,
        repository.tokenUsage(),
    ) { profile, key, mode, usage ->
        SettingsUiState(
            loaded = true,
            profile = profile,
            apiKey = key,
            modelMode = mode,
            usage = usage.sortedByDescending { it.requests }.map {
                UsageRow(it, Pricing.estimateCostUsd(it.model, it.inputTokens, it.outputTokens))
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun updateProfile(transform: (UserProfile) -> UserProfile) {
        viewModelScope.launch {
            val base = draft.value ?: repository.profiles.profile.first()
            val updated = transform(base)
            draft.value = updated
            repository.profiles.save(updated)
        }
    }

    fun saveApiKey(key: String) = settings.setApiKey(key)

    fun setModelMode(mode: ModelMode) = settings.setModelMode(mode)

    fun resetUsage() {
        viewModelScope.launch { repository.resetUsage() }
    }

    /** Deletes all data. The root screen notices `onboarded == false` and shows onboarding again. */
    fun wipeEverything(forgetKey: Boolean) {
        viewModelScope.launch {
            if (forgetKey) settings.setApiKey("")
            draft.value = null
            repository.wipeEverything()
        }
    }
}
