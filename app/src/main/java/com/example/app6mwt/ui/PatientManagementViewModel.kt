package com.example.app6mwt.ui

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app6mwt.data.PacienteRepository
import com.example.app6mwt.data.model.Paciente
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.app6mwt.data.DefaultThresholdValues
import com.example.app6mwt.data.SettingsRepository

// Clase sellada NavigationEvent
sealed class NavigationEvent {
    data class ToPreparationScreen(
        val patientId: String,
        val patientName: String,
        val patientHasHistory: Boolean
    ) : NavigationEvent()

    data class ToHistoryScreen(val patientId: String) : NavigationEvent()

    object ExitApp : NavigationEvent()
}

// Data class para el estado enriquecido
data class PacienteConHistorialReal(
    val paciente: Paciente,
    val tieneHistorialReal: Boolean
)

data class AllSettingsDialogState(
    // Umbrales de Alarma
    val spo2Warning: String = "",
    val spo2Critical: String = "",
    val hrCriticalLow: String = "",
    val hrWarningLow: String = "",
    val hrWarningHigh: String = "",
    val hrCriticalHigh: String = "",
    // Rangos de Entrada
    val spo2InputMin: String = "",
    val spo2InputMax: String = "",
    val hrInputMin: String = "",
    val hrInputMax: String = "",
    val bpSystolicMin: String = "",
    val bpSystolicMax: String = "",
    val bpDiastolicMin: String = "",
    val bpDiastolicMax: String = "",
    val rrMin: String = "",
    val rrMax: String = "",
    val borgMin: String = "",
    val borgMax: String = "",
    // Para mensaje de error
    val validationError: String? = null
)

