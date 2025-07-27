package com.example.app6mwt.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app6mwt.bluetooth.BleConnectionStatus
import com.example.app6mwt.bluetooth.BleDeviceData
import com.example.app6mwt.bluetooth.BluetoothService
import com.example.app6mwt.data.DefaultThresholdValues
import com.example.app6mwt.data.SettingsRepository
import com.example.app6mwt.di.RecoveryData
import com.example.app6mwt.di.TestStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject


// --- Constantes ---
const val TEST_DURATION_MILLIS = 6 * 60 * 1000L
const val STOP_CONFIRMATION_COUNTDOWN_SECONDS = 5

const val TRACK_LENGTH = 30

const val RESTART_TEST_CONFIRMATION_MESSAGE = "¿Reiniciar la prueba? Se perderán los datos actuales y la prueba comenzará de nuevo desde el principio."
const val REINITIALIZE_TEST_CONFIRMATION_MESSAGE = "¿Volver a la configuración inicial? Se perderán los datos de la prueba actual."
const val TEST_COMPLETED_INFO_MESSAGE = "Prueba completada."
const val NAVIGATE_TO_RESULTS_CONFIRMATION_MESSAGE = "¿Navegar a la pantalla de resultados?"

private const val TREND_WINDOW_SIZE_FOR_CALC = 6
private const val NEW_VALUES_THRESHOLD_FOR_TREND_CALC = 3

const val FORCE_RECONNECT_TIMEOUT_SECONDS = 10
const val POOR_SIGNAL_THRESHOLD = 4
const val NO_FINGER_OR_RECALIBRATING_SIGNAL = 15

// --- Data classes y Enums ---
data class DataPoint(val timeMillis: Long, val value: Float, val distanceAtTime: Float)

data class StopRecord(
    val stopTimeMillis: Long,
    val spo2AtStopTime: Int,
    val heartRateAtStopTime: Int,
    val distanceAtStopTime: Float,
    val stopTimeFormatted: String
)

data class CriticalValueRecord(
    val value: Int,
    val timeMillis: Long,
    val distanceAtTime: Float
)

data class MinuteDataSnapshot(
    val minuteMark: Int, // 1, 2, 3, 4, 5, 6
    val minSpo2Overall: Int?, // El SpO2 mínimo desde el inicio HASTA ESTE MINUTO
    val maxHrOverall: Int?,   // El FC máximo desde el inicio HASTA ESTE MINUTO
    val distanceAtMinuteEnd: Float? // La distancia al final exacto de este minuto
)

enum class Trend { UP, DOWN, STABLE }
enum class StatusColor { NORMAL, WARNING, CRITICAL, UNKNOWN }
enum class MainButtonAction { START, RESTART_DURING_TEST, REINITIALIZE_AFTER_TEST }
enum class BluetoothIconStatus { GREEN, YELLOW, RED, GRAY, CONNECTING }

data class TestExecutionUiState(
    // Datos del paciente y configuración de la prueba
    val patientFullName: String = "",
    val patientId: String = "",
    val trackLength: Int = TRACK_LENGTH,

    // Estado en tiempo real de la prueba
    val currentTimeMillis: Long = 0L,
    val currentTimeFormatted: String = "00:00",
    val laps: Float = 0.0f,
    val lapsInputText: String = "0.00",
    val distanceMeters: Float = 0f,

    // Datos de sensores en tiempo real (siempre activos)
    val currentSpo2: Int? = null,
    val currentHeartRate: Int? = null,
    val spo2Trend: Trend = Trend.STABLE,
    val heartRateTrend: Trend = Trend.STABLE,
    val spo2StatusColor: StatusColor = StatusColor.UNKNOWN,
    val heartRateStatusColor: StatusColor = StatusColor.UNKNOWN,
    val isSensorFingerPresent: Boolean = true,
    val currentSignalStrength: Int? = null,

    // Datos acumulados durante la prueba
    val spo2DataPoints: List<DataPoint> = emptyList(),
    val heartRateDataPoints: List<DataPoint> = emptyList(),
    val stopRecords: List<StopRecord> = emptyList(),
    val stopsCount: Int = 0,

    // Almacenamos el registro completo del valor crítico
    val minSpo2Record: CriticalValueRecord? = null,
    val minHeartRateRecord: CriticalValueRecord? = null,
    val maxHeartRateRecord: CriticalValueRecord? = null,

    val minuteMarkerData: List<MinuteDataSnapshot> = emptyList(),

    // Control de estado de la prueba y UI
    val mainButtonAction: MainButtonAction = MainButtonAction.START,
    val isConfigPhase: Boolean = true,
    val isTestRunning: Boolean = false,
    val isTestFinished: Boolean = false,
    val preparationDataLoaded: Boolean = false,
    val canNavigateToResults: Boolean = false,

    // Bluetooth
    val bluetoothIconStatus: BluetoothIconStatus = BluetoothIconStatus.GRAY, // Estado inicial por defecto
    val bluetoothStatusMessage: String = "Iniciando servicio...", // Mensaje inicial
    val isAttemptingForceReconnect: Boolean = false, // Para la animación del círculo rojo al pulsar el icono

    // Diálogos
    val showExitConfirmationDialog: Boolean = false,
    val showStopConfirmationDialog: Boolean = false,
    val stopCountdownSeconds: Int = STOP_CONFIRMATION_COUNTDOWN_SECONDS,
    val showMainActionConfirmationDialog: Boolean = false,
    val mainActionConfirmationMessage: String = "",
    val showNavigateToResultsConfirmationDialog: Boolean = false,
    val testFinishedInfoMessage: String? = null,
    val showDeleteLastStopConfirmationDialog: Boolean = false,

    // Mensajes y Navegación
    val userMessage: String? = null,
    val testSummaryDataForNavigation: TestExecutionSummaryData? = null,
)

data class TestExecutionSummaryData(
    // --- Campos de TestPreparationData ---
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
    val isFirstTestForPatient: Boolean,

    // --- Campos específicos de la ejecución ---
    val testActualStartTimeMillis: Long,
    val actualTestDurationMillis: Long,
    val distanceMetersFinal: Float,
    val lapsFinal: Float,
    val trackLengthUsedMeters: Int,
    val minSpo2Record: CriticalValueRecord?,
    val maxHeartRateRecord: CriticalValueRecord?,
    val minHeartRateRecord: CriticalValueRecord?,
    val stopRecords: List<StopRecord>,
    val spo2DataPoints: List<DataPoint>,
    val heartRateDataPoints: List<DataPoint>,
    val minuteReadings: List<MinuteDataSnapshot> = emptyList()
)

