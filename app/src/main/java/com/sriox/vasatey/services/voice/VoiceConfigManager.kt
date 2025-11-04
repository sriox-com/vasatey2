package com.sriox.vasatey.services.voice

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * Voice detection configuration and settings
 */
@Serializable
data class VoiceDetectionConfig(
    val isEnabled: Boolean = true,
    val wakeWordSensitivity: Float = 0.5f,
    val voiceActivitySensitivity: Float = 0.5f,
    val customWakeWords: List<String> = emptyList(),
    val emergencyPhrases: List<String> = listOf(
        "help me",
        "emergency",
        "call help",
        "i need help",
        "assistance",
        "call nine one one",
        "call police",
        "call ambulance",
        "medical emergency",
        "fire emergency"
    ),
    val enableContinuousListening: Boolean = true,
    val enableBackgroundDetection: Boolean = true,
    val minimumConfidenceThreshold: Float = 0.3f,
    val falsePositiveReduction: Boolean = true,
    val audioProcessingMode: AudioProcessingMode = AudioProcessingMode.BALANCED,
    val maxDetectionsPerMinute: Int = 5,
    val cooldownPeriodMs: Long = 2000L
)

/**
 * Audio processing modes for different scenarios
 */
@Serializable
enum class AudioProcessingMode(val displayName: String, val description: String) {
    HIGH_ACCURACY("High Accuracy", "Best accuracy, higher battery usage"),
    BALANCED("Balanced", "Good accuracy with moderate battery usage"),
    POWER_SAVE("Power Save", "Lower accuracy, extended battery life")
}

/**
 * Voice detection configuration manager
 */
class VoiceConfigManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "voice_detection_config"
        private const val KEY_CONFIG = "voice_config"
        private const val KEY_CUSTOM_PHRASES = "custom_phrases"
        private const val KEY_LAST_CALIBRATION = "last_calibration"
    }
    
    private val sharedPrefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Get current voice detection configuration
     */
    fun getConfig(): VoiceDetectionConfig {
        val configJson = sharedPrefs.getString(KEY_CONFIG, null)
        return if (configJson != null) {
            try {
                json.decodeFromString<VoiceDetectionConfig>(configJson)
            } catch (e: Exception) {
                VoiceDetectionConfig() // Return default config on error
            }
        } else {
            VoiceDetectionConfig() // Return default config
        }
    }
    
    /**
     * Save voice detection configuration
     */
    fun saveConfig(config: VoiceDetectionConfig) {
        val configJson = json.encodeToString(config)
        sharedPrefs.edit()
            .putString(KEY_CONFIG, configJson)
            .apply()
    }
    
    /**
     * Update specific configuration fields
     */
    fun updateConfig(block: VoiceDetectionConfig.() -> VoiceDetectionConfig) {
        val currentConfig = getConfig()
        val updatedConfig = currentConfig.block()
        saveConfig(updatedConfig)
    }
    
    /**
     * Add custom wake word
     */
    fun addCustomWakeWord(wakeWord: String) {
        updateConfig {
            copy(customWakeWords = customWakeWords + wakeWord.lowercase().trim())
        }
    }
    
    /**
     * Remove custom wake word
     */
    fun removeCustomWakeWord(wakeWord: String) {
        updateConfig {
            copy(customWakeWords = customWakeWords.filter { it != wakeWord.lowercase().trim() })
        }
    }
    
    /**
     * Add custom emergency phrase
     */
    fun addCustomEmergencyPhrase(phrase: String) {
        updateConfig {
            copy(emergencyPhrases = emergencyPhrases + phrase.lowercase().trim())
        }
    }
    
    /**
     * Remove custom emergency phrase
     */
    fun removeCustomEmergencyPhrase(phrase: String) {
        updateConfig {
            copy(emergencyPhrases = emergencyPhrases.filter { it != phrase.lowercase().trim() })
        }
    }
    
    /**
     * Get all wake words (default + custom)
     */
    fun getAllWakeWords(): List<String> {
        val config = getConfig()
        val defaultWakeWords = listOf("help me", "emergency", "call help", "i need help", "assistance")
        return (defaultWakeWords + config.customWakeWords).distinct()
    }
    
    /**
     * Get all emergency phrases
     */
    fun getAllEmergencyPhrases(): List<String> {
        return getConfig().emergencyPhrases
    }
    
    /**
     * Reset configuration to defaults
     */
    fun resetToDefaults() {
        saveConfig(VoiceDetectionConfig())
    }
    
    /**
     * Set voice detection enabled/disabled
     */
    fun setVoiceDetectionEnabled(enabled: Boolean) {
        updateConfig {
            copy(isEnabled = enabled)
        }
    }
    
    /**
     * Set wake word sensitivity
     */
    fun setWakeWordSensitivity(sensitivity: Float) {
        val clampedSensitivity = sensitivity.coerceIn(0.0f, 1.0f)
        updateConfig {
            copy(wakeWordSensitivity = clampedSensitivity)
        }
    }
    
    /**
     * Set voice activity sensitivity
     */
    fun setVoiceActivitySensitivity(sensitivity: Float) {
        val clampedSensitivity = sensitivity.coerceIn(0.0f, 1.0f)
        updateConfig {
            copy(voiceActivitySensitivity = clampedSensitivity)
        }
    }
    
    /**
     * Set minimum confidence threshold
     */
    fun setMinimumConfidenceThreshold(threshold: Float) {
        val clampedThreshold = threshold.coerceIn(0.0f, 1.0f)
        updateConfig {
            copy(minimumConfidenceThreshold = clampedThreshold)
        }
    }
    
    /**
     * Set audio processing mode
     */
    fun setAudioProcessingMode(mode: AudioProcessingMode) {
        updateConfig {
            copy(audioProcessingMode = mode)
        }
    }
    
    /**
     * Set continuous listening enabled/disabled
     */
    fun setContinuousListeningEnabled(enabled: Boolean) {
        updateConfig {
            copy(enableContinuousListening = enabled)
        }
    }
    
    /**
     * Set background detection enabled/disabled
     */
    fun setBackgroundDetectionEnabled(enabled: Boolean) {
        updateConfig {
            copy(enableBackgroundDetection = enabled)
        }
    }
    
    /**
     * Set false positive reduction enabled/disabled
     */
    fun setFalsePositiveReductionEnabled(enabled: Boolean) {
        updateConfig {
            copy(falsePositiveReduction = enabled)
        }
    }
    
    /**
     * Set maximum detections per minute
     */
    fun setMaxDetectionsPerMinute(maxDetections: Int) {
        val clampedMax = maxDetections.coerceIn(1, 30)
        updateConfig {
            copy(maxDetectionsPerMinute = clampedMax)
        }
    }
    
    /**
     * Set cooldown period between detections
     */
    fun setCooldownPeriod(cooldownMs: Long) {
        val clampedCooldown = cooldownMs.coerceIn(500L, 10000L)
        updateConfig {
            copy(cooldownPeriodMs = clampedCooldown)
        }
    }
    
    /**
     * Save calibration timestamp
     */
    fun saveCalibrationTimestamp() {
        sharedPrefs.edit()
            .putLong(KEY_LAST_CALIBRATION, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Get last calibration timestamp
     */
    fun getLastCalibrationTimestamp(): Long {
        return sharedPrefs.getLong(KEY_LAST_CALIBRATION, 0L)
    }
    
    /**
     * Check if calibration is needed (older than 7 days)
     */
    fun isCalibrationNeeded(): Boolean {
        val lastCalibration = getLastCalibrationTimestamp()
        val weekInMs = 7 * 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - lastCalibration > weekInMs
    }
    
    /**
     * Export configuration as JSON string
     */
    fun exportConfig(): String {
        return json.encodeToString(getConfig())
    }
    
    /**
     * Import configuration from JSON string
     */
    fun importConfig(configJson: String): Boolean {
        return try {
            val config = json.decodeFromString<VoiceDetectionConfig>(configJson)
            saveConfig(config)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validate configuration
     */
    fun validateConfig(config: VoiceDetectionConfig): List<String> {
        val errors = mutableListOf<String>()
        
        if (config.wakeWordSensitivity < 0.0f || config.wakeWordSensitivity > 1.0f) {
            errors.add("Wake word sensitivity must be between 0.0 and 1.0")
        }
        
        if (config.voiceActivitySensitivity < 0.0f || config.voiceActivitySensitivity > 1.0f) {
            errors.add("Voice activity sensitivity must be between 0.0 and 1.0")
        }
        
        if (config.minimumConfidenceThreshold < 0.0f || config.minimumConfidenceThreshold > 1.0f) {
            errors.add("Minimum confidence threshold must be between 0.0 and 1.0")
        }
        
        if (config.maxDetectionsPerMinute < 1 || config.maxDetectionsPerMinute > 30) {
            errors.add("Max detections per minute must be between 1 and 30")
        }
        
        if (config.cooldownPeriodMs < 500L || config.cooldownPeriodMs > 10000L) {
            errors.add("Cooldown period must be between 500ms and 10000ms")
        }
        
        if (config.customWakeWords.any { it.isBlank() }) {
            errors.add("Custom wake words cannot be empty")
        }
        
        if (config.emergencyPhrases.any { it.isBlank() }) {
            errors.add("Emergency phrases cannot be empty")
        }
        
        return errors
    }
    
    /**
     * Get recommended settings based on device capabilities
     */
    fun getRecommendedSettings(context: Context): VoiceDetectionConfig {
        // Basic device capability detection
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val isLowMemoryDevice = memoryInfo.totalMem < 2L * 1024 * 1024 * 1024 // Less than 2GB RAM
        
        return if (isLowMemoryDevice) {
            // Power save settings for low-end devices
            VoiceDetectionConfig(
                audioProcessingMode = AudioProcessingMode.POWER_SAVE,
                wakeWordSensitivity = 0.6f,
                voiceActivitySensitivity = 0.6f,
                minimumConfidenceThreshold = 0.4f,
                maxDetectionsPerMinute = 3,
                cooldownPeriodMs = 3000L,
                falsePositiveReduction = true
            )
        } else {
            // Balanced settings for mid-range and high-end devices
            VoiceDetectionConfig(
                audioProcessingMode = AudioProcessingMode.BALANCED,
                wakeWordSensitivity = 0.5f,
                voiceActivitySensitivity = 0.5f,
                minimumConfidenceThreshold = 0.3f,
                maxDetectionsPerMinute = 5,
                cooldownPeriodMs = 2000L,
                falsePositiveReduction = true
            )
        }
    }
}