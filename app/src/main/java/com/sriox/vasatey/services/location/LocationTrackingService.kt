package com.sriox.vasatey.services.location

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.sriox.vasatey.R
import com.sriox.vasatey.VasateyApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

/**
 * Location tracking service for emergency response
 * Provides accurate location data with multiple providers and background tracking
 */
class LocationTrackingService : Service() {
    
    companion object {
        private const val TAG = "LocationService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "location_tracking_service"
        
        // Location update intervals
        private const val NORMAL_UPDATE_INTERVAL = 60000L // 1 minute
        private const val EMERGENCY_UPDATE_INTERVAL = 5000L // 5 seconds
        private const val FASTEST_UPDATE_INTERVAL = 2000L // 2 seconds
        
        // Location accuracy requirements
        private const val HIGH_ACCURACY_THRESHOLD = 50f // 50 meters
        private const val ACCEPTABLE_ACCURACY_THRESHOLD = 100f // 100 meters
        
        // Geofence settings
        private const val GEOFENCE_RADIUS = 200f // 200 meters
        private const val GEOFENCE_EXPIRATION = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    // Service binding
    private val binder = LocationTrackingBinder()
    
    // Location providers
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private lateinit var geofencingClient: GeofencingClient
    private var geocoder: Geocoder? = null
    
    // Location callbacks
    private var locationCallback: LocationCallback? = null
    private var locationListener: LocationListener? = null
    
    // Service state
    private val isTracking = AtomicBoolean(false)
    private val isEmergencyMode = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Location data
    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()
    
    private val _locationAccuracy = MutableStateFlow(LocationAccuracy.UNKNOWN)
    val locationAccuracy: StateFlow<LocationAccuracy> = _locationAccuracy.asStateFlow()
    
    private val _locationHistory = MutableStateFlow<List<LocationData>>(emptyList())
    val locationHistory: StateFlow<List<LocationData>> = _locationHistory.asStateFlow()
    
    // Application dependencies
    private lateinit var app: VasateyApplication
    
    // Location listeners
    private val listeners = mutableListOf<LocationTrackingListener>()
    
    inner class LocationTrackingBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }
    
    interface LocationTrackingListener {
        fun onLocationUpdated(location: LocationData)
        fun onLocationAccuracyChanged(accuracy: LocationAccuracy)
        fun onGeofenceEntered(geofenceId: String, location: LocationData)
        fun onGeofenceExited(geofenceId: String, location: LocationData)
        fun onLocationError(error: String, exception: Throwable?)
    }
    
