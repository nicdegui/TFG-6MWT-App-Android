package com.example.app6mwt.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_6mwt")

object UserPreferencesKeys {
    // --- Umbrales de Alarma para la Ejecución del Test ---
    val SPO2_WARNING_THRESHOLD = intPreferencesKey("spo2_warning_threshold")
    val SPO2_CRITICAL_THRESHOLD = intPreferencesKey("spo2_critical_threshold")

    val HR_CRITICAL_LOW_THRESHOLD = intPreferencesKey("hr_critical_low_threshold")
    val HR_WARNING_LOW_THRESHOLD = intPreferencesKey("hr_warning_low_threshold")
    val HR_WARNING_HIGH_THRESHOLD = intPreferencesKey("hr_warning_high_threshold")
    val HR_CRITICAL_HIGH_THRESHOLD = intPreferencesKey("hr_critical_high_threshold")

    // --- Rangos Aceptables para Registro de Datos (Basal/Post) ---
    val SPO2_INPUT_MIN = intPreferencesKey("spo2_input_min")
    val SPO2_INPUT_MAX = intPreferencesKey("spo2_input_max")
    val HR_INPUT_MIN = intPreferencesKey("hr_input_min")
    val HR_INPUT_MAX = intPreferencesKey("hr_input_max")
    val BP_SYSTOLIC_INPUT_MIN = intPreferencesKey("bp_systolic_input_min")
    val BP_SYSTOLIC_INPUT_MAX = intPreferencesKey("bp_systolic_input_max")
    val BP_DIASTOLIC_INPUT_MIN = intPreferencesKey("bp_diastolic_input_min")
    val BP_DIASTOLIC_INPUT_MAX = intPreferencesKey("bp_diastolic_input_max")
    val RR_INPUT_MIN = intPreferencesKey("rr_input_min")
    val RR_INPUT_MAX = intPreferencesKey("rr_input_max")
    val BORG_INPUT_MIN = intPreferencesKey("borg_input_min") // Rango general para Borg
    val BORG_INPUT_MAX = intPreferencesKey("borg_input_max")
}


// Valores por defecto (iguales a tus constantes originales)
object DefaultThresholdValues { // Renombrado para ser más genérico, o podrías crear DefaultSettingsValues
    // --- Umbrales de Alarma para la Ejecución del Test ---
    const val SPO2_CRITICAL_DEFAULT = 88 // Si SpO2 <= 88 -> Rojo
    const val SPO2_WARNING_DEFAULT = 94 // Si 88 < SpO2 <= 90 -> Amarillo. Si SpO2 > 90 -> Verde

    const val HR_CRITICAL_LOW_DEFAULT = 40
    const val HR_WARNING_LOW_DEFAULT = 50
    const val HR_WARNING_HIGH_DEFAULT = 130
    const val HR_CRITICAL_HIGH_DEFAULT = 150

    // --- Rangos Aceptables para Registro de Datos (Basal/Post) ---
    const val SPO2_INPUT_MIN_DEFAULT = 70
    const val SPO2_INPUT_MAX_DEFAULT = 100
    const val HR_INPUT_MIN_DEFAULT = 30
    const val HR_INPUT_MAX_DEFAULT = 220
    const val BP_SYSTOLIC_INPUT_MIN_DEFAULT = 70
    const val BP_SYSTOLIC_INPUT_MAX_DEFAULT = 250
    const val BP_DIASTOLIC_INPUT_MIN_DEFAULT = 40
    const val BP_DIASTOLIC_INPUT_MAX_DEFAULT = 150
    const val RR_INPUT_MIN_DEFAULT = 8
    const val RR_INPUT_MAX_DEFAULT = 40
    const val BORG_INPUT_MIN_DEFAULT = 0
    const val BORG_INPUT_MAX_DEFAULT = 10
}


