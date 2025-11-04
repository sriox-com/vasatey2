package com.sriox.vasatey.services.voicepackage com.sriox.vasatey.services.voice



import ai.picovoice.porcupine.Porcupineimport ai.picovoice.porcupine.*

import ai.picovoice.porcupine.PorcupineExceptionimport ai.picovoice.rhino.*

import ai.picovoice.porcupine.PorcupineManagerimport ai.picovoice.cobra.*

import ai.picovoice.porcupine.PorcupineManagerCallbackimport android.app.Service

import ai.picovoice.rhino.Rhinoimport android.content.Intent

import ai.picovoice.rhino.RhinoExceptionimport android.content.pm.PackageManager

import ai.picovoice.rhino.RhinoManagerimport android.media.AudioFormat

import ai.picovoice.rhino.RhinoManagerCallbackimport android.media.AudioRecord

import android.app.Notificationimport android.media.MediaRecorder

import android.app.NotificationChannelimport android.os.Binder

import android.app.NotificationManagerimport android.os.IBinder

import android.app.PendingIntentimport android.util.Log

import android.app.Serviceimport androidx.core.app.ActivityCompat

import android.content.Contextimport androidx.core.app.NotificationCompat

import android.content.Intentimport com.sriox.vasatey.R

import android.content.pm.PackageManagerimport com.sriox.vasatey.VasateyApplication

import android.media.AudioManagerimport com.sriox.vasatey.data.models.VoiceDetectionLog

import android.os.Binderimport com.sriox.vasatey.data.repository.RepositoryResult

import android.os.Buildimport com.sriox.vasatey.services.fcm.NotificationHelper

import android.os.IBinderimport kotlinx.coroutines.*

import android.os.PowerManagerimport java.io.File

import androidx.core.app.NotificationCompatimport java.time.LocalDateTime

import androidx.core.content.ContextCompatimport java.time.format.DateTimeFormatter

import com.sriox.vasatey.Rimport java.util.concurrent.atomic.AtomicBoolean

import com.sriox.vasatey.data.models.SystemLog

import com.sriox.vasatey.data.models.VoiceDetectionLog/**

import com.sriox.vasatey.data.repository.NotificationRepository * Voice detection service using Picovoice for emergency detection

import com.sriox.vasatey.data.supabase.SupabaseClientConfig * Handles wake word detection and emergency phrase recognition

import com.sriox.vasatey.ui.activities.MainActivity */

