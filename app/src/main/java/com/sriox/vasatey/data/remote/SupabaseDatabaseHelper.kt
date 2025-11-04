package com.sriox.vasatey.data.remote

import android.util.Log
import com.sriox.vasatey.data.models.*
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Database operations helper for Supabase
 * Provides all CRUD operations for the application data
 */
class SupabaseDatabaseHelper private constructor() {
    
    companion object {
        private const val TAG = "SupabaseDatabaseHelper"
        
        @Volatile
        private var INSTANCE: SupabaseDatabaseHelper? = null
        
        fun getInstance(): SupabaseDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SupabaseDatabaseHelper().also { INSTANCE = it }
            }
        }
    }
    
    private val supabaseClient = SupabaseClientManager.getInstance()
    
    // ===== USER OPERATIONS =====
    
    /**
     * Create or update user profile
     */
    suspend fun upsertUserProfile(profile: UserProfile): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Upserting user profile: ${profile.id}")
            
            val updatedProfile = profile.copy(
                updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            )
            
            val result = supabaseClient.database
                .from("user_profiles")
                .upsert(updatedProfile)
                .decodeSingle<UserProfile>()
            
            Log.i(TAG, "User profile upserted successfully: ${result.id}")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upsert user profile", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get user profile by ID
     */
    suspend fun getUserProfile(userId: String): Result<UserProfile?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting user profile: $userId")
            
            val result = supabaseClient.database
                .from("user_profiles")
                .select()
                .eq("id", userId)
                .decodeSingleOrNull<UserProfile>()
            
            Log.i(TAG, "User profile retrieved: ${result?.id}")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user profile", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update user profile
     */
    suspend fun updateUserProfile(userId: String, updates: Map<String, Any>): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating user profile: $userId")
            
            val updatedData = updates.toMutableMap()
            updatedData["updated_at"] = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            
            val result = supabaseClient.database
                .from("user_profiles")
                .update(updatedData)
                .eq("id", userId)
                .decodeSingle<UserProfile>()
            
            Log.i(TAG, "User profile updated successfully: ${result.id}")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update user profile", e)
            Result.failure(e)
        }
    }
    
    // ===== EMERGENCY ALERT OPERATIONS =====
    
    /**
     * Create emergency alert
     */
    suspend fun createEmergencyAlert(alert: EmergencyAlert): Result<EmergencyAlert> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating emergency alert for user: ${alert.userId}")
            
            val result = supabaseClient.database
                .from("emergency_alerts")
                .insert(alert)
                .decodeSingle<EmergencyAlert>()
            
            Log.i(TAG, "Emergency alert created successfully: ${result.id}")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create emergency alert", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get emergency alerts for user
     */
    suspend fun getUserEmergencyAlerts(
        userId: String,
        limit: Int = 50,
        offset: Int = 0
    ): Result<List<EmergencyAlert>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting emergency alerts for user: $userId")
            
            val result = supabaseClient.database
                .from("emergency_alerts")
                .select()
                .eq("user_id", userId)
                .order("created_at", ascending = false)
                .limit(limit.toLong())
                .range(offset.toLong(), (offset + limit - 1).toLong())
                .decodeList<EmergencyAlert>()
            
            Log.i(TAG, "Retrieved ${result.size} emergency alerts for user: $userId")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user emergency alerts", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get emergency alert by ID
     */
    suspend fun getEmergencyAlert(alertId: String): Result<EmergencyAlert?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting emergency alert: $alertId")
            
            val result = supabaseClient.database
                .from("emergency_alerts")
                .select()
                .eq("id", alertId)
                .decodeSingleOrNull<EmergencyAlert>()
            
            Log.i(TAG, "Emergency alert retrieved: ${result?.id}")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get emergency alert", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update emergency alert status
     */
    suspend fun updateEmergencyAlertStatus(
        alertId: String,
        status: String,
        resolvedAt: String? = null,
        resolution: String? = null
    ): Result<EmergencyAlert> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating emergency alert status: $alertId -> $status")
            
            val updates = mutableMapOf<String, Any>(
                "status" to status,
                "updated_at" to LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            )
            
            if (resolvedAt != null) updates["resolved_at"] = resolvedAt
            if (resolution != null) updates["resolution"] = resolution
            
            val result = supabaseClient.database
                .from("emergency_alerts")
                .update(updates)
                .eq("id", alertId)
                .decodeSingle<EmergencyAlert>()
            
            Log.i(TAG, "Emergency alert status updated successfully: ${result.id}")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update emergency alert status", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get active emergency alerts for user
     */
    suspend fun getActiveEmergencyAlerts(userId: String): Result<List<EmergencyAlert>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting active emergency alerts for user: $userId")
            
            val result = supabaseClient.database
                .from("emergency_alerts")
                .select()
                .eq("user_id", userId)
                .eq("status", "active")
                .order("created_at", ascending = false)
                .decodeList<EmergencyAlert>()
            
            Log.i(TAG, "Retrieved ${result.size} active emergency alerts for user: $userId")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get active emergency alerts", e)
            Result.failure(e)
        }
    }
    
    // ===== ALERT RESPONSE OPERATIONS =====
    
    /**
     * Create alert response
     */
    suspend fun createAlertResponse(response: AlertResponse): Result<AlertResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating alert response for alert: ${response.alertId}")
            
            val result = supabaseClient.database
                .from("alert_responses")
                .insert(response)
                .decodeSingle<AlertResponse>()
            
            Log.i(TAG, "Alert response created successfully: ${result.id}")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create alert response", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get alert responses for alert
     */
    suspend fun getAlertResponses(alertId: String): Result<List<AlertResponse>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting alert responses for alert: $alertId")
            
            val result = supabaseClient.database
                .from("alert_responses")
                .select()
                .eq("alert_id", alertId)
                .order("created_at", ascending = true)
                .decodeList<AlertResponse>()
            
            Log.i(TAG, "Retrieved ${result.size} alert responses for alert: $alertId")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get alert responses", e)
            Result.failure(e)
        }
    }
    
    // ===== NOTIFICATION OPERATIONS =====
    
    /**
     * Create notification
     */
    suspend fun createNotification(notification: Notification): Result<Notification> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating notification for user: ${notification.userId}")
            
            val result = supabaseClient.database
                .from("notifications")
                .insert(notification)
                .decodeSingle<Notification>()
            
            Log.i(TAG, "Notification created successfully: ${result.id}")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create notification", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get notifications for user
     */
    suspend fun getUserNotifications(
        userId: String,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<Notification>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting notifications for user: $userId")
            
            val result = supabaseClient.database
                .from("notifications")
                .select()
                .eq("user_id", userId)
                .order("created_at", ascending = false)
                .limit(limit.toLong())
                .range(offset.toLong(), (offset + limit - 1).toLong())
                .decodeList<Notification>()
            
            Log.i(TAG, "Retrieved ${result.size} notifications for user: $userId")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user notifications", e)
            Result.failure(e)
        }
    }
    
    /**
     * Mark notification as read
     */
    suspend fun markNotificationAsRead(notificationId: String): Result<Notification> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Marking notification as read: $notificationId")
            
            val updates = mapOf(
                "read" to true,
                "read_at" to LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            )
            
            val result = supabaseClient.database
                .from("notifications")
                .update(updates)
                .eq("id", notificationId)
                .decodeSingle<Notification>()
            
            Log.i(TAG, "Notification marked as read: ${result.id}")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark notification as read", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get unread notifications count
     */
    suspend fun getUnreadNotificationsCount(userId: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting unread notifications count for user: $userId")
            
            val result = supabaseClient.database
                .from("notifications")
                .select("id", count = io.github.jan.supabase.postgrest.query.Count.EXACT)
                .eq("user_id", userId)
                .eq("read", false)
                .decodeList<Map<String, Any>>()
            
            val count = result.size
            Log.i(TAG, "Unread notifications count for user $userId: $count")
            Result.success(count)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get unread notifications count", e)
            Result.failure(e)
        }
    }
    
    // ===== DEVICE SESSION OPERATIONS =====
    
    /**
     * Create or update device session
     */
    suspend fun upsertDeviceSession(session: DeviceSession): Result<DeviceSession> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Upserting device session for user: ${session.userId}")
            
            val result = supabaseClient.database
                .from("device_sessions")
                .upsert(session)
                .decodeSingle<DeviceSession>()
            
            Log.i(TAG, "Device session upserted successfully: ${result.id}")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upsert device session", e)
            Result.failure(e)
        }
    }
    
    /**
     * End device session
     */
    suspend fun endDeviceSession(sessionId: String): Result<DeviceSession> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Ending device session: $sessionId")
            
            val updates = mapOf(
                "ended_at" to LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                "is_active" to false
            )
            
            val result = supabaseClient.database
                .from("device_sessions")
                .update(updates)
                .eq("id", sessionId)
                .decodeSingle<DeviceSession>()
            
            Log.i(TAG, "Device session ended successfully: ${result.id}")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end device session", e)
            Result.failure(e)
        }
    }
    
    // ===== VOICE DETECTION LOG OPERATIONS =====
    
    /**
     * Create voice detection log
     */
    suspend fun createVoiceDetectionLog(log: VoiceDetectionLog): Result<VoiceDetectionLog> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating voice detection log for user: ${log.userId}")
            
            val result = supabaseClient.database
                .from("voice_detection_logs")
                .insert(log)
                .decodeSingle<VoiceDetectionLog>()
            
            Log.i(TAG, "Voice detection log created successfully: ${result.id}")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create voice detection log", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get voice detection logs for user
     */
    suspend fun getUserVoiceDetectionLogs(
        userId: String,
        limit: Int = 50,
        offset: Int = 0
    ): Result<List<VoiceDetectionLog>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting voice detection logs for user: $userId")
            
            val result = supabaseClient.database
                .from("voice_detection_logs")
                .select()
                .eq("user_id", userId)
                .order("detected_at", ascending = false)
                .limit(limit.toLong())
                .range(offset.toLong(), (offset + limit - 1).toLong())
                .decodeList<VoiceDetectionLog>()
            
            Log.i(TAG, "Retrieved ${result.size} voice detection logs for user: $userId")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user voice detection logs", e)
            Result.failure(e)
        }
    }
    
    // ===== SYSTEM LOG OPERATIONS =====
    
    /**
     * Create system log
     */
    suspend fun createSystemLog(log: SystemLog): Result<SystemLog> = withContext(Dispatchers.IO) {
        try {
            val result = supabaseClient.database
                .from("system_logs")
                .insert(log)
                .decodeSingle<SystemLog>()
            
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create system log", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get system logs
     */
    suspend fun getSystemLogs(
        userId: String? = null,
        logLevel: String? = null,
        category: String? = null,
        limit: Int = 100,
        offset: Int = 0
    ): Result<List<SystemLog>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting system logs with filters")
            
            var query = supabaseClient.database
                .from("system_logs")
                .select()
            
            if (userId != null) query = query.eq("user_id", userId)
            if (logLevel != null) query = query.eq("log_level", logLevel)
            if (category != null) query = query.eq("category", category)
            
            val result = query
                .order("created_at", ascending = false)
                .limit(limit.toLong())
                .range(offset.toLong(), (offset + limit - 1).toLong())
                .decodeList<SystemLog>()
            
            Log.i(TAG, "Retrieved ${result.size} system logs")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get system logs", e)
            Result.failure(e)
        }
    }
    
    // ===== APP SETTINGS OPERATIONS =====
    
    /**
     * Get app setting by key
     */
    suspend fun getAppSetting(key: String): Result<AppSetting?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting app setting: $key")
            
            val result = supabaseClient.database
                .from("app_settings")
                .select()
                .eq("setting_key", key)
                .decodeSingleOrNull<AppSetting>()
            
            Log.i(TAG, "App setting retrieved: $key")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get app setting", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all app settings
     */
    suspend fun getAllAppSettings(): Result<List<AppSetting>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting all app settings")
            
            val result = supabaseClient.database
                .from("app_settings")
                .select()
                .order("setting_key", ascending = true)
                .decodeList<AppSetting>()
            
            Log.i(TAG, "Retrieved ${result.size} app settings")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all app settings", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update app setting
     */
    suspend fun updateAppSetting(key: String, value: String): Result<AppSetting> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating app setting: $key")
            
            val updates = mapOf(
                "setting_value" to value,
                "updated_at" to LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            )
            
            val result = supabaseClient.database
                .from("app_settings")
                .update(updates)
                .eq("setting_key", key)
                .decodeSingle<AppSetting>()
            
            Log.i(TAG, "App setting updated successfully: $key")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update app setting", e)
            Result.failure(e)
        }
    }
}