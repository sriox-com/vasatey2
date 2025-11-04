package com.sriox.vasatey.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.sriox.vasatey.R
import com.sriox.vasatey.VasateyApplication
import com.sriox.vasatey.data.supabase.AuthState
import com.sriox.vasatey.data.supabase.AuthenticationHelper
import com.sriox.vasatey.services.voice.VoiceDetectionManager
import kotlinx.coroutines.launch

/**
 * Main activity for Vasatey emergency detection app
 * Provides dashboard UI and coordinates app services
 */
class MainActivity : AppCompatActivity() {
    
    // Voice detection manager
    private lateinit var voiceDetectionManager: VoiceDetectionManager
    
    // Authentication helper
    private lateinit var authHelper: AuthenticationHelper
    
    // UI components
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusText: MaterialTextView
    private lateinit var voiceToggleButton: MaterialButton
    private lateinit var settingsButton: MaterialButton
    private lateinit var emergencyButton: MaterialButton
    
    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize components
        initializeComponents()
        
        // Setup UI
        setupUI()
        
        // Check permissions
        checkAndRequestPermissions()
        
        // Handle notification intent
        handleNotificationIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceDetectionManager.cleanup()
    }
    
    /**
     * Initialize app components
     */
    private fun initializeComponents() {
        val app = application as VasateyApplication
        
        // Initialize voice detection manager
        voiceDetectionManager = VoiceDetectionManager(this)
        voiceDetectionManager.initialize()
        
        // Initialize authentication helper
        authHelper = AuthenticationHelper(app.supabaseClient)
    }
    
    /**
     * Setup UI components and listeners
     */
    private fun setupUI() {
        // Initialize views
        statusCard = findViewById(R.id.statusCard)
        statusText = findViewById(R.id.statusText)
        voiceToggleButton = findViewById(R.id.voiceToggleButton)
        settingsButton = findViewById(R.id.settingsButton)
        emergencyButton = findViewById(R.id.emergencyButton)
        
        // Setup button listeners
        voiceToggleButton.setOnClickListener {
            toggleVoiceDetection()
        }
        
        settingsButton.setOnClickListener {
            openSettings()
        }
        
        emergencyButton.setOnClickListener {
            triggerManualEmergency()
        }
        
        // Observe voice detection state
        lifecycleScope.launch {
            voiceDetectionManager.isListening.collect { isListening ->
                updateVoiceDetectionUI(isListening)
            }
        }
        
        // Observe authentication state
        lifecycleScope.launch {
            authHelper.getAuthState().collect { authState ->
                updateAuthenticationUI(authState)
            }
        }
    }
    
    /**
     * Check and request required permissions
     */
    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf<String>().apply {
            // Audio recording permission
            add(Manifest.permission.RECORD_AUDIO)
            
            // Location permissions
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            
            // Notification permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            
            // Background location (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            
            // Phone permissions
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.SEND_SMS)
        }
        
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            onPermissionsGranted()
        }
    }
    
    /**
     * Handle permission request results
     */
    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        val deniedPermissions = permissions.filter { !it.value }.keys
        
        if (deniedPermissions.isEmpty()) {
            onPermissionsGranted()
        } else {
            handleDeniedPermissions(deniedPermissions)
        }
    }
    
    /**
     * Called when all required permissions are granted
     */
    private fun onPermissionsGranted() {
        Toast.makeText(this, "Permissions granted - Vasatey is ready", Toast.LENGTH_SHORT).show()
        
        // Start voice detection if enabled
        if (voiceDetectionManager.isVoiceDetectionAvailable()) {
            voiceDetectionManager.startVoiceDetection()
        }
    }
    
    /**
     * Handle denied permissions
     */
    private fun handleDeniedPermissions(deniedPermissions: Set<String>) {
        val criticalPermissions = setOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        
        val hasCriticalDenied = deniedPermissions.any { it in criticalPermissions }
        
        if (hasCriticalDenied) {
            Toast.makeText(
                this,
                "Critical permissions denied - Some features may not work",
                Toast.LENGTH_LONG
            ).show()
        }
        
        updateUIForMissingPermissions(deniedPermissions)
    }
    
    /**
     * Toggle voice detection on/off
     */
    private fun toggleVoiceDetection() {
        if (voiceDetectionManager.isVoiceDetectionAvailable()) {
            voiceDetectionManager.toggleVoiceDetection()
        } else {
            Toast.makeText(this, "Voice detection not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Open settings activity
     */
    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * Trigger manual emergency
     */
    private fun triggerManualEmergency() {
        // TODO: Implement manual emergency trigger
        Toast.makeText(this, "Manual emergency trigger - Not implemented yet", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Update voice detection UI state
     */
    private fun updateVoiceDetectionUI(isListening: Boolean) {
        runOnUiThread {
            if (isListening) {
                voiceToggleButton.text = "Stop Listening"
                voiceToggleButton.setBackgroundColor(getColor(R.color.emergency_red))
                statusText.text = "Vasatey is listening for emergency phrases"
                statusCard.setCardBackgroundColor(getColor(R.color.safe_background))
            } else {
                voiceToggleButton.text = "Start Listening"
                voiceToggleButton.setBackgroundColor(getColor(R.color.success_green))
                statusText.text = "Vasatey is not listening"
                statusCard.setCardBackgroundColor(getColor(R.color.card_background))
            }
        }
    }
    
    /**
     * Update authentication UI state
     */
    private fun updateAuthenticationUI(authState: AuthState) {
        when (authState) {
            is AuthState.Authenticated -> {
                // User is logged in
                android.util.Log.d("MainActivity", "User authenticated")
            }
            is AuthState.Unauthenticated -> {
                // Show login prompt or redirect to auth
                android.util.Log.d("MainActivity", "User not authenticated")
            }
            is AuthState.Loading -> {
                // Show loading state
                android.util.Log.d("MainActivity", "Authentication loading")
            }
            is AuthState.Error -> {
                // Show error message
                Toast.makeText(this, "Authentication error: ${authState.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Update UI for missing permissions
     */
    private fun updateUIForMissingPermissions(missingPermissions: Set<String>) {
        runOnUiThread {
            val hasAudioPermission = Manifest.permission.RECORD_AUDIO !in missingPermissions
            val hasLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION !in missingPermissions
            
            voiceToggleButton.isEnabled = hasAudioPermission
            
            if (!hasAudioPermission) {
                statusText.text = "Microphone permission required for voice detection"
                statusCard.setCardBackgroundColor(getColor(R.color.warning_orange))
            } else if (!hasLocationPermission) {
                statusText.text = "Location permission required for emergency services"
                statusCard.setCardBackgroundColor(getColor(R.color.warning_orange))
            }
        }
    }
    
    /**
     * Handle notification intents
     */
    private fun handleNotificationIntent(intent: Intent?) {
        intent?.let { i ->
            when (i.getStringExtra("notification_type")) {
                "emergency" -> {
                    val alertId = i.getStringExtra("alert_id")
                    // TODO: Handle emergency notification tap
                    android.util.Log.d("MainActivity", "Emergency notification tapped: $alertId")
                }
                "general" -> {
                    // TODO: Handle general notification tap
                    android.util.Log.d("MainActivity", "General notification tapped")
                }
                "system" -> {
                    // TODO: Handle system notification tap
                    android.util.Log.d("MainActivity", "System notification tapped")
                }
            }
        }
    }
}

/**
 * Placeholder SettingsActivity class
 */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // TODO: Implement settings UI
        setContentView(android.R.layout.simple_list_item_1)
        
        Toast.makeText(this, "Settings - Coming soon", Toast.LENGTH_SHORT).show()
        finish()
    }
}