@HiltViewModel
class PatientManagementViewModel @Inject constructor(
    private val repository: PacienteRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    var searchQuery by mutableStateOf("")
        private set

    private val _selectedPatientInfo = MutableStateFlow<PacienteConHistorialReal?>(null)
    val selectedPatientInfo: StateFlow<PacienteConHistorialReal?> = _selectedPatientInfo.asStateFlow()

    var infoMessage by mutableStateOf("Seleccione un paciente o realice una búsqueda.")
        private set

    var showSettingsDialog by mutableStateOf(false)
        private set

    var alarmThresholdsDialogState by mutableStateOf(AllSettingsDialogState())
        private set

    // --- Lista de Pacientes Enriquecida ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val pacientesConEstadoHistorial: StateFlow<List<PacienteConHistorialReal>> =
        repository.getPacientesConEstadoHistorialCombinado()
            .onEach { updatedList ->
                Log.d("PatientMgmtVM", "[pacientesConEstadoHistorial] Nueva lista recibida. Elementos: ${updatedList.size}")
                val currentSelected = _selectedPatientInfo.value
                if (currentSelected != null) {
                    val updatedSelectedPatientInList = updatedList.find { it.paciente.id == currentSelected.paciente.id }
                    if (updatedSelectedPatientInList != null) {
                        if (updatedSelectedPatientInList != currentSelected) {
                            Log.d("PatientMgmtVM", "[pacientesConEstadoHistorial] Actualizando _selectedPatientInfo para ${updatedSelectedPatientInList.paciente.id}. " +
                                    "Nuevo tieneHistorialReal: ${updatedSelectedPatientInList.tieneHistorialReal}.")
                            _selectedPatientInfo.value = updatedSelectedPatientInList
                        }
                    } else {
                        Log.w("PatientMgmtVM", "[pacientesConEstadoHistorial] Paciente seleccionado (${currentSelected.paciente.id}) ya no está en la lista. Deseleccionando.")
                        _selectedPatientInfo.value = null
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    // --- Estados para los diálogos ---
    var showAddPatientDialog by mutableStateOf(false)
        private set
    var newPatientNameInput by mutableStateOf("")
        private set
    var isEditingPatientName by mutableStateOf(false)
        private set
    var editingPatientNameValue by mutableStateOf("")
        private set
    var showDeletePatientConfirmationDialog by mutableStateOf(false)
        private set

    private var nextPatientIdCounter by mutableIntStateOf(1001)
    init {
        viewModelScope.launch {
            nextPatientIdCounter = repository.obtenerSiguienteIdNumerico()
            Log.d("PatientMgmtVM", "ViewModel INIT. Hash: ${this.hashCode()}")
        }
    }

    private val _navigateToEvent = MutableSharedFlow<NavigationEvent>()
    val navigateToEvent = _navigateToEvent.asSharedFlow()

    fun onSearchQueryChanged(query: String) {
        searchQuery = query
        if (query.isBlank() && selectedPatientInfo.value == null) {
            infoMessage = "Seleccione un paciente o realice una búsqueda."
        }
    }

    fun performSearch() {
        if (searchQuery.isBlank()) {
            infoMessage = "Ingrese un ID para buscar."
            return
        }
        viewModelScope.launch {
            val foundInList = pacientesConEstadoHistorial.value.find { it.paciente.id == searchQuery }
            if (foundInList != null) {
                _selectedPatientInfo.value = foundInList
                infoMessage = "Paciente: ${foundInList.paciente.nombre} (ID: ${foundInList.paciente.id})"
                repository.actualizarAccesoPaciente(foundInList.paciente.id)
            } else {
                _selectedPatientInfo.value = null
                infoMessage = "Búsqueda errónea, el paciente ID '$searchQuery' no está registrado o la lista no está actualizada."
            }
        }
    }

    fun onPatientSelectedFromList(pacienteConInfo: PacienteConHistorialReal) {
        _selectedPatientInfo.value = pacienteConInfo
        infoMessage = "Paciente: ${pacienteConInfo.paciente.nombre} (ID: ${pacienteConInfo.paciente.id})"
        viewModelScope.launch {
            repository.actualizarAccesoPaciente(pacienteConInfo.paciente.id)
        }
    }

    // --- Gestión del diálogo "Añadir Paciente" ---
    fun onOpenAddPatientDialog() {
        newPatientNameInput = ""
        showAddPatientDialog = true
        infoMessage = "Registrando un nuevo paciente."
    }

    fun onCloseAddPatientDialog() {
        showAddPatientDialog = false
        restoreInfoMessageAfterDialog()
    }

    fun onNewPatientNameChange(name: String) {
        newPatientNameInput = name
    }

    fun onConfirmAddPatient() {
        val trimmedName = newPatientNameInput.trim()
        if (trimmedName.isBlank()) {
            infoMessage = "Error: El nombre del nuevo paciente no puede estar vacío."
            return
        }
        if (trimmedName.any { it.isDigit() }) {
            infoMessage = "Error: El nombre del nuevo paciente no debe contener números."
            return
        }

        viewModelScope.launch {
            val actualSiguienteIdNumerico = repository.obtenerSiguienteIdNumerico()
            val newId = actualSiguienteIdNumerico.toString()

            Log.d("PatientMgmtVM", "onConfirmAddPatient: Usando ID $newId (obtenido de repo) para el nuevo paciente.")
            val newPatient = Paciente(
                id = newId,
                nombre = trimmedName,
                tieneHistorial = false,
                ultimoAccesoTimestamp = System.currentTimeMillis()
            )
            repository.insertarPaciente(newPatient)

            val nuevoPacienteConInfo = PacienteConHistorialReal(
                paciente = newPatient,
                tieneHistorialReal = false
            )
            _selectedPatientInfo.value = nuevoPacienteConInfo
            infoMessage = "Paciente: ${nuevoPacienteConInfo.paciente.nombre} (ID: ${nuevoPacienteConInfo.paciente.id})"


            val proximoIdDespuesDeInsertar = repository.obtenerSiguienteIdNumerico()
            nextPatientIdCounter = proximoIdDespuesDeInsertar
            Log.d("PatientMgmtVM", "onConfirmAddPatient: Nuevo paciente registrado. nextPatientIdCounter (estado) actualizado a: $proximoIdDespuesDeInsertar")

            infoMessage = "Nuevo paciente '${newPatient.nombre}' (ID: ${newPatient.id}) registrado."
        }
        onCloseAddPatientDialog()
    }

    // --- Gestión del diálogo "Editar Nombre Paciente" ---
    fun onStartEditPatientName() {
        _selectedPatientInfo.value?.let {
            editingPatientNameValue = it.paciente.nombre
            isEditingPatientName = true
            infoMessage = "Editando nombre para paciente ID: ${it.paciente.id}"
        } ?: run {
            infoMessage = "Seleccione un paciente para poder editar su nombre."
        }
    }

    fun onCancelEditPatientName() {
        isEditingPatientName = false
        restoreInfoMessageAfterDialog()
    }

    fun onEditingPatientNameChange(updatedName: String) {
        editingPatientNameValue = updatedName
    }

    fun onConfirmEditPatientName() {
        val currentPatientInfo = _selectedPatientInfo.value
        if (currentPatientInfo == null) {
            infoMessage = "Error interno: No hay paciente seleccionado para editar."
            isEditingPatientName = false
            return
        }

        val proposedName = editingPatientNameValue.trim()
        if (proposedName.isBlank()) {
            infoMessage = "Error: El nombre del paciente no puede estar vacío."
            return
        }
        if (proposedName.any { it.isDigit() }) {
            infoMessage = "Error: El nombre del paciente no debe contener números."
            return
        }

        viewModelScope.launch {
            repository.actualizarNombrePaciente(currentPatientInfo.paciente.id, proposedName)
            infoMessage = "Nombre del paciente ID '${currentPatientInfo.paciente.id}' actualizado a '$proposedName'."
        }
        isEditingPatientName = false
    }

    // --- Navegación ---
    fun onPrepareTestClicked() {
        _selectedPatientInfo.value?.let { info ->
            viewModelScope.launch {
                _navigateToEvent.emit(
                    NavigationEvent.ToPreparationScreen(
                        info.paciente.id,
                        info.paciente.nombre,
                        info.tieneHistorialReal
                    )
                )
            }
        } ?: run {
            infoMessage = "Por favor, seleccione un paciente para preparar la prueba."
        }
    }

    fun onViewHistoryClicked() {
        _selectedPatientInfo.value?.let { info ->
            if (info.tieneHistorialReal) {
                viewModelScope.launch {
                    _navigateToEvent.emit(NavigationEvent.ToHistoryScreen(info.paciente.id))
                }
            } else {
                infoMessage = "El paciente ID ${info.paciente.id} (${info.paciente.nombre}) no tiene historial de pruebas."
            }
        } ?: run {
            infoMessage = "Por favor, seleccione un paciente para ver su historial."
        }
    }

    // --- Gestión del diálogo "Eliminar Paciente" ---
    fun requestDeleteSelectedPatient() {
        if (_selectedPatientInfo.value != null && !isEditingPatientName) { // No permitir si se está editando nombre
            // El mensaje de confirmación estará en el diálogo, aquí solo mostramos el diálogo
            showDeletePatientConfirmationDialog = true
        } else if (_selectedPatientInfo.value == null) {
            infoMessage = "Seleccione un paciente para eliminarlo."
        } else if (isEditingPatientName) {
            infoMessage = "Termine la edición del nombre antes de eliminar al paciente."
        }
    }

    fun confirmDeletePatient() {
        _selectedPatientInfo.value?.paciente?.let { patientToDelete ->
            viewModelScope.launch {
                repository.eliminarPaciente(patientToDelete.id) // Usar el ID para eliminar
                infoMessage = "Paciente ${patientToDelete.nombre} (ID: ${patientToDelete.id}) eliminado."
            }
        }
        showDeletePatientConfirmationDialog = false
    }

    fun cancelDeletePatient() {
        showDeletePatientConfirmationDialog = false
        restoreInfoMessageAfterDialog()
    }

    private fun restoreInfoMessageAfterDialog() {
        _selectedPatientInfo.value?.let { info ->
            infoMessage = "Paciente: ${info.paciente.nombre} (ID: ${info.paciente.id})"
        } ?: run {
            if (searchQuery.isBlank()) {
                infoMessage = "Seleccione un paciente o realice una búsqueda."
            }else if (!infoMessage.startsWith("Búsqueda errónea")) {
                infoMessage = "Realice una nueva búsqueda o seleccione un paciente."
            }
        }
    }

    var showExitAppConfirmationDialog by mutableStateOf(false)
        private set

    fun requestExitApp() {
        showExitAppConfirmationDialog = true
    }
    fun confirmExitApp() {
        showExitAppConfirmationDialog = false
        viewModelScope.launch { _navigateToEvent.emit(NavigationEvent.ExitApp) }
    }
    fun cancelExitApp() {
        showExitAppConfirmationDialog = false
    }

    // --- Funciones para el Diálogo de Ajustes ---
    fun onOpenSettingsDialog() {
        viewModelScope.launch {
            alarmThresholdsDialogState = AllSettingsDialogState(
                spo2Warning = settingsRepository.spo2WarningThresholdFlow.first().toString(),
                spo2Critical = settingsRepository.spo2CriticalThresholdFlow.first().toString(),
                hrCriticalLow = settingsRepository.hrCriticalLowThresholdFlow.first().toString(),
                hrWarningLow = settingsRepository.hrWarningLowThresholdFlow.first().toString(),
                hrWarningHigh = settingsRepository.hrWarningHighThresholdFlow.first().toString(),
                hrCriticalHigh = settingsRepository.hrCriticalHighThresholdFlow.first().toString(),

                spo2InputMin = settingsRepository.spo2InputMinFlow.first().toString(),
                spo2InputMax = settingsRepository.spo2InputMaxFlow.first().toString(),
                hrInputMin = settingsRepository.hrInputMinFlow.first().toString(),
                hrInputMax = settingsRepository.hrInputMaxFlow.first().toString(),
                bpSystolicMin = settingsRepository.bpSystolicInputMinFlow.first().toString(),
                bpSystolicMax = settingsRepository.bpSystolicInputMaxFlow.first().toString(),
                bpDiastolicMin = settingsRepository.bpDiastolicInputMinFlow.first().toString(),
                bpDiastolicMax = settingsRepository.bpDiastolicInputMaxFlow.first().toString(),
                rrMin = settingsRepository.rrInputMinFlow.first().toString(),
                rrMax = settingsRepository.rrInputMaxFlow.first().toString(),
                borgMin = settingsRepository.borgInputMinFlow.first().toString(),
                borgMax = settingsRepository.borgInputMaxFlow.first().toString()
            )
            showSettingsDialog = true
        }
    }

    fun onCloseSettingsDialog() {
        showSettingsDialog = false
        alarmThresholdsDialogState = alarmThresholdsDialogState.copy(validationError = null)
    }

    fun onSettingsDialogStateChanged(newDialogState: AllSettingsDialogState) {
        alarmThresholdsDialogState = newDialogState.copy(validationError = null)
    }

    fun saveSettings() {
        val currentState = alarmThresholdsDialogState

        // --- Conversión a Int (con validación básica de no nulo y tipo) ---
        val spo2Warning = currentState.spo2Warning.toIntOrNull()
        val spo2Critical = currentState.spo2Critical.toIntOrNull()
        val hrCriticalLow = currentState.hrCriticalLow.toIntOrNull()
        val hrWarningLow = currentState.hrWarningLow.toIntOrNull()
        val hrWarningHigh = currentState.hrWarningHigh.toIntOrNull()
        val hrCriticalHigh = currentState.hrCriticalHigh.toIntOrNull()

        val spo2Min = currentState.spo2InputMin.toIntOrNull()
        val spo2Max = currentState.spo2InputMax.toIntOrNull()
        val hrMin = currentState.hrInputMin.toIntOrNull()
        val hrMax = currentState.hrInputMax.toIntOrNull()
        val bpSysMin = currentState.bpSystolicMin.toIntOrNull()
        val bpSysMax = currentState.bpSystolicMax.toIntOrNull()
        val bpDiaMin = currentState.bpDiastolicMin.toIntOrNull()
        val bpDiaMax = currentState.bpDiastolicMax.toIntOrNull()
        val rrMin = currentState.rrMin.toIntOrNull()
        val rrMax = currentState.rrMax.toIntOrNull()
        val borgMin = currentState.borgMin.toIntOrNull()
        val borgMax = currentState.borgMax.toIntOrNull()

        // --- Validación Lógica Detallada ---
        val errors = mutableListOf<String>()

        // Validación Umbrales de Alarma
        if (spo2Warning == null || spo2Warning !in 0..100) errors.add("SpO2 Alerta (alarma) inválido (0-100).")
        if (spo2Critical == null || spo2Critical !in 0..100) errors.add("SpO2 Crítico (alarma) inválido (0-100).")
        if (spo2Warning != null && spo2Critical != null && spo2Critical >= spo2Warning) {
            errors.add("SpO2 Crítico (alarma) debe ser menor que SpO2 Alerta (alarma).")
        }

        if (hrCriticalLow == null || hrCriticalLow <= 0) errors.add("FC Crítica Baja (alarma) inválida (>0).")
        if (hrWarningLow == null || hrWarningLow <= 0) errors.add("FC Alerta Baja (alarma) inválida (>0).")
        if (hrWarningHigh == null || hrWarningHigh <= 0) errors.add("FC Alerta Alta (alarma) inválida (>0).")
        if (hrCriticalHigh == null || hrCriticalHigh <= 0) errors.add("FC Crítica Alta (alarma) inválida (>0).")

        if (hrCriticalLow != null && hrWarningLow != null && hrCriticalLow >= hrWarningLow) {
            errors.add("FC Crítica Baja (alarma) debe ser < FC Alerta Baja (alarma).")
        }
        if (hrWarningLow != null && hrWarningHigh != null && hrWarningLow >= hrWarningHigh) {
            errors.add("FC Alerta Baja (alarma) debe ser < FC Alerta Alta (alarma).")
        }
        if (hrWarningHigh != null && hrCriticalHigh != null && hrWarningHigh >= hrCriticalHigh) {
            errors.add("FC Alerta Alta (alarma) debe ser < FC Crítica Alta (alarma).")
        }

        // Validación Rangos de Entrada
        if (spo2Min == null || spo2Min !in 0..100) errors.add("SpO2 Mín (entrada) inválido (0-100).")
        if (spo2Max == null || spo2Max !in 0..100) errors.add("SpO2 Máx (entrada) inválido (0-100).")
        if (spo2Min != null && spo2Max != null && spo2Min > spo2Max) errors.add("SpO2 Mín (entrada) > SpO2 Máx (entrada).")

        if (hrMin == null || hrMin <= 0) errors.add("FC Mín (entrada) inválida (>0).")
        if (hrMax == null || hrMax <= 0) errors.add("FC Máx (entrada) inválida (>0).")
        if (hrMin != null && hrMax != null && hrMin > hrMax) errors.add("FC Mín (entrada) > FC Máx (entrada).")

        if (bpSysMin == null || bpSysMin <= 0) errors.add("TAS Mín (entrada) inválida (>0).")
        if (bpSysMax == null || bpSysMax <= 0) errors.add("TAS Máx (entrada) inválida (>0).")
        if (bpSysMin != null && bpSysMax != null && bpSysMin > bpSysMax) errors.add("TAS Mín (entrada) > TAS Máx (entrada).")

        if (bpDiaMin == null || bpDiaMin <= 0) errors.add("TAD Mín (entrada) inválida (>0).")
        if (bpDiaMax == null || bpDiaMax <= 0) errors.add("TAD Máx (entrada) inválida (>0).")
        if (bpDiaMin != null && bpDiaMax != null && bpDiaMin > bpDiaMax) errors.add("TAD Mín (entrada) > TAD Máx (entrada).")

        if (bpSysMin != null && bpDiaMin != null && bpSysMin <= bpDiaMin && bpSysMin != DefaultThresholdValues.BP_SYSTOLIC_INPUT_MIN_DEFAULT && bpDiaMin != DefaultThresholdValues.BP_DIASTOLIC_INPUT_MIN_DEFAULT) {
            // Solo validar si no son los valores por defecto, para permitir inicio sin error si son iguales
            errors.add("TAS Mín (entrada) debe ser > TAD Mín (entrada).")
        }
        if (bpSysMax != null && bpDiaMax != null && bpSysMax <= bpDiaMax && bpSysMax != DefaultThresholdValues.BP_SYSTOLIC_INPUT_MAX_DEFAULT && bpDiaMax != DefaultThresholdValues.BP_DIASTOLIC_INPUT_MAX_DEFAULT) {
            errors.add("TAS Máx (entrada) debe ser > TAD Máx (entrada).")
        }


        if (rrMin == null || rrMin <= 0) errors.add("FR Mín (entrada) inválida (>0).")
        if (rrMax == null || rrMax <= 0) errors.add("FR Máx (entrada) inválida (>0).")
        if (rrMin != null && rrMax != null && rrMin > rrMax) errors.add("FR Mín (entrada) > FR Máx (entrada).")

        if (borgMin == null || borgMin < 0) errors.add("Borg Mín (entrada) inválido (>=0).") // Borg puede ser 0
        if (borgMax == null || borgMax < 0) errors.add("Borg Máx (entrada) inválido (>=0).")
        if (borgMin != null && borgMax != null && borgMin > borgMax) errors.add("Borg Mín (entrada) > Borg Máx (entrada).")


        if (errors.isNotEmpty()) {
            val fullErrorMessage = errors.joinToString("\n")
            Log.e("PatientMgmtVM", "Error de validación al guardar ajustes:\n$fullErrorMessage")
            alarmThresholdsDialogState = currentState.copy(validationError = fullErrorMessage)
            return
        }

        // Si todas las validaciones pasan (los !! son seguros aquí)
        viewModelScope.launch {
            settingsRepository.saveAllSettings(
                spo2Warning = spo2Warning!!, spo2Critical = spo2Critical!!,
                hrCriticalLow = hrCriticalLow!!, hrWarningLow = hrWarningLow!!,
                hrWarningHigh = hrWarningHigh!!, hrCriticalHigh = hrCriticalHigh!!,
                spo2InputMin = spo2Min!!, spo2InputMax = spo2Max!!,
                hrInputMin = hrMin!!, hrInputMax = hrMax!!,
                bpSystolicMin = bpSysMin!!, bpSystolicMax = bpSysMax!!,
                bpDiastolicMin = bpDiaMin!!, bpDiastolicMax = bpDiaMax!!,
                rrMin = rrMin!!, rrMax = rrMax!!,
                borgMin = borgMin!!, borgMax = borgMax!!
            )
            Log.d("PatientMgmtVM", "Todos los ajustes guardados.")
            onCloseSettingsDialog() // Cierra si todo OK
        }
    }
}
