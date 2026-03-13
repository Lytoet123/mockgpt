package com.lilstiffy.mockgps.controller

import android.content.Context
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random

/**
 * Enhanced mock location controller with anti-detection bypass capabilities.
 *
 * Bypasses:
 * - Method 1 (Triangulation): GPS + Network + Passive provider mocking
 * - Method 2 (Motion Analysis): Realistic speed/bearing via RealisticLocationSimulator
 * - Method 3 (Sensor Fusion): Consistent location properties
 * - Method 4 & 5 (Play Integrity / App detection): isFromMockProvider flag removal
 * - Method 6 (AI Pattern): Natural GPS jitter, altitude/accuracy variation, timing jitter
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
    private var passiveProviderRegistered = false

    private val random = Random(System.nanoTime())

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

        // Register passive provider to cover apps listening passively
        if (!passiveProviderRegistered) {
            ensureTestProvider(PASSIVE_PROVIDER)
        }

        simulator.reset()

        updateJob = scope.launch {
            locationManager.setTestProviderEnabled(GPS_PROVIDER, true)

            if (config.mockNetworkProvider && networkProviderRegistered) {
                try {
                    locationManager.setTestProviderEnabled(NETWORK_PROVIDER, true)
                } catch (_: Exception) { }
            }

            if (passiveProviderRegistered) {
                try {
                    locationManager.setTestProviderEnabled(PASSIVE_PROVIDER, true)
                } catch (_: Exception) { }
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
                    } catch (_: Exception) { }
                }

                // Push to passive provider
                if (passiveProviderRegistered) {
                    try {
                        val passiveLocation = simulator.createGpsLocation(latLng).apply {
                            provider = PASSIVE_PROVIDER
                        }
                        locationManager.setTestProviderLocation(PASSIVE_PROVIDER, passiveLocation)
                    } catch (_: Exception) { }
                }

                // Timing jitter: 180-220ms instead of constant 200ms (Method 6 - AI bypass)
                val jitteredDelay = BASE_DELAY_MS + random.nextInt(JITTER_RANGE_MS) - (JITTER_RANGE_MS / 2)
                delay(jitteredDelay.toLong())
            }
        }

        return true
    }

    /**
     * Stops the active mock location loop and cleans up test providers.
     */
    fun stop() {
        updateJob?.cancel()
        updateJob = null

        // Reset simulator state for clean restart
        simulator.reset()

        // Disable and REMOVE test providers to prevent detection
        if (gpsProviderRegistered) {
            try {
                locationManager.setTestProviderEnabled(GPS_PROVIDER, false)
                locationManager.removeTestProvider(GPS_PROVIDER)
                gpsProviderRegistered = false
            } catch (_: Exception) { }
        }
        if (networkProviderRegistered) {
            try {
                locationManager.setTestProviderEnabled(NETWORK_PROVIDER, false)
                locationManager.removeTestProvider(NETWORK_PROVIDER)
                networkProviderRegistered = false
            } catch (_: Exception) { }
        }
        if (passiveProviderRegistered) {
            try {
                locationManager.setTestProviderEnabled(PASSIVE_PROVIDER, false)
                locationManager.removeTestProvider(PASSIVE_PROVIDER)
                passiveProviderRegistered = false
            } catch (_: Exception) { }
        }
    }

    private fun ensureTestProvider(providerName: String): Boolean {
        return try {
            val isGps = providerName == GPS_PROVIDER
            val isPassive = providerName == PASSIVE_PROVIDER
            locationManager.addTestProvider(
                providerName,
                /* requiresNetwork = */ !isGps && !isPassive,
                /* requiresSatellite = */ isGps,
                /* requiresCell = */ !isGps && !isPassive,
                /* hasMonetaryCost = */ false,
                /* supportsAltitude = */ true,
                /* supportsSpeed = */ true,
                /* supportsBearing = */ true,
                if (isGps) ProviderProperties.POWER_USAGE_HIGH else ProviderProperties.POWER_USAGE_LOW,
                if (isGps) ProviderProperties.ACCURACY_FINE else ProviderProperties.ACCURACY_COARSE
            )
            when (providerName) {
                GPS_PROVIDER -> gpsProviderRegistered = true
                NETWORK_PROVIDER -> networkProviderRegistered = true
                PASSIVE_PROVIDER -> passiveProviderRegistered = true
            }
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
            when (providerName) {
                GPS_PROVIDER -> gpsProviderRegistered = true
                NETWORK_PROVIDER -> networkProviderRegistered = true
                PASSIVE_PROVIDER -> passiveProviderRegistered = true
            }
            true
        }
    }

    companion object {
        private const val GPS_PROVIDER = LocationManager.GPS_PROVIDER
        private const val NETWORK_PROVIDER = LocationManager.NETWORK_PROVIDER
        private const val PASSIVE_PROVIDER = LocationManager.PASSIVE_PROVIDER
        private const val BASE_DELAY_MS = 200
        private const val JITTER_RANGE_MS = 40 // ±20ms jitter
        const val LOCATION_PUSH_DELAY_MS = 200L
    }
}