@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val dataStore = context.settingsDataStore

    // --- Flujos para leer los valores de Umbrales de Alarma ---
    val spo2WarningThresholdFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            preferences[UserPreferencesKeys.SPO2_WARNING_THRESHOLD] ?: DefaultThresholdValues.SPO2_WARNING_DEFAULT
        }
    val spo2CriticalThresholdFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            preferences[UserPreferencesKeys.SPO2_CRITICAL_THRESHOLD] ?: DefaultThresholdValues.SPO2_CRITICAL_DEFAULT
        }
    val hrCriticalLowThresholdFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            preferences[UserPreferencesKeys.HR_CRITICAL_LOW_THRESHOLD] ?: DefaultThresholdValues.HR_CRITICAL_LOW_DEFAULT
        }
    val hrWarningLowThresholdFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            preferences[UserPreferencesKeys.HR_WARNING_LOW_THRESHOLD] ?: DefaultThresholdValues.HR_WARNING_LOW_DEFAULT
        }
    val hrWarningHighThresholdFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            preferences[UserPreferencesKeys.HR_WARNING_HIGH_THRESHOLD] ?: DefaultThresholdValues.HR_WARNING_HIGH_DEFAULT
        }
    val hrCriticalHighThresholdFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            preferences[UserPreferencesKeys.HR_CRITICAL_HIGH_THRESHOLD] ?: DefaultThresholdValues.HR_CRITICAL_HIGH_DEFAULT
        }

    // --- Flujos para leer los valores de Rangos de Entrada ---
    val spo2InputMinFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[UserPreferencesKeys.SPO2_INPUT_MIN] ?: DefaultThresholdValues.SPO2_INPUT_MIN_DEFAULT }
    val spo2InputMaxFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[UserPreferencesKeys.SPO2_INPUT_MAX] ?: DefaultThresholdValues.SPO2_INPUT_MAX_DEFAULT }
    val hrInputMinFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[UserPreferencesKeys.HR_INPUT_MIN] ?: DefaultThresholdValues.HR_INPUT_MIN_DEFAULT }
    val hrInputMaxFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[UserPreferencesKeys.HR_INPUT_MAX] ?: DefaultThresholdValues.HR_INPUT_MAX_DEFAULT }
    val bpSystolicInputMinFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[UserPreferencesKeys.BP_SYSTOLIC_INPUT_MIN] ?: DefaultThresholdValues.BP_SYSTOLIC_INPUT_MIN_DEFAULT }
    val bpSystolicInputMaxFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[UserPreferencesKeys.BP_SYSTOLIC_INPUT_MAX] ?: DefaultThresholdValues.BP_SYSTOLIC_INPUT_MAX_DEFAULT }
    val bpDiastolicInputMinFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[UserPreferencesKeys.BP_DIASTOLIC_INPUT_MIN] ?: DefaultThresholdValues.BP_DIASTOLIC_INPUT_MIN_DEFAULT }
    val bpDiastolicInputMaxFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[UserPreferencesKeys.BP_DIASTOLIC_INPUT_MAX] ?: DefaultThresholdValues.BP_DIASTOLIC_INPUT_MAX_DEFAULT }
    val rrInputMinFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[UserPreferencesKeys.RR_INPUT_MIN] ?: DefaultThresholdValues.RR_INPUT_MIN_DEFAULT }
    val rrInputMaxFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[UserPreferencesKeys.RR_INPUT_MAX] ?: DefaultThresholdValues.RR_INPUT_MAX_DEFAULT }
    val borgInputMinFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[UserPreferencesKeys.BORG_INPUT_MIN] ?: DefaultThresholdValues.BORG_INPUT_MIN_DEFAULT }
    val borgInputMaxFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[UserPreferencesKeys.BORG_INPUT_MAX] ?: DefaultThresholdValues.BORG_INPUT_MAX_DEFAULT }


    // --- Función para guardar todos los Ajustes (Umbrales y Rangos) ---
    suspend fun saveAllSettings(
        // Umbrales de Alarma
        spo2Warning: Int,
        spo2Critical: Int,
        hrCriticalLow: Int,
        hrWarningLow: Int,
        hrWarningHigh: Int,
        hrCriticalHigh: Int,
        // Rangos de Entrada
        spo2InputMin: Int, spo2InputMax: Int,
        hrInputMin: Int, hrInputMax: Int,
        bpSystolicMin: Int, bpSystolicMax: Int,
        bpDiastolicMin: Int, bpDiastolicMax: Int,
        rrMin: Int, rrMax: Int,
        borgMin: Int, borgMax: Int
    ) {
        dataStore.edit { settings ->
            // Guardar Umbrales
            settings[UserPreferencesKeys.SPO2_WARNING_THRESHOLD] = spo2Warning
            settings[UserPreferencesKeys.SPO2_CRITICAL_THRESHOLD] = spo2Critical
            settings[UserPreferencesKeys.HR_CRITICAL_LOW_THRESHOLD] = hrCriticalLow
            settings[UserPreferencesKeys.HR_WARNING_LOW_THRESHOLD] = hrWarningLow
            settings[UserPreferencesKeys.HR_WARNING_HIGH_THRESHOLD] = hrWarningHigh
            settings[UserPreferencesKeys.HR_CRITICAL_HIGH_THRESHOLD] = hrCriticalHigh

            // Guardar Rangos de Entrada
            settings[UserPreferencesKeys.SPO2_INPUT_MIN] = spo2InputMin
            settings[UserPreferencesKeys.SPO2_INPUT_MAX] = spo2InputMax
            settings[UserPreferencesKeys.HR_INPUT_MIN] = hrInputMin
            settings[UserPreferencesKeys.HR_INPUT_MAX] = hrInputMax
            settings[UserPreferencesKeys.BP_SYSTOLIC_INPUT_MIN] = bpSystolicMin
            settings[UserPreferencesKeys.BP_SYSTOLIC_INPUT_MAX] = bpSystolicMax
            settings[UserPreferencesKeys.BP_DIASTOLIC_INPUT_MIN] = bpDiastolicMin
            settings[UserPreferencesKeys.BP_DIASTOLIC_INPUT_MAX] = bpDiastolicMax
            settings[UserPreferencesKeys.RR_INPUT_MIN] = rrMin
            settings[UserPreferencesKeys.RR_INPUT_MAX] = rrMax
            settings[UserPreferencesKeys.BORG_INPUT_MIN] = borgMin
            settings[UserPreferencesKeys.BORG_INPUT_MAX] = borgMax
        }
    }
}
