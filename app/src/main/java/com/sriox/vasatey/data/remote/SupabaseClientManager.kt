package com.sriox.vasatey.data.remote

import android.content.Context
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import kotlinx.serialization.json.Json
import java.io.InputStream

/**
 * Supabase client configuration and management
 * Handles initialization and provides access to Supabase services
 */
class SupabaseClientManager private constructor() {
    
    companion object {
        private const val TAG = "SupabaseClientManager"
        private const val CONFIG_FILE = "supabase-config.json"
        
        @Volatile
        private var INSTANCE: SupabaseClientManager? = null
        
        fun getInstance(): SupabaseClientManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SupabaseClientManager().also { INSTANCE = it }
            }
        }
    }
    
    private var _client: SupabaseClient? = null
    
    val client: SupabaseClient
        get() = _client ?: throw IllegalStateException("Supabase client not initialized. Call initialize() first.")
    
    val auth: Auth
        get() = client.auth
    
    val database: Postgrest
        get() = client.postgrest
    
    val realtime: Realtime
        get() = client.realtime
    
    /**
     * Configuration data class for Supabase
     */
    @kotlinx.serialization.Serializable
    data class SupabaseConfig(
        val url: String,
        val anonKey: String,
        val serviceRoleKey: String? = null,
        val enableRealtime: Boolean = true,
        val enableAuth: Boolean = true,
        val schema: String = "public"
    )
    
    /**
     * Initialize Supabase client with configuration from assets
     */
    fun initialize(context: Context): Result<Unit> {
        return try {
            val config = loadConfig(context)
            
            _client = createSupabaseClient(
                supabaseUrl = config.url,
                supabaseKey = config.anonKey
            ) {
                install(Auth) {
                    flowType = io.github.jan.supabase.gotrue.FlowType.PKCE
                    scheme = "com.sriox.vasatey"
                    host = "supabase.co"
                }
                
                install(Postgrest) {
                    schema = config.schema
                }
                
                if (config.enableRealtime) {
                    install(Realtime)
                }
                
                defaultSerializer = Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
            }
            
            Log.i(TAG, "Supabase client initialized successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Supabase client", e)
            Result.failure(e)
        }
    }
    
    /**
     * Load configuration from assets/supabase-config.json
     */
    private fun loadConfig(context: Context): SupabaseConfig {
        return try {
            val inputStream: InputStream = context.assets.open(CONFIG_FILE)
            val configJson = inputStream.bufferedReader().use { it.readText() }
            Json.decodeFromString<SupabaseConfig>(configJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Supabase config from assets", e)
            // Fallback to default configuration (will need to be updated with actual values)
            SupabaseConfig(
                url = "https://your-project.supabase.co",
                anonKey = "your-anon-key"
            )
        }
    }
    
    /**
     * Check if client is initialized
     */
    fun isInitialized(): Boolean = _client != null
    
    /**
     * Get current user session
     */
    suspend fun getCurrentSession() = auth.currentSessionOrNull()
    
    /**
     * Check if user is authenticated
     */
    suspend fun isAuthenticated(): Boolean = getCurrentSession() != null
    
    /**
     * Sign out current user
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Log.i(TAG, "User signed out successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign out user", e)
            Result.failure(e)
        }
    }
    
    /**
     * Reset client instance (for testing or re-initialization)
     */
    fun reset() {
        _client = null
        INSTANCE = null
    }
}

/**
 * Authentication result wrapper
 */
sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val exception: Throwable) : AuthResult<Nothing>()
    object Loading : AuthResult<Nothing>()
}

/**
 * Authentication error types
 */
enum class AuthError(val message: String) {
    INVALID_EMAIL("Please enter a valid email address"),
    WEAK_PASSWORD("Password must be at least 8 characters long"),
    USER_NOT_FOUND("No account found with this email"),
    INVALID_CREDENTIALS("Invalid email or password"),
    EMAIL_ALREADY_EXISTS("An account with this email already exists"),
    NETWORK_ERROR("Network connection error. Please check your internet connection"),
    SERVER_ERROR("Server error. Please try again later"),
    RATE_LIMITED("Too many attempts. Please try again later"),
    EMAIL_NOT_CONFIRMED("Please check your email and confirm your account"),
    UNKNOWN_ERROR("An unexpected error occurred");
    
    companion object {
        fun fromException(exception: Throwable): AuthError {
            val message = exception.message?.lowercase() ?: ""
            return when {
                message.contains("invalid_credentials") -> INVALID_CREDENTIALS
                message.contains("user_not_found") -> USER_NOT_FOUND
                message.contains("email_address_invalid") -> INVALID_EMAIL
                message.contains("weak_password") -> WEAK_PASSWORD
                message.contains("email_address_not_authorized") -> EMAIL_ALREADY_EXISTS
                message.contains("email_not_confirmed") -> EMAIL_NOT_CONFIRMED
                message.contains("rate_limit") -> RATE_LIMITED
                message.contains("network") || message.contains("timeout") -> NETWORK_ERROR
                message.contains("server") || message.contains("500") -> SERVER_ERROR
                else -> UNKNOWN_ERROR
            }
        }
    }
}