@HiltViewModel
class TestExecutionViewModel @Inject constructor(
    private val bluetoothService: BluetoothService,
    private val testStateHolder: TestStateHolder,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TestExecutionUiState())
    val uiState: StateFlow<TestExecutionUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var stopCountdownJob: Job? = null
    private var userMessageClearJob: Job? = null
    private var recoveryDataCaptureJob: Job? = null

    private var currentTestPreparationData: TestPreparationData? = null
    private var testActualStartTimeMillis: Long = 0L

    // --- NUEVAS VARIABLES PARA LA LÓGICA DE TENDENCIA ---
    private var liveDataProcessingJob: Job? = null // Job para el nuevo bucle de procesamiento

    // Listas para almacenar los valores de los sensores para el cálculo de tendencia
    private val spo2ValuesForTrendCalculation = mutableListOf<Int>()
    private val hrValuesForTrendCalculation = mutableListOf<Int>()

    // Contadores para saber cuándo recalcular la tendencia
    private var spo2ReadingsSinceLastTrendCalc = 0
    private var hrReadingsSinceLastTrendCalc = 0

    // Variables para almacenar el último dato válido recibido del sensor
    private var lastValidSpo2FromSensor: Int? = null
    private var lastValidHrFromSensor: Int? = null
    private var lastNoFingerDetectedFromSensor: Boolean? = null // Para manejar la presencia del dedo
    private var lastSignalStrengthFromSensor: Int? = null    // Para manejar la calidad de la señal

    private var userSpo2WarningThreshold = DefaultThresholdValues.SPO2_WARNING_DEFAULT
    private var userSpo2CriticalThreshold = DefaultThresholdValues.SPO2_CRITICAL_DEFAULT
    private var userHrCriticalLowThreshold = DefaultThresholdValues.HR_CRITICAL_LOW_DEFAULT
    private var userHrWarningLowThreshold = DefaultThresholdValues.HR_WARNING_LOW_DEFAULT
    private var userHrWarningHighThreshold = DefaultThresholdValues.HR_WARNING_HIGH_DEFAULT
    private var userHrCriticalHighThreshold = DefaultThresholdValues.HR_CRITICAL_HIGH_DEFAULT

    private val timeFormatter = SimpleDateFormat("mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

    init {
        Log.d("TestExecutionVM", "ViewModel inicializado. Observando datos y conexión Bluetooth.")
        // --- Cargar los umbrales del usuario al inicio ---
        viewModelScope.launch {
            userSpo2WarningThreshold = settingsRepository.spo2WarningThresholdFlow.first()
            userSpo2CriticalThreshold = settingsRepository.spo2CriticalThresholdFlow.first()
            userHrCriticalLowThreshold = settingsRepository.hrCriticalLowThresholdFlow.first()
            userHrWarningLowThreshold = settingsRepository.hrWarningLowThresholdFlow.first()
            userHrWarningHighThreshold = settingsRepository.hrWarningHighThresholdFlow.first()
            userHrCriticalHighThreshold = settingsRepository.hrCriticalHighThresholdFlow.first()

            Log.i("TestExecutionVM", "Umbrales de usuario cargados: " +
                    "SpO2 Warn=$userSpo2WarningThreshold, SpO2 Crit=$userSpo2CriticalThreshold, " +
                    "HR CritLow=$userHrCriticalLowThreshold, HR WarnLow=$userHrWarningLowThreshold, " +
                    "HR WarnHigh=$userHrWarningHighThreshold, HR CritHigh=$userHrCriticalHighThreshold")

            // Ahora que los umbrales están cargados, se puede proceder con el resto de la inicialización
            // que podría depender de ellos (como el estado inicial de los colores).
            // Es importante que esta carga ocurra ANTES de que cualquier lógica use estos umbrales.
        }
        observeRawBluetoothDeviceData()
        startLiveDataProcessingLoop()
        observeBluetoothConnectionStatus()
        // Inicializar el estado BT al arrancar
        _uiState.update { currentState ->
            val (initialIcon, initialMsg) = determineBluetoothVisualStatus(
                connectionStatus = bluetoothService.connectionStatus.value,
                deviceData = bluetoothService.bleDeviceData.value,
                isBluetoothAdapterEnabled = bluetoothService.isBluetoothEnabled(),
            )
            currentState.copy(
                bluetoothIconStatus = initialIcon,
                bluetoothStatusMessage = initialMsg
            )
        }
    }

    /**
     * Observa los datos crudos del sensor tan pronto como llegan del BluetoothService.
     * Solo guarda los últimos valores válidos para ser procesados por el bucle de liveDataProcessingLoop.
     * También actualiza el estado visual inmediato del icono de Bluetooth.
     */
    private fun observeRawBluetoothDeviceData() {
        bluetoothService.bleDeviceData
            .onEach { data: BleDeviceData ->
                // Log.v("TestExecutionVM", "RAW Data: SpO2=${data.spo2}, HR=${data.heartRate}, NoFinger=${data.noFingerDetected}, Signal=${data.signalStrength}")

                // Guarda los últimos datos recibidos del sensor
                lastValidSpo2FromSensor = data.spo2
                lastValidHrFromSensor = data.heartRate
                lastNoFingerDetectedFromSensor = data.noFingerDetected
                lastSignalStrengthFromSensor = data.signalStrength

                // Determinar si el dedo está presente BASADO EN LOS DATOS CRUDOS MÁS RECIENTES
                val isFingerCurrentlyPresent = !(data.noFingerDetected == true ||
                        data.signalStrength == NO_FINGER_OR_RECALIBRATING_SIGNAL ||
                        data.spo2 == null || data.spo2 <= 0 ||
                        data.heartRate == null || data.heartRate <= 0)

                val displaySpo2 = if (isFingerCurrentlyPresent) data.spo2 else null
                val displayHr = if (isFingerCurrentlyPresent) data.heartRate else null

                _uiState.update { currentState ->
                    val (newIconStatus, newStatusMessage) = determineBluetoothVisualStatus(
                        connectionStatus = bluetoothService.connectionStatus.value,
                        deviceData = data, // Usar los datos más recientes para el estado visual
                        isBluetoothAdapterEnabled = bluetoothService.isBluetoothEnabled()
                    )
                    currentState.copy(
                        currentSpo2 = displaySpo2,
                        currentHeartRate = displayHr,
                        isSensorFingerPresent = isFingerCurrentlyPresent, // Actualización inmediata
                        currentSignalStrength = data.signalStrength,   // Actualización inmediata
                        bluetoothIconStatus = if (currentState.isAttemptingForceReconnect) BluetoothIconStatus.CONNECTING else newIconStatus,
                        bluetoothStatusMessage = if (currentState.isAttemptingForceReconnect) "Reconectando..." else newStatusMessage
                        // currentSpo2, currentHeartRate, trends y statusColors se actualizan en startLiveDataProcessingLoop
                    )
                }
            }
            .catch { e -> Log.e("TestExecutionVM", "Error en flow BleDeviceData: ${e.message}", e) }
            .launchIn(viewModelScope)
    }

    /**
     * Inicia un bucle que procesa los datos del sensor (previamente guardados por observeRawBluetoothDeviceData)
     * a un ritmo fijo (ej. 1 segundo). Aquí se calculan las tendencias y se actualiza la UI
     * con los valores de SpO2, HR, tendencias y sus colores de estado.
     */
    private fun startLiveDataProcessingLoop() {
        liveDataProcessingJob?.cancel()
        liveDataProcessingJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L) // Procesar cada segundo

                val spo2ForProcessing = lastValidSpo2FromSensor // Usa los últimos valores guardados
                val hrForProcessing = lastValidHrFromSensor
                val isFingerPresentCurrently = _uiState.value.isSensorFingerPresent


                _uiState.update { currentState ->
                    var newSpo2Trend: Trend = currentState.spo2Trend
                    var newHrTrend: Trend = currentState.heartRateTrend
                    var newSpo2StatusColor: StatusColor = currentState.spo2StatusColor
                    var newHrStatusColor: StatusColor = currentState.heartRateStatusColor

                    // Solo procesamos y mostramos valores si el dedo está presente
                    if (isFingerPresentCurrently && spo2ForProcessing != null && hrForProcessing != null && spo2ForProcessing > 0 && hrForProcessing > 0) {
                        newSpo2StatusColor = determineStatusColorSpo2(spo2ForProcessing)
                        newHrStatusColor = determineStatusColorHr(hrForProcessing)

                        // Calcular tendencias siempre
                        if (true) {
                            // Lógica de TENDENCIA para SpO2
                            spo2ValuesForTrendCalculation.add(spo2ForProcessing)
                            while (spo2ValuesForTrendCalculation.size > TREND_WINDOW_SIZE_FOR_CALC) {
                                if (spo2ValuesForTrendCalculation.isNotEmpty()) spo2ValuesForTrendCalculation.removeAt(0)
                            }
                            spo2ReadingsSinceLastTrendCalc++
                            if (spo2ReadingsSinceLastTrendCalc >= NEW_VALUES_THRESHOLD_FOR_TREND_CALC) {
                                if (spo2ValuesForTrendCalculation.size == TREND_WINDOW_SIZE_FOR_CALC) {
                                    newSpo2Trend = calculateTrendFromAverageOfLastThree(spo2ValuesForTrendCalculation.toList())
                                }
                                spo2ReadingsSinceLastTrendCalc = 0
                            }

                            // Lógica de TENDENCIA para Heart Rate
                            hrValuesForTrendCalculation.add(hrForProcessing)
                            while (hrValuesForTrendCalculation.size > TREND_WINDOW_SIZE_FOR_CALC) {
                                if (hrValuesForTrendCalculation.isNotEmpty()) hrValuesForTrendCalculation.removeAt(0)
                            }
                            hrReadingsSinceLastTrendCalc++
                            if (hrReadingsSinceLastTrendCalc >= NEW_VALUES_THRESHOLD_FOR_TREND_CALC) {
                                if (hrValuesForTrendCalculation.size == TREND_WINDOW_SIZE_FOR_CALC) {
                                    newHrTrend = calculateTrendFromAverageOfLastThree(hrValuesForTrendCalculation.toList())
                                }
                                hrReadingsSinceLastTrendCalc = 0
                            }
                        }
                    } else {
                        // No hay dedo o datos inválidos del sensor: limpiar valores y tendencias
                        newSpo2Trend = Trend.STABLE
                        newHrTrend = Trend.STABLE
                        newSpo2StatusColor = StatusColor.UNKNOWN
                        newHrStatusColor = StatusColor.UNKNOWN

                        spo2ValuesForTrendCalculation.clear()
                        hrValuesForTrendCalculation.clear()
                        spo2ReadingsSinceLastTrendCalc = 0
                        hrReadingsSinceLastTrendCalc = 0
                    }

                    currentState.copy(
                        spo2Trend = newSpo2Trend,
                        heartRateTrend = newHrTrend,
                        spo2StatusColor = newSpo2StatusColor,
                        heartRateStatusColor = newHrStatusColor
                        // isSensorFingerPresent ya se actualiza en observeRawBluetoothDeviceData
                    )
                }
            }
        }
    }

    private fun calculateTrendFromAverageOfLastThree(currentValues: List<Int>): Trend {
        // Asegúrate de que TREND_WINDOW_SIZE_FOR_CALC sea 6 para esta lógica
        if (currentValues.size < TREND_WINDOW_SIZE_FOR_CALC) {
            return Trend.STABLE // No hay suficientes datos, o estado inicial
        }

        // Toma los últimos 6 valores. No es necesario takeLast si ya controlas el tamaño a 6.
        // val lastSixValues = currentValues.takeLast(TREND_WINDOW_SIZE_FOR_CALC)
        // Directamente usar currentValues ya que nos aseguramos que tiene tamaño 6

        val previousThree = currentValues.subList(0, 3)
        val currentThree = currentValues.subList(3, 6)

        val averagePreviousThree = previousThree.average()
        val averageCurrentThree = currentThree.average()

        // El umbral de 0.5 es un ejemplo, ajústalo si es necesario
        // TestResultsViewModel no parece usar un umbral aquí, solo comparación directa.
        // Mantenlo consistente con TestResultsViewModel.
        return when {
            averageCurrentThree > averagePreviousThree -> Trend.UP
            averageCurrentThree < averagePreviousThree -> Trend.DOWN
            else -> Trend.STABLE
        }
    }

    private fun determineBluetoothVisualStatus(
        connectionStatus: BleConnectionStatus,
        deviceData: BleDeviceData,
        isBluetoothAdapterEnabled: Boolean,
    ): Pair<BluetoothIconStatus, String> {

        // CASO 1: Bluetooth del teléfono/tablet DESACTIVADO
        if (!isBluetoothAdapterEnabled) {
            return Pair(BluetoothIconStatus.RED, "Error de conexión, active bluetooth")
        }

        return when (connectionStatus) {
            BleConnectionStatus.SUBSCRIBED -> {
                if (deviceData.noFingerDetected == true ||
                    deviceData.signalStrength == NO_FINGER_OR_RECALIBRATING_SIGNAL ||
                    deviceData.spo2 == null || deviceData.spo2 <= 0 ||
                    deviceData.heartRate == null || deviceData.heartRate <= 0) {
                    Pair(BluetoothIconStatus.YELLOW, "Coloque el dedo en el sensor")
                } else if (deviceData.signalStrength != null && deviceData.signalStrength <= POOR_SIGNAL_THRESHOLD) {
                    Pair(BluetoothIconStatus.YELLOW, "Sensor: señal baja")
                } else {
                    Pair(BluetoothIconStatus.GREEN, "Sensor conectado") // Manteniendo tu mensaje original por ahora
                }
            }
            BleConnectionStatus.CONNECTED -> {
                if (deviceData.noFingerDetected == true ||
                    deviceData.signalStrength == NO_FINGER_OR_RECALIBRATING_SIGNAL ||
                    deviceData.spo2 == null || deviceData.spo2 == 0 ||
                    deviceData.heartRate == null || deviceData.heartRate == 0) {
                    Pair(BluetoothIconStatus.YELLOW, "Sensor no listo / sin dedo")
                } else {
                    Pair(BluetoothIconStatus.YELLOW, "Conectado (parcial)")
                }
            }
            BleConnectionStatus.CONNECTING, BleConnectionStatus.RECONNECTING -> {
                Pair(BluetoothIconStatus.CONNECTING, "Conectando...")
            }
            BleConnectionStatus.IDLE,
            BleConnectionStatus.SCANNING,
            BleConnectionStatus.DISCONNECTED_BY_USER,
            BleConnectionStatus.DISCONNECTED_ERROR,
            BleConnectionStatus.ERROR_GENERIC,
            BleConnectionStatus.ERROR_DEVICE_NOT_FOUND,
            BleConnectionStatus.ERROR_SERVICE_NOT_FOUND,
            BleConnectionStatus.ERROR_CHARACTERISTIC_NOT_FOUND,
            BleConnectionStatus.ERROR_SUBSCRIBE_FAILED -> {
                Pair(BluetoothIconStatus.RED, "Pérdida de conexión, pulse para reconectar")
            }
            else -> {
                Log.w("TestExecutionVM", "Estado de conexión BT no manejado: $connectionStatus. Defecto a GRIS.")
                Pair(BluetoothIconStatus.GRAY, "Estado BT: ${connectionStatus.name}")
            }
        }
    }

    private fun observeBluetoothConnectionStatus() {
        bluetoothService.connectionStatus
            .onEach { status: BleConnectionStatus ->
                Log.i("TestExecutionVM", "Estado de conexión Bluetooth: $status")
                val currentDeviceData = bluetoothService.bleDeviceData.value
                _uiState.update { currentState ->
                    val (newIconStatus, newStatusMessage) = determineBluetoothVisualStatus(
                        connectionStatus = status,
                        deviceData = currentDeviceData,
                        isBluetoothAdapterEnabled = bluetoothService.isBluetoothEnabled()
                    )

                    var userMessageUpdate: String? = currentState.userMessage

                    if (currentState.isTestRunning) {
                        when (status) {
                            BleConnectionStatus.DISCONNECTED_ERROR,
                            BleConnectionStatus.ERROR_BLUETOOTH_DISABLED,
                            BleConnectionStatus.DISCONNECTED_BY_USER -> {
                                userMessageUpdate = "¡Conexión Bluetooth perdida!"
                            }

                            BleConnectionStatus.RECONNECTING -> {
                                userMessageUpdate = "Intentando reconectar con el dispositivo..."
                            }

                            BleConnectionStatus.SUBSCRIBED, BleConnectionStatus.CONNECTED -> {
                                if (currentState.userMessage?.contains("perdida", ignoreCase = true) == true ||
                                    currentState.userMessage?.contains("reconectar", ignoreCase = true) == true
                                ) {
                                    userMessageUpdate = "Conexión restaurada."

                                }
                            }

                            else -> {
                            }
                        }
                    } else if (currentState.isConfigPhase || currentState.isTestFinished) {
                        if (!status.isConsideredConnectedOrSubscribed()) {
                            userMessageUpdate = when (status) {
                                BleConnectionStatus.ERROR_BLUETOOTH_DISABLED -> "Bluetooth está desactivado. Actívelo para conectar."
                                else -> currentState.userMessage
                            }
                        } else {
                            if (currentState.userMessage?.contains("Bluetooth", ignoreCase = true) == true ||
                                currentState.userMessage?.contains("conexión", ignoreCase = true) == true
                            ) {
                                userMessageUpdate = null
                            }
                        }
                    }

                    currentState.copy(
                        userMessage = userMessageUpdate,
                        bluetoothIconStatus = if (currentState.isAttemptingForceReconnect) BluetoothIconStatus.CONNECTING else newIconStatus,
                        bluetoothStatusMessage = if (currentState.isAttemptingForceReconnect) "Reconectando..." else newStatusMessage,
                    )
                }
            }
            .catch { e ->
                Log.e("TestExecutionVM", "Error en el flow de BleConnectionStatus: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        userMessage = "Error interno procesando estado Bluetooth.",
                        bluetoothIconStatus = BluetoothIconStatus.RED,
                        bluetoothStatusMessage = "Error interno"
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun BleConnectionStatus.isErrorStatus(): Boolean {
        return this == BleConnectionStatus.DISCONNECTED_ERROR ||
                this == BleConnectionStatus.ERROR_BLUETOOTH_DISABLED ||
                this == BleConnectionStatus.ERROR_PERMISSIONS ||
                this == BleConnectionStatus.ERROR_DEVICE_NOT_FOUND ||
                this == BleConnectionStatus.ERROR_SERVICE_NOT_FOUND ||
                this == BleConnectionStatus.ERROR_CHARACTERISTIC_NOT_FOUND ||
                this == BleConnectionStatus.ERROR_SUBSCRIBE_FAILED ||
                this == BleConnectionStatus.ERROR_GENERIC
    }

    private fun BleConnectionStatus.isConsideredConnectedOrSubscribed(): Boolean {
        return this == BleConnectionStatus.CONNECTED || this == BleConnectionStatus.SUBSCRIBED
    }

    fun initializeTest(preparationData: TestPreparationData) {
        Log.d("TestExecutionVM", "Inicializando prueba con datos para: ${preparationData.patientFullName}")
        cancelTestInProgressActivities(restartLiveDataProcessing = false)
        currentTestPreparationData = preparationData
        testStateHolder.resetRecoveryState()

        // Obtener datos actuales del sensor desde el servicio para el estado inicial de la UI
        val currentSensorData = bluetoothService.bleDeviceData.value
        val currentConnectionStatus = bluetoothService.connectionStatus.value
        val isBtEnabled = bluetoothService.isBluetoothEnabled()

        val (initialIconStatus, initialStatusMessage) = determineBluetoothVisualStatus(
            connectionStatus = currentConnectionStatus,
            deviceData = currentSensorData,
            isBluetoothAdapterEnabled = isBtEnabled
        )

        val isFingerPresentNow = !(currentSensorData.noFingerDetected == true ||
                currentSensorData.signalStrength == NO_FINGER_OR_RECALIBRATING_SIGNAL ||
                currentSensorData.spo2 == null || currentSensorData.spo2 <= 0 ||
                currentSensorData.heartRate == null || currentSensorData.heartRate <= 0)

        val initialSpo2FromSensor = if (isFingerPresentNow) currentSensorData.spo2 else null
        val initialHrFromSensor = if (isFingerPresentNow) currentSensorData.heartRate else null

        val displaySpo2 = preparationData.basalSpo2.takeIf { it in 1..100 } ?: initialSpo2FromSensor
        val displayHr = preparationData.basalHeartRate.takeIf { it > 0 } ?: initialHrFromSensor

        spo2ValuesForTrendCalculation.clear()
        hrValuesForTrendCalculation.clear()
        spo2ReadingsSinceLastTrendCalc = 0
        hrReadingsSinceLastTrendCalc = 0

        _uiState.value = TestExecutionUiState(
            patientId = preparationData.patientId,
            patientFullName = preparationData.patientFullName,
            trackLength = _uiState.value.trackLength.takeIf { it > 0 } ?: TRACK_LENGTH,
            currentTimeMillis = 0L,
            currentTimeFormatted = formatTimeDisplay(0L),
            laps = 0.0f,
            lapsInputText = formatLapsDisplay(0.0f),
            distanceMeters = 0f,

            currentSpo2 = displaySpo2,
            currentHeartRate = displayHr,
            isSensorFingerPresent = isFingerPresentNow,
            currentSignalStrength = currentSensorData.signalStrength,
            spo2StatusColor = displaySpo2?.let { determineStatusColorSpo2(it) } ?: StatusColor.UNKNOWN,
            heartRateStatusColor = displayHr?.let { determineStatusColorHr(it) } ?: StatusColor.UNKNOWN,
            spo2Trend = Trend.STABLE,
            heartRateTrend = Trend.STABLE,

            bluetoothIconStatus = initialIconStatus,
            bluetoothStatusMessage = initialStatusMessage,
            isAttemptingForceReconnect = false,

            // Datos acumulados
            spo2DataPoints = emptyList(),
            heartRateDataPoints = emptyList(),
            stopRecords = emptyList(),
            stopsCount = 0,
            minSpo2Record = null,
            minHeartRateRecord = null,
            maxHeartRateRecord = null,
            minuteMarkerData = emptyList(),

            // Control de estado de la prueba y UI
            mainButtonAction = MainButtonAction.START,
            isConfigPhase = true,
            isTestRunning = false,
            isTestFinished = false,
            preparationDataLoaded = true,
            canNavigateToResults = false,


            // Diálogos
            showExitConfirmationDialog = false,
            showStopConfirmationDialog = false,
            stopCountdownSeconds = STOP_CONFIRMATION_COUNTDOWN_SECONDS,
            showMainActionConfirmationDialog = false,
            mainActionConfirmationMessage = "",
            showNavigateToResultsConfirmationDialog = false,
            testFinishedInfoMessage = null,
            showDeleteLastStopConfirmationDialog = false,

            // Mensajes y Navegación
            userMessage = null,
            testSummaryDataForNavigation = null
        )
        startLiveDataProcessingLoop()
        Log.d("TestExecutionVM", "ViewModel inicializado. UI State: ${_uiState.value}")
    }

    private fun startTestExecution() {
        if (currentTestPreparationData == null) {
            _uiState.update { it.copy(userMessage = "Error: Datos de preparación no encontrados.") }
            clearUserMessageAfterDelay()
            return
        }
        if (_uiState.value.trackLength <= 0) {
            _uiState.update { it.copy(userMessage = "Error: La longitud de pista debe ser mayor a 0.") }
            clearUserMessageAfterDelay()
            return
        }

        val currentBleStatus = bluetoothService.connectionStatus.value
        if (currentBleStatus != BleConnectionStatus.SUBSCRIBED) {
            val message = when (currentBleStatus) {
                BleConnectionStatus.DISCONNECTED_ERROR,
                BleConnectionStatus.DISCONNECTED_BY_USER -> "Pulsioxímetro desconectado."
                BleConnectionStatus.ERROR_BLUETOOTH_DISABLED -> "Bluetooth está desactivado."
                BleConnectionStatus.ERROR_PERMISSIONS -> "Faltan permisos de Bluetooth."
                BleConnectionStatus.SCANNING, BleConnectionStatus.CONNECTING,
                BleConnectionStatus.RECONNECTING -> "Esperando conexión con el pulsioxímetro (${currentBleStatus.name})..."
                BleConnectionStatus.CONNECTED -> "Pulsioxímetro conectado, esperando datos..."
                else -> "Pulsioxímetro no está listo (${currentBleStatus.name}). Verifique la conexión."
            }
            _uiState.update { it.copy(userMessage = "No se puede iniciar: $message") }
            clearUserMessageAfterDelay(4000)
            return
        }

        // Usar el isSensorFingerPresent del _uiState, que es el más actualizado
        if (!_uiState.value.isSensorFingerPresent) {
            _uiState.update { it.copy(userMessage = "Sensor: Sin dedo o datos no válidos. Coloque bien el dedo.") }
            clearUserMessageAfterDelay(3500)
            return
        }
        // Tomar los valores actuales del _uiState que ya deberían estar actualizados por el loop
        val initialSpo2ForTest = _uiState.value.currentSpo2
        val initialHrForTest = _uiState.value.currentHeartRate

        if (initialSpo2ForTest == null || initialSpo2ForTest <= 0 || initialHrForTest == null || initialHrForTest <= 0) {
            _uiState.update { it.copy(userMessage = "Sensor: Datos no válidos. Verifique el sensor.") }
            clearUserMessageAfterDelay(3500)
            return
        }

        testActualStartTimeMillis = System.currentTimeMillis()

        spo2ValuesForTrendCalculation.clear()
        hrValuesForTrendCalculation.clear()
        spo2ReadingsSinceLastTrendCalc = 0
        hrReadingsSinceLastTrendCalc = 0

        _uiState.update {
            it.copy(
                isConfigPhase = false,
                isTestRunning = true,
                isTestFinished = false,
                mainButtonAction = MainButtonAction.RESTART_DURING_TEST,
                currentTimeMillis = 0L,
                currentTimeFormatted = formatTimeDisplay(0L),
                spo2DataPoints = if (initialSpo2ForTest != null && initialSpo2ForTest > 0) listOf(DataPoint(0L, initialSpo2ForTest.toFloat(), 0f)) else emptyList(),
                heartRateDataPoints = if (initialHrForTest != null && initialHrForTest > 0) listOf(DataPoint(0L, initialHrForTest.toFloat(), 0f)) else emptyList(),
                minSpo2Record = null,
                minHeartRateRecord = null,
                maxHeartRateRecord = null,
                stopRecords = emptyList(),
                stopsCount = 0,
                laps = 0.0f,
                lapsInputText = formatLapsDisplay(0.0f),
                distanceMeters = 0.0f,
                userMessage = "Prueba iniciada.",
                testSummaryDataForNavigation = null,
                showMainActionConfirmationDialog = false,
                testFinishedInfoMessage = null,
                canNavigateToResults = false,
                minuteMarkerData = emptyList()
            )
        }
        startTimerAndDataCollection()
        clearUserMessageAfterDelay()
        Log.i("TestExecutionVM", "Prueba iniciada con SpO2: $initialSpo2ForTest, HR: $initialHrForTest. Tiempo Epoch: $testActualStartTimeMillis")
    }

    // --- Lógica del Botón Principal (Start/Restart/Reinitialize) ---
    fun onMainButtonClicked() {
        when (_uiState.value.mainButtonAction) {
            MainButtonAction.START -> startTestExecution()
            MainButtonAction.RESTART_DURING_TEST -> requestRestartTestConfirmation()
            MainButtonAction.REINITIALIZE_AFTER_TEST -> requestReinitializeTestConfirmation()
        }
    }

    private fun requestRestartTestConfirmation() { // Para el botón RESTART (antes START en modo running)
        if (!_uiState.value.isTestRunning) return
        _uiState.update {
            it.copy(
                showMainActionConfirmationDialog = true,
                mainActionConfirmationMessage = RESTART_TEST_CONFIRMATION_MESSAGE
            )
        }
    }

    fun confirmRestartTestAndReturnToConfig() { // Acción del botón "RESTART"
        timerJob?.cancel()
        recoveryDataCaptureJob?.cancel()

        spo2ValuesForTrendCalculation.clear()
        hrValuesForTrendCalculation.clear()
        spo2ReadingsSinceLastTrendCalc = 0
        hrReadingsSinceLastTrendCalc = 0

        _uiState.update {
            val patientData = currentTestPreparationData
            it.copy(
                isConfigPhase = true,
                isTestRunning = false,
                isTestFinished = false,
                mainButtonAction = MainButtonAction.START,
                currentTimeMillis = 0L,
                currentTimeFormatted = formatTimeDisplay(0L),
                laps = 0.0f,
                lapsInputText = formatLapsDisplay(0.0f),
                distanceMeters = 0f,
                spo2Trend = Trend.STABLE,
                heartRateTrend = Trend.STABLE,
                spo2DataPoints = emptyList(),
                heartRateDataPoints = emptyList(),
                stopRecords = emptyList(),
                stopsCount = 0,
                minSpo2Record = null,
                minHeartRateRecord = null,
                maxHeartRateRecord = null,
                showMainActionConfirmationDialog = false,
                userMessage = "Prueba reiniciada. Listo para comenzar.",
                testSummaryDataForNavigation = null,
                testFinishedInfoMessage = null,
                canNavigateToResults = false,
                minuteMarkerData = emptyList()
            )
        }
        clearUserMessageAfterDelay()
    }

    private fun requestReinitializeTestConfirmation() { // Para el botón REINICIAR (después de finalizar)
        if (!_uiState.value.isTestFinished) return
        _uiState.update {
            it.copy(
                showMainActionConfirmationDialog = true,
                mainActionConfirmationMessage = REINITIALIZE_TEST_CONFIRMATION_MESSAGE
            )
        }
    }

    // confirmReinitializeTest es la acción del diálogo "REINICIAR"
    fun confirmReinitializeTestToConfig() { // Renombrada para claridad
        _uiState.update { it.copy(showMainActionConfirmationDialog = false) }
        currentTestPreparationData?.let { prepData ->
            initializeTest(prepData)
        }
        _uiState.update { it.copy(userMessage = "Pantalla reiniciada. Lista para nueva configuración.") }
        clearUserMessageAfterDelay()
    }

    fun dismissMainActionConfirmationDialog() {
        _uiState.update {
            it.copy(
                showMainActionConfirmationDialog = false,
                mainActionConfirmationMessage = ""
            )
        }
    }

    private fun finishTestExecution(testCompletedNormally: Boolean) {
        timerJob?.cancel()
        stopCountdownJob?.cancel() // Cancelar el countdown de stop si estaba activo
        val finalMessage = if (testCompletedNormally) TEST_COMPLETED_INFO_MESSAGE else "Prueba detenida por el usuario."

        // 1. INICIA EL TIMER DE 60 SEGUNDOS EN SEGUNDO PLANO
        recoveryDataCaptureJob?.cancel()
        recoveryDataCaptureJob = viewModelScope.launch {
            Log.d("TestExecutionVM", "Temporizador de recuperación de 60s iniciado.")
            delay(60000L) // Esperar 1 minuto

            // Capturar los ÚLTIMOS datos disponibles del sensor después del delay
            val recoverySensorData = bluetoothService.bleDeviceData.value
            // Usar una lógica similar a isSensorFingerPresent para validar datos de recuperación
            val isRecoveryFingerPresent = !(recoverySensorData.noFingerDetected == true ||
                    recoverySensorData.signalStrength == NO_FINGER_OR_RECALIBRATING_SIGNAL ||
                    recoverySensorData.spo2 == null || recoverySensorData.spo2 <= 0 ||
                    recoverySensorData.heartRate == null || recoverySensorData.heartRate <= 0)

            val validSpo2 = if(isRecoveryFingerPresent) recoverySensorData.spo2 else null
            val validHr = if(isRecoveryFingerPresent) recoverySensorData.heartRate else null

            testStateHolder.postRecoveryData(
                RecoveryData(
                    spo2 = validSpo2,
                    hr = validHr,
                    isRecoveryPeriodOver = true,
                    wasDataCapturedDuringPeriod = validSpo2 != null && validHr != null
                )
            )
            if (validSpo2 != null && validHr != null) {
                Log.d("TestExecutionVM", "Datos de recuperación emitidos: SpO2=$validSpo2, HR=$validHr.")
            } else {
                Log.w("TestExecutionVM", "No se pudieron capturar datos de recuperación válidos tras 1 min.")
            }
        }

        _uiState.update {
            it.copy(
                isTestRunning = false,
                isTestFinished = true,
                mainButtonAction = MainButtonAction.REINITIALIZE_AFTER_TEST,
                testFinishedInfoMessage = finalMessage,
                canNavigateToResults = true,
                showStopConfirmationDialog = false
            )
        }
    }


    // Para el diálogo informativo que aparece al finalizar la prueba
    fun dismissTestFinishedInfoDialog() {
        _uiState.update { it.copy(testFinishedInfoMessage = null) }
        // El usuario puede ahora usar el botón de "Navegar a Resultados" o "Reiniciar"
    }

    private fun startTimerAndDataCollection() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var elapsedMillis = 0L

            // Variables para rastrear el SpO2 mínimo y FC máximo acumulativos
            var currentOverallMinSpo2: Int? = null
            var currentOverallMaxHr: Int? = null
            val minuteSnapshots = mutableListOf<MinuteDataSnapshot>()

            // Añadir el primer punto de datos si la prueba acaba de empezar y hay datos válidos
            // Esto asegura que el gráfico comience desde el tiempo 0 si los datos son válidos.
            val initialState = _uiState.value
            if (elapsedMillis == 0L && initialState.isTestRunning && initialState.isSensorFingerPresent) {
                if (initialState.currentSpo2 != null && initialState.currentSpo2 > 0) {
                    // No necesitamos añadirlo aquí si ya lo hicimos en startTestExecution.
                    // Lo importante es que _uiState.spo2DataPoints y heartRateDataPoints se inicialicen bien.
                }
            }

            while (isActive && _uiState.value.isTestRunning && elapsedMillis < TEST_DURATION_MILLIS) {
                delay(1000) // Procesamos cada segundo
                val previousTimeMillis = elapsedMillis
                elapsedMillis += 1000

                _uiState.update { currentState ->
                    if (!currentState.isTestRunning) return@update currentState

                    val currentSpo2Val = currentState.currentSpo2 // Ya actualizado por liveDataProcessingLoop
                    val currentHrVal = currentState.currentHeartRate // Ya actualizado por liveDataProcessingLoop
                    val sensorFingerPresent = currentState.isSensorFingerPresent
                    val currentDistance = currentState.distanceMeters

                    var newSpo2DataPoints = currentState.spo2DataPoints
                    var newHrDataPoints = currentState.heartRateDataPoints
                    // Los récords globales min/max de la prueba ya se manejan, los mantenemos
                    var newMinSpo2RecordOverallTest = currentState.minSpo2Record
                    var newMinHrRecordOverallTest = currentState.minHeartRateRecord
                    var newMaxHrRecordOverallTest = currentState.maxHeartRateRecord

                    // --- LÓGICA DE ACTUALIZACIÓN DE DATOS POR SEGUNDO ---
                    if (sensorFingerPresent) {
                        if (currentSpo2Val != null && currentSpo2Val in 1..100) {
                            newSpo2DataPoints = (currentState.spo2DataPoints + DataPoint(elapsedMillis, currentSpo2Val.toFloat(), currentDistance)).takeLast(360) // Limitar tamaño si es necesario
                            // Actualizar SpO2 mínimo global de la prueba
                            if (newMinSpo2RecordOverallTest == null || currentSpo2Val < newMinSpo2RecordOverallTest.value) {
                                newMinSpo2RecordOverallTest = CriticalValueRecord(currentSpo2Val, elapsedMillis, currentDistance)
                            }
                            // Actualizar SpO2 mínimo para los snapshots por minuto
                            if (currentOverallMinSpo2 == null || currentSpo2Val < currentOverallMinSpo2!!) {
                                currentOverallMinSpo2 = currentSpo2Val
                            }
                        }
                        if (currentHrVal != null && currentHrVal > 0) {
                            newHrDataPoints = (currentState.heartRateDataPoints + DataPoint(elapsedMillis, currentHrVal.toFloat(), currentDistance)).takeLast(360)
                            // Actualizar HR mínimo/máximo global de la prueba
                            if (newMinHrRecordOverallTest == null || currentHrVal < newMinHrRecordOverallTest.value) {
                                newMinHrRecordOverallTest = CriticalValueRecord(currentHrVal, elapsedMillis, currentDistance)
                            }
                            if (newMaxHrRecordOverallTest == null || currentHrVal > newMaxHrRecordOverallTest.value) {
                                newMaxHrRecordOverallTest = CriticalValueRecord(currentHrVal, elapsedMillis, currentDistance)
                            }
                            // Actualizar FC máximo para los snapshots por minuto
                            if (currentOverallMaxHr == null || currentHrVal > currentOverallMaxHr!!) {
                                currentOverallMaxHr = currentHrVal
                            }
                        }
                    } else {
                        // Si no hay dedo, no añadimos DataPoints nuevos con valores null o cero.
                        // Los gráficos simplemente no se actualizarán para este segundo.
                        // Los valores min/max globales y por minuto tampoco se actualizarían con datos inválidos.
                        Log.v("TimerDataCollection", "No hay dedo o datos inválidos en el segundo $elapsedMillis. No se añaden DataPoints.")
                    }

                    // --- LÓGICA DE SNAPSHOT POR MINUTO ---
                    val currentMinute = (elapsedMillis / 60000L).toInt()
                    val previousMinute = (previousTimeMillis / 60000L).toInt()

                    // Si hemos cruzado un límite de minuto (y no es el minuto 0)
                    if (currentMinute > previousMinute && currentMinute > 0 && currentMinute <= 6) {
                        // Y si aún no hemos guardado un snapshot para ESTE minuto
                        if (minuteSnapshots.none { it.minuteMark == currentMinute }) {
                            val snapshot = MinuteDataSnapshot(
                                minuteMark = currentMinute,
                                minSpo2Overall = currentOverallMinSpo2, // El mínimo HASTA AHORA
                                maxHrOverall = currentOverallMaxHr,     // El máximo HASTA AHORA
                                distanceAtMinuteEnd = currentDistance   // Distancia al final de este minuto
                            )
                            minuteSnapshots.add(snapshot)
                            Log.d("TestExecutionVM", "Snapshot Minuto $currentMinute: SpO2Min=${snapshot.minSpo2Overall}, HrMax=${snapshot.maxHrOverall}, Dist=${snapshot.distanceAtMinuteEnd}")
                        }
                    }

                    currentState.copy(
                        currentTimeMillis = elapsedMillis,
                        currentTimeFormatted = formatTimeDisplay(elapsedMillis),
                        spo2DataPoints = newSpo2DataPoints,
                        heartRateDataPoints = newHrDataPoints,
                        minSpo2Record = newMinSpo2RecordOverallTest, // Para el resumen final de toda la prueba
                        minHeartRateRecord = newMinHrRecordOverallTest, // Para el resumen final de toda la prueba
                        maxHeartRateRecord = newMaxHrRecordOverallTest, // Para el resumen final de toda la prueba
                        minuteMarkerData = minuteSnapshots.toList() // Actualizar el estado con la lista de snapshots
                    )
                }

                if (elapsedMillis >= TEST_DURATION_MILLIS) {
                    // Asegurarse de que el último snapshot (minuto 6) se capture si no se hizo exactamente en el tick del segundo
                    val finalMinute = (elapsedMillis / 60000L).toInt()
                    if (finalMinute == 6 && minuteSnapshots.none { it.minuteMark == 6}) {
                        val snapshot = MinuteDataSnapshot(
                            minuteMark = 6,
                            minSpo2Overall = currentOverallMinSpo2,
                            maxHrOverall = currentOverallMaxHr,
                            distanceAtMinuteEnd = _uiState.value.distanceMeters
                        )
                        minuteSnapshots.add(snapshot)
                        _uiState.update { it.copy(minuteMarkerData = minuteSnapshots.toList())}
                        Log.d("TestExecutionVM", "Snapshot Minuto 6 (final): SpO2Min=${snapshot.minSpo2Overall}, HrMax=${snapshot.maxHrOverall}, Dist=${snapshot.distanceAtMinuteEnd}")
                    }
                    finishTestExecution(testCompletedNormally = true)
                }
            }
        }
    }

    fun onStopTestInitiated() {
        if (!_uiState.value.isTestRunning) return // Solo si la prueba está corriendo
        _uiState.update {
            it.copy(
                showStopConfirmationDialog = true,
                stopCountdownSeconds = STOP_CONFIRMATION_COUNTDOWN_SECONDS
            )
        }

        stopCountdownJob?.cancel()
        stopCountdownJob = viewModelScope.launch {
            for (i in STOP_CONFIRMATION_COUNTDOWN_SECONDS downTo 1) {
                // Verificar si el diálogo sigue activo y la prueba corriendo
                if (!_uiState.value.showStopConfirmationDialog || !_uiState.value.isTestRunning) {
                    _uiState.update { it.copy(stopCountdownSeconds = STOP_CONFIRMATION_COUNTDOWN_SECONDS) } // Reset
                    return@launch // Salir si el usuario canceló o la prueba ya no corre
                }
                _uiState.update { it.copy(stopCountdownSeconds = i - 1) }
                delay(1000)
            }
            // Si el countdown llega a 0 y el diálogo sigue activo y la prueba corriendo
            if (_uiState.value.showStopConfirmationDialog && _uiState.value.stopCountdownSeconds <= 0 && _uiState.value.isTestRunning) {
                confirmStopTest() // Confirmar parada automáticamente
            }
        }
    }

    fun cancelStopTest() {
        stopCountdownJob?.cancel()
        _uiState.update {
            it.copy(
                showStopConfirmationDialog = false,
                stopCountdownSeconds = STOP_CONFIRMATION_COUNTDOWN_SECONDS
            )
        } // Reset
    }

    fun confirmStopTest() {
        stopCountdownJob?.cancel() // Cancelar el countdown si se confirma manualmente
        if (_uiState.value.isTestRunning) {
            finishTestExecution(testCompletedNormally = false) // Marcar como no completada normalmente
        }
        // Asegurarse de que el diálogo se cierre, aunque finishTestExecution ya lo hace
        _uiState.update { it.copy(showStopConfirmationDialog = false) }
    }

    // El botón "Continuar a Resultados" en la TopAppBar ahora llama a esta función
    fun onContinueToResultsClicked() {
        if (!_uiState.value.isTestFinished) {
            _uiState.update { it.copy(userMessage = "La prueba debe finalizar para ver los resultados.") }
            clearUserMessageAfterDelay()
            return
        }
        // Llamar directamente a la función que muestra el diálogo de confirmación
        // Esta función también construye el resumen si es necesario.
        requestNavigateToResultsConfirmation()
    }

    fun requestNavigateToResultsConfirmation() {
        if (!_uiState.value.isTestFinished) {
            _uiState.update { it.copy(userMessage = "La prueba debe finalizar antes de ver los resultados.") }
            clearUserMessageAfterDelay()
            return
        }

        val summary = buildTestExecutionSummary()
        if (summary == null) {
            _uiState.update { it.copy(userMessage = "Error al generar el resumen de la prueba.") }
            clearUserMessageAfterDelay()
            return
        }
        _uiState.update {
            it.copy(
                testSummaryDataForNavigation = summary,
                showNavigateToResultsConfirmationDialog = true,
                testFinishedInfoMessage = null // Ocultar el diálogo de "prueba finalizada"
            )
        }
    }

    fun dismissNavigateToResultsConfirmation() {
        _uiState.update {
            it.copy(
                showNavigateToResultsConfirmationDialog = false
            )
        }
    }

    // Cuando el usuario confirma el diálogo de "Navegar a Resultados"
    fun confirmNavigateToResults() {
        if (_uiState.value.testSummaryDataForNavigation == null) {
            _uiState.update {
                it.copy(
                    userMessage = "No hay datos de resumen para navegar.",
                    showNavigateToResultsConfirmationDialog = false
                )
            }
            clearUserMessageAfterDelay()
            return
        }
        _uiState.update {
            it.copy(
                showNavigateToResultsConfirmationDialog = false,
                testFinishedInfoMessage = null // Asegurar que el info dialog esté cerrado
            )
        }
    }

    fun onNavigationToResultsCompleted() {
        _uiState.update { it.copy(testSummaryDataForNavigation = null) }
    }

    fun onAddStop() {
        if (!_uiState.value.isTestRunning) {
            _uiState.update { it.copy(userMessage = "Solo se pueden añadir paradas durante la prueba.") }
            clearUserMessageAfterDelay()
            return
        }

        val currentState = _uiState.value
        val currentTimeMs = currentState.currentTimeMillis
        val spo2AtStop = currentState.currentSpo2
        val hrAtStop = currentState.currentHeartRate

        if (currentState.isSensorFingerPresent && spo2AtStop != null && hrAtStop != null) {
            val newStop = StopRecord(
                stopTimeMillis = currentTimeMs,
                spo2AtStopTime = spo2AtStop,
                heartRateAtStopTime = hrAtStop,
                distanceAtStopTime = currentState.distanceMeters, // <<--- GUARDAR DISTANCIA AQUÍ
                stopTimeFormatted = formatTimeDisplay(currentTimeMs)
            )
            _uiState.update {
                it.copy(
                    stopRecords = it.stopRecords + newStop,
                    stopsCount = it.stopsCount + 1
                )
            }
        } else {
            // Manejar error de que no se puede registrar parada sin datos
            _uiState.update { it.copy(userMessage = "No se puede registrar parada: datos de sensor no válidos.") }
            clearUserMessageAfterDelay()
        }
    }

    fun onTrackLengthChanged(newLengthText: String) {
        // Permitir cambiar solo en fase de configuración O si la prueba ha terminado (para ajustar post-test)
        if (!(_uiState.value.isConfigPhase || _uiState.value.isTestFinished)) {
            _uiState.update { it.copy(userMessage = "Ajustar longitud solo antes o después de la prueba.") }
            clearUserMessageAfterDelay()
            return
        }
        // Validar que solo sean números y hasta 3 dígitos
        if (newLengthText.isEmpty() || newLengthText.matches(Regex("^\\d{1,3}$"))) {
            val newLengthInt = newLengthText.toIntOrNull()

            if (newLengthText.isEmpty()) {
                _uiState.update {
                    it.copy(
                        trackLength = 0,
                        distanceMeters = it.laps * 0f
                    )
                } // Poner a 0 si está vacío
            } else if (newLengthInt != null) {
                if (newLengthInt > 0) {
                    _uiState.update {
                        it.copy(
                            trackLength = newLengthInt,
                            distanceMeters = it.laps * newLengthInt.toFloat() // Recalcular distancia
                        )
                    }
                } else { // newLengthInt es 0
                    _uiState.update {
                        it.copy(
                            trackLength = 0,
                            distanceMeters = it.laps * 0f,
                            userMessage = "La longitud debe ser mayor a 0."
                        )
                    }
                    clearUserMessageAfterDelay()
                }
            }
            // Si newLengthInt es null pero newLengthText no está vacío, es un error de formato que el regex ya debería filtrar,
            // pero por si acaso, no hacemos nada o podríamos mostrar un mensaje.
        }
    }

    fun onLapsInputChanged(newText: String) {
        // Permitir cambiar vueltas si la prueba está corriendo o ha terminado (para ajustes finales)
        if (!(_uiState.value.isTestRunning || _uiState.value.isTestFinished)) return
        // Validar formato: números, opcionalmente un punto decimal y hasta 2 decimales
        if (newText.isEmpty() || newText.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) {
            _uiState.update { it.copy(lapsInputText = newText) } // Actualizar el texto inmediatamente

            val newLapsValue = newText.toFloatOrNull()

            if (newLapsValue != null && newLapsValue >= 0.0f) {
                _uiState.update {
                    it.copy(
                        laps = newLapsValue,
                        distanceMeters = newLapsValue * it.trackLength.toFloat() // Recalcular distancia
                    )
                }
            } else if (newText.isEmpty()) {
                // Si el texto está vacío, resetear vueltas y distancia a 0
                _uiState.update {
                    it.copy(
                        laps = 0.0f,
                        distanceMeters = 0.0f
                    )
                }
            }
            // Si newLapsValue es null pero el texto no está vacío (ej. solo ".")
            // no actualizamos 'laps' ni 'distanceMeters' hasta que sea un número válido.
            // El lapsInputText ya refleja la entrada parcial.
        }
    }

    fun formatLapsDisplay(laps: Float): String {
        // Asegurar que siempre se muestren dos decimales y el valor no sea negativo.
        return String.format(Locale.US, "%.2f", laps.coerceAtLeast(0.0f))
    }

    fun onIncrementLaps() {
        if (!(_uiState.value.isTestRunning || _uiState.value.isTestFinished)) return

        val currentLaps = _uiState.value.laps
        val newLaps = currentLaps + 1.0f

        _uiState.update {
            it.copy(
                laps = newLaps,
                lapsInputText = formatLapsDisplay(newLaps), // Actualizar el texto formateado
                distanceMeters = newLaps * it.trackLength.toFloat()
            )
        }
    }

    fun onDecrementLaps() {
        if (!(_uiState.value.isTestRunning || _uiState.value.isTestFinished)) return

        val currentLaps = _uiState.value.laps
        val newLaps = (currentLaps - 1.0f).coerceAtLeast(0.0f) // No permitir valores negativos

        _uiState.update {
            it.copy(
                laps = newLaps,
                lapsInputText = formatLapsDisplay(newLaps), // Actualizar el texto formateado
                distanceMeters = newLaps * it.trackLength.toFloat()
            )
        }
    }

    fun requestDeleteLastStopConfirmation() {
        if (_uiState.value.stopRecords.isNotEmpty() && (_uiState.value.isTestRunning || _uiState.value.isTestFinished)) {
            _uiState.update { it.copy(showDeleteLastStopConfirmationDialog = true) }
        } else if (_uiState.value.stopRecords.isEmpty()) {
            _uiState.update { it.copy(userMessage = "No hay paradas para eliminar.") }
            clearUserMessageAfterDelay()
        } else {
            _uiState.update { it.copy(userMessage = "Las paradas solo se pueden gestionar durante o después de la prueba.") }
            clearUserMessageAfterDelay()
        }
    }

    fun dismissDeleteLastStopConfirmation() {
        _uiState.update { it.copy(showDeleteLastStopConfirmationDialog = false) }
    }

    fun confirmDeleteLastStop() {
        _uiState.update { it.copy(showDeleteLastStopConfirmationDialog = false) } // Cerrar diálogo primero
        if (_uiState.value.stopRecords.isNotEmpty()) {
            _uiState.update {
                val updatedRecords = it.stopRecords.dropLast(1)
                it.copy(
                    stopsCount = updatedRecords.size,
                    stopRecords = updatedRecords
                )
            }
        }
    }

    private fun clearUserMessageAfterDelay(delayMillis: Long = 3000) {
        userMessageClearJob?.cancel()
        userMessageClearJob = viewModelScope.launch {
            delay(delayMillis)
            _uiState.update { it.copy(userMessage = null) }
        }
    }

    // --- Navegación y Salida de Pantalla ---
    fun requestExitConfirmation() {
        _uiState.update { it.copy(showExitConfirmationDialog = true) }
    }

    fun dismissExitConfirmation() {
        _uiState.update { it.copy(showExitConfirmationDialog = false) }
    }

    fun confirmExitTest() {
        _uiState.update { it.copy(showExitConfirmationDialog = false) } // Cerrar el diálogo
        cancelTestInProgressActivities(restartLiveDataProcessing = false)
    }


    // --- Diálogos Informativos ---
    // --- Funciones de Formato y Ayuda ---

    internal fun determineStatusColorSpo2(spo2: Int): StatusColor {
        return when {
            spo2 <= 0 -> StatusColor.UNKNOWN
            // Usar los umbrales cargados desde SettingsRepository
            spo2 <= userSpo2CriticalThreshold -> StatusColor.CRITICAL
            spo2 < userSpo2WarningThreshold  -> StatusColor.WARNING // Si es < umbral de warning (y no crítico)
            else -> StatusColor.NORMAL
        }
    }

    internal fun determineStatusColorHr(hr: Int): StatusColor {
        return when {
            hr <= 0 -> StatusColor.UNKNOWN // Valor no válido o no disponible
            // Usar los umbrales cargados desde SettingsRepository
            hr < userHrCriticalLowThreshold || hr > userHrCriticalHighThreshold -> StatusColor.CRITICAL
            hr < userHrWarningLowThreshold || hr > userHrWarningHighThreshold -> StatusColor.WARNING
            else -> StatusColor.NORMAL
        }
    }

    fun formatTimeDisplay(millis: Long): String {
        return timeFormatter.format(millis.coerceAtLeast(0L))
    }

    fun buildTestExecutionSummary(): TestExecutionSummaryData? {
        val prepData = currentTestPreparationData ?: run {
            Log.e("SummaryBuilder", "No hay TestPreparationData para construir el resumen.")
            return null
        }
        val state = _uiState.value

        // La duración actual de la prueba es currentTimeMillis si la prueba ha corrido o está finalizada
        val actualDuration =
            if (state.isTestFinished || state.isTestRunning || state.currentTimeMillis > 0) {
                state.currentTimeMillis
            } else {
                0L // Si la prueba nunca empezó, la duración es 0
            }

        // El tiempo de inicio real de la prueba
        val startTime = if (testActualStartTimeMillis > 0L) {
            testActualStartTimeMillis
        } else {
            Log.w("SummaryBuilder", "testActualStartTimeMillis es 0, el tiempo de inicio puede no ser preciso.")
            if (actualDuration > 0L) System.currentTimeMillis() - actualDuration else 0L
        }

        Log.d("SummaryBuilder", "Construyendo resumen: StartTime=$startTime, Duration=$actualDuration, Laps=${state.laps}")

        return TestExecutionSummaryData(
            // --- Campos de TestPreparationData ---
            patientId = prepData.patientId,
            patientFullName = prepData.patientFullName,
            patientSex = prepData.patientSex,
            patientAge = prepData.patientAge,
            patientHeightCm = prepData.patientHeightCm,
            patientWeightKg = prepData.patientWeightKg,
            usesInhalers = prepData.usesInhalers,
            usesOxygen = prepData.usesOxygen,
            theoreticalDistance = prepData.theoreticalDistance,
            basalSpo2 = prepData.basalSpo2,
            basalHeartRate = prepData.basalHeartRate,
            basalBloodPressureSystolic = prepData.basalBloodPressureSystolic,
            basalBloodPressureDiastolic = prepData.basalBloodPressureDiastolic,
            basalRespiratoryRate = prepData.basalRespiratoryRate,
            basalDyspneaBorg = prepData.basalDyspneaBorg,
            basalLegPainBorg = prepData.basalLegPainBorg,
            devicePlacementLocation = prepData.devicePlacementLocation,
            isFirstTestForPatient = prepData.isFirstTestForPatient,
            // --- Campos específicos de la ejecución ---
            testActualStartTimeMillis = startTime,
            actualTestDurationMillis = actualDuration,
            distanceMetersFinal = state.distanceMeters,
            lapsFinal = state.laps,
            trackLengthUsedMeters = state.trackLength, // La longitud de pista que se usó
            minSpo2Record = state.minSpo2Record,
            maxHeartRateRecord = state.maxHeartRateRecord,
            minHeartRateRecord = state.minHeartRateRecord,
            stopRecords = state.stopRecords,
            spo2DataPoints = state.spo2DataPoints,
            heartRateDataPoints = state.heartRateDataPoints,
            minuteReadings = state.minuteMarkerData
        )
    }

    /**
     * Cancela jobs relacionados con una prueba en curso (timer, countdowns).
     * No detiene la recepción/simulación continua de sensores por defecto,
     * ya que la UI podría querer mostrar datos en tiempo real incluso si la prueba está pausada o finalizada.
     */
    private fun cancelTestInProgressActivities(restartLiveDataProcessing: Boolean = true) {
        timerJob?.cancel()
        stopCountdownJob?.cancel()
        recoveryDataCaptureJob?.cancel()
        forceReconnectJob?.cancel()

        if (!restartLiveDataProcessing) {
            liveDataProcessingJob?.cancel() // Cancelar solo si no se va a reiniciar
        }

        _uiState.update {
            it.copy(
                showStopConfirmationDialog = false,
                stopCountdownSeconds = STOP_CONFIRMATION_COUNTDOWN_SECONDS,
                isAttemptingForceReconnect = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelTestInProgressActivities(restartLiveDataProcessing = false)
        Log.d("TestExecutionVM", "ViewModel onCleared.")
    }

    fun clearUserMessage() {
        userMessageClearJob?.cancel() // Cancela cualquier job de limpieza de mensaje anterior
        _uiState.update { it.copy(userMessage = null) }
    }

    fun onBluetoothIconClicked() {
        if (_uiState.value.isAttemptingForceReconnect || forceReconnectJob?.isActive == true) {
            Log.d("TestExecutionVM", "Intento de reconexión ya en curso o icono pulsado rápidamente.")
            _uiState.update { it.copy(userMessage = "Reconexión en progreso...") }
            clearUserMessageAfterDelay(1500)
            return
        }

        if (!bluetoothService.isBluetoothEnabled()) {
            _uiState.update { it.copy(userMessage = "Active Bluetooth en ajustes del sistema.") }
            clearUserMessageAfterDelay(3500)
            return
        }

        if (_uiState.value.bluetoothIconStatus == BluetoothIconStatus.RED &&
            _uiState.value.bluetoothStatusMessage.contains("Pérdida de conexión", ignoreCase = true)
        ) {
            val deviceAddressToReconnect = bluetoothService.lastKnownConnectedDeviceAddress.value
            if (deviceAddressToReconnect == null) {
                _uiState.update { it.copy(userMessage = "No hay dispositivo previo para reconectar.") }
                clearUserMessageAfterDelay()
                _uiState.update { cs ->
                    val (icon, msg) = determineBluetoothVisualStatus(
                        connectionStatus = bluetoothService.connectionStatus.value,
                        deviceData = bluetoothService.bleDeviceData.value,
                        isBluetoothAdapterEnabled = bluetoothService.isBluetoothEnabled()
                    )
                    cs.copy(bluetoothIconStatus = icon, bluetoothStatusMessage = msg)
                }
                return
            }

            forceReconnectJob?.cancel()
            forceReconnectJob = viewModelScope.launch {
                Log.i("TestExecutionVM", "Iniciando intento de reconexión forzada al dispositivo: $deviceAddressToReconnect")
                _uiState.update {
                    it.copy(
                        isAttemptingForceReconnect = true,
                        bluetoothIconStatus = BluetoothIconStatus.CONNECTING,
                        bluetoothStatusMessage = "Reconectando..."
                    )
                }

                var operationSuccessful = false
                try {
                    // Usamos withTimeout para toda la secuencia de desconexión-conexión-espera
                    withTimeout(FORCE_RECONNECT_TIMEOUT_SECONDS * 1000L) {
                        // 1. Desconectar si está actualmente conectado o en un estado intermedio
                        val currentStatus = bluetoothService.connectionStatus.value
                        if (currentStatus.isConsideredConnectedOrSubscribed() ||
                            currentStatus == BleConnectionStatus.CONNECTING ||
                            currentStatus == BleConnectionStatus.CONNECTED) { // Asegúrate que isConsideredConnectedOrSubscribed los cubra
                            Log.d("TestExecutionVM", "Desconectando antes de forzar reconexión...")
                            bluetoothService.disconnect() // ESTA FUNCIÓN SÍ EXISTE EN TU INTERFAZ
                            delay(700L) // Dar un poco más de tiempo para que la desconexión se procese bien
                        }

                        // 2. Intentar conectar
                        bluetoothService.connect(deviceAddressToReconnect) // ESTA FUNCIÓN SÍ EXISTE EN TU INTERFAZ
                        // Asumimos que esta función inicia la conexión
                        // y el resultado se verá en el flow connectionStatus

                        // 3. Esperar a que el estado sea SUBSCRIBED o un estado de error
                        //    Esto es crucial. connect() solo inicia el proceso.
                        val finalStatus = bluetoothService.connectionStatus.first { status ->
                            status == BleConnectionStatus.SUBSCRIBED || status.isErrorStatus() || status == BleConnectionStatus.DISCONNECTED_ERROR
                        }
                        operationSuccessful = (finalStatus == BleConnectionStatus.SUBSCRIBED)
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.w("TestExecutionVM", "Reconexión forzada TIMED OUT esperando un estado final.")
                    operationSuccessful = false
                    // Si hubo timeout, es buena idea asegurarse de que el servicio está desconectado.
                    // Podrías llamar a bluetoothService.disconnect() aquí si el estado no es ya un error/desconexión.
                    if (bluetoothService.connectionStatus.value != BleConnectionStatus.DISCONNECTED_ERROR &&
                        bluetoothService.connectionStatus.value != BleConnectionStatus.IDLE &&
                        bluetoothService.connectionStatus.value != BleConnectionStatus.DISCONNECTED_BY_USER) {
                        bluetoothService.disconnect()
                    }
                } catch (e: Exception) {
                    Log.e("TestExecutionVM", "Error durante el intento de reconexión: ${e.message}", e)
                    operationSuccessful = false
                }

                _uiState.update { it.copy(isAttemptingForceReconnect = false) }

                if (operationSuccessful) {
                    Log.i("TestExecutionVM", "Reconexión forzada parece haber tenido éxito (SUBSCRIBED).")
                    // El flow de connectionStatus ya debería haber actualizado el icono y mensaje.
                    // Puedes poner un mensaje de éxito temporal si quieres.
                    _uiState.update { it.copy(userMessage = "Conexión restaurada.") }
                    clearUserMessageAfterDelay()
                } else {
                    Log.w("TestExecutionVM", "Reconexión forzada falló o tuvo timeout.")
                    _uiState.update {
                        it.copy(
                            userMessage = "No se pudo reconectar. Verifique el dispositivo.",
                            // El icono y mensaje de BT se actualizarán por el flow de connectionStatus,
                            // pero podemos forzar uno aquí si es necesario.
                            // bluetoothIconStatus = BluetoothIconStatus.RED,
                            // bluetoothStatusMessage = "Fallo al reconectar"
                        )
                    }
                    clearUserMessageAfterDelay()
                }

                // Siempre reevaluar el estado visual final basado en lo que diga el servicio
                val (finalIcon, finalMsg) = determineBluetoothVisualStatus(
                    connectionStatus = bluetoothService.connectionStatus.value,
                    deviceData = bluetoothService.bleDeviceData.value,
                    isBluetoothAdapterEnabled = bluetoothService.isBluetoothEnabled()
                )
                _uiState.update { it.copy(bluetoothIconStatus = finalIcon, bluetoothStatusMessage = finalMsg) }
            }
        } else if (_uiState.value.bluetoothIconStatus == BluetoothIconStatus.GRAY) {
            _uiState.update { it.copy(userMessage = "Bluetooth no conectado. Intente desde ajustes.") }
            clearUserMessageAfterDelay()
        }
    }
    private var forceReconnectJob: Job? = null // Declaración para el Job de reconexión
}
