package com.lilstiffy.mockgps.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.lilstiffy.mockgps.controller.AntiDetectionConfig
import com.lilstiffy.mockgps.controller.RealisticLocationSimulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Legacy location mocker - updated to use RealisticLocationSimulator
 * for anti-detection bypass capabilities.
 */
class LocationMocker(context: Context) {
    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private var isMocking = false
    lateinit var latLng: LatLng
    var listener: LocationListener? = null

    var config: AntiDetectionConfig = AntiDetectionConfig()
        set(value) {
            field = value
            simulator = RealisticLocationSimulator(value)
        }

    private var simulator = RealisticLocationSimulator(config)

    fun toggleMocking() {
        if (isMocking)
            stopMockingLocation()
        else
            startMockingLocation()
    }

    @SuppressLint("MissingPermission")
    private fun startMockingLocation() {
        listener?.let {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                50L,
                0f,
                it
            )
        }
        if (!isMocking) {
            isMocking = true
            simulator.reset()
            CoroutineScope(Dispatchers.IO).launch {
                mockLocation()
            }
            Log.d("LocationMocker", "Mock location started with anti-detection")
        }
    }

    private fun stopMockingLocation() {
        if (isMocking) {
            isMocking = false
            Log.d("LocationMocker", "Mock location stopped")
        }
    }

    private fun addTestProvider(providerName: String) {
        val isGps = providerName == LocationManager.GPS_PROVIDER
        try {
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
        } catch (_: IllegalArgumentException) {
            // Provider already exists
        }
    }

    private suspend fun mockLocation() {
        while (isMocking) {
            // GPS provider
            addTestProvider(LocationManager.GPS_PROVIDER)
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)

            val gpsLocation = simulator.createGpsLocation(latLng)
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, gpsLocation)

            // Network provider for triangulation bypass
            if (config.mockNetworkProvider) {
                try {
                    addTestProvider(LocationManager.NETWORK_PROVIDER)
                    locationManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
                    val networkLocation = simulator.createNetworkLocation(latLng)
                    locationManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, networkLocation)
                } catch (e: Exception) {
                    Log.w("LocationMocker", "Network provider failed: ${e.message}")
                }
            }

            kotlinx.coroutines.delay(200)
        }
    }
}
