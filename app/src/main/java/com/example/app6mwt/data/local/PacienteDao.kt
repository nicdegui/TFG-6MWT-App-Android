package com.example.app6mwt.data.local

import androidx.room.*
import com.example.app6mwt.data.model.Paciente
import kotlinx.coroutines.flow.Flow

@Dao
interface PacienteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarOActualizarPaciente(paciente: Paciente)

    @Query("DELETE FROM pacientes WHERE id = :pacienteId")
    suspend fun eliminarPacientePorId(pacienteId: String)

    @Query("SELECT * FROM pacientes WHERE id = :id")
    suspend fun obtenerPacientePorId(id: String): Paciente?

    @Query("SELECT * FROM pacientes ORDER BY ultimoAccesoTimestamp DESC")
    fun observarTodosLosPacientes(): Flow<List<Paciente>>

    @Query("SELECT MAX(CAST(id AS INTEGER)) FROM pacientes")
    suspend fun obtenerMaxIdNumerico(): Int?

    @Query("UPDATE pacientes SET tieneHistorial = :tieneHistorial WHERE id = :pacienteId")
    suspend fun actualizarEstadoHistorial(pacienteId: String, tieneHistorial: Boolean)

    @Query("UPDATE pacientes SET ultimoAccesoTimestamp = :timestamp WHERE id = :pacienteId")
    suspend fun actualizarTimestampAcceso(pacienteId: String, timestamp: Long)

    @Query("UPDATE pacientes SET nombre = :nuevoNombre, ultimoAccesoTimestamp = :timestamp WHERE id = :pacienteId")
    suspend fun actualizarNombreEImplicitamenteTimestamp(pacienteId: String, nuevoNombre: String, timestamp: Long)

}
