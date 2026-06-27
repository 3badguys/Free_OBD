package com.freeobd.app.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeobd.app.domain.model.OBDData
import com.freeobd.app.presentation.theme.*
import org.koin.androidx.compose.koinViewModel

/**
 * Live data dashboard screen with configurable gauges.
 *
 * Displays real-time sensor data from the vehicle in a grid of
 * custom gauge widgets. Users can start/stop polling and select
 * which PIDs to display.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPids by viewModel.selectedPids.collectAsState()

    // PID picker dialog state
    var showPidPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Data") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showPidPicker = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Gauge")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = OnBackground
                )
            )
        },
        bottomBar = {
            // Polling control bar
            Surface(
                color = Surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isPolling = (uiState as? DashboardUiState.Active)?.isPolling == true

                    if (isPolling) {
                        Button(
                            onClick = { viewModel.onEvent(DashboardEvent.StopPolling) },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.onEvent(DashboardEvent.StartPolling) }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start")
                        }
                    }

                    Text(
                        text = if (isPolling) "Polling..." else "Paused",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isPolling) StatusGreen else OnSurfaceVariant
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                DashboardUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                }

                is DashboardUiState.Active -> {
                    if (state.pidValues.isEmpty()) {
                        EmptyContent("Waiting for data...")
                    } else {
                        GaugeGrid(
                            pidValues = state.pidValues,
                            selectedPids = state.selectedPids
                        )
                    }
                }

                is DashboardUiState.Paused -> {
                    if (state.lastValues.isEmpty()) {
                        EmptyContent("Start polling to see data")
                    } else {
                        GaugeGrid(
                            pidValues = state.lastValues,
                            selectedPids = state.selectedPids
                        )
                    }
                }

                is DashboardUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = StatusRed,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = OnBackground
                            )
                        }
                    }
                }
            }
        }
    }

    // PID picker bottom sheet
    if (showPidPicker) {
        PidPickerSheet(
            selectedPids = selectedPids,
            onAddPid = {
                viewModel.onEvent(DashboardEvent.AddPid(it))
            },
            onRemovePid = {
                viewModel.onEvent(DashboardEvent.RemovePid(it))
            },
            onDismiss = { showPidPicker = false }
        )
    }
}

@Composable
private fun GaugeGrid(
    pidValues: Map<Int, OBDData>,
    selectedPids: Set<Int>
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(selectedPids.toList().sorted()) { pidId ->
            val data = pidValues[pidId]
            val (label, unit, min, max) = pidMetadata(pidId)

            GaugeWidget(
                value = (data as? OBDData.Numeric)?.value ?: 0.0,
                label = label,
                unit = unit,
                minValue = min,
                maxValue = max,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PidPickerSheet(
    selectedPids: Set<Int>,
    onAddPid: (Int) -> Unit,
    onRemovePid: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val availablePids = listOf(
        0x0C to "Engine RPM",
        0x0D to "Vehicle Speed",
        0x05 to "Coolant Temp",
        0x11 to "Throttle Position",
        0x04 to "Engine Load",
        0x2F to "Fuel Level",
        0x10 to "MAF Rate",
        0x0F to "Intake Air Temp",
        0x46 to "Ambient Temp",
        0x5C to "Oil Temp",
        0x0A to "Fuel Pressure",
        0x0B to "Intake Pressure",
        0x33 to "Baro Pressure",
        0x1F to "Run Time",
        0x21 to "MIL Distance"
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Select Gauges",
                style = MaterialTheme.typography.titleMedium,
                color = OnBackground
            )
            Spacer(modifier = Modifier.height(16.dp))

            availablePids.forEach { (pidId, name) ->
                val isSelected = pidId in selectedPids
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (isSelected) onRemovePid(pidId) else onAddPid(pidId)
                    },
                    color = if (isSelected) Primary.copy(alpha = 0.08f) else Surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnBackground
                            )
                            Text(
                                text = "PID 0x${pidId.toHex2()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked) onAddPid(pidId) else onRemovePid(pidId)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Primary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun EmptyContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurface
        )
    }
}

// ── PID Metadata ───────────────────────────────────────────

private fun pidMetadata(pidId: Int): PIDInfo {
    return when (pidId) {
        0x04 -> PIDInfo("Engine Load", "%", 0.0, 100.0)
        0x05 -> PIDInfo("Coolant Temp", "°C", -40.0, 215.0)
        0x0A -> PIDInfo("Fuel Pressure", "kPa", 0.0, 765.0)
        0x0B -> PIDInfo("Intake Pressure", "kPa", 0.0, 255.0)
        0x0C -> PIDInfo("RPM", "rpm", 0.0, 8000.0)
        0x0D -> PIDInfo("Speed", "km/h", 0.0, 255.0)
        0x0F -> PIDInfo("Intake Air Temp", "°C", -40.0, 215.0)
        0x10 -> PIDInfo("MAF", "g/s", 0.0, 500.0)
        0x11 -> PIDInfo("Throttle", "%", 0.0, 100.0)
        0x1F -> PIDInfo("Run Time", "s", 0.0, 65535.0)
        0x21 -> PIDInfo("MIL Distance", "km", 0.0, 65535.0)
        0x2F -> PIDInfo("Fuel Level", "%", 0.0, 100.0)
        0x33 -> PIDInfo("Baro Pressure", "kPa", 0.0, 255.0)
        0x46 -> PIDInfo("Ambient Temp", "°C", -40.0, 215.0)
        0x5C -> PIDInfo("Oil Temp", "°C", -40.0, 210.0)
        else -> PIDInfo("PID ${pidId.toHex2()}", "", 0.0, 100.0)
    }
}

private data class PIDInfo(
    val label: String,
    val unit: String,
    val min: Double,
    val max: Double
)

/** Format an Int as 2-char hex string. */
private fun Int.toHex2(): String = String.format("%02X", this)
