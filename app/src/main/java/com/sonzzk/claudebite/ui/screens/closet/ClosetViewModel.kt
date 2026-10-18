package com.sonzzk.claudebite.ui.screens.closet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonzzk.claudebite.data.HealthRepository
import com.sonzzk.claudebite.data.prefs.Progression
import com.sonzzk.claudebite.domain.LevelInfo
import com.sonzzk.claudebite.domain.Leveling
import com.sonzzk.claudebite.domain.Outfit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ClosetUiState(
    val loaded: Boolean = false,
    val progression: Progression = Progression(),
    /** Badge name -> unlock time (epoch millis). */
    val unlockedBadges: Map<String, Long> = emptyMap(),
) {
    val level: LevelInfo get() = Leveling.info(progression.xp)
    fun isUnlocked(outfit: Outfit): Boolean = outfit.unlockLevel <= level.level
}

/** Claude-chan's wardrobe: shows level/affection, equips outfits and lists badges. */
class ClosetViewModel(private val repository: HealthRepository) : ViewModel() {

    val state: StateFlow<ClosetUiState> = combine(
        repository.profiles.progression,
        repository.achievements(),
    ) { progression, achievements ->
        ClosetUiState(
            loaded = true,
            progression = progression,
            unlockedBadges = achievements.associate { it.id to it.unlockedAt },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClosetUiState())

    /** Equips an outfit if the user's level is high enough. Returns true when it changed. */
    fun equip(outfit: Outfit): Boolean {
        val s = state.value
        if (!s.isUnlocked(outfit) || s.progression.equippedOutfit == outfit.id) return false
        viewModelScope.launch { repository.profiles.equipOutfit(outfit.id) }
        return true
    }
}
