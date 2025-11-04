package com.sriox.vasatey.data.models

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Notification data model representing all types of notifications sent
 * Maps to the 'notifications' table in Supabase
 */
@Serializable
data class Notification(
    val id: String,
    val userId: String,
    val alertId: String? = null,
    
    // Notification Details
    val notificationType: String,
    val deliveryMethod: String,
    val recipientType: String,
    
    // Recipient Information
    val recipientIdentifier: String,
    val recipientName: String? = null,
    
    // Message Content
    val title: String? = null,
    val message: String,
    val priorityLevel: Int = 1,
    
    // Delivery Status
    val status: String = "pending",
    val sentAt: String? = null,
    val deliveredAt: String? = null,
    val readAt: String? = null,
    val failedAt: String? = null,
    val failureReason: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val nextRetryAt: String? = null,
    
    // External Service Details
    val externalMessageId: String? = null,
    val serviceResponse: Map<String, String> = emptyMap(),
    val costCents: Int? = null,
    
    // Audit fields
    val createdAt: String,
    val updatedAt: String
) {
    
    /**
     * Notification types enumeration
     */
    enum class NotificationType(val value: String, val displayName: String) {
        EMERGENCY_ALERT("emergency_alert", "Emergency Alert"),
        STATUS_UPDATE("status_update", "Status Update"),
        SYSTEM_NOTIFICATION("system_notification", "System Notification"),
        REMINDER("reminder", "Reminder");
        
        companion object {
            fun fromValue(value: String): NotificationType {
                return values().find { it.value == value } ?: SYSTEM_NOTIFICATION
            }
        }
    }
    
    /**
     * Delivery methods enumeration
     */
    enum class DeliveryMethod(val value: String, val displayName: String) {
        PUSH("push", "Push Notification"),
        SMS("sms", "SMS"),
        EMAIL("email", "Email"),
        CALL("call", "Phone Call"),
        WEBHOOK("webhook", "Webhook");
        
        companion object {
            fun fromValue(value: String): DeliveryMethod {
                return values().find { it.value == value } ?: PUSH
            }
        }
    }
    
    /**
     * Recipient types enumeration
     */
    enum class RecipientType(val value: String, val displayName: String) {
        USER("user", "User"),
        EMERGENCY_CONTACT("emergency_contact", "Emergency Contact"),
        RESPONDER("responder", "Emergency Responder"),
        SYSTEM("system", "System");
        
        companion object {
            fun fromValue(value: String): RecipientType {
                return values().find { it.value == value } ?: USER
            }
        }
    }
    
    /**
     * Delivery status enumeration
     */
    enum class Status(val value: String, val displayName: String) {
        PENDING("pending", "Pending"),
        SENT("sent", "Sent"),
        DELIVERED("delivered", "Delivered"),
        READ("read", "Read"),
        FAILED("failed", "Failed"),
        BOUNCED("bounced", "Bounced");
        
        companion object {
            fun fromValue(value: String): Status {
                return values().find { it.value == value } ?: PENDING
            }
        }
    }
    
    /**
     * Priority levels enumeration
     */
    enum class Priority(val level: Int, val displayName: String) {
        LOW(1, "Low"),
        NORMAL(2, "Normal"),
        HIGH(3, "High"),
        URGENT(4, "Urgent"),
        EMERGENCY(5, "Emergency");
        
        companion object {
            fun fromLevel(level: Int): Priority {
                return values().find { it.level == level } ?: NORMAL
            }
        }
    }
    
    /**
     * Get notification type enum
     */
    fun getNotificationType(): NotificationType = NotificationType.fromValue(notificationType)
    
    /**
     * Get delivery method enum
     */
    fun getDeliveryMethod(): DeliveryMethod = DeliveryMethod.fromValue(deliveryMethod)
    
    /**
     * Get recipient type enum
     */
    fun getRecipientType(): RecipientType = RecipientType.fromValue(recipientType)
    
    /**
     * Get status enum
     */
    fun getStatus(): Status = Status.fromValue(status)
    
    /**
     * Get priority enum
     */
    fun getPriority(): Priority = Priority.fromLevel(priorityLevel)
    
    /**
     * Check if notification was successfully delivered
     */
    fun isDelivered(): Boolean = status in listOf("delivered", "read")
    
    /**
     * Check if notification failed to send
     */
    fun isFailed(): Boolean = status in listOf("failed", "bounced")
    
    /**
     * Check if notification can be retried
     */
    fun canRetry(): Boolean = isFailed() && retryCount < maxRetries
    
    /**
     * Check if notification is emergency priority
     */
    fun isEmergencyPriority(): Boolean = priorityLevel >= 4
    
    /**
     * Get formatted creation time
     */
    fun getFormattedCreatedAt(): String {
        return try {
            val dateTime = LocalDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME)
            dateTime.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))
        } catch (e: Exception) {
            createdAt
        }
    }
    
    /**
     * Get formatted sent time
     */
    fun getFormattedSentAt(): String? {
        return sentAt?.let {
            try {
                val dateTime = LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
                dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            } catch (e: Exception) {
                it
            }
        }
    }
    
    /**
     * Get delivery status with timing
     */
    fun getDeliveryStatusWithTime(): String {
        return when (status) {
            "delivered" -> "Delivered ${getFormattedSentAt() ?: ""}"
            "read" -> "Read ${readAt?.let { getFormattedTime(it) } ?: ""}"
            "failed" -> "Failed: ${failureReason ?: "Unknown error"}"
            "sent" -> "Sent ${getFormattedSentAt() ?: ""}"
            "pending" -> "Pending"
            else -> status.replaceFirstChar { it.titlecase() }
        }
    }
    
    /**
     * Get cost in dollars if available
     */
    fun getCostInDollars(): Double? = costCents?.let { it / 100.0 }
    
    private fun getFormattedTime(timestamp: String): String {
        return try {
            val dateTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME)
            dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            timestamp
        }
    }
    
    companion object {
        /**
         * Create emergency alert notification
         */
        fun createEmergencyAlert(
            userId: String,
            alertId: String,
            recipientIdentifier: String,
            recipientName: String?,
            deliveryMethod: String,
            recipientType: String,
            message: String,
            title: String? = null
        ): Notification {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            return Notification(
                id = "", // Will be set by database
                userId = userId,
                alertId = alertId,
                notificationType = "emergency_alert",
                deliveryMethod = deliveryMethod,
                recipientType = recipientType,
                recipientIdentifier = recipientIdentifier,
                recipientName = recipientName,
                title = title ?: "Emergency Alert",
                message = message,
                priorityLevel = 5, // Emergency priority
                createdAt = now,
                updatedAt = now
            )
        }
        
        /**
         * Create status update notification
         */
        fun createStatusUpdate(
            userId: String,
            alertId: String,
            recipientIdentifier: String,
            deliveryMethod: String,
            message: String
        ): Notification {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            return Notification(
                id = "",
                userId = userId,
                alertId = alertId,
                notificationType = "status_update",
                deliveryMethod = deliveryMethod,
                recipientType = "emergency_contact",
                recipientIdentifier = recipientIdentifier,
                message = message,
                priorityLevel = 3, // High priority
                createdAt = now,
                updatedAt = now
            )
        }
        
        /**
         * Create system notification
         */
        fun createSystemNotification(
            userId: String,
            message: String,
            priority: Int = 2
        ): Notification {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            return Notification(
                id = "",
                userId = userId,
                notificationType = "system_notification",
                deliveryMethod = "push",
                recipientType = "user",
                recipientIdentifier = userId,
                message = message,
                priorityLevel = priority,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}

/**
 * Device session data model for tracking active app sessions
 * Maps to the 'device_sessions' table in Supabase
 */
@Serializable
data class DeviceSession(
    val id: String,
    val userId: String,
    
    // Device Information
    val deviceId: String,
    val deviceName: String? = null,
    val deviceType: String,
    val deviceModel: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    
    // Session Details
    val sessionToken: String? = null,
    val fcmToken: String? = null,
    val lastActiveAt: String,
    val sessionStartAt: String,
    val sessionEndAt: String? = null,
    val isActive: Boolean = true,
    
    // Location Tracking
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val locationPermissionsGranted: Boolean = false,
    val microphonePermissionsGranted: Boolean = false,
    val notificationPermissionsGranted: Boolean = false,
    
    // App State
    val voiceDetectionActive: Boolean = false,
    val batteryOptimizationDisabled: Boolean = false,
    val backgroundRestrictionsDisabled: Boolean = false,
    
    // Performance Metrics
    val cpuUsagePercent: Double? = null,
    val memoryUsageMb: Int? = null,
    val batteryLevel: Int? = null,
    val networkType: String? = null,
    val signalStrength: Int? = null,
    
    // Audit fields
    val createdAt: String,
    val updatedAt: String
) {
    
    /**
     * Device types enumeration
     */
    enum class DeviceType(val value: String, val displayName: String) {
        ANDROID("android", "Android"),
        IOS("ios", "iOS"),
        WEB("web", "Web Browser");
        
        companion object {
            fun fromValue(value: String): DeviceType {
                return values().find { it.value == value } ?: ANDROID
            }
        }
    }
    
    /**
     * Get device type enum
     */
    fun getDeviceType(): DeviceType = DeviceType.fromValue(deviceType)
    
    /**
     * Check if session has current location
     */
    fun hasCurrentLocation(): Boolean = lastLatitude != null && lastLongitude != null
    
    /**
     * Check if all required permissions are granted
     */
    fun hasAllPermissions(): Boolean = 
        locationPermissionsGranted && 
        microphonePermissionsGranted && 
        notificationPermissionsGranted
    
    /**
     * Get formatted last active time
     */
    fun getFormattedLastActive(): String {
        return try {
            val dateTime = LocalDateTime.parse(lastActiveAt, DateTimeFormatter.ISO_DATE_TIME)
            val now = LocalDateTime.now()
            val duration = java.time.Duration.between(dateTime, now)
            
            when {
                duration.toMinutes() < 1 -> "Just now"
                duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
                duration.toHours() < 24 -> "${duration.toHours()}h ago"
                else -> "${duration.toDays()}d ago"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Get device display name
     */
    fun getDisplayName(): String {
        return deviceName?.takeIf { it.isNotBlank() }
            ?: deviceModel?.takeIf { it.isNotBlank() }
            ?: "${getDeviceType().displayName} Device"
    }
    
    /**
     * Check if device is ready for emergency detection
     */
    fun isEmergencyReady(): Boolean = 
        isActive && 
        hasAllPermissions() && 
        batteryOptimizationDisabled && 
        voiceDetectionActive
    
    companion object {
        /**
         * Create new device session
         */
        fun create(
            userId: String,
            deviceId: String,
            deviceType: String,
            deviceModel: String? = null,
            osVersion: String? = null,
            appVersion: String? = null
        ): DeviceSession {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            return DeviceSession(
                id = "",
                userId = userId,
                deviceId = deviceId,
                deviceType = deviceType,
                deviceModel = deviceModel,
                osVersion = osVersion,
                appVersion = appVersion,
                lastActiveAt = now,
                sessionStartAt = now,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}