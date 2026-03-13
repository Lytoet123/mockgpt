package com.lilstiffy.mockgps.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.provider.ProviderProperties
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.lilstiffy.mockgps.controller.AntiDetectionConfig
import com.lilstiffy.mockgps.controller.RealisticLocationSimulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Random

/**
 * Legacy location mocker - updated with FusedLocation + anti-detection bypass.
 */
class LocationMocker(context: Context) {
    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var isMocking = false
    lateinit var latLng: LatLng
    var listener: LocationListener? = null
    private val random = Random(System.nanoTime())
    private var fusedMockEnabled = false

    var config: AntiDetectionConfig = AntiDetectionConfig()
        set(value) {
            field = value
            simulator = RealisticLocationSimulator(value)
        }

    private var simulator = RealisticLocationSimulator(config)
    private var gpsRegistered = false
    private var networkRegistered = false
    private var passiveRegistered = false

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

            // Register providers ONCE before starting the loop
            registerProvider(LocationManager.GPS_PROVIDER)
            if (config.mockNetworkProvider) {
                registerProvider(LocationManager.NETWORK_PROVIDER)
            }
            registerProvider(LocationManager.PASSIVE_PROVIDER)

            // Enable providers
            try { locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true) } catch (_: Exception) {}
            if (config.mockNetworkProvider && networkRegistered) {
                try { locationManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true) } catch (_: Exception) {}
            }
            if (passiveRegistered) {
                try { locationManager.setTestProviderEnabled(LocationManager.PASSIVE_PROVIDER, true) } catch (_: Exception) {}
            }

            // Enable FusedLocationProviderClient mock mode
            enableFusedMock()

            CoroutineScope(Dispatchers.IO).launch {
                mockLocation()
            }
        }
    }

    private fun stopMockingLocation() {
        if (isMocking) {
            isMocking = false
            simulator.reset()
            disableFusedMock()
            removeProvider(LocationManager.GPS_PROVIDER)
            removeProvider(LocationManager.NETWORK_PROVIDER)
            removeProvider(LocationManager.PASSIVE_PROVIDER)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableFusedMock() {
        try {
            fusedClient.setMockMode(true)
                .addOnSuccessListener { fusedMockEnabled = true }
                .addOnFailureListener { fusedMockEnabled = false }
        } catch (_: Exception) { fusedMockEnabled = false }
    }

    @SuppressLint("MissingPermission")
    private fun disableFusedMock() {
        if (fusedMockEnabled) {
            try {
                fusedClient.setMockMode(false)
                fusedMockEnabled = false
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun pushFusedLocation(baseLocation: Location) {
        if (!fusedMockEnabled) return
        try {
            val fusedLocation = Location(baseLocation).apply {
                provider = "fused"
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = System.nanoTime()
            }
            fusedClient.setMockLocation(fusedLocation)
        } catch (_: Exception) {}
    }

    private fun registerProvider(providerName: String) {
        val isGps = providerName == LocationManager.GPS_PROVIDER
        val isPassive = providerName == LocationManager.PASSIVE_PROVIDER
        try {
            locationManager.addTestProvider(
                providerName,
                !isGps && !isPassive,
                isGps,
                !isGps && !isPassive,
                false,
                true,
                true,
                true,
                if (isGps) ProviderProperties.POWER_USAGE_HIGH else ProviderProperties.POWER_USAGE_LOW,
                if (isGps) ProviderProperties.ACCURACY_FINE else ProviderProperties.ACCURACY_COARSE
            )
        } catch (_: IllegalArgumentException) {
            // Provider already exists
        }
        when (providerName) {
            LocationManager.GPS_PROVIDER -> gpsRegistered = true
            LocationManager.NETWORK_PROVIDER -> networkRegistered = true
            LocationManager.PASSIVE_PROVIDER -> passiveRegistered = true
        }
    }

    private fun removeProvider(providerName: String) {
        try {
            locationManager.setTestProviderEnabled(providerName, false)
            locationManager.removeTestProvider(providerName)
        } catch (_: Exception) {}
        when (providerName) {
            LocationManager.GPS_PROVIDER -> gpsRegistered = false
            LocationManager.NETWORK_PROVIDER -> networkRegistered = false
            LocationManager.PASSIVE_PROVIDER -> passiveRegistered = false
        }
    }

    private suspend fun mockLocation() {
        while (isMocking) {
            // GPS provider
            val gpsLocation = simulator.createGpsLocation(latLng)
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, gpsLocation)

            // Network provider for triangulation bypass
            if (config.mockNetworkProvider && networkRegistered) {
                try {
                    val networkLocation = simulator.createNetworkLocation(latLng)
                    locationManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, networkLocation)
                } catch (_: Exception) {}
            }

            // Passive provider
            if (passiveRegistered) {
                try {
                    val passiveLocation = simulator.createGpsLocation(latLng).apply {
                        provider = LocationManager.PASSIVE_PROVIDER
                    }
                    locationManager.setTestProviderLocation(LocationManager.PASSIVE_PROVIDER, passiveLocation)
                } catch (_: Exception) {}
            }

            // FusedLocationProviderClient (CRITICAL for modern apps)
            pushFusedLocation(gpsLocation)

            // Timing jitter: 180-220ms
            val jitteredDelay = 200L + random.nextInt(40) - 20
            kotlinx.coroutines.delay(jitteredDelay)
        }
    }
}
