package com.sriox.vasatey.data.remote

import android.util.Log
import com.sriox.vasatey.data.models.User
import com.sriox.vasatey.data.models.UserProfile
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Authentication helper for managing user authentication with Supabase
 * Provides comprehensive authentication functionality with proper error handling
 */
class AuthenticationHelper private constructor() {
    
    companion object {
        private const val TAG = "AuthenticationHelper"
        
        @Volatile
        private var INSTANCE: AuthenticationHelper? = null
        
        fun getInstance(): AuthenticationHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthenticationHelper().also { INSTANCE = it }
            }
        }
    }
    
    private val supabaseClient = SupabaseClientManager.getInstance()
    
    /**
     * Sign up new user with email and password
     */
    suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        phoneNumber: String? = null
    ): AuthResult<User> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting sign up process for email: $email")
            
            // Validate input
            val validationError = validateSignUpInput(email, password, fullName)
            if (validationError != null) {
                return@withContext AuthResult.Error(Exception(validationError.message))
            }
            
            // Sign up with Supabase Auth
            val authResult = supabaseClient.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                data = mapOf(
                    "full_name" to fullName,
                    "phone_number" to (phoneNumber ?: "")
                )
            }
            
            // Create user profile in database
            val userProfile = UserProfile(
                id = authResult.user?.id ?: "",
                fullName = fullName,
                email = email,
                phoneNumber = phoneNumber,
                isVerified = false,
                emergencyContacts = emptyList(),
                settings = mapOf(
                    "voice_detection_enabled" to "true",
                    "location_sharing_enabled" to "true",
                    "emergency_auto_call" to "false"
                ),
                createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            )
            
            // Insert user profile
            supabaseClient.database
                .from("user_profiles")
                .insert(userProfile)
            
            // Create User object
            val user = User(
                id = authResult.user?.id ?: "",
                email = email,
                emailVerified = false,
                phoneNumber = phoneNumber,
                fullName = fullName,
                lastSignInAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                profile = userProfile
            )
            
            Log.i(TAG, "User signed up successfully: ${user.id}")
            AuthResult.Success(user)
            
        } catch (e: Exception) {
            Log.e(TAG, "Sign up failed", e)
            AuthResult.Error(e)
        }
    }
    
    /**
     * Sign in existing user with email and password
     */
    suspend fun signIn(email: String, password: String): AuthResult<User> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting sign in process for email: $email")
            
            // Validate input
            val validationError = validateSignInInput(email, password)
            if (validationError != null) {
                return@withContext AuthResult.Error(Exception(validationError.message))
            }
            
            // Sign in with Supabase Auth
            val authResult = supabaseClient.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            
            // Get user profile from database
            val userProfile = getUserProfile(authResult.user?.id ?: "")
            
            // Create User object
            val user = User(
                id = authResult.user?.id ?: "",
                email = authResult.user?.email ?: email,
                emailVerified = authResult.user?.emailConfirmedAt != null,
                phoneNumber = authResult.user?.phone,
                fullName = userProfile?.fullName ?: "",
                lastSignInAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                createdAt = authResult.user?.createdAt ?: LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                profile = userProfile
            )
            
            Log.i(TAG, "User signed in successfully: ${user.id}")
            AuthResult.Success(user)
            
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed", e)
            AuthResult.Error(e)
        }
    }
    
    /**
     * Sign out current user
     */
    suspend fun signOut(): AuthResult<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting sign out process")
            
            supabaseClient.signOut().getOrThrow()
            
            Log.i(TAG, "User signed out successfully")
            AuthResult.Success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Sign out failed", e)
            AuthResult.Error(e)
        }
    }
    
    /**
     * Get current authenticated user
     */
    suspend fun getCurrentUser(): AuthResult<User?> = withContext(Dispatchers.IO) {
        try {
            val session = supabaseClient.getCurrentSession()
            if (session == null) {
                return@withContext AuthResult.Success(null)
            }
            
            val authUser = session.user
            val userProfile = getUserProfile(authUser?.id ?: "")
            
            val user = User(
                id = authUser?.id ?: "",
                email = authUser?.email ?: "",
                emailVerified = authUser?.emailConfirmedAt != null,
                phoneNumber = authUser?.phone,
                fullName = userProfile?.fullName ?: "",
                lastSignInAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                createdAt = authUser?.createdAt ?: LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                profile = userProfile
            )
            
            AuthResult.Success(user)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current user", e)
            AuthResult.Error(e)
        }
    }
    
    /**
     * Send password reset email
     */
    suspend fun resetPassword(email: String): AuthResult<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sending password reset email to: $email")
            
            if (!isValidEmail(email)) {
                return@withContext AuthResult.Error(Exception(AuthError.INVALID_EMAIL.message))
            }
            
            supabaseClient.auth.resetPasswordForEmail(email)
            
            Log.i(TAG, "Password reset email sent successfully")
            AuthResult.Success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send password reset email", e)
            AuthResult.Error(e)
        }
    }
    
    /**
     * Update user password
     */
    suspend fun updatePassword(newPassword: String): AuthResult<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating user password")
            
            if (!isValidPassword(newPassword)) {
                return@withContext AuthResult.Error(Exception(AuthError.WEAK_PASSWORD.message))
            }
            
            supabaseClient.auth.updateUser {
                password = newPassword
            }
            
            Log.i(TAG, "Password updated successfully")
            AuthResult.Success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update password", e)
            AuthResult.Error(e)
        }
    }
    
    /**
     * Update user email
     */
    suspend fun updateEmail(newEmail: String): AuthResult<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating user email to: $newEmail")
            
            if (!isValidEmail(newEmail)) {
                return@withContext AuthResult.Error(Exception(AuthError.INVALID_EMAIL.message))
            }
            
            supabaseClient.auth.updateUser {
                email = newEmail
            }
            
            Log.i(TAG, "Email updated successfully")
            AuthResult.Success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update email", e)
            AuthResult.Error(e)
        }
    }
    
    /**
     * Resend email confirmation
     */
    suspend fun resendEmailConfirmation(email: String): AuthResult<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Resending email confirmation to: $email")
            
            supabaseClient.auth.resend(
                type = io.github.jan.supabase.gotrue.OtpType.Email.SIGNUP,
                email = email
            )
            
            Log.i(TAG, "Email confirmation resent successfully")
            AuthResult.Success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resend email confirmation", e)
            AuthResult.Error(e)
        }
    }
    
    /**
     * Check if user is authenticated
     */
    suspend fun isAuthenticated(): Boolean {
        return try {
            supabaseClient.isAuthenticated()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check authentication status", e)
            false
        }
    }
    
    /**
     * Get user profile from database
     */
    private suspend fun getUserProfile(userId: String): UserProfile? {
        return try {
            val result = supabaseClient.database
                .from("user_profiles")
                .select()
                .eq("id", userId)
                .decodeSingleOrNull<UserProfile>()
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user profile for user: $userId", e)
            null
        }
    }
    
    /**
     * Validate sign up input
     */
    private fun validateSignUpInput(email: String, password: String, fullName: String): AuthError? {
        return when {
            !isValidEmail(email) -> AuthError.INVALID_EMAIL
            !isValidPassword(password) -> AuthError.WEAK_PASSWORD
            fullName.isBlank() -> AuthError.UNKNOWN_ERROR
            else -> null
        }
    }
    
    /**
     * Validate sign in input
     */
    private fun validateSignInInput(email: String, password: String): AuthError? {
        return when {
            !isValidEmail(email) -> AuthError.INVALID_EMAIL
            password.isBlank() -> AuthError.WEAK_PASSWORD
            else -> null
        }
    }
    
    /**
     * Validate email format
     */
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    /**
     * Validate password strength
     */
    private fun isValidPassword(password: String): Boolean {
        return password.length >= 8
    }
}

/**
 * Authentication state listener interface
 */
interface AuthStateListener {
    fun onAuthStateChanged(user: User?)
    fun onAuthError(error: AuthError)
}

/**
 * Extension function to handle auth results with proper error mapping
 */
fun <T> AuthResult<T>.fold(
    onSuccess: (T) -> Unit,
    onError: (AuthError) -> Unit
) {
    when (this) {
        is AuthResult.Success -> onSuccess(data)
        is AuthResult.Error -> onError(AuthError.fromException(exception))
        is AuthResult.Loading -> { /* Handle loading state if needed */ }
    }
}