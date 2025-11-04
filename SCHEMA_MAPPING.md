# Database Schema to Kotlin Model Mapping

This document provides a quick reference for how Kotlin data models map to Supabase database tables.

## Table of Contents

1. [Overview](#overview)
2. [Table Mapping](#table-mapping)
3. [Model Definitions](#model-definitions)
4. [Usage Examples](#usage-examples)

## Overview

The Vasatey application uses Kotlinx Serialization for data models that directly map to Supabase PostgreSQL tables. All models are annotated with `@Serializable` for automatic JSON encoding/decoding.

## Table Mapping

| Kotlin Model | Database Table | File Location | Primary Use |
|-------------|---------------|---------------|-------------|
| `User` | `users` (Supabase Auth) | `User.kt` | Authentication data |
| `UserProfile` | `user_profiles` | `User.kt` | Extended user information |
| `EmergencyContact` | Embedded in `UserProfile` | `User.kt` | Emergency contact details |
| `EmergencyAlert` | `emergency_alerts` | `EmergencyAlert.kt` | Alert incidents |
| `AlertResponse` | `alert_responses` | `EmergencyAlert.kt` | Response tracking |
| `Notification` | `notifications` | `Notification.kt` | Notification records |
| `DeviceSession` | `device_sessions` | `Notification.kt` | Device management |
| `VoiceDetectionLog` | `voice_detection_logs` | `SystemModels.kt` | Voice events |
| `SystemLog` | `system_logs` | `SystemModels.kt` | Application logs |
| `AppSetting` | Embedded in various tables | `SystemModels.kt` | Configuration |
| `Location` | Embedded in various tables | `SystemModels.kt` | Location data |

## Model Definitions

### User & UserProfile

#### User.kt
```kotlin
@Serializable
data class User(
    val id: String,                    // UUID
    val email: String,                 // Email address
    val createdAt: String,             // ISO timestamp
    val updatedAt: String,             // ISO timestamp
    val lastSignInAt: String? = null,  // ISO timestamp
    val emailConfirmedAt: String? = null,
    val phone: String? = null,
    val isActive: Boolean = true,
    val appMetadata: Map<String, String> = emptyMap(),
    val userMetadata: Map<String, String> = emptyMap()
)
```

**Database Table**: `users` (managed by Supabase Auth)

**Key Methods**:
- `getFormattedCreatedAt()`: Returns formatted date
- `isEmailConfirmed()`: Check email verification status
- `getDisplayName()`: Get user's display name

---

```kotlin
@Serializable
data class UserProfile(
    val id: String,                              // UUID
    val userId: String,                          // References users.id
    
    // Personal Information
    val fullName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val dateOfBirth: String? = null,
    val gender: String? = null,
    val avatarUrl: String? = null,
    
    // Contact Information
    val phonePrimary: String? = null,
    val phoneSecondary: String? = null,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val postalCode: String? = null,
    
    // Location
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val lastLocationUpdate: String? = null,
    val locationSharingEnabled: Boolean = true,
    
    // Emergency Settings
    val emergencyEnabled: Boolean = true,
    val voiceDetectionEnabled: Boolean = true,
    val wakeWordSensitivity: Double = 0.5,
    val autoEmergencyTimeout: Int = 30,
    
    // Notification Preferences
    val fcmToken: String? = null,
    val pushNotificationsEnabled: Boolean = true,
    val smsNotificationsEnabled: Boolean = true,
    val emailNotificationsEnabled: Boolean = true,
    
    // Emergency Contacts (up to 3)
    val emergencyContact1Name: String? = null,
    val emergencyContact1Phone: String? = null,
    val emergencyContact1Email: String? = null,
    val emergencyContact2Name: String? = null,
    val emergencyContact2Phone: String? = null,
    val emergencyContact2Email: String? = null,
    val emergencyContact3Name: String? = null,
    val emergencyContact3Phone: String? = null,
    val emergencyContact3Email: String? = null,
    
    // Medical Information
    val medicalConditions: String? = null,
    val medications: String? = null,
    val allergies: String? = null,
    val bloodType: String? = null,
    val medicalNotes: String? = null,
    
    // App Settings
    val languagePreference: String = "en",
    val timezone: String? = null,
    val notificationQuietHoursStart: String? = null,
    val notificationQuietHoursEnd: String? = null,
    
    // Audit
    val createdAt: String,
    val updatedAt: String
)
```

**Database Table**: `user_profiles`

**Key Methods**:
- `getFullAddress()`: Returns formatted address
- `getPrimaryContact()`: Get primary phone number
- `hasCurrentLocation()`: Check if location is set
- `getEmergencyContacts()`: Get list of emergency contacts
- `isEmergencyReady()`: Verify emergency readiness

---

```kotlin
@Serializable
data class EmergencyContact(
    val name: String,
    val phone: String,
    val email: String? = null,
    val priority: Int
)
```

**Storage**: Embedded in `UserProfile` fields

### Emergency Alerts

#### EmergencyAlert.kt
```kotlin
@Serializable
data class EmergencyAlert(
    val id: String,                              // UUID
    val userId: String,                          // References users.id
    
    // Alert Classification
    val alertType: String = "general",           // AlertType enum value
    val severityLevel: Int = 1,                  // 1-5 (low to emergency)
    val status: String = "active",               // Status enum value
    
    // Trigger Information
    val triggerMethod: String,                   // How alert was triggered
    val voicePhraseDetected: String? = null,
    val confidenceScore: Double? = null,         // 0.0 - 1.0
    
    // Location Data
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val locationAccuracy: Double? = null,
    val addressDescription: String? = null,
    val indoorLocation: String? = null,
    
    // Timing
    val detectedAt: String,                      // ISO timestamp
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
    
    // Communication Tracking
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
    
    // Audit
    val createdAt: String,
    val updatedAt: String
)
```

**Database Table**: `emergency_alerts`

**Enums**:
- `AlertType`: general, voice_detected, manual, fall_detected, panic, medical, fire, intrusion
- `SeverityLevel`: LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4), EMERGENCY(5)
- `Status`: active, acknowledged, responding, resolved, false_alarm, cancelled

**Key Methods**:
- `isActive()`: Check if alert is active
- `requiresImmediateAttention()`: Check if critical
- `getFormattedDetectedAt()`: Formatted timestamp
- `getTimeSinceDetection()`: Human-readable time
- `hasLocation()`: Check if location available
- `getDisplayAddress()`: Get formatted address
- `isVoiceTriggered()`: Check trigger method
- `getTotalCommunications()`: Count all communications

**Factory Methods**:
- `create()`: Create generic alert
- `createVoiceAlert()`: Create voice-triggered alert
- `createManualAlert()`: Create manual alert

---

```kotlin
@Serializable
data class AlertResponse(
    val id: String,
    val alertId: String,                         // References emergency_alerts.id
    val responderUserId: String? = null,
    
    // Response Details
    val responseType: String,                    // ResponseType enum
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
    
    // Audit
    val createdAt: String,
    val updatedAt: String
)
```

**Database Table**: `alert_responses`

**Enums**:
- `ResponseType`: acknowledged, dispatched, en_route, on_scene, resolved

### Notifications

#### Notification.kt
```kotlin
@Serializable
data class Notification(
    val id: String,
    val userId: String,
    val notificationType: String,
    val title: String,
    val message: String,
    val priority: String = "normal",
    val category: String = "general",
    val actionUrl: String? = null,
    val actionData: Map<String, String> = emptyMap(),
    val isRead: Boolean = false,
    val readAt: String? = null,
    val deliveryStatus: String = "pending",
    val deliveredAt: String? = null,
    val fcmMessageId: String? = null,
    val createdAt: String,
    val expiresAt: String? = null
)
```

**Database Table**: `notifications`

---

```kotlin
@Serializable
data class DeviceSession(
    val id: String,
    val userId: String,
    val deviceId: String,
    val deviceName: String? = null,
    val deviceType: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val fcmToken: String? = null,
    val isActive: Boolean = true,
    val lastSeenAt: String? = null,
    val createdAt: String
)
```

**Database Table**: `device_sessions`

### System Models

#### SystemModels.kt
```kotlin
@Serializable
data class VoiceDetectionLog(
    val id: String,
    val userId: String,
    val sessionId: String,
    val detectionType: String,
    val wakeWord: String,
    val confidenceScore: Double,
    val wasAccepted: Boolean,
    val rejectionReason: String? = null,
    val audioQuality: Double? = null,
    val backgroundNoiseLevel: Double? = null,
    val processingTimeMs: Int,
    val deviceInfo: Map<String, String> = emptyMap(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: String
)
```

**Database Table**: `voice_detection_logs`

---

```kotlin
@Serializable
data class SystemLog(
    val id: String,
    val userId: String? = null,
    val sessionId: String? = null,
    val logLevel: String,
    val category: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
    val stackTrace: String? = null,
    val deviceInfo: Map<String, String> = emptyMap(),
    val appVersion: String? = null,
    val createdAt: String
)
```

**Database Table**: `system_logs`

**Log Levels**: debug, info, warning, error, critical

**Categories**: emergency, notification, voice_detection, location, authentication, database, network, system

## Usage Examples

### Creating a User Profile

```kotlin
val profile = UserProfile(
    id = "",
    userId = authUserId,
    fullName = "John Doe",
    phonePrimary = "+1234567890",
    emergencyContact1Name = "Jane Doe",
    emergencyContact1Phone = "+0987654321",
    voiceDetectionEnabled = true,
    createdAt = LocalDateTime.now().toString(),
    updatedAt = LocalDateTime.now().toString()
)

val result = SupabaseDatabaseHelper.getInstance()
    .upsertUserProfile(profile)
```

### Creating an Emergency Alert

```kotlin
val alert = EmergencyAlert.createVoiceAlert(
    userId = currentUserId,
    phrase = "help me",
    confidence = 0.95,
    latitude = 37.7749,
    longitude = -122.4194
)

val result = SupabaseDatabaseHelper.getInstance()
    .saveEmergencyAlert(alert)
```

### Querying Alerts

```kotlin
val alerts = SupabaseDatabaseHelper.getInstance()
    .getEmergencyAlerts(
        userId = currentUserId,
        status = "active",
        limit = 20
    )
```

### Logging Voice Detection

```kotlin
val log = VoiceDetectionLog(
    id = "",
    userId = currentUserId,
    sessionId = sessionId,
    detectionType = "wake_word",
    wakeWord = "hey vasatey",
    confidenceScore = 0.89,
    wasAccepted = true,
    processingTimeMs = 250,
    createdAt = LocalDateTime.now().toString()
)

SupabaseDatabaseHelper.getInstance()
    .saveVoiceDetectionLog(log)
```

## Column Name Conventions

### Kotlin Property Names (camelCase)
```kotlin
val fullName: String
val createdAt: String
val isActive: Boolean
```

### Database Column Names (snake_case)
```sql
full_name VARCHAR(255)
created_at TIMESTAMP WITH TIME ZONE
is_active BOOLEAN
```

Kotlinx Serialization handles the conversion automatically using `@SerialName` annotations when needed.

## Foreign Key Relationships

```
users (Supabase Auth)
  └─> user_profiles (user_id)
      └─> emergency_alerts (user_id)
          └─> alert_responses (alert_id)
      └─> notifications (user_id)
      └─> device_sessions (user_id)
      └─> voice_detection_logs (user_id)
      └─> system_logs (user_id)
```

## Timestamp Handling

All timestamps are stored as ISO 8601 strings in UTC:
```kotlin
val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
// Example: "2024-11-04T08:30:00"
```

Database stores as `TIMESTAMP WITH TIME ZONE` for proper timezone handling.

## Best Practices

1. **Always use factory methods** for creating complex objects:
   ```kotlin
   EmergencyAlert.createVoiceAlert(...)
   UserProfile.fromRegistration(...)
   ```

2. **Check for null values** on optional fields:
   ```kotlin
   profile.emergencyContact1Phone?.let { phone ->
       // Use phone
   }
   ```

3. **Use helper methods** for common operations:
   ```kotlin
   if (alert.isActive()) { ... }
   if (profile.isEmergencyReady()) { ... }
   ```

4. **Handle errors with Result**:
   ```kotlin
   when (val result = database.getUserProfile(userId)) {
       is Result.Success -> // Handle success
       is Result.Failure -> // Handle error
   }
   ```

## Schema Version

**Current Version**: 1.0.0  
**Last Updated**: November 2024  
**Compatible With**: Supabase SDK 2.0.4

---

For complete database schema, see `database/vasatey_complete_schema.sql`.  
For API documentation, see `SUPABASE_API_REFERENCE.md`.
