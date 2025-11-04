# Vasatey API Usage Guide

This guide provides practical examples for using the Vasatey backend API through the Supabase Kotlin SDK.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Authentication](#authentication)
3. [User Management](#user-management)
4. [Emergency Alerts](#emergency-alerts)
5. [Notifications](#notifications)
6. [Voice Detection](#voice-detection)
7. [Device Sessions](#device-sessions)
8. [Error Handling](#error-handling)
9. [Real-time Subscriptions](#real-time-subscriptions)

## Getting Started

### Initialize Supabase Client

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Supabase
        val result = SupabaseClientManager.getInstance().initialize(this)
        
        result.onSuccess {
            Log.i("App", "Supabase initialized successfully")
        }.onFailure { error ->
            Log.e("App", "Failed to initialize Supabase", error)
        }
    }
}
```

### Get Helper Instances

```kotlin
// Get authentication helper
val auth = AuthenticationHelper.getInstance()

// Get database helper
val database = SupabaseDatabaseHelper.getInstance()

// Get Supabase client
val client = SupabaseClientManager.getInstance().client
```

## Authentication

### Sign Up

```kotlin
suspend fun signUpUser(
    email: String,
    password: String,
    fullName: String,
    phone: String
) {
    val result = auth.signUp(
        email = email,
        password = password,
        fullName = fullName,
        phoneNumber = phone
    )
    
    when (result) {
        is AuthResult.Success -> {
            val user = result.data
            Log.i("Auth", "User created: ${user.id}")
            // Navigate to main screen
        }
        is AuthResult.Error -> {
            val error = AuthError.fromException(result.exception)
            Log.e("Auth", "Sign up failed: ${error.message}")
            // Show error to user
        }
        is AuthResult.Loading -> {
            // Show loading indicator
        }
    }
}
```

### Sign In

```kotlin
suspend fun signInUser(email: String, password: String) {
    val result = auth.signIn(email, password)
    
    when (result) {
        is AuthResult.Success -> {
            val user = result.data
            Log.i("Auth", "Signed in: ${user.email}")
        }
        is AuthResult.Error -> {
            handleAuthError(result.exception)
        }
        is AuthResult.Loading -> {
            showLoadingIndicator()
        }
    }
}
```

### Sign Out

```kotlin
suspend fun signOut() {
    val result = SupabaseClientManager.getInstance().signOut()
    
    result.onSuccess {
        // Navigate to login screen
    }.onFailure { error ->
        Log.e("Auth", "Sign out failed", error)
    }
}
```

### Check Authentication Status

```kotlin
suspend fun checkAuth() {
    if (SupabaseClientManager.getInstance().isAuthenticated()) {
        val session = SupabaseClientManager.getInstance().getCurrentSession()
        Log.i("Auth", "User is authenticated: ${session?.user?.email}")
    } else {
        // Navigate to login
    }
}
```

## User Management

### Get User Profile

```kotlin
suspend fun loadUserProfile(userId: String) {
    val result = database.getUserProfile(userId)
    
    result.onSuccess { profile ->
        if (profile != null) {
            // Display profile data
            binding.tvName.text = profile.fullName
            binding.tvEmail.text = profile.userId
            
            // Check emergency readiness
            if (profile.isEmergencyReady()) {
                binding.ivStatus.setImageResource(R.drawable.ic_check)
            }
        } else {
            // Profile not found
        }
    }.onFailure { error ->
        Log.e("Profile", "Failed to load profile", error)
    }
}
```

### Update User Profile

```kotlin
suspend fun updateProfile(userId: String) {
    val updates = mapOf(
        "full_name" to "John Updated",
        "phone_primary" to "+1234567890",
        "voice_detection_enabled" to true,
        "wake_word_sensitivity" to 0.75
    )
    
    val result = database.updateUserProfile(userId, updates)
    
    result.onSuccess { updatedProfile ->
        Log.i("Profile", "Profile updated: ${updatedProfile.fullName}")
    }.onFailure { error ->
        Log.e("Profile", "Update failed", error)
    }
}
```

### Update FCM Token

```kotlin
suspend fun updateFCMToken(userId: String, token: String) {
    val updates = mapOf("fcm_token" to token)
    
    database.updateUserProfile(userId, updates).onSuccess {
        Log.i("FCM", "Token updated successfully")
    }
}
```

### Update Location

```kotlin
suspend fun updateUserLocation(userId: String, latitude: Double, longitude: Double) {
    val updates = mapOf(
        "current_latitude" to latitude,
        "current_longitude" to longitude,
        "last_location_update" to LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
    )
    
    database.updateUserProfile(userId, updates).onSuccess {
        Log.i("Location", "Location updated: $latitude, $longitude")
    }
}
```

## Emergency Alerts

### Create Voice-Triggered Alert

```kotlin
suspend fun createVoiceAlert(
    userId: String,
    phrase: String,
    confidence: Double,
    location: Pair<Double, Double>?
) {
    val alert = EmergencyAlert.createVoiceAlert(
        userId = userId,
        phrase = phrase,
        confidence = confidence,
        latitude = location?.first,
        longitude = location?.second
    )
    
    val result = database.saveEmergencyAlert(alert)
    
    result.onSuccess { savedAlert ->
        Log.i("Alert", "Emergency alert created: ${savedAlert.id}")
        
        // Send notifications to emergency contacts
        notifyEmergencyContacts(savedAlert)
        
        // Trigger UI update
        showEmergencyUI(savedAlert)
    }.onFailure { error ->
        Log.e("Alert", "Failed to create alert", error)
    }
}
```

### Create Manual Alert

```kotlin
suspend fun createManualEmergencyAlert(
    userId: String,
    description: String?,
    location: Pair<Double, Double>?
) {
    val alert = EmergencyAlert.createManualAlert(
        userId = userId,
        latitude = location?.first,
        longitude = location?.second,
        description = description
    )
    
    database.saveEmergencyAlert(alert).onSuccess { savedAlert ->
        Log.i("Alert", "Manual alert created: ${savedAlert.id}")
    }
}
```

### Get User's Alerts

```kotlin
suspend fun loadUserAlerts(userId: String) {
    val result = database.getEmergencyAlerts(
        userId = userId,
        status = "active",
        limit = 50
    )
    
    result.onSuccess { alerts ->
        Log.i("Alert", "Loaded ${alerts.size} alerts")
        
        // Filter by severity
        val criticalAlerts = alerts.filter { it.requiresImmediateAttention() }
        
        // Display in UI
        alertAdapter.submitList(alerts)
    }.onFailure { error ->
        Log.e("Alert", "Failed to load alerts", error)
    }
}
```

### Update Alert Status

```kotlin
suspend fun resolveAlert(alertId: String, resolutionNotes: String) {
    val updates = mapOf(
        "status" to "resolved",
        "resolved_at" to LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
        "resolution_notes" to resolutionNotes
    )
    
    val result = database.updateEmergencyAlert(alertId, updates)
    
    result.onSuccess { updatedAlert ->
        Log.i("Alert", "Alert resolved: ${updatedAlert.id}")
    }
}
```

### Get Alert by ID

```kotlin
suspend fun loadAlertDetails(alertId: String) {
    val result = database.getEmergencyAlert(alertId)
    
    result.onSuccess { alert ->
        if (alert != null) {
            // Display alert details
            binding.tvType.text = alert.getAlertType().displayName
            binding.tvSeverity.text = alert.getSeverityLevel().displayName
            binding.tvStatus.text = alert.getStatus().displayName
            binding.tvTime.text = alert.getTimeSinceDetection()
            
            if (alert.hasLocation()) {
                binding.tvLocation.text = alert.getDisplayAddress()
            }
            
            if (alert.isVoiceTriggered()) {
                binding.tvPhrase.text = alert.voicePhraseDetected
                binding.tvConfidence.text = "${alert.getConfidencePercentage()}%"
            }
        }
    }
}
```

## Notifications

### Send Notification

```kotlin
suspend fun sendNotification(
    userId: String,
    title: String,
    message: String,
    priority: String = "high",
    actionUrl: String? = null
) {
    val notification = Notification(
        id = "",
        userId = userId,
        notificationType = "emergency",
        title = title,
        message = message,
        priority = priority,
        category = "emergency",
        actionUrl = actionUrl,
        isRead = false,
        deliveryStatus = "pending",
        createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
    )
    
    val result = database.saveNotification(notification)
    
    result.onSuccess { saved ->
        Log.i("Notification", "Notification sent: ${saved.id}")
    }
}
```

### Get Unread Notifications

```kotlin
suspend fun loadUnreadNotifications(userId: String) {
    val result = database.getNotifications(
        userId = userId,
        isRead = false,
        limit = 50
    )
    
    result.onSuccess { notifications ->
        val unreadCount = notifications.size
        updateNotificationBadge(unreadCount)
        
        // Group by category
        val emergencyNotifications = notifications.filter { it.category == "emergency" }
        val systemNotifications = notifications.filter { it.category == "system" }
    }
}
```

### Mark Notification as Read

```kotlin
suspend fun markNotificationRead(notificationId: String) {
    val updates = mapOf(
        "is_read" to true,
        "read_at" to LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
    )
    
    database.updateNotification(notificationId, updates).onSuccess {
        Log.i("Notification", "Marked as read")
    }
}
```

## Voice Detection

### Log Voice Detection Event

```kotlin
suspend fun logVoiceDetection(
    userId: String,
    sessionId: String,
    wakeWord: String,
    confidence: Double,
    accepted: Boolean,
    location: Pair<Double, Double>?
) {
    val log = VoiceDetectionLog(
        id = "",
        userId = userId,
        sessionId = sessionId,
        detectionType = "wake_word",
        wakeWord = wakeWord,
        confidenceScore = confidence,
        wasAccepted = accepted,
        rejectionReason = if (!accepted) "confidence_too_low" else null,
        audioQuality = 0.8,
        backgroundNoiseLevel = 0.2,
        processingTimeMs = 180,
        deviceInfo = mapOf(
            "model" to Build.MODEL,
            "os_version" to Build.VERSION.RELEASE
        ),
        latitude = location?.first,
        longitude = location?.second,
        createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
    )
    
    val result = database.saveVoiceDetectionLog(log)
    
    result.onSuccess {
        Log.i("Voice", "Detection logged")
    }
}
```

### Get Voice Detection History

```kotlin
suspend fun loadVoiceHistory(userId: String, limit: Int = 100) {
    val result = database.getVoiceDetectionLogs(
        userId = userId,
        limit = limit
    )
    
    result.onSuccess { logs ->
        // Calculate statistics
        val totalDetections = logs.size
        val accepted = logs.count { it.wasAccepted }
        val rejected = logs.count { !it.wasAccepted }
        val avgConfidence = logs.map { it.confidenceScore }.average()
        
        Log.i("Voice", "Detections: $totalDetections, Accepted: $accepted, Avg Confidence: $avgConfidence")
    }
}
```

## Device Sessions

### Register Device Session

```kotlin
suspend fun registerDevice(
    userId: String,
    deviceId: String,
    fcmToken: String
) {
    val session = DeviceSession(
        id = "",
        userId = userId,
        deviceId = deviceId,
        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
        deviceType = "android",
        osVersion = Build.VERSION.RELEASE,
        appVersion = BuildConfig.VERSION_NAME,
        fcmToken = fcmToken,
        isActive = true,
        lastSeenAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
        createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
    )
    
    val result = database.saveDeviceSession(session)
    
    result.onSuccess {
        Log.i("Device", "Device registered")
    }
}
```

### Update Device Activity

```kotlin
suspend fun updateDeviceActivity(deviceId: String) {
    val updates = mapOf(
        "last_seen_at" to LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
        "is_active" to true
    )
    
    database.updateDeviceSession(deviceId, updates).onSuccess {
        Log.i("Device", "Activity updated")
    }
}
```

## Error Handling

### Handling Authentication Errors

```kotlin
fun handleAuthError(exception: Throwable) {
    val error = AuthError.fromException(exception)
    
    val message = when (error) {
        AuthError.INVALID_EMAIL -> "Please enter a valid email address"
        AuthError.WEAK_PASSWORD -> "Password must be at least 8 characters"
        AuthError.INVALID_CREDENTIALS -> "Invalid email or password"
        AuthError.EMAIL_ALREADY_EXISTS -> "An account with this email already exists"
        AuthError.NETWORK_ERROR -> "No internet connection. Please check your network"
        AuthError.RATE_LIMITED -> "Too many attempts. Please try again later"
        else -> "An error occurred. Please try again"
    }
    
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
```

### Handling Database Errors

```kotlin
suspend fun safeLoadProfile(userId: String) {
    try {
        val result = database.getUserProfile(userId)
        
        result.onSuccess { profile ->
            // Handle success
        }.onFailure { error ->
            when {
                error.message?.contains("network") == true -> {
                    showRetryDialog("Network error. Retry?")
                }
                error.message?.contains("not found") == true -> {
                    createInitialProfile(userId)
                }
                else -> {
                    logErrorAndNotify(error)
                }
            }
        }
    } catch (e: Exception) {
        Log.e("Database", "Unexpected error", e)
        showErrorMessage("Something went wrong")
    }
}
```

### Retry Logic

```kotlin
suspend fun <T> retryOperation(
    maxRetries: Int = 3,
    delayMs: Long = 1000,
    operation: suspend () -> Result<T>
): Result<T> {
    repeat(maxRetries) { attempt ->
        val result = operation()
        if (result.isSuccess) {
            return result
        }
        
        if (attempt < maxRetries - 1) {
            delay(delayMs * (attempt + 1))
        }
    }
    
    return operation()
}

// Usage
val profile = retryOperation {
    database.getUserProfile(userId)
}
```

## Real-time Subscriptions

### Subscribe to Alert Updates

```kotlin
fun subscribeToAlerts(userId: String) {
    lifecycleScope.launch {
        client.realtime
            .channel("emergency_alerts")
            .on(
                ChangeAction.INSERT,
                filter = "user_id=eq.$userId"
            ) { payload ->
                val newAlert = payload.decodeAs<EmergencyAlert>()
                handleNewAlert(newAlert)
            }
            .subscribe()
    }
}
```

### Subscribe to Notifications

```kotlin
fun subscribeToNotifications(userId: String) {
    lifecycleScope.launch {
        client.realtime
            .channel("notifications")
            .on(
                ChangeAction.ALL,
                filter = "user_id=eq.$userId"
            ) { payload ->
                when (payload.eventType) {
                    "INSERT" -> {
                        val notification = payload.decodeAs<Notification>()
                        showNotification(notification)
                    }
                    "UPDATE" -> {
                        // Handle update
                    }
                }
            }
            .subscribe()
    }
}
```

## Best Practices

### 1. Use Coroutines Properly

```kotlin
// In Activity/Fragment
lifecycleScope.launch {
    val result = database.getUserProfile(userId)
    // Handle result on main thread
}

// In ViewModel
viewModelScope.launch {
    _profileState.value = UiState.Loading
    val result = database.getUserProfile(userId)
    _profileState.value = when {
        result.isSuccess -> UiState.Success(result.getOrNull())
        else -> UiState.Error(result.exceptionOrNull())
    }
}
```

### 2. Cache Data Locally

```kotlin
class ProfileRepository {
    private var cachedProfile: UserProfile? = null
    private var cacheTime: Long = 0
    private val cacheTimeout = 5 * 60 * 1000 // 5 minutes
    
    suspend fun getProfile(userId: String): UserProfile? {
        val now = System.currentTimeMillis()
        
        if (cachedProfile != null && (now - cacheTime) < cacheTimeout) {
            return cachedProfile
        }
        
        val result = database.getUserProfile(userId)
        if (result.isSuccess) {
            cachedProfile = result.getOrNull()
            cacheTime = now
        }
        
        return cachedProfile
    }
}
```

### 3. Handle Offline Mode

```kotlin
fun isOnline(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

suspend fun saveAlertWithOfflineSupport(alert: EmergencyAlert) {
    if (isOnline()) {
        database.saveEmergencyAlert(alert)
    } else {
        // Save to local database
        localDb.saveAlert(alert)
        // Sync later when online
        scheduleSync()
    }
}
```

### 4. Implement Proper Logging

```kotlin
suspend fun logSystemEvent(
    category: String,
    message: String,
    level: String = "info",
    details: Map<String, String> = emptyMap()
) {
    val log = SystemLog(
        id = "",
        userId = getCurrentUserId(),
        sessionId = getCurrentSessionId(),
        logLevel = level,
        category = category,
        message = message,
        details = details,
        deviceInfo = getDeviceInfo(),
        appVersion = BuildConfig.VERSION_NAME,
        createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
    )
    
    database.saveSystemLog(log)
}
```

---

For more information:
- See [README.md](README.md) for setup instructions
- See [SCHEMA_MAPPING.md](SCHEMA_MAPPING.md) for database schema
- See [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) for deployment steps
