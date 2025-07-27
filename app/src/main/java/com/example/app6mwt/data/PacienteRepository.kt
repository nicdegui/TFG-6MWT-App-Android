package com.example.app6mwt.data

import android.util.Log
import com.example.app6mwt.data.local.ConteoPorPaciente
import com.example.app6mwt.data.local.PacienteDao
import com.example.app6mwt.data.local.PruebaRealizadaDao
import com.example.app6mwt.data.model.Paciente
import com.example.app6mwt.data.model.PruebaRealizada
import com.example.app6mwt.ui.PacienteConHistorialReal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PacienteRepository @Inject constructor(
    private val pacienteDao: PacienteDao,
    private val pruebaRealizadaDao: PruebaRealizadaDao
) {

    val todosLosPacientesOrdenados: Flow<List<Paciente>> = pacienteDao.observarTodosLosPacientes()

    suspend fun insertarPaciente(paciente: Paciente) {
        val pacienteConTimestamp = paciente.copy(ultimoAccesoTimestamp = System.currentTimeMillis())
        pacienteDao.insertarOActualizarPaciente(pacienteConTimestamp)
    }

    suspend fun eliminarPaciente(pacienteId: String) {
        pruebaRealizadaDao.eliminarTodasLasPruebasDePaciente(pacienteId)
        pacienteDao.eliminarPacientePorId(pacienteId)
    }

    suspend fun obtenerPacientePorId(pacienteId: String): Paciente? {
        val paciente = pacienteDao.obtenerPacientePorId(pacienteId)
        paciente?.let {
            actualizarAccesoPaciente(it.id)
        }
        return paciente
    }

    suspend fun obtenerSiguienteIdNumerico(): Int {
        val maxId = pacienteDao.obtenerMaxIdNumerico() ?: 1000
        return maxId + 1
    }

    suspend fun actualizarAccesoPaciente(pacienteId: String) {
        pacienteDao.actualizarTimestampAcceso(pacienteId, System.currentTimeMillis())
    }

    suspend fun actualizarNombrePaciente(pacienteId: String, nuevoNombre: String) {
        pacienteDao.actualizarNombreEImplicitamenteTimestamp(pacienteId, nuevoNombre, System.currentTimeMillis())
    }

    suspend fun guardarPruebaRealizada(prueba: PruebaRealizada): PruebaRealizada? {
        Log.d("PacienteRepo", "Inicio de guardarPruebaRealizada para paciente ${prueba.pacienteId}, numeroPrueba ${prueba.numeroPruebaPaciente}")
        // El pruebaId se autogenerará si es 0
        pruebaRealizadaDao.insertarPrueba(prueba)
        Log.i("PacienteRepo", "Llamada a pruebaRealizadaDao.insertarPrueba completada para paciente ${prueba.pacienteId}")

        // Después de insertar, obtenemos la prueba más reciente para tener su ID autogenerado
        val pruebaGuardadaConId = pruebaRealizadaDao.getPruebaMasRecienteParaPaciente(prueba.pacienteId)

        if (pruebaGuardadaConId != null && pruebaGuardadaConId.numeroPruebaPaciente == prueba.numeroPruebaPaciente) {
            Log.i("PacienteRepo", "Prueba guardada y recuperada con ID: ${pruebaGuardadaConId.pruebaId} para paciente ${prueba.pacienteId}")
            actualizarEstadoHistorialPaciente(prueba.pacienteId, true)
            return pruebaGuardadaConId
        } else {
            Log.e("PacienteRepo", "Error: No se pudo recuperar la prueba recién guardada o el numeroPruebaPaciente no coincide.")
            // Aún actualizamos el historial, ya que algo se insertó.
            actualizarEstadoHistorialPaciente(prueba.pacienteId, true)
            return null // O manejar el error de forma más robusta
        }
    }

    suspend fun actualizarPruebaRealizada(prueba: PruebaRealizada) {
        Log.d("PacienteRepo", "Actualizando prueba ID ${prueba.pruebaId} para paciente ${prueba.pacienteId}")
        pruebaRealizadaDao.actualizarPrueba(prueba)
        Log.i("PacienteRepo", "Prueba ID ${prueba.pruebaId} actualizada.")
        // No es necesario actualizar el estado del historial aquí, ya que la prueba ya existía.
    }

    suspend fun getNumeroPruebaById(idDeLaPrueba: Int): Int? {
        return pruebaRealizadaDao.getNumeroPruebaById(idDeLaPrueba)
    }

    suspend fun getProximoNumeroPruebaParaPaciente(pacienteId: String): Int {
        val numeroDePruebasExistentes =
            pruebaRealizadaDao.getConteoPruebasDePacienteSync(pacienteId)
        return numeroDePruebasExistentes + 1
    }

    suspend fun actualizarEstadoHistorialPaciente(pacienteId: String, tieneHistorial: Boolean) {
        Log.d("PacienteRepo", "Actualizando estado historial para paciente ID: $pacienteId a: $tieneHistorial")
        try {
            pacienteDao.actualizarEstadoHistorial(pacienteId, tieneHistorial)
            Log.d("PacienteRepo", "Estado historial actualizado en DAO para paciente ID: $pacienteId")
        } catch (e: Exception) {
            Log.e("PacienteRepo", "Error al actualizar estado historial para ID: $pacienteId", e)
        }
    }

    fun getPacientesConEstadoHistorialCombinado(): Flow<List<PacienteConHistorialReal>> {
        return todosLosPacientesOrdenados
            .combine(
                pruebaRealizadaDao.observarTodosLosConteosDePruebas()
                    .onStart { emit(emptyList<ConteoPorPaciente>()) }
                    .distinctUntilChanged()
            ) { pacientes, conteos ->
                Log.d("RepoCombine", "Combinando ${pacientes.size} pacientes con ${conteos.size} registros de conteo.")
                val conteosMap = conteos.associateBy({ it.pacienteId }, { it.conteoPruebas })

                pacientes.map { paciente ->
                    val conteoActual = conteosMap[paciente.id] ?: 0
                    val tieneHistorialRealCalculado = conteoActual > 0

                    if (paciente.tieneHistorial != tieneHistorialRealCalculado) {
                        Log.w("RepoCombineMap", "Discrepancia para Paciente ID: ${paciente.id}. " +
                                "BD.tieneHistorial: ${paciente.tieneHistorial}, " +
                                "Conteo ($conteoActual) implica: $tieneHistorialRealCalculado")
                    }

                    PacienteConHistorialReal(
                        paciente = paciente,
                        tieneHistorialReal = tieneHistorialRealCalculado
                    )
                }
            }
            .onStart { Log.d("RepoCombine", "getPacientesConEstadoHistorialCombinado Flow iniciado.") }
            .catch { e ->
                Log.e("RepoCombine", "Error en getPacientesConEstadoHistorialCombinado Flow", e)
                emit(emptyList<PacienteConHistorialReal>())
            }
    }

    suspend fun getPruebaMasRecienteParaPaciente(pacienteId: String): PruebaRealizada? {
        return pruebaRealizadaDao.getPruebaMasRecienteParaPaciente(pacienteId)
    }
}
