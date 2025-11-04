package com.sriox.vasatey.data.models

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Voice detection log data model for tracking voice detection events
 * Maps to the 'voice_detection_logs' table in Supabase
 */
@Serializable
data class VoiceDetectionLog(
    val id: String,
    val userId: String,
    val sessionId: String? = null,
    val alertId: String? = null,
    
    // Detection Details
    val wakeWordDetected: String? = null,
    val phraseDetected: String? = null,
    val confidenceScore: Double? = null,
    val processingTimeMs: Int? = null,
    
    // Audio Information
    val audioDurationMs: Int? = null,
    val audioSampleRate: Int? = null,
    val audioFileUrl: String? = null,
    val noiseLevel: Double? = null,
    
    // Context
    val triggerSource: String,
    val wasFalsePositive: Boolean = false,
    val userConfirmed: Boolean? = null,
    
    // Device State
    val batteryLevel: Int? = null,
    val isCharging: Boolean? = null,
    val screenOn: Boolean? = null,
    val appInForeground: Boolean? = null,
    
    // Location
    val latitude: Double? = null,
    val longitude: Double? = null,
    
    // Audit fields
    val detectedAt: String,
    val createdAt: String
) {
    
    /**
     * Trigger source enumeration
     */
    enum class TriggerSource(val value: String, val displayName: String) {
        CONTINUOUS_LISTENING("continuous_listening", "Continuous Listening"),
        MANUAL_ACTIVATION("manual_activation", "Manual Activation"),
        KEYWORD_SPOTTED("keyword_spotted", "Keyword Spotted");
        
        companion object {
            fun fromValue(value: String): TriggerSource {
                return values().find { it.value == value } ?: CONTINUOUS_LISTENING
            }
        }
    }
    
    /**
     * Get trigger source enum
     */
    fun getTriggerSource(): TriggerSource = TriggerSource.fromValue(triggerSource)
    
    /**
     * Check if detection was successful
     */
    fun wasSuccessful(): Boolean = !wasFalsePositive && confidenceScore?.let { it >= 0.7 } ?: false
    
    /**
     * Get confidence percentage
     */
    fun getConfidencePercentage(): Int? = confidenceScore?.let { (it * 100).toInt() }
    
    /**
     * Get formatted detection time
     */
    fun getFormattedDetectedAt(): String {
        return try {
            val dateTime = LocalDateTime.parse(detectedAt, DateTimeFormatter.ISO_DATE_TIME)
            dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        } catch (e: Exception) {
            detectedAt
        }
    }
    
    /**
     * Check if detection has location data
     */
    fun hasLocation(): Boolean = latitude != null && longitude != null
    
    /**
     * Get processing performance rating
     */
    fun getProcessingPerformance(): String {
        return when (processingTimeMs) {
            null -> "Unknown"
            in 0..100 -> "Excellent"
            in 101..300 -> "Good"
            in 301..500 -> "Fair"
            else -> "Poor"
        }
    }
    
    companion object {
        /**
         * Create new voice detection log
         */
        fun create(
            userId: String,
            sessionId: String?,
            wakeWord: String?,
            phrase: String?,
            confidence: Double?,
            triggerSource: String,
            processingTime: Int? = null
        ): VoiceDetectionLog {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            return VoiceDetectionLog(
                id = "",
                userId = userId,
                sessionId = sessionId,
                wakeWordDetected = wakeWord,
                phraseDetected = phrase,
                confidenceScore = confidence,
                processingTimeMs = processingTime,
                triggerSource = triggerSource,
                detectedAt = now,
                createdAt = now
            )
        }
    }
}

/**
 * System log data model for application events and monitoring
 * Maps to the 'system_logs' table in Supabase
 */
