package com.lilstiffy.mockgps.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.lilstiffy.mockgps.MainActivity
import com.lilstiffy.mockgps.R
import com.lilstiffy.mockgps.controller.AntiDetectionConfig
import com.lilstiffy.mockgps.controller.RouteState
import com.lilstiffy.mockgps.extensions.roundedShadow
import com.lilstiffy.mockgps.service.LocationHelper
import com.lilstiffy.mockgps.service.MockLocationService
import com.lilstiffy.mockgps.storage.StorageManager
import com.lilstiffy.mockgps.ui.components.AntiDetectionPanel
import com.lilstiffy.mockgps.ui.components.FavoritesListComponent
import com.lilstiffy.mockgps.ui.components.FooterComponent
import com.lilstiffy.mockgps.ui.components.SearchComponent
import com.lilstiffy.mockgps.ui.screens.viewmodels.MapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val DATE_DISPLAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
private val TIME_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    mapViewModel: MapViewModel = viewModel(),
    activity: MainActivity,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isMocking by remember { mutableStateOf(false) }
    var antiDetectionConfig by remember { mutableStateOf(AntiDetectionConfig()) }
    var showAntiDetection by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(mapViewModel.markerPosition.value, 15f)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }

    val routeState by mapViewModel.routeState.collectAsState()

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val result = mapViewModel.loadRoute(context, uri)
        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
    }

    val mapStyle = if (isSystemInDarkTheme())
        MapStyleOptions.loadRawResourceStyle(LocalContext.current, R.raw.style_json)
    else
        MapStyleOptions("")

    fun animateCamera() {
        scope.launch(Dispatchers.Main) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newCameraPosition(
                    CameraPosition(mapViewModel.markerPosition.value, 15f, 0f, 0f)
                ),
                durationMs = 1000
            )
        }
    }

    LaunchedEffect(routeState.currentPoint) {
        if (routeState.currentPoint != null) {
            animateCamera()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Anti-detection toggle button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { showAntiDetection = !showAntiDetection }) {
                Text(
                    text = if (showAntiDetection) "Hide Anti-Detection" else "Show Anti-Detection",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (showAntiDetection) {
            AntiDetectionPanel(
                config = antiDetectionConfig,
                onConfigChange = { newConfig ->
                    antiDetectionConfig = newConfig
                    MockLocationService.instance?.antiDetectionConfig = newConfig
                }
            )
        }

        RouteControls(
            state = routeState,
            onLoadCsv = {
                csvPickerLauncher.launch(arrayOf("text/csv", "text/plain", "application/vnd.ms-excel"))
            },
            onPickDate = {
                val baseDate = routeState.startDateTime?.toLocalDate() ?: java.time.LocalDate.now()
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        mapViewModel.updateStartDate(java.time.LocalDate.of(year, month + 1, dayOfMonth))
                    },
                    baseDate.year,
                    baseDate.monthValue - 1,
                    baseDate.dayOfMonth
                ).show()
            },
            onPickTime = {
                val baseTime = routeState.startDateTime?.toLocalTime() ?: java.time.LocalTime.now()
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        mapViewModel.updateStartTime(java.time.LocalTime.of(hourOfDay, minute))
                    },
                    baseTime.hour,
                    baseTime.minute,
                    true
                ).show()
            },
            onIntervalCommit = { minutes ->
                mapViewModel.updateInterval(minutes)
            },
            onPrev = {
                mapViewModel.previousPoint()
            },
            onNext = {
                mapViewModel.nextPoint()
            },
            onJumpTo = { index ->
                mapViewModel.jumpTo(index)
            }
        )

        Box(modifier = Modifier.weight(1f)) {
            // Google maps
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                onMapLoaded = {
                    LocationHelper.requestPermissions(activity)
                    mapViewModel.updateMarkerPosition(mapViewModel.markerPosition.value)
                },
                properties = MapProperties(
                    mapStyleOptions = mapStyle
                ),
                uiSettings = MapUiSettings(
                    tiltGesturesEnabled = false,
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled = false,
                    mapToolbarEnabled = false,
                    compassEnabled = false
                ),
                onMapClick = { latLng ->
                    if (!isMocking) {
                        mapViewModel.updateMarkerPosition(latLng)
                    }
                },
                cameraPositionState = cameraPositionState
            ) {
                Marker(
                    state = MarkerState(mapViewModel.markerPosition.value)
                )
            }

            Column(
                modifier = Modifier.statusBarsPadding()
            ) {
                SearchComponent(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxHeight(0.075f)
                        .fillMaxWidth()
                        .padding(4.dp)
                        .roundedShadow(32.dp)
                        .zIndex(32f),
                    onSearch = { searchTerm ->
                        if (isMocking) {
                            Toast.makeText(
                                activity,
                                "You can't search while mocking location",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@SearchComponent
                        }

                        LocationHelper.geocoding(searchTerm) { foundLatLng ->
                            foundLatLng?.let {
                                mapViewModel.updateMarkerPosition(it)
                                animateCamera()
                            }
                        }
                    }
                )

                IconButton(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .align(Alignment.End),
                    onClick = { showBottomSheet = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Blue, contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.List,
                        contentDescription = "show favorites"
                    )
                }
            }

            FooterComponent(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(1f)
                    .navigationBarsPadding()
                    .padding(4.dp)
                    .zIndex(32f)
                    .roundedShadow(16.dp),
                address = mapViewModel.address.value,
                latLng = mapViewModel.markerPosition.value,
                label = routeState.currentPoint?.label,
                timestamp = routeState.currentTimestamp,
                isMocking = isMocking,
                isFavorite = mapViewModel.markerPositionIsFavorite.value,
                onStart = {
                    isMocking = activity.toggleMocking()
                },
                onFavorite = { mapViewModel.toggleFavoriteForLocation() }
            )

            if (showBottomSheet) {
                FavoritesListComponent(
                    onDismissRequest = {
                        showBottomSheet = false
                    },
                    sheetState = sheetState,
                    data = StorageManager.favorites,
                    onEntryClicked = { clickedEntry ->
                        if (isMocking) {
                            Toast.makeText(
                                activity,
                                "You can't switch location while mocking",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@FavoritesListComponent
                        }
                        mapViewModel.apply {
                            mapViewModel.updateMarkerPosition(clickedEntry.latLng)
                            scope.launch {
                                sheetState.hide()
                                showBottomSheet = false
                            }
                            animateCamera()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RouteControls(
    state: RouteState,
    onLoadCsv: () -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onIntervalCommit: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJumpTo: (Int) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onLoadCsv) {
                    Text(text = "Load CSV")
                }

                Text(
                    text = if (state.totalPoints > 0) {
                        "/"
                    } else {
                        "0/0"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )

                IconButton(onClick = { onJumpTo(1) }, enabled = state.isLoaded) {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Reset to first")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val dateText = state.startDateTime?.toLocalDate()?.format(DATE_DISPLAY_FORMAT) ?: "--"
                val timeText = state.startDateTime?.toLocalTime()?.format(TIME_DISPLAY_FORMAT) ?: "--"

                TextButton(onClick = onPickDate, enabled = state.isLoaded) {
                    Text(text = "Date: ")
                }

                TextButton(onClick = onPickTime, enabled = state.isLoaded) {
                    Text(text = "Time: ")
                }
            }

            var intervalText by rememberSaveable(state.intervalMinutes) { mutableStateOf(state.intervalMinutes.toString()) }
            var indexText by rememberSaveable(state.currentIndex) {
                mutableStateOf(if (state.currentIndex == 0) "" else state.currentIndex.toString())
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = intervalText,
                    onValueChange = { value ->
                        intervalText = value.filter { it.isDigit() }
                        val minutes = intervalText.toLongOrNull()
                        if (minutes != null) {
                            onIntervalCommit(minutes)
                        }
                    },
                    label = { Text("Interval (min)") },
                    enabled = state.isLoaded,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )

                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = indexText,
                    onValueChange = { value ->
                        indexText = value.filter { it.isDigit() }
                    },
                    label = { Text("Point #") },
                    enabled = state.isLoaded,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )

                Button(
                    onClick = {
                        val index = indexText.toIntOrNull()
                        if (index != null) {
                            onJumpTo(index)
                        }
                    },
                    enabled = state.isLoaded
                ) {
                    Text("Go")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onPrev, enabled = state.isLoaded && state.currentIndex > 1) {
                    Text("Prev")
                }
                Button(onClick = onNext, enabled = state.isLoaded && state.currentIndex < state.totalPoints) {
                    Text("Next")
                }
                val label = state.currentPoint?.label ?: "--"
                val time = state.currentTimestamp?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) ?: "--"
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(text = "Label: ", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Timestamp: ", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
