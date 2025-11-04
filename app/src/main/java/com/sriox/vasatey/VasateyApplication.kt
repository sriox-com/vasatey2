package com.sriox.vasatey

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.sriox.vasatey.data.remote.SupabaseClientManager
import com.sriox.vasatey.service.FCMTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Main application class for Vasatey emergency detection app
 * Handles app-wide initialization and configuration
 */
class VasateyApplication : Application() {
    
    companion object {
        @Volatile
        private var INSTANCE: VasateyApplication? = null
        
        fun getInstance(): VasateyApplication {
            return INSTANCE ?: throw IllegalStateException("Application not initialized")
        }
    }
    
    // Application scope for background operations
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        
        Log.d(TAG, "Vasatey Application starting...")
        
        // Initialize core services
        initializeServices()
        
        // Setup background initialization
        applicationScope.launch {
            initializeBackgroundServices()
        }
        
        Log.i(TAG, "Vasatey Application initialized successfully")
    }
    
    /**
     * Initialize core services
     */
    private fun initializeServices() {
        try {
            // Initialize Firebase
            FirebaseApp.initializeApp(this)
            
            Log.d(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize core services", e)
        }
    }
    
    /**
     * Initialize background services
     */
    private suspend fun initializeBackgroundServices() {
        try {
            // Initialize Supabase client
            val supabaseResult = SupabaseClientManager.getInstance().initialize(this@VasateyApplication)
            if (supabaseResult.isSuccess) {
                Log.i(TAG, "Supabase client initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize Supabase client", supabaseResult.exceptionOrNull())
            }
            
            // Initialize FCM token if needed
            val tokenManager = FCMTokenManager.getInstance()
            if (tokenManager.shouldRefreshToken(this@VasateyApplication)) {
                val tokenResult = tokenManager.getCurrentToken(this@VasateyApplication)
                if (tokenResult.isSuccess) {
                    Log.d(TAG, "FCM token refreshed successfully")
                } else {
                    Log.w(TAG, "Failed to refresh FCM token", tokenResult.exceptionOrNull())
                }
            }
            
            Log.d(TAG, "Background services initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize background services", e)
        }
    }
    
    /**
     * Get application context safely
     */
    fun getAppContext(): Context = applicationContext
    
    /**
     * Check if app is properly initialized
     */
    fun isInitialized(): Boolean {
        return ::supabaseClient.isInitialized && ::fcmTokenManager.isInitialized
    }
    
    override fun onTerminate() {
        super.onTerminate()
        
        // Clean up resources
        try {
            supabaseClient.close()
            android.util.Log.d("VasateyApp", "App terminated and resources cleaned up")
        } catch (e: Exception) {
            android.util.Log.e("VasateyApp", "Error during app termination", e)
        }
    }
}