import kotlinx.coroutines.CoroutineScopeclass VoiceDetectionService : Service() {

import kotlinx.coroutines.Dispatchers    

import kotlinx.coroutines.Job    companion object {

import kotlinx.coroutines.launch        private const val TAG = "VoiceDetectionService"

import java.time.LocalDateTime        private const val NOTIFICATION_ID = 1001

import java.time.format.DateTimeFormatter        private const val CHANNEL_ID = "voice_detection_service"

        

/**        // Audio configuration

 * Voice Detection Service using Picovoice for emergency phrase recognition        private const val SAMPLE_RATE = 16000

 * Handles continuous listening, wake word detection, and emergency phrase processing        private const val FRAME_LENGTH = 512

 */        private const val BUFFER_SIZE_FACTOR = 10

class VoiceDetectionService : Service() {        

            // Wake words for emergency detection

    companion object {        private val EMERGENCY_WAKE_WORDS = arrayOf(

        private const val NOTIFICATION_ID = 2001            "help me",

        private const val CHANNEL_ID = "voice_detection_service"            "emergency",

                    "call help",

        // Actions            "i need help",

        const val ACTION_START_LISTENING = "com.sriox.vasatey.START_LISTENING"            "assistance"

        const val ACTION_STOP_LISTENING = "com.sriox.vasatey.STOP_LISTENING"        )

        const val ACTION_TOGGLE_LISTENING = "com.sriox.vasatey.TOGGLE_LISTENING"        

                // Emergency contexts for Rhino

        // Broadcast intents        private const val EMERGENCY_CONTEXT = "emergency_detection"

        const val BROADCAST_WAKE_WORD_DETECTED = "com.sriox.vasatey.WAKE_WORD_DETECTED"        

        const val BROADCAST_EMERGENCY_PHRASE_DETECTED = "com.sriox.vasatey.EMERGENCY_PHRASE_DETECTED"        // Detection sensitivity (0.0 to 1.0)

        const val BROADCAST_LISTENING_STATE_CHANGED = "com.sriox.vasatey.LISTENING_STATE_CHANGED"        private const val WAKE_WORD_SENSITIVITY = 0.5f

                private const val VOICE_ACTIVITY_SENSITIVITY = 0.5f

        // Emergency phrases and wake words    }

        private val EMERGENCY_WAKE_WORDS = arrayOf("hey-vasatey", "emergency-vasatey")    

        private val EMERGENCY_PHRASES = mapOf(    // Service binding

            "help me" to "general_emergency",    private val binder = VoiceDetectionBinder()

            "call police" to "police_emergency",    

            "call ambulance" to "medical_emergency",    // Picovoice components

            "call fire department" to "fire_emergency",    private var porcupineManager: PorcupineManager? = null

            "i need help" to "general_emergency",    private var rhinoManager: RhinoManager? = null

            "emergency" to "general_emergency"    private var cobraManager: CobraManager? = null

        )    

    }    // Audio recording

        private var audioRecord: AudioRecord? = null

    // Service binding    private val isRecording = AtomicBoolean(false)

    private val binder = VoiceDetectionBinder()    private val isListening = AtomicBoolean(false)

        

    // Picovoice components    // Service scope

    private var porcupineManager: PorcupineManager? = null    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var rhinoManager: RhinoManager? = null    private var recordingJob: Job? = null

        

    // Service state    // Application dependencies

    private var isListening = false    private lateinit var app: VasateyApplication

    private var isInitialized = false    private lateinit var notificationHelper: NotificationHelper

    private var currentSessionId: String? = null    

        // Current session tracking

    // Wake lock for continuous operation    private var currentSessionId: String? = null

    private lateinit var wakeLock: PowerManager.WakeLock    private var currentUserId: String? = null

        

    // Audio manager for volume control    // Detection statistics

    private lateinit var audioManager: AudioManager    private var detectionCount = 0

        private var falsePositiveCount = 0

    // Coroutine scope for database operations    private var lastDetectionTime: Long = 0

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())    

        // Voice detection listeners

    // Database repositories    private val listeners = mutableListOf<VoiceDetectionListener>()

    private lateinit var notificationRepository: NotificationRepository    

        inner class VoiceDetectionBinder : Binder() {

    // Detection statistics        fun getService(): VoiceDetectionService = this@VoiceDetectionService

    private var detectionCount = 0    }

    private var falsePositiveCount = 0    

    private var sessionStartTime: Long = 0    interface VoiceDetectionListener {

            fun onWakeWordDetected(keyword: String, confidence: Float)

    inner class VoiceDetectionBinder : Binder() {        fun onEmergencyPhraseDetected(intent: String, slots: Map<String, String>)

        fun getService(): VoiceDetectionService = this@VoiceDetectionService        fun onVoiceActivityDetected(probability: Float)

    }        fun onListeningStateChanged(isListening: Boolean)

            fun onError(error: String, exception: Throwable?)

    override fun onCreate() {    }

        super.onCreate()    

            override fun onCreate() {

        // Initialize repositories        super.onCreate()

        val supabaseClient = SupabaseClientConfig(this)        app = application as VasateyApplication

        notificationRepository = NotificationRepository(supabaseClient)        notificationHelper = NotificationHelper(this)

                

        // Initialize audio manager        initializePicovoice()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager        startForegroundService()

                

        // Initialize wake lock        Log.d(TAG, "Voice Detection Service created")

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager    }

        wakeLock = powerManager.newWakeLock(    

            PowerManager.PARTIAL_WAKE_LOCK,    override fun onBind(intent: Intent): IBinder = binder

            "Vasatey::VoiceDetectionWakeLock"    

        )    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

                when (intent?.action) {

        // Create notification channel            "START_LISTENING" -> startListening()

        createNotificationChannel()            "STOP_LISTENING" -> stopListening()

                    "PAUSE_LISTENING" -> pauseListening()

        // Initialize Picovoice            "RESUME_LISTENING" -> resumeListening()

        initializePicovoice()        }

                return START_STICKY

        android.util.Log.d("VoiceDetectionService", "Voice detection service created")    }

    }    

        override fun onDestroy() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {        super.onDestroy()

        when (intent?.action) {        stopListening()

            ACTION_START_LISTENING -> startListening()        cleanup()

            ACTION_STOP_LISTENING -> stopListening()        Log.d(TAG, "Voice Detection Service destroyed")

            ACTION_TOGGLE_LISTENING -> toggleListening()    }

            else -> {    

                // Default action - start listening if not already    /**

                if (!isListening) {     * Initialize Picovoice components

                    startListening()     */

                }    private fun initializePicovoice() {

            }        try {

        }            initializePorcupine()

                    initializeRhino()

        return START_STICKY // Restart service if killed            initializeCobra()

    }            Log.d(TAG, "Picovoice components initialized successfully")

            } catch (e: Exception) {

    override fun onBind(intent: Intent?): IBinder = binder            Log.e(TAG, "Failed to initialize Picovoice", e)

                notifyError("Failed to initialize voice detection", e)

    override fun onDestroy() {        }

        super.onDestroy()    }

            

        stopListening()    /**

        cleanup()     * Initialize Porcupine for wake word detection

             */

        android.util.Log.d("VoiceDetectionService", "Voice detection service destroyed")    private fun initializePorcupine() {

    }        try {

                val builder = PorcupineManager.Builder()

    /**                .setSensitivities(FloatArray(EMERGENCY_WAKE_WORDS.size) { WAKE_WORD_SENSITIVITY })

     * Initialize Picovoice components                .setKeywords(*EMERGENCY_WAKE_WORDS)

     */                .setErrorCallback { error ->

    private fun initializePicovoice() {                    Log.e(TAG, "Porcupine error: ${error.message}")

        try {                    notifyError("Wake word detection error", error)

            // Check microphone permission                }

            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)             

                != PackageManager.PERMISSION_GRANTED) {            porcupineManager = builder.build(applicationContext) { keywordIndex ->

                android.util.Log.e("VoiceDetectionService", "Microphone permission not granted")                handleWakeWordDetection(keywordIndex)

                return            }

            }            

                        Log.d(TAG, "Porcupine initialized with ${EMERGENCY_WAKE_WORDS.size} wake words")

            // Initialize Porcupine for wake word detection        } catch (e: Exception) {

            initializePorcupine()            Log.e(TAG, "Failed to initialize Porcupine", e)

                        throw e

            // Initialize Rhino for command understanding        }

            initializeRhino()    }

                

            isInitialized = true    /**

            android.util.Log.d("VoiceDetectionService", "Picovoice initialized successfully")     * Initialize Rhino for speech-to-intent

                 */

        } catch (e: Exception) {    private fun initializeRhino() {

            android.util.Log.e("VoiceDetectionService", "Failed to initialize Picovoice", e)        try {

            logSystemError("VOICE_INIT_ERROR", "Failed to initialize Picovoice", e)            val contextPath = extractContext(EMERGENCY_CONTEXT)

        }            

    }            val builder = RhinoManager.Builder()

                    .setContextPath(contextPath)

    /**                .setSensitivity(WAKE_WORD_SENSITIVITY)

     * Initialize Porcupine wake word detection                .setErrorCallback { error ->

     */                    Log.e(TAG, "Rhino error: ${error.message}")

    private fun initializePorcupine() {                    notifyError("Speech recognition error", error)

        try {                }

            porcupineManager = PorcupineManager.Builder()            

                .setAccessKey(getPicovoiceAccessKey())            rhinoManager = builder.build(applicationContext) { inference ->

                .setKeywords(EMERGENCY_WAKE_WORDS)                handleSpeechInference(inference)

                .setSensitivities(floatArrayOf(0.7f, 0.7f)) // Sensitivity for each wake word            }

                .build(applicationContext, object : PorcupineManagerCallback {            

                    override fun invoke(keywordIndex: Int) {            Log.d(TAG, "Rhino initialized with emergency context")

                        handleWakeWordDetected(keywordIndex)        } catch (e: Exception) {

                    }            Log.e(TAG, "Failed to initialize Rhino", e)

                })            // Continue without Rhino if context is not available

                        }

        } catch (e: PorcupineException) {    }

            android.util.Log.e("VoiceDetectionService", "Failed to initialize Porcupine", e)    

            throw e    /**

        }     * Initialize Cobra for voice activity detection

    }     */

        private fun initializeCobra() {

    /**        try {

     * Initialize Rhino speech-to-intent            cobraManager = CobraManager.Builder()

     */                .setErrorCallback { error ->

    private fun initializeRhino() {                    Log.e(TAG, "Cobra error: ${error.message}")

        try {                    notifyError("Voice activity detection error", error)

            rhinoManager = RhinoManager.Builder()                }

                .setAccessKey(getPicovoiceAccessKey())                .build(applicationContext)

                .setContextPath(getEmergencyContextPath())            

                .setSensitivity(0.6f)            Log.d(TAG, "Cobra initialized for voice activity detection")

                .setEndpointDurationSec(2.0f)        } catch (e: Exception) {

                .setRequireEndpoint(true)            Log.e(TAG, "Failed to initialize Cobra", e)

                .build(applicationContext, object : RhinoManagerCallback {            // Continue without Cobra if not available

                    override fun invoke(inference: ai.picovoice.rhino.RhinoInference) {        }

                        handleEmergencyInference(inference)    }

                    }    

                })    /**

                     * Extract Rhino context from assets

        } catch (e: RhinoException) {     */

            android.util.Log.e("VoiceDetectionService", "Failed to initialize Rhino", e)    private fun extractContext(contextName: String): String {

            throw e        val contextDir = File(filesDir, "rhino_contexts")

        }        if (!contextDir.exists()) {

    }            contextDir.mkdirs()

            }

    /**        

     * Start voice detection        val contextFile = File(contextDir, "$contextName.rhn")

     */        

    fun startListening() {        if (!contextFile.exists()) {

        if (!isInitialized) {            try {

            android.util.Log.e("VoiceDetectionService", "Cannot start listening - not initialized")                assets.open("rhino_contexts/$contextName.rhn").use { input ->

            return                    contextFile.outputStream().use { output ->

        }                        input.copyTo(output)

                            }

        if (isListening) {                }

            android.util.Log.d("VoiceDetectionService", "Already listening")            } catch (e: Exception) {

            return                Log.w(TAG, "Could not extract Rhino context: $contextName", e)

        }                throw e

                    }

        try {        }

            // Start wake word detection        

            porcupineManager?.start()        return contextFile.absolutePath

                }

            // Acquire wake lock    

            if (!wakeLock.isHeld) {    /**

                wakeLock.acquire(10 * 60 * 1000L) // 10 minutes max     * Start foreground service with notification

            }     */

                private fun startForegroundService() {

            // Start foreground service        val notification = NotificationCompat.Builder(this, CHANNEL_ID)

            startForeground(NOTIFICATION_ID, createListeningNotification())            .setContentTitle("Vasatey Voice Detection")

                        .setContentText("Monitoring for emergency phrases")

            // Update state            .setSmallIcon(R.drawable.ic_mic_on)

            isListening = true            .setOngoing(true)

            sessionStartTime = System.currentTimeMillis()            .setPriority(NotificationCompat.PRIORITY_LOW)

            currentSessionId = generateSessionId()            .build()

            detectionCount = 0        

            falsePositiveCount = 0        startForeground(NOTIFICATION_ID, notification)

                }

            // Broadcast state change    

            broadcastListeningStateChanged(true)    /**

                 * Start listening for voice commands

            // Log session start     */

            logVoiceDetection(    fun startListening(userId: String? = null, sessionId: String? = null) {

                sessionId = currentSessionId,        if (isListening.get()) {

                triggerSource = "manual_activation",            Log.d(TAG, "Already listening")

                wakeWord = null,            return

                phrase = "listening_started"        }

            )        

                    currentUserId = userId

            android.util.Log.d("VoiceDetectionService", "Voice detection started")        currentSessionId = sessionId

                    

        } catch (e: Exception) {        if (checkAudioPermission()) {

            android.util.Log.e("VoiceDetectionService", "Failed to start listening", e)            serviceScope.launch {

            logSystemError("VOICE_START_ERROR", "Failed to start voice detection", e)                try {

        }                    initializeAudioRecord()

    }                    isListening.set(true)

                        notifyListeningStateChanged(true)

    /**                    

     * Stop voice detection                    porcupineManager?.start()

     */                    rhinoManager?.start()

    fun stopListening() {                    

        if (!isListening) {                    startAudioRecording()

            android.util.Log.d("VoiceDetectionService", "Already stopped")                    

            return                    notificationHelper.showListeningStatusNotification(true)

        }                    Log.d(TAG, "Voice detection started")

                        } catch (e: Exception) {

        try {                    Log.e(TAG, "Failed to start listening", e)

            // Stop Picovoice managers                    notifyError("Failed to start voice detection", e)

            porcupineManager?.stop()                    isListening.set(false)

            rhinoManager?.stop()                    notifyListeningStateChanged(false)

                            }

            // Release wake lock            }

            if (wakeLock.isHeld) {        } else {

                wakeLock.release()            Log.e(TAG, "Audio permission not granted")

            }            notifyError("Audio permission required for voice detection", null)

                    }

            // Update state    }

            isListening = false    

                /**

            // Broadcast state change     * Stop listening for voice commands

            broadcastListeningStateChanged(false)     */

                fun stopListening() {

            // Log session end        if (!isListening.get()) {

            logVoiceDetection(            Log.d(TAG, "Already stopped")

                sessionId = currentSessionId,            return

                triggerSource = "manual_deactivation",        }

                wakeWord = null,        

                phrase = "listening_stopped"        isListening.set(false)

            )        isRecording.set(false)

                    

            // Stop foreground service        recordingJob?.cancel()

            stopForeground(true)        

                    try {

            android.util.Log.d("VoiceDetectionService", "Voice detection stopped")            porcupineManager?.stop()

                        rhinoManager?.stop()

        } catch (e: Exception) {            audioRecord?.stop()

            android.util.Log.e("VoiceDetectionService", "Error stopping voice detection", e)            audioRecord?.release()

            logSystemError("VOICE_STOP_ERROR", "Error stopping voice detection", e)            audioRecord = null

        }        } catch (e: Exception) {

    }            Log.e(TAG, "Error stopping voice detection", e)

            }

    /**        

     * Toggle listening state        notifyListeningStateChanged(false)

     */        notificationHelper.showListeningStatusNotification(false)

    fun toggleListening() {        Log.d(TAG, "Voice detection stopped")

        if (isListening) {    }

            stopListening()    

        } else {    /**

            startListening()     * Pause listening temporarily

        }     */

    }    fun pauseListening() {

            if (isListening.get()) {

    /**            stopListening()

     * Handle wake word detection            Log.d(TAG, "Voice detection paused")

     */        }

    private fun handleWakeWordDetected(keywordIndex: Int) {    }

        val wakeWord = EMERGENCY_WAKE_WORDS.getOrNull(keywordIndex) ?: "unknown"    

        detectionCount++    /**

             * Resume listening after pause

        android.util.Log.d("VoiceDetectionService", "Wake word detected: $wakeWord")     */

            fun resumeListening() {

        // Log detection        if (!isListening.get()) {

        logVoiceDetection(            startListening(currentUserId, currentSessionId)

            sessionId = currentSessionId,            Log.d(TAG, "Voice detection resumed")

            triggerSource = "keyword_spotted",        }

            wakeWord = wakeWord,    }

            phrase = null    

        )    /**

             * Initialize audio recording

        // Broadcast wake word detection     */

        val intent = Intent(BROADCAST_WAKE_WORD_DETECTED).apply {    private fun initializeAudioRecord() {

            putExtra("wake_word", wakeWord)        val bufferSize = AudioRecord.getMinBufferSize(

            putExtra("keyword_index", keywordIndex)            SAMPLE_RATE,

            putExtra("session_id", currentSessionId)            AudioFormat.CHANNEL_IN_MONO,

        }            AudioFormat.ENCODING_PCM_16BIT

        sendBroadcast(intent)        ) * BUFFER_SIZE_FACTOR

                

        // Start listening for emergency phrases        if (ActivityCompat.checkSelfPermission(

        try {                this,

            rhinoManager?.process()                android.Manifest.permission.RECORD_AUDIO

        } catch (e: Exception) {            ) != PackageManager.PERMISSION_GRANTED

            android.util.Log.e("VoiceDetectionService", "Error starting phrase detection", e)        ) {

        }            throw SecurityException("Audio recording permission not granted")

    }        }

            

    /**        audioRecord = AudioRecord(

     * Handle emergency phrase inference            MediaRecorder.AudioSource.MIC,

     */            SAMPLE_RATE,

    private fun handleEmergencyInference(inference: ai.picovoice.rhino.RhinoInference) {            AudioFormat.CHANNEL_IN_MONO,

        if (inference.isUnderstood) {            AudioFormat.ENCODING_PCM_16BIT,

            val intent = inference.intent            bufferSize

            val slots = inference.slots        )

                    

            android.util.Log.d("VoiceDetectionService", "Emergency phrase detected: $intent")        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {

                        throw RuntimeException("Failed to initialize AudioRecord")

            // Process emergency intent        }

            processEmergencyIntent(intent, slots)    }

                

        } else {    /**

            android.util.Log.d("VoiceDetectionService", "Phrase not understood")     * Start audio recording and processing

            falsePositiveCount++     */

                private fun startAudioRecording() {

            // Log false positive        recordingJob = serviceScope.launch {

            logVoiceDetection(            audioRecord?.startRecording()

                sessionId = currentSessionId,            isRecording.set(true)

                triggerSource = "continuous_listening",            

                wakeWord = null,            val audioBuffer = ShortArray(FRAME_LENGTH)

                phrase = "not_understood",            

                wasFalsePositive = true            while (isRecording.get() && isActive) {

            )                try {

        }                    val numRead = audioRecord?.read(audioBuffer, 0, FRAME_LENGTH) ?: 0

    }                    

                        if (numRead > 0) {

    /**                        processAudioFrame(audioBuffer)

     * Process emergency intent from speech recognition                    }

     */                } catch (e: Exception) {

    private fun processEmergencyIntent(intent: String, slots: Map<String, String>) {                    Log.e(TAG, "Error reading audio", e)

        val emergencyType = mapIntentToEmergencyType(intent, slots)                    if (isActive) {

                                notifyError("Audio recording error", e)

        // Log emergency detection                    }

        logVoiceDetection(                    break

            sessionId = currentSessionId,                }

            triggerSource = "continuous_listening",            }

            wakeWord = null,        }

            phrase = intent,    }

            wasFalsePositive = false    

        )    /**

             * Process audio frame through Picovoice components

        // Broadcast emergency phrase detection     */

        val broadcastIntent = Intent(BROADCAST_EMERGENCY_PHRASE_DETECTED).apply {    private suspend fun processAudioFrame(audioBuffer: ShortArray) {

            putExtra("intent", intent)        try {

            putExtra("emergency_type", emergencyType)            // Voice activity detection with Cobra

            putExtra("slots", HashMap(slots))            cobraManager?.let { cobra ->

            putExtra("session_id", currentSessionId)                val voiceProbability = cobra.process(audioBuffer)

        }                if (voiceProbability > VOICE_ACTIVITY_SENSITIVITY) {

        sendBroadcast(broadcastIntent)                    notifyVoiceActivityDetected(voiceProbability)

                        }

        android.util.Log.d("VoiceDetectionService", "Emergency detected: $emergencyType")            }

    }            

                // Wake word detection with Porcupine

    /**            porcupineManager?.let { porcupine ->

     * Map Rhino intent to emergency type                val keywordIndex = porcupine.process(audioBuffer)

     */                if (keywordIndex >= 0) {

    private fun mapIntentToEmergencyType(intent: String, slots: Map<String, String>): String {                    handleWakeWordDetection(keywordIndex)

        return when (intent.lowercase()) {                }

            "police", "call_police" -> "police_emergency"            }

            "medical", "ambulance", "call_ambulance" -> "medical_emergency"            

            "fire", "call_fire" -> "fire_emergency"            // Speech-to-intent with Rhino

            "help", "emergency", "general_help" -> "general_emergency"            rhinoManager?.let { rhino ->

            else -> "general_emergency"                val isFinalized = rhino.process(audioBuffer)

        }                if (isFinalized) {

    }                    val inference = rhino.inference

                        if (inference.isUnderstood) {

    /**                        handleSpeechInference(inference)

     * Create notification for listening state                    }

     */                }

    private fun createListeningNotification(): Notification {            }

        val intent = Intent(this, MainActivity::class.java)        } catch (e: Exception) {

        val pendingIntent = PendingIntent.getActivity(            Log.e(TAG, "Error processing audio frame", e)

            this, 0, intent,        }

            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE    }

        )    

            /**

        val stopIntent = Intent(this, VoiceDetectionService::class.java).apply {     * Handle wake word detection

            action = ACTION_STOP_LISTENING     */

        }    private fun handleWakeWordDetection(keywordIndex: Int) {

        val stopPendingIntent = PendingIntent.getService(        if (keywordIndex < EMERGENCY_WAKE_WORDS.size) {

            this, 0, stopIntent,            val keyword = EMERGENCY_WAKE_WORDS[keywordIndex]

            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE            val confidence = calculateConfidence(keywordIndex)

        )            

                    detectionCount++

        return NotificationCompat.Builder(this, CHANNEL_ID)            lastDetectionTime = System.currentTimeMillis()

            .setSmallIcon(R.drawable.ic_mic_on)            

            .setContentTitle("Vasatey is listening")            Log.d(TAG, "Wake word detected: $keyword (confidence: $confidence)")

            .setContentText("Monitoring for emergency phrases")            

            .setPriority(NotificationCompat.PRIORITY_LOW)            // Log detection

            .setOngoing(true)            serviceScope.launch {

            .setContentIntent(pendingIntent)                logVoiceDetection(

            .addAction(R.drawable.ic_mic_off, "Stop", stopPendingIntent)                    wakeWord = keyword,

            .build()                    confidence = confidence.toDouble(),

    }                    triggerSource = "continuous_listening"

                    )

    /**            }

     * Create notification channel            

     */            // Notify listeners

    private fun createNotificationChannel() {            notifyWakeWordDetected(keyword, confidence)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {            

            val channel = NotificationChannel(            // Trigger emergency detection flow

                CHANNEL_ID,            triggerEmergencyDetection(keyword, confidence)

                "Voice Detection Service",        }

                NotificationManager.IMPORTANCE_LOW    }

            ).apply {    

                description = "Continuous voice monitoring for emergency detection"    /**

                setShowBadge(false)     * Handle speech inference from Rhino

            }     */

                private fun handleSpeechInference(inference: RhinoInference) {

            val notificationManager = getSystemService(NotificationManager::class.java)        val intent = inference.intent

            notificationManager.createNotificationChannel(channel)        val slots = inference.slots

        }        

    }        Log.d(TAG, "Speech inference: intent=$intent, slots=$slots")

            

    /**        // Log detection

     * Broadcast listening state change        serviceScope.launch {

     */            logVoiceDetection(

    private fun broadcastListeningStateChanged(isListening: Boolean) {                phrase = intent,

        val intent = Intent(BROADCAST_LISTENING_STATE_CHANGED).apply {                confidence = 0.8, // Rhino doesn't provide confidence

            putExtra("is_listening", isListening)                triggerSource = "speech_recognition"

            putExtra("session_id", currentSessionId)            )

            putExtra("detection_count", detectionCount)        }

            putExtra("false_positive_count", falsePositiveCount)        

        }        // Notify listeners

        sendBroadcast(intent)        notifyEmergencyPhraseDetected(intent, slots)

    }        

            // Process emergency intent

    /**        processEmergencyIntent(intent, slots)

     * Log voice detection event    }

     */    

    private fun logVoiceDetection(    /**

        sessionId: String?,     * Calculate confidence score for wake word detection

        triggerSource: String,     */

        wakeWord: String?,    private fun calculateConfidence(keywordIndex: Int): Float {

        phrase: String?,        // Simple confidence calculation based on keyword index and timing

        wasFalsePositive: Boolean = false        val baseConfidence = WAKE_WORD_SENSITIVITY

    ) {        val timeFactor = if (System.currentTimeMillis() - lastDetectionTime > 5000) 0.1f else -0.1f

        serviceScope.launch {        return (baseConfidence + timeFactor).coerceIn(0.0f, 1.0f)

            try {    }

                val log = VoiceDetectionLog.create(    

                    userId = getCurrentUserId() ?: "unknown",    /**

                    sessionId = sessionId,     * Trigger emergency detection workflow

                    wakeWord = wakeWord,     */

                    phrase = phrase,    private fun triggerEmergencyDetection(keyword: String, confidence: Float) {

                    confidence = null,        val intent = Intent("com.sriox.vasatey.VOICE_EMERGENCY_DETECTED").apply {

                    triggerSource = triggerSource            putExtra("keyword", keyword)

                ).copy(wasFalsePositive = wasFalsePositive)            putExtra("confidence", confidence)

                            putExtra("timestamp", System.currentTimeMillis())

                // TODO: Save to database via repository            putExtra("user_id", currentUserId)

                android.util.Log.d("VoiceDetectionService", "Voice detection logged: $phrase")            putExtra("session_id", currentSessionId)

            } catch (e: Exception) {        }

                android.util.Log.e("VoiceDetectionService", "Failed to log voice detection", e)        sendBroadcast(intent)

            }    }

        }    

    }    /**

         * Process emergency intent from speech recognition

    /**     */

     * Log system error    private fun processEmergencyIntent(intent: String, slots: Map<String, String>) {

     */        val broadcastIntent = Intent("com.sriox.vasatey.VOICE_EMERGENCY_INTENT").apply {

    private fun logSystemError(errorCode: String, message: String, throwable: Throwable) {            putExtra("intent", intent)

        serviceScope.launch {            putExtra("slots", HashMap(slots))

            try {            putExtra("timestamp", System.currentTimeMillis())

                val log = SystemLog.error(            putExtra("user_id", currentUserId)

                    category = "VOICE",            putExtra("session_id", currentSessionId)

                    eventType = errorCode,        }

                    message = message,        sendBroadcast(broadcastIntent)

                    throwable = throwable,    }

                    userId = getCurrentUserId()    

                )    /**

                     * Log voice detection to database

                // TODO: Save to database via repository     */

                android.util.Log.e("VoiceDetectionService", "System error logged: $errorCode")    private suspend fun logVoiceDetection(

            } catch (e: Exception) {        wakeWord: String? = null,

                android.util.Log.e("VoiceDetectionService", "Failed to log system error", e)        phrase: String? = null,

            }        confidence: Double? = null,

        }        triggerSource: String

    }    ) {

            try {

    /**            if (currentUserId != null) {

     * Get Picovoice access key                val log = VoiceDetectionLog.create(

     */                    userId = currentUserId!!,

    private fun getPicovoiceAccessKey(): String {                    sessionId = currentSessionId,

        return try {                    wakeWord = wakeWord,

            // Try to get from metadata first                    phrase = phrase,

            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)                    confidence = confidence,

            appInfo.metaData?.getString("picovoice_access_key") ?: "YOUR_PICOVOICE_ACCESS_KEY"                    triggerSource = triggerSource

        } catch (e: Exception) {                )

            "YOUR_PICOVOICE_ACCESS_KEY"                

        }                // TODO: Save to repository

    }                Log.d(TAG, "Voice detection logged: $log")

                }

    /**        } catch (e: Exception) {

     * Get emergency context path for Rhino            Log.e(TAG, "Failed to log voice detection", e)

     */        }

    private fun getEmergencyContextPath(): String {    }

        return try {    

            // Return path to emergency context file in assets    /**

            "emergency_context.rhn" // This would be in assets folder     * Check audio recording permission

        } catch (e: Exception) {     */

            ""    private fun checkAudioPermission(): Boolean {

        }        return ActivityCompat.checkSelfPermission(

    }            this,

                android.Manifest.permission.RECORD_AUDIO

    /**        ) == PackageManager.PERMISSION_GRANTED

     * Generate unique session ID    }

     */    

    private fun generateSessionId(): String {    /**

        return "session_${System.currentTimeMillis()}_${(1000..9999).random()}"     * Add voice detection listener

    }     */

        fun addListener(listener: VoiceDetectionListener) {

    /**        listeners.add(listener)

     * Get current user ID (placeholder)    }

     */    

    private fun getCurrentUserId(): String? {    /**

        // TODO: Get from authentication service     * Remove voice detection listener

        return null     */

    }    fun removeListener(listener: VoiceDetectionListener) {

            listeners.remove(listener)

    /**    }

     * Cleanup resources    

     */    /**

    private fun cleanup() {     * Notify listeners of wake word detection

        try {     */

            porcupineManager?.delete()    private fun notifyWakeWordDetected(keyword: String, confidence: Float) {

            rhinoManager?.delete()        listeners.forEach { listener ->

                        try {

            if (wakeLock.isHeld) {                listener.onWakeWordDetected(keyword, confidence)

                wakeLock.release()            } catch (e: Exception) {

            }                Log.e(TAG, "Error notifying listener", e)

                        }

        } catch (e: Exception) {        }

            android.util.Log.e("VoiceDetectionService", "Error during cleanup", e)    }

        }    

    }    /**

         * Notify listeners of emergency phrase detection

    /**     */

     * Get service status    private fun notifyEmergencyPhraseDetected(intent: String, slots: Map<String, String>) {

     */        listeners.forEach { listener ->

    fun getServiceStatus(): VoiceDetectionStatus {            try {

        return VoiceDetectionStatus(                listener.onEmergencyPhraseDetected(intent, slots)

            isListening = isListening,            } catch (e: Exception) {

            isInitialized = isInitialized,                Log.e(TAG, "Error notifying listener", e)

            sessionId = currentSessionId,            }

            detectionCount = detectionCount,        }

            falsePositiveCount = falsePositiveCount,    }

            sessionDurationMs = if (sessionStartTime > 0) System.currentTimeMillis() - sessionStartTime else 0    

        )    /**

    }     * Notify listeners of voice activity detection

}     */

    private fun notifyVoiceActivityDetected(probability: Float) {

/**        listeners.forEach { listener ->

 * Voice detection service status data class            try {

 */                listener.onVoiceActivityDetected(probability)

data class VoiceDetectionStatus(            } catch (e: Exception) {

    val isListening: Boolean,                Log.e(TAG, "Error notifying listener", e)

    val isInitialized: Boolean,            }

    val sessionId: String?,        }

    val detectionCount: Int,    }

    val falsePositiveCount: Int,    

    val sessionDurationMs: Long    /**

)     * Notify listeners of listening state change
     */
    private fun notifyListeningStateChanged(isListening: Boolean) {
        listeners.forEach { listener ->
            try {
                listener.onListeningStateChanged(isListening)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener", e)
            }
        }
    }
    
    /**
     * Notify listeners of errors
     */
    private fun notifyError(message: String, exception: Throwable?) {
        listeners.forEach { listener ->
            try {
                listener.onError(message, exception)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener", e)
            }
        }
    }
    
    /**
     * Get current listening state
     */
    fun isCurrentlyListening(): Boolean = isListening.get()
    
    /**
     * Get detection statistics
     */
    fun getDetectionStats(): VoiceDetectionStats {
        return VoiceDetectionStats(
            totalDetections = detectionCount,
            falsePositives = falsePositiveCount,
            lastDetectionTime = lastDetectionTime,
            isListening = isListening.get(),
            isRecording = isRecording.get()
        )
    }
    
    /**
     * Clean up resources
     */
    private fun cleanup() {
        try {
            serviceScope.cancel()
            porcupineManager?.delete()
            rhinoManager?.delete()
            cobraManager?.delete()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}

/**
 * Voice detection statistics data class
 */
data class VoiceDetectionStats(
    val totalDetections: Int,
    val falsePositives: Int,
    val lastDetectionTime: Long,
    val isListening: Boolean,
    val isRecording: Boolean
)