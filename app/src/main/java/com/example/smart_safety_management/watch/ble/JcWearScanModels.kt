package com.example.smart_safety_management.watch.ble

data class JcWearDiscoveredDevice(
    val address: String,
    val name: String?,
    val rssi: Int?,
) {
    val displayName: String
        get() = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown J2208A"

    val rssiLabel: String
        get() = rssi?.let { "$it dBm" } ?: "신호 확인 중"

    val registrationSerial: String
        get() = "J2208A-${address.uppercase()}"
}

enum class JcWearConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    FAILED,
}

data class JcWearScanUiState(
    val permissionGranted: Boolean = false,
    val bluetoothEnabled: Boolean = true,
    val scanning: Boolean = false,
    val selectedAddress: String? = null,
    val connectionState: JcWearConnectionState = JcWearConnectionState.DISCONNECTED,
    val discoveredDevices: List<JcWearDiscoveredDevice> = emptyList(),
    val errorMessage: String? = null,
) {
    val scanActionLabel: String
        get() = when {
            !permissionGranted -> "권한 허용"
            scanning -> "스캔 중지"
            else -> "스캔 시작"
        }

    val canRegister: Boolean
        get() = !selectedAddress.isNullOrBlank() &&
            connectionState == JcWearConnectionState.CONNECTED
}

data class JcWearHealthReading(
    val heartRate: Int? = null,
    val bodyTemp: Float? = null,
    val batteryLevel: Int? = null,
    val ppgValue: Int? = null,
) {
    val hasAnyValue: Boolean
        get() = heartRate != null || bodyTemp != null || batteryLevel != null || ppgValue != null

    companion object {
        fun fromSdkMap(map: Map<String, Any?>): JcWearHealthReading {
            val data = map["dicData"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val sources = listOf(data, map)
            val heart = sources.firstStringValue(
                "heartRate",
                "HeartRate",
                "heartValue",
                "onceHeartValue",
                "StaticHR",
                "PPGHrValue",
                "ECGHrValue",
                "PPGHR",
            )?.toIntOrNull()
            val temp = sources.firstStringValue(
                "KHrvTempValue",
                "temperature",
                "axillaryTemperature",
                "TempData",
            )
            val battery = sources.firstStringValue("batteryLevel", "BatteryLevel")?.toIntOrNull()
            return JcWearHealthReading(
                heartRate = heart,
                bodyTemp = temp?.replace(",", ".")?.toFloatOrNull(),
                batteryLevel = battery,
            )
        }

        private fun List<Map<*, *>>.firstStringValue(vararg keys: String): String? {
            for (source in this) {
                for (key in keys) {
                    val value = source[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    if (value != null) return value
                }
            }
            return null
        }
    }
}
