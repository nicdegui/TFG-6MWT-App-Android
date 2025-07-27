package com.example.app6mwt.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import com.example.app6mwt.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val applicationScope: CoroutineScope
) : BluetoothService {

    private val TAG = "BluetoothServiceImpl"

    private val bluetoothManager: BluetoothManager? by lazy {
        try {
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener BluetoothManager", e)
            null
        }
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }
    private var bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

    @Volatile
    private var bluetoothGatt: BluetoothGatt? = null

    private val _scannedDevices = MutableStateFlow<List<UiScannedDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<UiScannedDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionStatus = MutableStateFlow(BleConnectionStatus.IDLE)
    override val connectionStatus: StateFlow<BleConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectedDevice = MutableStateFlow<UiScannedDevice?>(null)
    override val connectedDevice: StateFlow<UiScannedDevice?> = _connectedDevice.asStateFlow()

    private val _bleDeviceData = MutableStateFlow(BleDeviceData())
    override val bleDeviceData: StateFlow<BleDeviceData> = _bleDeviceData.asStateFlow()

    private val _errorMessages = MutableSharedFlow<String>()
    override val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    private val foundDeviceAddresses = Collections.synchronizedSet(mutableSetOf<String>())
    private var currentConnectingDeviceAddress: String? = null
    private var isDisconnectInitiatedByUser = false

    private val _lastKnownConnectedDeviceAddress = MutableStateFlow<String?>(null)
    override val lastKnownConnectedDeviceAddress: StateFlow<String?> = _lastKnownConnectedDeviceAddress.asStateFlow()

    private var reconnectJob: Job? = null
    private val MAX_RECONNECT_ATTEMPTS = 1
    private var reconnectAttempts = 0

    private val _isBluetoothAdapterEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)

    private var miniRescanJob: Job? = null //

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                val newAdapterState = when (state) {
                    BluetoothAdapter.STATE_ON -> true
                    BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> false
                    else -> _isBluetoothAdapterEnabled.value // Mantener el estado anterior para estados intermedios
                }
                if (_isBluetoothAdapterEnabled.value != newAdapterState) {
                    Log.d(
                        TAG,
                        "Estado del adaptador Bluetooth cambiado a: ${if (newAdapterState) "ON" else "OFF"}"
                    )
                    _isBluetoothAdapterEnabled.value = newAdapterState
                    if (newAdapterState) { // Si el Bluetooth SE ENCENDIÓ
                        // Si el estado anterior era ERROR_BLUETOOTH_DISABLED, o IDLE y no teníamos permisos,
                        // ahora podría estar listo o simplemente IDLE pero operativo.
                        // Lo importante es que ya no está en ERROR_BLUETOOTH_DISABLED.
                        if (_connectionStatus.value == BleConnectionStatus.ERROR_BLUETOOTH_DISABLED) {
                            // Chequear si ahora tenemos permisos. Si no, el estado debería ser ERROR_PERMISSIONS.
                            // Si tenemos permisos, debería ser IDLE.
                            if (hasRequiredBluetoothPermissions()) {
                                _connectionStatus.value = BleConnectionStatus.IDLE
                                applicationScope.launch { _errorMessages.emit("Bluetooth operativo.") } // O un mensaje más genérico
                                Log.i(
                                    TAG,
                                    "Bluetooth activado. Estado cambiado a IDLE (Permisos OK)."
                                )
                            } else {
                                _connectionStatus.value = BleConnectionStatus.ERROR_PERMISSIONS
                                applicationScope.launch { _errorMessages.emit("Bluetooth activado, pero faltan permisos.") }
                                Log.w(
                                    TAG,
                                    "Bluetooth activado, pero faltan permisos. Estado cambiado a ERROR_PERMISSIONS."
                                )
                            }
                        }
                        // Podríamos considerar reiniciar el scanner aquí si el usuario había intentado escanear
                        // y falló solo por BT desactivado, pero es mejor que el usuario reintente la acción.

                    } else { // Si el Bluetooth SE APAGÓ (newAdapterState es false)
                        if (_connectionStatus.value != BleConnectionStatus.IDLE && _connectionStatus.value != BleConnectionStatus.ERROR_BLUETOOTH_DISABLED) {
                            // Si el BT se apaga y estábamos conectados/conectando, forzar desconexión
                            Log.w(
                                TAG,
                                "Bluetooth se ha desactivado. Forzando desconexión y reseteo de estados."
                            )
                            val gattToClose = bluetoothGatt
                            bluetoothGatt = null
                            gattToClose?.close()

                            _connectionStatus.value = BleConnectionStatus.ERROR_BLUETOOTH_DISABLED
                            _connectedDevice.value = null
                            _bleDeviceData.value = BleDeviceData()
                            _isScanning.value = false
                            reconnectJob?.cancel()
                            currentConnectingDeviceAddress = null
                            applicationScope.launch { _errorMessages.emit("Bluetooth se ha desactivado.") }
                        } else if (_connectionStatus.value == BleConnectionStatus.IDLE && !newAdapterState) {
                            // Si estaba IDLE y se apaga el BT, también es un estado de error por BT desactivado.
                            _connectionStatus.value = BleConnectionStatus.ERROR_BLUETOOTH_DISABLED
                            applicationScope.launch { _errorMessages.emit("Bluetooth se ha desactivado.") }
                            Log.d(
                                TAG,
                                "Bluetooth desactivado mientras estaba IDLE. Estado cambiado a ERROR_BLUETOOTH_DISABLED."
                            )
                        }
                    }
                }
            }
        }
    }

    override fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // API 28+
            locationManager.isLocationEnabled
        } else {
            try {
                // Método más antiguo, puede requerir más permisos o ser menos fiable
                @Suppress("DEPRECATION")
                val mode = android.provider.Settings.Secure.getInt(
                    context.contentResolver,
                    android.provider.Settings.Secure.LOCATION_MODE
                )
                mode != android.provider.Settings.Secure.LOCATION_MODE_OFF
            } catch (e: Exception) {
                Log.e(TAG, "Error al verificar el estado de la ubicación (legacy)", e)
                false // Asumir desactivado si hay error
            }
        }
    }

    override val isServiceReady: StateFlow<Boolean> = combine(
        _isBluetoothAdapterEnabled, // Se actualiza por el BroadcastReceiver
        _connectionStatus // Usamos connectionStatus como un trigger para re-evaluar permisos en caso de que cambien (aunque menos probable)
        // o simplemente para forzar la reevaluación cuando el estado de BT cambie
    ) { isAdapterEnabled, _ -> // El valor de connectionStatus no se usa directamente aquí
        val hasPerms = hasRequiredBluetoothPermissions() // Chequear permisos actualizados
        val locationOn = isLocationEnabled()
        Log.v(TAG, "isServiceReady recalculado: AdapterEnabled=$isAdapterEnabled, HasPerms=$hasPerms, LocationOn=$locationOn. Resultado: ${isAdapterEnabled && hasPerms}")
        isAdapterEnabled && hasPerms && locationOn
    }.stateIn(applicationScope, SharingStarted.WhileSubscribed(5000), hasRequiredBluetoothPermissions() && (bluetoothAdapter?.isEnabled == true) && isLocationEnabled()
    )


    init {
        Log.d(TAG, "BluetoothService Initialized")
        // --- NUEVO: Registrar BroadcastReceiver ---
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothStateReceiver, filter)
        // Actualizar estado inicial del adaptador por si acaso
        _isBluetoothAdapterEnabled.value = bluetoothAdapter?.isEnabled == true
        Log.d(TAG, "Estado inicial del adaptador BT: ${_isBluetoothAdapterEnabled.value}")
    }

    // Asegurarse de desregistrar el receiver cuando el servicio (si es Scoped) se destruya.
    // Si es un @Singleton a nivel de aplicación, se desregistraría cuando la app muere,
    // pero es buena práctica tener un método de limpieza si el scope es menor.
    // Por ahora, para un @Singleton de app, esto está "bien", pero para scopes menores,
    // se necesitaría un método @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY) o similar si el servicio fuera LifecycleOwner,
    // o un método explícito de 'shutdown' que el Application llame en su onTerminate (menos fiable).
    // Con Hilt @Singleton, Hilt maneja el ciclo de vida, pero no llama a un "onCleared" como en ViewModels.
    // Para simplificar, asumimos que el receiver vive mientras la app viva.

    private fun updateStatusAndEmitError(status: BleConnectionStatus, errorMessage: String, logError: Boolean = true) {
        if (logError) Log.e(TAG, errorMessage)
        _connectionStatus.value = status
        applicationScope.launch { _errorMessages.emit(errorMessage) }
    }

    override fun hasRequiredBluetoothPermissions(): Boolean {
        return getRequiredBluetoothPermissionsArray().all { // Usa la nueva función
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // --- NUEVO: Función para obtener el array de permisos ---
    override fun getRequiredBluetoothPermissionsArray(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    override fun isBluetoothEnabled(): Boolean {
        // Ahora podemos usar nuestro StateFlow interno, aunque bluetoothAdapter?.isEnabled también es válido
        // para una comprobación puntual. La ventaja de _isBluetoothAdapterEnabled.value es que es reactivo.
        return _isBluetoothAdapterEnabled.value // Más reactivo
        // return bluetoothAdapter?.isEnabled == true // Menos reactivo si el estado cambia externamente
    }

    @SuppressLint("MissingPermission")
    override fun startScan() {
        if (!hasRequiredBluetoothPermissions()) {
            updateStatusAndEmitError(BleConnectionStatus.ERROR_PERMISSIONS, "Faltan permisos Bluetooth para escanear.")
            return
        }
        if (!isBluetoothEnabled()) { // Usa la función actualizada
            updateStatusAndEmitError(BleConnectionStatus.ERROR_BLUETOOTH_DISABLED, "Bluetooth está desactivado.")
            return
        }
        if (_isScanning.value) {
            Log.d(TAG, "El escaneo ya está en progreso.")
            return
        }

        Log.d(TAG, "Iniciando escaneo BLE...")
        clearScannedDevicesInternal()
        _isScanning.value = true
        _connectionStatus.value = BleConnectionStatus.SCANNING

        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
            if (bluetoothLeScanner == null) {
                updateStatusAndEmitError(BleConnectionStatus.ERROR_GENERIC, "No se pudo obtener el BLE Scanner.")
                _isScanning.value = false
                _connectionStatus.value = BleConnectionStatus.IDLE
                return
            }
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bluetoothLeScanner?.startScan(null, scanSettings, leScanCallback)
        } catch (e: Exception) {
            updateStatusAndEmitError(BleConnectionStatus.ERROR_GENERIC, "Excepción al iniciar escaneo: ${e.localizedMessage}")
            _isScanning.value = false
            _connectionStatus.value = BleConnectionStatus.IDLE
            return
        }

        applicationScope.launch {
            delay(20000)
            if (_isScanning.value) {
                Log.d(TAG, "Escaneo detenido automáticamente por tiempo.")
                stopScan()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        if (!_isScanning.value) {
            return
        }
        Log.d(TAG, "Deteniendo escaneo BLE...")
        // Solo intentar detener formalmente si tenemos permisos y BT está encendido
        // Esto evita crashes si los permisos se revocan o BT se apaga durante el escaneo.
        if (hasRequiredBluetoothPermissions() && isBluetoothEnabled()) {
            try {
                bluetoothLeScanner?.stopScan(leScanCallback)
            } catch (e: Exception) { // SecurityException si faltan permisos, IllegalStateException si BT apagado
                Log.e(TAG, "Excepción al detener escaneo formalmente: ${e.localizedMessage}")
            }
        } else {
            Log.w(TAG, "No se puede detener el escaneo formalmente (sin permisos/BT apagado), pero se actualizan flags.")
        }

        _isScanning.value = false
        // Si estábamos en SCANNING (que es lo normal al detener un escaneo),
        // o si veníamos de un DISCONNECTED_ERROR donde se intentó un mini-escaneo que pudo o no completarse,
        // es seguro poner el estado en IDLE para permitir una nueva acción del usuario.
        if (_connectionStatus.value == BleConnectionStatus.SCANNING || _connectionStatus.value == BleConnectionStatus.DISCONNECTED_ERROR) {
            _connectionStatus.value = BleConnectionStatus.IDLE
            Log.d(TAG, "Escaneo detenido, estado cambiado a IDLE.")
        } else {
            // Si estábamos en otro estado (ej. CONNECTING se canceló indirectamente), mantenemos ese estado
            // o la lógica de conexión se encargará de él.
            Log.d(TAG, "Escaneo detenido, pero el estado de conexión era ${_connectionStatus.value} (no SCANNING ni DISCONNECTED_ERROR), no se cambia a IDLE por stopScan.")
        }
    }

    private fun clearScannedDevicesInternal() {
        _scannedDevices.value = emptyList()
        foundDeviceAddresses.clear()
    }

    override fun clearScannedDevices() {
        clearScannedDevicesInternal()
    }


    private val leScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val deviceAddress = device.address
                if (deviceAddress.isNullOrEmpty() || !BluetoothAdapter.checkBluetoothAddress(deviceAddress)) {
                    Log.w(TAG, "Dispositivo con dirección inválida encontrado y omitido.")
                    return
                }

                if (!foundDeviceAddresses.contains(deviceAddress)) {
                    foundDeviceAddresses.add(deviceAddress)
                    var deviceName: String? = "Desconocido"
                    // Solo intentar obtener el nombre si tenemos BLUETOOTH_CONNECT (API 31+) o los permisos antiguos
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED ||
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && hasRequiredBluetoothPermissions())) {
                        try {
                            deviceName = device.name ?: "Desconocido"
                        } catch (e: SecurityException) {
                            Log.w(TAG, "Permiso faltante para obtener nombre de dispositivo en escaneo para $deviceAddress.")
                            deviceName = "N/A (Sin Permiso)"
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Log.w(TAG, "Permiso BLUETOOTH_CONNECT faltante para obtener nombre de dispositivo en API S+ para $deviceAddress.")
                        deviceName = "N/A (Sin Permiso)"
                    }


                    val uiDevice = UiScannedDevice(
                        deviceName = deviceName,
                        address = deviceAddress,
                        rssi = result.rssi,
                        rawDevice = device
                    )
                    _scannedDevices.value = (_scannedDevices.value + uiDevice).distinctBy { it.address }
                    Log.d(TAG, "Dispositivo encontrado: ${uiDevice.deviceName} - ${uiDevice.address} RSSI: ${uiDevice.rssi}")
                } else {
                    _scannedDevices.update { list ->
                        list.map {
                            if (it.address == deviceAddress) it.copy(rssi = result.rssi) else it
                        }
                    }
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            updateStatusAndEmitError(BleConnectionStatus.ERROR_GENERIC, "Escaneo BLE fallido: código $errorCode")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    override fun connect(deviceAddress: String) {
        if (!hasRequiredBluetoothPermissions()) {
            updateStatusAndEmitError(BleConnectionStatus.ERROR_PERMISSIONS, "Faltan permisos para conectar.")
            return
        }
        if (!isBluetoothEnabled()) {
            updateStatusAndEmitError(BleConnectionStatus.ERROR_BLUETOOTH_DISABLED, "Bluetooth está desactivado.")
            return
        }
        if (_connectionStatus.value == BleConnectionStatus.CONNECTING && currentConnectingDeviceAddress == deviceAddress) {
            Log.d(TAG, "Ya se está intentando conectar a $deviceAddress")
            return
        }
        if ((_connectionStatus.value == BleConnectionStatus.CONNECTED || _connectionStatus.value == BleConnectionStatus.SUBSCRIBED) &&
            _connectedDevice.value?.address == deviceAddress) {
            Log.d(TAG, "Ya conectado a $deviceAddress")
            return
        }

        if (bluetoothGatt != null && _connectedDevice.value?.address != deviceAddress) {
            Log.d(TAG, "Conectando a un nuevo dispositivo ($deviceAddress), desconectando del anterior (${_connectedDevice.value?.address}).")
            // disconnect() internamente setea isDisconnectInitiatedByUser = true, lo cual es correcto aquí
            // para no intentar reconectar al antiguo.
            disconnect()
        }

        stopScan() // Detener escaneo antes de conectar

        val deviceToConnect: BluetoothDevice? = try {
            bluetoothAdapter?.getRemoteDevice(deviceAddress)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Dirección MAC inválida: $deviceAddress", e)
            null
        }

        if (deviceToConnect == null) {
            updateStatusAndEmitError(BleConnectionStatus.ERROR_DEVICE_NOT_FOUND, "No se pudo obtener BluetoothDevice para: $deviceAddress")
            return
        }

        _connectionStatus.value = BleConnectionStatus.CONNECTING
        currentConnectingDeviceAddress = deviceAddress
        isDisconnectInitiatedByUser = false // Resetear flag para esta nueva conexión
        reconnectAttempts = 0
        reconnectJob?.cancel()

        var deviceNameForLog: String? = deviceAddress
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && hasRequiredBluetoothPermissions())) {
            try {
                deviceNameForLog = deviceToConnect.name ?: deviceAddress
            } catch (e: SecurityException) { /* ya manejado */ }
        }

        Log.d(TAG, "Intentando conectar a GATT: ${deviceNameForLog}")

        // Cerrar explícitamente el GATT anterior si aún existe
        bluetoothGatt?.close()
        bluetoothGatt = null

        applicationScope.launch(ioDispatcher) {
            // Guardar la referencia al gatt devuelto por connectGatt
            val newGatt = deviceToConnect.connectGatt(
                context,
                false, // autoConnect = false
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
            bluetoothGatt = newGatt // Asignar a la variable de clase

            if (newGatt == null) {
                withContext(Dispatchers.Main) {
                    updateStatusAndEmitError(BleConnectionStatus.ERROR_GENERIC, "connectGatt devolvió null para $deviceAddress")
                    currentConnectingDeviceAddress = null
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        Log.d(TAG, "disconnect() llamado. GATT: ${if (bluetoothGatt != null) "Existe" else "Null"}. Conectado a: ${_connectedDevice.value?.address}")
        isDisconnectInitiatedByUser = true
        reconnectJob?.cancel() // Cancelar cualquier intento de reconexión en curso
        reconnectAttempts = 0
        // currentConnectingDeviceAddress = null; // No limpiar aquí, onConnectionStateChange lo manejará si la desconexión es exitosa

        val gattToDisconnect = bluetoothGatt // Capturar la instancia actual para trabajar con ella
        // bluetoothGatt = null; // No anular aquí todavía, esperar al callback o timeout

        if (gattToDisconnect != null) {
            if (hasRequiredBluetoothPermissions() && isBluetoothEnabled()) {
                Log.d(TAG, "Desconectando GATT para ${gattToDisconnect.device.address}...")
                try {
                    gattToDisconnect.disconnect() // Esto debería disparar onConnectionStateChange con STATE_DISCONNECTED
                    // El gatt.close() se llamará en onConnectionStateChange o después de un timeout
                    applicationScope.launch {
                        delay(600) // Dar un tiempo prudencial para que el callback se ejecute
                        // Solo cerrar aquí si el callback no lo hizo y el gatt sigue siendo el mismo
                        if (bluetoothGatt == gattToDisconnect && _connectionStatus.value != BleConnectionStatus.DISCONNECTED_BY_USER) {
                            Log.w(TAG, "Callback de desconexión no se ejecutó a tiempo o gatt no se limpió, cerrando explícitamente.")
                            gattToDisconnect.close()
                            if (bluetoothGatt == gattToDisconnect) bluetoothGatt = null // Asegurar limpieza
                            // Actualizar estado si no se hizo por el callback
                            _connectionStatus.value = BleConnectionStatus.DISCONNECTED_BY_USER
                            _connectedDevice.value = null
                            _bleDeviceData.value = BleDeviceData()
                        }
                    }
                } catch (e: Exception) { // SecurityException, IllegalStateException
                    Log.e(TAG, "Excepción durante disconnect(): ${e.localizedMessage}")
                    try { gattToDisconnect.close() } catch (exClose: Exception) { Log.e(TAG, "Excepción al cerrar GATT en error de disconnect: ${exClose.localizedMessage}")}
                    if (bluetoothGatt == gattToDisconnect) bluetoothGatt = null
                    _connectionStatus.value = BleConnectionStatus.DISCONNECTED_BY_USER
                    _connectedDevice.value = null
                    _bleDeviceData.value = BleDeviceData()
                }
            } else {
                Log.w(TAG, "Faltan permisos o BT apagado para desconectar GATT formalmente. Solo cerrando.")
                try { gattToDisconnect.close() } catch (e: Exception) {Log.e(TAG, "Excepción durante close forzado: ${e.localizedMessage}")}
                if (bluetoothGatt == gattToDisconnect) bluetoothGatt = null
                _connectionStatus.value = BleConnectionStatus.DISCONNECTED_BY_USER
                _connectedDevice.value = null
                _bleDeviceData.value = BleDeviceData()
            }
        } else {
            Log.d(TAG, "disconnect() llamado pero bluetoothGatt ya es null.")
            // Asegurar que el estado refleje la desconexión si no lo hace
            if (_connectionStatus.value != BleConnectionStatus.IDLE &&
                _connectionStatus.value != BleConnectionStatus.DISCONNECTED_BY_USER &&
                _connectionStatus.value != BleConnectionStatus.ERROR_BLUETOOTH_DISABLED) { // No sobreescribir si ya está en error de BT
                _connectionStatus.value = BleConnectionStatus.DISCONNECTED_BY_USER // O IDLE si es más apropiado
            }
            _connectedDevice.value = null
            _bleDeviceData.value = BleDeviceData()
        }
    }


    @SuppressLint("MissingPermission")
    private fun attemptReconnect() {
        if (isDisconnectInitiatedByUser) {
            Log.d(TAG, "Reconexión abortada, desconexión iniciada por el usuario.")
            // El estado ya debería ser DISCONNECTED_BY_USER por la llamada a disconnect()
            // o se establecerá en onConnectionStateChange si fue un error pero luego se llamó a disconnect.
            // Si el estado no es ya de desconexión por usuario, ponerlo.
            if (_connectionStatus.value != BleConnectionStatus.DISCONNECTED_BY_USER && _connectionStatus.value != BleConnectionStatus.IDLE) {
                _connectionStatus.value =
                    BleConnectionStatus.DISCONNECTED_ERROR // O el error original
            }
            // _connectedDevice.value = null // Ya debería estar limpio o se limpiará en onConnectionStateChange
            // _bleDeviceData.value = BleDeviceData() // Igual
            return
        }

        val addressToReconnect = currentConnectingDeviceAddress
            ?: _connectedDevice.value?.address // Priorizar el que se estaba intentando conectar

        if (addressToReconnect.isNullOrBlank()) {
            updateStatusAndEmitError(
                BleConnectionStatus.DISCONNECTED_ERROR,
                "No hay dirección válida para reconectar.",
                logError = false
            )
            _connectedDevice.value = null // Asegurar limpieza
            _bleDeviceData.value = BleDeviceData()
            return
        }
        if (!isBluetoothEnabled()) {
            updateStatusAndEmitError(
                BleConnectionStatus.ERROR_BLUETOOTH_DISABLED,
                "No se puede reconectar, Bluetooth desactivado."
            )
            return
        }
        if (!hasRequiredBluetoothPermissions()) {
            updateStatusAndEmitError(
                BleConnectionStatus.ERROR_PERMISSIONS,
                "No se puede reconectar, faltan permisos."
            )
            return
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            updateStatusAndEmitError(
                BleConnectionStatus.DISCONNECTED_ERROR,
                "Fallaron todos los intentos de reconexión a $addressToReconnect."
            )
            _connectedDevice.value = null
            _bleDeviceData.value = BleDeviceData()
            currentConnectingDeviceAddress = null // Limpiar a quién intentábamos conectar
            // --- INICIO NUEVA LÓGICA DE MINI-RESCAN ---
            Log.i(TAG, "Fallaron los reintentos. Iniciando mini-escaneo para actualizar lista.")
            // Cancelar cualquier escaneo o mini-escaneo anterior
            if (_isScanning.value) {
                try {
                    bluetoothLeScanner?.stopScan(leScanCallback)
                } catch (e: Exception) {
                    Log.w(TAG, "Error deteniendo escaneo activo antes de mini-escaneo: $e")
                }
                _isScanning.value = false // Asegurar que el flag está bajo
            }
            miniRescanJob?.cancel() // Cancelar mini-escaneo anterior si existe

            // Limpiar la lista de dispositivos actual ANTES del mini-escaneo para que sea fresca
            // Opcional: podrías mantenerla y solo actualizar/eliminar, pero limpiarla es más simple
            // para asegurar que el dispositivo desconectado desaparezca si ya no se anuncia.
            // clearScannedDevicesInternal() // Descomentar si quieres limpiar completamente antes.
            // Si no, el mini-escaneo añadirá/actualizará.

            _connectionStatus.value =
                BleConnectionStatus.SCANNING // Indicar que estamos escaneando brevemente
            _isScanning.value = true // Activar el flag de escaneo
            foundDeviceAddresses.clear() // Limpiar direcciones encontradas para el nuevo escaneo
            // Nota: Si NO limpias _scannedDevices aquí, y el dispositivo
            // que se desconectó sigue siendo anunciado por el OS (cache),
            // podría reaparecer. Limpiar foundDeviceAddresses es clave.

            if (bluetoothLeScanner == null) bluetoothLeScanner =
                bluetoothAdapter?.bluetoothLeScanner

            if (hasRequiredBluetoothPermissions() && isBluetoothEnabled() && bluetoothLeScanner != null) {
                val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Escaneo rápido
                    .build()
                try {
                    bluetoothLeScanner?.startScan(null, scanSettings, leScanCallback)
                    miniRescanJob = applicationScope.launch {
                        delay(5000) // Duración del mini-escaneo (5 segundos)
                        if (_isScanning.value && _connectionStatus.value == BleConnectionStatus.SCANNING) { // Solo detener si sigue en este modo
                            Log.d(TAG, "Mini-escaneo finalizado.")
                            stopScan() // stopScan cambiará _isScanning a false y _connectionStatus a IDLE si estaba en SCANNING
                        }
                    }
                } catch (e: Exception) {
                    updateStatusAndEmitError(
                        BleConnectionStatus.ERROR_GENERIC,
                        "Excepción al iniciar mini-escaneo: ${e.localizedMessage}"
                    )
                    _isScanning.value = false
                    _connectionStatus.value = BleConnectionStatus.IDLE // O DISCONNECTED_ERROR
                }
            } else {
                Log.w(TAG, "No se puede iniciar mini-escaneo (sin permisos, BT apagado o scanner nulo).")
                _isScanning.value = false
                // Dejar el estado como DISCONNECTED_ERROR o cambiar a IDLE si no hay más errores.
                // Si no se pudo escanear, el estado actual es DISCONNECTED_ERROR, lo cual es apropiado.
                _connectionStatus.value = BleConnectionStatus.IDLE // Si se quiere que vuelva a IDLE para que el usuario pueda reintentar manualmente
                applicationScope.launch { _errorMessages.emit("No se pudo actualizar la lista de dispositivos.") }

            }
            // --- FIN NUEVA LÓGICA DE MINI-RESCAN ---
            return
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            var deviceNameForLog = deviceAddress
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && hasRequiredBluetoothPermissions())
            ) {
                try {
                    deviceNameForLog = gatt.device.name ?: deviceAddress
                } catch (e: SecurityException) { /*ignorado*/
                }
            }

            Log.d(
                TAG,
                "onConnectionStateChange Address: $deviceNameForLog, GATT obj: ${
                    System.identityHashCode(gatt)
                }, Status: $status, NewState: $newState"
            )

            // Asegurarse de que este callback es para el GATT que nos interesa
            if (bluetoothGatt != null && bluetoothGatt != gatt && currentConnectingDeviceAddress != deviceAddress) {
                Log.w(
                    TAG,
                    "Callback de onConnectionStateChange para un GATT ($deviceNameForLog) diferente al actual (bluetoothGatt para ${bluetoothGatt?.device?.address}, currentConnecting: $currentConnectingDeviceAddress). Ignorando y cerrando este gatt obsoleto."
                )
                try {
                    gatt.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error cerrando gatt obsoleto: $e")
                }
                return
            }


            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "CONECTADO a GATT server en $deviceNameForLog.")
                    // Asegurarse que este es el GATT actual
                    if (bluetoothGatt != gatt && bluetoothGatt != null) {
                        Log.w(
                            TAG,
                            "GATT en callback ($deviceNameForLog) es diferente al bluetoothGatt actual (${bluetoothGatt?.device?.address}). Cerrando el anterior y usando el nuevo."
                        )
                        bluetoothGatt?.close() // Cerrar el gatt anterior si existía y era diferente
                    }
                    bluetoothGatt = gatt // Actualizar al gatt del callback

                    _connectionStatus.value = BleConnectionStatus.CONNECTED
                    reconnectAttempts = 0
                    reconnectJob?.cancel()
                    currentConnectingDeviceAddress = null // Conexión establecida

                    val connectedUiDevice =
                        _scannedDevices.value.find { it.address == deviceAddress }
                            ?: UiScannedDevice(deviceNameForLog, deviceAddress, 0, gatt.device)
                    _connectedDevice.value = connectedUiDevice

                    applicationScope.launch(ioDispatcher) {
                        delay(600)
                        Log.d(
                            TAG,
                            "Iniciando descubrimiento de servicios para $deviceNameForLog..."
                        )
                        if (bluetoothGatt == gatt) { // Re-confirmar que gatt no cambió
                            val discoveryInitiated = gatt.discoverServices()
                            if (!discoveryInitiated) {
                                Log.e(
                                    TAG,
                                    "discoverServices() falló al iniciar para $deviceNameForLog."
                                )
                                withContext(Dispatchers.Main) {
                                    updateStatusAndEmitError(
                                        BleConnectionStatus.ERROR_SERVICE_NOT_FOUND,
                                        "Fallo al iniciar descubrimiento de servicios."
                                    )
                                    disconnect()
                                }
                            }
                        } else {
                            Log.w(
                                TAG,
                                "GATT cambió durante onConnectionStateChange (CONNECTED) antes de discoverServices."
                            )
                        }
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(
                        TAG,
                        "DESCONECTADO de GATT server ($deviceNameForLog). ¿Iniciado por usuario?: $isDisconnectInitiatedByUser. GATT obj: ${
                            System.identityHashCode(gatt)
                        }"
                    )

                    // Limpieza del GATT
                    try {
                        gatt.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Excepción al cerrar GATT en disconnected state: $e")
                    }
                    if (bluetoothGatt == gatt) { // Solo invalidar si es el mismo objeto gatt
                        bluetoothGatt = null
                        Log.d(
                            TAG,
                            "bluetoothGatt (objeto ${System.identityHashCode(gatt)}) puesto a null."
                        )
                    } else {
                        Log.w(
                            TAG,
                            "GATT en callback de desconexión ($deviceNameForLog) es diferente al bluetoothGatt actual (${bluetoothGatt?.device?.address}). No se anula bluetoothGatt global."
                        )
                    }

                    _bleDeviceData.value = BleDeviceData()

                    if (isDisconnectInitiatedByUser) {
                        _connectionStatus.value = BleConnectionStatus.DISCONNECTED_BY_USER
                        _connectedDevice.value = null
                        currentConnectingDeviceAddress = null // Limpiar si fue desconexión manual
                        // isDisconnectInitiatedByUser = false; // Resetear para la próxima conexión, O NO. Depende.
                        // Si se resetea aquí, una reconexión automática podría dispararse si
                        // el siguiente error no la setea a true.
                        // Mejor resetearla en 'connect()'.
                    } else {
                        // Desconexión inesperada.
                        // currentConnectingDeviceAddress podría ser el dispositivo al que se intentaba conectar,
                        // o null si la conexión fue exitosa y luego se perdió.
                        // _connectedDevice.value?.address sería el dispositivo que se desconectó si estábamos conectados.
                        val lastAttemptedOrConnectedAddress =
                            currentConnectingDeviceAddress ?: _connectedDevice.value?.address

                        // Limpiar _connectedDevice.value ANTES de intentar reconectar para evitar estados inconsistentes
                        _connectedDevice.value = null

                        if (lastAttemptedOrConnectedAddress == deviceAddress) { // Asegurarse de que el evento es para el dispositivo relevante
                            Log.w(
                                TAG,
                                "Desconexión inesperada de $deviceNameForLog. Intentando reconexión."
                            )
                            // Guardar la dirección del dispositivo que se desconectó para el intento de reconexión
                            currentConnectingDeviceAddress = deviceAddress
                            updateStatusAndEmitError(
                                BleConnectionStatus.DISCONNECTED_ERROR,
                                "Conexión perdida con $deviceNameForLog.",
                                logError = false
                            ) // No es un error crítico aun
                            attemptReconnect()
                        } else {
                            Log.w(
                                TAG,
                                "Desconexión de $deviceNameForLog, pero no era el dispositivo activo o el que se intentaba conectar. Estado actual: ${_connectionStatus.value}"
                            )
                            // Si no es el dispositivo activo, solo actualizamos el estado si es relevante (ej. si estábamos CONNECTING a él)
                            if (currentConnectingDeviceAddress == deviceAddress) {
                                updateStatusAndEmitError(
                                    BleConnectionStatus.DISCONNECTED_ERROR,
                                    "Fallo al conectar con $deviceNameForLog.",
                                    logError = true
                                )
                                currentConnectingDeviceAddress =
                                    null // Limpiar porque falló la conexión inicial
                            }
                            // No intentar reconectar si no es el dispositivo relevante.
                        }
                    }
                }
            } else { // status != BluetoothGatt.GATT_SUCCESS
                Log.e(
                    TAG,
                    "Error en onConnectionStateChange para $deviceNameForLog. Status: $status, NewState: $newState. ¿Iniciado por usuario?: $isDisconnectInitiatedByUser. GATT obj: ${
                        System.identityHashCode(gatt)
                    }"
                )

                // Limpieza del GATT
                try {
                    gatt.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Excepción al cerrar GATT en error de conexión: $e")
                }
                if (bluetoothGatt == gatt) { // Solo invalidar si es el mismo objeto gatt
                    bluetoothGatt = null
                    Log.d(
                        TAG,
                        "bluetoothGatt (objeto ${System.identityHashCode(gatt)}) puesto a null debido a error."
                    )
                } else {
                    Log.w(
                        TAG,
                        "GATT en callback de error ($deviceNameForLog) es diferente al bluetoothGatt actual (${bluetoothGatt?.device?.address}). No se anula bluetoothGatt global."
                    )
                }

                _bleDeviceData.value = BleDeviceData()

                // Si fue un error durante un intento de conexión (currentConnectingDeviceAddress == deviceAddress)
                // O si estábamos conectados a este dispositivo (_connectedDevice.value?.address == deviceAddress)
                val relevantDeviceAddress =
                    currentConnectingDeviceAddress ?: _connectedDevice.value?.address

                _connectedDevice.value = null // Limpiar siempre en caso de error

                if (isDisconnectInitiatedByUser) {
                    _connectionStatus.value = BleConnectionStatus.DISCONNECTED_BY_USER
                    currentConnectingDeviceAddress = null
                } else if (relevantDeviceAddress == deviceAddress) { // Error relevante para el dispositivo activo/intentado
                    updateStatusAndEmitError(
                        BleConnectionStatus.DISCONNECTED_ERROR,
                        "Error de GATT ($status) al ${if (newState == BluetoothProfile.STATE_CONNECTING) "conectar" else "cambiar estado"} con $deviceNameForLog."
                    )
                    // Guardar la dirección del dispositivo que falló para el intento de reconexión
                    currentConnectingDeviceAddress = deviceAddress
                    attemptReconnect()
                } else {
                    // Error de un GATT que no era el principal o el que se intentaba conectar.
                    // Esto podría ser un callback tardío de un gatt.close() anterior.
                    Log.w(
                        TAG,
                        "Error de GATT ($status) para un dispositivo no activo/no intentado ($deviceNameForLog). No se tomarán acciones de reconexión."
                    )
                    if (currentConnectingDeviceAddress == deviceAddress) { // Si justo este era el que se estaba intentando conectar pero ya no es el gatt "oficial"
                        updateStatusAndEmitError(
                            BleConnectionStatus.DISCONNECTED_ERROR,
                            "Error de GATT ($status) al conectar con $deviceNameForLog (posiblemente gatt obsoleto)."
                        )
                        currentConnectingDeviceAddress = null
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val deviceNameForLog = gatt.device.name ?: gatt.device.address
            Log.d(TAG, "onServicesDiscovered para $deviceNameForLog. Status: $status. GATT obj: ${System.identityHashCode(gatt)}")

            if (bluetoothGatt != gatt) {
                Log.w(TAG, "Callback de onServicesDiscovered para un GATT ($deviceNameForLog) diferente al actual (${bluetoothGatt?.device?.address}). Ignorando.")
                return
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(BluetoothConstants.BM1000_SERVICE_UUID)
                if (service == null) {
                    Log.e(TAG, "Servicio BM1000 (${BluetoothConstants.BM1000_SERVICE_UUID}) no encontrado en $deviceNameForLog.")
                    updateStatusAndEmitError(BleConnectionStatus.ERROR_SERVICE_NOT_FOUND, "Servicio BLE requerido no encontrado.")
                    disconnect()
                    return
                }
                val characteristic = service.getCharacteristic(BluetoothConstants.BM1000_MEASUREMENT_CHARACTERISTIC_UUID)
                if (characteristic == null) {
                    Log.e(TAG, "Característica RX (${BluetoothConstants.BM1000_MEASUREMENT_CHARACTERISTIC_UUID}) no encontrada en $deviceNameForLog.")
                    updateStatusAndEmitError(BleConnectionStatus.ERROR_CHARACTERISTIC_NOT_FOUND, "Característica BLE requerida no encontrada.")
                    disconnect()
                    return
                }
                // Habilitar notificaciones para la característica
                val notificationSet = gatt.setCharacteristicNotification(characteristic, true)
                if (!notificationSet) {
                    Log.e(TAG, "Fallo al habilitar notificación para característica RX en $deviceNameForLog.")
                    updateStatusAndEmitError(BleConnectionStatus.ERROR_SUBSCRIBE_FAILED, "Fallo al suscribirse a notificaciones BLE (setCharacteristicNotification).")
                    disconnect()
                    return
                }
                // Escribir en el descriptor CCCD (Client Characteristic Configuration Descriptor)
                val cccDescriptor = characteristic.getDescriptor(BluetoothConstants.CCCD_UUID)
                if (cccDescriptor == null) {
                    Log.e(TAG, "Descriptor CCCD (${BluetoothConstants.CCCD_UUID}) no encontrado para RX en $deviceNameForLog.")
                    updateStatusAndEmitError(BleConnectionStatus.ERROR_SUBSCRIBE_FAILED, "Fallo al obtener descriptor CCCD.")
                    disconnect()
                    return
                }

                cccDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val descriptorWritten = gatt.writeDescriptor(cccDescriptor)
                if (!descriptorWritten) {
                    Log.e(TAG, "Fallo al escribir en el descriptor CCCD para $deviceNameForLog.")
                    updateStatusAndEmitError(BleConnectionStatus.ERROR_SUBSCRIBE_FAILED, "Fallo al suscribirse a notificaciones BLE (writeDescriptor).")
                    disconnect()
                } else {
                    Log.i(TAG, "Notificaciones habilitadas y descriptor CCCD escrito para $deviceNameForLog. Suscripción exitosa.")
                    _connectionStatus.value = BleConnectionStatus.SUBSCRIBED
                    // --- AQUÍ ACTUALIZAMOS EL STATEFLOW ---
                    _lastKnownConnectedDeviceAddress.value = gatt.device.address
                    Log.d(TAG, "Actualizado lastKnownConnectedDeviceAddress: ${gatt.device.address}")

                    applicationScope.launch { _errorMessages.emit("Dispositivo listo y recibiendo datos.") }
                }

            } else {
                Log.e(TAG, "onServicesDiscovered falló con status: $status para $deviceNameForLog")
                updateStatusAndEmitError(BleConnectionStatus.ERROR_SERVICE_NOT_FOUND, "Fallo al descubrir servicios (status: $status).")
                disconnect()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // Ya no se usa la sobrecarga obsoleta, usar la que incluye 'value'
        }

        // --- USAR ESTA SOBRECARGA para API 33+ ---
        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)

            if (characteristic.uuid == BluetoothConstants.BM1000_MEASUREMENT_CHARACTERISTIC_UUID) {
                // El dispositivo puede enviar múltiples paquetes de 5 bytes concatenados.
                // Procesar cada paquete de 5 bytes.
                if (value.isNotEmpty() && value.size % 5 == 0) {
                    val numberOfPackets = value.size / 5
                    Log.d(
                        TAG,
                        "BM1000 Data Received: ${value.toHexString()} (${value.size} bytes, $numberOfPackets packets)"
                    )

                    for (i in 0 until numberOfPackets) {
                        val packet = value.copyOfRange(i * 5, (i * 5) + 5)
                        Log.d(
                            TAG,
                            "Processing packet ${i + 1}/${numberOfPackets}: ${packet.toHexString()}"
                        )

                        // Sincronización (basado en el MSB del primer byte del paquete de 5)
                        // byte0: 1BPNSSSS
                        // El primer byte de un segmento válido DEBE tener el MSB (bit 7) a 1.
                        // Los siguientes 4 bytes del segmento DEBEN tener el MSB a 0.
                        // Esta es una validación simple por paquete, asumiendo que los paquetes vienen alineados
                        // desde el callback, lo cual es común para notificaciones BLE.
                        if ((packet[0].toInt() and 0x80) == 0) {
                            Log.w(
                                TAG,
                                "Desync? Packet ${i + 1} byte0 MSB is 0: ${packet[0].toHexString()}. Skipping this 5-byte packet."
                            )
                            continue // Saltar este paquete de 5 bytes si no parece el inicio de un segmento
                        }
                        // (Opcional) Verificar MSB de packet[1] a packet[4] si es necesario mayor robustez.
                        // for (j in 1..4) {
                        //     if ((packet[j].toInt() and 0x80) != 0) {
                        //         Log.w(TAG, "Desync? Packet ${i+1} byte${j} MSB is 1. Skipping.")
                        //         // break // or continue outer loop if this whole notification is bad
                        //     }
                        // }


                        // --- Parseo según la documentación ---
                        val byte0 = packet[0].toInt() // 1BPNSSSS
                        val byte1 = packet[1].toInt() and 0x7F // 0LLLLLLL (Pleth)
                        val byte2 = packet[2].toInt() // 0PRCGGGG (No aplicar &0x7F aún)
                        val byte3 = packet[3].toInt() and 0x7F // 0ppppppp (HR LSBs)
                        val byte4 = packet[4].toInt() and 0x7F // 0XXXXXXX (SpO2)

                        // Byte 0: 1BPNSSSS
                        // val beepEvent = (byte0 shr 6) and 0x01       // B (no usado directamente en BleDeviceData)
                        // val probeFlag = (byte0 shr 5) and 0x01       // P (documentado como siempre 0)
                        // val noSignalFlagN = (byte0 shr 4) and 0x01   // N (documentado como siempre 0)
                        val signalStrengthS =
                            byte0 and 0x0F             // SSSS (0-15) Sensor signal strength

                        // Byte 1: 0LLLLLLL
                        val plethL = byte1 // (0-127)

                        // Byte 2: 0PRCGGGG
                        val pulseRateBitP = (byte2 shr 6) and 0x01       // P (MSB de HR)
                        val pulseDetectionFlagR =
                            (byte2 shr 5) and 0x01 // R (0 = calibrando/sin dedo)
                        val fingerDetectionFlagC = (byte2 shr 4) and 0x01// C (0 = intentando medir)
                        val visualPulseIntensityG = byte2 and 0x0F       // GGGG (0-15, BarGraph)

                        // Byte 3: 0ppppppp
                        val pulseRateLsbsp = byte3

                        // Byte 4: 0XXXXXXX
                        val spo2X = byte4

                        // --- Cálculo de Valores Finales ---
                        val finalSpo2 = if (spo2X == 127) null else spo2X.coerceIn(
                            0,
                            100
                        ) // 127="sin calibración/nada"

                        val finalHRRaw = (pulseRateBitP shl 7) or pulseRateLsbsp
                        val finalHR =
                            if (finalHRRaw == 255) null else finalHRRaw // 255="nada que sensar"

                        // Lógica de "No Dedo Detectado" y "Calidad de Señal"
                        // Basado en tu documentación:
                        // fingerDetectionFlagC: 1 = Dedo NO detectado (sensor no está intentando medir)
                        // pulseDetectionFlagR: 0 = Calibrando o sin dedo. 1 = Pulso detectado.
                        // signalStrengthS: 15 = "no finger or (re)calibration in progress"
                        // visualPulseIntensityG (BarGraph): 0 = "no sensing", 15 = "no measurements displayed"
                        // spo2X == 127 o finalHRRaw == 255 también son indicadores.

                        var noFingerDetected = false
                        if (fingerDetectionFlagC == 1) { // C=1 (inverted logic: 0 means sensor is trying measurement)
                            noFingerDetected = true
                            Log.d(TAG, "NoFinger: fingerDetectionFlagC = 1")
                        } else if (signalStrengthS == 15) { // S=15 indica "no finger or recalibration"
                            noFingerDetected =
                                true // Podría ser también "calibrando", pero a efectos de mostrar SpO2/HR, es similar a "no dedo"
                            Log.d(TAG, "NoFinger or Recalibrating: signalStrengthS = 15")
                        } else if (spo2X == 127 || finalHRRaw == 255) { // Valores inválidos también pueden implicar no dedo
                            noFingerDetected = true
                            Log.d(TAG, "NoFinger: spo2X=127 or finalHRRaw=255")
                        } else if (pulseDetectionFlagR == 0 && fingerDetectionFlagC == 0) {
                            // C=0 (intentando medir) y R=0 (calibrando o sin dedo, pero C=0 sugiere "calibrando con dedo")
                            // En este caso, el dedo está, pero aún no hay lectura estable.
                            // Para el _bleDeviceData.noFingerDetected lo marcamos como false, pero SpO2/HR podrían ser null
                            // si aún no son válidos. El `signalStrengthS` podría ser bajo aquí.
                            Log.d(
                                TAG,
                                "Calibrating with finger: fingerDetectionFlagC=0, pulseDetectionFlagR=0"
                            )
                            noFingerDetected = false // Dedo presente, pero calibrando
                        }


                        // Usamos signalStrengthS como el indicador principal de la calidad de la señal del sensor
                        // y visualPulseIntensityG como el barGraph para la UI.
                        val currentSignalStrength = signalStrengthS // SSSS

                        _bleDeviceData.value = BleDeviceData(
                            spo2 = if (noFingerDetected) null else finalSpo2,
                            heartRate = if (noFingerDetected) null else finalHR,
                            signalStrength = currentSignalStrength, // Usar SSSS como la "fuerza de señal" principal
                            noFingerDetected = noFingerDetected,
                            barGraphValue = visualPulseIntensityG, // GGGG para el bargraph
                            plethValue = plethL, // LLLLLLL para el pletismograma
                            timestamp = System.currentTimeMillis()
                        )
                        Log.d(
                            TAG,
                            "Parsed BM1000 Data: SpO2=$finalSpo2, HR=$finalHR, Signal(S)=$currentSignalStrength, NoFinger=$noFingerDetected, BarGraph(G)=$visualPulseIntensityG, Pleth(L)=$plethL, R=$pulseDetectionFlagR, C=$fingerDetectionFlagC"
                        )

                    } // Fin del bucle for (para múltiples paquetes)

                } else if (value.isNotEmpty()) {
                    Log.w(
                        TAG,
                        "Paquete BM1000 con longitud inesperada (no múltiplo de 5): ${value.size} bytes. Contenido: ${value.toHexString()}. Descartando."
                    )
                } else {
                    Log.w(TAG, "Paquete BM1000 vacío recibido.")
                }
            } else {
                Log.d(
                    TAG,
                    "onCharacteristicChanged for other UUID: ${characteristic.uuid}, value: ${value.toHexString()}"
                )
            }
        }
    }
    // Helper para logs de ByteArrays (opcional)
    private fun ByteArray.toHexString(): String = joinToString(separator = " ") { eachByte -> "%02x".format(eachByte) }
    private fun Byte.toHexString(): String = "%02x".format(this)

} // Fin de la clase