@Serializable
data class SystemLog(
    val id: String,
    val userId: String? = null,
    val sessionId: String? = null,
    val alertId: String? = null,
    
    // Log Classification
    val logLevel: String,
    val category: String,
    val eventType: String,
    
    // Log Content
    val message: String,
    val errorCode: String? = null,
    val stackTrace: String? = null,
    
    // Context Data
    val contextData: Map<String, String> = emptyMap(),
    val userAgent: String? = null,
    val ipAddress: String? = null,
    val requestId: String? = null,
    
    // Performance Metrics
    val executionTimeMs: Int? = null,
    val memoryUsageMb: Int? = null,
    val cpuUsagePercent: Double? = null,
    
    // Audit fields
    val createdAt: String
) {
    
    /**
     * Log levels enumeration
     */
    enum class LogLevel(val value: String, val displayName: String, val priority: Int) {
        DEBUG("DEBUG", "Debug", 1),
        INFO("INFO", "Info", 2),
        WARN("WARN", "Warning", 3),
        ERROR("ERROR", "Error", 4),
        CRITICAL("CRITICAL", "Critical", 5);
        
        companion object {
            fun fromValue(value: String): LogLevel {
                return values().find { it.value == value } ?: INFO
            }
        }
    }
    
    /**
     * Log categories enumeration
     */
    enum class Category(val value: String, val displayName: String) {
        AUTH("AUTH", "Authentication"),
        VOICE("VOICE", "Voice Detection"),
        LOCATION("LOCATION", "Location Services"),
        NOTIFICATION("NOTIFICATION", "Notifications"),
        DATABASE("DATABASE", "Database Operations"),
        NETWORK("NETWORK", "Network Operations"),
        EMERGENCY("EMERGENCY", "Emergency System"),
        UI("UI", "User Interface"),
        SYSTEM("SYSTEM", "System Events");
        
        companion object {
            fun fromValue(value: String): Category {
                return values().find { it.value == value } ?: SYSTEM
            }
        }
    }
    
    /**
     * Get log level enum
     */
    fun getLogLevel(): LogLevel = LogLevel.fromValue(logLevel)
    
    /**
     * Get category enum
     */
    fun getCategory(): Category = Category.fromValue(category)
    
    /**
     * Check if log indicates an error
     */
    fun isError(): Boolean = logLevel in listOf("ERROR", "CRITICAL")
    
    /**
     * Check if log is high priority
     */
    fun isHighPriority(): Boolean = getLogLevel().priority >= 3
    
    /**
     * Get formatted timestamp
     */
    fun getFormattedCreatedAt(): String {
        return try {
            val dateTime = LocalDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME)
            dateTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
        } catch (e: Exception) {
            createdAt
        }
    }
    
    /**
     * Get execution performance
     */
    fun getExecutionPerformance(): String {
        return when (executionTimeMs) {
            null -> "N/A"
            in 0..50 -> "Fast"
            in 51..200 -> "Normal"
            in 201..500 -> "Slow"
            else -> "Very Slow"
        }
    }
    
    companion object {
        /**
         * Create debug log
         */
        fun debug(
            category: String,
            eventType: String,
            message: String,
            userId: String? = null,
            contextData: Map<String, String> = emptyMap()
        ): SystemLog {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            return SystemLog(
                id = "",
                userId = userId,
                logLevel = "DEBUG",
                category = category,
                eventType = eventType,
                message = message,
                contextData = contextData,
                createdAt = now
            )
        }
        
        /**
         * Create info log
         */
        fun info(
            category: String,
            eventType: String,
            message: String,
            userId: String? = null,
            contextData: Map<String, String> = emptyMap()
        ): SystemLog {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            return SystemLog(
                id = "",
                userId = userId,
                logLevel = "INFO",
                category = category,
                eventType = eventType,
                message = message,
                contextData = contextData,
                createdAt = now
            )
        }
        
        /**
         * Create error log
         */
        fun error(
            category: String,
            eventType: String,
            message: String,
            throwable: Throwable? = null,
            userId: String? = null,
            contextData: Map<String, String> = emptyMap()
        ): SystemLog {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            return SystemLog(
                id = "",
                userId = userId,
                logLevel = "ERROR",
                category = category,
                eventType = eventType,
                message = message,
                stackTrace = throwable?.stackTraceToString(),
                contextData = contextData,
                createdAt = now
            )
        }
    }
}

/**
 * App settings data model for configuration management
 * Maps to the 'app_settings' table in Supabase
 */
