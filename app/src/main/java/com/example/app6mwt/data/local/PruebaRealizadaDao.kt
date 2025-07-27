package com.example.app6mwt.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.app6mwt.data.model.PruebaRealizada
import kotlinx.coroutines.flow.Flow

data class ConteoPorPaciente(
    val pacienteId: String,
    val conteoPruebas: Int
)

@Dao
interface PruebaRealizadaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarPrueba(prueba: PruebaRealizada)

    @Update
    suspend fun actualizarPrueba(prueba: PruebaRealizada)

    @Query("SELECT * FROM pruebas_realizadas WHERE pacienteId = :pacienteId ORDER BY fechaTimestamp DESC")
    fun observarPruebasDePaciente(pacienteId: String): Flow<List<PruebaRealizada>>

    @Query("SELECT COUNT(pruebaId) FROM pruebas_realizadas WHERE pacienteId = :pacienteId")
    suspend fun getConteoPruebasDePacienteSync(pacienteId: String): Int

    @Query("DELETE FROM pruebas_realizadas WHERE pruebaId = :idDeLaPrueba")
    suspend fun eliminarPruebaPorSuId(idDeLaPrueba: Int)

    @Query("DELETE FROM pruebas_realizadas WHERE pacienteId = :pacienteId")
    suspend fun eliminarTodasLasPruebasDePaciente(pacienteId: String)

    @Query("SELECT pacienteId, COUNT(pruebaId) as conteoPruebas FROM pruebas_realizadas GROUP BY pacienteId")
    fun observarTodosLosConteosDePruebas(): Flow<List<ConteoPorPaciente>>

    @Query("SELECT * FROM pruebas_realizadas WHERE pacienteId = :pacienteId")
    suspend fun getPruebasPorPacienteIdDirecto(pacienteId: String): List<PruebaRealizada>

    @Query("SELECT * FROM pruebas_realizadas WHERE pacienteId = :pacienteId ORDER BY fechaTimestamp DESC LIMIT 1")
    suspend fun getPruebaMasRecienteParaPaciente(pacienteId: String): PruebaRealizada?

    @Query("SELECT numeroPruebaPaciente FROM pruebas_realizadas WHERE pruebaId = :idDeLaPrueba")
    suspend fun getNumeroPruebaById(idDeLaPrueba: Int): Int?

}
