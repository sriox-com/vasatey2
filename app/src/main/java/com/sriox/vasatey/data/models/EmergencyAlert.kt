package com.sriox.vasatey.data.models

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Emergency alert data model representing emergency incidents
 * Maps to the 'emergency_alerts' table in Supabase
 */
@Serializable
data class EmergencyAlert(
    val id: String,
    val userId: String,
    
    // Alert Classification
    val alertType: String = "general",
    val severityLevel: Int = 1,
    val status: String = "active",
    
    // Trigger Information
    val triggerMethod: String,
    val voicePhraseDetected: String? = null,
    val confidenceScore: Double? = null,
    
    // Location Data
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val locationAccuracy: Double? = null,
    val addressDescription: String? = null,
    val indoorLocation: String? = null,
    
    // Timing
    val detectedAt: String,
    val acknowledgedAt: String? = null,
    val respondedAt: String? = null,
    val resolvedAt: String? = null,
    val cancelledAt: String? = null,
    
    // Response Information
    val responderType: String? = null,
    val responseTimeSeconds: Int? = null,
    val resolutionNotes: String? = null,
    val wasFalseAlarm: Boolean = false,
    val falseAlarmReason: String? = null,
    
    // Communication
    val notificationsSent: Int = 0,
    val callsMade: Int = 0,
    val smsSent: Int = 0,
    val emailsSent: Int = 0,
    
    // Device Information
    val deviceInfo: Map<String, String> = emptyMap(),
    val batteryLevel: Int? = null,
    val networkType: String? = null,
    val signalStrength: Int? = null,
    
    // Additional Context
    val userReportedDescription: String? = null,
    val audioFileUrl: String? = null,
    val photos: List<String> = emptyList(),
    val weatherConditions: Map<String, String> = emptyMap(),
    
    // Verification
    val requiresVerification: Boolean = false,
    val verifiedBy: String? = null,
    val verifiedAt: String? = null,
    val verificationMethod: String? = null,
    
    // Audit fields
    val createdAt: String,
    val updatedAt: String
) {
    
    /**
     * Alert types enumeration
     */
    enum class AlertType(val value: String, val displayName: String) {
        GENERAL("general", "General Emergency"),
        VOICE_DETECTED("voice_detected", "Voice Command Detected"),
        MANUAL("manual", "Manual Trigger"),
        FALL_DETECTED("fall_detected", "Fall Detected"),
        PANIC("panic", "Panic Alert"),
        MEDICAL("medical", "Medical Emergency"),
        FIRE("fire", "Fire Emergency"),
        INTRUSION("intrusion", "Security Breach");
        
        companion object {
            fun fromValue(value: String): AlertType {
                return values().find { it.value == value } ?: GENERAL
            }
        }
    }
    
    /**
     * Alert severity levels
     */
    enum class SeverityLevel(val level: Int, val displayName: String, val color: String) {
        LOW(1, "Low", "#4CAF50"),
        MEDIUM(2, "Medium", "#FF9800"),
        HIGH(3, "High", "#F44336"),
        CRITICAL(4, "Critical", "#9C27B0"),
        EMERGENCY(5, "Emergency", "#E91E63");
        
        companion object {
            fun fromLevel(level: Int): SeverityLevel {
                return values().find { it.level == level } ?: LOW
            }
        }
    }
    
    /**
     * Alert status enumeration
     */
    enum class Status(val value: String, val displayName: String) {
        ACTIVE("active", "Active"),
        ACKNOWLEDGED("acknowledged", "Acknowledged"),
        RESPONDING("responding", "Responding"),
        RESOLVED("resolved", "Resolved"),
        FALSE_ALARM("false_alarm", "False Alarm"),
        CANCELLED("cancelled", "Cancelled");
        
        companion object {
            fun fromValue(value: String): Status {
                return values().find { it.value == value } ?: ACTIVE
            }
        }
    }
    
    /**
     * Get alert type enum
     */
    fun getAlertType(): AlertType = AlertType.fromValue(alertType)
    
    /**
     * Get severity level enum
     */
    fun getSeverityLevel(): SeverityLevel = SeverityLevel.fromLevel(severityLevel)
    
    /**
     * Get status enum
     */
    fun getStatus(): Status = Status.fromValue(status)
    
    /**
     * Check if alert is currently active (not resolved or cancelled)
     */
    fun isActive(): Boolean = status in listOf("active", "acknowledged", "responding")
    
    /**
     * Check if alert requires immediate attention
     */
    fun requiresImmediateAttention(): Boolean = isActive() && severityLevel >= 4
    
    /**
     * Get formatted detection time
     */
    fun getFormattedDetectedAt(): String {
        return try {
            val dateTime = LocalDateTime.parse(detectedAt, DateTimeFormatter.ISO_DATE_TIME)
            dateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))
        } catch (e: Exception) {
            detectedAt
        }
    }
    
    /**
     * Get time since detection
     */
    fun getTimeSinceDetection(): String {
        return try {
            val detectedTime = LocalDateTime.parse(detectedAt, DateTimeFormatter.ISO_DATE_TIME)
            val now = LocalDateTime.now()
            val duration = java.time.Duration.between(detectedTime, now)
            
            when {
                duration.toMinutes() < 1 -> "Just now"
                duration.toMinutes() < 60 -> "${duration.toMinutes()} minutes ago"
                duration.toHours() < 24 -> "${duration.toHours()} hours ago"
                else -> "${duration.toDays()} days ago"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Get response time if available
     */
    fun getResponseTime(): Duration? {
        return responseTimeSeconds?.seconds
    }
    
    /**
     * Check if alert has location data
     */
    fun hasLocation(): Boolean = latitude != null && longitude != null
    
    /**
     * Get location coordinate pair
     */
    fun getLocationPair(): Pair<Double, Double>? {
        return if (hasLocation()) Pair(latitude!!, longitude!!) else null
    }
    
    /**
     * Get display address
     */
    fun getDisplayAddress(): String {
        return addressDescription?.takeIf { it.isNotBlank() }
            ?: indoorLocation?.takeIf { it.isNotBlank() }
            ?: if (hasLocation()) "Lat: ${latitude}, Lng: ${longitude}" 
            else "Location unknown"
    }
    
    /**
     * Check if alert was triggered by voice
     */
    fun isVoiceTriggered(): Boolean = triggerMethod == "voice_command" && !voicePhraseDetected.isNullOrBlank()
    
    /**
     * Get confidence score as percentage
     */
    fun getConfidencePercentage(): Int? = confidenceScore?.let { (it * 100).toInt() }
    
    /**
     * Check if alert requires user verification
     */
    fun needsVerification(): Boolean = requiresVerification && verifiedAt == null
    
    /**
     * Get total communications sent
     */
    fun getTotalCommunications(): Int = notificationsSent + callsMade + smsSent + emailsSent
    
    companion object {
        /**
         * Create new emergency alert
         */
        fun create(
            userId: String,
            alertType: String,
            severityLevel: Int,
            triggerMethod: String,
            latitude: Double? = null,
            longitude: Double? = null,
            voicePhrase: String? = null,
            confidence: Double? = null
        ): EmergencyAlert {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            return EmergencyAlert(
                id = "", // Will be set by database
                userId = userId,
                alertType = alertType,
                severityLevel = severityLevel,
                triggerMethod = triggerMethod,
                latitude = latitude,
                longitude = longitude,
                voicePhraseDetected = voicePhrase,
                confidenceScore = confidence,
                detectedAt = now,
                createdAt = now,
                updatedAt = now
            )
        }
        
        /**
         * Create voice-triggered alert
         */
        fun createVoiceAlert(
            userId: String,
            phrase: String,
            confidence: Double,
            latitude: Double? = null,
            longitude: Double? = null
        ): EmergencyAlert {
            val severity = when {
                confidence >= 0.9 -> 5 // Emergency
                confidence >= 0.8 -> 4 // Critical
                confidence >= 0.7 -> 3 // High
                else -> 2 // Medium
            }
            
            return create(
                userId = userId,
                alertType = "voice_detected",
                severityLevel = severity,
                triggerMethod = "voice_command",
                latitude = latitude,
                longitude = longitude,
                voicePhrase = phrase,
                confidence = confidence
            )
        }
        
        /**
         * Create manual alert
         */
        fun createManualAlert(
            userId: String,
            latitude: Double? = null,
            longitude: Double? = null,
            description: String? = null
        ): EmergencyAlert {
            return create(
                userId = userId,
                alertType = "manual",
                severityLevel = 4, // Critical by default for manual triggers
                triggerMethod = "manual_button",
                latitude = latitude,
                longitude = longitude
            ).copy(userReportedDescription = description)
        }
    }
}

/**
 * Alert response data model for tracking responses to alerts
 * Maps to the 'alert_responses' table in Supabase
 */
@Serializable
data class AlertResponse(
    val id: String,
    val alertId: String,
    val responderUserId: String? = null,
    
    // Response Details
    val responseType: String,
    val responseMethod: String? = null,
    val responderName: String? = null,
    val responderOrganization: String? = null,
    val responderContact: String? = null,
    
    // Location and Timing
    val responderLatitude: Double? = null,
    val responderLongitude: Double? = null,
    val estimatedArrivalTime: String? = null,
    val actualArrivalTime: String? = null,
    
    // Communication
    val responseMessage: String? = null,
    val communicationLog: List<Map<String, String>> = emptyList(),
    val statusUpdates: List<Map<String, String>> = emptyList(),
    
    // Audit fields
    val createdAt: String,
    val updatedAt: String
) {
    
    /**
     * Response types enumeration
     */
    enum class ResponseType(val value: String, val displayName: String) {
        ACKNOWLEDGED("acknowledged", "Acknowledged"),
        DISPATCHED("dispatched", "Dispatched"),
        EN_ROUTE("en_route", "En Route"),
        ON_SCENE("on_scene", "On Scene"),
        RESOLVED("resolved", "Resolved");
        
        companion object {
            fun fromValue(value: String): ResponseType {
                return values().find { it.value == value } ?: ACKNOWLEDGED
            }
        }
    }
    
    /**
     * Get response type enum
     */
    fun getResponseType(): ResponseType = ResponseType.fromValue(responseType)
    
    /**
     * Get formatted response time
     */
    fun getFormattedCreatedAt(): String {
        return try {
            val dateTime = LocalDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME)
            dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            createdAt
        }
    }
    
    /**
     * Check if responder has location
     */
    fun hasResponderLocation(): Boolean = responderLatitude != null && responderLongitude != null
    
    /**
     * Get estimated time of arrival if available
     */
    fun getFormattedETA(): String? {
        return estimatedArrivalTime?.let {
            try {
                val dateTime = LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
                dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            } catch (e: Exception) {
                it
            }
        }
    }
}