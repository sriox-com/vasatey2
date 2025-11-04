package com.sriox.vasatey.services.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sriox.vasatey.R
import com.sriox.vasatey.data.models.Notification
import com.sriox.vasatey.data.repository.NotificationRepository
import com.sriox.vasatey.data.supabase.SupabaseClientConfig
import com.sriox.vasatey.ui.activities.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Firebase Cloud Messaging service for handling push notifications
 */
class VasateyFirebaseMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val CHANNEL_ID_EMERGENCY = "emergency_alerts"
        private const val CHANNEL_ID_GENERAL = "general_notifications"
        private const val CHANNEL_ID_SYSTEM = "system_notifications"
        
        private const val NOTIFICATION_ID_EMERGENCY = 1000
        private const val NOTIFICATION_ID_GENERAL = 2000
        private const val NOTIFICATION_ID_SYSTEM = 3000
    }
    
    private lateinit var notificationRepository: NotificationRepository
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        initializeRepository()
        createNotificationChannels()
    }
    
    /**
     * Initialize notification repository
     */
    private fun initializeRepository() {
        val supabaseClient = SupabaseClientConfig(this)
        notificationRepository = NotificationRepository(supabaseClient)
    }
    
    /**
     * Called when a new FCM token is generated
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        
        // Log token for debugging
        android.util.Log.d("FCM", "New token: $token")
        
        // Send token to server
        serviceScope.launch {
            sendTokenToServer(token)
        }
    }
    
    /**
     * Called when a message is received
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Log message
        android.util.Log.d("FCM", "From: ${remoteMessage.from}")
        
        // Handle data payload
        remoteMessage.data.let { data ->
            if (data.isNotEmpty()) {
                handleDataMessage(data)
            }
        }
        
        // Handle notification payload
        remoteMessage.notification?.let { notification ->
            handleNotificationMessage(notification, remoteMessage.data)
        }
    }
    
    /**
     * Handle data messages
     */
    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"] ?: "general"
        val title = data["title"] ?: "Vasatey Notification"
        val body = data["body"] ?: ""
        val alertId = data["alert_id"]
        val userId = data["user_id"]
        val priority = data["priority"] ?: "normal"
        
        when (type) {
            "emergency_alert" -> {
                handleEmergencyAlert(title, body, alertId, userId, data)
            }
            "alert_response" -> {
                handleAlertResponse(title, body, alertId, userId, data)
            }
            "system_update" -> {
                handleSystemNotification(title, body, data)
            }
            else -> {
                handleGeneralNotification(title, body, data)
            }
        }
        
        // Save notification to database
        if (userId != null) {
            saveNotificationToDatabase(type, title, body, userId, data)
        }
    }
    
    /**
     * Handle notification messages
     */
    private fun handleNotificationMessage(
        notification: RemoteMessage.Notification,
        data: Map<String, String>
    ) {
        val title = notification.title ?: "Vasatey"
        val body = notification.body ?: ""
        val type = data["type"] ?: "general"
        
        when (type) {
            "emergency_alert" -> {
                showEmergencyNotification(title, body, data)
            }
            else -> {
                showGeneralNotification(title, body, data)
            }
        }
    }
    
    /**
     * Handle emergency alert notifications
     */
    private fun handleEmergencyAlert(
        title: String,
        body: String,
        alertId: String?,
        userId: String?,
        data: Map<String, String>
    ) {
        // Show high-priority emergency notification
        showEmergencyNotification(title, body, data)
        
        // Trigger emergency response actions
        val intent = Intent("com.sriox.vasatey.EMERGENCY_ALERT_RECEIVED").apply {
            putExtra("alert_id", alertId)
            putExtra("user_id", userId)
            putExtra("title", title)
            putExtra("body", body)
        }
        sendBroadcast(intent)
    }
    
    /**
     * Handle alert response notifications
     */
    private fun handleAlertResponse(
        title: String,
        body: String,
        alertId: String?,
        userId: String?,
        data: Map<String, String>
    ) {
        showGeneralNotification(title, body, data)
        
        // Broadcast alert response
        val intent = Intent("com.sriox.vasatey.ALERT_RESPONSE_RECEIVED").apply {
            putExtra("alert_id", alertId)
            putExtra("user_id", userId)
            putExtra("response_type", data["response_type"])
        }
        sendBroadcast(intent)
    }
    
    /**
     * Handle system notifications
     */
    private fun handleSystemNotification(
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        showSystemNotification(title, body, data)
    }
    
    /**
     * Handle general notifications
     */
    private fun handleGeneralNotification(
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        showGeneralNotification(title, body, data)
    }
    
    /**
     * Show emergency notification with high priority
     */
    private fun showEmergencyNotification(
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("notification_type", "emergency")
            putExtra("alert_id", data["alert_id"])
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_EMERGENCY)
            .setSmallIcon(R.drawable.ic_emergency)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true) // Show full screen for emergency
            .setColor(resources.getColor(R.color.emergency_red, null))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_EMERGENCY, notification)
    }
    
    /**
     * Show general notification
     */
    private fun showGeneralNotification(
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("notification_type", "general")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_GENERAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_GENERAL, notification)
    }
    
    /**
     * Show system notification
     */
    private fun showSystemNotification(
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("notification_type", "system")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SYSTEM)
            .setSmallIcon(R.drawable.ic_system)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_SYSTEM, notification)
    }
    
    /**
     * Create notification channels for different types of notifications
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Emergency alerts channel
            val emergencyChannel = NotificationChannel(
                CHANNEL_ID_EMERGENCY,
                "Emergency Alerts",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Critical emergency notifications"
                enableLights(true)
                enableVibration(true)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            
            // General notifications channel
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
                enableLights(true)
                enableVibration(true)
            }
            
            // System notifications channel
            val systemChannel = NotificationChannel(
                CHANNEL_ID_SYSTEM,
                "System Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "System and maintenance notifications"
                enableLights(false)
                enableVibration(false)
            }
            
            notificationManager.createNotificationChannels(
                listOf(emergencyChannel, generalChannel, systemChannel)
            )
        }
    }
    
    /**
     * Send FCM token to server
     */
    private suspend fun sendTokenToServer(token: String) {
        try {
            // TODO: Implement API call to save token to user profile
            android.util.Log.d("FCM", "Token sent to server: $token")
        } catch (e: Exception) {
            android.util.Log.e("FCM", "Failed to send token to server", e)
        }
    }
    
    /**
     * Save notification to database
     */
    private fun saveNotificationToDatabase(
        type: String,
        title: String,
        body: String,
        userId: String,
        data: Map<String, String>
    ) {
        serviceScope.launch {
            try {
                val notification = Notification.create(
                    userId = userId,
                    type = type,
                    title = title,
                    message = body,
                    data = data
                )
                
                notificationRepository.createNotification(notification)
            } catch (e: Exception) {
                android.util.Log.e("FCM", "Failed to save notification to database", e)
            }
        }
    }
}

