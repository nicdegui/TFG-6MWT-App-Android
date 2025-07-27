package com.example.app6mwt.ui

import android.Manifest
import android.app.Application
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app6mwt.bluetooth.BleConnectionStatus
import com.example.app6mwt.bluetooth.BluetoothService
import com.example.app6mwt.data.DefaultThresholdValues
import com.example.app6mwt.data.PacienteRepository
import com.example.app6mwt.data.SettingsRepository
import com.example.app6mwt.data.model.PruebaRealizada
import com.example.app6mwt.bluetooth.UiScannedDevice as ServiceUiScannedDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import kotlin.collections.toTypedArray

// --- Modelos de Datos ---
data class PatientDetails(
    val id: String,
    val fullName: String,
    var sex: String,
    var age: Int,
    var heightCm: Int,
    var weightKg: Int,
    var usesInhalers: Boolean,
    var usesOxygen: Boolean
)

data class TestPreparationData(
    val patientId: String,
    val patientFullName: String,
    val patientSex: String,
    val patientAge: Int,
    val patientHeightCm: Int,
    val patientWeightKg: Int,
    val usesInhalers: Boolean,
    val usesOxygen: Boolean,
    val theoreticalDistance: Double,
    val basalSpo2: Int,
    val basalHeartRate: Int,
    val basalBloodPressureSystolic: Int,
    val basalBloodPressureDiastolic: Int,
    val basalRespiratoryRate: Int,
    val basalDyspneaBorg: Int,
    val basalLegPainBorg: Int,
    val devicePlacementLocation: String,
    val isFirstTestForPatient: Boolean
)

enum class DevicePlacementLocation {
    NONE, FINGER, EAR
}

// --- Eventos de Navegación ---
sealed class PreparationNavigationEvent {
    data class ToTestExecution(val preparationData: TestPreparationData) : PreparationNavigationEvent()
}

