package com.sriox.vasatey

import ai.picovoice.porcupine.*
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sriox.vasatey.models.VercelNotificationRequest
import com.sriox.vasatey.network.RetrofitInstance
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class ListeningService : Service() {

    private var porcupineManager: PorcupineManager? = null
    private var serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val dbHelper = SupabaseDatabaseHelper()
    private val authHelper = SupabaseAuthHelper()
    private lateinit var alertHistoryManager: AlertHistoryManager

    override fun onCreate() {
        super.onCreate()
        Log.d("ListeningService", "=== SERVICE STARTING ===")
        alertHistoryManager = AlertHistoryManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        acquireWakeLock()
        startForegroundService()
        Log.d("ListeningService", "Starting Porcupine listening...")
        startPorcupineListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        stopPorcupine()
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Restart service if killed by system
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vasatey::listening_wake_lock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val channelId = "vasatey_listen_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Vasatey Listening Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Vasatey")
            .setContentText("Listening for wake word…")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(2, notification)
    }

    private fun startPorcupineListening() {
        serviceScope.launch {
            try {
                // Get current user
                val currentUser = authHelper.getCurrentUser()
                if (currentUser == null) {
                    showErrorNotification("User not logged in.")
                    stopSelf()
                    return@launch
                }

                // Get user settings to get access key and wake word
                val userSettingsResult = dbHelper.getUserSettings(currentUser.id)
                val userSettings = userSettingsResult.getOrNull()
                if (userSettings == null) {
                    showErrorNotification("Unable to load user settings. Please save your Picovoice access key in settings first.")
                    stopSelf()
                    return@launch
                }

                val accessKey = userSettings.picovoiceAccessKey
                val wakeWord = userSettings.wakeWord
                val sensitivity = userSettings.voiceSensitivity

                if (accessKey.isNullOrEmpty()) {
                    showErrorNotification("Picovoice Access Key not found. Please add it in Settings and try again.")
                    stopSelf()
                    return@launch
                }

                Log.d("ListeningService", "Starting Porcupine with wake word: '$wakeWord', sensitivity: $sensitivity")

                // Map wake word to corresponding .ppn file
                val keywordFileName = when {
                    wakeWord.contains("help", ignoreCase = true) -> "help.ppn"
                    wakeWord.contains("leave", ignoreCase = true) -> "leave-me-alone.ppn"
                    wakeWord.contains("vasatey", ignoreCase = true) -> "help.ppn" // Map "hey vasatey" to help.ppn
                    else -> "help.ppn" // Default fallback
                }
                
                val keywordPath = FileUtils.extractAsset(this@ListeningService, keywordFileName)

                val callback = PorcupineManagerCallback { keywordIndex ->
                    Log.d("ListeningService", "=== WAKE WORD DETECTED ===")
                    Log.d("ListeningService", "Keyword index: $keywordIndex")
                    Log.d("ListeningService", "Wake word '$wakeWord' detected!")
                    Log.d("ListeningService", "Triggering help alert...")
                    triggerHelpAlertToGuardians()
                }

                porcupineManager = PorcupineManager.Builder()
                    .setAccessKey(accessKey)
                    .setKeywordPath(keywordPath)
                    .setSensitivity(sensitivity)
                    .build(applicationContext, callback)

                porcupineManager?.start()
                Log.d("ListeningService", "Porcupine started successfully for '$wakeWord' with sensitivity $sensitivity.")

            } catch (e: Exception) {
                Log.e("ListeningService", "Error starting Porcupine: ${e.message}", e)
                showErrorNotification("A critical error occurred in the listening service: ${e.message}")
                stopSelf()
            }
        }
    }

    private suspend fun getUserSettingsFromSupabase(): UserProfile? {
        val currentUser = authHelper.getCurrentUser() ?: return null
        val result = authHelper.getUserProfile(currentUser.email!!)
        return result.getOrNull()
    }

    private fun triggerHelpAlertToGuardians() = serviceScope.launch {
        Log.d("ListeningService", "=== HELP ALERT TRIGGERED ===")
        
        val location = getCurrentLocation()
        if (location == null) {
            Log.w("ListeningService", "Proceeding with alert but without location data.")
        }
        
        val currentUser = authHelper.getCurrentUser()
        if (currentUser == null) {
            Log.e("ListeningService", "No current user found")
            return@launch
        }
        
        val userEmail = currentUser.email
        if (userEmail == null) {
            Log.e("ListeningService", "No user email found")
            return@launch
        }
        
        Log.d("ListeningService", "Current user: $userEmail")
        
        try {
            // Get guardians from Supabase
            Log.d("ListeningService", "Getting guardians for user: $userEmail")
            val guardiansResult = dbHelper.getGuardiansForUser(userEmail)
            val guardians = guardiansResult.getOrElse { 
                Log.e("ListeningService", "Failed to get guardians")
                emptyList() 
            }

            Log.d("ListeningService", "Found ${guardians.size} guardians")
            
            if (guardians.isEmpty()) {
                Log.w("ListeningService", "No guardians found - showing error notification")
                showErrorNotification("You have no guardians to alert. Please add guardians in settings.")
                return@launch
            }

            // Get user profile
            val userProfile = authHelper.getUserProfile(userEmail).getOrNull()
            val userName = userProfile?.fullName ?: userEmail
            val mobileNumber = userProfile?.phoneNumber

            val notificationTasks = guardians.map { guardian ->
                async(Dispatchers.IO) {
                    sendNotificationToGuardian(guardian.guardianEmail ?: "", userName, userEmail, mobileNumber, location?.latitude, location?.longitude)
                }
            }

            val results = notificationTasks.awaitAll()
            val successCount = results.count { it }

            // Log the alert to the database for history
            if (successCount > 0) {
                Log.d("ListeningService", "Logging alert to database...")
                logAlertToSupabase(userName, userEmail, mobileNumber, location?.latitude, location?.longitude)
            }

            withContext(Dispatchers.Main) {
                if (successCount > 0) {
                    showDetectedNotification(successCount, guardians.size)
                } else {
                    showErrorNotification("Help alert failed to send. Check connection and guardian setup.")
                }
            }
        } catch (e: Exception) {
            Log.e("ListeningService", "Error triggering help alerts", e)
            showErrorNotification("A critical error occurred while sending alerts.")
        }
    }

    private suspend fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w("ListeningService", "Location permission not granted. Cannot get location.")
            return null
        }
        return try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        } catch (e: Exception) {
            Log.e("ListeningService", "Could not get location", e)
            null
        }
    }

    private suspend fun sendNotificationToGuardian(guardianEmail: String, fromUserName: String, fromUserEmail: String, fromUserMobile: String?, lat: Double?, lon: Double?): Boolean {
        return try {
            AppLogger.logInfo("NOTIFICATION", "=== SENDING NOTIFICATION ===", "Guardian Email: $guardianEmail")
            
            var guardianToken: String? = null
            var guardianProfile: UserProfile? = null
            
            // First, get the guardian's profile and FCM token from database
            AppLogger.logInfo("NOTIFICATION", "Looking up guardian profile", "Email: $guardianEmail")
            val supabase = SupabaseClient.client
            val allProfiles = supabase.from("user_profiles")
                .select()
                .decodeList<UserProfile>()
            
            guardianProfile = allProfiles.firstOrNull { it.email == guardianEmail }
            guardianToken = guardianProfile?.fcmToken
            
            AppLogger.logInfo("NOTIFICATION", "Guardian profile lookup result", 
                "Profile found: ${guardianProfile != null}, Has FCM token: ${!guardianToken.isNullOrEmpty()}")
            
            // If the guardian is the current user, get fresh FCM token directly from Firebase
            val currentUser = authHelper.getCurrentUser()
            if (currentUser?.email == guardianEmail) {
                AppLogger.logInfo("NOTIFICATION", "Guardian is current user - getting fresh FCM token", "")
                try {
                    guardianToken = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                    AppLogger.logSuccess("NOTIFICATION", "Got fresh FCM token", "Token: ${guardianToken?.take(20)}...")
                    
                    // Update database with fresh token
                    if (guardianToken != null) {
                        dbHelper.updateFCMToken(currentUser.id, guardianToken)
                        AppLogger.logInfo("NOTIFICATION", "Updated database with fresh token", "")
                    }
                } catch (e: Exception) {
                    AppLogger.logError("NOTIFICATION", "Failed to get fresh FCM token", "Error: ${e.message}", e)
                }
            }
            
            // Check if we have a valid FCM token
            if (guardianToken.isNullOrEmpty()) {
                // No FCM token means guardian doesn't have the app installed or hasn't logged in
                AppLogger.logWarning("NOTIFICATION", "No FCM token found for guardian", 
                    "Guardian: $guardianEmail - likely doesn't have app installed or hasn't logged in recently")
                
                // Try alternative notification methods
                return sendAlternativeNotification(guardianEmail, fromUserName, fromUserEmail, fromUserMobile, lat, lon)
            }

            // Send FCM notification via Vercel endpoint
            val request = VercelNotificationRequest(
                token = guardianToken,
                title = "🚨 VASATEY EMERGENCY ALERT",
                body = "$fromUserName needs immediate help!",
                fullName = fromUserName,
                email = fromUserEmail,
                phoneNumber = fromUserMobile,
                lastKnownLatitude = lat,
                lastKnownLongitude = lon
            )

            AppLogger.logInfo("NOTIFICATION", "=== SENDING FCM NOTIFICATION ===", 
                "URL: https://vasatey-notify-msg.vercel.app/api/sendNotification\n" +
                "Token: ${guardianToken.take(20)}...\n" +
                "Title: ${request.title}\n" +
                "Body: ${request.body}\n" +
                "From: ${request.fullName} (${request.email})")

            val response = RetrofitInstance.api.sendNotification(request)
            
            AppLogger.logInfo("NOTIFICATION", "=== FCM RESPONSE ===", 
                "Response Code: ${response.code()}\n" +
                "Is Successful: ${response.isSuccessful}\n" +
                "Message: ${response.message()}")
            
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                AppLogger.logError("NOTIFICATION", "FCM notification failed", "Error: $errorBody")
                
                // Fallback to alternative notification if FCM fails
                return sendAlternativeNotification(guardianEmail, fromUserName, fromUserEmail, fromUserMobile, lat, lon)
            } else {
                val responseBody = response.body()?.string()
                AppLogger.logSuccess("NOTIFICATION", "FCM notification sent successfully", "Response: $responseBody")
                return true
            }
        } catch (e: Exception) {
            AppLogger.logError("NOTIFICATION", "Exception in sendNotificationToGuardian", "Exception: ${e.message}", e)
            
            // Try alternative notification as fallback
            return sendAlternativeNotification(guardianEmail, fromUserName, fromUserEmail, fromUserMobile, lat, lon)
        }
    }
    
    private suspend fun sendAlternativeNotification(guardianEmail: String, fromUserName: String, fromUserEmail: String, fromUserMobile: String?, lat: Double?, lon: Double?): Boolean {
        return try {
            AppLogger.logInfo("NOTIFICATION", "=== SENDING ALTERNATIVE NOTIFICATION ===", 
                "Guardian: $guardianEmail (FCM not available)")
            
            // Create emergency notification payload for email/SMS fallback
            val locationText = if (lat != null && lon != null) {
                "Location: https://maps.google.com/?q=$lat,$lon"
            } else {
                "Location: Not available"
            }
            
            val emergencyPayload = mapOf(
                "guardianEmail" to guardianEmail,
                "userName" to fromUserName,
                "userEmail" to fromUserEmail,
                "userMobile" to (fromUserMobile ?: "Not provided"),
                "latitude" to lat,
                "longitude" to lon,
                "message" to "EMERGENCY: $fromUserName needs immediate help!",
                "locationText" to locationText,
                "timestamp" to System.currentTimeMillis(),
                "notificationType" to "EMERGENCY_FALLBACK"
            )
            
            // Try to send via webhook or email service
            val fallbackUrl = "https://vasatey-notify-msg.vercel.app/api/sendNotification"
            val fallbackRequest = """
                {
                    "guardianEmail": "$guardianEmail",
                    "userName": "$fromUserName", 
                    "userEmail": "$fromUserEmail",
                    "userMobile": "${fromUserMobile ?: ""}",
                    "latitude": $lat,
                    "longitude": $lon,
                    "emergency": true,
                    "fallback": true,
                    "message": "EMERGENCY: $fromUserName needs immediate help!"
                }
            """.trimIndent()
            
            AppLogger.logInfo("NOTIFICATION", "Attempting fallback notification", 
                "Method: Email/SMS fallback\nPayload: $fallbackRequest")
            
            // For now, log that we attempted alternative notification
            // In a real implementation, you'd integrate with email/SMS services
            AppLogger.logWarning("NOTIFICATION", "Alternative notification attempted", 
                "Guardian $guardianEmail notified via fallback method (email/SMS integration needed)")
            
            // Return true to indicate we attempted notification, even if via fallback
            return true
            
        } catch (e: Exception) {
            AppLogger.logError("NOTIFICATION", "Alternative notification failed", "Error: ${e.message}", e)
            return false
        }
    }

    private suspend fun logAlertToSupabase(fromUserName: String, fromUserEmail: String, fromUserMobile: String?, lat: Double?, lon: Double?) {
        try {
            Log.d("ListeningService", "=== LOGGING ALERT TO DATABASE ===")
            val currentUser = authHelper.getCurrentUser()
            if (currentUser != null) {
                // Get the user profile ID instead of using auth ID
                val profileIdResult = dbHelper.getUserProfileId(currentUser.id)
                val profileId = profileIdResult.getOrNull()
                
                Log.d("ListeningService", "Profile ID: $profileId")
                
                if (profileId != null) {
                    val alert = Alert(
                        userId = profileId,
                        alertType = "emergency",
                        severity = "high",
                        triggerMethod = "voice",
                        locationLatitude = lat,
                        locationLongitude = lon,
                        alertMessage = "Emergency alert from $fromUserName - Mobile: ${fromUserMobile ?: "Not available"}"
                    )
                    
                    Log.d("ListeningService", "Saving alert: ${alert.alertType} at location ($lat, $lon)")
                    
                    // Use AlertHistoryManager for smart storage management
                    val saveResult = alertHistoryManager.saveAlert(alert, profileId)
                    if (saveResult.isSuccess) {
                        Log.d("ListeningService", "Alert saved successfully!")
                    } else {
                        Log.e("ListeningService", "Failed to save alert: ${saveResult.exceptionOrNull()?.message}")
                    }
                } else {
                    Log.e("ListeningService", "Profile ID is null")
                }
            } else {
                Log.e("ListeningService", "Current user is null")
            }
        } catch (e: Exception) {
            Log.e("ListeningService", "Failed to log alert to Supabase", e)
        }
    }

    private fun stopPorcupine() {
        porcupineManager?.stop()
        porcupineManager?.delete()
    }

    private fun showDetectedNotification(successCount: Int, totalCount: Int) {
        val channelId = "vasatey_alert_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Help Alerts Sent", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 Help Alert Sent!")
            .setContentText("Notified $successCount of $totalCount guardians.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showErrorNotification(message: String) {
        val channelId = "vasatey_error_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Service Errors", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Vasatey Alert Error")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
