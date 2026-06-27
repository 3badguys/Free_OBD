package com.freeobd.app.presentation.bluetooth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeobd.app.domain.model.BluetoothDeviceInfo
import com.freeobd.app.domain.model.DeviceType
import com.freeobd.app.presentation.theme.*
import org.koin.androidx.compose.koinViewModel

/**
 * Bluetooth connection screen — the app's home screen.
 *
 * Features:
 * - Scan for nearby OBD-II Bluetooth adapters (SPP + BLE)
 * - Display discovered devices with signal strength and type
 * - Protocol selection (ATSP0-ATSP9)
 * - Advanced ECU address configuration
 * - Connect/disconnect flow
 * - Navigation to Dashboard, DTC, and Vehicle Info when connected
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScreen(
    onNavigateToDashboard: () -> Unit,
    onNavigateToDTC: () -> Unit,
    onNavigateToVehicleInfo: () -> Unit,
    viewModel: BluetoothViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Protocol selection dropdown state
    var showProtocolPicker by remember { mutableStateOf(false) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var selectedProtocol by remember { mutableStateOf("ATSP0 (Auto)") }
    var ecuAddress by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Free OBD") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = Primary
                ),
                actions = {
                    // Connected state actions
                    if (uiState is BluetoothUiState.Connected) {
                        IconButton(onClick = onNavigateToDashboard) {
                            Icon(
                                Icons.Default.Dashboard,
                                contentDescription = "Dashboard",
                                tint = Primary
                            )
                        }
                        IconButton(onClick = onNavigateToDTC) {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = "Diagnostic Codes",
                                tint = StatusYellow
                            )
                        }
                        IconButton(onClick = onNavigateToVehicleInfo) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = "Vehicle Info",
                                tint = StatusBlue
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when (val state = uiState) {
                BluetoothUiState.Idle -> {
                    IdleContent(
                        onStartScan = { viewModel.onEvent(BluetoothEvent.StartScan) }
                    )
                }

                is BluetoothUiState.Scanning -> {
                    ScanningContent()
                }

                is BluetoothUiState.DevicesFound -> {
                    DeviceListContent(
                        devices = state.devices,
                        isScanning = state.isScanning,
                        selectedProtocol = selectedProtocol,
                        ecuAddress = ecuAddress,
                        showProtocolPicker = showProtocolPicker,
                        showAdvancedOptions = showAdvancedOptions,
                        onStartScan = { viewModel.onEvent(BluetoothEvent.StartScan) },
                        onStopScan = { viewModel.onEvent(BluetoothEvent.StopScan) },
                        onConnect = { device ->
                            viewModel.onEvent(
                                BluetoothEvent.Connect(
                                    device = device,
                                    protocol = protocolToAtCommand(selectedProtocol),
                                    ecuAddress = ecuAddress.ifBlank { null }
                                )
                            )
                        },
                        onProtocolSelected = { display ->
                            selectedProtocol = display
                            showProtocolPicker = false
                        },
                        onToggleProtocolPicker = { showProtocolPicker = it },
                        onToggleAdvanced = { showAdvancedOptions = it },
                        onEcuAddressChanged = { ecuAddress = it }
                    )
                }

                is BluetoothUiState.Connecting -> {
                    ConnectingContent(state.device)
                }

                is BluetoothUiState.Connected -> {
                    ConnectedContent(
                        state = state,
                        onNavigateToDashboard = onNavigateToDashboard,
                        onNavigateToDTC = onNavigateToDTC,
                        onNavigateToVehicleInfo = onNavigateToVehicleInfo,
                        onDisconnect = { viewModel.onEvent(BluetoothEvent.Disconnect) }
                    )
                }

                is BluetoothUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        isRecoverable = state.isRecoverable,
                        onRetry = { viewModel.onEvent(BluetoothEvent.DismissError) },
                        onDismiss = { viewModel.onEvent(BluetoothEvent.DismissError) }
                    )
                }
            }
        }
    }
}

// ── Content Composables ────────────────────────────────────

@Composable
private fun IdleContent(onStartScan: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Connect your OBD-II adapter",
                style = MaterialTheme.typography.headlineSmall,
                color = OnBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Tap scan to find nearby Bluetooth adapters",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onStartScan,
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan for Devices")
            }
        }
    }
}

@Composable
private fun ScanningContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Scanning for devices...",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Make sure your OBD adapter is powered on",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceListContent(
    devices: List<BluetoothDeviceInfo>,
    isScanning: Boolean,
    selectedProtocol: String,
    ecuAddress: String,
    showProtocolPicker: Boolean,
    showAdvancedOptions: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (BluetoothDeviceInfo) -> Unit,
    onProtocolSelected: (String) -> Unit,
    onToggleProtocolPicker: (Boolean) -> Unit,
    onToggleAdvanced: (Boolean) -> Unit,
    onEcuAddressChanged: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Scan controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${devices.size} device(s) found",
                style = MaterialTheme.typography.titleMedium,
                color = OnBackground
            )
            Row {
                if (isScanning) {
                    TextButton(onClick = onStopScan) {
                        Text("Stop", color = StatusRed)
                    }
                } else {
                    TextButton(onClick = onStartScan) {
                        Text("Scan Again", color = Primary)
                    }
                }
            }
        }

        if (isScanning) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Primary,
                trackColor = SurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Protocol picker
        TextButton(
            onClick = { onToggleProtocolPicker(!showProtocolPicker) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "Protocol: $selectedProtocol",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface
            )
            Icon(
                if (showProtocolPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = OnSurface
            )
        }

        if (showProtocolPicker) {
            ProtocolPicker(onProtocolSelected = onProtocolSelected)
        }

        // Advanced options toggle
        TextButton(
            onClick = { onToggleAdvanced(!showAdvancedOptions) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "Advanced Options",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
            Icon(
                if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = OnSurfaceVariant
            )
        }

        if (showAdvancedOptions) {
            AdvancedOptions(
                ecuAddress = ecuAddress,
                onEcuAddressChanged = onEcuAddressChanged
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Device list
        if (devices.isEmpty() && !isScanning) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No devices found. Ensure Bluetooth is enabled and adapter is powered.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices, key = { it.address }) { device ->
                    DeviceCard(device = device, onConnect = { onConnect(device) })
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: BluetoothDeviceInfo,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device type icon
            Icon(
                imageVector = when (device.type) {
                    DeviceType.BLE -> Icons.Default.BluetoothConnected
                    else -> Icons.Default.Bluetooth
                },
                contentDescription = null,
                tint = if (device.isPaired) StatusGreen else OnSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Device info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = OnBackground
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
                Row {
                    device.type.let { type ->
                        Text(
                            text = when (type) {
                                DeviceType.SPP -> "Classic (SPP)"
                                DeviceType.BLE -> "BLE"
                                DeviceType.UNKNOWN -> "Unknown type"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                    if (device.isPaired) {
                        Text(
                            text = " · Paired",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusGreen
                        )
                    }
                    if (device.rssi != null) {
                        Text(
                            text = " · ${device.rssi} dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            // Connect button
            Button(
                onClick = onConnect,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun ProtocolPicker(onProtocolSelected: (String) -> Unit) {
    val protocols = listOf(
        "ATSP0 (Auto)" to "Auto-detect",
        "ATSP6 (CAN 11/500)" to "ISO 15765-4 CAN 11-bit 500kbps",
        "ATSP7 (CAN 29/500)" to "ISO 15765-4 CAN 29-bit 500kbps",
        "ATSP8 (CAN 11/250)" to "ISO 15765-4 CAN 11-bit 250kbps",
        "ATSP9 (CAN 29/250)" to "ISO 15765-4 CAN 29-bit 250kbps",
        "ATSP5 (KWP Fast)" to "ISO 14230-4 KWP (fast init)",
        "ATSP4 (KWP 5Bd)" to "ISO 14230-4 KWP (5-baud init)",
        "ATSP3 (9141-2)" to "ISO 9141-2",
        "ATSP2 (VPW)" to "SAE J1850 VPW",
        "ATSP1 (PWM)" to "SAE J1850 PWM"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            protocols.forEach { (display, description) ->
                TextButton(
                    onClick = { onProtocolSelected(display) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = display,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = OnBackground
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedOptions(
    ecuAddress: String,
    onEcuAddressChanged: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "ECU Address (CAN ID)",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Default: 0x7DF (broadcast). Advanced users only.",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = ecuAddress,
                onValueChange = onEcuAddressChanged,
                placeholder = { Text("7DF") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = OnSurfaceVariant,
                    cursorColor = Primary
                )
            )
        }
    }
}

@Composable
private fun ConnectingContent(device: BluetoothDeviceInfo) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Connecting to ${device.name ?: device.address}...",
                style = MaterialTheme.typography.bodyLarge,
                color = OnBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Initializing ELM327 adapter",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectedContent(
    state: BluetoothUiState.Connected,
    onNavigateToDashboard: () -> Unit,
    onNavigateToDTC: () -> Unit,
    onNavigateToVehicleInfo: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Connection status banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = StatusGreen.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = StatusGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Connected",
                        style = MaterialTheme.typography.titleSmall,
                        color = StatusGreen
                    )
                    Text(
                        "${state.deviceName} · ${state.protocol}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface
                    )
                }
                TextButton(onClick = onDisconnect) {
                    Text("Disconnect", color = StatusRed)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Feature navigation cards
        Text(
            "Available Features",
            style = MaterialTheme.typography.titleMedium,
            color = OnBackground
        )
        Spacer(modifier = Modifier.height(12.dp))

        FeatureCard(
            title = "Live Data Dashboard",
            description = "View real-time sensor data with customizable gauges",
            icon = Icons.Default.Dashboard,
            onClick = onNavigateToDashboard
        )

        Spacer(modifier = Modifier.height(8.dp))

        FeatureCard(
            title = "Diagnostic Trouble Codes",
            description = "Read, view, and clear stored fault codes",
            icon = Icons.Default.Build,
            onClick = onNavigateToDTC
        )

        Spacer(modifier = Modifier.height(8.dp))

        FeatureCard(
            title = "Vehicle Information",
            description = "View VIN, calibration IDs, and CVN data",
            icon = Icons.Default.DirectionsCar,
            onClick = onNavigateToVehicleInfo
        )
    }
}

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = OnBackground
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    isRecoverable: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = StatusRed
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = OnBackground,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row {
                if (isRecoverable) {
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────

/** Convert a protocol display string back to its AT command. */
private fun protocolToAtCommand(display: String): String {
    return when {
        display.startsWith("ATSP") -> display.substring(0, 5) // "ATSP6"
        else -> "ATSP0"
    }
}
