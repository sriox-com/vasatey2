package com.sriox.vasatey.data.models

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * User data model representing authenticated users in the system
 * Maps to the 'users' table in Supabase
 */
@Serializable
data class User(
    val id: String,
    val email: String,
    val createdAt: String,
    val updatedAt: String,
    val lastSignInAt: String? = null,
    val emailConfirmedAt: String? = null,
    val phone: String? = null,
    val isActive: Boolean = true,
    val appMetadata: Map<String, String> = emptyMap(),
    val userMetadata: Map<String, String> = emptyMap()
) {
    
    /**
     * Get formatted creation date
     */
    fun getFormattedCreatedAt(): String {
        return try {
            val dateTime = LocalDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME)
            dateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        } catch (e: Exception) {
            createdAt
        }
    }
    
    /**
     * Check if user email is confirmed
     */
    fun isEmailConfirmed(): Boolean = emailConfirmedAt != null
    
    /**
     * Get display name from metadata or email
     */
    fun getDisplayName(): String {
        return userMetadata["full_name"] 
            ?: userMetadata["name"]
            ?: email.substringBefore("@").replaceFirstChar { it.titlecase() }
    }
    
    companion object {
        /**
         * Create empty user for initialization
         */
        fun empty(): User {
            return User(
                id = "",
                email = "",
                createdAt = "",
                updatedAt = ""
            )
        }
    }
}

/**
 * User profile data model with comprehensive user information
 * Maps to the 'user_profiles' table in Supabase
 */
@Serializable
data class UserProfile(
    val id: String,
    val userId: String,
    
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
    
    // Current Location
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
    
    // Emergency Contacts
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
    
    // Audit fields
    val createdAt: String,
    val updatedAt: String
) {
    
    /**
     * Get full address as formatted string
     */
    fun getFullAddress(): String {
        val addressParts = listOfNotNull(
            addressLine1,
            addressLine2,
            city,
            state,
            postalCode,
            country
        ).filter { it.isNotBlank() }
        
        return addressParts.joinToString(", ")
    }
    
    /**
     * Get primary contact information
     */
    fun getPrimaryContact(): String? {
        return phonePrimary?.takeIf { it.isNotBlank() }
    }
    
    /**
     * Check if user has current location
     */
    fun hasCurrentLocation(): Boolean {
        return currentLatitude != null && currentLongitude != null
    }
    
    /**
     * Get emergency contacts as list
     */
    fun getEmergencyContacts(): List<EmergencyContact> {
        val contacts = mutableListOf<EmergencyContact>()
        
        if (!emergencyContact1Name.isNullOrBlank() && !emergencyContact1Phone.isNullOrBlank()) {
            contacts.add(
                EmergencyContact(
                    name = emergencyContact1Name,
                    phone = emergencyContact1Phone,
                    email = emergencyContact1Email,
                    priority = 1
                )
            )
        }
        
        if (!emergencyContact2Name.isNullOrBlank() && !emergencyContact2Phone.isNullOrBlank()) {
            contacts.add(
                EmergencyContact(
                    name = emergencyContact2Name,
                    phone = emergencyContact2Phone,
                    email = emergencyContact2Email,
                    priority = 2
                )
            )
        }
        
        if (!emergencyContact3Name.isNullOrBlank() && !emergencyContact3Phone.isNullOrBlank()) {
            contacts.add(
                EmergencyContact(
                    name = emergencyContact3Name,
                    phone = emergencyContact3Phone,
                    email = emergencyContact3Email,
                    priority = 3
                )
            )
        }
        
        return contacts
    }
    
    /**
     * Check if profile is complete enough for emergency use
     */
    fun isEmergencyReady(): Boolean {
        return emergencyEnabled && 
               hasCurrentLocation() && 
               getEmergencyContacts().isNotEmpty() &&
               !fcmToken.isNullOrBlank()
    }
    
    companion object {
        /**
         * Create empty profile for a user
         */
        fun empty(userId: String): UserProfile {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            return UserProfile(
                id = "",
                userId = userId,
                createdAt = now,
                updatedAt = now
            )
        }
        
        /**
         * Create basic profile from user registration data
         */
        fun fromRegistration(
            userId: String,
            fullName: String?,
            email: String,
            phone: String?
        ): UserProfile {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            return UserProfile(
                id = "",
                userId = userId,
                fullName = fullName,
                phonePrimary = phone,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}

/**
 * Emergency contact data structure
 */
@Serializable
data class EmergencyContact(
    val name: String,
    val phone: String,
    val email: String? = null,
    val priority: Int
)