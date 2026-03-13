package com.lilstiffy.mockgps.controller

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val TIME_DISPLAY_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** Represents a single row imported from the CSV route file. */
data class RoutePoint(
    val position: LatLng,
    val label: String? = null,
    val timestamp: LocalDateTime? = null
)

/** Snapshot exposed to the UI describing the active route. */
data class RouteState(
    val isLoaded: Boolean = false,
    val currentIndex: Int = 0, // 1-based for presentation
    val totalPoints: Int = 0,
    val currentPoint: RoutePoint? = null,
    val currentTimestamp: LocalDateTime? = null,
    val startDateTime: LocalDateTime? = null,
    val intervalMinutes: Long = RouteController.DEFAULT_INTERVAL_MINUTES
) {
    val formattedTimestamp: String?
        get() = currentTimestamp?.format(TIME_DISPLAY_FORMAT)
}

class RouteController(
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) {

    private val _state = MutableStateFlow(RouteState())
    val state: StateFlow<RouteState> = _state.asStateFlow()

    private var points: List<RoutePoint> = emptyList()
    private var currentIndexZeroBased: Int = 0
    private var intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES
    private var startDateTime: LocalDateTime? = null

    fun load(points: List<RoutePoint>, fallbackStart: LocalDateTime? = null) {
        this.points = points
        currentIndexZeroBased = 0
        intervalMinutes = intervalMinutes.coerceAtLeast(1)

        startDateTime = points.firstOrNull { it.timestamp != null }?.timestamp
            ?: startDateTime
            ?: fallbackStart
            ?: clock()

        publishState()
    }

    fun updateStartDate(date: LocalDate) {
        val time = startDateTime?.toLocalTime() ?: LocalTime.MIDNIGHT
        startDateTime = LocalDateTime.of(date, time)
        publishState()
    }

    fun updateStartTime(time: LocalTime) {
        val date = startDateTime?.toLocalDate() ?: clock().toLocalDate()
        startDateTime = LocalDateTime.of(date, time)
        publishState()
    }

    fun updateIntervalMinutes(minutes: Long) {
        intervalMinutes = max(1, minutes)
        publishState()
    }

    fun jumpTo(indexOneBased: Int) {
        if (points.isEmpty()) return
        val clamped = indexOneBased.coerceIn(1, points.size)
        currentIndexZeroBased = clamped - 1
        publishState()
    }

    fun next() {
        if (points.isEmpty()) return
        if (currentIndexZeroBased + 1 < points.size) {
            currentIndexZeroBased += 1
            publishState()
        }
    }

    fun previous() {
        if (points.isEmpty()) return
        if (currentIndexZeroBased - 1 >= 0) {
            currentIndexZeroBased -= 1
            publishState()
        }
    }

    private fun publishState() {
        val currentPoint = points.getOrNull(currentIndexZeroBased)
        val currentTimestamp = currentPoint?.timestamp ?: startDateTime?.let { base ->
            base.plusMinutes(intervalMinutes * currentIndexZeroBased)
        }

        _state.value = RouteState(
            isLoaded = points.isNotEmpty(),
            currentIndex = if (points.isEmpty()) 0 else currentIndexZeroBased + 1,
            totalPoints = points.size,
            currentPoint = currentPoint,
            currentTimestamp = currentTimestamp,
            startDateTime = startDateTime,
            intervalMinutes = intervalMinutes
        )
    }

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 20L
    }
}
