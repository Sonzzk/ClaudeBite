package com.sonzzk.claudebite.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonzzk.claudebite.data.HealthRepository
import com.sonzzk.claudebite.data.prefs.DailyTargets
import com.sonzzk.claudebite.data.prefs.Goal
import com.sonzzk.claudebite.data.prefs.SecureSettings
import com.sonzzk.claudebite.data.prefs.UserProfile
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.domain.NutritionCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The pages of onboarding, in order. Each one has Claude-chan's mood for that page. */
enum class OnboardingStep(val mood: MascotMood) {
    WELCOME(MascotMood.WAVING),
    NAME(MascotMood.HAPPY),
    BASICS(MascotMood.HAPPY),
    BODY(MascotMood.HAPPY),
    ACTIVITY(MascotMood.HAPPY),
    GOAL(MascotMood.EXCITED),
    DIET(MascotMood.HAPPY),
    PLAN(MascotMood.EXCITED),
    API_KEY(MascotMood.THINKING),
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val profile: UserProfile = UserProfile(),
    val apiKey: String = "",
    val saving: Boolean = false,
) {
    /** The profile exactly as it will be saved (water goal from body weight, target = weight when maintaining). */
    val finalProfile: UserProfile
        get() = profile.copy(
            name = profile.name.trim(),
            onboarded = true,
            waterGoalMl = NutritionCalculator.suggestedWaterMl(profile.weightKg),
            targetWeightKg = if (profile.goal == Goal.MAINTAIN) profile.weightKg else profile.targetWeightKg,
        )

    val targets: DailyTargets get() = NutritionCalculator.targets(finalProfile)
    val weeksToGoal: Int? get() = NutritionCalculator.weeksToGoal(finalProfile)

    val canContinue: Boolean
        get() = when (step) {
            OnboardingStep.NAME -> profile.name.isNotBlank()
            else -> true
        }

    val isFirst: Boolean get() = step.ordinal == 0
}

/** Holds the draft profile while the user walks through onboarding; nothing is saved until [finish]. */
class OnboardingViewModel(
    private val repository: HealthRepository,
    private val settings: SecureSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun next() = _state.update { s ->
        val steps = OnboardingStep.entries
        if (!s.canContinue) s else s.copy(step = steps[(s.step.ordinal + 1).coerceAtMost(steps.lastIndex)])
    }

    fun back() = _state.update { s -> s.copy(step = OnboardingStep.entries[(s.step.ordinal - 1).coerceAtLeast(0)]) }

    fun updateProfile(transform: (UserProfile) -> UserProfile) = _state.update { it.copy(profile = transform(it.profile)) }

    /** Picks a goal and nudges the target weight in a sensible direction so the user rarely has to touch it. */
    fun selectGoal(goal: Goal) = updateProfile { p ->
        val target = when (goal) {
            Goal.LOSE_FAT -> if (p.targetWeightKg < p.weightKg) p.targetWeightKg else p.weightKg - 5f
            Goal.RECOMP -> if (p.targetWeightKg <= p.weightKg) p.targetWeightKg else p.weightKg - 2f
            Goal.BUILD_MUSCLE -> if (p.targetWeightKg > p.weightKg) p.targetWeightKg else p.weightKg + 3f
            Goal.MAINTAIN -> p.weightKg
        }
        p.copy(goal = goal, targetWeightKg = target.coerceIn(30f, 250f))
    }

    fun setApiKey(value: String) = _state.update { it.copy(apiKey = value) }

    /** Saves everything, logs the starting weight, then calls [onDone] (which navigates home). */
    fun finish(saveKey: Boolean, onDone: () -> Unit) {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            if (saveKey && s.apiKey.isNotBlank()) settings.setApiKey(s.apiKey)
            val profile = s.finalProfile
            repository.logWeight(profile.weightKg)
            repository.profiles.save(profile)
            onDone()
        }
    }
}
