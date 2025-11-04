package com.sriox.vasatey.data.repository

import com.sriox.vasatey.data.models.*
import com.sriox.vasatey.data.supabase.SupabaseClientConfig
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Repository result sealed class for handling database operation results
 */
sealed class RepositoryResult<T> {
    data class Success<T>(val data: T) : RepositoryResult<T>()
    data class Error<T>(val message: String, val throwable: Throwable? = null) : RepositoryResult<T>()
    class Loading<T> : RepositoryResult<T>()
}

/**
 * User repository for managing user data operations
 */
class UserRepository(
    private val supabaseClient: SupabaseClientConfig
) {
    
    private val db = supabaseClient.getDatabase()
    
    /**
     * Create or update user
     */
    suspend fun upsertUser(user: User): RepositoryResult<User> {
        return try {
            val result = db.from("users")
                .upsert(user)
                .decodeSingle<User>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to save user",
                throwable = e
            )
        }
    }
    
    /**
     * Get user by ID
     */
    suspend fun getUserById(userId: String): RepositoryResult<User?> {
        return try {
            val result = db.from("users")
                .select()
                .eq("id", userId)
                .maybeSingle<User>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to get user",
                throwable = e
            )
        }
    }
    
    /**
     * Get user by email
     */
    suspend fun getUserByEmail(email: String): RepositoryResult<User?> {
        return try {
            val result = db.from("users")
                .select()
                .eq("email", email)
                .maybeSingle<User>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to get user by email",
                throwable = e
            )
        }
    }
    
    /**
     * Update user last seen
     */
    suspend fun updateUserLastSeen(userId: String): RepositoryResult<Unit> {
        return try {
            db.from("users")
                .update({
                    set("last_seen_at", java.time.LocalDateTime.now().toString())
                })
                .eq("id", userId)
            
            RepositoryResult.Success(Unit)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to update last seen",
                throwable = e
            )
        }
    }
    
    /**
     * Delete user
     */
    suspend fun deleteUser(userId: String): RepositoryResult<Unit> {
        return try {
            db.from("users")
                .delete()
                .eq("id", userId)
            
            RepositoryResult.Success(Unit)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to delete user",
                throwable = e
            )
        }
    }
}

/**
 * User profile repository for managing user profile data
 */
class UserProfileRepository(
    private val supabaseClient: SupabaseClientConfig
) {
    
    private val db = supabaseClient.getDatabase()
    
    /**
     * Create or update user profile
     */
    suspend fun upsertUserProfile(profile: UserProfile): RepositoryResult<UserProfile> {
        return try {
            val result = db.from("user_profiles")
                .upsert(profile)
                .decodeSingle<UserProfile>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to save user profile",
                throwable = e
            )
        }
    }
    
    /**
     * Get user profile by user ID
     */
    suspend fun getUserProfile(userId: String): RepositoryResult<UserProfile?> {
        return try {
            val result = db.from("user_profiles")
                .select()
                .eq("user_id", userId)
                .maybeSingle<UserProfile>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to get user profile",
                throwable = e
            )
        }
    }
    
    /**
     * Update emergency contacts
     */
    suspend fun updateEmergencyContacts(
        userId: String,
        contacts: List<EmergencyContact>
    ): RepositoryResult<UserProfile> {
        return try {
            val result = db.from("user_profiles")
                .update({
                    set("emergency_contacts", contacts)
                })
                .eq("user_id", userId)
                .decodeSingle<UserProfile>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to update emergency contacts",
                throwable = e
            )
        }
    }
    
    /**
     * Update privacy settings
     */
    suspend fun updatePrivacySettings(
        userId: String,
        settings: PrivacySettings
    ): RepositoryResult<UserProfile> {
        return try {
            val result = db.from("user_profiles")
                .update({
                    set("privacy_settings", settings)
                })
                .eq("user_id", userId)
                .decodeSingle<UserProfile>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to update privacy settings",
                throwable = e
            )
        }
    }
}

/**
 * Emergency alert repository for managing emergency alerts
 */
