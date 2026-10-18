package com.sonzzk.claudebite.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How Claude-chan picks a model.
 * AUTO picks per task (cheap Haiku for chat and text, Sonnet for photos and plans).
 */
enum class ModelMode(val label: String, val description: String) {
    AUTO("Auto (recommended)", "Picks the cheapest model that does each job well"),
    ECONOMY("Economy", "Always the cheapest model. Photo estimates are a bit rougher"),
    BEST("Best quality", "Always the strongest model. Costs more per message"),
}

/** API key and AI settings, stored encrypted on the device. The key never leaves the phone except to api.anthropic.com. */
class SecureSettings(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "secure_settings", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        // Some emulators/devices have a broken keystore; fall back to private prefs rather than crash.
        context.getSharedPreferences("secure_settings_fallback", Context.MODE_PRIVATE)
    }

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API, "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _modelMode = MutableStateFlow(
        runCatching { ModelMode.valueOf(prefs.getString(KEY_MODE, null) ?: "") }.getOrDefault(ModelMode.AUTO)
    )
    val modelMode: StateFlow<ModelMode> = _modelMode.asStateFlow()

    fun setApiKey(value: String) {
        prefs.edit().putString(KEY_API, value.trim()).apply()
        _apiKey.value = value.trim()
    }

    fun setModelMode(mode: ModelMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _modelMode.value = mode
    }

    val hasApiKey: Boolean get() = _apiKey.value.isNotBlank()

    private companion object {
        const val KEY_API = "anthropic_api_key"
        const val KEY_MODE = "model_mode"
    }
}