/**
 * FCM token manager for handling device tokens
 */
class FCMTokenManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "fcm_token_prefs"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_TOKEN_TIMESTAMP = "token_timestamp"
    }
    
    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Get current FCM token
     */
    suspend fun getCurrentToken(): String? {
        return try {
            var token: String? = null
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    token = task.result
                    saveTokenLocally(token)
                }
            }
            token
        } catch (e: Exception) {
            android.util.Log.e("FCMTokenManager", "Failed to get FCM token", e)
            null
        }
    }
    
    /**
     * Get cached token from local storage
     */
    fun getCachedToken(): String? {
        return sharedPrefs.getString(KEY_FCM_TOKEN, null)
    }
    
    /**
     * Save token locally
     */
    private fun saveTokenLocally(token: String?) {
        token?.let {
            sharedPrefs.edit()
                .putString(KEY_FCM_TOKEN, it)
                .putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis())
                .apply()
        }
    }
    
    /**
     * Check if token needs refresh (older than 24 hours)
     */
    fun shouldRefreshToken(): Boolean {
        val timestamp = sharedPrefs.getLong(KEY_TOKEN_TIMESTAMP, 0)
        val age = System.currentTimeMillis() - timestamp
        return age > 24 * 60 * 60 * 1000 // 24 hours
    }
    
    /**
     * Subscribe to topic
     */
    fun subscribeToTopic(topic: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnCompleteListener { task ->
                val message = if (task.isSuccessful) {
                    "Subscribed to topic: $topic"
                } else {
                    "Failed to subscribe to topic: $topic"
                }
                android.util.Log.d("FCMTokenManager", message)
            }
    }
    
    /**
     * Unsubscribe from topic
     */
    fun unsubscribeFromTopic(topic: String) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            .addOnCompleteListener { task ->
                val message = if (task.isSuccessful) {
                    "Unsubscribed from topic: $topic"
                } else {
                    "Failed to unsubscribe from topic: $topic"
                }
                android.util.Log.d("FCMTokenManager", message)
            }
    }
}

/**
 * Notification helper for creating and managing local notifications
 */
class NotificationHelper(private val context: Context) {
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    /**
     * Show emergency countdown notification
     */
    fun showEmergencyCountdown(
        title: String,
        message: String,
        secondsRemaining: Int,
        alertId: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("alert_id", alertId)
            putExtra("action", "view_emergency")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val cancelIntent = Intent("com.sriox.vasatey.CANCEL_EMERGENCY").apply {
            putExtra("alert_id", alertId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, VasateyFirebaseMessagingService.CHANNEL_ID_EMERGENCY)
            .setSmallIcon(R.drawable.ic_emergency)
            .setContentTitle(title)
            .setContentText("$message ($secondsRemaining seconds)")
            .setProgress(30, 30 - secondsRemaining, false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_cancel, "Cancel", cancelPendingIntent)
            .setColor(context.resources.getColor(R.color.emergency_red, null))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        
        notificationManager.notify(VasateyFirebaseMessagingService.NOTIFICATION_ID_EMERGENCY, notification)
    }
    
    /**
     * Cancel emergency notification
     */
    fun cancelEmergencyNotification() {
        notificationManager.cancel(VasateyFirebaseMessagingService.NOTIFICATION_ID_EMERGENCY)
    }
    
    /**
     * Show listening status notification
     */
    fun showListeningStatusNotification(isListening: Boolean) {
        val title = if (isListening) "Vasatey is listening" else "Vasatey is paused"
        val message = if (isListening) "Monitoring for emergency phrases" else "Voice monitoring paused"
        val icon = if (isListening) R.drawable.ic_mic_on else R.drawable.ic_mic_off
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, VasateyFirebaseMessagingService.CHANNEL_ID_SYSTEM)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isListening)
            .setContentIntent(pendingIntent)
            .build()
        
        notificationManager.notify(VasateyFirebaseMessagingService.NOTIFICATION_ID_SYSTEM + 1, notification)
    }
}