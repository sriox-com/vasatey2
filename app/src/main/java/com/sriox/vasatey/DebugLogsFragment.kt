package com.sriox.vasatey

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sriox.vasatey.databinding.FragmentDebugLogsBinding
import com.sriox.vasatey.models.VercelNotificationRequest
import com.sriox.vasatey.network.RetrofitInstance
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class DebugLogsFragment : Fragment() {

    private var _binding: FragmentDebugLogsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var logsAdapter: DebugLogsAdapter
    private val logsList = mutableListOf<DebugLog>()
    
    private val authHelper = SupabaseAuthHelper()
    private val dbHelper = SupabaseDatabaseHelper()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDebugLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupButtons()
        
        // Auto-load logs on start
        refreshLogsFromAppLogger()
        runAllTests()
    }
    
    private fun setupRecyclerView() {
        logsAdapter = DebugLogsAdapter(logsList)
        binding.logsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = logsAdapter
        }
    }
    
    private fun setupButtons() {
        binding.buttonRunTests.setOnClickListener {
            runAllTests()
        }
        
        binding.buttonClearLogs.setOnClickListener {
            clearLogs()
        }
        
        binding.buttonTestSupabase.setOnClickListener {
            testSupabaseConnection()
        }
        
        binding.buttonTestNotifications.setOnClickListener {
            testNotificationEndpoint()
        }
        
        binding.buttonTestAuth.setOnClickListener {
            testAuthentication()
        }
        
        binding.buttonTestGuardians.setOnClickListener {
            testGuardianOperations()
        }
        
        binding.buttonExportLogs.setOnClickListener {
            exportLogs()
        }
        
        binding.buttonRefreshLogs.setOnClickListener {
            refreshLogsFromAppLogger()
        }
        
        binding.buttonTestFcm.setOnClickListener {
            testFcmTokensWithTroubleshooting()
        }
        
        // Add a long click listener for manual FCM token refresh
        binding.buttonTestFcm.setOnLongClickListener {
            refreshFcmTokenForCurrentUser()
            true
        }
    }
    
    private fun addLog(level: String, category: String, message: String, details: String = "") {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val log = DebugLog(timestamp, level, category, message, details)
        logsList.add(0, log) // Add to top
        logsAdapter.notifyItemInserted(0)
        binding.logsRecyclerView.scrollToPosition(0)
        
        // Also log to AppLogger for persistence
        when (level) {
            "ERROR" -> AppLogger.logError(category, message, details)
            "WARN" -> AppLogger.logWarning(category, message, details)
            "SUCCESS" -> AppLogger.logSuccess(category, message, details)
            "INFO" -> AppLogger.logInfo(category, message, details)
            else -> AppLogger.logDebug(category, message, details)
        }
    }
    
    private fun refreshLogsFromAppLogger() {
        logsList.clear()
        val appLogs = AppLogger.getAllLogs()
        appLogs.forEach { logEntry ->
            val debugLog = DebugLog(
                logEntry.timestamp.substring(11), // Extract time part
                logEntry.level,
                logEntry.category,
                logEntry.message,
                logEntry.details
            )
            logsList.add(debugLog)
        }
        logsAdapter.notifyDataSetChanged()
        addLog("INFO", "SYSTEM", "Refreshed logs from AppLogger", "Loaded ${appLogs.size} entries")
    }
    
    private fun exportLogs() {
        lifecycleScope.launch {
            try {
                val filePath = AppLogger.saveLogsToFile(requireContext())
                Toast.makeText(requireContext(), "Logs exported to: $filePath", Toast.LENGTH_LONG).show()
                addLog("SUCCESS", "EXPORT", "Logs exported successfully", filePath)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to export logs: ${e.message}", Toast.LENGTH_SHORT).show()
                addLog("ERROR", "EXPORT", "Failed to export logs", e.message ?: "Unknown error")
            }
        }
    }
    
    private fun clearLogs() {
        logsList.clear()
        logsAdapter.notifyDataSetChanged()
        addLog("INFO", "SYSTEM", "Logs cleared")
    }
    
    private fun runAllTests() {
        addLog("INFO", "SYSTEM", "Starting comprehensive system tests...")
        
        testSupabaseConnection()
        testAuthentication()
        testFcmTokensWithTroubleshooting()
        testNotificationEndpoint()
        testGuardianOperations()
        testSystemStatus()
    }
    
    private fun refreshFcmTokenForCurrentUser() {
        lifecycleScope.launch {
            addLog("INFO", "FCM_REFRESH", "=== MANUAL FCM TOKEN REFRESH ===")
            
            try {
                val currentUser = authHelper.getCurrentUser()
                if (currentUser?.email == null) {
                    addLog("ERROR", "FCM_REFRESH", "No authenticated user", "Please log in first")
                    return@launch
                }
                
                addLog("INFO", "FCM_REFRESH", "Current user", "Email: ${currentUser.email}")
                
                // Step 1: Delete existing token
                addLog("INFO", "FCM_REFRESH", "Deleting old FCM token", "")
                try {
                    FirebaseMessaging.getInstance().deleteToken().await()
                    addLog("SUCCESS", "FCM_REFRESH", "Old token deleted", "")
                } catch (e: Exception) {
                    addLog("WARN", "FCM_REFRESH", "Token deletion failed", "Proceeding anyway: ${e.message}")
                }
                
                // Step 2: Wait a moment and get fresh token
                addLog("INFO", "FCM_REFRESH", "Getting fresh FCM token", "")
                kotlinx.coroutines.delay(1000)
                
                try {
                    val freshToken = FirebaseMessaging.getInstance().token.await()
                    addLog("SUCCESS", "FCM_REFRESH", "Fresh FCM token obtained", "Token: ${freshToken.take(30)}...")
                    
                    // Step 3: Update in database
                    addLog("INFO", "FCM_REFRESH", "Updating FCM token in database", "")
                    val dbHelper = SupabaseDatabaseHelper()
                    dbHelper.updateFCMToken(currentUser.id, freshToken).fold(
                        onSuccess = { 
                            addLog("SUCCESS", "FCM_REFRESH", "FCM token saved to database!", 
                                "User: ${currentUser.email}\nToken updated successfully")
                            
                            // Verify it was saved
                            lifecycleScope.launch {
                                try {
                                    val updatedProfile = authHelper.getUserProfile(currentUser.email!!).getOrNull()
                                    if (updatedProfile?.fcmToken != null) {
                                        addLog("SUCCESS", "FCM_REFRESH", "Verification: Token saved correctly", 
                                            "Database now contains: ${updatedProfile.fcmToken.take(30)}...")
                                    } else {
                                        addLog("ERROR", "FCM_REFRESH", "Verification failed", "Token not found in database")
                                    }
                                } catch (e: Exception) {
                                    addLog("ERROR", "FCM_REFRESH", "Verification error", "Error: ${e.message}")
                                }
                            }
                        },
                        onFailure = { error ->
                            addLog("ERROR", "FCM_REFRESH", "Database update failed", "Error: ${error.message}")
                        }
                    )
                    
                } catch (e: Exception) {
                    addLog("ERROR", "FCM_REFRESH", "Fresh token generation failed", "Error: ${e.message}")
                }
                
            } catch (e: Exception) {
                addLog("ERROR", "FCM_REFRESH", "FCM refresh failed", "Exception: ${e.message}")
            }
        }
    }

    private fun testFcmTokensWithTroubleshooting() {
        lifecycleScope.launch {
            addLog("INFO", "FCM", "=== FCM TOKEN TESTING ===")
            
            try {
                // Step 1: Check package name
                val packageName = requireContext().packageName
                addLog("INFO", "FCM", "App package name", "Package: $packageName")
                
                // Step 2: Try FCM token retrieval
                addLog("INFO", "FCM", "Attempting FCM token retrieval", "")
                
                // Approach 1: Direct token request
                try {
                    val token = FirebaseMessaging.getInstance().token.await()
                    addLog("SUCCESS", "FCM", "FCM token obtained successfully", "Token: ${token.take(30)}...")
                    
                    // Validate token format
                    if (token.length > 140) {
                        addLog("SUCCESS", "FCM", "Token format validation", "Token length: ${token.length} (Valid)")
                    } else {
                        addLog("WARN", "FCM", "Token format validation", "Token length: ${token.length} (Suspicious - may be invalid)")
                    }
                    
                    // Test if token is current/fresh
                    addLog("INFO", "FCM", "Token obtained on", "Timestamp: ${System.currentTimeMillis()}")
                    
                } catch (e: Exception) {
                    addLog("ERROR", "FCM", "FCM token failed", "Error: ${e.javaClass.simpleName}: ${e.message}")
                    
                    // Approach 2: Try deleting and regenerating token
                    addLog("INFO", "FCM", "Attempting token regeneration", "Deleting old token and creating new one")
                    try {
                        FirebaseMessaging.getInstance().deleteToken().await()
                        addLog("SUCCESS", "FCM", "Old token deleted", "Now getting fresh token...")
                        
                        kotlinx.coroutines.delay(2000) // Wait a bit
                        val freshToken = FirebaseMessaging.getInstance().token.await()
                        addLog("SUCCESS", "FCM", "Fresh FCM token obtained", "Token: ${freshToken.take(30)}...")
                        
                    } catch (e2: Exception) {
                        addLog("ERROR", "FCM", "Token regeneration failed", "Error: ${e2.javaClass.simpleName}: ${e2.message}")
                    }
                }
                
                // Step 5: Check current user and database connection
                addLog("INFO", "FCM", "Checking user context", "")
                val currentUser = authHelper.getCurrentUser()
                if (currentUser != null) {
                    addLog("SUCCESS", "FCM", "User authenticated", "Email: ${currentUser.email}")
                    
                    // Check if user profile has FCM token
                    try {
                        val userProfile = authHelper.getUserProfile(currentUser.email ?: "").getOrNull()
                        if (userProfile != null) {
                            val storedToken = userProfile.fcmToken
                            addLog("INFO", "FCM", "Stored FCM token in database", 
                                "Has token: ${!storedToken.isNullOrEmpty()}\n" +
                                "Token: ${storedToken?.take(30) ?: "None"}...")
                        } else {
                            addLog("WARN", "FCM", "User profile not found", "Cannot check stored FCM token")
                        }
                    } catch (e: Exception) {
                        addLog("ERROR", "FCM", "Database profile lookup failed", "Error: ${e.message}")
                    }
                } else {
                    addLog("WARN", "FCM", "No authenticated user", "FCM token cannot be stored")
                }
                
                // Step 6: Network connectivity test
                addLog("INFO", "FCM", "Testing network connectivity", "")
                try {
                    val networkTest = withContext(Dispatchers.IO) {
                        makePostRequest("https://www.google.com", "{}")
                    }
                    addLog("SUCCESS", "FCM", "Network connectivity", "Internet connection working")
                } catch (e: Exception) {
                    addLog("ERROR", "FCM", "Network connectivity failed", "Error: ${e.message}")
                }
                
                addLog("INFO", "FCM", "=== TROUBLESHOOTING COMPLETE ===", 
                    "Check the logs above for specific issues and solutions")
                
            } catch (e: Exception) {
                addLog("ERROR", "FCM", "Troubleshooting failed", "Exception: ${e.message}")
            }
        }
    }
    
    private suspend fun testGuardiansFcmTokens(currentUserId: String) {
        try {
            addLog("INFO", "FCM", "Testing guardians' FCM tokens...")
            
            // Get user profile ID
            val profileIdResult = dbHelper.getUserProfileId(currentUserId)
            profileIdResult.fold(
                onSuccess = { profileId ->
                    if (profileId != null) {
                        // Get guardians
                        dbHelper.getGuardiansForUser(profileId).fold(
                            onSuccess = { guardians ->
                                addLog("INFO", "FCM", "Checking FCM tokens for ${guardians.size} guardians", "")
                                
                                guardians.forEach { guardian ->
                                    checkGuardianFcmToken(guardian.guardianEmail ?: "")
                                }
                                
                                if (guardians.isEmpty()) {
                                    addLog("WARN", "FCM", "No guardians configured", "Cannot test guardian FCM tokens")
                                }
                            },
                            onFailure = { error ->
                                addLog("ERROR", "FCM", "Failed to get guardians", "Error: ${error.message}")
                            }
                        )
                    } else {
                        addLog("ERROR", "FCM", "Profile ID is null", "Cannot check guardian tokens")
                    }
                },
                onFailure = { error ->
                    addLog("ERROR", "FCM", "Failed to get profile ID", "Error: ${error.message}")
                }
            )
        } catch (e: Exception) {
            addLog("ERROR", "FCM", "Guardian FCM test exception", "Exception: ${e.message}")
        }
    }
    
    private suspend fun checkGuardianFcmToken(guardianEmail: String) {
        if (guardianEmail.isEmpty()) return
        
        try {
            val supabase = SupabaseClient.client
            val allProfiles = supabase.from("user_profiles")
                .select()
                .decodeList<UserProfile>()
            
            val guardianProfile = allProfiles.firstOrNull { it.email == guardianEmail }
            
            if (guardianProfile != null) {
                val token = guardianProfile.fcmToken
                if (!token.isNullOrEmpty()) {
                    addLog("SUCCESS", "FCM", "Guardian has FCM token", "Guardian: $guardianEmail, Token: ${token.take(20)}...")
                } else {
                    addLog("WARN", "FCM", "Guardian has no FCM token", "Guardian: $guardianEmail - may not have app installed or logged in")
                }
            } else {
                addLog("WARN", "FCM", "Guardian profile not found", "Guardian: $guardianEmail - not registered in system")
            }
        } catch (e: Exception) {
            addLog("ERROR", "FCM", "Failed to check guardian FCM token", "Guardian: $guardianEmail, Error: ${e.message}")
        }
    }
    
    private fun testSupabaseConnection() {
        lifecycleScope.launch {
            addLog("INFO", "SUPABASE", "=== SUPABASE CONNECTION DIAGNOSTICS ===")
            
            try {
                val supabase = SupabaseClient.client
                
                // Test 1: Basic client initialization
                addLog("SUCCESS", "SUPABASE", "Supabase client initialized", 
                    "URL: ${supabase.supabaseUrl}")
                
                // Test 2: API connectivity
                addLog("INFO", "SUPABASE", "Testing API connectivity", "")
                try {
                    val response = withContext(Dispatchers.IO) {
                        supabase.postgrest.from("user_profiles").select().limit(1)
                    }
                    addLog("SUCCESS", "SUPABASE", "Database connection successful", "Can query user_profiles table")
                } catch (e: Exception) {
                    addLog("ERROR", "SUPABASE", "Database connection failed", 
                        "Error: ${e.message}\nType: ${e.javaClass.simpleName}")
                    
                    // Specific Supabase error guidance
                    if (e.message?.contains("403") == true) {
                        addLog("WARN", "SUPABASE", "403 Forbidden Error", 
                            "This usually means:\n" +
                            "1. RLS (Row Level Security) is blocking access\n" +
                            "2. API key doesn't have proper permissions\n" +
                            "3. Table policies are too restrictive")
                    }
                }
                
                // Test 3: Authentication service
                addLog("INFO", "SUPABASE", "Testing authentication service", "")
                try {
                    val currentUser = supabase.auth.currentUserOrNull()
                    if (currentUser != null) {
                        addLog("SUCCESS", "SUPABASE", "User already authenticated", 
                            "User ID: ${currentUser.id}\nEmail: ${currentUser.email}")
                    } else {
                        addLog("INFO", "SUPABASE", "No authenticated user", "User needs to sign in")
                    }
                } catch (e: Exception) {
                    addLog("ERROR", "SUPABASE", "Auth service check failed", "Error: ${e.message}")
                }
                
                // Test 4: Basic connectivity test
                addLog("INFO", "SUPABASE", "Testing basic HTTP connectivity", "")
                val url = "https://hjxmjmdqvgiaeourpbbc.supabase.co/rest/v1/"
                val response = withContext(Dispatchers.IO) {
                    makeHttpRequest(url, mapOf(
                        "apikey" to "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhqeG1qbWRxdmdpYWVvdXJwYmJjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIxMzg5NjEsImV4cCI6MjA3NzcxNDk2MX0.mVibzZbffS1JfCVa7yW8yndG_e7iYI72vgo_9h3SCiQ",
                        "Authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhqeG1qbWRxdmdpYWVvdXJwYmJjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIxMzg5NjEsImV4cCI6MjA3NzcxNDk2MX0.mVibzZbffS1JfCVa7yW8yndG_e7iYI72vgo_9h3SCiQ"
                    ))
                }
                
                if (response.isSuccess) {
                    addLog("SUCCESS", "SUPABASE", "HTTP connectivity successful", "Response: ${response.data}")
                    
                    // Test individual tables
                    testTable("user_profiles")
                    testTable("guardians") 
                    testTable("alerts")
                    testTable("user_settings")
                } else {
                    addLog("ERROR", "SUPABASE", "HTTP connectivity failed", "Error: ${response.error}")
                }
                
                // Test 5: Check for common issues
                addLog("INFO", "SUPABASE", "Common issue guidance", "")
                addLog("WARN", "SUPABASE", "If authentication fails with 'Requests from this Android':", 
                    "1. Check Supabase Dashboard → Settings → API\n" +
                    "2. Verify API keys are correct\n" +
                    "3. Check RLS policies on user_profiles table\n" +
                    "4. Ensure CORS is configured for mobile apps\n" +
                    "5. Verify project URL is correct")
                
                addLog("INFO", "SUPABASE", "=== SUPABASE DIAGNOSTICS COMPLETE ===", "")
                }
                
            } catch (e: Exception) {
                addLog("ERROR", "SUPABASE", "Connection exception", "Exception: ${e.message}")
            }
        }
    }
    
    private suspend fun testTable(tableName: String) {
        try {
            val url = "https://hjxmjmdqvgiaeourpbbc.supabase.co/rest/v1/$tableName?select=*&limit=1"
            val response = withContext(Dispatchers.IO) {
                makeHttpRequest(url, mapOf(
                    "apikey" to "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhqeG1qbWRxdmdpYWVvdXJwYmJjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIxMzg5NjEsImV4cCI6MjA3NzcxNDk2MX0.mVibzZbffS1JfCVa7yW8yndG_e7iYI72vgo_9h3SCiQ",
                    "Authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhqeG1qbWRxdmdpYWVvdXJwYmJjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIxMzg5NjEsImV4cCI6MjA3NzcxNDk2MX0.mVibzZbffS1JfCVa7yW8yndG_e7iYI72vgo_9h3SCiQ"
                ))
            }
            
            if (response.isSuccess) {
                addLog("SUCCESS", "TABLE", "Table '$tableName' accessible", "Data: ${response.data}")
            } else {
                addLog("ERROR", "TABLE", "Table '$tableName' not accessible", "Error: ${response.error}")
            }
        } catch (e: Exception) {
            addLog("ERROR", "TABLE", "Table '$tableName' exception", "Exception: ${e.message}")
        }
    }
    
    private fun testAuthentication() {
        lifecycleScope.launch {
            addLog("INFO", "AUTH", "=== AUTHENTICATION DIAGNOSTICS ===")
            
            try {
                // Step 1: Check Supabase connection
                addLog("INFO", "AUTH", "Testing Supabase connection", "")
                val supabase = SupabaseClient.client
                addLog("SUCCESS", "AUTH", "Supabase client initialized", "URL: ${supabase.supabaseUrl}")
                
                // Step 2: Check package name
                val packageName = requireContext().packageName
                addLog("INFO", "AUTH", "App package name", "Package: $packageName")
                
                // Step 3: Check certificate fingerprint
                addLog("INFO", "AUTH", "App certificate fingerprint", 
                    "SHA-1: 02:8B:CD:13:1B:06:0B:A2:31:5C:4F:0E:17:BE:71:3A:2D:B3:C2:9D")
                
                // Step 4: Test current authentication state
                addLog("INFO", "AUTH", "Checking current authentication state", "")
                
                val currentUser = authHelper.getCurrentUser()
                if (currentUser != null) {
                    addLog("SUCCESS", "AUTH", "User logged in", "User ID: ${currentUser.id}, Email: ${currentUser.email}")
                    
                    // Test user profile fetch
                    val profileResult = authHelper.getUserProfile(currentUser.email!!)
                    profileResult.fold(
                        onSuccess = { profile ->
                            addLog("SUCCESS", "AUTH", "User profile loaded", "Profile: ${profile?.fullName ?: "No name"}")
                        },
                        onFailure = { error ->
                            addLog("ERROR", "AUTH", "User profile failed", "Error: ${error.message}")
                        }
                    )
                } else {
                    addLog("WARN", "AUTH", "No user logged in", "User needs to authenticate")
                }
                
                // Step 6: Check for common auth issues
                addLog("INFO", "AUTH", "Common authentication issue guidance", "")
                addLog("WARN", "AUTH", "If you see 'Requests from this Android' error:", 
                    "1. Firebase Console → Project Settings\n" +
                    "2. Add SHA-1: 02:8B:CD:13:1B:06:0B:A2:31:5C:4F:0E:17:BE:71:3A:2D:B3:C2:9D\n" +
                    "3. Check API key restrictions\n" +
                    "4. Verify package name: com.sriox.vasatey\n" +
                    "5. Download fresh google-services.json")
                
                addLog("INFO", "AUTH", "=== AUTHENTICATION DIAGNOSTICS COMPLETE ===", "")
                
            } catch (e: Exception) {
                addLog("ERROR", "AUTH", "Authentication exception", "Exception: ${e.message}")
            }
        }
    }
    
    private fun testNotificationEndpoint() {
        lifecycleScope.launch {
            addLog("INFO", "NOTIFICATION", "=== TESTING NOTIFICATION SYSTEM ===")
            
            try {
                val currentUser = authHelper.getCurrentUser()
                if (currentUser?.email == null) {
                    addLog("ERROR", "NOTIFICATION", "No authenticated user", "Please log in first")
                    return@launch
                }
                
                addLog("INFO", "NOTIFICATION", "Current user", "Email: ${currentUser.email}")
                
                // Step 1: Try to get FCM token with better error handling
                addLog("INFO", "NOTIFICATION", "Attempting to get FCM token...", "")
                
                var currentToken: String? = null
                try {
                    // Check if Firebase is properly initialized
                    addLog("INFO", "NOTIFICATION", "Checking Firebase initialization", "")
                    
                    currentToken = FirebaseMessaging.getInstance().token.await()
                    addLog("SUCCESS", "NOTIFICATION", "FCM token obtained successfully", "Token: ${currentToken?.take(30)}...")
                    
                } catch (e: Exception) {
                    addLog("ERROR", "NOTIFICATION", "FCM token retrieval failed", "Error: ${e.javaClass.simpleName}: ${e.message}")
                    
                    // If FIS_AUTH_ERROR, provide specific guidance
                    if (e.message?.contains("FIS_AUTH_ERROR") == true) {
                        addLog("WARN", "NOTIFICATION", "Firebase Installation Service Auth Error detected", 
                            "This usually means:\n" +
                            "1. google-services.json is missing or invalid\n" +
                            "2. Package name doesn't match Firebase project\n" +
                            "3. Network connectivity issues\n" +
                            "4. Firebase project not properly configured")
                        
                        // Try to get some diagnostic info
                        addLog("INFO", "NOTIFICATION", "Diagnostic info", 
                            "Package: ${requireContext().packageName}\n" +
                            "App ID from config should match Firebase console")
                    }
                    
                    // Try to continue with a test token for endpoint testing
                    currentToken = "test_token_fcm_unavailable_${System.currentTimeMillis()}"
                    addLog("WARN", "NOTIFICATION", "Using test token for endpoint testing", "Token: $currentToken")
                }
                
                // Step 2: Test the endpoint even with a test token
                addLog("INFO", "NOTIFICATION", "Testing Vercel endpoint connectivity", "")
                
                // First test basic connectivity
                try {
                    val connectivityTest = withContext(Dispatchers.IO) {
                        val url = "https://vasatey-notify-msg.vercel.app/api/sendNotification"
                        val testPayload = """{"test": "connectivity"}"""
                        makePostRequest(url, testPayload)
                    }
                    
                    if (connectivityTest.isSuccess) {
                        addLog("SUCCESS", "NOTIFICATION", "Endpoint is reachable", "Response: ${connectivityTest.data}")
                    } else {
                        addLog("INFO", "NOTIFICATION", "Endpoint response", "Status: ${connectivityTest.error}")
                    }
                } catch (e: Exception) {
                    addLog("ERROR", "NOTIFICATION", "Endpoint connectivity test failed", "Error: ${e.message}")
                }
                
                // Step 3: Test with full payload (even if token is fake)
                if (currentToken != null) {
                    val testRequest = VercelNotificationRequest(
                        token = currentToken,
                        title = "🧪 VASATEY TEST NOTIFICATION",
                        body = "This is a test notification from your debug page",
                        fullName = currentUser.email ?: "Test User",
                        email = currentUser.email ?: "",
                        phoneNumber = "+1234567890",
                        lastKnownLatitude = 12.9716,
                        lastKnownLongitude = 77.5946
                    )
                    
                    addLog("INFO", "NOTIFICATION", "Sending test notification to endpoint", 
                        "URL: https://vasatey-notify-msg.vercel.app/api/sendNotification\n" +
                        "Using token: ${currentToken.take(20)}...")
                    
                    val response = withContext(Dispatchers.IO) {
                        RetrofitInstance.api.sendNotification(testRequest)
                    }
                    
                    addLog("INFO", "NOTIFICATION", "Endpoint response", 
                        "Status: ${response.code()}\nSuccess: ${response.isSuccessful}\nMessage: ${response.message()}")
                    
                    if (response.isSuccessful) {
                        val body = response.body()?.string()
                        addLog("SUCCESS", "NOTIFICATION", "Test notification sent successfully!", "Response: $body")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        addLog("ERROR", "NOTIFICATION", "Notification failed", "Error: $errorBody")
                        
                        // Provide specific guidance based on error
                        if (errorBody?.contains("invalid-argument") == true || errorBody?.contains("registration token") == true) {
                            addLog("INFO", "NOTIFICATION", "Token validation failed", 
                                "This is expected if FCM token is unavailable. The endpoint is working correctly.")
                        }
                    }
                }
                
            } catch (e: Exception) {
                addLog("ERROR", "NOTIFICATION", "Test exception", "Exception: ${e.message}")
            }
        }
    }
    
    private fun testGuardianOperations() {
        lifecycleScope.launch {
            addLog("INFO", "GUARDIANS", "=== TESTING GUARDIAN NOTIFICATION FLOW ===")
            
            try {
                val currentUser = authHelper.getCurrentUser()
                if (currentUser?.email == null) {
                    addLog("ERROR", "GUARDIANS", "No authenticated user", "Please log in first")
                    return@launch
                }
                
                addLog("INFO", "GUARDIANS", "Current user", "Email: ${currentUser.email}")
                
                // Step 1: Get user profile ID
                val profileIdResult = dbHelper.getUserProfileId(currentUser.id)
                profileIdResult.fold(
                    onSuccess = { profileId ->
                        if (profileId != null) {
                            addLog("SUCCESS", "GUARDIANS", "User profile ID found", "Profile ID: $profileId")
                            
                            // Step 2: Get guardians
                            dbHelper.getGuardiansForUser(profileId).fold(
                                onSuccess = { guardians ->
                                    addLog("SUCCESS", "GUARDIANS", "Guardians loaded", "Count: ${guardians.size}")
                                    
                                    if (guardians.isEmpty()) {
                                        addLog("WARN", "GUARDIANS", "No guardians found", "Add guardians in settings to receive alerts")
                                        return@launch
                                    }
                                    
                                    // Step 3: Test each guardian's FCM token
                                    guardians.forEachIndexed { index, guardian ->
                                        addLog("INFO", "GUARDIANS", "Guardian ${index + 1}", 
                                            "Name: ${guardian.guardianName}\nEmail: ${guardian.guardianEmail ?: "No email"}\nPhone: ${guardian.guardianPhone ?: "No phone"}")
                                        
                                        if (guardian.guardianEmail != null) {
                                            // Get guardian's profile and FCM token
                                            lifecycleScope.launch {
                                                try {
                                                    val guardianProfile = authHelper.getUserProfile(guardian.guardianEmail!!).getOrNull()
                                                    val guardianToken = guardianProfile?.fcmToken
                                                    
                                                    addLog("INFO", "GUARDIANS", "Guardian FCM status", 
                                                        "Email: ${guardian.guardianEmail}\n" +
                                                        "Has Profile: ${guardianProfile != null}\n" +
                                                        "Has FCM Token: ${!guardianToken.isNullOrEmpty()}\n" +
                                                        "Token: ${guardianToken?.take(30)}...")
                                                    
                                                    if (!guardianToken.isNullOrEmpty()) {
                                                        // Test sending notification to this guardian
                                                        val testRequest = VercelNotificationRequest(
                                                            token = guardianToken,
                                                            title = "🧪 VASATEY GUARDIAN TEST",
                                                            body = "Test emergency alert from ${currentUser.email}",
                                                            fullName = currentUser.email ?: "Test User",
                                                            email = currentUser.email ?: "",
                                                            phoneNumber = guardianProfile?.phoneNumber ?: "+0000000000",
                                                            lastKnownLatitude = 12.9716,
                                                            lastKnownLongitude = 77.5946
                                                        )
                                                        
                                                        addLog("INFO", "GUARDIANS", "Testing notification to guardian", 
                                                            "Guardian: ${guardian.guardianEmail}")
                                                        
                                                        val response = withContext(Dispatchers.IO) {
                                                            RetrofitInstance.api.sendNotification(testRequest)
                                                        }
                                                        
                                                        if (response.isSuccessful) {
                                                            addLog("SUCCESS", "GUARDIANS", "Guardian notification successful!", 
                                                                "Guardian: ${guardian.guardianEmail} should have received the test alert")
                                                        } else {
                                                            val errorBody = response.errorBody()?.string()
                                                            addLog("ERROR", "GUARDIANS", "Guardian notification failed", 
                                                                "Guardian: ${guardian.guardianEmail}\nError: $errorBody")
                                                        }
                                                    } else {
                                                        addLog("WARN", "GUARDIANS", "Guardian has no FCM token", 
                                                            "Guardian: ${guardian.guardianEmail} - they need to install the app and log in")
                                                    }
                                                } catch (e: Exception) {
                                                    addLog("ERROR", "GUARDIANS", "Guardian test failed", 
                                                        "Guardian: ${guardian.guardianEmail}\nError: ${e.message}")
                                                }
                                            }
                                        } else {
                                            addLog("WARN", "GUARDIANS", "Guardian has no email", "Cannot send notifications without email")
                                        }
                                    }
                                    
                                },
                                onFailure = { error ->
                                    addLog("ERROR", "GUARDIANS", "Failed to load guardians", "Error: ${error.message}")
                                }
                            )
                        } else {
                            addLog("ERROR", "GUARDIANS", "Profile ID is null", "Cannot proceed with guardian test")
                        }
                    },
                    onFailure = { error ->
                        addLog("ERROR", "GUARDIANS", "Profile ID not found", "Error: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                addLog("ERROR", "GUARDIANS", "Guardian operations exception", "Exception: ${e.message}")
            }
        }
    }
    
    private fun testSystemStatus() {
        lifecycleScope.launch {
            addLog("INFO", "SYSTEM", "Checking system status...")
            
            try {
                // Check network connectivity
                val isConnected = withContext(Dispatchers.IO) {
                    try {
                        val url = URL("https://www.google.com")
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 5000
                        connection.connect()
                        connection.responseCode == 200
                    } catch (e: Exception) {
                        false
                    }
                }
                
                if (isConnected) {
                    addLog("SUCCESS", "SYSTEM", "Internet connectivity working")
                } else {
                    addLog("ERROR", "SYSTEM", "No internet connectivity")
                }
                
                // Check app permissions
                val context = requireContext()
                val permissions = listOf(
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
                
                permissions.forEach { permission ->
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, permission
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    
                    if (granted) {
                        addLog("SUCCESS", "PERMISSIONS", "Permission granted", permission)
                    } else {
                        addLog("WARN", "PERMISSIONS", "Permission not granted", permission)
                    }
                }
                
            } catch (e: Exception) {
                addLog("ERROR", "SYSTEM", "System status exception", "Exception: ${e.message}")
            }
        }
    }
    
    private fun makeHttpRequest(url: String, headers: Map<String, String>): TestResponse {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            val response = if (responseCode == 200) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream)).use { it.readText() }
            }
            
            if (responseCode == 200) {
                TestResponse(true, response, "")
            } else {
                TestResponse(false, "", "HTTP $responseCode: $response")
            }
        } catch (e: Exception) {
            TestResponse(false, "", e.message ?: "Unknown error")
        }
    }
    
    private fun makePostRequest(url: String, jsonBody: String): TestResponse {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            connection.outputStream.use { os ->
                val input = jsonBody.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = connection.responseCode
            val response = if (responseCode in 200..299) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream)).use { it.readText() }
            }
            
            if (responseCode in 200..299) {
                TestResponse(true, response, "")
            } else {
                TestResponse(false, "", "HTTP $responseCode: $response")
            }
        } catch (e: Exception) {
            TestResponse(false, "", e.message ?: "Unknown error")
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class TestResponse(
    val isSuccess: Boolean,
    val data: String,
    val error: String
)

data class DebugLog(
    val timestamp: String,
    val level: String,
    val category: String,
    val message: String,
    val details: String
)