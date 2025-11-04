package com.sriox.vasatey.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sriox.vasatey.R
import com.sriox.vasatey.ui.main.MainActivity
import com.sriox.vasatey.ui.alert.AlertDetailsActivity
import com.sriox.vasatey.data.remote.SupabaseDatabaseHelper
import com.sriox.vasatey.data.models.Notification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Firebase Cloud Messaging service for handling push notifications
 * Manages FCM token updates and incoming notification messages
 */
class FCMNotificationService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "FCMNotificationService"
        
        // Notification channels
        const val EMERGENCY_CHANNEL_ID = "emergency_alerts"
        const val GENERAL_CHANNEL_ID = "general_notifications"
        const val SYSTEM_CHANNEL_ID = "system_notifications"
        
        // Notification request codes
        private const val EMERGENCY_REQUEST_CODE = 1001
        private const val GENERAL_REQUEST_CODE = 1002
        private const val SYSTEM_REQUEST_CODE = 1003
        
        // Intent extras
        const val EXTRA_NOTIFICATION_TYPE = "notification_type"
        const val EXTRA_ALERT_ID = "alert_id"
        const val EXTRA_USER_ID = "user_id"
    }
    
    private val databaseHelper = SupabaseDatabaseHelper.getInstance()
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        Log.d(TAG, "FCM Service created")
    }
    
    /**
     * Called when a new FCM token is generated
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received: $token")
        
        // Save token to SharedPreferences for later use
        saveTokenToPreferences(token)
        
        // Update token in database if user is authenticated
        serviceScope.launch {
            try {
                updateTokenInDatabase(token)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update FCM token in database", e)
            }
        }
    }
    
    /**
     * Called when a message is received
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "Message received from: ${remoteMessage.from}")
        Log.d(TAG, "Message data: ${remoteMessage.data}")
        
        try {
            // Extract notification data
            val notificationType = remoteMessage.data["type"] ?: "general"
            val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Vasatey"
            val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: ""
            val alertId = remoteMessage.data["alert_id"]
            val userId = remoteMessage.data["user_id"]
            
            // Log notification to database
            serviceScope.launch {
                logNotificationToDatabase(notificationType, title, body, alertId, userId)
            }
            
            // Show notification based on type
            when (notificationType) {
                "emergency" -> showEmergencyNotification(title, body, alertId)
                "system" -> showSystemNotification(title, body)
                else -> showGeneralNotification(title, body)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing FCM message", e)
            
            // Fallback: show basic notification
            showGeneralNotification(
                remoteMessage.notification?.title ?: "Vasatey",
                remoteMessage.notification?.body ?: "You have a new notification"
            )
        }
    }
    
    /**
     * Create notification channels for different types of notifications
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Emergency alerts channel
            val emergencyChannel = NotificationChannel(
                EMERGENCY_CHANNEL_ID,
                "Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical emergency alerts and notifications"
                enableLights(true)
                enableVibration(true)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            
            // General notifications channel
            val generalChannel = NotificationChannel(
                GENERAL_CHANNEL_ID,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications and updates"
                enableLights(true)
                enableVibration(true)
            }
            
            // System notifications channel
            val systemChannel = NotificationChannel(
                SYSTEM_CHANNEL_ID,
                "System Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "System status and maintenance notifications"
                enableLights(false)
                enableVibration(false)
            }
            
            notificationManager.createNotificationChannels(
                listOf(emergencyChannel, generalChannel, systemChannel)
            )
            
            Log.d(TAG, "Notification channels created")
        }
    }
    
    /**
     * Show emergency notification with high priority
     */
    private fun showEmergencyNotification(title: String, body: String, alertId: String?) {
        Log.d(TAG, "Showing emergency notification: $title")
        
        val intent = if (alertId != null) {
            Intent(this, AlertDetailsActivity::class.java).apply {
                putExtra(EXTRA_ALERT_ID, alertId)
                putExtra(EXTRA_NOTIFICATION_TYPE, "emergency")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_TYPE, "emergency")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            EMERGENCY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, EMERGENCY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_emergency_24)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(resources.getColor(R.color.emergency_red, null))
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(EMERGENCY_REQUEST_CODE, notification)
    }
    
    /**
     * Show general notification
     */
    private fun showGeneralNotification(title: String, body: String) {
        Log.d(TAG, "Showing general notification: $title")
        
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_TYPE, "general")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            GENERAL_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, GENERAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(GENERAL_REQUEST_CODE, notification)
    }
    
    /**
     * Show system notification
     */
    private fun showSystemNotification(title: String, body: String) {
        Log.d(TAG, "Showing system notification: $title")
        
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_TYPE, "system")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            SYSTEM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, SYSTEM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_system_24)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SYSTEM_REQUEST_CODE, notification)
    }
    
    /**
     * Save FCM token to SharedPreferences
     */
    private fun saveTokenToPreferences(token: String) {
        val sharedPref = getSharedPreferences("vasatey_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("fcm_token", token)
            putLong("fcm_token_timestamp", System.currentTimeMillis())
            apply()
        }
        Log.d(TAG, "FCM token saved to preferences")
    }
    
    /**
     * Update FCM token in database
     */
    private suspend fun updateTokenInDatabase(token: String) {
        try {
            // Note: This would typically require user authentication
            // For now, we'll just log the token update
            Log.d(TAG, "FCM token ready for database update: $token")
            
            // TODO: Update user's FCM token in user_profiles table
            // This should be called when user is authenticated
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update FCM token in database", e)
        }
    }
    
    /**
     * Log notification to database
     */
    private suspend fun logNotificationToDatabase(
        type: String,
        title: String,
        body: String,
        alertId: String?,
        userId: String?
    ) {
        try {
            if (userId == null) {
                Log.w(TAG, "Cannot log notification without user ID")
                return
            }
            
            val notification = Notification(
                id = "",
                userId = userId,
                alertId = alertId,
                type = type,
                title = title,
                message = body,
                priority = if (type == "emergency") "high" else "normal",
                read = false,
                delivered = true,
                deliveredAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            )
            
            val result = databaseHelper.createNotification(notification)
            if (result.isSuccess) {
                Log.d(TAG, "Notification logged to database successfully")
            } else {
                Log.e(TAG, "Failed to log notification to database", result.exceptionOrNull())
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging notification to database", e)
        }
    }
}

