package com.sonzzk.claudebite.domain

import com.sonzzk.claudebite.data.prefs.DailyTargets
import com.sonzzk.claudebite.data.prefs.DietStyle
import com.sonzzk.claudebite.data.prefs.Goal
import com.sonzzk.claudebite.data.prefs.Sex
import com.sonzzk.claudebite.data.prefs.UserProfile
import kotlin.math.roundToInt

/**
 * Turns a profile into daily calorie and macro targets. Pure math, no AI, so it costs nothing.
 *
 * - BMR: Mifflin-St Jeor equation (the most accurate simple formula for most people).
 * - TDEE: BMR x activity multiplier.
 * - Goal adjustment: 1 kg of body fat is roughly 7700 kcal, so 0.5 kg/week is about -550 kcal/day.
 * - Protein: 1.6-2.2 g per kg body weight depending on goal (research-backed range for muscle retention/growth).
 * - Fat: 25-30% of calories (keto: 65%). Carbs fill the rest.
 */
object NutritionCalculator {

    fun bmr(p: UserProfile): Double {
        val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age
        return if (p.sex == Sex.MALE) base + 5 else base - 161
    }

    fun targets(p: UserProfile): DailyTargets {
        val bmr = bmr(p)
        val tdee = bmr * p.activity.multiplier
        val dailyDelta = p.pace.kgPerWeek * 7700 / 7
        val goalCalories = when (p.goal) {
            Goal.LOSE_FAT -> tdee - dailyDelta
            Goal.MAINTAIN -> tdee
            Goal.BUILD_MUSCLE -> tdee + dailyDelta * 0.5 // lean bulk: smaller surplus limits fat gain
            Goal.RECOMP -> tdee - 250
        }
        // Never recommend below a safe floor.
        val floor = if (p.sex == Sex.MALE) 1500.0 else 1200.0
        val calories = if (p.customCalorieTarget > 0) p.customCalorieTarget
        else goalCalories.coerceAtLeast(floor).roundTo(10)

        val proteinPerKg = when (p.goal) {
            Goal.LOSE_FAT, Goal.RECOMP -> 2.0
            Goal.BUILD_MUSCLE -> 1.8
            Goal.MAINTAIN -> 1.6
        }
        val protein = (proteinPerKg * p.weightKg).roundToInt()
        val fatShare = if (p.diet == DietStyle.KETO) 0.65 else if (p.goal == Goal.LOSE_FAT) 0.30 else 0.27
        val fat = (calories * fatShare / 9).roundToInt()
        val carbs = ((calories - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(if (p.diet == DietStyle.KETO) 20 else 50)

        return DailyTargets(
            calories = calories,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            waterMl = p.waterGoalMl,
            bmr = bmr.roundToInt(),
            tdee = tdee.roundToInt(),
        )
    }

    /** Recommended water: ~35 ml per kg, rounded to 250 ml glasses. */
    fun suggestedWaterMl(weightKg: Float): Int = ((weightKg * 35) / 250).roundToInt() * 250

    fun bmi(p: UserProfile): Float {
        val m = p.heightCm / 100f
        return p.weightKg / (m * m)
    }

    /** Rough number of weeks to reach the target weight at the chosen pace. */
    fun weeksToGoal(p: UserProfile): Int? {
        val diff = kotlin.math.abs(p.weightKg - p.targetWeightKg)
        if (diff < 0.5f || p.goal == Goal.MAINTAIN) return null
        return (diff / p.pace.kgPerWeek).roundToInt().coerceAtLeast(1)
    }

    private fun Double.roundTo(step: Int): Int = ((this / step).roundToInt()) * step
}
