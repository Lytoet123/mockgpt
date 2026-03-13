package com.lilstiffy.mockgps.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.google.android.gms.maps.model.LatLng
import com.lilstiffy.mockgps.controller.AntiDetectionConfig
import com.lilstiffy.mockgps.controller.MockController
import com.lilstiffy.mockgps.controller.MovementMode
import com.lilstiffy.mockgps.storage.StorageManager

class MockLocationService : Service() {

    companion object {
        const val TAG = "MockLocationService"
        var instance: MockLocationService? = null
    }

    var isMocking = false
        private set

    lateinit var latLng: LatLng

    private lateinit var mockController: MockController

    var antiDetectionConfig: AntiDetectionConfig = AntiDetectionConfig()
        set(value) {
            field = value
            if (::mockController.isInitialized) {
                mockController.config = value
            }
        }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        mockController = MockController(this)
        mockController.config = antiDetectionConfig
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return MockLocationBinder()
    }

    override fun onDestroy() {
        stopMockingLocation()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    fun toggleMocking() {
        if (isMocking) stopMockingLocation() else startMockingLocation()
    }

    fun updateMovementMode(mode: MovementMode) {
        antiDetectionConfig = antiDetectionConfig.copy(movementMode = mode)
    }

    fun updateAntiDetection(
        mockNetwork: Boolean? = null,
        realisticMotion: Boolean? = null,
        hideMock: Boolean? = null,
        gpsJitter: Boolean? = null,
        sensorConsistency: Boolean? = null,
        movementMode: MovementMode? = null
    ) {
        antiDetectionConfig = antiDetectionConfig.copy(
            mockNetworkProvider = mockNetwork ?: antiDetectionConfig.mockNetworkProvider,
            realisticMotion = realisticMotion ?: antiDetectionConfig.realisticMotion,
            hideMockProvider = hideMock ?: antiDetectionConfig.hideMockProvider,
            gpsJitter = gpsJitter ?: antiDetectionConfig.gpsJitter,
            sensorConsistency = sensorConsistency ?: antiDetectionConfig.sensorConsistency,
            movementMode = movementMode ?: antiDetectionConfig.movementMode
        )
    }

    private fun startMockingLocation() {
        if (!::latLng.isInitialized) {
            Toast.makeText(this, "No location selected", Toast.LENGTH_SHORT).show()
            return
        }

        StorageManager.addLocationToHistory(latLng)

        if (!isMocking) {
            val started = mockController.start { latLng }
            if (started) {
                isMocking = true
                Log.d(TAG, "Mock location started with anti-detection: $antiDetectionConfig")
            }
        }
    }

    private fun stopMockingLocation() {
        if (isMocking) {
            mockController.stop()
            isMocking = false
            Log.d(TAG, "Mock location stopped")
        }
    }

    inner class MockLocationBinder : Binder() {
        fun getService(): MockLocationService {
            return this@MockLocationService
        }
    }
}