    override fun onCreate() {
        super.onCreate()
        app = application as VasateyApplication
        
        initializeLocationServices()
        startForegroundService()
        
        Log.d(TAG, "Location Tracking Service created")
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_TRACKING" -> {
                val emergencyMode = intent.getBooleanExtra("emergency_mode", false)
                startLocationTracking(emergencyMode)
            }
            "STOP_TRACKING" -> stopLocationTracking()
            "EMERGENCY_MODE" -> enableEmergencyMode()
            "NORMAL_MODE" -> disableEmergencyMode()
        }
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopLocationTracking()
        cleanup()
        Log.d(TAG, "Location Tracking Service destroyed")
    }
    
    /**
     * Initialize location services
     */
    private fun initializeLocationServices() {
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            geofencingClient = LocationServices.getGeofencingClient(this)
            
            if (Geocoder.isPresent()) {
                geocoder = Geocoder(this, Locale.getDefault())
            }
            
            Log.d(TAG, "Location services initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize location services", e)
            notifyError("Failed to initialize location services", e)
        }
    }
    
    /**
     * Start foreground service with notification
     */
    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vasatey Location Tracking")
            .setContentText("Monitoring location for emergency detection")
            .setSmallIcon(R.drawable.ic_location_on)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    /**
     * Start location tracking
     */
    fun startLocationTracking(emergencyMode: Boolean = false) {
        if (!checkLocationPermissions()) {
            notifyError("Location permissions not granted", null)
            return
        }
        
        if (isTracking.get()) {
            Log.d(TAG, "Location tracking already active")
            return
        }
        
        isEmergencyMode.set(emergencyMode)
        isTracking.set(true)
        
        serviceScope.launch {
            try {
                startFusedLocationTracking()
                startLegacyLocationTracking()
                
                // Get last known location immediately
                getLastKnownLocation()
                
                Log.d(TAG, "Location tracking started (Emergency mode: $emergencyMode)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start location tracking", e)
                notifyError("Failed to start location tracking", e)
                isTracking.set(false)
            }
        }
    }
    
    /**
     * Stop location tracking
     */
    fun stopLocationTracking() {
        if (!isTracking.get()) {
            Log.d(TAG, "Location tracking already stopped")
            return
        }
        
        isTracking.set(false)
        isEmergencyMode.set(false)
        
        try {
            locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
            locationListener?.let { locationManager.removeUpdates(it) }
            
            Log.d(TAG, "Location tracking stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location tracking", e)
        }
    }
    
    /**
     * Enable emergency mode for frequent updates
     */
    fun enableEmergencyMode() {
        if (!isEmergencyMode.get()) {
            isEmergencyMode.set(true)
            
            if (isTracking.get()) {
                // Restart tracking with emergency settings
                stopLocationTracking()
                startLocationTracking(true)
            }
            
            Log.d(TAG, "Emergency mode enabled")
        }
    }
    
    /**
     * Disable emergency mode
     */
    fun disableEmergencyMode() {
        if (isEmergencyMode.get()) {
            isEmergencyMode.set(false)
            
            if (isTracking.get()) {
                // Restart tracking with normal settings
                stopLocationTracking()
                startLocationTracking(false)
            }
            
            Log.d(TAG, "Emergency mode disabled")
        }
    }
    
    /**
     * Start Fused Location Provider tracking
     */
    private suspend fun startFusedLocationTracking() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        val updateInterval = if (isEmergencyMode.get()) {
            EMERGENCY_UPDATE_INTERVAL
        } else {
            NORMAL_UPDATE_INTERVAL
        }
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            updateInterval
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL)
            setMaxUpdateDelayMillis(updateInterval * 2)
            setWaitForAccurateLocation(true)
        }.build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                
                locationResult.lastLocation?.let { location ->
                    processLocationUpdate(location, LocationProvider.FUSED)
                }
            }
            
            override fun onLocationAvailability(availability: LocationAvailability) {
                super.onLocationAvailability(availability)
                
                if (!availability.isLocationAvailable) {
                    Log.w(TAG, "Location not available from Fused Provider")
                }
            }
        }
        
        locationCallback?.let { callback ->
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        }
    }
    
    /**
     * Start legacy LocationManager tracking as backup
     */
    private fun startLegacyLocationTracking() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        val updateInterval = if (isEmergencyMode.get()) {
            EMERGENCY_UPDATE_INTERVAL
        } else {
            NORMAL_UPDATE_INTERVAL
        }
        
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                processLocationUpdate(location, LocationProvider.GPS)
            }
            
            override fun onProviderEnabled(provider: String) {
                Log.d(TAG, "Location provider enabled: $provider")
            }
            
            override fun onProviderDisabled(provider: String) {
                Log.w(TAG, "Location provider disabled: $provider")
            }
            
            @Deprecated("Deprecated in API level 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                Log.d(TAG, "Location provider status changed: $provider, status: $status")
            }
        }
        
        locationListener?.let { listener ->
            // Try GPS first
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    updateInterval,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            }
            
            // Also use Network provider for faster initial fix
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    updateInterval,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            }
        }
    }
    
    /**
     * Get last known location immediately
     */
    private suspend fun getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        try {
            val lastLocation: Task<Location> = fusedLocationClient.lastLocation
            lastLocation.addOnSuccessListener { location ->
                location?.let {
                    processLocationUpdate(it, LocationProvider.LAST_KNOWN)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get last known location", e)
        }
    }
    
    /**
     * Process location update from any provider
     */
    private fun processLocationUpdate(location: Location, provider: LocationProvider) {
        serviceScope.launch {
            try {
                val locationData = createLocationData(location, provider)
                
                // Update current location
                _currentLocation.value = locationData
                
                // Update accuracy status
                updateLocationAccuracy(location.accuracy)
                
                // Add to history
                addToLocationHistory(locationData)
                
                // Notify listeners
                notifyLocationUpdated(locationData)
                
                // Reverse geocode for address if needed
                if (provider != LocationProvider.LAST_KNOWN) {
                    reverseGeocodeLocation(locationData)
                }
                
                Log.d(TAG, "Location updated: ${locationData.latitude}, ${locationData.longitude} (${provider.name})")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing location update", e)
                notifyError("Error processing location update", e)
            }
        }
    }
    
    /**
     * Create LocationData from Android Location
     */
    private fun createLocationData(location: Location, provider: LocationProvider): LocationData {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
        
        return LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            altitude = if (location.hasAltitude()) location.altitude else null,
            bearing = if (location.hasBearing()) location.bearing else null,
            speed = if (location.hasSpeed()) location.speed else null,
            provider = provider.name.lowercase(),
            timestamp = timestamp,
            isEmergencyMode = isEmergencyMode.get(),
            address = null // Will be filled by reverse geocoding
        )
    }
    
    /**
     * Update location accuracy status
     */
    private fun updateLocationAccuracy(accuracy: Float) {
        val newAccuracy = when {
            accuracy <= HIGH_ACCURACY_THRESHOLD -> LocationAccuracy.HIGH
            accuracy <= ACCEPTABLE_ACCURACY_THRESHOLD -> LocationAccuracy.MEDIUM
            else -> LocationAccuracy.LOW
        }
        
        if (_locationAccuracy.value != newAccuracy) {
            _locationAccuracy.value = newAccuracy
            notifyLocationAccuracyChanged(newAccuracy)
        }
    }
    
    /**
     * Add location to history with size limit
     */
    private fun addToLocationHistory(locationData: LocationData) {
        val currentHistory = _locationHistory.value.toMutableList()
        currentHistory.add(0, locationData) // Add to beginning
        
        // Keep only recent locations (limit to 100)
        if (currentHistory.size > 100) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        
        _locationHistory.value = currentHistory
    }
    
    /**
     * Reverse geocode location to get address
     */
    private suspend fun reverseGeocodeLocation(locationData: LocationData) {
        geocoder?.let { geocoder ->
            try {
                val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Use new async API for Android 13+
                    withContext(Dispatchers.IO) {
                        suspendCancellableCoroutine { continuation ->
                            geocoder.getFromLocation(
                                locationData.latitude,
                                locationData.longitude,
                                1
                            ) { addresses ->
                                continuation.resume(addresses, null)
                            }
                        }
                    }
                } else {
                    // Use legacy synchronous API
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(locationData.latitude, locationData.longitude, 1)
                    }
                }
                
                addresses?.firstOrNull()?.let { address ->
                    val formattedAddress = formatAddress(address)
                    val updatedLocationData = locationData.copy(address = formattedAddress)
                    _currentLocation.value = updatedLocationData
                    
                    // Update in history as well
                    val updatedHistory = _locationHistory.value.toMutableList()
                    val index = updatedHistory.indexOfFirst { it.timestamp == locationData.timestamp }
                    if (index >= 0) {
                        updatedHistory[index] = updatedLocationData
                        _locationHistory.value = updatedHistory
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Reverse geocoding failed", e)
            }
        }
    }
    
    /**
     * Format address from Geocoder result
     */
    private fun formatAddress(address: Address): String {
        val addressParts = mutableListOf<String>()
        
        address.getAddressLine(0)?.let { addressParts.add(it) }
        
        if (addressParts.isEmpty()) {
            // Build address manually if getAddressLine is not available
            address.subThoroughfare?.let { addressParts.add(it) }
            address.thoroughfare?.let { addressParts.add(it) }
            address.locality?.let { addressParts.add(it) }
            address.adminArea?.let { addressParts.add(it) }
            address.postalCode?.let { addressParts.add(it) }
            address.countryName?.let { addressParts.add(it) }
        }
        
        return addressParts.joinToString(", ").ifEmpty { "Unknown location" }
    }
    
    /**
     * Check location permissions
     */
    private fun checkLocationPermissions(): Boolean {
        val fineLocationGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val coarseLocationGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        return fineLocationGranted || coarseLocationGranted
    }
    
    /**
     * Get current location synchronously
     */
    suspend fun getCurrentLocationAsync(): LocationData? {
        return if (isTracking.get()) {
            _currentLocation.value
        } else {
            // Get one-time location
            getOneTimeLocation()
        }
    }
    
    /**
     * Get one-time location without starting continuous tracking
     */
    private suspend fun getOneTimeLocation(): LocationData? {
        if (!checkLocationPermissions()) {
            return null
        }
        
        return withContext(Dispatchers.Main) {
            try {
                suspendCancellableCoroutine { continuation ->
                    if (ActivityCompat.checkSelfPermission(
                            this@LocationTrackingService,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        continuation.resume(null, null)
                        return@suspendCancellableCoroutine
                    }
                    
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        null
                    ).addOnSuccessListener { location ->
                        location?.let {
                            val locationData = createLocationData(it, LocationProvider.ONE_TIME)
                            continuation.resume(locationData, null)
                        } ?: continuation.resume(null, null)
                    }.addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to get one-time location", exception)
                        continuation.resume(null, null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting one-time location", e)
                null
            }
        }
    }
    
    /**
     * Calculate distance between two locations
     */
    fun calculateDistance(location1: LocationData, location2: LocationData): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            location1.latitude,
            location1.longitude,
            location2.latitude,
            location2.longitude,
            results
        )
        return results[0]
    }
    
    /**
     * Add location tracking listener
     */
    fun addListener(listener: LocationTrackingListener) {
        listeners.add(listener)
    }
    
    /**
     * Remove location tracking listener
     */
    fun removeListener(listener: LocationTrackingListener) {
        listeners.remove(listener)
    }
    
    /**
     * Notify listeners of location update
     */
    private fun notifyLocationUpdated(location: LocationData) {
        listeners.forEach { listener ->
            try {
                listener.onLocationUpdated(location)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener", e)
            }
        }
    }
    
    /**
     * Notify listeners of accuracy change
     */
    private fun notifyLocationAccuracyChanged(accuracy: LocationAccuracy) {
        listeners.forEach { listener ->
            try {
                listener.onLocationAccuracyChanged(accuracy)
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
                listener.onLocationError(message, exception)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener", e)
            }
        }
    }
    
    /**
     * Get tracking status
     */
    fun isCurrentlyTracking(): Boolean = isTracking.get()
    
    /**
     * Get emergency mode status
     */
    fun isInEmergencyMode(): Boolean = isEmergencyMode.get()
    
    /**
     * Clean up resources
     */
    private fun cleanup() {
        try {
            serviceScope.cancel()
            locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
            locationListener?.let { locationManager.removeUpdates(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}

/**
 * Location data class
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double? = null,
    val bearing: Float? = null,
    val speed: Float? = null,
    val provider: String,
    val timestamp: String,
    val isEmergencyMode: Boolean = false,
    val address: String? = null
)

/**
 * Location provider enumeration
 */
enum class LocationProvider {
    FUSED,
    GPS,
    NETWORK,
    LAST_KNOWN,
    ONE_TIME
}

/**
 * Location accuracy levels
 */
enum class LocationAccuracy(val displayName: String) {
    HIGH("High Accuracy"),
    MEDIUM("Medium Accuracy"),
    LOW("Low Accuracy"),
    UNKNOWN("Unknown")
}