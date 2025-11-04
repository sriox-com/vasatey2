package com.sriox.vasatey

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.sriox.vasatey.databinding.ActivitySignupBinding
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {

    private lateinit var authHelper: SupabaseAuthHelper
    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authHelper = SupabaseAuthHelper()

        binding.signupBtn.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()
            val mobileNumber = binding.mobileInput.text.toString().trim()
            val school = binding.schoolInput.text.toString().trim()
            val pet = binding.petInput.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || 
                mobileNumber.isEmpty() || school.isEmpty() || pet.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show loading state
            binding.signupBtn.isEnabled = false
            binding.signupBtn.text = "Creating account..."

            // Create user profile without FCM token initially
            lifecycleScope.launch {
                try {
                    // First, try to get FCM token but don't fail if it doesn't work
                    var fcmToken: String? = null
                    try {
                        AppLogger.logInfo("SIGNUP", "Attempting to get FCM token during signup", "User: $email")
                        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                            fcmToken = token
                            AppLogger.logSuccess("SIGNUP", "FCM token obtained during signup", "Token: ${token.take(20)}...")
                        }.addOnFailureListener { e ->
                            AppLogger.logWarning("SIGNUP", "FCM token not available during signup", "Will retry after login - Error: ${e.message}")
                        }
                        
                        // Wait a moment for the token, but don't block forever
                        kotlinx.coroutines.delay(2000)
                    } catch (e: Exception) {
                        AppLogger.logWarning("SIGNUP", "FCM token generation failed", "Will proceed without token - Error: ${e.message}")
                    }

                    val userProfile = UserProfile(
                        email = email,
                        fullName = name,
                        fcmToken = fcmToken, // This can be null, will be updated later
                        phoneNumber = mobileNumber,
                        emergencyContact = mobileNumber, // Use mobile as emergency contact for now
                        medicalInfo = "School: $school, Pet: $pet" // Combine school and pet info
                    )

                    AppLogger.logInfo("SIGNUP", "Creating user profile", "Email: $email, FCM Token: ${if (fcmToken != null) "Available" else "Not available"}")
                    
                    authHelper.signUp(email, password, userProfile).fold(
                        onSuccess = { user ->
                            AppLogger.logSuccess("SIGNUP", "User created successfully", "Email: ${user.email}")
                            
                            // If we didn't get FCM token during signup, try to get it now and update
                            if (fcmToken == null) {
                                AppLogger.logInfo("SIGNUP", "Attempting to get FCM token after signup", "")
                                try {
                                    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                                        lifecycleScope.launch {
                                            AppLogger.logSuccess("SIGNUP", "FCM token obtained after signup", "Updating user profile")
                                            // Update the user profile with the FCM token
                                            val dbHelper = SupabaseDatabaseHelper()
                                            dbHelper.updateFCMToken(user.id, token)
                                        }
                                    }
                                } catch (e: Exception) {
                                    AppLogger.logWarning("SIGNUP", "FCM token still not available", "User can update manually later")
                                }
                            }
                            
                            Toast.makeText(this@SignupActivity, "Signup successful!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
                            finish()
                        },
                        onFailure = { exception ->
                            AppLogger.logError("SIGNUP", "Signup failed", "Error: ${exception.message}")
                            binding.signupBtn.isEnabled = true
                            binding.signupBtn.text = "Sign Up"
                            Toast.makeText(
                                this@SignupActivity,
                                "Signup failed: ${exception.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                } catch (e: Exception) {
                    AppLogger.logError("SIGNUP", "Signup process exception", "Error: ${e.message}")
                    binding.signupBtn.isEnabled = true
                    binding.signupBtn.text = "Sign Up"
                    Toast.makeText(this@SignupActivity, "Signup failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.loginRedirect.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}
