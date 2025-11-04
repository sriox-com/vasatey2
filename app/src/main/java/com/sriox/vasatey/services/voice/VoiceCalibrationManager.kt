package com.sriox.vasatey.services.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * Voice calibration utility for optimizing voice detection settings
 * Analyzes ambient noise and user voice characteristics
 */
class VoiceCalibrationManager(
    private val context: Context,
    private val configManager: VoiceConfigManager
) {
    
    companion object {
        private const val TAG = "VoiceCalibration"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_LENGTH = 512
        private const val CALIBRATION_DURATION_MS = 10000L // 10 seconds
        private const val NOISE_SAMPLE_DURATION_MS = 3000L // 3 seconds
        private const val VOICE_SAMPLE_DURATION_MS = 5000L // 5 seconds
    }
    
    private var audioRecord: AudioRecord? = null
    private val calibrationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Calibration result data class
     */
    data class CalibrationResult(
        val ambientNoiseLevel: Double,
        val averageVoiceLevel: Double,
        val recommendedSensitivity: Float,
        val recommendedThreshold: Float,
        val signalToNoiseRatio: Double,
        val calibrationQuality: CalibrationQuality,
        val recommendations: List<String>
    )
    
    /**
     * Calibration quality levels
     */
    enum class CalibrationQuality(val displayName: String) {
        EXCELLENT("Excellent"),
        GOOD("Good"),
        FAIR("Fair"),
        POOR("Poor")
    }
    
    /**
     * Calibration step interface
     */
    interface CalibrationCallback {
        fun onCalibrationStarted()
        fun onStepStarted(step: CalibrationStep, message: String)
        fun onStepProgress(step: CalibrationStep, progress: Float)
        fun onStepCompleted(step: CalibrationStep)
        fun onCalibrationCompleted(result: CalibrationResult)
        fun onCalibrationError(error: String, exception: Throwable?)
    }
    
    /**
     * Calibration steps
     */
    enum class CalibrationStep(val displayName: String) {
        NOISE_ANALYSIS("Analyzing ambient noise"),
        VOICE_TRAINING("Voice characteristic analysis"),
        OPTIMIZATION("Optimizing settings"),
        VALIDATION("Validating configuration")
    }
    
    /**
     * Start voice calibration process
     */
    suspend fun startCalibration(callback: CalibrationCallback): CalibrationResult {
        return withContext(Dispatchers.Default) {
            try {
                callback.onCalibrationStarted()
                
                // Step 1: Analyze ambient noise
                callback.onStepStarted(CalibrationStep.NOISE_ANALYSIS, "Please remain quiet for noise analysis...")
                val noiseLevel = analyzeAmbientNoise(callback)
                callback.onStepCompleted(CalibrationStep.NOISE_ANALYSIS)
                
                // Step 2: Analyze voice characteristics
                callback.onStepStarted(CalibrationStep.VOICE_TRAINING, "Please speak naturally for voice analysis...")
                val voiceLevel = analyzeVoiceCharacteristics(callback)
                callback.onStepCompleted(CalibrationStep.VOICE_TRAINING)
                
                // Step 3: Optimize settings
                callback.onStepStarted(CalibrationStep.OPTIMIZATION, "Optimizing detection settings...")
                val optimizedSettings = optimizeSettings(noiseLevel, voiceLevel, callback)
                callback.onStepCompleted(CalibrationStep.OPTIMIZATION)
                
                // Step 4: Validate configuration
                callback.onStepStarted(CalibrationStep.VALIDATION, "Validating configuration...")
                val result = validateCalibration(noiseLevel, voiceLevel, optimizedSettings, callback)
                callback.onStepCompleted(CalibrationStep.VALIDATION)
                
                // Save calibration results
                configManager.saveCalibrationTimestamp()
                configManager.updateConfig {
                    copy(
                        wakeWordSensitivity = result.recommendedSensitivity,
                        minimumConfidenceThreshold = result.recommendedThreshold
                    )
                }
                
                callback.onCalibrationCompleted(result)
                result
                
            } catch (e: Exception) {
                callback.onCalibrationError("Calibration failed: ${e.message}", e)
                throw e
            } finally {
                cleanup()
            }
        }
    }
    
    /**
     * Analyze ambient noise level
     */
    private suspend fun analyzeAmbientNoise(callback: CalibrationCallback): Double {
        return withContext(Dispatchers.Default) {
            initializeAudioRecord()
            
            val samples = mutableListOf<Double>()
            val startTime = System.currentTimeMillis()
            val duration = NOISE_SAMPLE_DURATION_MS
            
            audioRecord?.startRecording()
            
            val audioBuffer = ShortArray(FRAME_LENGTH)
            
            while (System.currentTimeMillis() - startTime < duration) {
                val numRead = audioRecord?.read(audioBuffer, 0, FRAME_LENGTH) ?: 0
                
                if (numRead > 0) {
                    val rms = calculateRMS(audioBuffer, numRead)
                    samples.add(rms)
                    
                    // Update progress
                    val progress = (System.currentTimeMillis() - startTime).toFloat() / duration
                    callback.onStepProgress(CalibrationStep.NOISE_ANALYSIS, progress)
                }
                
                delay(10) // Small delay to prevent overwhelming
            }
            
            audioRecord?.stop()
            
            // Calculate average noise level
            samples.average()
        }
    }
    
    /**
     * Analyze voice characteristics
     */
    private suspend fun analyzeVoiceCharacteristics(callback: CalibrationCallback): Double {
        return withContext(Dispatchers.Default) {
            initializeAudioRecord()
            
            val samples = mutableListOf<Double>()
            val startTime = System.currentTimeMillis()
            val duration = VOICE_SAMPLE_DURATION_MS
            
            audioRecord?.startRecording()
            
            val audioBuffer = ShortArray(FRAME_LENGTH)
            var speechDetectedSamples = 0
            
            while (System.currentTimeMillis() - startTime < duration) {
                val numRead = audioRecord?.read(audioBuffer, 0, FRAME_LENGTH) ?: 0
                
                if (numRead > 0) {
                    val rms = calculateRMS(audioBuffer, numRead)
                    
                    // Only include samples that likely contain speech
                    if (rms > 0.01) { // Basic voice activity detection threshold
                        samples.add(rms)
                        speechDetectedSamples++
                    }
                    
                    // Update progress
                    val progress = (System.currentTimeMillis() - startTime).toFloat() / duration
                    callback.onStepProgress(CalibrationStep.VOICE_TRAINING, progress)
                }
                
                delay(10)
            }
            
            audioRecord?.stop()
            
            // Calculate average voice level from speech samples
            if (samples.isNotEmpty()) {
                samples.average()
            } else {
                0.1 // Default if no speech detected
            }
        }
    }
    
    /**
     * Optimize detection settings based on calibration data
     */
    private suspend fun optimizeSettings(
        noiseLevel: Double,
        voiceLevel: Double,
        callback: CalibrationCallback
    ): Pair<Float, Float> {
        return withContext(Dispatchers.Default) {
            delay(500) // Simulate optimization processing
            callback.onStepProgress(CalibrationStep.OPTIMIZATION, 0.5f)
            
            // Calculate signal-to-noise ratio
            val snr = if (noiseLevel > 0) voiceLevel / noiseLevel else 10.0
            
            // Optimize sensitivity based on SNR
            val sensitivity = when {
                snr > 8.0 -> 0.3f // High SNR - can use lower sensitivity
                snr > 4.0 -> 0.5f // Medium SNR - balanced sensitivity
                snr > 2.0 -> 0.7f // Low SNR - higher sensitivity needed
                else -> 0.8f // Very low SNR - maximum sensitivity
            }.coerceIn(0.1f, 0.9f)
            
            // Optimize threshold based on voice characteristics
            val threshold = when {
                voiceLevel > 0.3 -> 0.2f // Strong voice - lower threshold
                voiceLevel > 0.15 -> 0.3f // Medium voice - balanced threshold
                voiceLevel > 0.05 -> 0.4f // Quiet voice - higher threshold
                else -> 0.5f // Very quiet - maximum threshold
            }.coerceIn(0.1f, 0.6f)
            
            callback.onStepProgress(CalibrationStep.OPTIMIZATION, 1.0f)
            
            Pair(sensitivity, threshold)
        }
    }
    
    /**
     * Validate calibration results
     */
    private suspend fun validateCalibration(
        noiseLevel: Double,
        voiceLevel: Double,
        settings: Pair<Float, Float>,
        callback: CalibrationCallback
    ): CalibrationResult {
        return withContext(Dispatchers.Default) {
            delay(300) // Simulate validation
            callback.onStepProgress(CalibrationStep.VALIDATION, 0.5f)
            
            val (sensitivity, threshold) = settings
            val snr = if (noiseLevel > 0) voiceLevel / noiseLevel else 10.0
            
            // Determine calibration quality
            val quality = when {
                snr > 6.0 && voiceLevel > 0.2 -> CalibrationQuality.EXCELLENT
                snr > 3.0 && voiceLevel > 0.1 -> CalibrationQuality.GOOD
                snr > 1.5 && voiceLevel > 0.05 -> CalibrationQuality.FAIR
                else -> CalibrationQuality.POOR
            }
            
            // Generate recommendations
            val recommendations = mutableListOf<String>()
            
            when (quality) {
                CalibrationQuality.EXCELLENT -> {
                    recommendations.add("Great! Your environment is ideal for voice detection.")
                }
                CalibrationQuality.GOOD -> {
                    recommendations.add("Good setup. Voice detection should work reliably.")
                }
                CalibrationQuality.FAIR -> {
                    recommendations.add("Consider reducing background noise for better detection.")
                    if (voiceLevel < 0.1) {
                        recommendations.add("Try speaking louder or closer to your device.")
                    }
                }
                CalibrationQuality.POOR -> {
                    recommendations.add("Environment is challenging for voice detection.")
                    recommendations.add("Try to minimize background noise.")
                    recommendations.add("Speak clearly and at a normal volume.")
                    recommendations.add("Consider using manual emergency triggers as backup.")
                }
            }
            
            if (snr < 2.0) {
                recommendations.add("High background noise detected. Consider using the app in a quieter environment.")
            }
            
            if (noiseLevel > 0.5) {
                recommendations.add("Very high ambient noise. Voice detection may have false positives.")
            }
            
            callback.onStepProgress(CalibrationStep.VALIDATION, 1.0f)
            
            CalibrationResult(
                ambientNoiseLevel = noiseLevel,
                averageVoiceLevel = voiceLevel,
                recommendedSensitivity = sensitivity,
                recommendedThreshold = threshold,
                signalToNoiseRatio = snr,
                calibrationQuality = quality,
                recommendations = recommendations
            )
        }
    }
    
    /**
     * Calculate RMS (Root Mean Square) for audio level measurement
     */
    private fun calculateRMS(audioBuffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            sum += audioBuffer[i].toDouble().pow(2)
        }
        return sqrt(sum / length) / Short.MAX_VALUE
    }
    
    /**
     * Initialize audio recording
     */
    private fun initializeAudioRecord() {
        cleanup() // Clean up any existing recorder
        
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw RuntimeException("Failed to initialize AudioRecord")
            }
        } catch (e: SecurityException) {
            throw RuntimeException("Audio recording permission not granted", e)
        }
    }
    
    /**
     * Clean up audio resources
     */
    private fun cleanup() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
    
    /**
     * Cancel ongoing calibration
     */
    fun cancelCalibration() {
        calibrationScope.cancel()
        cleanup()
    }
    
    /**
     * Quick environment test
     */
    suspend fun quickEnvironmentTest(): EnvironmentTestResult {
        return withContext(Dispatchers.Default) {
            try {
                initializeAudioRecord()
                
                val samples = mutableListOf<Double>()
                val startTime = System.currentTimeMillis()
                val duration = 2000L // 2 seconds
                
                audioRecord?.startRecording()
                
                val audioBuffer = ShortArray(FRAME_LENGTH)
                
                while (System.currentTimeMillis() - startTime < duration) {
                    val numRead = audioRecord?.read(audioBuffer, 0, FRAME_LENGTH) ?: 0
                    
                    if (numRead > 0) {
                        val rms = calculateRMS(audioBuffer, numRead)
                        samples.add(rms)
                    }
                    
                    delay(10)
                }
                
                audioRecord?.stop()
                
                val averageLevel = samples.average()
                val suitability = when {
                    averageLevel > 0.5 -> EnvironmentSuitability.POOR
                    averageLevel > 0.2 -> EnvironmentSuitability.FAIR
                    averageLevel > 0.05 -> EnvironmentSuitability.GOOD
                    else -> EnvironmentSuitability.EXCELLENT
                }
                
                EnvironmentTestResult(averageLevel, suitability)
                
            } catch (e: Exception) {
                EnvironmentTestResult(0.0, EnvironmentSuitability.UNKNOWN)
            } finally {
                cleanup()
            }
        }
    }
    
    /**
     * Environment test result
     */
    data class EnvironmentTestResult(
        val noiseLevel: Double,
        val suitability: EnvironmentSuitability
    )
    
    /**
     * Environment suitability levels
     */
    enum class EnvironmentSuitability(val displayName: String, val description: String) {
        EXCELLENT("Excellent", "Perfect environment for voice detection"),
        GOOD("Good", "Good environment with minimal background noise"),
        FAIR("Fair", "Acceptable environment with some background noise"),
        POOR("Poor", "Challenging environment with high background noise"),
        UNKNOWN("Unknown", "Unable to determine environment suitability")
    }
}