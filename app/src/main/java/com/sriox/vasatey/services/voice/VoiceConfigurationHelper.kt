package com.sriox.vasatey.services.voice

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * Voice configuration helper for managing Picovoice settings and user preferences
 */
class VoiceConfigurationHelper(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "voice_config_prefs"
        private const val KEY_VOICE_CONFIG = "voice_configuration"
        private const val KEY_CUSTOM_WAKE_WORDS = "custom_wake_words"
        private const val KEY_EMERGENCY_PHRASES = "emergency_phrases"
        private const val KEY_SENSITIVITY_SETTINGS = "sensitivity_settings"
        
        // Default configuration values
        private const val DEFAULT_PORCUPINE_SENSITIVITY = 0.7f
        private const val DEFAULT_RHINO_SENSITIVITY = 0.6f
        private const val DEFAULT_ENDPOINT_DURATION = 2.0f
        private val DEFAULT_WAKE_WORDS = arrayOf("hey-vasatey", "emergency-vasatey")
    }
    
    private val sharedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences if encryption fails
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Voice configuration data class
     */
    @Serializable
    data class VoiceConfiguration(
        val isVoiceDetectionEnabled: Boolean = true,
        val isWakeWordEnabled: Boolean = true,
        val isContinuousListeningEnabled: Boolean = true,
        val porcupineSensitivity: Float = DEFAULT_PORCUPINE_SENSITIVITY,
        val rhinoSensitivity: Float = DEFAULT_RHINO_SENSITIVITY,
        val endpointDurationSeconds: Float = DEFAULT_ENDPOINT_DURATION,
        val requireEndpoint: Boolean = true,
        val enableAudioLogging: Boolean = false,
        val maxSessionDurationMinutes: Int = 60,
        val autoRestartOnError: Boolean = true,
        val enableVibrationFeedback: Boolean = true,
        val enableAudioFeedback: Boolean = false,
        val customWakeWords: List<String> = emptyList(),
        val emergencyPhrases: Map<String, String> = emptyMap(),
        val lastUpdated: String = ""
    )
    
    /**
     * Custom wake word configuration
     */
    @Serializable
    data class CustomWakeWord(
        val keyword: String,
        val sensitivity: Float,
        val isEnabled: Boolean = true,
        val createdAt: String,
        val useCount: Int = 0
    )
    
    /**
     * Emergency phrase configuration
     */
    @Serializable
    data class EmergencyPhrase(
        val phrase: String,
        val emergencyType: String,
        val isEnabled: Boolean = true,
        val priority: Int = 1, // 1 = high, 2 = medium, 3 = low
        val requireConfirmation: Boolean = true,
        val createdAt: String,
        val useCount: Int = 0
    )
    
    /**
     * Sensitivity settings for different scenarios
     */
    @Serializable
    data class SensitivitySettings(
        val quietEnvironment: Float = 0.8f,
        val normalEnvironment: Float = 0.7f,
        val noisyEnvironment: Float = 0.5f,
        val sleepMode: Float = 0.9f,
        val currentSetting: String = "normalEnvironment"
    )
    
    /**
     * Get current voice configuration
     */
    fun getVoiceConfiguration(): VoiceConfiguration {
        return try {
            val configJson = sharedPrefs.getString(KEY_VOICE_CONFIG, null)
            if (configJson != null) {
                json.decodeFromString<VoiceConfiguration>(configJson)
            } else {
                val defaultConfig = VoiceConfiguration()
                saveVoiceConfiguration(defaultConfig)
                defaultConfig
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceConfigHelper", "Failed to load voice configuration", e)
            VoiceConfiguration()
        }
    }
    
    /**
     * Save voice configuration
     */
    fun saveVoiceConfiguration(config: VoiceConfiguration) {
        try {
            val configJson = json.encodeToString(config.copy(
                lastUpdated = java.time.LocalDateTime.now().toString()
            ))
            sharedPrefs.edit()
                .putString(KEY_VOICE_CONFIG, configJson)
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("VoiceConfigHelper", "Failed to save voice configuration", e)
        }
    }
    
    /**
     * Get custom wake words
     */
    fun getCustomWakeWords(): List<CustomWakeWord> {
        return try {
            val wakeWordsJson = sharedPrefs.getString(KEY_CUSTOM_WAKE_WORDS, null)
            if (wakeWordsJson != null) {
                json.decodeFromString<List<CustomWakeWord>>(wakeWordsJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceConfigHelper", "Failed to load custom wake words", e)
            emptyList()
        }
    }
    
    /**
     * Save custom wake words
     */
    fun saveCustomWakeWords(wakeWords: List<CustomWakeWord>) {
        try {
            val wakeWordsJson = json.encodeToString(wakeWords)
            sharedPrefs.edit()
                .putString(KEY_CUSTOM_WAKE_WORDS, wakeWordsJson)
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("VoiceConfigHelper", "Failed to save custom wake words", e)
        }
    }
    
    /**
     * Add custom wake word
     */
    fun addCustomWakeWord(keyword: String, sensitivity: Float): Boolean {
        return try {
            val currentWakeWords = getCustomWakeWords().toMutableList()
            
            // Check if wake word already exists
            if (currentWakeWords.any { it.keyword.equals(keyword, ignoreCase = true) }) {
                return false
            }
            
            val newWakeWord = CustomWakeWord(
                keyword = keyword,
                sensitivity = sensitivity,
                createdAt = java.time.LocalDateTime.now().toString()
            )
            
            currentWakeWords.add(newWakeWord)
            saveCustomWakeWords(currentWakeWords)
            true
        } catch (e: Exception) {
            android.util.Log.e("VoiceConfigHelper", "Failed to add custom wake word", e)
            false
        }
    }
    
    /**
     * Get emergency phrases
     */
    fun getEmergencyPhrases(): List<EmergencyPhrase> {
        return try {
            val phrasesJson = sharedPrefs.getString(KEY_EMERGENCY_PHRASES, null)
            if (phrasesJson != null) {
                json.decodeFromString<List<EmergencyPhrase>>(phrasesJson)
            } else {
                getDefaultEmergencyPhrases()
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceConfigHelper", "Failed to load emergency phrases", e)
            getDefaultEmergencyPhrases()
        }
    }
    
    /**
     * Save emergency phrases
     */
    fun saveEmergencyPhrases(phrases: List<EmergencyPhrase>) {
        try {
            val phrasesJson = json.encodeToString(phrases)
            sharedPrefs.edit()
                .putString(KEY_EMERGENCY_PHRASES, phrasesJson)
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("VoiceConfigHelper", "Failed to save emergency phrases", e)
        }
    }
    
    /**
     * Add custom emergency phrase
     */
    fun addEmergencyPhrase(
        phrase: String,
        emergencyType: String,
        priority: Int = 1,
        requireConfirmation: Boolean = true
    ): Boolean {
        return try {
            val currentPhrases = getEmergencyPhrases().toMutableList()
            
            // Check if phrase already exists
            if (currentPhrases.any { it.phrase.equals(phrase, ignoreCase = true) }) {
                return false
            }
            
            val newPhrase = EmergencyPhrase(
                phrase = phrase,
                emergencyType = emergencyType,
                priority = priority,
                requireConfirmation = requireConfirmation,
                createdAt = java.time.LocalDateTime.now().toString()
            )
            
            currentPhrases.add(newPhrase)
            saveEmergencyPhrases(currentPhrases)
            true
        } catch (e: Exception) {
            android.util.Log.e("VoiceConfigHelper", "Failed to add emergency phrase", e)
            false
        }
    }
    
    /**
     * Get sensitivity settings
     */
    fun getSensitivitySettings(): SensitivitySettings {
        return try {
            val settingsJson = sharedPrefs.getString(KEY_SENSITIVITY_SETTINGS, null)
            if (settingsJson != null) {
                json.decodeFromString<SensitivitySettings>(settingsJson)
            } else {
                val defaultSettings = SensitivitySettings()
                saveSensitivitySettings(defaultSettings)
                defaultSettings
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceConfigHelper", "Failed to load sensitivity settings", e)
            SensitivitySettings()
        }
    }
    
    /**
     * Save sensitivity settings
     */
    fun saveSensitivitySettings(settings: SensitivitySettings) {
        try {
            val settingsJson = json.encodeToString(settings)
            sharedPrefs.edit()
                .putString(KEY_SENSITIVITY_SETTINGS, settingsJson)
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("VoiceConfigHelper", "Failed to save sensitivity settings", e)
        }
    }
    
    /**
     * Get current sensitivity based on environment
     */
    fun getCurrentSensitivity(): Float {
        val settings = getSensitivitySettings()
        return when (settings.currentSetting) {
            "quietEnvironment" -> settings.quietEnvironment
            "noisyEnvironment" -> settings.noisyEnvironment
            "sleepMode" -> settings.sleepMode
            else -> settings.normalEnvironment
        }
    }
    
    /**
     * Update sensitivity environment
     */
    fun updateSensitivityEnvironment(environment: String) {
        val settings = getSensitivitySettings()
        saveSensitivitySettings(settings.copy(currentSetting = environment))
    }
    
    /**
     * Get all wake words (default + custom)
     */
    fun getAllWakeWords(): Array<String> {
        val customWakeWords = getCustomWakeWords()
            .filter { it.isEnabled }
            .map { it.keyword }
        
        return (DEFAULT_WAKE_WORDS.toList() + customWakeWords).toTypedArray()
    }
    
    /**
     * Get all wake word sensitivities
     */
    fun getAllWakeWordSensitivities(): FloatArray {
        val config = getVoiceConfiguration()
        val customWakeWords = getCustomWakeWords().filter { it.isEnabled }
        
        val defaultSensitivities = FloatArray(DEFAULT_WAKE_WORDS.size) { config.porcupineSensitivity }
        val customSensitivities = customWakeWords.map { it.sensitivity }.toFloatArray()
        
        return defaultSensitivities + customSensitivities
    }
    
    /**
     * Get enabled emergency phrases as map
     */
    fun getEnabledEmergencyPhrasesMap(): Map<String, String> {
        return getEmergencyPhrases()
            .filter { it.isEnabled }
            .associate { it.phrase to it.emergencyType }
    }
    
    /**
     * Increment wake word use count
     */
    fun incrementWakeWordUseCount(keyword: String) {
        try {
            val wakeWords = getCustomWakeWords().toMutableList()
            val index = wakeWords.indexOfFirst { it.keyword.equals(keyword, ignoreCase = true) }
            
            if (index >= 0) {
                wakeWords[index] = wakeWords[index].copy(useCount = wakeWords[index].useCount + 1)
                saveCustomWakeWords(wakeWords)
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceConfigHelper", "Failed to increment wake word use count", e)
        }
    }
    
    /**
     * Increment emergency phrase use count
     */
    fun incrementEmergencyPhraseUseCount(phrase: String) {
        try {
            val phrases = getEmergencyPhrases().toMutableList()
            val index = phrases.indexOfFirst { it.phrase.equals(phrase, ignoreCase = true) }
            
            if (index >= 0) {
                phrases[index] = phrases[index].copy(useCount = phrases[index].useCount + 1)
                saveEmergencyPhrases(phrases)
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceConfigHelper", "Failed to increment phrase use count", e)
        }
    }
    
    /**
     * Reset all configuration to defaults
     */
    fun resetToDefaults() {
        try {
            sharedPrefs.edit().clear().apply()
            android.util.Log.d("VoiceConfigHelper", "Voice configuration reset to defaults")
        } catch (e: Exception) {
            android.util.Log.e("VoiceConfigHelper", "Failed to reset configuration", e)
        }
    }
    
    /**
     * Get default emergency phrases
     */
    private fun getDefaultEmergencyPhrases(): List<EmergencyPhrase> {
        val now = java.time.LocalDateTime.now().toString()
        return listOf(
            EmergencyPhrase("help me", "general_emergency", priority = 1, createdAt = now),
            EmergencyPhrase("call police", "police_emergency", priority = 1, createdAt = now),
            EmergencyPhrase("call ambulance", "medical_emergency", priority = 1, createdAt = now),
            EmergencyPhrase("call fire department", "fire_emergency", priority = 1, createdAt = now),
            EmergencyPhrase("i need help", "general_emergency", priority = 2, createdAt = now),
            EmergencyPhrase("emergency", "general_emergency", priority = 1, createdAt = now),
            EmergencyPhrase("medical emergency", "medical_emergency", priority = 1, createdAt = now),
            EmergencyPhrase("fire emergency", "fire_emergency", priority = 1, createdAt = now)
        )
    }
    
    /**
     * Export configuration as JSON string
     */
    fun exportConfiguration(): String {
        return try {
            val config = mapOf(
                "voiceConfiguration" to getVoiceConfiguration(),
                "customWakeWords" to getCustomWakeWords(),
                "emergencyPhrases" to getEmergencyPhrases(),
                "sensitivitySettings" to getSensitivitySettings()
            )
            json.encodeToString(config)
        } catch (e: Exception) {
            android.util.Log.e("VoiceConfigHelper", "Failed to export configuration", e)
            "{}"
        }
    }
    
    /**
     * Import configuration from JSON string
     */
    fun importConfiguration(configJson: String): Boolean {
        return try {
            // TODO: Implement configuration import
            // This would parse the JSON and update all settings
            android.util.Log.d("VoiceConfigHelper", "Configuration import not yet implemented")
            false
        } catch (e: Exception) {
            android.util.Log.e("VoiceConfigHelper", "Failed to import configuration", e)
            false
        }
    }
}