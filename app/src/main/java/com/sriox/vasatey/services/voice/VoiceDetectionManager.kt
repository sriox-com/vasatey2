package com.sriox.vasatey.services.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Voice detection manager for managing the voice detection service
 * Provides a high-level interface for voice detection functionality
 */
class VoiceDetectionManager(
    private val context: Context
) : VoiceDetectionService.VoiceDetectionListener {
    
    private var voiceService: VoiceDetectionService? = null
    private var isBound = false
    
    // State flows for reactive UI updates
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private val _lastDetection = MutableStateFlow<VoiceDetectionEvent?>(null)
    val lastDetection: StateFlow<VoiceDetectionEvent?> = _lastDetection.asStateFlow()
    
    private val _detectionStats = MutableStateFlow(VoiceDetectionStats(0, 0, 0, false, false))
    val detectionStats: StateFlow<VoiceDetectionStats> = _detectionStats.asStateFlow()
    
    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()
    
    // External listeners
    private val externalListeners = mutableListOf<VoiceDetectionService.VoiceDetectionListener>()
    
    /**
     * Service connection callback
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as VoiceDetectionService.VoiceDetectionBinder
            voiceService = binder.getService()
            voiceService?.addListener(this@VoiceDetectionManager)
            isBound = true
            
            // Update initial state
            voiceService?.let { service ->
                _isListening.value = service.isCurrentlyListening()
                _detectionStats.value = service.getDetectionStats()
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            voiceService?.removeListener(this@VoiceDetectionManager)
            voiceService = null
            isBound = false
            _isListening.value = false
        }
    }
    
    /**
     * Start the voice detection service and bind to it
     */
    fun startService() {
        val intent = Intent(context, VoiceDetectionService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    /**
     * Stop the voice detection service
     */
    fun stopService() {
        if (isBound) {
            voiceService?.removeListener(this)
            context.unbindService(serviceConnection)
            isBound = false
        }
        
        val intent = Intent(context, VoiceDetectionService::class.java)
        context.stopService(intent)
        
        _isListening.value = false
    }
    
    /**
     * Start listening for voice commands
     */
    fun startListening(userId: String? = null, sessionId: String? = null) {
        voiceService?.startListening(userId, sessionId)
    }
    
    /**
     * Stop listening for voice commands
     */
    fun stopListening() {
        voiceService?.stopListening()
    }
    
    /**
     * Pause listening temporarily
     */
    fun pauseListening() {
        voiceService?.pauseListening()
    }
    
    /**
     * Resume listening after pause
     */
    fun resumeListening() {
        voiceService?.resumeListening()
    }
    
    /**
     * Add external listener
     */
    fun addListener(listener: VoiceDetectionService.VoiceDetectionListener) {
        externalListeners.add(listener)
    }
    
    /**
     * Remove external listener
     */
    fun removeListener(listener: VoiceDetectionService.VoiceDetectionListener) {
        externalListeners.remove(listener)
    }
    
    /**
     * Get current detection statistics
     */
    fun getCurrentStats(): VoiceDetectionStats? {
        return voiceService?.getDetectionStats()
    }
    
    /**
     * Check if service is bound and ready
     */
    fun isServiceReady(): Boolean = isBound && voiceService != null
    
    // VoiceDetectionListener implementation
    override fun onWakeWordDetected(keyword: String, confidence: Float) {
        val event = VoiceDetectionEvent.WakeWordDetected(keyword, confidence, System.currentTimeMillis())
        _lastDetection.value = event
        
        // Forward to external listeners
        externalListeners.forEach { listener ->
            try {
                listener.onWakeWordDetected(keyword, confidence)
            } catch (e: Exception) {
                // Log error but continue
            }
        }
        
        // Update stats
        updateStats()
    }
    
    override fun onEmergencyPhraseDetected(intent: String, slots: Map<String, String>) {
        val event = VoiceDetectionEvent.EmergencyPhraseDetected(intent, slots, System.currentTimeMillis())
        _lastDetection.value = event
        
        // Forward to external listeners
        externalListeners.forEach { listener ->
            try {
                listener.onEmergencyPhraseDetected(intent, slots)
            } catch (e: Exception) {
                // Log error but continue
            }
        }
        
        // Update stats
        updateStats()
    }
    
    override fun onVoiceActivityDetected(probability: Float) {
        // Forward to external listeners
        externalListeners.forEach { listener ->
            try {
                listener.onVoiceActivityDetected(probability)
            } catch (e: Exception) {
                // Log error but continue
            }
        }
    }
    
    override fun onListeningStateChanged(isListening: Boolean) {
        _isListening.value = isListening
        
        // Forward to external listeners
        externalListeners.forEach { listener ->
            try {
                listener.onListeningStateChanged(isListening)
            } catch (e: Exception) {
                // Log error but continue
            }
        }
        
        // Update stats
        updateStats()
    }
    
    override fun onError(error: String, exception: Throwable?) {
        _errorState.value = error
        
        // Forward to external listeners
        externalListeners.forEach { listener ->
            try {
                listener.onError(error, exception)
            } catch (e: Exception) {
                // Log error but continue
            }
        }
    }
    
    /**
     * Update detection statistics
     */
    private fun updateStats() {
        voiceService?.let { service ->
            _detectionStats.value = service.getDetectionStats()
        }
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _errorState.value = null
    }
}

/**
 * Voice detection event sealed class
 */
sealed class VoiceDetectionEvent(val timestamp: Long) {
    data class WakeWordDetected(
        val keyword: String,
        val confidence: Float,
        val detectionTimestamp: Long
    ) : VoiceDetectionEvent(detectionTimestamp)
    
    data class EmergencyPhraseDetected(
        val intent: String,
        val slots: Map<String, String>,
        val detectionTimestamp: Long
    ) : VoiceDetectionEvent(detectionTimestamp)
    
    data class VoiceActivityDetected(
        val probability: Float,
        val detectionTimestamp: Long
    ) : VoiceDetectionEvent(detectionTimestamp)
}