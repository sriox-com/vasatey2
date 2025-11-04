package com.sriox.vasatey.services.location

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Location manager for managing the location tracking service
 * Provides a high-level interface for location functionality
 */
class LocationManager(
    private val context: Context
) : LocationTrackingService.LocationTrackingListener {
    
    private var locationService: LocationTrackingService? = null
    private var isBound = false
    
    // State flows for reactive UI updates
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    
    private val _isEmergencyMode = MutableStateFlow(false)
    val isEmergencyMode: StateFlow<Boolean> = _isEmergencyMode.asStateFlow()
    
    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()
    
    private val _locationAccuracy = MutableStateFlow(LocationAccuracy.UNKNOWN)
    val locationAccuracy: StateFlow<LocationAccuracy> = _locationAccuracy.asStateFlow()
    
    private val _locationHistory = MutableStateFlow<List<LocationData>>(emptyList())
    val locationHistory: StateFlow<List<LocationData>> = _locationHistory.asStateFlow()
    
    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()
    
    // External listeners
    private val externalListeners = mutableListOf<LocationTrackingService.LocationTrackingListener>()
    
    /**
     * Service connection callback
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationTrackingService.LocationTrackingBinder
            locationService = binder.getService()
            locationService?.addListener(this@LocationManager)
            isBound = true
            
            // Update initial state
            locationService?.let { service ->
                _isTracking.value = service.isCurrentlyTracking()
                _isEmergencyMode.value = service.isInEmergencyMode()
                _currentLocation.value = service.currentLocation.value
                _locationAccuracy.value = service.locationAccuracy.value
                _locationHistory.value = service.locationHistory.value
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            locationService?.removeListener(this@LocationManager)
            locationService = null
            isBound = false
            _isTracking.value = false
            _isEmergencyMode.value = false
        }
    }
    
    /**
     * Start the location tracking service and bind to it
     */
    fun startService() {
        val intent = Intent(context, LocationTrackingService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    /**
     * Stop the location tracking service
     */
    fun stopService() {
        if (isBound) {
            locationService?.removeListener(this)
            context.unbindService(serviceConnection)
            isBound = false
        }
        
        val intent = Intent(context, LocationTrackingService::class.java)
        context.stopService(intent)
        
        _isTracking.value = false
        _isEmergencyMode.value = false
    }
    
    /**
     * Start location tracking
     */
    fun startTracking(emergencyMode: Boolean = false) {
        locationService?.startLocationTracking(emergencyMode)
    }
    
    /**
     * Stop location tracking
     */
    fun stopTracking() {
        locationService?.stopLocationTracking()
    }
    
    /**
     * Enable emergency mode
     */
    fun enableEmergencyMode() {
        locationService?.enableEmergencyMode()
    }
    
    /**
     * Disable emergency mode
     */
    fun disableEmergencyMode() {
        locationService?.disableEmergencyMode()
    }
    
    /**
     * Get current location asynchronously
     */
    suspend fun getCurrentLocation(): LocationData? {
        return locationService?.getCurrentLocationAsync()
    }
    
    /**
     * Calculate distance between two locations
     */
    fun calculateDistance(location1: LocationData, location2: LocationData): Float? {
        return locationService?.calculateDistance(location1, location2)
    }
    
    /**
     * Add external listener
     */
    fun addListener(listener: LocationTrackingService.LocationTrackingListener) {
        externalListeners.add(listener)
    }
    
    /**
     * Remove external listener
     */
    fun removeListener(listener: LocationTrackingService.LocationTrackingListener) {
        externalListeners.remove(listener)
    }
    
    /**
     * Check if service is bound and ready
     */
    fun isServiceReady(): Boolean = isBound && locationService != null
    
    /**
     * Get location tracking statistics
     */
    fun getLocationStats(): LocationStats? {
        val history = _locationHistory.value
        val current = _currentLocation.value
        
        return if (history.isNotEmpty() && current != null) {
            LocationStats(
                totalLocations = history.size,
                accuracy = current.accuracy,
                lastUpdateTime = current.timestamp,
                emergencyModeActive = _isEmergencyMode.value,
                trackingActive = _isTracking.value,
                averageAccuracy = history.map { it.accuracy }.average().toFloat()
            )
        } else null
    }
    
    // LocationTrackingListener implementation
    override fun onLocationUpdated(location: LocationData) {
        _currentLocation.value = location
        
        // Update history
        val currentHistory = _locationHistory.value.toMutableList()
        currentHistory.add(0, location)
        if (currentHistory.size > 50) { // Keep last 50 locations
            currentHistory.removeAt(currentHistory.size - 1)
        }
        _locationHistory.value = currentHistory
        
        // Forward to external listeners
        externalListeners.forEach { listener ->
            try {
                listener.onLocationUpdated(location)
            } catch (e: Exception) {
                // Log error but continue
            }
        }
    }
    
    override fun onLocationAccuracyChanged(accuracy: LocationAccuracy) {
        _locationAccuracy.value = accuracy
        
        // Forward to external listeners
        externalListeners.forEach { listener ->
            try {
                listener.onLocationAccuracyChanged(accuracy)
            } catch (e: Exception) {
                // Log error but continue
            }
        }
    }
    
    override fun onGeofenceEntered(geofenceId: String, location: LocationData) {
        // Forward to external listeners
        externalListeners.forEach { listener ->
            try {
                listener.onGeofenceEntered(geofenceId, location)
            } catch (e: Exception) {
                // Log error but continue
            }
        }
    }
    
    override fun onGeofenceExited(geofenceId: String, location: LocationData) {
        // Forward to external listeners
        externalListeners.forEach { listener ->
            try {
                listener.onGeofenceExited(geofenceId, location)
            } catch (e: Exception) {
                // Log error but continue
            }
        }
    }
    
    override fun onLocationError(error: String, exception: Throwable?) {
        _errorState.value = error
        
        // Forward to external listeners
        externalListeners.forEach { listener ->
            try {
                listener.onLocationError(error, exception)
            } catch (e: Exception) {
                // Log error but continue
            }
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
 * Location tracking statistics
 */
data class LocationStats(
    val totalLocations: Int,
    val accuracy: Float,
    val lastUpdateTime: String,
    val emergencyModeActive: Boolean,
    val trackingActive: Boolean,
    val averageAccuracy: Float
)