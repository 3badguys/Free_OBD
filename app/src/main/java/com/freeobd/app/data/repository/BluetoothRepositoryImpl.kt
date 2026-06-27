package com.freeobd.app.data.repository

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.freeobd.app.data.remote.BleTransport
import com.freeobd.app.data.remote.ObdTransport
import com.freeobd.app.data.remote.SppTransport
import com.freeobd.app.domain.model.BluetoothDeviceInfo
import com.freeobd.app.domain.model.ConnectionState
import com.freeobd.app.domain.model.DeviceType
import com.freeobd.app.domain.repository.BluetoothRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Implementation of [BluetoothRepository] using Android's native Bluetooth APIs.
 *
 * Supports simultaneous scanning of classic Bluetooth (SPP/RFCOMM) and
 * Bluetooth Low Energy (BLE/GATT) devices. Connection is handled through
 * the [ObdTransport] abstraction (SppTransport or BleTransport).
 *
 * Manages permissions for Android 6.0+ runtime checks and Android 12+ (API 31+)
 * BLUETOOTH_SCAN/BLUETOOTH_CONNECT permissions.
 */
class BluetoothRepositoryImpl(
    private val context: Context
) : BluetoothRepository {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _scanResults = MutableSharedFlow<BluetoothDeviceInfo>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    override val scanResults: Flow<BluetoothDeviceInfo> = _scanResults.asSharedFlow()

    private var activeTransport: ObdTransport? = null
    override val transport: ObdTransport? get() = activeTransport

    private val scanJob = SupervisorJob()
    private val scanScope = CoroutineScope(scanJob + Dispatchers.IO)
    private var scanReceiver: BroadcastReceiver? = null

    @Volatile
    override var isConnected: Boolean = false
        private set

    override var connectedDeviceAddress: String? = null
        private set

    override suspend fun startScan(): Result<Unit> {
        val error = checkPermissions()
        if (error != null) {
            return Result.failure(SecurityException(error))
        }

        val adapter = bluetoothAdapter
            ?: return Result.failure(IllegalStateException("Bluetooth not supported on this device"))

        if (!adapter.isEnabled) {
            return Result.failure(IllegalStateException("Bluetooth is disabled"))
        }

        if (_connectionState.value == ConnectionState.SCANNING) {
            return Result.success(Unit) // Already scanning
        }

        _connectionState.value = ConnectionState.SCANNING

        // Register broadcast receiver for classic Bluetooth discovery results
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE,
                                    BluetoothDevice::class.java
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                            .let { if (it != Short.MIN_VALUE) it.toInt() else null }

                        device?.let {
                            scanScope.launch {
                                _scanResults.emit(
                                    BluetoothDeviceInfo(
                                        address = device.address,
                                        name = device.name,
                                        type = detectDeviceType(device),
                                        rssi = rssi,
                                        isPaired = device.bondState == BluetoothDevice.BOND_BONDED
                                    )
                                )
                            }
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        stopScanInternal()
                        // Also emit paired devices that might not show up in discovery
                        emitPairedDevices()
                        _connectionState.compareAndSet(
                            ConnectionState.SCANNING,
                            ConnectionState.DISCONNECTED
                        )
                    }

                    BluetoothDevice.ACTION_NAME_CHANGED -> {
                        // Device name resolved
                    }
                }
            }
        }

        scanReceiver = receiver
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            }
        )

        // Start SPP discovery (classic Bluetooth)
        if (!adapter.startDiscovery()) {
            return Result.failure(IllegalStateException("Failed to start Bluetooth discovery"))
        }

        // Also start BLE scanning if supported
        if (adapter.bluetoothLeScanner != null) {
            startBleScan(adapter)
        }

        return Result.success(Unit)
    }

    override suspend fun stopScan() {
        stopScanInternal()
    }

    private fun stopScanInternal() {
        try {
            bluetoothAdapter?.cancelDiscovery()
            scanReceiver?.let { context.unregisterReceiver(it) }
            scanReceiver = null
            scanJob.cancelChildren()
        } catch (_: Exception) {
            // Receiver may already be unregistered
        }
    }

    override suspend fun connect(
        device: BluetoothDeviceInfo,
        protocol: String,
        ecuAddress: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.CONNECTING

        val transport: ObdTransport = when (device.type) {
            DeviceType.BLE -> BleTransport(context)
            DeviceType.SPP, DeviceType.UNKNOWN -> SppTransport()
        }

        val result = transport.connect(device)
        if (result.isSuccess) {
            activeTransport = transport
            isConnected = true
            connectedDeviceAddress = device.address
            _connectionState.value = ConnectionState.CONNECTED
        } else {
            _connectionState.value = ConnectionState.DISCONNECTED
        }

        result
    }

    override suspend fun disconnect() {
        activeTransport?.disconnect()
        activeTransport = null
        isConnected = false
        connectedDeviceAddress = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Start BLE scanning using BluetoothLeScanner.
     * Filters are set loosely to catch a wide range of OBD adapters.
     */
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            scanScope.launch {
                _scanResults.emit(
                    BluetoothDeviceInfo(
                        address = device.address,
                        name = device.name ?: "BLE OBD Adapter",
                        type = DeviceType.BLE,
                        rssi = result.rssi,
                        isPaired = device.bondState == BluetoothDevice.BOND_BONDED
                    )
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            android.util.Log.w("BluetoothRepo", "BLE scan failed: code=$errorCode")
        }
    }

    private fun startBleScan(adapter: BluetoothAdapter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                adapter.bluetoothLeScanner?.startScan(bleScanCallback)
            } catch (e: SecurityException) {
                android.util.Log.w("BluetoothRepo", "BLE scan failed: permission denied")
            }
        }
    }

    /**
     * Emit already-paired devices that may be OBD adapters.
     * Paired devices don't always appear in discovery scans.
     */
    private fun emitPairedDevices() {
        val adapter = bluetoothAdapter ?: return
        try {
            val pairedDevices: Set<BluetoothDevice> = adapter.bondedDevices
            pairedDevices.forEach { device ->
                if (isLikelyObdDevice(device.name, device.address)) {
                    scanScope.launch {
                        _scanResults.emit(
                            BluetoothDeviceInfo(
                                address = device.address,
                                name = device.name,
                                type = DeviceType.SPP,
                                isPaired = true
                            )
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permissions not granted
        }
    }

    /**
     * Determine if a device is likely an OBD-II adapter based on its name or MAC prefix.
     * Common adapter naming patterns:
     * - "OBDII", "OBD", "OBD-II"
     * - "ELM327", "ELM 327"
     * - "Vgate", "V-LINK"
     * - "CARISTA", "Carly"
     * - Generic "HC-05", "HC-06" (common Bluetooth modules used in adapters)
     */
    private fun isLikelyObdDevice(name: String?, address: String?): Boolean {
        val upperName = name?.uppercase() ?: ""
        return when {
            upperName.contains("OBD") -> true
            upperName.contains("ELM") -> true
            upperName.contains("V-LINK") || upperName.contains("VGATE") -> true
            upperName.contains("CARISTA") -> true
            upperName.contains("CARLY") -> true
            upperName.contains("HC-05") || upperName.contains("HC-06") -> true
            else -> false // Unknown — will still show in scan results
        }
    }

    /** Detect the transport type from the BluetoothDevice info. */
    private fun detectDeviceType(device: BluetoothDevice): DeviceType {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
                device.type == BluetoothDevice.DEVICE_TYPE_LE -> DeviceType.BLE
            device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC -> DeviceType.SPP
            device.type == BluetoothDevice.DEVICE_TYPE_DUAL -> DeviceType.SPP
            else -> DeviceType.UNKNOWN
        }
    }

    /**
     * Check all required Bluetooth permissions for the current Android API level.
     *
     * @return An error message string if permissions are missing, null if all granted.
     */
    private fun checkPermissions(): String? {
        val missingPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ uses BLUETOOTH_SCAN and BLUETOOTH_CONNECT
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                missingPermissions.add("BLUETOOTH_SCAN")
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                missingPermissions.add("BLUETOOTH_CONNECT")
            }
        } else {
            // Pre-Android 12 uses BLUETOOTH and BLUETOOTH_ADMIN
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                != PackageManager.PERMISSION_GRANTED
            ) {
                missingPermissions.add("BLUETOOTH")
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                missingPermissions.add("BLUETOOTH_ADMIN")
            }
            // BLE scanning also requires location on pre-12
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                missingPermissions.add("ACCESS_FINE_LOCATION")
            }
        }

        return if (missingPermissions.isNotEmpty()) {
            "Missing permissions: ${missingPermissions.joinToString(", ")}"
        } else {
            null
        }
    }
}
