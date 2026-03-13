package com.lilstiffy.mockgps.controller

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import com.google.android.gms.maps.model.LatLng
import java.lang.reflect.Field
import kotlin.math.*
import java.util.Random

/**
 * Generates realistic Location objects that bypass common GPS spoofing detection:
 *
 * - Method 1 (Triangulation): Produces locations for both GPS and Network providers
 * - Method 2 (Motion Analysis): Calculates realistic speed/bearing between points
 * - Method 3 (Sensor Fusion): Ensures speed, bearing, altitude are internally consistent
 * - Method 5 (isFromMockProvider): Removes mock provider flag via reflection
 * - Method 6 (AI Pattern): Adds natural GPS jitter, altitude drift, accuracy fluctuation
 */
class RealisticLocationSimulator(
    private val config: AntiDetectionConfig = AntiDetectionConfig()
) {
    private var previousLocation: Location? = null
    private var previousTimestamp: Long = 0L
    private var cumulativeAltitudeDrift = 0.0
    private var smoothedSpeed = 0f
    private var smoothedBearing = 0f
    private val random = Random(System.nanoTime())

    // Perlin-like noise state for smooth jitter
    private var jitterPhaseX = random.nextDouble() * 2 * Math.PI
    private var jitterPhaseY = random.nextDouble() * 2 * Math.PI

    /**
     * Creates a realistic GPS Location object from the given coordinates.
     */
    fun createGpsLocation(latLng: LatLng): Location {
        return createLocation(LocationManager.GPS_PROVIDER, latLng)
    }

    /**
     * Creates a realistic Network Location object offset slightly from GPS
     * to simulate cell tower / WiFi triangulation (Method 1 bypass).
     */
    fun createNetworkLocation(latLng: LatLng): Location {
        if (!config.mockNetworkProvider) return createLocation(LocationManager.NETWORK_PROVIDER, latLng)

        // Network location is typically 30-100m less accurate than GPS
        val networkOffset = randomOffset(metersRadius = 30.0 + random.nextDouble() * 70.0)
        val networkLatLng = LatLng(
            latLng.latitude + networkOffset.first,
            latLng.longitude + networkOffset.second
        )
        return createLocation(LocationManager.NETWORK_PROVIDER, networkLatLng).apply {
            accuracy = 30f + random.nextFloat() * 70f // 30-100m accuracy for network
        }
    }

    private fun createLocation(provider: String, rawLatLng: LatLng): Location {
        val now = System.currentTimeMillis()
        val nanoTime = System.nanoTime()

        // Apply GPS jitter (Method 6 - AI pattern bypass)
        val latLng = if (config.gpsJitter && provider == LocationManager.GPS_PROVIDER) {
            applyRealisticJitter(rawLatLng)
        } else {
            rawLatLng
        }

        val location = Location(provider).apply {
            latitude = latLng.latitude
            longitude = latLng.longitude
            time = now
            elapsedRealtimeNanos = nanoTime
        }

        // Calculate realistic speed and bearing (Method 2 & 3)
        if (config.realisticMotion || config.sensorConsistency) {
            applyMotionData(location, now)
        }

        // Realistic altitude with natural variation (Method 3)
        if (config.altitudeVariation) {
            location.altitude = generateRealisticAltitude()
        } else {
            location.altitude = config.baseAltitudeMeters
        }

        // Realistic accuracy fluctuation (Method 6)
        if (config.accuracyVariation && provider == LocationManager.GPS_PROVIDER) {
            location.accuracy = generateRealisticAccuracy()
        } else if (provider == LocationManager.GPS_PROVIDER) {
            location.accuracy = config.baseAccuracyMeters
        }

        // Add satellite info extras for GPS provider
        if (provider == LocationManager.GPS_PROVIDER) {
            location.extras = Bundle().apply {
                putInt("satellites", 7 + random.nextInt(8)) // 7-14 satellites
                putInt("satellitesInFix", 5 + random.nextInt(6)) // 5-10 in fix
            }
        }

        // Hide mock provider flag (Method 4 & 5 bypass)
        if (config.hideMockProvider) {
            removeMockProviderFlag(location)
        }

        previousLocation = Location(location) // copy
        previousTimestamp = now

        return location
    }

    /**
     * Applies smooth, realistic GPS jitter using sine-wave-based pseudo-noise.
     * Real GPS signals have correlated noise, not purely random jumps.
     */
    private fun applyRealisticJitter(latLng: LatLng): LatLng {
        // Advance phase slowly for smooth drift
        jitterPhaseX += 0.02 + random.nextDouble() * 0.03
        jitterPhaseY += 0.02 + random.nextDouble() * 0.03

        // Combine multiple frequencies for natural-looking noise
        val jitterX = (sin(jitterPhaseX) * 0.6 + sin(jitterPhaseX * 2.7) * 0.3 + random.nextGaussian() * 0.1)
        val jitterY = (sin(jitterPhaseY) * 0.6 + sin(jitterPhaseY * 3.1) * 0.3 + random.nextGaussian() * 0.1)

        val radiusMeters = config.jitterRadiusMeters
        val offsetLat = (jitterX * radiusMeters) / 111_111.0 // 1 degree ≈ 111111m
        val offsetLng = (jitterY * radiusMeters) / (111_111.0 * cos(Math.toRadians(latLng.latitude)))

        return LatLng(latLng.latitude + offsetLat, latLng.longitude + offsetLng)
    }

    /**
     * Calculates and applies realistic speed/bearing based on movement between points.
     * Uses exponential smoothing to avoid sudden jumps (Method 2 bypass).
     */
    private fun applyMotionData(location: Location, now: Long) {
        val prev = previousLocation ?: run {
            location.speed = 0f
            location.bearing = 0f
            return
        }

        val timeDeltaSeconds = (now - previousTimestamp) / 1000.0
        if (timeDeltaSeconds <= 0) {
            location.speed = smoothedSpeed
            location.bearing = smoothedBearing
            return
        }

        val distanceMeters = prev.distanceTo(location)
        val rawSpeed = (distanceMeters / timeDeltaSeconds).toFloat()

        // Clamp speed to movement mode maximum (Method 2)
        val maxSpeedMs = (config.movementMode.maxSpeedKmh * 1000.0 / 3600.0).toFloat()
        val clampedSpeed = rawSpeed.coerceAtMost(maxSpeedMs)

        // Exponential smoothing for natural speed transitions
        val alpha = 0.3f
        smoothedSpeed = smoothedSpeed * (1 - alpha) + clampedSpeed * alpha

        // Calculate bearing
        val rawBearing = prev.bearingTo(location)
        smoothedBearing = smoothBearing(smoothedBearing, rawBearing, 0.4f)

        location.speed = smoothedSpeed
        location.bearing = smoothedBearing

        // Mark speed/bearing as available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            location.speedAccuracyMetersPerSecond = 0.5f + random.nextFloat() * 0.5f
            location.bearingAccuracyDegrees = 5f + random.nextFloat() * 10f
        }
    }

    /**
     * Smoothly interpolates bearing to avoid 360->0 degree jumps.
     */
    private fun smoothBearing(current: Float, target: Float, alpha: Float): Float {
        var delta = target - current
        // Normalize to [-180, 180]
        while (delta > 180) delta -= 360
        while (delta < -180) delta += 360
        val result = current + delta * alpha
        return ((result % 360) + 360) % 360
    }

    /**
     * Generates realistic altitude with smooth drift (Method 3 & 6).
     * Real GPS altitude readings drift slowly over time.
     */
    private fun generateRealisticAltitude(): Double {
        // Slow random walk for altitude drift
        cumulativeAltitudeDrift += random.nextGaussian() * 0.15
        cumulativeAltitudeDrift = cumulativeAltitudeDrift.coerceIn(-5.0, 5.0) // max ±5m drift

        val noise = random.nextGaussian() * 0.8 // short-term noise
        return config.baseAltitudeMeters + cumulativeAltitudeDrift + noise
    }

    /**
     * Generates realistic GPS accuracy values.
     * Real accuracy fluctuates between 3-12m with occasional spikes.
     */
    private fun generateRealisticAccuracy(): Float {
        val base = config.baseAccuracyMeters
        val variation = (random.nextGaussian() * 1.5).toFloat()
        // Occasionally spike accuracy to simulate temporary satellite issues
        val spike = if (random.nextFloat() < 0.05f) random.nextFloat() * 8f else 0f
        return (base + variation + spike).coerceAtLeast(1.5f)
    }

    /**
     * Removes the isFromMockProvider flag using reflection (Method 4 & 5 bypass).
     * This is the key technique to bypass isFromMockProvider() checks.
     */
    private fun removeMockProviderFlag(location: Location) {
        try {
            // Method 1: Use reflection to set isFromMockProvider = false
            val isMockField: Field = Location::class.java.getDeclaredField("mIsMock")
            isMockField.isAccessible = true
            isMockField.setBoolean(location, false)
        } catch (e: NoSuchFieldException) {
            // Field name varies by Android version, try alternatives
            try {
                val field = Location::class.java.getDeclaredField("mIsFromMockProvider")
                field.isAccessible = true
                field.setBoolean(location, false)
            } catch (e2: Exception) {
                // Try via extras bundle
                try {
                    location.extras = (location.extras ?: Bundle()).apply {
                        putBoolean("mockProvider", false)
                    }
                } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            // Silently fail - some ROMs may have different internals
        }

        // Also try the public API if available (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val method = Location::class.java.getDeclaredMethod("setMock", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(location, false)
            } catch (_: Exception) { }
        }
    }

    /**
     * Generates a random lat/lng offset in meters.
     */
    private fun randomOffset(metersRadius: Double): Pair<Double, Double> {
        val angle = random.nextDouble() * 2 * Math.PI
        val distance = random.nextDouble() * metersRadius
        val latOffset = (distance * cos(angle)) / 111_111.0
        val lngOffset = (distance * sin(angle)) / 111_111.0
        return Pair(latOffset, lngOffset)
    }

    /**
     * Resets internal state (call when starting a new mock session).
     */
    fun reset() {
        previousLocation = null
        previousTimestamp = 0L
        cumulativeAltitudeDrift = 0.0
        smoothedSpeed = 0f
        smoothedBearing = 0f
        jitterPhaseX = random.nextDouble() * 2 * Math.PI
        jitterPhaseY = random.nextDouble() * 2 * Math.PI
    }
}
