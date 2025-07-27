package com.example.app6mwt.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app6mwt.bluetooth.BleConnectionStatus
import com.example.app6mwt.bluetooth.BleDeviceData
import com.example.app6mwt.bluetooth.BluetoothService
import com.example.app6mwt.data.DefaultThresholdValues
import com.example.app6mwt.data.PacienteRepository
import com.example.app6mwt.data.SettingsRepository
import com.example.app6mwt.data.model.PruebaRealizada
import com.example.app6mwt.di.RecoveryData
import com.example.app6mwt.di.TestStateHolder
import com.example.app6mwt.ui.theme.SuccessGreenColor
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import com.example.app6mwt.ui.theme.*
import com.example.app6mwt.util.SixMinuteWalkTestPdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// --- Enums y Data Classes ---
enum class PostTestField {
    BLOOD_PRESSURE, RESPIRATORY_RATE, DYSPNEA_BORG, LEG_PAIN_BORG
}

enum class BluetoothIconStatus2(
    val icon: ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val isClickable: Boolean
) {
    GREEN(
        icon = Icons.Filled.BluetoothConnected,
        color = SuccessGreenColor,
        isClickable = false
    ),
    YELLOW(
        icon = Icons.Filled.Warning,
        color = WarningYellowColor,
        isClickable = true
    ),
    RED(
        icon = Icons.Filled.BluetoothDisabled,
        color = ErrorRedColor,
        isClickable = true
    ),
    CONNECTING(
        icon = Icons.AutoMirrored.Filled.BluetoothSearching,
        color = ConnectingBlueColor,
        isClickable = false
    ),
    GRAY(
        icon = Icons.Filled.Bluetooth,
        color = DisabledGrayColor,
        isClickable = true
    );
}

data class PruebaCompletaDetalles(
    val summaryData: TestExecutionSummaryData?,
    val postTestSpo2: Int?,
    val postTestHeartRate: Int?,
    val postTestSystolicBP: Int?,
    val postTestDiastolicBP: Int?,
    val postTestRespiratoryRate: Int?,
    val postTestDyspneaBorg: Int?,
    val postTestLegPainBorg: Int?,
    val observations: String?
)

// Estado de la UI que contiene toda la información que la pantalla necesita mostrar.
data class TestResultsUiState(
    val isLoading: Boolean = true,
    val patientId: String = "",
    val patientFullName: String = "",
    val testDate: Long = 0L,

    val summaryData: TestExecutionSummaryData? = null,

    val totalDistanceMeters: Float = 0f,
    val theoreticalDistanceMeters: Double = 0.0,
    val percentageOfTheoretical: Float = 0f,
    val minuteSnapshotsForTable: List<MinuteDataSnapshot> = emptyList(),
    val stopRecordsForTable: List<StopRecord> = emptyList(),
    val numberOfStops: Int = 0,
    val minSpo2ForDisplay: CriticalValueRecord? = null,
    val maxHeartRateForDisplay: CriticalValueRecord? = null,
    val minHeartRateForDisplay: CriticalValueRecord? = null,

    val basalSpo2: Int? = null,
    val basalHeartRate: Int? = null,
    val basalBloodPressureSystolic: Int? = null,
    val basalBloodPressureDiastolic: Int? = null,
    val basalBloodPressureFormatted: String = "",
    val basalRespiratoryRate: Int? = null,
    val basalDyspneaBorg: Int? = null,
    val basalLegPainBorg: Int? = null,

    val postTestBloodPressureInput: String = "",
    val postTestRespiratoryRateInput: String = "",
    val postTestDyspneaBorgInput: String = "",
    val postTestLegPainBorgInput: String = "",
    val postTestSystolicBP: Int? = null,
    val postTestDiastolicBP: Int? = null,
    val postTestRespiratoryRate: Int? = null,
    val postTestDyspneaBorg: Int? = null,
    val postTestLegPainBorg: Int? = null,

    val isPostTestBloodPressureValid: Boolean = true,
    val isPostTestRespiratoryRateValid: Boolean = true,
    val isPostTestDyspneaBorgValid: Boolean = true,
    val isPostTestLegPainBorgValid: Boolean = true,
    val arePostTestValuesCompleteAndValid: Boolean = false,
    val validationMessage: String? = null,

    val recoverySpo2: Int? = null,
    val recoveryHeartRate: Int? = null,
    val isRecoveryPeriodOver: Boolean = false,
    val wasRecoveryDataCapturedInitially: Boolean = false,
    val isAwaitingPostTimeoutRecoveryData: Boolean = false,

    val observations: String = "",
    val showObservationsDialog: Boolean = false,

    val currentSpo2: Int? = null,
    val currentHeartRate: Int? = null,
    val spo2Trend: Trend = Trend.STABLE,
    val spo2AlarmStatus: StatusColor = StatusColor.UNKNOWN,
    val heartRateTrend: Trend = Trend.STABLE,
    val heartRateAlarmStatus: StatusColor = StatusColor.UNKNOWN,
    val isDeviceConnected: Boolean = false,
    val bluetoothVisualStatus: BluetoothIconStatus2 = BluetoothIconStatus2.GRAY,
    val bluetoothStatusMessage: String = "Iniciando...",
    val isAttemptingForceReconnect: Boolean = false,
    val criticalAlarmMessage: String? = null,

    val userMessage: String? = null,
    val shouldNavigateToHome: Boolean = false,
    val showExitConfirmationDialog: Boolean = false,

    val isGeneratingPdf: Boolean = false,
    val pdfGeneratedUri: Uri? = null,
    val pdfGenerationError: String? = null,

    val savedTestDatabaseId: Int? = null,
    val isTestSaved: Boolean = false,
    val savedTestNumeroPruebaPaciente: Int? = null,
    val hasUnsavedChanges: Boolean = false
)

// Patrón para validar "número/número"
private val BLOOD_PRESSURE_PATTERN = Regex("^\\d{2,3}\\s*/\\s*\\d{2,3}\$")


