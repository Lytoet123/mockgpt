package com.lilstiffy.mockgps.controller

import android.content.Context
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Enhanced mock location controller with anti-detection bypass capabilities.
 *
 * Bypasses:
 * - Method 1 (Triangulation): Dual GPS + Network provider mocking
 * - Method 2 (Motion Analysis): Realistic speed/bearing via RealisticLocationSimulator
 * - Method 3 (Sensor Fusion): Consistent location properties
 * - Method 4 & 5 (Play Integrity / App detection): isFromMockProvider flag removal
 * - Method 6 (AI Pattern): Natural GPS jitter, altitude/accuracy variation
 */
class MockController(
    private val context: Context
) {
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val scope = CoroutineScope(Dispatchers.IO)
    private var updateJob: Job? = null
    private var gpsProviderRegistered = false
    private var networkProviderRegistered = false

    var config: AntiDetectionConfig = AntiDetectionConfig()
        set(value) {
            field = value
            simulator = RealisticLocationSimulator(value)
        }

    private var simulator = RealisticLocationSimulator(config)

    /**
     * Starts pushing mock location updates using the supplied [latLngProvider].
     * Returns true if updates could be scheduled.
     */
    fun start(latLngProvider: () -> LatLng): Boolean {
        if (updateJob?.isActive == true) {
            return true
        }

        if (!gpsProviderRegistered && !ensureTestProvider(GPS_PROVIDER)) {
            return false
        }

        // Register network provider for triangulation bypass (Method 1)
        if (config.mockNetworkProvider && !networkProviderRegistered) {
            ensureTestProvider(NETWORK_PROVIDER)
        }

        simulator.reset()

        updateJob = scope.launch {
            locationManager.setTestProviderEnabled(GPS_PROVIDER, true)
            if (config.mockNetworkProvider && networkProviderRegistered) {
                try {
                    locationManager.setTestProviderEnabled(NETWORK_PROVIDER, true)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not enable network provider: ${e.message}")
                }
            }

            while (isActive) {
                val latLng = latLngProvider()

                // Push realistic GPS location (Methods 2, 3, 5, 6)
                val gpsLocation = simulator.createGpsLocation(latLng)
                locationManager.setTestProviderLocation(GPS_PROVIDER, gpsLocation)

                // Push matching network location for triangulation bypass (Method 1)
                if (config.mockNetworkProvider && networkProviderRegistered) {
                    try {
                        val networkLocation = simulator.createNetworkLocation(latLng)
                        locationManager.setTestProviderLocation(NETWORK_PROVIDER, networkLocation)
                    } catch (e: Exception) {
                        Log.w(TAG, "Network provider update failed: ${e.message}")
                    }
                }

                delay(LOCATION_PUSH_DELAY_MS)
            }
        }

        return true
    }

    /**
     * Stops the active mock location loop.
     */
    fun stop() {
        updateJob?.cancel()
        updateJob = null

        if (gpsProviderRegistered) {
            try {
                locationManager.setTestProviderEnabled(GPS_PROVIDER, false)
            } catch (_: Exception) { }
        }
        if (networkProviderRegistered) {
            try {
                locationManager.setTestProviderEnabled(NETWORK_PROVIDER, false)
            } catch (_: Exception) { }
        }
    }

    private fun ensureTestProvider(providerName: String): Boolean {
        return try {
            val isGps = providerName == GPS_PROVIDER
            locationManager.addTestProvider(
                providerName,
                /* requiresNetwork = */ !isGps,
                /* requiresSatellite = */ isGps,
                /* requiresCell = */ !isGps,
                /* hasMonetaryCost = */ false,
                /* supportsAltitude = */ true,
                /* supportsSpeed = */ true,
                /* supportsBearing = */ true,
                if (isGps) ProviderProperties.POWER_USAGE_HIGH else ProviderProperties.POWER_USAGE_LOW,
                if (isGps) ProviderProperties.ACCURACY_FINE else ProviderProperties.ACCURACY_COARSE
            )
            if (isGps) gpsProviderRegistered = true else networkProviderRegistered = true
            true
        } catch (securityException: SecurityException) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "Mock location failed for $providerName. Check mock location app setting.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            false
        } catch (illegalArgumentException: IllegalArgumentException) {
            // The provider already exists – treat as success.
            if (providerName == GPS_PROVIDER) gpsProviderRegistered = true else networkProviderRegistered = true
            true
        }
    }

    companion object {
        private const val TAG = "MockController"
        private const val GPS_PROVIDER = LocationManager.GPS_PROVIDER
        private const val NETWORK_PROVIDER = LocationManager.NETWORK_PROVIDER
        const val LOCATION_PUSH_DELAY_MS = 200L
    }
}