@Serializable
data class AppSetting(
    val id: String,
    val settingKey: String,
    val settingValue: String, // JSON string that will be parsed based on settingType
    val settingType: String,
    val description: String? = null,
    val isPublic: Boolean = false,
    val isUserConfigurable: Boolean = false,
    
    // Validation
    val validationRules: String? = null, // JSON schema
    val defaultValue: String? = null,
    
    // Environment
    val environment: String = "production",
    val version: String = "1.0.0",
    
    // Audit fields
    val createdAt: String,
    val updatedAt: String
) {
    
    /**
     * Setting types enumeration
     */
    enum class SettingType(val value: String, val displayName: String) {
        STRING("string", "String"),
        NUMBER("number", "Number"),
        BOOLEAN("boolean", "Boolean"),
        OBJECT("object", "Object"),
        ARRAY("array", "Array");
        
        companion object {
            fun fromValue(value: String): SettingType {
                return values().find { it.value == value } ?: STRING
            }
        }
    }
    
    /**
     * Get setting type enum
     */
    fun getSettingType(): SettingType = SettingType.fromValue(settingType)
    
    /**
     * Get typed value based on setting type
     */
    inline fun <reified T> getTypedValue(): T? {
        return try {
            when (getSettingType()) {
                SettingType.STRING -> settingValue.removePrefix("\"").removeSuffix("\"") as? T
                SettingType.BOOLEAN -> settingValue.toBoolean() as? T
                SettingType.NUMBER -> {
                    when (T::class) {
                        Int::class -> settingValue.toInt() as? T
                        Double::class -> settingValue.toDouble() as? T
                        Float::class -> settingValue.toFloat() as? T
                        Long::class -> settingValue.toLong() as? T
                        else -> null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get string value
     */
    fun getStringValue(): String? = getTypedValue<String>()
    
    /**
     * Get boolean value
     */
    fun getBooleanValue(): Boolean? = getTypedValue<Boolean>()
    
    /**
     * Get integer value
     */
    fun getIntValue(): Int? = getTypedValue<Int>()
    
    /**
     * Get double value
     */
    fun getDoubleValue(): Double? = getTypedValue<Double>()
    
    companion object {
        // Common setting keys
        const val APP_VERSION = "app_version"
        const val EMERGENCY_TIMEOUT_SECONDS = "emergency_timeout_seconds"
        const val MAX_VOICE_DETECTION_ATTEMPTS = "max_voice_detection_attempts"
        const val VOICE_CONFIDENCE_THRESHOLD = "voice_confidence_threshold"
        const val NOTIFICATION_RETRY_ATTEMPTS = "notification_retry_attempts"
        const val LOCATION_UPDATE_INTERVAL_MS = "location_update_interval_ms"
        const val EMERGENCY_CONTACT_LIMIT = "emergency_contact_limit"
        const val SUPPORTED_LANGUAGES = "supported_languages"
        const val MAINTENANCE_MODE = "maintenance_mode"
        const val DEBUG_MODE = "debug_mode"
    }
}

/**
 * Location data model for geographic coordinates
 */
@Serializable
data class Location(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Double? = null,
    val timestamp: String,
    val address: String? = null,
    val provider: String? = null
) {
    
    /**
     * Calculate distance to another location in meters
     */
    fun distanceTo(other: Location): Double {
        val earthRadius = 6371000.0 // Earth's radius in meters
        
        val lat1Rad = Math.toRadians(latitude)
        val lat2Rad = Math.toRadians(other.latitude)
        val deltaLatRad = Math.toRadians(other.latitude - latitude)
        val deltaLngRad = Math.toRadians(other.longitude - longitude)
        
        val a = kotlin.math.sin(deltaLatRad / 2) * kotlin.math.sin(deltaLatRad / 2) +
                kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) *
                kotlin.math.sin(deltaLngRad / 2) * kotlin.math.sin(deltaLngRad / 2)
        
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Get coordinate pair
     */
    fun getCoordinatePair(): Pair<Double, Double> = Pair(latitude, longitude)
    
    /**
     * Get formatted coordinates
     */
    fun getFormattedCoordinates(): String = "$latitude, $longitude"
    
    /**
     * Check if location is valid
     */
    fun isValid(): Boolean = 
        latitude in -90.0..90.0 && 
        longitude in -180.0..180.0
    
    companion object {
        /**
         * Create current location
         */
        fun current(
            latitude: Double,
            longitude: Double,
            altitude: Double? = null,
            accuracy: Double? = null,
            address: String? = null
        ): Location {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            return Location(
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                accuracy = accuracy,
                timestamp = now,
                address = address,
                provider = "gps"
            )
        }
    }
}