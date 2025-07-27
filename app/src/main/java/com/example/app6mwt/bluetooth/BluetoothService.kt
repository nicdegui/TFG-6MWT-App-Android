package com.example.app6mwt.bluetooth

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

// Estados de conexión más detallados (SIN CAMBIOS)
enum class BleConnectionStatus {
    IDLE,
    SCANNING,
    CONNECTING,
    CONNECTED,
    SUBSCRIBED,
    DISCONNECTED_BY_USER,
    DISCONNECTED_ERROR,
    RECONNECTING,
    ERROR_PERMISSIONS,
    ERROR_BLUETOOTH_DISABLED,
    ERROR_DEVICE_NOT_FOUND,
    ERROR_SERVICE_NOT_FOUND,
    ERROR_CHARACTERISTIC_NOT_FOUND,
    ERROR_SUBSCRIBE_FAILED,
    ERROR_GENERIC
}

// Datos que el servicio expondrá (SIN CAMBIOS)
data class BleDeviceData(
    val spo2: Int? = null,
    val heartRate: Int? = null,
    val signalStrength: Int? = null,
    val noFingerDetected: Boolean = true,
    val plethValue: Int? = null,
    val barGraphValue: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// UiScannedDevice (SIN CAMBIOS)
data class UiScannedDevice(
    val deviceName: String?,
    val address: String,
    val rssi: Int?,
    val rawDevice: BluetoothDevice
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UiScannedDevice
        return address == other.address
    }
    override fun hashCode(): Int = address.hashCode()
}


interface BluetoothService {
    val scannedDevices: StateFlow<List<UiScannedDevice>>
    val isScanning: StateFlow<Boolean>
    val connectionStatus: StateFlow<BleConnectionStatus>
    val connectedDevice: StateFlow<UiScannedDevice?>
    val bleDeviceData: StateFlow<BleDeviceData>
    val errorMessages: SharedFlow<String>
    val lastKnownConnectedDeviceAddress: StateFlow<String?> // Dirección del último dispositivo conectado exitosamente

    // --- NUEVO ---
    val isServiceReady: StateFlow<Boolean> // Para saber si BT está ON y con permisos
    fun getRequiredBluetoothPermissionsArray(): Array<String> // Para obtener los permisos necesarios

    // --- Funciones existentes (SIN CAMBIOS EN LA FIRMA) ---
    fun hasRequiredBluetoothPermissions(): Boolean
    fun isBluetoothEnabled(): Boolean
    fun isLocationEnabled(): Boolean
    fun startScan()
    fun stopScan()
    fun connect(deviceAddress: String)
    fun disconnect()
    fun clearScannedDevices()
}
