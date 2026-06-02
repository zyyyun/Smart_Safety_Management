package com.example.smart_safety_management.watch.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.jstyle.blesdk2208a.Util.BleSDK
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JcWearBleBridge(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private var telemetryLoopActive = false
    private var pendingIdentifyAddress: String? = null

    private val _uiState = MutableStateFlow(
        JcWearScanUiState(
            permissionGranted = hasBluetoothPermission(),
            bluetoothEnabled = bluetoothAdapter?.isEnabled == true,
        ),
    )
    val uiState: StateFlow<JcWearScanUiState> = _uiState.asStateFlow()

    private val _healthReadings = MutableSharedFlow<JcWearHealthReading>(extraBufferCapacity = 16)
    val healthReadings: SharedFlow<JcWearHealthReading> = _healthReadings

    fun refreshEnvironment() {
        _uiState.value = _uiState.value.copy(
            permissionGranted = hasBluetoothPermission(),
            bluetoothEnabled = bluetoothAdapter?.isEnabled == true,
        )
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        refreshEnvironment()
        val adapter = bluetoothAdapter
        val current = _uiState.value
        if (!current.permissionGranted) {
            setError("블루투스 권한이 필요합니다.")
            return
        }
        if (adapter == null || !adapter.isEnabled) {
            _uiState.value = current.copy(
                bluetoothEnabled = false,
                scanning = false,
                connectionState = JcWearConnectionState.DISCONNECTED,
                errorMessage = "블루투스를 켜주세요.",
            )
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            setError("BLE 스캐너를 사용할 수 없습니다.")
            return
        }
        _uiState.value = current.copy(
            scanning = true,
            connectionState = JcWearConnectionState.SCANNING,
            errorMessage = null,
        )
        runCatching {
            scanner.startScan(
                null,
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                scanCallback,
            )
        }.onFailure { e ->
            Log.w(TAG, "startScan failed", e)
            setError("스캔을 시작하지 못했습니다: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!hasBluetoothPermission()) {
            _uiState.value = _uiState.value.copy(scanning = false)
            return
        }
        runCatching { bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _uiState.value = _uiState.value.copy(
            scanning = false,
            connectionState = if (_uiState.value.connectionState == JcWearConnectionState.SCANNING) {
                JcWearConnectionState.DISCONNECTED
            } else {
                _uiState.value.connectionState
            },
        )
    }

    @SuppressLint("MissingPermission")
    fun connect(device: JcWearDiscoveredDevice) {
        if (!hasBluetoothPermission()) {
            setError("블루투스 연결 권한이 필요합니다.")
            return
        }
        stopScan()
        disconnect()
        val remoteDevice = runCatching {
            bluetoothAdapter?.getRemoteDevice(device.address)
        }.getOrNull()
        if (remoteDevice == null) {
            setError("선택한 워치를 찾을 수 없습니다.")
            return
        }
        _uiState.value = _uiState.value.copy(
            selectedAddress = device.address,
            connectionState = JcWearConnectionState.CONNECTING,
            errorMessage = null,
        )
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            remoteDevice.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            remoteDevice.connectGatt(appContext, false, gattCallback)
        }
    }

    fun identify(device: JcWearDiscoveredDevice) {
        val current = _uiState.value
        val isConnectedTarget = (
            current.connectionState == JcWearConnectionState.CONNECTED ||
                current.connectionState == JcWearConnectionState.READING
            ) &&
            current.selectedAddress.equals(device.address, ignoreCase = true)
        if (isConnectedTarget && gatt != null) {
            vibrateForIdentification()
            return
        }
        pendingIdentifyAddress = device.address
        connect(device)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        telemetryLoopActive = false
        if (hasBluetoothPermission()) {
            val activeGatt = gatt
            runCatching { activeGatt?.disconnect() }
            runCatching { activeGatt?.close() }
        }
        handler.removeCallbacksAndMessages(null)
        gatt = null
        _uiState.value = _uiState.value.copy(
            connectionState = JcWearConnectionState.DISCONNECTED,
            selectedAddress = null,
        )
    }

    @SuppressLint("MissingPermission")
    fun close() {
        stopScan()
        disconnect()
    }

    private fun vibrateForIdentification() {
        val activeGatt = gatt ?: return
        writeCommand(activeGatt, BleSDK.MotorVibrationWithTimes(IDENTIFY_VIBRATION_TIMES))
    }

    private fun startBModeReadLoop(gatt: BluetoothGatt) {
        if (telemetryLoopActive) return
        telemetryLoopActive = true
        _uiState.value = _uiState.value.copy(connectionState = JcWearConnectionState.READING)
        writeCommand(gatt, JcWearBModeProtocol.resetCommand)
        handler.postDelayed({
            if (!telemetryLoopActive) return@postDelayed
            writeCommand(gatt, JcWearBModeProtocol.ppgInitCommand)
        }, B_MODE_PPG_INIT_DELAY_MS)
        handler.postDelayed({
            if (!telemetryLoopActive) return@postDelayed
            writeCommand(gatt, JcWearBModeProtocol.realtimeStartCommand)
            scheduleBModeRead(gatt)
        }, B_MODE_REALTIME_START_DELAY_MS)
    }

    @SuppressLint("MissingPermission")
    private fun scheduleBModeRead(gatt: BluetoothGatt) {
        if (!telemetryLoopActive) return
        val characteristic = dataCharacteristic(gatt) ?: run {
            failBModeRead("B-mode data characteristic not found.")
            return
        }
        val accepted = gatt.readCharacteristic(characteristic)
        if (!accepted) {
            failBModeRead("B-mode data read could not be started.")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.toJcWearDevice() ?: return
            val current = _uiState.value
            val merged = (current.discoveredDevices
                .filterNot { it.address == device.address } + device)
                .sortedByDescending { it.rssi ?: Int.MIN_VALUE }
            _uiState.value = current.copy(discoveredDevices = merged, errorMessage = null)
        }

        override fun onScanFailed(errorCode: Int) {
            setError("스캔 실패: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                telemetryLoopActive = false
                handler.removeCallbacksAndMessages(null)
                runCatching { gatt.close() }
                _uiState.value = _uiState.value.copy(
                    connectionState = JcWearConnectionState.FAILED,
                    errorMessage = "워치 연결에 실패했습니다. 다시 시도해주세요.",
                )
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> runCatching { gatt.discoverServices() }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    telemetryLoopActive = false
                    handler.removeCallbacksAndMessages(null)
                    runCatching { gatt.close() }
                    _uiState.value = _uiState.value.copy(
                        connectionState = JcWearConnectionState.DISCONNECTED,
                    )
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _uiState.value = _uiState.value.copy(
                    connectionState = JcWearConnectionState.CONNECTED,
                    errorMessage = null,
                )
                startBModeReadLoop(gatt)
                if (pendingIdentifyAddress.equals(_uiState.value.selectedAddress, ignoreCase = true)) {
                    pendingIdentifyAddress = null
                    handler.postDelayed({ vibrateForIdentification() }, IDENTIFY_VIBRATION_DELAY_MS)
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    connectionState = JcWearConnectionState.FAILED,
                    errorMessage = "워치 서비스를 확인하지 못했습니다.",
                )
            }
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            handleBModeRead(characteristic.value, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            handleBModeRead(value, status)
        }
    }

    private fun handleBModeRead(value: ByteArray, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            failBModeRead("B-mode data read failed: $status")
            return
        }
        val ppg = JcWearBModeProtocol.parsePpg(value)
        _healthReadings.tryEmit(JcWearHealthReading(ppgValue = ppg))
        _uiState.value = _uiState.value.copy(
            connectionState = JcWearConnectionState.READING,
            errorMessage = null,
        )
        handler.postDelayed({ gatt?.let { scheduleBModeRead(it) } }, B_MODE_READ_INTERVAL_MS)
    }

    @SuppressLint("MissingPermission")
    private fun writeCommand(gatt: BluetoothGatt, command: ByteArray) {
        val characteristic = dataCharacteristic(gatt) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                command,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            )
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = command
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun ScanResult.toJcWearDevice(): JcWearDiscoveredDevice? {
        val name = scanRecord?.deviceName ?: runCatching { device.name }.getOrNull()
        if (!isLikelyJcWear(name)) return null
        return JcWearDiscoveredDevice(
            address = device.address,
            name = name,
            rssi = rssi,
        )
    }

    private fun dataCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? =
        gatt.getService(JcWearBModeProtocol.serviceUuid)?.getCharacteristic(JcWearBModeProtocol.dataUuid)

    private fun hasBluetoothPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun setError(message: String) {
        _uiState.value = _uiState.value.copy(
            scanning = false,
            connectionState = JcWearConnectionState.FAILED,
            errorMessage = message,
        )
    }

    private fun failBModeRead(message: String) {
        telemetryLoopActive = false
        _uiState.value = _uiState.value.copy(
            connectionState = JcWearConnectionState.FAILED,
            errorMessage = message,
        )
    }

    companion object {
        private const val TAG = "JcWearBleBridge"
        private const val B_MODE_PPG_INIT_DELAY_MS = 3_000L
        private const val B_MODE_REALTIME_START_DELAY_MS = 5_000L
        private const val B_MODE_READ_INTERVAL_MS = 100L
        private const val IDENTIFY_VIBRATION_TIMES = 2
        private const val IDENTIFY_VIBRATION_DELAY_MS = 800L

        fun isLikelyJcWear(name: String?): Boolean {
            val normalized = name?.trim()?.lowercase().orEmpty()
            return normalized.contains("2208") ||
                normalized.contains("jstyle") ||
                normalized.contains("j-style") ||
                normalized.contains("jcwear") ||
                normalized.contains("bracelet")
        }
    }
}
