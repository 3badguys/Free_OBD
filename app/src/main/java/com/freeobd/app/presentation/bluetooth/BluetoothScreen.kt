package com.freeobd.app.presentation.bluetooth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
    onNavigateToLiveData: () -> Unit = {},
    onNavigateToFreezeFrame: () -> Unit = {},
    onNavigateToDTC: () -> Unit,
    onNavigateToVehicleInfo: () -> Unit,
    onNavigateToDebugConsole: () -> Unit = {},
    viewModel: BluetoothViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()

    // Permission launcher
    val bluetoothPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.onEvent(BluetoothEvent.StartScan)
        }
    }

    /** Check if Bluetooth permissions are granted. */
    fun hasBluetoothPermissions(): Boolean {
        return bluetoothPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Start scan after checking permissions (skipped in demo mode). */
    fun requestPermissionsAndScan() {
        if (isDemoMode || hasBluetoothPermissions()) {
            viewModel.onEvent(BluetoothEvent.StartScan)
        } else {
            permissionLauncher.launch(bluetoothPermissions)
        }
    }

    // Protocol & Advanced config — accordion style (only one open at a time)
    var showProtocolPicker by remember { mutableStateOf(false) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var selectedProtocol by remember { mutableStateOf(viewModel.protocolDisplay) }
    var selectedTransport by remember { mutableStateOf(viewModel.selectedTransport) }
    var ecuAddress by remember { mutableStateOf(viewModel.ecuAddress) }
    var showResponseHeaders by remember { mutableStateOf(viewModel.showResponseHeaders) }
    val debugLoggingEnabled by com.freeobd.app.data.remote.DebugLogger.enabledFlow.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Free OBD")
                        if (isDemoMode) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = StatusYellow.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    "DEMO",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusYellow
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = Primary
                ),
                actions = {
                    // Demo mode toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Demo",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDemoMode) StatusYellow else OnSurfaceVariant
                        )
                        Switch(
                            checked = isDemoMode,
                            onCheckedChange = {
                                viewModel.onEvent(BluetoothEvent.ToggleDemoMode)
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = StatusYellow,
                                checkedThumbColor = Primary
                            ),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
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
                        onStartScan = { requestPermissionsAndScan() },
                        isDemoMode = isDemoMode
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
                        selectedTransport = selectedTransport,
                        ecuAddress = ecuAddress,
                        showResponseHeaders = showResponseHeaders,
                        debugLoggingEnabled = debugLoggingEnabled,
                        showProtocolPicker = showProtocolPicker,
                        showAdvancedOptions = showAdvancedOptions,
                        onStartScan = { requestPermissionsAndScan() },
                        onStopScan = { viewModel.onEvent(BluetoothEvent.StopScan) },
                        onConnect = { device ->
                            viewModel.onEvent(
                                BluetoothEvent.Connect(
                                    device = device,
                                    protocol = protocolToAtCommand(selectedProtocol),
                                    ecuAddress = ecuAddress.ifBlank { null },
                                    transportType = if (selectedTransport == "BLE") DeviceType.BLE else DeviceType.SPP
                                )
                            )
                        },
                        onProtocolSelected = { display ->
                            selectedProtocol = display
                            viewModel.protocolDisplay = display
                            showProtocolPicker = false
                        },
                        onTransportSelected = {
                            selectedTransport = it
                            viewModel.selectedTransport = it
                        },
                        onToggleProtocolPicker = { open ->
                            showProtocolPicker = open
                            if (open) showAdvancedOptions = false
                        },
                        onToggleAdvanced = { open ->
                            showAdvancedOptions = open
                            if (open) showProtocolPicker = false
                        },
                        onEcuAddressChanged = {
                            ecuAddress = it
                            viewModel.ecuAddress = it
                        },
                        onShowResponseHeadersToggled = {
                            showResponseHeaders = it
                            viewModel.showResponseHeaders = it
                            com.freeobd.app.data.mock.DemoModeState.showResponseHeaders = it
                        },
                        onDebugLoggingToggled = { enabled ->
                            if (enabled) {
                                viewModel.onEvent(BluetoothEvent.EnableDebugLogging)
                            } else {
                                viewModel.onEvent(BluetoothEvent.DisableDebugLogging)
                            }
                        }
                    )
                }

                is BluetoothUiState.Connecting -> {
                    ConnectingContent(state.device)
                }

                is BluetoothUiState.Connected -> {
                    ConnectedContent(
                        state = state,
                        debugLoggingEnabled = debugLoggingEnabled,
                        onNavigateToDashboard = onNavigateToDashboard,
                        onNavigateToLiveData = onNavigateToLiveData,
                        onNavigateToFreezeFrame = onNavigateToFreezeFrame,
                        onNavigateToDTC = onNavigateToDTC,
                        onNavigateToVehicleInfo = onNavigateToVehicleInfo,
                        onNavigateToDebugConsole = onNavigateToDebugConsole,
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
private fun IdleContent(onStartScan: () -> Unit, isDemoMode: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isDemoMode) Icons.Default.Android else Icons.Default.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = if (isDemoMode) StatusYellow else OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (isDemoMode) "Demo Mode Active" else "Connect your OBD-II adapter",
                style = MaterialTheme.typography.headlineSmall,
                color = OnBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isDemoMode) "Simulated OBD data — no hardware needed"
                else "Tap scan to find nearby Bluetooth adapters",
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
    selectedTransport: String,
    ecuAddress: String,
    showResponseHeaders: Boolean,
    debugLoggingEnabled: Boolean,
    showProtocolPicker: Boolean,
    showAdvancedOptions: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (BluetoothDeviceInfo) -> Unit,
    onProtocolSelected: (String) -> Unit,
    onTransportSelected: (String) -> Unit,
    onToggleProtocolPicker: (Boolean) -> Unit,
    onToggleAdvanced: (Boolean) -> Unit,
    onEcuAddressChanged: (String) -> Unit,
    onShowResponseHeadersToggled: (Boolean) -> Unit,
    onDebugLoggingToggled: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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

        // Transport / Protocol / Advanced — single row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Transport type toggle (SPP / BLE)
            TextButton(
                onClick = { onTransportSelected(if (selectedTransport == "SPP") "BLE" else "SPP") },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    "Transport: $selectedTransport",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface
                )
            }

            // Protocol picker
            TextButton(
                onClick = { onToggleProtocolPicker(!showProtocolPicker) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
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

            // Advanced options toggle
            TextButton(
                onClick = { onToggleAdvanced(!showAdvancedOptions) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    "Advanced",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
                Icon(
                    if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                tint = OnSurfaceVariant
            )
            } // Advanced TextButton
        } // Row (transport / protocol / advanced)

        if (showProtocolPicker) {
            ProtocolPicker(selectedProtocol = selectedProtocol, onProtocolSelected = onProtocolSelected)
        }

        if (showAdvancedOptions) {
            AdvancedOptions(
                ecuAddress = ecuAddress,
                showResponseHeaders = showResponseHeaders,
                debugLoggingEnabled = debugLoggingEnabled,
                onEcuAddressChanged = onEcuAddressChanged,
                onShowResponseHeadersToggled = onShowResponseHeadersToggled,
                onDebugLoggingToggled = onDebugLoggingToggled
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
            // distinctBy prevents crash if duplicates slip through despite ViewModel dedup
            val uniqueDevices = devices.distinctBy { it.address }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uniqueDevices.forEach { device ->
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
private fun ProtocolPicker(selectedProtocol: String, onProtocolSelected: (String) -> Unit) {
    val protocols = listOf(
        "ATSP0 (Auto)" to "Automatic detection",
        "ATSP1 (PWM)" to "SAE J1850 PWM (41.6 kbaud)",
        "ATSP2 (VPW)" to "SAE J1850 VPW (10.4 kbaud)",
        "ATSP3 (9141-2)" to "ISO 9141-2 (5 baud init)",
        "ATSP4 (KWP 5Bd)" to "ISO 14230-4 KWP (5 baud init)",
        "ATSP5 (KWP Fast)" to "ISO 14230-4 KWP (fast init)",
        "ATSP6 (CAN 11/500)" to "ISO 15765-4 CAN (11-bit, 500 kbaud)",
        "ATSP7 (CAN 29/500)" to "ISO 15765-4 CAN (29-bit, 500 kbaud)",
        "ATSP8 (CAN 11/250)" to "ISO 15765-4 CAN (11-bit, 250 kbaud)",
        "ATSP9 (CAN 29/250)" to "ISO 15765-4 CAN (29-bit, 250 kbaud)",
        "ATSPA (J1939)" to "SAE J1939 CAN (29-bit, 250 kbaud)",
        "ATSPB (User1)" to "User1 CAN (11-bit, 125 kbaud)",
        "ATSPC (User2)" to "User2 CAN (11-bit, 50 kbaud)"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            protocols.forEach { (display, description) ->
                val isSelected = display == selectedProtocol
                TextButton(
                    onClick = { onProtocolSelected(display) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isSelected) Modifier.background(
                            Primary.copy(alpha = 0.12f), MaterialTheme.shapes.small
                        ) else Modifier)
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
    showResponseHeaders: Boolean,
    debugLoggingEnabled: Boolean,
    onEcuAddressChanged: (String) -> Unit,
    onShowResponseHeadersToggled: (Boolean) -> Unit,
    onDebugLoggingToggled: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ECU Address
            Text(
                "ECU Address",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "CAN header address. Default: 0x7DF (broadcast).",
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

            Spacer(modifier = Modifier.height(16.dp))

            // Show Response Headers Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Show Response Headers",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurface
                    )
                    Text(
                        "Enable ATH1 to include CAN headers in responses",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
                Switch(
                    checked = showResponseHeaders,
                    onCheckedChange = onShowResponseHeadersToggled,
                    colors = SwitchDefaults.colors(checkedTrackColor = Primary)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Debug Logging Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Enable Debug Logging",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurface
                    )
                    Text(
                        "Record all AT/OBD commands and responses",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
                Switch(
                    checked = debugLoggingEnabled,
                    onCheckedChange = onDebugLoggingToggled,
                    colors = SwitchDefaults.colors(checkedTrackColor = Primary)
                )
            }
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
    debugLoggingEnabled: Boolean = false,
    onNavigateToDashboard: () -> Unit,
    onNavigateToLiveData: () -> Unit = {},
    onNavigateToFreezeFrame: () -> Unit = {},
    onNavigateToDTC: () -> Unit,
    onNavigateToVehicleInfo: () -> Unit,
    onNavigateToDebugConsole: () -> Unit,
    onDisconnect: () -> Unit
) {
    val voltage = state.voltage
    val lowVoltage = voltage != null && voltage < 10.0
    var showVoltageWarning by remember { mutableStateOf(lowVoltage) }

    // Voltage warning dialog
    if (showVoltageWarning && voltage != null) {
        AlertDialog(
            onDismissRequest = { showVoltageWarning = false },
            title = { Text("Voltage Warning") },
            text = {
                Text(
                    if (voltage == 0.0 || voltage < 1.0)
                        "OBD port voltage is ${"%.1f".format(voltage)}V. " +
                        "The vehicle ignition may be OFF. Turn the ignition ON and reconnect."
                    else
                        "Low voltage detected: ${"%.1f".format(voltage)}V. " +
                        "The ECU may not be fully powered. Check battery or ignition."
                )
            },
            confirmButton = {
                TextButton(onClick = { showVoltageWarning = false }) {
                    Text("OK")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Connection status banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (lowVoltage) StatusYellow.copy(alpha = 0.15f)
                    else StatusGreen.copy(alpha = 0.15f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (lowVoltage) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (lowVoltage) StatusYellow else StatusGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Connected",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (lowVoltage) StatusYellow else StatusGreen
                    )
                    Text(
                        state.deviceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface
                    )
                    if (state.voltage != null) {
                        Text(
                            "Voltage: ${"%.1f".format(voltage)}V",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (lowVoltage) StatusYellow else StatusGreen
                        )
                    }
                    if (state.adapterInfo != null) {
                        Text(
                            "ATI: ${state.adapterInfo}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                    if (state.protocolInfo != null) {
                        Text(
                            "${state.protocolInfo.description} · ATDPN=${state.protocolInfo.number}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
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
            title = "Dashboard",
            description = "Real-time sensor gauges — up to 6 customizable displays",
            icon = Icons.Default.Dashboard,
            onClick = onNavigateToDashboard
        )

        Spacer(modifier = Modifier.height(8.dp))

        FeatureCard(
            title = "Live Data Explorer",
            description = "Discover supported PIDs and browse all sensor values",
            icon = Icons.Default.QueryStats,
            onClick = onNavigateToLiveData
        )

        Spacer(modifier = Modifier.height(8.dp))

        FeatureCard(
            title = "Freeze Frame Data",
            description = "Sensor snapshot captured at the moment a fault occurred",
            icon = Icons.Default.CameraAlt,
            onClick = onNavigateToFreezeFrame
        )

        Spacer(modifier = Modifier.height(8.dp))

        FeatureCard(
            title = "Diagnostic Trouble Codes",
            description = "Read, view, and clear stored DTC fault codes",
            icon = Icons.Default.Build,
            onClick = onNavigateToDTC
        )

        Spacer(modifier = Modifier.height(8.dp))

        FeatureCard(
            title = "Vehicle Information",
            description = "VIN, calibration IDs, CVN, and ECU identification data",
            icon = Icons.Default.DirectionsCar,
            onClick = onNavigateToVehicleInfo
        )

        if (debugLoggingEnabled) {
            Spacer(modifier = Modifier.height(8.dp))

            FeatureCard(
                title = "Debug Console",
                description = "View raw ELM327 command and response log",
                icon = Icons.Default.BugReport,
                onClick = onNavigateToDebugConsole
            )
        }
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
