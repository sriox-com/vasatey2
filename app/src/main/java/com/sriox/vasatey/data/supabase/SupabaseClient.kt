package com.sriox.vasatey.data.supabase

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import org.json.JSONObject

/**
 * Supabase client configuration and initialization
 * Handles connection to Supabase backend services
 */
class SupabaseClientConfig(
    private val context: Context
) {
    
    companion object {
        private const val SUPABASE_URL = "https://your-project.supabase.co"
        private const val SUPABASE_ANON_KEY = "your-anon-key"
        
        // Timeout configurations
        private const val REQUEST_TIMEOUT = 60_000L
        private const val SOCKET_TIMEOUT = 60_000L
        private const val CONNECT_TIMEOUT = 60_000L
    }
    
    /**
     * Create and configure Supabase client
     */
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = getSupabaseUrl(),
            supabaseKey = getSupabaseKey()
        ) {
            // Install Postgrest for database operations
            install(Postgrest) {
                // Default schema
                defaultSchema = "public"
            }
            
            // Install GoTrue for authentication
            install(GoTrue) {
                // Auto refresh tokens
                autoRefreshToken = true
                // Save session in shared preferences
                autoSaveToStorage = true
                // Minimum auto refresh time
                minAutoRefreshDuration = 10
            }
            
            // Install Realtime for real-time updates
            install(Realtime) {
                // Realtime configuration
                reconnectDelay = 1000
                heartbeatInterval = 30000
            }
            
            // Install Storage for file uploads
            install(Storage)
            
            // HTTP client configuration
            httpEngine {
                install(HttpTimeout) {
                    requestTimeoutMillis = REQUEST_TIMEOUT
                    socketTimeoutMillis = SOCKET_TIMEOUT
                    connectTimeoutMillis = CONNECT_TIMEOUT
                }
            }
        }
    }
    
    /**
     * Get Supabase URL from configuration
     */
    private fun getSupabaseUrl(): String {
        return try {
            // Try to read from app configuration
            val configFile = context.assets.open("supabase-config.json")
            val configContent = configFile.bufferedReader().use { it.readText() }
            val configJson = JSONObject(configContent)
            configJson.getString("supabase_url")
        } catch (e: Exception) {
            // Fallback to default
            SUPABASE_URL
        }
    }
    
    /**
     * Get Supabase anonymous key from configuration
     */
    private fun getSupabaseKey(): String {
        return try {
            // Try to read from app configuration
            val configFile = context.assets.open("supabase-config.json")
            val configContent = configFile.bufferedReader().use { it.readText() }
            val configJson = JSONObject(configContent)
            configJson.getString("supabase_anon_key")
        } catch (e: Exception) {
            // Fallback to default
            SUPABASE_ANON_KEY
        }
    }
    
    /**
     * Get database client
     */
    fun getDatabase() = client.postgrest
    
    /**
     * Get auth client
     */
    fun getAuth() = client.auth
    
    /**
     * Get realtime client
     */
    fun getRealtime() = client.realtime
    
    /**
     * Get storage client
     */
    fun getStorage() = client.storage
    
    /**
     * Check if client is connected
     */
    suspend fun isConnected(): Boolean {
        return try {
            // Try a simple query to check connection
            client.from("users").select().limit(1)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Close client connections
     */
    fun close() {
        try {
            client.close()
        } catch (e: Exception) {
            // Log error
        }
    }
}

/**
 * Authentication result sealed class
 */
sealed class AuthResult {
    data class Success(val user: UserInfo) : AuthResult()
    data class Error(val message: String, val throwable: Throwable? = null) : AuthResult()
    object Loading : AuthResult()
}

/**
 * Authentication state sealed class
 */
sealed class AuthState {
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * User session data
 */
data class UserSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val isEmailConfirmed: Boolean = false
)

/**
 * Authentication helper class
 * Handles user authentication operations with Supabase
 */
class AuthenticationHelper(
    private val supabaseClient: SupabaseClientConfig
) {
    
    private val auth = supabaseClient.getAuth()
    
    /**
     * Get current authentication state as Flow
     */
    fun getAuthState(): Flow<AuthState> = flow {
        try {
            emit(AuthState.Loading)
            val session = auth.currentSessionOrNull()
            if (session != null) {
                emit(AuthState.Authenticated)
            } else {
                emit(AuthState.Unauthenticated)
            }
        } catch (e: Exception) {
            emit(AuthState.Error(e.message ?: "Authentication error"))
        }
    }
    
    /**
     * Sign up with email and password
     */
    suspend fun signUp(
        email: String,
        password: String,
        fullName: String? = null
    ): AuthResult {
        return try {
            val result = auth.signUpWith(Email) {
                this.email = email
                this.password = password
                data = buildMap {
                    fullName?.let { put("full_name", it) }
                }
            }
            
            AuthResult.Success(result.user!!)
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Sign up failed",
                throwable = e
            )
        }
    }
    
    /**
     * Sign in with email and password
     */
    suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            
            AuthResult.Success(result.user!!)
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Sign in failed",
                throwable = e
            )
        }
    }
    
    /**
     * Sign out current user
     */
    suspend fun signOut(): AuthResult {
        return try {
            auth.signOut()
            AuthResult.Success(
                UserInfo(
                    id = "",
                    aud = "",
                    createdAt = "",
                    email = "",
                    emailConfirmedAt = null,
                    phone = null,
                    confirmationSentAt = null,
                    recoveryRequestSentAt = null,
                    emailChangeRequestSentAt = null,
                    newEmail = null,
                    invitedAt = null,
                    actionLink = null,
                    userMetadata = emptyMap(),
                    appMetadata = emptyMap(),
                    factors = null
                )
            )
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Sign out failed",
                throwable = e
            )
        }
    }
    
    /**
     * Send password reset email
     */
    suspend fun resetPassword(email: String): AuthResult {
        return try {
            auth.resetPasswordForEmail(email)
            AuthResult.Success(
                UserInfo(
                    id = "",
                    aud = "",
                    createdAt = "",
                    email = email,
                    emailConfirmedAt = null,
                    phone = null,
                    confirmationSentAt = null,
                    recoveryRequestSentAt = null,
                    emailChangeRequestSentAt = null,
                    newEmail = null,
                    invitedAt = null,
                    actionLink = null,
                    userMetadata = emptyMap(),
                    appMetadata = emptyMap(),
                    factors = null
                )
            )
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Password reset failed",
                throwable = e
            )
        }
    }
    
    /**
     * Update user password
     */
    suspend fun updatePassword(newPassword: String): AuthResult {
        return try {
            val result = auth.updateUser {
                password = newPassword
            }
            
            AuthResult.Success(result)
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Password update failed",
                throwable = e
            )
        }
    }
    
    /**
     * Update user email
     */
    suspend fun updateEmail(newEmail: String): AuthResult {
        return try {
            val result = auth.updateUser {
                email = newEmail
            }
            
            AuthResult.Success(result)
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Email update failed",
                throwable = e
            )
        }
    }
    
    /**
     * Get current user
     */
    suspend fun getCurrentUser(): UserInfo? {
        return try {
            auth.retrieveUserForCurrentSession()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get current session
     */
    fun getCurrentSession(): UserSession? {
        return try {
            val session = auth.currentSessionOrNull()
            session?.let {
                UserSession(
                    userId = it.user?.id ?: "",
                    email = it.user?.email ?: "",
                    accessToken = it.accessToken,
                    refreshToken = it.refreshToken ?: "",
                    expiresAt = it.expiresAt ?: 0L,
                    isEmailConfirmed = it.user?.emailConfirmedAt != null
                )
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return auth.currentSessionOrNull() != null
    }
    
    /**
     * Refresh current session
     */
    suspend fun refreshSession(): AuthResult {
        return try {
            val session = auth.refreshCurrentSession()
            AuthResult.Success(session.user!!)
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Session refresh failed",
                throwable = e
            )
        }
    }
}