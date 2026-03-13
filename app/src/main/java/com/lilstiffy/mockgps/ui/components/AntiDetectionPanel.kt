package com.lilstiffy.mockgps.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lilstiffy.mockgps.controller.AntiDetectionConfig
import com.lilstiffy.mockgps.controller.MovementMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntiDetectionPanel(
    config: AntiDetectionConfig,
    onConfigChange: (AntiDetectionConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Anti-Detection (Lab Test)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error
            )

            // Method 1: Triangulation bypass
            ToggleRow(
                label = "Mock Network Provider",
                description = "Bypass triangulation (Method 1)",
                checked = config.mockNetworkProvider,
                onCheckedChange = { onConfigChange(config.copy(mockNetworkProvider = it)) }
            )

            // Method 2: Motion analysis bypass
            ToggleRow(
                label = "Realistic Motion",
                description = "Speed/bearing simulation (Method 2)",
                checked = config.realisticMotion,
                onCheckedChange = { onConfigChange(config.copy(realisticMotion = it)) }
            )

            // Method 3: Sensor fusion bypass
            ToggleRow(
                label = "Sensor Consistency",
                description = "Consistent sensor data (Method 3)",
                checked = config.sensorConsistency,
                onCheckedChange = { onConfigChange(config.copy(sensorConsistency = it)) }
            )

            // Method 4 & 5: Mock provider flag bypass
            ToggleRow(
                label = "Hide Mock Provider",
                description = "Remove isFromMockProvider flag (Method 4&5)",
                checked = config.hideMockProvider,
                onCheckedChange = { onConfigChange(config.copy(hideMockProvider = it)) }
            )

            // Method 6: AI pattern bypass
            ToggleRow(
                label = "GPS Jitter",
                description = "Natural noise pattern (Method 6)",
                checked = config.gpsJitter,
                onCheckedChange = { onConfigChange(config.copy(gpsJitter = it)) }
            )

            ToggleRow(
                label = "Altitude Variation",
                description = "Realistic altitude drift",
                checked = config.altitudeVariation,
                onCheckedChange = { onConfigChange(config.copy(altitudeVariation = it)) }
            )

            ToggleRow(
                label = "Accuracy Variation",
                description = "Fluctuating GPS accuracy",
                checked = config.accuracyVariation,
                onCheckedChange = { onConfigChange(config.copy(accuracyVariation = it)) }
            )

            // Movement mode selector
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    value = config.movementMode.name,
                    onValueChange = {},
                    label = { Text("Movement Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    MovementMode.values().forEach { mode ->
                        DropdownMenuItem(
                            text = { Text("${mode.name} (max ${mode.maxSpeedKmh} km/h)") },
                            onClick = {
                                onConfigChange(config.copy(movementMode = mode))
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
