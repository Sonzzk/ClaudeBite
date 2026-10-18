package com.sonzzk.claudebite.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "profile")

/** Gamification state that lives next to the profile. */
data class Progression(
    val xp: Int = 0,
    /** 0-100: goes up when the user logs consistently, drops slowly when they forget. */
    val affection: Int = 50,
    val equippedOutfit: String = "classic",
    val lastOpenedEpochDay: Long = 0,
)

class ProfileRepository(private val context: Context) {

    private object Keys {
        val name = stringPreferencesKey("name")
        val sex = stringPreferencesKey("sex")
        val age = intPreferencesKey("age")
        val height = floatPreferencesKey("height_cm")
        val weight = floatPreferencesKey("weight_kg")
        val target = floatPreferencesKey("target_kg")
        val activity = stringPreferencesKey("activity")
        val goal = stringPreferencesKey("goal")
        val pace = stringPreferencesKey("pace")
        val diet = stringPreferencesKey("diet")
        val units = stringPreferencesKey("units")
        val water = intPreferencesKey("water_goal")
        val customCal = intPreferencesKey("custom_cal")
        val onboarded = booleanPreferencesKey("onboarded")
        val xp = intPreferencesKey("xp")
        val affection = intPreferencesKey("affection")
        val outfit = stringPreferencesKey("outfit")
        val lastOpened = longPreferencesKey("last_opened")
    }

    val profile: Flow<UserProfile> = context.dataStore.data.map { p ->
        val d = UserProfile()
        UserProfile(
            name = p[Keys.name] ?: d.name,
            sex = p[Keys.sex].toEnum(d.sex),
            age = p[Keys.age] ?: d.age,
            heightCm = p[Keys.height] ?: d.heightCm,
            weightKg = p[Keys.weight] ?: d.weightKg,
            targetWeightKg = p[Keys.target] ?: d.targetWeightKg,
            activity = p[Keys.activity].toEnum(d.activity),
            goal = p[Keys.goal].toEnum(d.goal),
            pace = p[Keys.pace].toEnum(d.pace),
            diet = p[Keys.diet].toEnum(d.diet),
            units = p[Keys.units].toEnum(d.units),
            waterGoalMl = p[Keys.water] ?: d.waterGoalMl,
            customCalorieTarget = p[Keys.customCal] ?: 0,
            onboarded = p[Keys.onboarded] ?: false,
        )
    }

    val progression: Flow<Progression> = context.dataStore.data.map { p ->
        Progression(
            xp = p[Keys.xp] ?: 0,
            affection = p[Keys.affection] ?: 50,
            equippedOutfit = p[Keys.outfit] ?: "classic",
            lastOpenedEpochDay = p[Keys.lastOpened] ?: 0,
        )
    }

    suspend fun save(profile: UserProfile) {
        context.dataStore.edit { p ->
            p[Keys.name] = profile.name
            p[Keys.sex] = profile.sex.name
            p[Keys.age] = profile.age
            p[Keys.height] = profile.heightCm
            p[Keys.weight] = profile.weightKg
            p[Keys.target] = profile.targetWeightKg
            p[Keys.activity] = profile.activity.name
            p[Keys.goal] = profile.goal.name
            p[Keys.pace] = profile.pace.name
            p[Keys.diet] = profile.diet.name
            p[Keys.units] = profile.units.name
            p[Keys.water] = profile.waterGoalMl
            p[Keys.customCal] = profile.customCalorieTarget
            p[Keys.onboarded] = profile.onboarded
        }
    }

    suspend fun updateWeight(kg: Float) {
        context.dataStore.edit { it[Keys.weight] = kg }
    }

    suspend fun addXp(amount: Int) {
        context.dataStore.edit { it[Keys.xp] = (it[Keys.xp] ?: 0) + amount }
    }

    suspend fun changeAffection(delta: Int) {
        context.dataStore.edit { it[Keys.affection] = ((it[Keys.affection] ?: 50) + delta).coerceIn(0, 100) }
    }

    suspend fun equipOutfit(id: String) {
        context.dataStore.edit { it[Keys.outfit] = id }
    }

    suspend fun markOpened(epochDay: Long) {
        context.dataStore.edit { it[Keys.lastOpened] = epochDay }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
