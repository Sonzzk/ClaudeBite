package com.sonzzk.claudebite.data.prefs

enum class Sex { MALE, FEMALE }

enum class ActivityLevel(val multiplier: Float, val label: String) {
    SEDENTARY(1.2f, "Mostly sitting"),
    LIGHT(1.375f, "Light exercise 1-3x/week"),
    MODERATE(1.55f, "Exercise 3-5x/week"),
    ACTIVE(1.725f, "Hard exercise 6-7x/week"),
    ATHLETE(1.9f, "Athlete / physical job"),
}

enum class Goal(val label: String) {
    LOSE_FAT("Lose fat"),
    MAINTAIN("Stay healthy"),
    BUILD_MUSCLE("Build muscle"),
    RECOMP("Lose fat & build muscle"),
}

/** How fast to move toward the goal. Used for the calorie deficit/surplus. */
enum class Pace(val label: String, val kgPerWeek: Float) {
    GENTLE("Gentle", 0.25f),
    STEADY("Steady", 0.5f),
    FAST("Fast", 0.75f),
}

enum class DietStyle(val label: String) {
    ANYTHING("Anything"), VEGETARIAN("Vegetarian"), VEGAN("Vegan"),
    PESCATARIAN("Pescatarian"), KETO("Low carb / keto"), HALAL("Halal"),
}

enum class UnitSystem { METRIC, IMPERIAL }

/** Everything we know about the user. Stored in DataStore; never sent to Claude in full (see CoachContext). */
data class UserProfile(
    val name: String = "",
    val sex: Sex = Sex.FEMALE,
    val age: Int = 25,
    val heightCm: Float = 170f,
    val weightKg: Float = 70f,
    val targetWeightKg: Float = 65f,
    val activity: ActivityLevel = ActivityLevel.MODERATE,
    val goal: Goal = Goal.MAINTAIN,
    val pace: Pace = Pace.STEADY,
    val diet: DietStyle = DietStyle.ANYTHING,
    val units: UnitSystem = UnitSystem.METRIC,
    val waterGoalMl: Int = 2500,
    /** Optional manual override; 0 means "use the calculated target". */
    val customCalorieTarget: Int = 0,
    val onboarded: Boolean = false,
)

/** Daily targets derived from the profile by NutritionCalculator. */
data class DailyTargets(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val waterMl: Int,
    val bmr: Int,
    val tdee: Int,
)
