package com.lilstiffy.mockgps.controller

/**
 * Configuration for anti-detection features used in lab testing.
 * Each flag corresponds to a bypass method for a known GPS spoofing detection technique.
 */
data class AntiDetectionConfig(
    // Method 1: Triangulation bypass - mock Network provider alongside GPS
    val mockNetworkProvider: Boolean = true,

    // Method 2: Motion analysis - realistic speed, acceleration, bearing
    val realisticMotion: Boolean = true,
    val maxWalkingSpeedKmh: Double = 5.5,
    val maxDrivingSpeedKmh: Double = 80.0,
    val movementMode: MovementMode = MovementMode.WALKING,

    // Method 3: Sensor fusion - consistent speed/bearing/altitude in Location objects
    val sensorConsistency: Boolean = true,

    // Method 4 & 5: Mock provider flag bypass
    val hideMockProvider: Boolean = true,

    // Method 6: AI pattern analysis bypass - natural GPS jitter & drift
    val gpsJitter: Boolean = true,
    val jitterRadiusMeters: Double = 3.0, // realistic GPS noise ~2-5m

    // Realistic altitude variation
    val altitudeVariation: Boolean = true,
    val baseAltitudeMeters: Double = 15.0,

    // Realistic accuracy fluctuation
    val accuracyVariation: Boolean = true,
    val baseAccuracyMeters: Float = 4.0f,
)

enum class MovementMode(val maxSpeedKmh: Double) {
    WALKING(5.5),
    CYCLING(25.0),
    MOTORCYCLE(55.0),
    DRIVING(80.0),
    STATIONARY(0.0)
}