@HiltViewModel
class PreparationViewModel @Inject constructor(
    private val application: Application,
    val bluetoothService: BluetoothService,
    private val settingsRepository: SettingsRepository,
    private val pacienteRepository: PacienteRepository
) : ViewModel() {

    // --- RANGOS DE ENTRADA DESDE SETTINGS (ALINEADO CON TESTRESULTSVIEWMODEL) ---
    private var inputSpo2Min = DefaultThresholdValues.SPO2_INPUT_MIN_DEFAULT
    private var inputSpo2Max = DefaultThresholdValues.SPO2_INPUT_MAX_DEFAULT
    private var inputHrMin = DefaultThresholdValues.HR_INPUT_MIN_DEFAULT
    private var inputHrMax = DefaultThresholdValues.HR_INPUT_MAX_DEFAULT
    private var inputBpSysMin = DefaultThresholdValues.BP_SYSTOLIC_INPUT_MIN_DEFAULT
    private var inputBpSysMax = DefaultThresholdValues.BP_SYSTOLIC_INPUT_MAX_DEFAULT
    private var inputBpDiaMin = DefaultThresholdValues.BP_DIASTOLIC_INPUT_MIN_DEFAULT
    private var inputBpDiaMax = DefaultThresholdValues.BP_DIASTOLIC_INPUT_MAX_DEFAULT
    private var inputRrMin = DefaultThresholdValues.RR_INPUT_MIN_DEFAULT
    private var inputRrMax = DefaultThresholdValues.RR_INPUT_MAX_DEFAULT
    private var inputBorgMin = DefaultThresholdValues.BORG_INPUT_MIN_DEFAULT
    private var inputBorgMax = DefaultThresholdValues.BORG_INPUT_MAX_DEFAULT

    // Estados para los hints de los rangos en la UI
    private val _spo2RangeHint = mutableStateOf("Rango (${DefaultThresholdValues.SPO2_INPUT_MIN_DEFAULT}-${DefaultThresholdValues.SPO2_INPUT_MAX_DEFAULT})")
    val spo2RangeHint: State<String> = _spo2RangeHint

    private val _hrRangeHint = mutableStateOf("Rango (${DefaultThresholdValues.HR_INPUT_MIN_DEFAULT}-${DefaultThresholdValues.HR_INPUT_MAX_DEFAULT})")
    val hrRangeHint: State<String> = _hrRangeHint

    private val _bpRangeHint = mutableStateOf("S(${DefaultThresholdValues.BP_SYSTOLIC_INPUT_MIN_DEFAULT}-${DefaultThresholdValues.BP_SYSTOLIC_INPUT_MAX_DEFAULT}), D(${DefaultThresholdValues.BP_DIASTOLIC_INPUT_MIN_DEFAULT}-${DefaultThresholdValues.BP_DIASTOLIC_INPUT_MAX_DEFAULT})")
    val bpRangeHint: State<String> = _bpRangeHint

    private val _rrRangeHint = mutableStateOf("Rango (${DefaultThresholdValues.RR_INPUT_MIN_DEFAULT}-${DefaultThresholdValues.RR_INPUT_MAX_DEFAULT})")
    val rrRangeHint: State<String> = _rrRangeHint

    private val _borgRangeHint = mutableStateOf("Rango (${DefaultThresholdValues.BORG_INPUT_MIN_DEFAULT}-${DefaultThresholdValues.BORG_INPUT_MAX_DEFAULT})")
    val borgRangeHint: State<String> = _borgRangeHint

    // --- Estados para la UI ---
    private val _patientId = MutableStateFlow<String?>(null)
    val patientId: StateFlow<String?> = _patientId.asStateFlow()
    private var _internalPatientDetails by mutableStateOf<PatientDetails?>(null)
    val internalPatientDetails: PatientDetails?
        get() = _internalPatientDetails
    private val _patientHasPreviousHistory = MutableStateFlow(false)
    val patientHasPreviousHistory: StateFlow<Boolean> = _patientHasPreviousHistory.asStateFlow()
    private val _patientFullName = MutableStateFlow("")
    val patientFullName: StateFlow<String> = _patientFullName.asStateFlow()
    private val _patientSex = MutableStateFlow("")
    val patientSex: StateFlow<String> = _patientSex.asStateFlow()
    private val _patientAge = MutableStateFlow("")
    val patientAge: StateFlow<String> = _patientAge.asStateFlow()
    private val _patientHeightCm = MutableStateFlow("")
    val patientHeightCm: StateFlow<String> = _patientHeightCm.asStateFlow()
    private val _patientWeightKg = MutableStateFlow("")
    val patientWeightKg: StateFlow<String> = _patientWeightKg.asStateFlow()
    private val _usesInhalers = MutableStateFlow(false)
    val usesInhalers: StateFlow<Boolean> = _usesInhalers.asStateFlow()
    private val _usesOxygen = MutableStateFlow(false)
    val usesOxygen: StateFlow<Boolean> = _usesOxygen.asStateFlow()
    private val _theoreticalDistance = mutableDoubleStateOf(0.0)
    val theoreticalDistance: State<Double> = _theoreticalDistance

    // --- ESTADOS BLE ADAPTADOS ---
    private val _isBleReady = MutableStateFlow(false)
    val isBleReady: StateFlow<Boolean> = _isBleReady.asStateFlow()

    val connectionStatus: StateFlow<BleConnectionStatus> = bluetoothService.connectionStatus // Directo del servicio

    private val _uiBluetoothMessage = MutableStateFlow<String?>("Verificando estado de Bluetooth...") // Mensaje inicial
    val uiBluetoothMessage: StateFlow<String?> = _uiBluetoothMessage.asStateFlow()

    val scannedDevices: StateFlow<List<ServiceUiScannedDevice>> = bluetoothService.scannedDevices // Directo del servicio

    val connectedDeviceName: StateFlow<String?> = bluetoothService.connectedDevice
        .map { serviceDevice -> serviceDevice?.deviceName ?: serviceDevice?.address } // Asumiendo que connectedDevice en el servicio es BluetoothDevice?
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val connectedDeviceAddress: StateFlow<String?> = bluetoothService.connectedDevice
        .map { serviceDevice -> serviceDevice?.address }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isConnecting: StateFlow<Boolean> = bluetoothService.connectionStatus.map {
        it == BleConnectionStatus.CONNECTING || it == BleConnectionStatus.RECONNECTING
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isScanning: StateFlow<Boolean> = bluetoothService.isScanning // Directo del servicio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // --- Estados de Datos BLE (actualizados por el SERVICIO ahora) ---
    private val _latestBleSpo2 = MutableStateFlow<Int?>(null) // Se actualizará desde el servicio
    val latestBleSpo2: StateFlow<Int?> = _latestBleSpo2.asStateFlow()
    private val _latestBleHeartRate = MutableStateFlow<Int?>(null) // Se actualizará desde el servicio
    val latestBleHeartRate: StateFlow<Int?> = _latestBleHeartRate.asStateFlow()
    private val _latestBleSignalStrength = MutableStateFlow<Int?>(null)
    val latestBleSignalStrength: StateFlow<Int?> = _latestBleSignalStrength.asStateFlow()
    private val _latestBleNoFinger = MutableStateFlow<Boolean?>(true)
    val latestBleNoFinger: StateFlow<Boolean?> = _latestBleNoFinger.asStateFlow()
    private val _latestBlePleth = MutableStateFlow<Int?>(null)
    val latestBlePleth: StateFlow<Int?> = _latestBlePleth.asStateFlow()
    private val _latestBleBarGraph = MutableStateFlow<Int?>(null)
    val latestBleBarGraph: StateFlow<Int?> = _latestBleBarGraph.asStateFlow()

    // --- ESTADOS DE ENTRADA MANUAL DE BASALES (SIN CAMBIOS) ---
    private val _spo2Input = MutableStateFlow("")
    val spo2Input: StateFlow<String> = _spo2Input.asStateFlow()
    private val _heartRateInput = MutableStateFlow("")
    val heartRateInput: StateFlow<String> = _heartRateInput.asStateFlow()
    private val _bloodPressureInput = MutableStateFlow("")
    val bloodPressureInput: StateFlow<String> = _bloodPressureInput.asStateFlow()
    private val _respiratoryRateInput = MutableStateFlow("")
    val respiratoryRateInput: StateFlow<String> = _respiratoryRateInput.asStateFlow()
    private val _dyspneaBorgInput = MutableStateFlow("")
    val dyspneaBorgInput: StateFlow<String> = _dyspneaBorgInput.asStateFlow()
    private val _legPainBorgInput = MutableStateFlow("")
    val legPainBorgInput: StateFlow<String> = _legPainBorgInput.asStateFlow()

    private val _basalValuesStatusMessage = MutableStateFlow("Complete los campos basales.")
    val basalValuesStatusMessage: StateFlow<String> = _basalValuesStatusMessage.asStateFlow()
    private val _areBasalsValid = MutableStateFlow(false)
    val areBasalsValid: StateFlow<Boolean> = _areBasalsValid.asStateFlow()
    private val _isValidSpo2 = MutableStateFlow(true)
    val isValidSpo2: StateFlow<Boolean> = _isValidSpo2.asStateFlow()
    private val _isValidHeartRate = MutableStateFlow(true)
    val isValidHeartRate: StateFlow<Boolean> = _isValidHeartRate.asStateFlow()
    private val _isValidBloodPressure = MutableStateFlow(true)
    val isValidBloodPressure: StateFlow<Boolean> = _isValidBloodPressure.asStateFlow()
    private val _isValidRespiratoryRate = MutableStateFlow(true)
    val isValidRespiratoryRate: StateFlow<Boolean> = _isValidRespiratoryRate.asStateFlow()
    private val _isValidDyspneaBorg = MutableStateFlow(true)
    val isValidDyspneaBorg: StateFlow<Boolean> = _isValidDyspneaBorg.asStateFlow()
    private val _isValidLegPainBorg = MutableStateFlow(true)
    val isValidLegPainBorg: StateFlow<Boolean> = _isValidLegPainBorg.asStateFlow()

    // --- OTROS ESTADOS DE UI Y EVENTOS (SIN CAMBIOS) ---
    private val _showNavigateBackDialog = MutableStateFlow(false)
    val showNavigateBackDialog: StateFlow<Boolean> = _showNavigateBackDialog.asStateFlow()
    private val _navigateBackEvent = MutableSharedFlow<Unit>()
    val navigateBackEvent: SharedFlow<Unit> = _navigateBackEvent.asSharedFlow()
    private val _isDevicePlaced = MutableStateFlow(false)
    val isDevicePlaced: StateFlow<Boolean> = _isDevicePlaced.asStateFlow()
    private val _devicePlacementLocation = MutableStateFlow(DevicePlacementLocation.NONE)
    val devicePlacementLocation: StateFlow<DevicePlacementLocation> = _devicePlacementLocation.asStateFlow()
    private val _navigateToEvent = MutableSharedFlow<PreparationNavigationEvent>()
    val navigateToEvent = _navigateToEvent.asSharedFlow()

    // --- GESTIÓN DE PERMISOS Y ACTIVACIONES ---
    private val _requestEnableBluetoothEvent = MutableSharedFlow<Unit>()
    val requestEnableBluetoothEvent = _requestEnableBluetoothEvent.asSharedFlow()
    // Evento para solicitar permisos de Android (puede ser una lista)
    private val _requestPermissionsEvent = MutableSharedFlow<Array<String>>()
    val requestPermissionsEvent: SharedFlow<Array<String>> = _requestPermissionsEvent.asSharedFlow()
    private val _requestLocationServicesEvent = MutableSharedFlow<Unit>()
    val requestLocationServicesEvent = _requestLocationServicesEvent.asSharedFlow()

    private val _lastServiceErrorMessage = MutableStateFlow<String?>(null)

    // Definiciones de conjuntos de permisos (Constantes)
    companion object {
        val BT_PERMISSIONS_S_AND_ABOVE = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        const val ACCESS_FINE_LOCATION_STRING = Manifest.permission.ACCESS_FINE_LOCATION

        // Para Android < 12 (API < 31), los permisos son diferentes
        val BT_PERMISSIONS_BELOW_S = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            ACCESS_FINE_LOCATION_STRING // Ubicación es crucial para escanear en estas versiones
        )

        fun getRequiredBluetoothPermissions(context: Context): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BT_PERMISSIONS_S_AND_ABOVE
            } else {
                BT_PERMISSIONS_BELOW_S
            }
        }
    }

    // --- LÓGICA MANUAL PARA CANSTARTTEST ---
    private val _combinedCanStartTestConditions = MutableStateFlow(calculateCanStartTest())
    val canStartTest: StateFlow<Boolean> = _combinedCanStartTestConditions.asStateFlow()

    private fun calculateCanStartTest(): Boolean {
        val currentIsBleReady = _isBleReady.value
        val currentConnectionStatus = connectionStatus.value // Del servicio
        val currentAreBasalsValid = _areBasalsValid.value
        val currentIsDevicePlaced = _isDevicePlaced.value
        val currentDevicePlacementLocation = _devicePlacementLocation.value
        val currentPatientId = _patientId.value
        val currentPatientFullName = _patientFullName.value
        val currentInternalPatientDetails = _internalPatientDetails // Chequea que los detalles estén cargados y sean válidos
        val currentNoFinger = _latestBleNoFinger.value ?: true // Si es null, asumir que no hay dedo

        val isConnectedAndSubscribed = currentConnectionStatus == BleConnectionStatus.SUBSCRIBED
        val devicePlacementOk = if (currentIsDevicePlaced) {
            currentDevicePlacementLocation != DevicePlacementLocation.NONE
        } else {
            false
        }
        val patientInfoOk = !currentPatientId.isNullOrBlank() &&
                currentPatientFullName.isNotBlank() &&
                currentInternalPatientDetails != null &&
                currentInternalPatientDetails.sex.isNotBlank() &&
                currentInternalPatientDetails.age > 0 &&
                currentInternalPatientDetails.heightCm > 0 &&
                currentInternalPatientDetails.weightKg > 0

        val allConditionsMet = currentIsBleReady && // Condición clave añadida/modificada
                isConnectedAndSubscribed &&
                devicePlacementOk &&
                patientInfoOk &&
                currentAreBasalsValid &&
                !currentNoFinger

        Log.d("CanStartTestLogic", "Calculating: Status=$currentConnectionStatus (${isConnectedAndSubscribed}), Basals=$currentAreBasalsValid, Placed=$currentIsDevicePlaced ($devicePlacementOk), PatientID=${!currentPatientId.isNullOrBlank()}, PatientName=${currentPatientFullName.isNotBlank()}, DetailsSex=${currentInternalPatientDetails?.sex?.isNotBlank()}, NoFinger=${!currentNoFinger}, Final=$allConditionsMet")
        return allConditionsMet
    }

    private fun setupCanStartTestManualListeners() {val flowsToObserve = listOf(
        _isBleReady,
        connectionStatus,
        _areBasalsValid,
        _isDevicePlaced,
        _devicePlacementLocation.asStateFlow(), // Convertir a StateFlow para onEach
        _patientId,
        _patientFullName,
        _latestBleNoFinger,
        // Observar cambios en internalPatientDetails indirectamente a través de sus componentes
        _patientSex, _patientAge, _patientHeightCm, _patientWeightKg
    )

        flowsToObserve.forEach { flow ->
            flow.onEach {
                _combinedCanStartTestConditions.value = calculateCanStartTest()
            }.launchIn(viewModelScope)
        }
    }

    init {
        Log.d(TAG, "PreparationViewModel init")
        viewModelScope.launch {
            // Cargar los rangos de entrada UNA VEZ desde el repositorio
            inputSpo2Min = settingsRepository.spo2InputMinFlow.first()
            inputSpo2Max = settingsRepository.spo2InputMaxFlow.first()
            inputHrMin = settingsRepository.hrInputMinFlow.first()
            inputHrMax = settingsRepository.hrInputMaxFlow.first()
            inputBpSysMin = settingsRepository.bpSystolicInputMinFlow.first()
            inputBpSysMax = settingsRepository.bpSystolicInputMaxFlow.first()
            inputBpDiaMin = settingsRepository.bpDiastolicInputMinFlow.first()
            inputBpDiaMax = settingsRepository.bpDiastolicInputMaxFlow.first()
            inputRrMin = settingsRepository.rrInputMinFlow.first()
            inputRrMax = settingsRepository.rrInputMaxFlow.first()
            inputBorgMin = settingsRepository.borgInputMinFlow.first()
            inputBorgMax = settingsRepository.borgInputMaxFlow.first()

            Log.i(TAG, "Rangos de entrada cargados desde SettingsRepository: " +
                    "SpO2 (${inputSpo2Min}-${inputSpo2Max}), HR (${inputHrMin}-${inputHrMax}), etc.")

            // Actualizar los hints de los rangos para la UI con los valores cargados
            _spo2RangeHint.value = "(${inputSpo2Min}-${inputSpo2Max})"
            _hrRangeHint.value = "(${inputHrMin}-${inputHrMax})"
            _bpRangeHint.value = "S(${inputBpSysMin}-${inputBpSysMax}), D(${inputBpDiaMin}-${inputBpDiaMax})"
            _rrRangeHint.value = "(${inputRrMin}-${inputRrMax})"
            _borgRangeHint.value = "(${inputBorgMin}-${inputBorgMax})"

            validateAllBasalInputs()
        }
        observeBluetoothServiceStateAndData()
        setupCanStartTestManualListeners()

        viewModelScope.launch {
            checkBleRequirementsAndReadyState()
        }
    }

    fun initialize(patientIdArg: String, patientNameArg: String, patientHasHistoryFromNav: Boolean) {
        val alreadyInitialized = _patientId.value == patientIdArg &&
                _patientFullName.value == patientNameArg &&
                _patientHasPreviousHistory.value == patientHasHistoryFromNav &&
                _internalPatientDetails?.id == patientIdArg

        if (alreadyInitialized) {
            Log.d("InitializeVM", "ViewModel ya inicializado con los mismos datos para $patientIdArg. Verificando BLE.")
            viewModelScope.launch {
                checkBleRequirementsAndReadyState()
                _combinedCanStartTestConditions.value = calculateCanStartTest()
            }
            return
        }

        _patientId.value = patientIdArg
        _patientFullName.value = patientNameArg
        _patientHasPreviousHistory.value = patientHasHistoryFromNav
        Log.d("InitializeVM", "Inicializando ViewModel para paciente ID: $patientIdArg, Nombre: $patientNameArg, TieneHistoria (desde Nav): $patientHasHistoryFromNav")

        viewModelScope.launch {
            var fetchedSex = ""
            var fetchedAgeString = ""
            var fetchedHeightCm = ""
            var fetchedWeightKg = ""
            var fetchedUsesInhalers = false
            var fetchedUsesOxygen = false

            if (patientHasHistoryFromNav) {
                Log.d("InitializeVM", "Paciente con historial indicado. Intentando cargar datos de la última prueba para $patientIdArg.")
                val lastTest: PruebaRealizada? = pacienteRepository.getPruebaMasRecienteParaPaciente(patientIdArg)

                if (lastTest?.datosCompletos?.summaryData != null) {
                    val summaryFromTest = lastTest.datosCompletos.summaryData
                    Log.i("InitializeVM", "Datos del sumario de la última prueba encontrados (Prueba ID: ${lastTest.pruebaId}): $summaryFromTest")

                    fetchedSex = summaryFromTest.patientSex
                    val ageAtLastTest = summaryFromTest.patientAge
                    fetchedHeightCm = if (summaryFromTest.patientHeightCm > 0) summaryFromTest.patientHeightCm.toString() else ""
                    fetchedWeightKg = if (summaryFromTest.patientWeightKg > 0) summaryFromTest.patientWeightKg.toString() else ""
                    fetchedUsesInhalers = summaryFromTest.usesInhalers
                    fetchedUsesOxygen = summaryFromTest.usesOxygen

                    // Calcular edad actual
                    if (ageAtLastTest > 0 && lastTest.fechaTimestamp > 0) {
                        val calendarPrueba = Calendar.getInstance().apply { timeInMillis = lastTest.fechaTimestamp }
                        val calendarActual = Calendar.getInstance()

                        var aniosDiferencia = calendarActual.get(Calendar.YEAR) - calendarPrueba.get(Calendar.YEAR)
                        // Si el día del año actual es menor que el día del año de la prueba,
                        // y hay al menos un año de diferencia, restar un año porque aún no ha "cumplido años" ese año.
                        if (calendarActual.get(Calendar.DAY_OF_YEAR) < calendarPrueba.get(Calendar.DAY_OF_YEAR) && aniosDiferencia > 0) {
                            aniosDiferencia--
                        }
                        val edadActual = ageAtLastTest + aniosDiferencia
                        fetchedAgeString = if (edadActual > 0) edadActual.toString() else ""
                        Log.d("InitializeVM", "Edad en prueba: $ageAtLastTest, Fecha prueba: ${calendarPrueba.time}, Edad actual calculada: $edadActual")
                    } else {
                        fetchedAgeString = if (ageAtLastTest > 0) ageAtLastTest.toString() else "" // Fallback
                        Log.d("InitializeVM", "Usando edad de la prueba ($ageAtLastTest) como fallback o porque no se pudo calcular la actual.")
                    }

                } else {
                    Log.w("InitializeVM", "Paciente con historial indicado, pero no se encontraron datos válidos (summaryData null o prueba null) para $patientIdArg. Se usarán campos vacíos/default.")
                }
            } else {
                Log.d("InitializeVM", "Paciente sin historial indicado desde la navegación, o se están introduciendo datos nuevos. Campos permanecerán vacíos/default.")
            }

            // Actualizar los StateFlows de la UI y _internalPatientDetails
            _patientSex.value = fetchedSex
            _patientAge.value = fetchedAgeString
            _patientHeightCm.value = fetchedHeightCm
            _patientWeightKg.value = fetchedWeightKg
            _usesInhalers.value = fetchedUsesInhalers
            _usesOxygen.value = fetchedUsesOxygen

            _internalPatientDetails = PatientDetails(
                id = patientIdArg,
                fullName = patientNameArg, // Este viene de la navegación
                sex = fetchedSex,
                age = fetchedAgeString.toIntOrNull() ?: 0,
                heightCm = fetchedHeightCm.toIntOrNull() ?: 0,
                weightKg = fetchedWeightKg.toIntOrNull() ?: 0,
                usesInhalers = fetchedUsesInhalers,
                usesOxygen = fetchedUsesOxygen
            )

            // Estas llamadas son cruciales y deben estar después de actualizar _internalPatientDetails
            calculateTheoreticalDistance()
            validateAllBasalInputs()
            checkBleRequirementsAndReadyState()
            _combinedCanStartTestConditions.value = calculateCanStartTest()
        }
    }

    private fun observeBluetoothServiceStateAndData() {
        // Observar el estado de conexión para mensajes y canStartTest
        bluetoothService.connectionStatus
            .onEach { status ->
                Log.d(TAG, "ConnectionStatus del servicio cambió a: $status")
                if (status == BleConnectionStatus.DISCONNECTED_BY_USER || status == BleConnectionStatus.DISCONNECTED_ERROR) {
                    clearBleDataStates()
                }
                checkBleRequirementsAndReadyState()
            }
            .launchIn(viewModelScope)

        // Observar isScanning para mensajes
        bluetoothService.isScanning
            .onEach { scanning ->
                Log.d(TAG, "isScanning del servicio cambió a: $scanning")
                checkBleRequirementsAndReadyState()
            }
            .launchIn(viewModelScope)

        // Observar los mensajes de error/informativos del servicio
        bluetoothService.errorMessages
            .onEach { message ->
                Log.d(TAG, "Mensaje de error/info del BluetoothService: $message")
                _lastServiceErrorMessage.value = message
                if (! (message.contains("permisos", ignoreCase = true) ||
                    message.contains("Bluetooth desactivado", ignoreCase = true) ||
                    message.contains("ubicación", ignoreCase = true)
                )) {
                    if (_isBleReady.value && !message.contains("GATT", ignoreCase = true) && !message.contains("error", ignoreCase = true) ) {
                        _uiBluetoothMessage.value = message
                    }
                }
                checkBleRequirementsAndReadyState()
            }
            .launchIn(viewModelScope)

        // Observar los datos BLE del servicio
        bluetoothService.bleDeviceData
            .onEach { newData ->
                Log.d(TAG, "Nuevos datos BLE del servicio: SpO2=${newData.spo2}, HR=${newData.heartRate}, NoFinger=${newData.noFingerDetected}, Signal=${newData.signalStrength}")
                _latestBleSpo2.value = newData.spo2
                _latestBleHeartRate.value = newData.heartRate
                _latestBleSignalStrength.value = newData.signalStrength
                _latestBleNoFinger.value = newData.noFingerDetected
                _latestBlePleth.value = newData.plethValue
                _latestBleBarGraph.value = newData.barGraphValue
                _combinedCanStartTestConditions.value = calculateCanStartTest()
            }
            .launchIn(viewModelScope)

        // Observar el dispositivo conectado para actualizar mensajes
        bluetoothService.connectedDevice
            .onEach { _ ->
                checkBleRequirementsAndReadyState()
            }
            .launchIn(viewModelScope)
    }

    // Función central para actualizar _isBleReady y _uiBluetoothMessage
    private fun checkBleRequirementsAndReadyState() {
        val context = application.applicationContext
        val isBtAdapterEnabled = bluetoothService.isBluetoothEnabled()
        val hasBtPermissions = getRequiredBluetoothPermissions(context).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        val hasLocationPermission = ContextCompat.checkSelfPermission(context,
            ACCESS_FINE_LOCATION_STRING) == PackageManager.PERMISSION_GRANTED
        val areLocationServicesEnabled = bluetoothService.isLocationEnabled() // Verifica el servicio de ubicación del dispositivo

        var currentUiMessage = ""
        var bleIsFullyReady = false

        when {
            !isBtAdapterEnabled ->
                currentUiMessage = "Bluetooth desactivado. Actívalo."
            !hasBtPermissions -> {
                val missingBtPerms = getRequiredBluetoothPermissions(context).filterNot {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }.joinToString { it.substringAfterLast('.') }
                currentUiMessage = "Faltan permisos de Bluetooth: $missingBtPerms."
            }
            !hasLocationPermission ->
                currentUiMessage = "Falta permiso de Ubicación."
            !areLocationServicesEnabled ->
                currentUiMessage = "Servicios de Ubicación desactivados. Actívalos."
            else -> {
                // Todos los requisitos previos están OK. Ahora el mensaje depende del estado de conexión/escaneo.
                bleIsFullyReady = true
                val currentStatus = connectionStatus.value
                val currentIsScanning = isScanning.value
                val currentConnectedDeviceName = connectedDeviceName.value ?: "el dispositivo"

                currentUiMessage = when {
                    currentIsScanning -> "Escaneando dispositivos..."
                    currentStatus == BleConnectionStatus.IDLE -> "Listo para escanear."
                    currentStatus == BleConnectionStatus.CONNECTING -> "Conectando a $currentConnectedDeviceName..."
                    currentStatus == BleConnectionStatus.RECONNECTING -> "Reconectando a $currentConnectedDeviceName..."
                    currentStatus == BleConnectionStatus.CONNECTED -> "Conectado a $currentConnectedDeviceName. Configurando..."
                    currentStatus == BleConnectionStatus.SUBSCRIBED -> "$currentConnectedDeviceName listo. Recibiendo datos."
                    currentStatus == BleConnectionStatus.DISCONNECTED_BY_USER -> "Desconectado."
                    currentStatus.name.startsWith("ERROR_") -> _lastServiceErrorMessage.value ?: "Error de conexión con $currentConnectedDeviceName." // Usa el último error del servicio
                    currentStatus == BleConnectionStatus.DISCONNECTED_ERROR -> _lastServiceErrorMessage.value ?: "Se perdió la conexión con $currentConnectedDeviceName."
                    else -> "Estado: ${currentStatus.name}"
                }
            }
        }

        if (_isBleReady.value != bleIsFullyReady) {
            _isBleReady.value = bleIsFullyReady
            Log.d(TAG, "_isBleReady actualizado a: $bleIsFullyReady")
        }
        if (_uiBluetoothMessage.value != currentUiMessage) {
            _uiBluetoothMessage.value = currentUiMessage
            Log.d(TAG, "Mensaje UI Bluetooth actualizado a: '$currentUiMessage'")
        }
        // Recalcular canStartTest porque isBleReady puede haber cambiado
        _combinedCanStartTestConditions.value = calculateCanStartTest()
    }

    fun startBleProcessOrRequestPermissions() {
        Log.d(TAG, "startBleProcessOrRequestPermissions: Iniciando verificación secuencial...")
        val context = application.applicationContext

        // 1. Verificar si el adaptador Bluetooth está encendido
        if (!bluetoothService.isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth desactivado. Emitiendo _requestEnableBluetoothEvent.")
            _uiBluetoothMessage.value = "Bluetooth desactivado. Pulsa para activar." // Mensaje más accionable
            viewModelScope.launch { _requestEnableBluetoothEvent.emit(Unit) }
            checkBleRequirementsAndReadyState() // Actualizar isBleReady y mensaje general
            return
        }
        Log.d(TAG, "Paso 1: Bluetooth está ACTIVADO.")

        // 2. Verificar Permisos de Bluetooth (BLUETOOTH_SCAN, BLUETOOTH_CONNECT en S+, o equivalentes + FINE_LOCATION en <S)
        val requiredBtPerms = getRequiredBluetoothPermissions(context)
        val missingBtPerms = requiredBtPerms.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingBtPerms.isNotEmpty()) {
            val missingPermsString = missingBtPerms.joinToString { it.substringAfterLast('.') }
            Log.w(TAG, "Faltan permisos de Bluetooth: [$missingPermsString]. Emitiendo _requestPermissionsEvent.")
            _uiBluetoothMessage.value = "Faltan permisos Bluetooth: $missingPermsString. Pulsa para conceder."
            viewModelScope.launch { _requestPermissionsEvent.emit(missingBtPerms.toTypedArray()) }
            checkBleRequirementsAndReadyState()
            return
        }
        Log.d(TAG, "Paso 2: Permisos de Bluetooth CONCEDIDOS.")

        // 3. Verificar Permiso de Ubicación Fina (ACCESS_FINE_LOCATION)
        // Este es requerido para el escaneo en versiones < S (ya incluido en requiredBtPerms)
        // y puede ser necesario en S+ si no se usa usesPermissionFlags="neverForLocation"
        // o si la app necesita la ubicación por otras razones. Lo verificamos explícitamente.
        if (ContextCompat.checkSelfPermission(context, ACCESS_FINE_LOCATION_STRING) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Falta permiso ACCESS_FINE_LOCATION. Emitiendo _requestPermissionsEvent.")
            _uiBluetoothMessage.value = "Falta permiso de Ubicación. Pulsa para conceder."
            viewModelScope.launch { _requestPermissionsEvent.emit(arrayOf(ACCESS_FINE_LOCATION_STRING)) }
            checkBleRequirementsAndReadyState()
            return
        }
        Log.d(TAG, "Paso 3: Permiso de Ubicación (ACCESS_FINE_LOCATION) CONCEDIDO.")

        // 4. Verificar si los servicios de Ubicación del dispositivo están activos
        if (!bluetoothService.isLocationEnabled()) {
            Log.w(TAG, "Servicios de ubicación del dispositivo desactivados. Emitiendo _requestLocationServicesEvent.")
            _uiBluetoothMessage.value = "Servicios de Ubicación desactivados. Pulsa para activar."
            viewModelScope.launch { _requestLocationServicesEvent.emit(Unit) }
            checkBleRequirementsAndReadyState()
            return
        }
        Log.d(TAG, "Paso 4: Servicios de Ubicación del dispositivo ACTIVADOS.")

        // Si llegamos aquí, todos los requisitos están cumplidos. Proceder a escanear.
        Log.i(TAG, "Todos los requisitos BLE están OK. Intentando iniciar escaneo.")
        _isBleReady.value = true // Confirmar que todo está listo
        val currentConnStatus = connectionStatus.value

        if (currentConnStatus == BleConnectionStatus.IDLE ||
            currentConnStatus.name.startsWith("DISCONNECTED_") ||
            currentConnStatus.name.startsWith("ERROR_") ||
            currentConnStatus == BleConnectionStatus.SCANNING // Permitir reiniciar si ya está escaneando
        ) {
            if (currentConnStatus != BleConnectionStatus.SCANNING) {
                bluetoothService.clearScannedDevices()
                Log.d(TAG, "Lista de dispositivos limpiada antes de nuevo escaneo.")
            }
            bluetoothService.startScan()
        } else {
            Log.d(TAG, "No se inicia escaneo. Estado actual: $currentConnStatus no es IDLE, SCANNING, DISCONNECTED o ERROR.")
        }
        checkBleRequirementsAndReadyState() // Actualizará el mensaje a "Escaneando..." o similar
    }

    fun onPermissionsResult(grantedPermissionsMap: Map<String, Boolean>) {
        Log.d(TAG, "Resultado de permisos recibido: $grantedPermissionsMap")
        val allGranted = grantedPermissionsMap.values.all { it }
        if (allGranted) {
            Log.d(TAG, "Todos los permisos solicitados fueron concedidos. Continuando proceso...")
            startBleProcessOrRequestPermissions()
        } else {
            Log.w(TAG, "Algunos permisos fueron denegados. Actualizando UI.")
            checkBleRequirementsAndReadyState()
        }
    }

    fun onBluetoothEnabledResult(isEnabled: Boolean) {
        Log.d(TAG, "Resultado de activación de Bluetooth: $isEnabled")
        if (isEnabled) {
            Log.d(TAG, "Bluetooth activado por el usuario. Continuando proceso.")
            startBleProcessOrRequestPermissions()
        } else {
            Log.w(TAG, "Usuario no activó Bluetooth.")
            checkBleRequirementsAndReadyState()
        }
    }

    fun onLocationServicesEnabledResult(areEnabled: Boolean) {
        Log.d(TAG, "Usuario regresó de ajustes de ubicación. Re-verificando estado.")
        startBleProcessOrRequestPermissions()
    }


    fun stopBleScan() {
        Log.i(TAG, "Solicitando parada de escaneo BLE al servicio.")
        bluetoothService.stopScan()
    }

    fun connectToScannedDevice(device: ServiceUiScannedDevice) {
        Log.d(TAG, "VM: Solicitando conexión a ${device.deviceName ?: device.address}.")
        if (!_isBleReady.value) { // _isBleReady ahora significa que TODOS los requisitos están OK
            Log.w(TAG, "Intento de conexión pero _isBleReady es false. Iniciando proceso de verificación.")
            startBleProcessOrRequestPermissions() // Guía al usuario a través de los pasos faltantes
            return
        }
        bluetoothService.connect(device.address)
    }

    fun disconnectBluetooth() {
        Log.d(TAG, "disconnectBluetooth() llamado en ViewModel.")
        bluetoothService.disconnect()
    }

    private fun clearBleDataStates() {
        Log.d("StateClear", "Limpiando estados de datos BLE en ViewModel.")
        _latestBleSpo2.value = null
        _latestBleHeartRate.value = null
        _latestBleSignalStrength.value = null
        _latestBleNoFinger.value = true
        _latestBlePleth.value = null
        _latestBleBarGraph.value = null
    }

    fun captureBasalFromBle() {
        Log.d("CaptureBasal", "Intentando capturar SpO2 y FC desde BLE. SpO2: ${_latestBleSpo2.value}, HR: ${_latestBleHeartRate.value}, NoFinger: ${_latestBleNoFinger.value}")

        val currentBleReady = _isBleReady.value
        val currentStatus = connectionStatus.value

        if (!currentBleReady) {
            _uiBluetoothMessage.value = "Dispositivo no preparado. Verifica Bluetooth, permisos y ubicación."
            Log.w("CaptureBasal", "Intento de captura fallido. isBleReady (comprensivo): $currentBleReady")
            startBleProcessOrRequestPermissions() // Intentar arreglar estado si no está listo
            return
        }

        if (currentStatus != BleConnectionStatus.SUBSCRIBED) {
            _uiBluetoothMessage.value = "Dispositivo no suscrito o conexión perdida. No se pueden capturar datos."
            Log.w("CaptureBasal", "Intento de captura fallido. Status: $currentStatus (no SUBSCRIBED)")
            return
        }


        if (_latestBleNoFinger.value == true) {
            _uiBluetoothMessage.value = "No se detecta el dedo en el sensor."
            Log.w("CaptureBasal", "Intento de captura con 'No Finger' activo.")
            return
        }

        val spo2ToCapture = _latestBleSpo2.value
        val hrToCapture = _latestBleHeartRate.value
        var spo2Captured = false
        var hrCaptured = false

        if (spo2ToCapture != null && spo2ToCapture in inputSpo2Min..inputSpo2Max) {
            _spo2Input.value = spo2ToCapture.toString()
            onSpo2InputChange(spo2ToCapture.toString())
            spo2Captured = true
        } else {
            Log.w("CaptureBasal", "SpO2 inválido o nulo desde BLE: $spo2ToCapture")
        }

        if (hrToCapture != null && hrToCapture in inputHrMin..inputHrMax) {
            _heartRateInput.value = hrToCapture.toString()
            onHeartRateInputChange(hrToCapture.toString())
            hrCaptured = true
        } else {
            Log.w("CaptureBasal", "HR inválido ($hrToCapture) o nulo desde BLE. Rango ($inputHrMin-$inputHrMax)")
        }

        if (spo2Captured && hrCaptured) {
            _uiBluetoothMessage.value = "SpO2 y FC capturados desde el dispositivo."
        } else if (spo2Captured) {
            _uiBluetoothMessage.value = "SpO2 capturado. FC del sensor no es válido/disponible."
        } else if (hrCaptured) {
            _uiBluetoothMessage.value = "FC capturada. SpO2 del sensor no es válido/disponible."
        } else {
            _uiBluetoothMessage.value = "Datos de SpO2 y FC del sensor no son válidos o no están disponibles."
        }
    }

    fun onSpo2InputChange(newValue: String) {
        _spo2Input.value = newValue.filter { it.isDigit() }
        validateSpo2()
        validateAllBasalInputs()
    }

    private fun validateSpo2() {
        val spo2 = _spo2Input.value.toIntOrNull()
        _isValidSpo2.value = spo2 != null && spo2 in inputSpo2Min..inputSpo2Max
    }

    fun onHeartRateInputChange(newValue: String) {
        _heartRateInput.value = newValue.filter { it.isDigit() }
        validateHeartRate()
        validateAllBasalInputs()
    }

    private fun validateHeartRate() {
        val hr = _heartRateInput.value.toIntOrNull()
        _isValidHeartRate.value = hr != null && hr in inputHrMin..inputHrMax
    }

    fun onBloodPressureInputChange(newValue: String) {
        _bloodPressureInput.value = newValue
        validateBloodPressure()
        validateAllBasalInputs()
    }

    private fun validateBloodPressure() {
        val input = _bloodPressureInput.value
        if (input.isBlank()) {
            _isValidBloodPressure.value = false
            return
        }
        val parts = input.split("/")
        if (parts.size == 2) {
            val systolic = parts[0].trim().toIntOrNull()
            val diastolic = parts[1].trim().toIntOrNull()
            _isValidBloodPressure.value = systolic != null && diastolic != null &&
                    systolic in inputBpSysMin..inputBpSysMax &&
                    diastolic in inputBpDiaMin..inputBpDiaMax &&
                    systolic > diastolic
        } else {
            _isValidBloodPressure.value = false
        }
    }

    fun onRespiratoryRateInputChange(newValue: String) {
        _respiratoryRateInput.value = newValue.filter { it.isDigit() }
        validateRespiratoryRate()
        validateAllBasalInputs()
    }

    private fun validateRespiratoryRate() {
        val rr = _respiratoryRateInput.value.toIntOrNull()
        _isValidRespiratoryRate.value = rr != null && rr in inputRrMin..inputRrMax
    }

    fun onDyspneaBorgInputChange(newValue: String) {
        _dyspneaBorgInput.value = newValue.filter { it.isDigit() }
        validateDyspneaBorg()
        validateAllBasalInputs()
    }

    private fun validateDyspneaBorg() {
        val borg = _dyspneaBorgInput.value.toIntOrNull()
        _isValidDyspneaBorg.value = borg != null && borg in inputBorgMin..inputBorgMax
    }


    fun onLegPainBorgInputChange(newValue: String) {
        _legPainBorgInput.value = newValue.filter { it.isDigit() }
        validateLegPainBorg()
        validateAllBasalInputs()
    }

    private fun validateLegPainBorg() {
        val borg = _legPainBorgInput.value.toIntOrNull()
        _isValidLegPainBorg.value = borg != null && borg in inputBorgMin..inputBorgMax
    }

    private fun validateAllBasalInputs() {
        // Se llaman primero las validaciones individuales
        validateSpo2()
        validateHeartRate()
        validateBloodPressure() // Asegúrate que esta función maneje bien el caso de input vacío para _isValidBloodPressure
        validateRespiratoryRate()
        validateDyspneaBorg()
        validateLegPainBorg()

        // Comprobación de que todos los campos estén llenos Y válidos
        val allRequiredValidAndFilled =
            _spo2Input.value.isNotBlank() && _isValidSpo2.value &&
                    _heartRateInput.value.isNotBlank() && _isValidHeartRate.value &&
                    _bloodPressureInput.value.isNotBlank() && _isValidBloodPressure.value && // Aquí, si BP está vacío, isValidBP puede ser true, pero isNotBlank será false.
                    _respiratoryRateInput.value.isNotBlank() && _isValidRespiratoryRate.value &&
                    _dyspneaBorgInput.value.isNotBlank() && _isValidDyspneaBorg.value &&
                    _legPainBorgInput.value.isNotBlank() && _isValidLegPainBorg.value

        _areBasalsValid.value = allRequiredValidAndFilled

        if (_areBasalsValid.value) {
            _basalValuesStatusMessage.value = "Todos los valores basales son válidos."
        } else {
            val errors = mutableListOf<String>()
            // Mensajes de error usan las variables miembro de los rangos
            if (_spo2Input.value.isNotBlank() && !_isValidSpo2.value) {
                errors.add("SpO2 ($inputSpo2Min-$inputSpo2Max)")
            }
            if (_heartRateInput.value.isNotBlank() && !_isValidHeartRate.value) {
                errors.add("FC ($inputHrMin-$inputHrMax)")
            }
            if (_bloodPressureInput.value.isNotBlank() && !_isValidBloodPressure.value) {
                errors.add("TA (S: $inputBpSysMin-$inputBpSysMax, D: $inputBpDiaMin-$inputBpDiaMax, S>D)")
            }
            if (_respiratoryRateInput.value.isNotBlank() && !_isValidRespiratoryRate.value) {
                errors.add("FR ($inputRrMin-$inputRrMax)")
            }
            if (_dyspneaBorgInput.value.isNotBlank() && !_isValidDyspneaBorg.value) {
                errors.add("Disnea ($inputBorgMin-$inputBorgMax)")
            }
            if (_legPainBorgInput.value.isNotBlank() && !_isValidLegPainBorg.value) {
                errors.add("Dolor Piernas ($inputBorgMin-$inputBorgMax)")
            }

            val missingFields = mutableListOf<String>()
            if (_spo2Input.value.isBlank()) missingFields.add("SpO2")
            if (_heartRateInput.value.isBlank()) missingFields.add("FC")
            if (_bloodPressureInput.value.isBlank()) missingFields.add("TA")
            if (_respiratoryRateInput.value.isBlank()) missingFields.add("FR")
            if (_dyspneaBorgInput.value.isBlank()) missingFields.add("Disnea")
            if (_legPainBorgInput.value.isBlank()) missingFields.add("Dolor Piernas")

            var message = ""
            if (missingFields.isNotEmpty()) {
                message = "Complete: ${missingFields.joinToString(", ")}."
                if (errors.isNotEmpty()) {
                    message += " Inválidos: ${errors.joinToString(", ")}."
                }
            } else if (errors.isNotEmpty()) {
                message = "Valores inválidos en: ${errors.joinToString(", ")}."
            } else {
                message = "Complete todos los campos basales." // Mensaje genérico si no hay errores específicos ni faltantes pero _areBasalsValid es false
            }
            _basalValuesStatusMessage.value = message
        }
        _combinedCanStartTestConditions.value = calculateCanStartTest() // Recalcular
    }

    // --- Funciones de Gestión de Estado de UI Adicional ---
    fun onPatientSexChange(newSex: String) {
        _patientSex.value = newSex
        _internalPatientDetails = _internalPatientDetails?.copy(sex = newSex)
            ?: PatientDetails(id = _patientId.value ?: "", fullName = _patientFullName.value, sex = newSex, age = 0, heightCm = 0, weightKg = 0, usesInhalers = false, usesOxygen = false)
        calculateTheoreticalDistance()
        _combinedCanStartTestConditions.value = calculateCanStartTest()
    }

    fun onPatientAgeChange(newAgeString: String) {
        val newAgeFiltered = newAgeString.filter { it.isDigit() }
        _patientAge.value = newAgeFiltered
        val newAgeInt = newAgeFiltered.toIntOrNull() ?: 0

        _internalPatientDetails = _internalPatientDetails?.copy(age = newAgeInt)
            ?: PatientDetails(id = _patientId.value ?: "", fullName = _patientFullName.value, sex = _patientSex.value, age = newAgeInt ?: 0, heightCm = 0, weightKg = 0, usesInhalers = false, usesOxygen = false)
        calculateTheoreticalDistance()
        _combinedCanStartTestConditions.value = calculateCanStartTest()
    }

    fun onPatientHeightChange(newHeightString: String) {
        val newHeightFiltered = newHeightString.filter { it.isDigit() }
        _patientHeightCm.value = newHeightFiltered
        val newHeightInt = newHeightFiltered.toIntOrNull() ?: 0

        _internalPatientDetails = _internalPatientDetails?.copy(heightCm = newHeightInt)
            ?: PatientDetails(id = _patientId.value ?: "", fullName = _patientFullName.value, sex = _patientSex.value, age = _internalPatientDetails?.age ?: 0, heightCm = newHeightInt ?: 0, weightKg = 0, usesInhalers = false, usesOxygen = false)
        calculateTheoreticalDistance()
        _combinedCanStartTestConditions.value = calculateCanStartTest()
    }


    fun onPatientWeightChange(newWeightString: String) {
        val newWeightFiltered = newWeightString.filter { it.isDigit() }
        _patientWeightKg.value = newWeightFiltered
        val newWeightInt = newWeightFiltered.toIntOrNull() ?: 0

        _internalPatientDetails = _internalPatientDetails?.copy(weightKg = newWeightInt)
            ?: PatientDetails(id = _patientId.value ?: "", fullName = _patientFullName.value, sex = _patientSex.value, age = _internalPatientDetails?.age ?: 0, heightCm = _internalPatientDetails?.heightCm ?: 0, weightKg = newWeightInt ?: 0, usesInhalers = false, usesOxygen = false)
        _combinedCanStartTestConditions.value = calculateCanStartTest()
        calculateTheoreticalDistance()
    }

    fun onUsesInhalersChange(newValue: Boolean) {
        _usesInhalers.value = newValue
        _internalPatientDetails = _internalPatientDetails?.copy(usesInhalers = newValue)
            ?: PatientDetails(id = _patientId.value ?: "", fullName = _patientFullName.value, sex = _patientSex.value, age = _internalPatientDetails?.age ?: 0, heightCm = _internalPatientDetails?.heightCm ?: 0, weightKg = _internalPatientDetails?.weightKg ?: 0, usesInhalers = newValue, usesOxygen = _usesOxygen.value)
    }

    fun onUsesOxygenChange(newValue: Boolean) {
        _usesOxygen.value = newValue
        _internalPatientDetails = _internalPatientDetails?.copy(usesOxygen = newValue)
            ?: PatientDetails(id = _patientId.value ?: "", fullName = _patientFullName.value, sex = _patientSex.value, age = _internalPatientDetails?.age ?: 0, heightCm = _internalPatientDetails?.heightCm ?: 0, weightKg = _internalPatientDetails?.weightKg ?: 0, usesInhalers = _usesInhalers.value, usesOxygen = newValue)
    }

    private fun calculateTheoreticalDistance() {
        val details = _internalPatientDetails
        if (details == null) {
            Log.d("CalcDist", "_internalPatientDetails es null. Distancia a 0.")
            _theoreticalDistance.value = 0.0
            return
        }

        val sex = details.sex
        val age = details.age
        val heightCm = details.heightCm
        val weightKg = details.weightKg
        Log.d("CalcDist", "Calculando con Sex: '$sex', Age: $age, Height: $heightCm, Weight: $weightKg")

        if (age <= 0 || heightCm <= 0 || weightKg <= 0 || sex.isBlank()) {
            _theoreticalDistance.value = 0.0
            Log.d("CalcDist", "Uno o más valores (age, height, weight, sex) no son válidos para calcular. Distancia a 0.")
            return
        }
        val distance = when (sex) {
            "M" -> (7.57 * heightCm) - (5.02 * age) - (1.76 * weightKg) - 309
            "F" -> (2.11 * heightCm) - (5.78 * age) - (2.29 * weightKg) + 667
            else -> {
                Log.w("CalcDist", "Sexo '$sex' no reconocido para fórmula. Distancia a 0.")
                0.0
            }
        }
        _theoreticalDistance.value = if (distance > 0) distance else 0.0
        Log.d("CalcDist", "Distancia calculada: ${_theoreticalDistance.value}")
    }

    fun onDevicePlacedToggle(isNowPlaced: Boolean) {
        _isDevicePlaced.value = isNowPlaced
        if (!isNowPlaced) {
            _devicePlacementLocation.value = DevicePlacementLocation.NONE
        }
        _combinedCanStartTestConditions.value = calculateCanStartTest()
    }

    fun onDevicePlacementLocationSelected(location: DevicePlacementLocation) {
        _devicePlacementLocation.value = location
        _isDevicePlaced.value = location != DevicePlacementLocation.NONE
        _combinedCanStartTestConditions.value = calculateCanStartTest()
    }

    fun confirmNavigateBack() {
        Log.d(TAG, "confirmNavigateBack() llamado.")
        _showNavigateBackDialog.value = false
        val currentStatus = bluetoothService.connectionStatus.value
        if (currentStatus != BleConnectionStatus.IDLE &&
            currentStatus != BleConnectionStatus.DISCONNECTED_BY_USER &&
            currentStatus != BleConnectionStatus.DISCONNECTED_ERROR) {
            Log.d(TAG, "Desconectando Bluetooth al confirmar navegación hacia atrás. Estado: $currentStatus")
            disconnectBluetooth()
        }
        viewModelScope.launch {
            _navigateBackEvent.emit(Unit)
        }
    }

    fun cancelNavigateBack() {
        _showNavigateBackDialog.value = false
    }

    fun requestNavigateBack() {
        _showNavigateBackDialog.value = true
    }

    fun onStartTestClicked() {
        startBleProcessOrRequestPermissions()
        viewModelScope.launch {
            kotlinx.coroutines.delay(100) // Pequeño delay para asegurar que los estados se propaguen

            if (!canStartTest.value) {
                var errorMessage = "No se puede iniciar el test. Requisitos incompletos: "
                val missing = mutableListOf<String>()

                // Re-verificar condiciones aquí para dar feedback específico si startBleProcessOrRequestPermissions no fue suficiente
                // (ej. el usuario denegó algo que se pidió)
                if (!_isBleReady.value) missing.add(_uiBluetoothMessage.value ?: "Bluetooth/Permisos no listos") // Usar el mensaje ya generado
                else if (connectionStatus.value != BleConnectionStatus.SUBSCRIBED) missing.add("Dispositivo no suscrito")

                if (_latestBleNoFinger.value == true && connectionStatus.value == BleConnectionStatus.SUBSCRIBED) missing.add("Sensor no detecta el dedo")
                if (!_areBasalsValid.value) missing.add("Valores basales inválidos o incompletos (${_basalValuesStatusMessage.value})")
                if (!_isDevicePlaced.value || _devicePlacementLocation.value == DevicePlacementLocation.NONE) missing.add("Colocación del dispositivo no confirmada")

                val patientDetails = _internalPatientDetails
                if (patientDetails == null ||
                    patientDetails.id.isBlank() ||
                    patientDetails.fullName.isBlank() ||
                    patientDetails.sex.isBlank() ||
                    patientDetails.age <= 0 ||
                    patientDetails.heightCm <= 0 ||
                    patientDetails.weightKg <= 0) {
                    missing.add("Información del paciente incompleta")
                }

                if (missing.isNotEmpty()) {
                    errorMessage += missing.joinToString("; ")
                    // No sobreescribir _uiBluetoothMessage si ya tiene un mensaje de startBleProcessOrRequestPermissions
                    if (_isBleReady.value) { // Solo si los requisitos básicos de BLE estaban OK, mostramos otros errores
                        _uiBluetoothMessage.value = "No se puede iniciar: ${missing.firstOrNull()}"
                    }
                } else if (_isBleReady.value) { // Si _isBleReady es true pero canStartTest es false
                    _uiBluetoothMessage.value = "Verifique todos los campos y la conexión del dispositivo."
                }
                Log.w("StartTest", "Intento de iniciar test fallido. CanStartTest: ${canStartTest.value}. Detalles: $errorMessage. Mensaje UI: ${_uiBluetoothMessage.value}")
                return@launch
            }

            // Si llegamos aquí, canStartTest.value es true
            val patientDetails = _internalPatientDetails!! // Sabemos que no es null por canStartTest
            val basalSpo2 = _spo2Input.value.toIntOrNull()!!
            val basalHeartRate = _heartRateInput.value.toIntOrNull()!!
            val bpParts = _bloodPressureInput.value.split("/")
            val basalSystolic = bpParts.getOrNull(0)?.trim()?.toIntOrNull()!!
            val basalDiastolic = bpParts.getOrNull(1)?.trim()?.toIntOrNull()!!
            val basalRespiratoryRate = _respiratoryRateInput.value.toIntOrNull()!!
            val basalDyspneaBorg = _dyspneaBorgInput.value.toIntOrNull()!!
            val basalLegPainBorg = _legPainBorgInput.value.toIntOrNull()!!

            val preparationData = TestPreparationData(
                patientId = patientDetails.id,
                patientFullName = patientDetails.fullName,
                patientSex = patientDetails.sex,
                patientAge = patientDetails.age,
                patientHeightCm = patientDetails.heightCm,
                patientWeightKg = patientDetails.weightKg,
                usesInhalers = patientDetails.usesInhalers,
                usesOxygen = patientDetails.usesOxygen,
                theoreticalDistance = _theoreticalDistance.value,
                basalSpo2 = basalSpo2,
                basalHeartRate = basalHeartRate,
                basalBloodPressureSystolic = basalSystolic,
                basalBloodPressureDiastolic = basalDiastolic,
                basalRespiratoryRate = basalRespiratoryRate,
                basalDyspneaBorg = basalDyspneaBorg,
                basalLegPainBorg = basalLegPainBorg,
                devicePlacementLocation = _devicePlacementLocation.value.name,
                isFirstTestForPatient = !_patientHasPreviousHistory.value // Si no hay historial previo cargado
            )

            Log.i("StartTest", "Preparando para navegar a TestExecution con datos: $preparationData")
            _navigateToEvent.emit(PreparationNavigationEvent.ToTestExecution(preparationData))
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "PreparationViewModel onCleared.")
        val serviceStatus = bluetoothService.connectionStatus.value
        if (serviceStatus != BleConnectionStatus.IDLE &&
            serviceStatus != BleConnectionStatus.DISCONNECTED_BY_USER &&
            serviceStatus != BleConnectionStatus.DISCONNECTED_ERROR) {
            Log.d(TAG, "Solicitando desconexión al servicio desde onCleared. Estado servicio: $serviceStatus")
            bluetoothService.disconnect()
        } else if (bluetoothService.isScanning.value) {
            Log.d(TAG, "Solicitando parada de escaneo al servicio desde onCleared.")
            bluetoothService.stopScan()
        }
        Log.d(TAG, "PreparationViewModel onCleared finalizado.")
    }
}