@HiltViewModel
class TestResultsViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context, // Para acceso a directorios de archivos
    private val savedStateHandle: SavedStateHandle,
    private val bluetoothService: BluetoothService,
    private val testStateHolder: TestStateHolder,
    private val pacienteRepository: PacienteRepository,
    private val gson: Gson,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TestResultsUiState())
    val uiState: StateFlow<TestResultsUiState> = _uiState.asStateFlow()

    private var userMessageClearJob: Job? = null
    private var recoveryDataJob: Job? = null
    private var liveSensorDataJob: Job? = null
    private var bluetoothStatusJob: Job? = null
    private var criticalAlarmMessageClearJob: Job? = null
    private var spo2ValuesSinceLastTrendCalc = mutableListOf<Int>()
    private var hrValuesSinceLastTrendCalc = mutableListOf<Int>()
    private var lastSensorUpdateTime = 0L
    private var internalRecoveryPeriodOver = false
    private var internalRecoveryDataCapturedDuringInitialMinute = false
    private var initialRecoverySpo2FromFlow: Int? = null
    private var initialRecoveryHrFromFlow: Int? = null

    // --- VARIABLES PARA LAS ALARMAS DESDE SETTINGS
    private var userSpo2WarningThreshold = DefaultThresholdValues.SPO2_WARNING_DEFAULT
    private var userSpo2CriticalThreshold = DefaultThresholdValues.SPO2_CRITICAL_DEFAULT
    private var userHrCriticalLowThreshold = DefaultThresholdValues.HR_CRITICAL_LOW_DEFAULT
    private var userHrWarningLowThreshold = DefaultThresholdValues.HR_WARNING_LOW_DEFAULT
    private var userHrWarningHighThreshold = DefaultThresholdValues.HR_WARNING_HIGH_DEFAULT
    private var userHrCriticalHighThreshold = DefaultThresholdValues.HR_CRITICAL_HIGH_DEFAULT

    // --- NUEVAS VARIABLES PARA RANGOS DE ENTRADA ---
    private var inputSpo2Min = DefaultThresholdValues.SPO2_INPUT_MIN_DEFAULT
    private var inputSpo2Max = DefaultThresholdValues.SPO2_INPUT_MAX_DEFAULT
    private var inputHrMin = DefaultThresholdValues.HR_INPUT_MIN_DEFAULT
    private var inputHrMax = DefaultThresholdValues.HR_INPUT_MAX_DEFAULT
    private var inputBpSystolicMin = DefaultThresholdValues.BP_SYSTOLIC_INPUT_MIN_DEFAULT
    private var inputBpSystolicMax = DefaultThresholdValues.BP_SYSTOLIC_INPUT_MAX_DEFAULT
    private var inputBpDiastolicMin = DefaultThresholdValues.BP_DIASTOLIC_INPUT_MIN_DEFAULT
    private var inputBpDiastolicMax = DefaultThresholdValues.BP_DIASTOLIC_INPUT_MAX_DEFAULT
    private var inputRrMin = DefaultThresholdValues.RR_INPUT_MIN_DEFAULT
    private var inputRrMax = DefaultThresholdValues.RR_INPUT_MAX_DEFAULT
    private var inputBorgMin = DefaultThresholdValues.BORG_INPUT_MIN_DEFAULT
    private var inputBorgMax = DefaultThresholdValues.BORG_INPUT_MAX_DEFAULT

    // --- NUEVOS STATES PÚBLICOS PARA LOS HINTS DE PLACEHOLDERS ---
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

    private val SENSOR_PROCESSING_INTERVAL_MS = 1000L
    private val spo2ValuesForNewTrend = mutableListOf<Int>()
    private val hrValuesForNewTrend = mutableListOf<Int>()

    init {
        Log.d("TestResultsVM", "ViewModel inicializado.")
        // --- Cargar Umbrales de Alarma ---
        viewModelScope.launch {
            userSpo2WarningThreshold = settingsRepository.spo2WarningThresholdFlow.first()
            userSpo2CriticalThreshold = settingsRepository.spo2CriticalThresholdFlow.first()
            userHrCriticalLowThreshold = settingsRepository.hrCriticalLowThresholdFlow.first()
            userHrWarningLowThreshold = settingsRepository.hrWarningLowThresholdFlow.first()
            userHrWarningHighThreshold = settingsRepository.hrWarningHighThresholdFlow.first()
            userHrCriticalHighThreshold = settingsRepository.hrCriticalHighThresholdFlow.first()

            Log.i("TestResultsVM", "Umbrales de SpO2/FC cargados: " +
                    "SpO2 Warn=$userSpo2WarningThreshold, SpO2 Crit=$userSpo2CriticalThreshold, " +
                    "HR CritLow=$userHrCriticalLowThreshold, HR WarnLow=$userHrWarningLowThreshold, " +
                    "HR WarnHigh=$userHrWarningHighThreshold, HR CritHigh=$userHrCriticalHighThreshold")

            // --- Cargar Rangos de Entrada ---
            inputSpo2Min = settingsRepository.spo2InputMinFlow.first()
            inputSpo2Max = settingsRepository.spo2InputMaxFlow.first()
            inputHrMin = settingsRepository.hrInputMinFlow.first()
            inputHrMax = settingsRepository.hrInputMaxFlow.first()
            inputBpSystolicMin = settingsRepository.bpSystolicInputMinFlow.first()
            inputBpSystolicMax = settingsRepository.bpSystolicInputMaxFlow.first()
            inputBpDiastolicMin = settingsRepository.bpDiastolicInputMinFlow.first()
            inputBpDiastolicMax = settingsRepository.bpDiastolicInputMaxFlow.first()
            inputRrMin = settingsRepository.rrInputMinFlow.first()
            inputRrMax = settingsRepository.rrInputMaxFlow.first()
            inputBorgMin = settingsRepository.borgInputMinFlow.first()
            inputBorgMax = settingsRepository.borgInputMaxFlow.first()

            // Actualizar los hints de los rangos para la UI con los valores cargados
            _spo2RangeHint.value = "(${inputSpo2Min}-${inputSpo2Max})"
            _hrRangeHint.value = "(${inputHrMin}-${inputHrMax})"
            _bpRangeHint.value = "S(${inputBpSystolicMin}-${inputBpSystolicMax}), D(${inputBpDiastolicMin}-${inputBpDiastolicMax})"
            _rrRangeHint.value = "(${inputRrMin}-${inputRrMax})"
            _borgRangeHint.value = "(${inputBorgMin}-${inputBorgMax})"

            Log.i("TestResultsVM", "Rangos de Entrada cargados: " +
                    "BP Sys Min=$inputBpSystolicMin, BP Sys Max=$inputBpSystolicMax, " +
                    "RR Min=$inputRrMin, RR Max=$inputRrMax, Borg Min=$inputBorgMin, Borg Max=$inputBorgMax"
            )
        }
        observeRecoveryData()
        loadInitialData()
        observeLiveSensorDataAndBluetoothStatus()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val patientIdArg: String? = savedStateHandle[AppDestinations.PATIENT_ID_ARG]
            val summaryDataJson: String? = savedStateHandle[AppDestinations.TEST_FINAL_DATA_ARG]

            if (patientIdArg == null) {
                _uiState.update {
                    it.copy(isLoading = false, userMessage = "Error: ID de paciente no encontrado.")
                }
                clearUserMessageAfterDelay()
                return@launch
            }

            var summary: TestExecutionSummaryData? = null
            if (summaryDataJson != null) {
                try {
                    summary = gson.fromJson(summaryDataJson, TestExecutionSummaryData::class.java)
                } catch (e: Exception) {
                    Log.e("TestResultsVM", "Error al deserializar JSON: ${e.message}")
                    _uiState.update {
                        it.copy(isLoading = false, patientId = patientIdArg, userMessage = "Error al cargar datos de la prueba.")
                    }
                    clearUserMessageAfterDelay()
                }
            } else {
                Log.w("TestResultsVM", "No se encontró TestExecutionSummaryData para paciente $patientIdArg.")
            }

            var patientFullNameFromRepo: String? = null
            try {
                val paciente = pacienteRepository.obtenerPacientePorId(patientIdArg)
                patientFullNameFromRepo = paciente?.nombre
            } catch (e: Exception) {
                Log.e("TestResultsVM", "Error al cargar datos del paciente desde el repositorio: ${e.message}")
            }

            if (summary != null) {
                val percentage = if (summary.theoreticalDistance > 0) {
                    (summary.distanceMetersFinal / summary.theoreticalDistance.toFloat()) * 100
                } else 0f
                val bpFormatted = if (summary.basalBloodPressureSystolic > 0 && summary.basalBloodPressureDiastolic > 0) {
                    "${summary.basalBloodPressureSystolic}/${summary.basalBloodPressureDiastolic} mmHg"
                } else ""

                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        patientId = summary.patientId,
                        patientFullName = summary.patientFullName,
                        testDate = summary.testActualStartTimeMillis,
                        summaryData = summary,
                        totalDistanceMeters = summary.distanceMetersFinal,
                        theoreticalDistanceMeters = summary.theoreticalDistance,
                        percentageOfTheoretical = percentage,
                        minuteSnapshotsForTable = summary.minuteReadings,
                        stopRecordsForTable = summary.stopRecords,
                        numberOfStops = summary.stopRecords.size,
                        minSpo2ForDisplay = summary.minSpo2Record,
                        maxHeartRateForDisplay = summary.maxHeartRateRecord,
                        minHeartRateForDisplay = summary.minHeartRateRecord,
                        basalSpo2 = summary.basalSpo2.takeIf { s -> s > 0 },
                        basalHeartRate = summary.basalHeartRate.takeIf { hr -> hr > 0 },
                        basalBloodPressureSystolic = summary.basalBloodPressureSystolic.takeIf { bp -> bp > 0 },
                        basalBloodPressureDiastolic = summary.basalBloodPressureDiastolic.takeIf { bp -> bp > 0 },
                        basalBloodPressureFormatted = bpFormatted,
                        basalRespiratoryRate = summary.basalRespiratoryRate.takeIf { rr -> rr > 0 },
                        basalDyspneaBorg = summary.basalDyspneaBorg.takeIf { b -> b >= 0 },
                        basalLegPainBorg = summary.basalLegPainBorg.takeIf { b -> b >= 0 },
                        userMessage = null,
                        recoverySpo2 = null,
                        recoveryHeartRate = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        patientId = patientIdArg,
                        patientFullName = patientFullNameFromRepo ?: "Paciente desconocido",
                        testDate = System.currentTimeMillis(),
                        summaryData = null,
                        userMessage = if (summaryDataJson != null) "Error al leer datos de la prueba." else "No hay datos de prueba previa. Ingrese valores post-prueba."
                    )
                }
                if (summaryDataJson == null) clearUserMessageAfterDelay(4000)
            }
            validateAllPostTestFields()
        }
    }

    fun observeRecoveryData() {
        recoveryDataJob?.cancel()
        recoveryDataJob = viewModelScope.launch {
            testStateHolder.recoveryDataFlow.collectLatest { recoveryData: RecoveryData ->
                Log.d(
                    "TestResultsVM",
                    "RecoveryDataFlow rcvd: SpO2=${recoveryData.spo2}, HR=${recoveryData.hr}, PeriodOver=${recoveryData.isRecoveryPeriodOver}, Captured=${recoveryData.wasDataCapturedDuringPeriod}"
                )

                internalRecoveryPeriodOver = recoveryData.isRecoveryPeriodOver
                internalRecoveryDataCapturedDuringInitialMinute = recoveryData.wasDataCapturedDuringPeriod
                initialRecoverySpo2FromFlow = recoveryData.spo2?.takeIf { it > 0 }
                initialRecoveryHrFromFlow = recoveryData.hr?.takeIf { it > 0 }

                var newAwaitingPostTimeout = _uiState.value.isAwaitingPostTimeoutRecoveryData
                var finalRecoverySpo2: Int? = _uiState.value.recoverySpo2
                var finalRecoveryHr: Int? = _uiState.value.recoveryHeartRate

                if (internalRecoveryPeriodOver) {
                    // El minuto ha terminado.
                    if (internalRecoveryDataCapturedDuringInitialMinute && initialRecoverySpo2FromFlow != null && initialRecoveryHrFromFlow != null) {
                        // Se capturaron datos válidos EXACTAMENTE al final del minuto por el RecoveryDataFlow. Úsalos.
                        finalRecoverySpo2 = initialRecoverySpo2FromFlow
                        finalRecoveryHr = initialRecoveryHrFromFlow
                        newAwaitingPostTimeout = false // Ya tenemos los datos.
                        Log.i("TestResultsVM", "RECOVERY: Periodo terminado. Datos CAPTURADOS por RecoveryDataFlow: SpO2=$finalRecoverySpo2, HR=$finalRecoveryHr")
                    } else {
                        // El minuto terminó, pero RecoveryDataFlow NO proveyó datos válidos en ese instante
                        // O solo proveyó uno de ellos.
                        // Ahora dependeremos del liveSensorData para rellenarlos si están conectados,
                        // o el usuario tendrá que actuar.
                        // Si ya tenemos valores (quizás de una reconexión previa y live data), no los borramos.
                        if (finalRecoverySpo2 == null || finalRecoveryHr == null) {
                            newAwaitingPostTimeout = true // Necesitamos esperar/obtenerlos.
                            Log.i("TestResultsVM", "RECOVERY: Periodo terminado. NO se capturaron datos completos por RecoveryDataFlow. Se esperará a live data o acción del usuario.")
                        } else {
                            // Ya teníamos ambos (quizás de un live data anterior tras reconexión), y el periodo terminó.
                            newAwaitingPostTimeout = false
                        }
                    }
                } else {
                    // El minuto de recuperación AÚN NO ha terminado.
                    // NO actualizamos recoverySpo2/HR en uiState todavía.
                    // Mantenemos isAwaitingPostTimeoutRecoveryData como estaba o lo ponemos a false si aún no terminó el minuto.
                    // Esto asegura que "Esperando..." se muestre.
                    finalRecoverySpo2 = null // Forzar a null mientras el periodo no termina
                    finalRecoveryHr = null   // Forzar a null mientras el periodo no termina
                    newAwaitingPostTimeout = false // No estamos "post-timeout" si el timeout no ha ocurrido
                    Log.d("TestResultsVM", "RECOVERY: Periodo AÚN NO terminado. recoverySpo2/HR se mantienen null en UI.")
                }

                _uiState.update { currentState ->
                    currentState.copy(
                        recoverySpo2 = finalRecoverySpo2,
                        recoveryHeartRate = finalRecoveryHr,
                        isRecoveryPeriodOver = internalRecoveryPeriodOver, // Actualiza el estado del UI
                        wasRecoveryDataCapturedInitially = internalRecoveryDataCapturedDuringInitialMinute, // Actualiza el estado del UI
                        isAwaitingPostTimeoutRecoveryData = newAwaitingPostTimeout
                    )
                }
                validateAllPostTestFields() // Revalidar cada vez que cambian estos estados
            }
        }
    }

    fun observeLiveSensorDataAndBluetoothStatus() {
        // Cancelar jobs existentes para evitar múltiples observadores si se llama de nuevo
        liveSensorDataJob?.cancel()
        Log.d("TestResultsVM_TREND", "Re-iniciando observación de datos del sensor (con muestreo de 1s).")

        // Limpiar listas y resetear estado de tendencia al (re)iniciar la observación
        spo2ValuesForNewTrend.clear()
        hrValuesForNewTrend.clear()
        spo2ValuesSinceLastTrendCalc.clear()
        hrValuesSinceLastTrendCalc.clear()
        lastSensorUpdateTime = 0L // Resetear el temporizador de procesamiento

        // Resetear tendencias y valores actuales en UiState para evitar mostrar datos viejos
        // hasta que lleguen los nuevos datos muestreados.
        _uiState.update { currentState ->
            currentState.copy(
                currentSpo2 = null,
                currentHeartRate = null,
                spo2Trend = Trend.STABLE,
                heartRateTrend = Trend.STABLE,
                spo2AlarmStatus = StatusColor.UNKNOWN,
                heartRateAlarmStatus = StatusColor.UNKNOWN,
                criticalAlarmMessage = null // Limpiar mensajes de alarma viejos
            )
        }

        // Observar datos del sensor
        liveSensorDataJob = bluetoothService.bleDeviceData
            .onEach { data: BleDeviceData ->
                val currentTime = System.currentTimeMillis()

                // Procesar solo si ha pasado al menos SENSOR_PROCESSING_INTERVAL_MS desde la última vez
                if (currentTime - lastSensorUpdateTime >= SENSOR_PROCESSING_INTERVAL_MS) {
                    lastSensorUpdateTime = currentTime
                    Log.d("TestResultsVM_SENSOR", "PROCESANDO DATOS (Muestreo 1s): SpO2=${data.spo2}, HR=${data.heartRate}, NoFinger=${data.noFingerDetected}, Signal=${data.signalStrength}")

                    // --- INICIO DE LA LÓGICA ORIGINAL DEL onEach ---
                    val currentUiStateValues = _uiState.value // Captura el estado actual DEL UI ANTES de este ciclo de procesamiento

                    // --- A. Determinar si hay datos válidos del sensor (dedo puesto) ---
                    val hasValidFingerData = !(
                            data.noFingerDetected == true ||
                                    data.signalStrength == NO_FINGER_OR_RECALIBRATING_SIGNAL || // Asegúrate que esta constante es correcta
                                    data.spo2 == null || data.spo2 == 0 ||
                                    data.heartRate == null || data.heartRate == 0
                            )

                    var newCurrentSpo2: Int? = null
                    var newCurrentHeartRate: Int? = null
                    var newSpo2Alarm = StatusColor.UNKNOWN
                    var newHrAlarm = StatusColor.UNKNOWN

                    var finalSpo2TrendForThisUpdate: Trend = currentUiStateValues.spo2Trend
                    var finalHrTrendForThisUpdate: Trend = currentUiStateValues.heartRateTrend

                    // Variables para datos de recuperación
                    var updatedRecoverySpo2 = currentUiStateValues.recoverySpo2
                    var updatedRecoveryHeartRate = currentUiStateValues.recoveryHeartRate
                    var newIsAwaitingPostTimeoutRecoveryData = currentUiStateValues.isAwaitingPostTimeoutRecoveryData
                    var userMessageForRecoveryUpdate: String? = null

                    var recoveryDataJustCapturedByLive = false

                    if (hasValidFingerData) {
                        newCurrentSpo2 = data.spo2
                        newCurrentHeartRate = data.heartRate
                        Log.d("TestResultsVM_TREND", "Dedo PUESTO. SpO2: $newCurrentSpo2, HR: $newCurrentHeartRate")

                        // ***** INICIO: Lógica de RELLENO para datos de recuperación *****
                        // Solo intentamos rellenar los datos de recuperación si:
                        // 1. El minuto de recuperación YA HA TERMINADO (internalRecoveryPeriodOver es true)
                        // 2. Y todavía nos faltan datos de recuperación (updatedRecoverySpo2 o updatedRecoveryHeartRate son null)
                        if (internalRecoveryPeriodOver) {
                            if (updatedRecoverySpo2 == null && newCurrentSpo2 != null && newCurrentSpo2 > 0) {
                                updatedRecoverySpo2 = newCurrentSpo2
                                Log.i("TestResultsVM", "LIVE_FILL: recoverySpo2 ($updatedRecoverySpo2) actualizado por live sensor data POST-PERIODO.")
                                recoveryDataJustCapturedByLive = true
                            }
                            if (updatedRecoveryHeartRate == null && newCurrentHeartRate != null && newCurrentHeartRate > 0) {
                                updatedRecoveryHeartRate = newCurrentHeartRate
                                Log.i("TestResultsVM", "LIVE_FILL: recoveryHeartRate ($updatedRecoveryHeartRate) actualizado por live sensor data POST-PERIODO.")
                                recoveryDataJustCapturedByLive = true
                            }

                            if (recoveryDataJustCapturedByLive) {
                                if (updatedRecoverySpo2 != null && updatedRecoveryHeartRate != null) {
                                    // Ya tenemos ambos, ya no estamos "awaiting" específicamente por ellos
                                    // a menos que el estado general de conexión/error lo requiera.
                                    // La variable 'newIsAwaitingPostTimeoutRecoveryData' se gestiona mejor en observeRecoveryData
                                    // y en onBluetoothIconClicked. Aquí solo confirmamos que tenemos los datos.
                                    if(newIsAwaitingPostTimeoutRecoveryData){ // Si estábamos esperando y ya los tenemos
                                        userMessageForRecoveryUpdate = "Datos de recuperación obtenidos."
                                        // Dejamos que observeRecoveryData o el onBluetoothIconClicked cambien newIsAwaitingPostTimeoutRecoveryData
                                    }
                                } else if (updatedRecoverySpo2 != null) {
                                    userMessageForRecoveryUpdate = "SpO2 de recuperación actualizada. Esperando FC..."
                                } else if (updatedRecoveryHeartRate != null) {
                                    userMessageForRecoveryUpdate = "FC de recuperación actualizada. Esperando SpO2..."
                                }
                            }
                        } else {
                            // El minuto de recuperación AÚN NO ha terminado.
                            // NO rellenamos updatedRecoverySpo2/Hr aquí, incluso si hay datos válidos.
                            // Dejamos que observeRecoveryData maneje la lógica inicial.
                            Log.d("TestResultsVM", "LIVE_SENSOR: Periodo de recuperación NO terminado. No se actualizan recoverySpo2/HR desde live data todavía.")
                        }

                        // --- LÓGICA DE TENDENCIA SpO2 ---
                        newCurrentSpo2?.let {
                            spo2ValuesForNewTrend.add(it)
                            spo2ValuesSinceLastTrendCalc.add(it)
                        }
                        while (spo2ValuesForNewTrend.size > 6) {
                            if (spo2ValuesForNewTrend.isNotEmpty()) spo2ValuesForNewTrend.removeAt(0)
                        }
                        Log.d("TestResultsVM_TREND", "SpO2 Lists: main=${spo2ValuesForNewTrend.joinToString()}, sinceLastCalc=${spo2ValuesSinceLastTrendCalc.joinToString()}")

                        if (spo2ValuesSinceLastTrendCalc.size >= 3) {
                            if (spo2ValuesForNewTrend.size == 6) {
                                finalSpo2TrendForThisUpdate = calculateTrendFromAverageOfLastThree(spo2ValuesForNewTrend.toList())
                                Log.i("TestResultsVM_TREND", "SpO2: CALCULADA nueva tendencia: $finalSpo2TrendForThisUpdate (desde ${spo2ValuesForNewTrend.toList()})")
                            } else {
                                Log.d("TestResultsVM_TREND", "SpO2: 3 nuevos valores, pero menos de 6 en total (${spo2ValuesForNewTrend.size}). Manteniendo tendencia: $finalSpo2TrendForThisUpdate")
                            }
                            spo2ValuesSinceLastTrendCalc.clear()
                        } else {
                            Log.d("TestResultsVM_TREND", "SpO2: Menos de 3 nuevos valores (${spo2ValuesSinceLastTrendCalc.size}). Manteniendo tendencia: $finalSpo2TrendForThisUpdate")
                        }

                        // --- LÓGICA DE TENDENCIA HR ---
                        newCurrentHeartRate?.let {
                            hrValuesForNewTrend.add(it)
                            hrValuesSinceLastTrendCalc.add(it)
                        }
                        while (hrValuesForNewTrend.size > 6) {
                            if (hrValuesForNewTrend.isNotEmpty()) hrValuesForNewTrend.removeAt(0)
                        }
                        Log.d("TestResultsVM_TREND", "HR Lists: main=${hrValuesForNewTrend.joinToString()}, sinceLastCalc=${hrValuesSinceLastTrendCalc.joinToString()}")

                        if (hrValuesSinceLastTrendCalc.size >= 3) {
                            if (hrValuesForNewTrend.size == 6) {
                                finalHrTrendForThisUpdate = calculateTrendFromAverageOfLastThree(hrValuesForNewTrend.toList())
                                Log.i("TestResultsVM_TREND", "HR: CALCULADA nueva tendencia: $finalHrTrendForThisUpdate (desde ${hrValuesForNewTrend.toList()})")
                            } else {
                                Log.d("TestResultsVM_TREND", "HR: 3 nuevos valores, pero menos de 6 en total (${hrValuesForNewTrend.size}). Manteniendo tendencia: $finalHrTrendForThisUpdate")
                            }
                            hrValuesSinceLastTrendCalc.clear()
                        } else {
                            Log.d("TestResultsVM_TREND", "HR: Menos de 3 nuevos valores (${hrValuesSinceLastTrendCalc.size}). Manteniendo tendencia: $finalHrTrendForThisUpdate")
                        }

                        newSpo2Alarm = newCurrentSpo2?.let { determineSpo2AlarmStatus(it) } ?: StatusColor.UNKNOWN
                        newHrAlarm = newCurrentHeartRate?.let { determineHeartRateAlarmStatus(it) } ?: StatusColor.UNKNOWN

                    } else {
                        Log.w("TestResultsVM_TREND", "Dedo NO PUESTO o datos inválidos (Muestreo 1s). Reseteando listas y tendencias.")
                        spo2ValuesForNewTrend.clear()
                        hrValuesForNewTrend.clear()
                        spo2ValuesSinceLastTrendCalc.clear()
                        hrValuesSinceLastTrendCalc.clear()
                        finalSpo2TrendForThisUpdate = Trend.STABLE
                        finalHrTrendForThisUpdate = Trend.STABLE
                    }

                    // --- B. Determinar estado visual del Bluetooth ---
                    // (Esta parte ya usa el 'data' del ciclo actual, lo cual es correcto)
                    val (currentBtIcon, currentBtMsg) = determineResultsBluetoothVisualStatus(
                        connectionStatus = bluetoothService.connectionStatus.value,
                        deviceData = data,
                        isBluetoothAdapterEnabled = bluetoothService.isBluetoothEnabled()
                    )

                    // --- C. Manejar Mensaje de Alarma Crítica ---
                    // (Esta lógica ya estaba bien)
                    var alarmMessageToShow: String? = currentUiStateValues.criticalAlarmMessage
                    var shouldStartNewTimerForCriticalAlarm = false
                    val previousUiStateSpo2Alarm = currentUiStateValues.spo2AlarmStatus
                    val previousUiStateHrAlarm = currentUiStateValues.heartRateAlarmStatus

                    if (newSpo2Alarm == StatusColor.CRITICAL && newCurrentSpo2 != null) {
                        val spo2Msg = "¡Alerta! SpO2 en nivel crítico: $newCurrentSpo2%"
                        alarmMessageToShow = if (_uiState.value.criticalAlarmMessage?.contains(spo2Msg) == false) {
                            _uiState.value.criticalAlarmMessage?.let { "$it\n$spo2Msg" } ?: spo2Msg
                        } else {
                            _uiState.value.criticalAlarmMessage ?: spo2Msg
                        }
                        if (previousUiStateSpo2Alarm != StatusColor.CRITICAL) {
                            shouldStartNewTimerForCriticalAlarm = true
                        }
                    } else if (previousUiStateSpo2Alarm == StatusColor.CRITICAL && newSpo2Alarm != StatusColor.CRITICAL) {
                        alarmMessageToShow = alarmMessageToShow?.lines()?.filterNot { it.contains("SpO2") }?.joinToString("\n")
                        if (alarmMessageToShow?.isBlank() == true) alarmMessageToShow = null
                    }

                    if (newHrAlarm == StatusColor.CRITICAL && newCurrentHeartRate != null) {
                        val hrMsg = "¡Alerta! Frecuencia Cardíaca en nivel crítico: $newCurrentHeartRate lpm"
                        alarmMessageToShow = if (_uiState.value.criticalAlarmMessage?.contains(hrMsg) == false) {
                            alarmMessageToShow?.let { "$it\n$hrMsg" } ?: hrMsg
                        } else {
                            alarmMessageToShow ?: hrMsg
                        }
                        if (previousUiStateHrAlarm != StatusColor.CRITICAL) {
                            shouldStartNewTimerForCriticalAlarm = true
                        }
                    } else if (previousUiStateHrAlarm == StatusColor.CRITICAL && newHrAlarm != StatusColor.CRITICAL) {
                        alarmMessageToShow = alarmMessageToShow?.lines()?.filterNot { it.contains("Frecuencia Cardíaca") }?.joinToString("\n")
                        if (alarmMessageToShow?.isBlank() == true) alarmMessageToShow = null
                    }

                    if (shouldStartNewTimerForCriticalAlarm && alarmMessageToShow != null) {
                        clearCriticalAlarmMessageAfterDelay()
                    }

                    if (newSpo2Alarm != StatusColor.CRITICAL && newHrAlarm != StatusColor.CRITICAL && _uiState.value.criticalAlarmMessage != null) {
                        alarmMessageToShow = null
                        clearCriticalAlarmMessage()
                    }

                    // --- D. Actualizar UiState ---
                    Log.d("TestResultsVM_TREND", "ACTUALIZANDO UI STATE (Muestreo 1s). SpO2 Trend: $finalSpo2TrendForThisUpdate, HR Trend: $finalHrTrendForThisUpdate")

                    var recoveryDataChangedInThisUpdate = false
                    val previousRecoverySpo2 = _uiState.value.recoverySpo2
                    val previousRecoveryHr = _uiState.value.recoveryHeartRate

                    _uiState.update { currentStateInternal ->
                        val newRecoverySpo2Value = if (internalRecoveryPeriodOver) updatedRecoverySpo2 else currentStateInternal.recoverySpo2
                        val newRecoveryHrValue = if (internalRecoveryPeriodOver) updatedRecoveryHeartRate else currentStateInternal.recoveryHeartRate

                        // Comprobar si los valores de recuperación realmente cambiaron
                        if (newRecoverySpo2Value != previousRecoverySpo2 || newRecoveryHrValue != previousRecoveryHr) {
                            recoveryDataChangedInThisUpdate = true
                        }

                        currentStateInternal.copy(
                            currentSpo2 = newCurrentSpo2,
                            currentHeartRate = newCurrentHeartRate,
                            spo2Trend = finalSpo2TrendForThisUpdate,
                            heartRateTrend = finalHrTrendForThisUpdate,
                            spo2AlarmStatus = newSpo2Alarm,
                            heartRateAlarmStatus = newHrAlarm,
                            criticalAlarmMessage = alarmMessageToShow,
                            bluetoothVisualStatus = if (currentStateInternal.isAttemptingForceReconnect) BluetoothIconStatus2.CONNECTING else currentBtIcon,
                            bluetoothStatusMessage = if (currentStateInternal.isAttemptingForceReconnect) "Reconectando..." else currentBtMsg,

                            // Actualizar valores de recuperación y flag
                            recoverySpo2 = newRecoverySpo2Value,
                            recoveryHeartRate = newRecoveryHrValue,
                            isAwaitingPostTimeoutRecoveryData = newIsAwaitingPostTimeoutRecoveryData,
                            userMessage = userMessageForRecoveryUpdate ?: currentStateInternal.userMessage // Mantener mensaje existente si no hay uno nuevo
                        )
                    }

                    // Si los datos de recuperación cambiaron, o si se acaba de obtener un dato de recuperación
                    // y estábamos esperando, revalidar.
                    if (recoveryDataChangedInThisUpdate || (recoveryDataJustCapturedByLive && userMessageForRecoveryUpdate != null)) {
                        validateAllPostTestFields() // Llamar DESPUÉS de actualizar el state
                    }

                    if (userMessageForRecoveryUpdate != null) {
                        clearUserMessageAfterDelay()
                    }
                } else {
                    // Opcional: Loguear datos descartados si quieres ver la frecuencia real de llegada
                    Log.v("TestResultsVM_SENSOR", "DATO DESCARTADO (muy frecuente, no ha pasado 1s): SpO2=${data.spo2}, HR=${data.heartRate}")
                }
            }
            .catch { e ->
                Log.e("TestResultsVM_TREND", "Error en flow de BleDeviceData: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        userMessage = "Error al procesar datos del sensor.",
                        // Limpiar valores también en caso de error del flow
                        currentSpo2 = null,
                        currentHeartRate = null,
                        spo2Trend = Trend.STABLE,
                        heartRateTrend = Trend.STABLE,
                        spo2AlarmStatus = StatusColor.UNKNOWN,
                        heartRateAlarmStatus = StatusColor.UNKNOWN
                    )
                }
                clearUserMessageAfterDelay()
            }
            .launchIn(viewModelScope)

        // El observador de bluetoothService.connectionStatus sigue siendo importante y no se modifica
        // (ya lo tienes y parece correcto para manejar el estado general de la conexión)
        if (bluetoothStatusJob == null || bluetoothStatusJob?.isActive == false) { // Para evitar relanzarlo si ya está activo
            bluetoothStatusJob = bluetoothService.connectionStatus
                .onEach { status: BleConnectionStatus ->
                    _uiState.update { currentState ->
                        val (newIcon, newMsg) = determineResultsBluetoothVisualStatus(
                            connectionStatus = status,
                            deviceData = bluetoothService.bleDeviceData.value, // Puede ser el último valor o null
                            isBluetoothAdapterEnabled = bluetoothService.isBluetoothEnabled()
                        )
                        val isConnectedByStatus = status.isConsideredConnectedOrSubscribed()

                        var isStillAttemptingReconnect = currentState.isAttemptingForceReconnect
                        if (isStillAttemptingReconnect && (status == BleConnectionStatus.SUBSCRIBED || status.isErrorStatus() || status == BleConnectionStatus.DISCONNECTED_BY_USER || status == BleConnectionStatus.IDLE)) {
                            isStillAttemptingReconnect = false
                        }

                        // Lógica para isAwaitingPostTimeoutRecoveryData en base a la conexión
                        var currentAwaiting = currentState.isAwaitingPostTimeoutRecoveryData
                        if (internalRecoveryPeriodOver) { // Solo nos importa "awaiting" si el periodo ya terminó
                            if (!isConnectedByStatus && (currentState.recoverySpo2 == null || currentState.recoveryHeartRate == null)) {
                                // Desconectado, periodo terminado, y faltan datos -> sí, estamos esperando.
                                currentAwaiting = true
                            } else if (isConnectedByStatus && (currentState.recoverySpo2 != null && currentState.recoveryHeartRate != null)) {
                                // Conectado, periodo terminado y TENEMOS ambos datos -> no estamos esperando.
                                currentAwaiting = false
                            }
                            // Si está conectado pero aún faltan datos, 'currentAwaiting' se mantendrá como estaba (probablemente true)
                        } else {
                            // Si el periodo de recuperación no ha terminado, no estamos "awaiting post timeout".
                            currentAwaiting = false
                        }

                        currentState.copy(
                            isDeviceConnected = isConnectedByStatus,
                            bluetoothVisualStatus = if (isStillAttemptingReconnect) BluetoothIconStatus2.CONNECTING else newIcon,
                            bluetoothStatusMessage = if (isStillAttemptingReconnect) "Reconectando..." else newMsg,
                            isAttemptingForceReconnect = isStillAttemptingReconnect,
                            isAwaitingPostTimeoutRecoveryData = currentAwaiting,

                            currentSpo2 = if (!isConnectedByStatus && !isStillAttemptingReconnect) null else currentState.currentSpo2,
                            currentHeartRate = if (!isConnectedByStatus && !isStillAttemptingReconnect) null else currentState.currentHeartRate,
                            spo2Trend = if (!isConnectedByStatus && !isStillAttemptingReconnect) Trend.STABLE else currentState.spo2Trend,
                            heartRateTrend = if (!isConnectedByStatus && !isStillAttemptingReconnect) Trend.STABLE else currentState.heartRateTrend,
                            spo2AlarmStatus = if (!isConnectedByStatus && !isStillAttemptingReconnect) StatusColor.UNKNOWN else currentState.spo2AlarmStatus,
                            heartRateAlarmStatus = if (!isConnectedByStatus && !isStillAttemptingReconnect) StatusColor.UNKNOWN else currentState.heartRateAlarmStatus,
                            criticalAlarmMessage = if (!isConnectedByStatus && !isStillAttemptingReconnect) null else currentState.criticalAlarmMessage
                        )
                    }
                    // Importante: revalidar si el estado de conexión cambia, ya que puede afectar
                    // si podemos obtener los datos de recuperación.
                    if (internalRecoveryPeriodOver) {
                        validateAllPostTestFields()
                    }
                }
                .catch { e ->
                    Log.e("TestResultsVM", "Error en flow de BleConnectionStatus: ${e.message}", e)
                    _uiState.update { it.copy(userMessage = "Error de conexión Bluetooth.") }
                    clearUserMessageAfterDelay()
                }
                .launchIn(viewModelScope)
        }
    }

    internal fun determineSpo2AlarmStatus(spo2: Int): StatusColor {
        return when {
            spo2 == 0 -> StatusColor.UNKNOWN
            // Usar los umbrales cargados desde SettingsRepository
            spo2 <= userSpo2CriticalThreshold -> StatusColor.CRITICAL
            spo2 < userSpo2WarningThreshold  -> StatusColor.WARNING // Si es < umbral de warning (y no crítico)
            else -> StatusColor.NORMAL
        }
    }

    internal fun determineHeartRateAlarmStatus(hr: Int): StatusColor {
        return when {
            hr == 0 -> StatusColor.UNKNOWN // Valor no válido o no disponible
            // Usar los umbrales cargados desde SettingsRepository
            hr < userHrCriticalLowThreshold || hr > userHrCriticalHighThreshold -> StatusColor.CRITICAL
            hr < userHrWarningLowThreshold || hr > userHrWarningHighThreshold -> StatusColor.WARNING
            else -> StatusColor.NORMAL
        }
    }

    private fun calculateTrendFromAverageOfLastThree(
        currentValues: List<Int> // Lista con los últimos 6 valores
    ): Trend {
        // Necesitamos al menos 6 valores para comparar dos grupos de 3
        if (currentValues.size < 6) {
            return Trend.STABLE // O la tendencia inicial que prefieras (guion)
        }

        // Tomar los últimos 6 valores. Asegúrate de que la lista se llena en orden cronológico.
        // Si los nuevos valores se añaden al final, esto es correcto.
        val lastSixValues = currentValues.takeLast(6)

        val previousThree = lastSixValues.subList(0, 3)
        val currentThree = lastSixValues.subList(3, 6)

        val averagePreviousThree = previousThree.average()
        val averageCurrentThree = currentThree.average()

        return when {
            averageCurrentThree > averagePreviousThree -> Trend.UP
            averageCurrentThree < averagePreviousThree -> Trend.DOWN
            else -> Trend.STABLE
        }
    }

    private fun determineResultsBluetoothVisualStatus(
        connectionStatus: BleConnectionStatus,
        deviceData: BleDeviceData?,
        isBluetoothAdapterEnabled: Boolean
    ): Pair<BluetoothIconStatus2, String> {
        if (!isBluetoothAdapterEnabled) {
            return BluetoothIconStatus2.RED to "Bluetooth desactivado"
        }

        return when (connectionStatus) {
            BleConnectionStatus.SUBSCRIBED -> {
                if (deviceData == null) {
                    BluetoothIconStatus2.YELLOW to "Conectado (esperando datos)"
                } else if (deviceData.noFingerDetected == true ||
                    deviceData.signalStrength == NO_FINGER_OR_RECALIBRATING_SIGNAL ||
                    deviceData.spo2 == null || deviceData.spo2 == 0 ||
                    deviceData.heartRate == null || deviceData.heartRate == 0) {
                    BluetoothIconStatus2.YELLOW to "Sensor: sin dedo/datos"
                } else if (deviceData.signalStrength != null && deviceData.signalStrength <= POOR_SIGNAL_THRESHOLD) {
                    BluetoothIconStatus2.YELLOW to "Sensor: señal baja"
                } else {
                    BluetoothIconStatus2.GREEN to "Sensor conectado"
                }
            }
            BleConnectionStatus.CONNECTED -> {
                BluetoothIconStatus2.CONNECTING /* O YELLOW */ to "Conectado (configurando...)"
            }
            BleConnectionStatus.CONNECTING, BleConnectionStatus.RECONNECTING -> {
                BluetoothIconStatus2.CONNECTING to "Conectando..."
            }
            BleConnectionStatus.SCANNING -> {
                BluetoothIconStatus2.CONNECTING to "Buscando dispositivo..."
            }
            BleConnectionStatus.DISCONNECTED_BY_USER -> {
                BluetoothIconStatus2.GRAY to "Desconectado (toque para conectar)"
            }
            BleConnectionStatus.DISCONNECTED_ERROR,
            BleConnectionStatus.ERROR_DEVICE_NOT_FOUND,
            BleConnectionStatus.ERROR_SERVICE_NOT_FOUND,
            BleConnectionStatus.ERROR_CHARACTERISTIC_NOT_FOUND,
            BleConnectionStatus.ERROR_SUBSCRIBE_FAILED,
            BleConnectionStatus.ERROR_GENERIC -> {
                BluetoothIconStatus2.RED to "Error conexión (toque para reintentar)"
            }
            BleConnectionStatus.IDLE -> {
                val lastDevice = bluetoothService.lastKnownConnectedDeviceAddress.value
                if (lastDevice != null) {
                    BluetoothIconStatus2.GRAY to "Inactivo (toque para reconectar)"
                } else {
                    BluetoothIconStatus2.GRAY to "Inactivo (sin disp. previo)"
                }
            }
            else -> BluetoothIconStatus2.GRAY to "Estado BT: ${connectionStatus.name}"
        }
    }

    fun onPostTestValueChange(field: PostTestField, value: String) {
        _uiState.update { currentState ->
            val newState = when (field) {
                PostTestField.BLOOD_PRESSURE -> currentState.copy(postTestBloodPressureInput = value)
                PostTestField.RESPIRATORY_RATE -> currentState.copy(postTestRespiratoryRateInput = value)
                PostTestField.DYSPNEA_BORG -> currentState.copy(postTestDyspneaBorgInput = value)
                PostTestField.LEG_PAIN_BORG -> currentState.copy(postTestLegPainBorgInput = value)
            }
            newState.copy(hasUnsavedChanges = if (newState.isTestSaved) true else newState.hasUnsavedChanges)
        }
        validateSinglePostTestField(field, value)
        validateAllPostTestFields()
    }

    private fun validateSinglePostTestField(field: PostTestField, value: String) {
        _uiState.update { currentState ->
            when (field) {
                PostTestField.BLOOD_PRESSURE -> {
                    val (isValidFormat, sys, dia) = parseAndValidateBloodPressure(value)
                    currentState.copy(
                        isPostTestBloodPressureValid = isValidFormat,
                        postTestSystolicBP = if (isValidFormat) sys else null,
                        postTestDiastolicBP = if (isValidFormat) dia else null
                    )
                }
                PostTestField.RESPIRATORY_RATE -> {
                    val intValue = value.toIntOrNull()
                    val isValid = intValue != null && intValue in inputRrMin..inputRrMax
                    currentState.copy(
                        isPostTestRespiratoryRateValid = isValid,
                        postTestRespiratoryRate = if (isValid) intValue else null
                    )
                }
                PostTestField.DYSPNEA_BORG -> {
                    val intValue = value.toIntOrNull() // Borg es entero
                    val isValid = intValue != null && intValue in inputBorgMin..inputBorgMax
                    currentState.copy(
                        isPostTestDyspneaBorgValid = isValid,
                        postTestDyspneaBorg = if (isValid) intValue else null
                    )
                }
                PostTestField.LEG_PAIN_BORG -> {
                    val intValue = value.toIntOrNull() // Borg es entero
                    val isValid = intValue != null && intValue in inputBorgMin..inputBorgMax
                    currentState.copy(
                        isPostTestLegPainBorgValid = isValid,
                        postTestLegPainBorg = if (isValid) intValue else null
                    )
                }
            }
        }
    }

    private fun parseAndValidateBloodPressure(bpInput: String): Triple<Boolean, Int?, Int?> {
        if (bpInput.isBlank()) {
            return Triple(true, null, null) // Considerar vacío como válido para no mostrar error inmediato, pero no completo
        }
        if (!BLOOD_PRESSURE_PATTERN.matches(bpInput)) {
            return Triple(false, null, null) // Formato incorrecto
        }
        val parts = bpInput.split("/").map { it.trim().toIntOrNull() }
        if (parts.size == 2 && parts[0] != null && parts[1] != null) {
            val sys = parts[0]!!
            val dia = parts[1]!!
            if (sys in inputBpSystolicMin..inputBpSystolicMax &&
                dia in inputBpDiastolicMin..inputBpDiastolicMax &&
                sys >= dia) {
                return Triple(true, sys, dia) // Válido
            }
        }
        return Triple(false, null, null) // Error de rango o lógica (sys < dia)
    }

    private fun validateAllPostTestFields() {
        _uiState.update { currentState ->
            val tempMissingOrInvalidFields = mutableListOf<String>()

            // Lógica de validación para recoverySpo2
            if (currentState.recoverySpo2 == null) {
                if (!internalRecoveryPeriodOver) { // Usamos la variable interna que es más directa
                    tempMissingOrInvalidFields.add("SpO2 Post (esperando 1 min rec.)")
                } else if (!currentState.isDeviceConnected && currentState.isAwaitingPostTimeoutRecoveryData) {
                    tempMissingOrInvalidFields.add("SpO2 Post (conectar sensor)")
                } else if (currentState.isDeviceConnected && currentState.isAwaitingPostTimeoutRecoveryData) {
                    tempMissingOrInvalidFields.add("SpO2 Post (esperando dato válido)")
                } else if (internalRecoveryPeriodOver && !currentState.isAwaitingPostTimeoutRecoveryData && !currentState.wasRecoveryDataCapturedInitially){
                    // Periodo terminó, no estamos "awaiting" (quizás porque el usuario no ha clicado en reconectar y estaba desconectado),
                    // Y no se capturaron inicialmente.
                    tempMissingOrInvalidFields.add("SpO2 Post (dato no obtenido)")
                } else { // Caso por defecto si los anteriores no aplican
                    tempMissingOrInvalidFields.add("SpO2 Post-Prueba")
                }
            }

            // Lógica de validación para recoveryHeartRate (similar a SpO2)
            if (currentState.recoveryHeartRate == null) {
                if (!internalRecoveryPeriodOver) {
                    tempMissingOrInvalidFields.add("FC Post (esperando 1 min rec.)")
                } else if (!currentState.isDeviceConnected && currentState.isAwaitingPostTimeoutRecoveryData) {
                    tempMissingOrInvalidFields.add("FC Post (conectar sensor)")
                } else if (currentState.isDeviceConnected && currentState.isAwaitingPostTimeoutRecoveryData) {
                    tempMissingOrInvalidFields.add("FC Post (esperando dato válido)")
                } else if (internalRecoveryPeriodOver && !currentState.isAwaitingPostTimeoutRecoveryData && !currentState.wasRecoveryDataCapturedInitially){
                    tempMissingOrInvalidFields.add("FC Post (dato no obtenido)")
                } else {
                    tempMissingOrInvalidFields.add("FC Post-Prueba")
                }
            }

            val (isBpFormatOk, parsedSysBp, parsedDiaBp) = parseAndValidateBloodPressure(currentState.postTestBloodPressureInput)
            val rrInputVal = currentState.postTestRespiratoryRateInput
            val dysInputVal = currentState.postTestDyspneaBorgInput
            val legInputVal = currentState.postTestLegPainBorgInput

            val rr = rrInputVal.toIntOrNull()
            val dys = dysInputVal.toIntOrNull()
            val leg = legInputVal.toIntOrNull()

            val isRrFormatOk = rrInputVal.isBlank() || (rr != null && rr in inputRrMin..inputRrMax)
            val isDysFormatOk = dysInputVal.isBlank() || (dys != null && dys in inputBorgMin..inputBorgMax)
            val isLegFormatOk = legInputVal.isBlank() || (leg != null && leg in inputBorgMin..inputBorgMax)

            val finalSysBp = if (isBpFormatOk && parsedSysBp != null) parsedSysBp else null
            val finalDiaBp = if (isBpFormatOk && parsedDiaBp != null) parsedDiaBp else null
            val finalRr = if (isRrFormatOk && rr != null) rr else null
            val finalDys = if (isDysFormatOk && dys != null) dys else null
            val finalLeg = if (isLegFormatOk && leg != null) leg else null

            val recoverySpo2Available = currentState.recoverySpo2 != null
            val recoveryHrAvailable = currentState.recoveryHeartRate != null
            val manualFieldsCompleteAndValid = finalSysBp != null && finalDiaBp != null && finalRr != null && finalDys != null && finalLeg != null
            val areAllFieldsCompleteAndValid = manualFieldsCompleteAndValid && recoverySpo2Available && recoveryHrAvailable

            // Re-evaluar los mensajes para los campos manuales basado en si están completos
            if (currentState.postTestBloodPressureInput.isBlank()) tempMissingOrInvalidFields.add("TA Post")
            else if (!isBpFormatOk) tempMissingOrInvalidFields.add("TA Post (formato/rango incorrecto)")

            if (rrInputVal.isBlank()) tempMissingOrInvalidFields.add("FR Post")
            else if (!isRrFormatOk) tempMissingOrInvalidFields.add("FR Post (rango incorrecto)")

            if (dysInputVal.isBlank()) tempMissingOrInvalidFields.add("Disnea Post")
            else if (!isDysFormatOk) tempMissingOrInvalidFields.add("Disnea Post (rango incorrecto)")

            if (legInputVal.isBlank()) tempMissingOrInvalidFields.add("Dolor MII Post")
            else if (!isLegFormatOk) tempMissingOrInvalidFields.add("Dolor MII Post (rango incorrecto)")

            val validationMsg = when {
                !internalRecoveryPeriodOver && (currentState.recoverySpo2 == null || currentState.recoveryHeartRate == null) ->
                    "Esperando finalización del minuto de recuperación para SpO2/FC Post-Prueba..."
                areAllFieldsCompleteAndValid -> "Todos los campos del registro post-prueba completo son válidos."
                tempMissingOrInvalidFields.isNotEmpty() -> {
                    val prefix = if (tempMissingOrInvalidFields.size > 1) "Faltan o son incorrectos: " else "Falta o es incorrecto: "
                    prefix + tempMissingOrInvalidFields.joinToString(", ") + "."
                }
                else -> "Por favor, complete todos los campos post-prueba." // Mensaje por defecto si no hay errores específicos pero no está todo completo
            }

            currentState.copy(
                isPostTestBloodPressureValid = isBpFormatOk,
                isPostTestRespiratoryRateValid = isRrFormatOk,
                isPostTestDyspneaBorgValid = isDysFormatOk,
                isPostTestLegPainBorgValid = isLegFormatOk,
                arePostTestValuesCompleteAndValid = areAllFieldsCompleteAndValid,
                validationMessage = validationMsg
            )
        }
    }

    fun onObservationsChange(text: String) {
        _uiState.update {
            it.copy(
                observations = text,
                hasUnsavedChanges = if (it.isTestSaved) true else it.hasUnsavedChanges
            )
        }
    }

    fun onShowObservationsDialog(show: Boolean) {
        _uiState.update { it.copy(showObservationsDialog = show) }
    }

    fun onSaveTestClicked() {
        val currentState = _uiState.value
        if (!currentState.arePostTestValuesCompleteAndValid) {
            _uiState.update { it.copy(userMessage = "Complete todos los campos post-prueba antes de guardar.") }
            clearUserMessageAfterDelay(3500)
            return
        }

        val patientIdToSave = currentState.summaryData?.patientId
            ?: currentState.patientId.takeIf { it.isNotBlank() } ?: run {
                Log.e("TestResultsVM", "ID de paciente vacío, no se puede guardar.")
                _uiState.update { it.copy(userMessage = "Error: Falta ID de paciente para guardar.") }
                clearUserMessageAfterDelay()
                return
            }

        viewModelScope.launch {
            val testDetails = PruebaCompletaDetalles(
                summaryData = currentState.summaryData,
                postTestSpo2 = currentState.recoverySpo2,
                postTestHeartRate = currentState.recoveryHeartRate,
                postTestSystolicBP = currentState.postTestSystolicBP,
                postTestDiastolicBP = currentState.postTestDiastolicBP,
                postTestRespiratoryRate = currentState.postTestRespiratoryRate,
                postTestDyspneaBorg = currentState.postTestDyspneaBorg,
                postTestLegPainBorg = currentState.postTestLegPainBorg,
                observations = currentState.observations.ifEmpty { null }
            )

            val testDate = currentState.summaryData?.testActualStartTimeMillis ?: currentState.testDate
            val distance = currentState.summaryData?.distanceMetersFinal ?: 0f
            val percentage = currentState.percentageOfTheoretical
            val minSpo2Value = currentState.summaryData?.minSpo2Record?.value ?: 0
            val stopsValue = currentState.summaryData?.stopRecords?.size ?: 0

            try {
                if (currentState.isTestSaved && currentState.savedTestDatabaseId != null) {
                    // Actualizar prueba existente
                    val pruebaIdExistente = currentState.savedTestDatabaseId // Ya es Int
                    val existingTestNumber = currentState.savedTestNumeroPruebaPaciente
                        ?: pacienteRepository.getNumeroPruebaById(pruebaIdExistente) // Llamada correcta
                        ?: 0 // Debería existir

                    val pruebaActualizada = PruebaRealizada(
                        pruebaId = pruebaIdExistente,
                        pacienteId = patientIdToSave,
                        fechaTimestamp = testDate,
                        numeroPruebaPaciente = existingTestNumber,
                        distanciaRecorrida = distance,
                        porcentajeTeorico = percentage,
                        spo2min = minSpo2Value,
                        stops = stopsValue,
                        datosCompletos = testDetails
                    )
                    pacienteRepository.actualizarPruebaRealizada(pruebaActualizada)
                    Log.i("TestResultsVM", "Prueba ID ${currentState.savedTestDatabaseId} actualizada para paciente: $patientIdToSave")
                    _uiState.update {
                        it.copy(
                            userMessage = "Cambios en la Prueba N.º$existingTestNumber guardados.",
                            hasUnsavedChanges = false // Cambios guardados
                        )
                    }
                } else {
                    // Guardar nueva prueba
                    val nextTestNumber = pacienteRepository.getProximoNumeroPruebaParaPaciente(patientIdToSave)
                    val nuevaPruebaSinId = PruebaRealizada(
                        pacienteId = patientIdToSave,
                        fechaTimestamp = testDate,
                        numeroPruebaPaciente = nextTestNumber,
                        distanciaRecorrida = distance,
                        porcentajeTeorico = percentage,
                        spo2min = minSpo2Value,
                        stops = stopsValue,
                        datosCompletos = testDetails
                    )
                    val pruebaGuardada = pacienteRepository.guardarPruebaRealizada(nuevaPruebaSinId)
                    if (pruebaGuardada != null) {
                        Log.i("TestResultsVM", "Prueba N.º${pruebaGuardada.numeroPruebaPaciente} guardada (ID: ${pruebaGuardada.pruebaId}) para paciente: $patientIdToSave")
                        _uiState.update {
                            it.copy(
                                isTestSaved = true,
                                savedTestDatabaseId = pruebaGuardada.pruebaId,
                                savedTestNumeroPruebaPaciente = pruebaGuardada.numeroPruebaPaciente,
                                userMessage = "Prueba N.º${pruebaGuardada.numeroPruebaPaciente} guardada exitosamente.",
                                hasUnsavedChanges = false
                            )
                        }
                    } else {
                        Log.e("TestResultsVM", "Error al guardar la nueva prueba y obtener su ID.")
                        _uiState.update {
                            it.copy(userMessage = "Error al guardar la prueba en la base de datos.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TestResultsVM", "Error al guardar/actualizar la prueba para $patientIdToSave: ${e.message}")
                _uiState.update {
                    it.copy(userMessage = "Error al guardar los resultados en la base de datos.")
                }
            } finally {
                clearUserMessageAfterDelay(3000)
            }
        }
    }

    fun onNavigationHandled() { // Renombrado para claridad
        _uiState.update { it.copy(shouldNavigateToHome = false) }
    }

    fun onGeneratePdfClicked() {
        val currentState = _uiState.value
        // Condición 1: Campos post-prueba completos y válidos
        if (!currentState.arePostTestValuesCompleteAndValid) {
            _uiState.update { it.copy(userMessage = "PDF no generado: Complete y guarde todos los campos post-prueba.") }
            clearUserMessageAfterDelay(3500)
            return
        }

        // Condición 2: La prueba debe estar guardada
        if (!currentState.isTestSaved) {
            _uiState.update { it.copy(userMessage = "Guarde la prueba primero para generar el PDF.") }
            clearUserMessageAfterDelay(3500)
            return
        }

        // Condición 3: No debe haber cambios sin guardar sobre una prueba ya guardada
        if (currentState.hasUnsavedChanges) { // Implícitamente, isTestSaved es true aquí
            _uiState.update { it.copy(userMessage = "Guarde los cambios pendientes para generar el PDF.") }
            clearUserMessageAfterDelay(3500)
            return
        }

        // Si llegamos aquí, la prueba está guardada, completa, y sin cambios pendientes.
        generatePdfReportInternal()
    }

    private fun generatePdfReportInternal() { // Renombrada para evitar confusión con el onClick handler
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingPdf = true, pdfGeneratedUri = null, pdfGenerationError = null) }

            val currentUiState = _uiState.value // Tomar el estado más reciente

            // Validaciones básicas que ya estaban
            if (currentUiState.patientId.isBlank()) {
                Log.e("TestResultsVM", "generatePdfReportInternal: Patient ID está vacío.")
                _uiState.update { it.copy(isGeneratingPdf = false, pdfGenerationError = "Falta ID de paciente.", userMessage = "Error: Falta ID de paciente.") }
                clearUserMessageAfterDelay()
                return@launch
            }
            if (currentUiState.summaryData == null) {
                Log.e("TestResultsVM", "generatePdfReportInternal: summaryData es null.")
                _uiState.update { it.copy(isGeneratingPdf = false, pdfGenerationError = "Faltan datos de la prueba.", userMessage = "Error: Faltan datos de la prueba.") }
                clearUserMessageAfterDelay()
                return@launch
            }

            // --- Determinar el número de prueba para el PDF ---
            // Si la prueba ya ha sido guardada, usa el número de prueba guardado.
            // Si no, obtén el "próximo" número como antes (sería para una prueba aún no guardada).
            val numeroPruebaParaPdf: Int = currentUiState.savedTestNumeroPruebaPaciente ?: try {
                val idPaciente = currentUiState.summaryData.patientId.ifBlank { currentUiState.patientId }
                if (idPaciente.isNotBlank()) {
                    pacienteRepository.getProximoNumeroPruebaParaPaciente(idPaciente)
                } else {
                    Log.w("TestResultsVM", "generatePdfReportInternal: No se pudo obtener ID de paciente para número de prueba.")
                    0 // O un valor por defecto / manejar como error
                }
            } catch (e: Exception) {
                Log.e("TestResultsVM", "generatePdfReportInternal: Error al obtener próximo número de prueba para PDF", e)
                0 // O un valor por defecto / manejar como error
            }

            val databaseIdPruebaParaPdf: Int? = currentUiState.savedTestDatabaseId
            if (databaseIdPruebaParaPdf == null) {
                Log.e("TestResultsVM", "generatePdfReportInternal: savedTestDatabaseId es null. No se puede generar PDF.")
                _uiState.update {
                    it.copy(
                        isGeneratingPdf = false,
                        pdfGenerationError = "Error: Falta ID de la prueba guardada para el PDF.",
                        userMessage = "Error: Falta ID de la prueba guardada para el PDF."
                    )
                }
                clearUserMessageAfterDelay()
                return@launch
            }

            val detallesParaPdf = PruebaCompletaDetalles(
                summaryData = currentUiState.summaryData,
                postTestSpo2 = currentUiState.recoverySpo2,
                postTestHeartRate = currentUiState.recoveryHeartRate,
                postTestSystolicBP = currentUiState.postTestSystolicBP,
                postTestDiastolicBP = currentUiState.postTestDiastolicBP,
                postTestRespiratoryRate = currentUiState.postTestRespiratoryRate,
                postTestDyspneaBorg = currentUiState.postTestDyspneaBorg,
                postTestLegPainBorg = currentUiState.postTestLegPainBorg,
                observations = currentUiState.observations.ifEmpty { null }
            )

            try {
                val pdfFile: File? = withContext(Dispatchers.IO) {
                    SixMinuteWalkTestPdfGenerator.generatePdf(
                        context = applicationContext,
                        detallesPrueba = detallesParaPdf,
                        numeroPrueba = numeroPruebaParaPdf, // Usar el número determinado
                        pruebaId = databaseIdPruebaParaPdf
                    )
                }

                if (pdfFile != null) {
                    val pdfUri = FileProvider.getUriForFile(
                        applicationContext,
                        "${applicationContext.packageName}.provider",
                        pdfFile
                    )
                    _uiState.update {
                        it.copy(
                            isGeneratingPdf = false,
                            pdfGeneratedUri = pdfUri,
                            userMessage = "Informe PDF (Prueba N.º$numeroPruebaParaPdf) guardado."
                        )
                    }
                    clearUserMessageAfterDelay(4000)
                    Log.i("TestResultsVM", "PDF generado (Prueba N.º$numeroPruebaParaPdf): $pdfUri")
                } else {
                    Log.e("TestResultsVM", "SixMinuteWalkTestPdfGenerator.generatePdf devolvió null.")
                    _uiState.update {
                        it.copy(
                            isGeneratingPdf = false,
                            pdfGenerationError = "No se pudo generar el archivo PDF.",
                            userMessage = "Error al crear el informe PDF."
                        )
                    }
                    clearUserMessageAfterDelay(4000)
                }
            } catch (e: Exception) {
                Log.e("TestResultsVM", "Error al generar o guardar el PDF", e)
                _uiState.update {
                    it.copy(
                        isGeneratingPdf = false,
                        pdfGenerationError = "Error al generar PDF: ${e.localizedMessage}",
                        userMessage = "Error al guardar el PDF."
                    )
                }
                clearUserMessageAfterDelay(4000)
            }
        }
    }

    fun requestFinalizeTest() { // Nueva función para la acción del botón "Finalizar"
        val currentState = _uiState.value
        if (!currentState.arePostTestValuesCompleteAndValid) {
            _uiState.update { it.copy(userMessage = "Complete y guarde todos los campos para finalizar.") }
            clearUserMessageAfterDelay(4000)
            return
        }
        if (!currentState.isTestSaved) {
            _uiState.update { it.copy(userMessage = "Guarde la prueba primero para poder finalizar.") }
            clearUserMessageAfterDelay(4000)
            return
        }
        if (currentState.hasUnsavedChanges) {
            _uiState.update { it.copy(userMessage = "Guarde los cambios pendientes antes de finalizar.") }
            clearUserMessageAfterDelay(4000)
            return
        }
        // Si todo está OK, procede a la navegación
        _uiState.update { it.copy(shouldNavigateToHome = true) }
    }

    fun onBluetoothIconClicked() {
        viewModelScope.launch {
            if (_uiState.value.isAttemptingForceReconnect) {
                Log.d("TestResultsVM", "Intento de reconexión ya en curso.")
                _uiState.update { it.copy(userMessage = "Reconexión en progreso...") }
                clearUserMessageAfterDelay(1500)
                return@launch
            }

            if (!bluetoothService.isBluetoothEnabled()) {
                _uiState.update { it.copy(userMessage = "Active Bluetooth en los ajustes del sistema.") }
                clearUserMessageAfterDelay(3500)
                return@launch
            }

            val currentUiStateValues = _uiState.value
            val visualStatus = currentUiStateValues.bluetoothVisualStatus
            val currentServiceStatus = bluetoothService.connectionStatus.value

            // Si el periodo de recuperación ha terminado Y faltan datos de SpO2 o HR
            var shouldExplicitlyAwaitRecoveryData = false
            if (internalRecoveryPeriodOver && (currentUiStateValues.recoverySpo2 == null || currentUiStateValues.recoveryHeartRate == null)) {
                shouldExplicitlyAwaitRecoveryData = true
                Log.d("TestResultsVM", "BT Icon Clicked: Periodo terminado, faltan datos de recuperación. Marcando isAwaitingPostTimeoutRecoveryData=true.")
            }

            if (visualStatus == BluetoothIconStatus2.RED ||
                (visualStatus == BluetoothIconStatus2.GRAY &&
                        (currentUiStateValues.bluetoothStatusMessage.contains("reconectar", ignoreCase = true) ||
                                currentUiStateValues.bluetoothStatusMessage.contains("inactivo", ignoreCase = true) ||
                                currentServiceStatus == BleConnectionStatus.IDLE || currentServiceStatus.isErrorStatus()
                                )
                        )
            ) {
                val deviceAddressToReconnect = bluetoothService.lastKnownConnectedDeviceAddress.value
                if (deviceAddressToReconnect != null) {
                    _uiState.update {
                        it.copy(
                            isAttemptingForceReconnect = true,
                            // Si ya marcamos shouldExplicitlyAwaitRecoveryData, lo mantenemos, si no, mantenemos el valor actual.
                            isAwaitingPostTimeoutRecoveryData = if (shouldExplicitlyAwaitRecoveryData) true else it.isAwaitingPostTimeoutRecoveryData
                        )
                    }
                    attemptDeviceReconnection(deviceAddressToReconnect)
                } else {
                    Log.e("TestResultsVM", "onBluetoothIconClicked: Se intentó reconectar pero lastKnownConnectedDeviceAddress es null. Esto no debería ocurrir.")
                    _uiState.update { it.copy(userMessage = "Error interno: No se encontró dispositivo previo.") }
                    clearUserMessageAfterDelay()
                }
            } else if (currentServiceStatus.isConsideredConnectedOrSubscribed()) {
                if (shouldExplicitlyAwaitRecoveryData) { // Ya está conectado, pero faltan datos de recuperación (y el periodo terminó)
                    _uiState.update { it.copy(
                        userMessage = "Sensor conectado. Esperando datos válidos de SpO2/FC de recuperación...",
                        isAwaitingPostTimeoutRecoveryData = true // Confirmar que estamos esperando activamente
                    )}
                    clearUserMessageAfterDelay(3500)
                } else if (!internalRecoveryPeriodOver && (currentUiStateValues.recoverySpo2 == null || currentUiStateValues.recoveryHeartRate == null)){
                    _uiState.update { it.copy(userMessage = "Sensor conectado. Esperando fin del minuto de recuperación para SpO2/FC.") }
                    clearUserMessageAfterDelay()
                } else {
                    _uiState.update { it.copy(userMessage = "Sensor ya ${currentUiStateValues.bluetoothStatusMessage.lowercase()}.") }
                    clearUserMessageAfterDelay()
                }
            } else {
                Log.d("TestResultsVM", "Icono BT pulsado, estado visual: $visualStatus. Mensaje: ${_uiState.value.bluetoothStatusMessage}. No se requiere acción o ya en curso.")
                _uiState.update { it.copy(userMessage = "Sensor: ${_uiState.value.bluetoothStatusMessage}") }
                clearUserMessageAfterDelay()
            }
            validateAllPostTestFields()
        }
    }

    private suspend fun attemptDeviceReconnection(deviceAddress: String) {
        Log.i("TestResultsVM", "Intentando reconexión forzada con $deviceAddress")

        if (bluetoothService.connectionStatus.value.isConsideredConnectedOrSubscribed()) {
            bluetoothService.disconnect()
            delay(500)
        }
        bluetoothService.connect(deviceAddress)

        delay(FORCE_RECONNECT_TIMEOUT_SECONDS * 1000L)

        if (_uiState.value.isAttemptingForceReconnect &&
            bluetoothService.connectionStatus.value != BleConnectionStatus.SUBSCRIBED) {
            Log.w("TestResultsVM", "Timeout de reconexión. El servicio no conectó a tiempo.")
            _uiState.update {
                it.copy(
                isAttemptingForceReconnect = false,
                userMessage = "Fallo al reconectar con el dispositivo."
                )
            }
            clearUserMessageAfterDelay()
        } else if (_uiState.value.isAttemptingForceReconnect &&
            bluetoothService.connectionStatus.value == BleConnectionStatus.SUBSCRIBED) {
            Log.i("TestResultsVM", "Reconexión forzada exitosa, confirmada tras delay.")
            _uiState.update { it.copy(isAttemptingForceReconnect = false, userMessage = "Reconexión exitosa.") }
            clearUserMessageAfterDelay()
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
        userMessageClearJob?.cancel()
    }

    fun clearUserMessageAfterDelay(delayMillis: Long = 2500) {
        userMessageClearJob?.cancel()
        userMessageClearJob = viewModelScope.launch {
            delay(delayMillis)
            clearUserMessage()
        }
    }

    fun clearCriticalAlarmMessage() {
        criticalAlarmMessageClearJob?.cancel()
        _uiState.update { it.copy(criticalAlarmMessage = null) }
    }

    private fun clearCriticalAlarmMessageAfterDelay(delayMillis: Long = 5000L) {
        criticalAlarmMessageClearJob?.cancel()
        criticalAlarmMessageClearJob = viewModelScope.launch {
            delay(delayMillis)
            clearCriticalAlarmMessage()
        }
    }

    override fun onCleared() {
        super.onCleared()
        recoveryDataJob?.cancel()
        liveSensorDataJob?.cancel()
        bluetoothStatusJob?.cancel()
        userMessageClearJob?.cancel()
        criticalAlarmMessageClearJob?.cancel()
        Log.d("TestResultsVM", "ViewModel onCleared.")
    }

    fun clearPdfUri() {
        _uiState.update { it.copy(pdfGeneratedUri = null) }
    }

    fun BleConnectionStatus.isConsideredConnectedOrSubscribed(): Boolean {
        return this == BleConnectionStatus.CONNECTED || this == BleConnectionStatus.SUBSCRIBED
    }

    fun BleConnectionStatus.isErrorStatus(): Boolean {
        return this == BleConnectionStatus.DISCONNECTED_ERROR ||
                this == BleConnectionStatus.ERROR_DEVICE_NOT_FOUND ||
                this == BleConnectionStatus.ERROR_SERVICE_NOT_FOUND ||
                this == BleConnectionStatus.ERROR_CHARACTERISTIC_NOT_FOUND ||
                this == BleConnectionStatus.ERROR_SUBSCRIBE_FAILED ||
                this == BleConnectionStatus.ERROR_GENERIC
    }
}

fun formatDurationMillis(millis: Long): String {
    if (millis < 0) return "N/A"
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
