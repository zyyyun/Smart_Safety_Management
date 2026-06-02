package com.example.smart_safety_management.watch.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
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
import com.jstyle.blesdk2208a.callback.DataListener2025
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

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

    private val dataListener = object : DataListener2025 {
        override fun dataCallback(maps: MutableMap<String, Any>?) {
            val reading = JcWearHealthReading.fromSdkMap(maps.orEmpty())
            if (reading.hasAnyValue) {
                _healthReadings.tryEmit(reading)
            }
        }

        override fun dataCallback(value: ByteArray?) = Unit
    }

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
        val isConnectedTarget = current.connectionState == JcWearConnectionState.CONNECTED &&
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
            runCatching { activeGatt?.let { writeCommand(it, BleSDK.RealTimeStep(false, false)) } }
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

    fun requestCurrentMeasurements() {
        val activeGatt = gatt ?: return
        writeCommand(activeGatt, BleSDK.RealTimeStep(true, true))
        writeCommand(activeGatt, BleSDK.GetDeviceBatteryLevel())
    }

    private fun vibrateForIdentification() {
        val activeGatt = gatt ?: return
        writeCommand(activeGatt, BleSDK.MotorVibrationWithTimes(IDENTIFY_VIBRATION_TIMES))
    }

    private fun startTelemetryLoop() {
        if (telemetryLoopActive) return
        telemetryLoopActive = true
        requestCurrentMeasurements()
        fun tick() {
            val activeGatt = gatt
            if (!telemetryLoopActive || activeGatt == null) return
            writeCommand(activeGatt, BleSDK.GetDeviceBatteryLevel())
            handler.postDelayed({ tick() }, BATTERY_REFRESH_INTERVAL_MS)
        }
        handler.postDelayed({ tick() }, BATTERY_REFRESH_INTERVAL_MS)
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
                enableNotifications(gatt)
                _uiState.value = _uiState.value.copy(
                    connectionState = JcWearConnectionState.CONNECTED,
                    errorMessage = null,
                )
                startTelemetryLoop()
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

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            handleNotification(characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotification(value)
        }
    }

    private fun handleNotification(value: ByteArray?) {
        if (value == null || value.isEmpty()) return
        runCatching { BleSDK.DataParsingWithData(value, dataListener) }
            .onFailure { Log.w(TAG, "Failed to parse JCWear payload", it) }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt) {
        val characteristic = notifyCharacteristic(gatt) ?: return
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: return
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)
    }

    @SuppressLint("MissingPermission")
    private fun writeCommand(gatt: BluetoothGatt, command: ByteArray?) {
        val characteristic = dataCharacteristic(gatt) ?: return
        characteristic.value = command ?: return
        gatt.writeCharacteristic(characteristic)
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
        jstyleService(gatt)?.getCharacteristic(DATA_CHARACTERISTIC_UUID)

    private fun notifyCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? =
        jstyleService(gatt)?.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID)

    private fun jstyleService(gatt: BluetoothGatt): BluetoothGattService? =
        gatt.getService(JSTYLE_SERVICE_UUID)

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

    companion object {
        private const val TAG = "JcWearBleBridge"
        private const val BATTERY_REFRESH_INTERVAL_MS = 5 * 60 * 1_000L
        private const val IDENTIFY_VIBRATION_TIMES = 2
        private const val IDENTIFY_VIBRATION_DELAY_MS = 800L
        private val JSTYLE_SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val DATA_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff6-0000-1000-8000-00805f9b34fb")
        private val NOTIFY_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff7-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

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
