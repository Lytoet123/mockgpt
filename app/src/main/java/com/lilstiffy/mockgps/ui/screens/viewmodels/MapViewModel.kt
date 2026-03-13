package com.lilstiffy.mockgps.ui.screens.viewmodels

import android.content.Context
import android.net.Uri
import android.location.Address
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.lilstiffy.mockgps.controller.CsvImporter
import com.lilstiffy.mockgps.controller.RouteController
import com.lilstiffy.mockgps.controller.RoutePoint
import com.lilstiffy.mockgps.controller.RouteState
import com.lilstiffy.mockgps.extensions.displayString
import com.lilstiffy.mockgps.service.LocationHelper
import com.lilstiffy.mockgps.service.MockLocationService
import com.lilstiffy.mockgps.storage.StorageManager
import com.lilstiffy.mockgps.ui.models.LocationEntry
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MapViewModel : ViewModel() {
    var markerPosition: MutableState<LatLng> = mutableStateOf(StorageManager.getLatestLocation())
        private set
    var address: MutableState<Address?> = mutableStateOf(null)
        private set

    var markerPositionIsFavorite: MutableState<Boolean> = mutableStateOf(false)
        private set

    private val routeController = RouteController()
    val routeState: StateFlow<RouteState> = routeController.state

    fun loadRoute(context: Context, uri: Uri): RouteLoadResult {
        return try {
            val points = CsvImporter.parse(context, uri)
            if (points.isEmpty()) {
                RouteLoadResult.Error("No route points found in CSV file.")
            } else {
                routeController.load(points, LocalDateTime.now())
                syncWithRoutePoint(routeController.state.value.currentPoint)
                RouteLoadResult.Success(points.size)
            }
        } catch (exception: Exception) {
            RouteLoadResult.Error("Failed to load CSV: ")
        }
    }

    fun updateStartDate(date: LocalDate) {
        routeController.updateStartDate(date)
    }

    fun updateStartTime(time: LocalTime) {
        routeController.updateStartTime(time)
    }

    fun updateInterval(minutes: Long) {
        routeController.updateIntervalMinutes(minutes)
    }

    fun jumpTo(index: Int) {
        routeController.jumpTo(index)
        syncWithRoutePoint(routeController.state.value.currentPoint)
    }

    fun nextPoint() {
        routeController.next()
        syncWithRoutePoint(routeController.state.value.currentPoint)
    }

    fun previousPoint() {
        routeController.previous()
        syncWithRoutePoint(routeController.state.value.currentPoint)
    }

    fun updateMarkerPosition(latLng: LatLng) {
        markerPosition.value = latLng
        MockLocationService.instance?.latLng = latLng

        LocationHelper.reverseGeocoding(latLng) { foundAddress ->
            address.value = foundAddress
        }

        checkIfFavorite()
    }

    fun toggleFavoriteForLocation() {
        StorageManager.toggleFavoriteForPosition(currentLocationEntry())
        checkIfFavorite()
    }

    private fun syncWithRoutePoint(routePoint: RoutePoint?) {
        routePoint?.let { updateMarkerPosition(it.position) }
    }

    private fun checkIfFavorite() {
        val currentLocationEntry = currentLocationEntry()
        markerPositionIsFavorite.value = StorageManager.containsFavoriteEntry(currentLocationEntry)
    }

    private fun currentLocationEntry(): LocationEntry {
        return LocationEntry(
            latLng = markerPosition.value,
            addressLine = address.value?.displayString()
        )
    }
}

data class RouteLoadResult(val success: Boolean, val message: String, val count: Int = 0) {
    companion object {
        fun Success(count: Int) = RouteLoadResult(true, "Loaded  points", count)
        fun Error(message: String) = RouteLoadResult(false, message)
    }
}