class EmergencyAlertRepository(
    private val supabaseClient: SupabaseClientConfig
) {
    
    private val db = supabaseClient.getDatabase()
    
    /**
     * Create emergency alert
     */
    suspend fun createEmergencyAlert(alert: EmergencyAlert): RepositoryResult<EmergencyAlert> {
        return try {
            val result = db.from("emergency_alerts")
                .insert(alert)
                .decodeSingle<EmergencyAlert>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to create emergency alert",
                throwable = e
            )
        }
    }
    
    /**
     * Get emergency alert by ID
     */
    suspend fun getEmergencyAlert(alertId: String): RepositoryResult<EmergencyAlert?> {
        return try {
            val result = db.from("emergency_alerts")
                .select()
                .eq("id", alertId)
                .maybeSingle<EmergencyAlert>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to get emergency alert",
                throwable = e
            )
        }
    }
    
    /**
     * Get user's emergency alerts
     */
    suspend fun getUserEmergencyAlerts(
        userId: String,
        limit: Int = 50,
        offset: Int = 0
    ): RepositoryResult<List<EmergencyAlert>> {
        return try {
            val result = db.from("emergency_alerts")
                .select()
                .eq("user_id", userId)
                .order("created_at", ascending = false)
                .limit(limit.toLong())
                .range(offset.toLong(), (offset + limit - 1).toLong())
                .decodeList<EmergencyAlert>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to get user emergency alerts",
                throwable = e
            )
        }
    }
    
    /**
     * Update emergency alert status
     */
    suspend fun updateAlertStatus(
        alertId: String,
        status: String,
        resolvedBy: String? = null
    ): RepositoryResult<EmergencyAlert> {
        return try {
            val updateMap = mutableMapOf<String, Any>(
                "status" to status,
                "updated_at" to java.time.LocalDateTime.now().toString()
            )
            
            if (status == "resolved" && resolvedBy != null) {
                updateMap["resolved_at"] = java.time.LocalDateTime.now().toString()
                updateMap["resolved_by"] = resolvedBy
            }
            
            val result = db.from("emergency_alerts")
                .update(updateMap)
                .eq("id", alertId)
                .decodeSingle<EmergencyAlert>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to update alert status",
                throwable = e
            )
        }
    }
    
    /**
     * Get active emergency alerts
     */
    suspend fun getActiveAlerts(userId: String): RepositoryResult<List<EmergencyAlert>> {
        return try {
            val result = db.from("emergency_alerts")
                .select()
                .eq("user_id", userId)
                .`in`("status", listOf("active", "acknowledged"))
                .order("created_at", ascending = false)
                .decodeList<EmergencyAlert>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to get active alerts",
                throwable = e
            )
        }
    }
    
    /**
     * Create alert response
     */
    suspend fun createAlertResponse(response: AlertResponse): RepositoryResult<AlertResponse> {
        return try {
            val result = db.from("alert_responses")
                .insert(response)
                .decodeSingle<AlertResponse>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to create alert response",
                throwable = e
            )
        }
    }
    
    /**
     * Get alert responses
     */
    suspend fun getAlertResponses(alertId: String): RepositoryResult<List<AlertResponse>> {
        return try {
            val result = db.from("alert_responses")
                .select()
                .eq("alert_id", alertId)
                .order("responded_at", ascending = true)
                .decodeList<AlertResponse>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to get alert responses",
                throwable = e
            )
        }
    }
}

/**
 * Notification repository for managing notifications
 */
class NotificationRepository(
    private val supabaseClient: SupabaseClientConfig
) {
    
    private val db = supabaseClient.getDatabase()
    
    /**
     * Create notification
     */
    suspend fun createNotification(notification: Notification): RepositoryResult<Notification> {
        return try {
            val result = db.from("notifications")
                .insert(notification)
                .decodeSingle<Notification>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to create notification",
                throwable = e
            )
        }
    }
    
    /**
     * Get user notifications
     */
    suspend fun getUserNotifications(
        userId: String,
        limit: Int = 50,
        offset: Int = 0
    ): RepositoryResult<List<Notification>> {
        return try {
            val result = db.from("notifications")
                .select()
                .eq("user_id", userId)
                .order("created_at", ascending = false)
                .limit(limit.toLong())
                .range(offset.toLong(), (offset + limit - 1).toLong())
                .decodeList<Notification>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to get user notifications",
                throwable = e
            )
        }
    }
    
    /**
     * Mark notification as read
     */
    suspend fun markNotificationAsRead(notificationId: String): RepositoryResult<Notification> {
        return try {
            val result = db.from("notifications")
                .update({
                    set("is_read", true)
                    set("read_at", java.time.LocalDateTime.now().toString())
                })
                .eq("id", notificationId)
                .decodeSingle<Notification>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to mark notification as read",
                throwable = e
            )
        }
    }
    
    /**
     * Get unread notification count
     */
    suspend fun getUnreadNotificationCount(userId: String): RepositoryResult<Int> {
        return try {
            val result = db.from("notifications")
                .select(Columns.list("id"))
                .eq("user_id", userId)
                .eq("is_read", false)
                .decodeList<Map<String, Any>>()
            
            RepositoryResult.Success(result.size)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to get unread notification count",
                throwable = e
            )
        }
    }
}

/**
 * Device session repository for managing device sessions
 */
class DeviceSessionRepository(
    private val supabaseClient: SupabaseClientConfig
) {
    
    private val db = supabaseClient.getDatabase()
    
    /**
     * Create device session
     */
    suspend fun createDeviceSession(session: DeviceSession): RepositoryResult<DeviceSession> {
        return try {
            val result = db.from("device_sessions")
                .insert(session)
                .decodeSingle<DeviceSession>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to create device session",
                throwable = e
            )
        }
    }
    
    /**
     * Get active device session
     */
    suspend fun getActiveDeviceSession(userId: String): RepositoryResult<DeviceSession?> {
        return try {
            val result = db.from("device_sessions")
                .select()
                .eq("user_id", userId)
                .eq("is_active", true)
                .order("created_at", ascending = false)
                .limit(1)
                .maybeSingle<DeviceSession>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to get active device session",
                throwable = e
            )
        }
    }
    
    /**
     * Update device session
     */
    suspend fun updateDeviceSession(session: DeviceSession): RepositoryResult<DeviceSession> {
        return try {
            val result = db.from("device_sessions")
                .update(session)
                .eq("id", session.id)
                .decodeSingle<DeviceSession>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to update device session",
                throwable = e
            )
        }
    }
    
    /**
     * End device session
     */
    suspend fun endDeviceSession(sessionId: String): RepositoryResult<DeviceSession> {
        return try {
            val result = db.from("device_sessions")
                .update({
                    set("is_active", false)
                    set("ended_at", java.time.LocalDateTime.now().toString())
                })
                .eq("id", sessionId)
                .decodeSingle<DeviceSession>()
            
            RepositoryResult.Success(result)
        } catch (e: Exception) {
            RepositoryResult.Error(
                message = e.message ?: "Failed to end device session",
                throwable = e
            )
        }
    }
}