/**
 * FCM token manager for handling token operations
 */
class FCMTokenManager private constructor() {
    
    companion object {
        private const val TAG = "FCMTokenManager"
        
        @Volatile
        private var INSTANCE: FCMTokenManager? = null
        
        fun getInstance(): FCMTokenManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FCMTokenManager().also { INSTANCE = it }
            }
        }
    }
    
    private val databaseHelper = SupabaseDatabaseHelper.getInstance()
    
    /**
     * Get current FCM token
     */
    suspend fun getCurrentToken(context: Context): Result<String> {
        return try {
            val task = com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            val token = kotlinx.coroutines.tasks.await(task)
            
            Log.d(TAG, "FCM token retrieved: $token")
            
            // Save to preferences
            saveTokenToPreferences(context, token)
            
            Result.success(token)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FCM token", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update user's FCM token in database
     */
    suspend fun updateUserToken(context: Context, userId: String): Result<String> {
        return try {
            val tokenResult = getCurrentToken(context)
            if (tokenResult.isFailure) {
                return Result.failure(tokenResult.exceptionOrNull() ?: Exception("Failed to get token"))
            }
            
            val token = tokenResult.getOrThrow()
            
            // Update user profile with new FCM token
            val updateResult = databaseHelper.updateUserProfile(
                userId = userId,
                updates = mapOf(
                    "fcm_token" to token,
                    "fcm_token_updated_at" to LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                )
            )
            
            if (updateResult.isSuccess) {
                Log.i(TAG, "FCM token updated in database for user: $userId")
                Result.success(token)
            } else {
                Log.e(TAG, "Failed to update FCM token in database", updateResult.exceptionOrNull())
                Result.failure(updateResult.exceptionOrNull() ?: Exception("Database update failed"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user FCM token", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get cached FCM token from preferences
     */
    fun getCachedToken(context: Context): String? {
        val sharedPref = context.getSharedPreferences("vasatey_prefs", Context.MODE_PRIVATE)
        return sharedPref.getString("fcm_token", null)
    }
    
    /**
     * Check if FCM token needs refresh (older than 24 hours)
     */
    fun shouldRefreshToken(context: Context): Boolean {
        val sharedPref = context.getSharedPreferences("vasatey_prefs", Context.MODE_PRIVATE)
        val timestamp = sharedPref.getLong("fcm_token_timestamp", 0)
        val twentyFourHours = 24 * 60 * 60 * 1000 // 24 hours in milliseconds
        
        return (System.currentTimeMillis() - timestamp) > twentyFourHours
    }
    
    /**
     * Save FCM token to SharedPreferences
     */
    private fun saveTokenToPreferences(context: Context, token: String) {
        val sharedPref = context.getSharedPreferences("vasatey_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("fcm_token", token)
            putLong("fcm_token_timestamp", System.currentTimeMillis())
            apply()
        }
        Log.d(TAG, "FCM token saved to preferences")
    }
    
    /**
     * Delete FCM token (for logout)
     */
    suspend fun deleteToken(context: Context): Result<Unit> {
        return try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken()
            
            // Clear from preferences
            val sharedPref = context.getSharedPreferences("vasatey_prefs", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                remove("fcm_token")
                remove("fcm_token_timestamp")
                apply()
            }
            
            Log.d(TAG, "FCM token deleted successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete FCM token", e)
            Result.failure(e)
        }
    }
}