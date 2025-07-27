package com.example.app6mwt.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.app6mwt.data.model.Paciente
import kotlinx.coroutines.Dispatchers
import com.example.app6mwt.data.model.PruebaRealizada
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(entities = [Paciente::class, PruebaRealizada::class], version = 11, exportSchema = false)
@TypeConverters(DataConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pacienteDao(): PacienteDao
    abstract fun pruebaRealizadaDao(): PruebaRealizadaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val databaseWriteExecutor = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun getDatabase(
            context: Context
        ): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "6mwt_app_database"
                )
                    .addCallback(AppDatabaseCallback())
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback() : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            Log.d("AppDatabaseCallback", "onCreate CALLED - Database schema will be created.)")
            databaseWriteExecutor.launch {
                Log.d("AppDatabaseCallback", "Coroutine launched for populateInitialData.")
                INSTANCE?.let { database ->
                    Log.d("AppDatabaseCallback", "INSTANCE found. Populating data...")
                    populateInitialData(database.pacienteDao())
                } ?: run {
                    Log.e("AppDatabaseCallback", "INSTANCE was NULL in coroutine. Population FAILED.")
                }
            }
        }

        suspend fun populateInitialData(pacienteDao: PacienteDao) {
            Log.d("AppDatabaseCallback", "populateInitialData STARTING")
            try {
                val pacientes = listOf(
                    Paciente(
                        "1001",
                        "Ana Pérez García",
                        ultimoAccesoTimestamp = System.currentTimeMillis() - 90000000
                    ),
                    Paciente(
                        "1002",
                        "Marta Gómez Sánchez",
                        ultimoAccesoTimestamp = System.currentTimeMillis() - 70000000
                    ),
                    Paciente(
                        "1003",
                        "Elena Torres Vazquez",
                        ultimoAccesoTimestamp = System.currentTimeMillis() - 50000000
                    )
                )
                Log.d("AppDatabaseCallback", "Número de pacientes a insertar: ${pacientes.size}")

                pacientes.forEach { paciente ->
                    Log.d(
                        "AppDatabaseCallback",
                        "Intentando insertar paciente: ID ${paciente.id} - Nombre ${paciente.nombre}"
                    )
                    try {
                        pacienteDao.insertarOActualizarPaciente(paciente)
                        Log.d(
                            "AppDatabaseCallback",
                            "ÉXITO al insertar paciente: ID ${paciente.id}"
                        )
                    } catch (e: Exception) {
                        Log.e(
                            "AppDatabaseCallback",
                            "ERROR al insertar paciente: ID ${paciente.id}",
                            e
                        )
                    }
                }
                Log.d("AppDatabaseCallback", "Todos los pacientes procesados en el bucle.")
            } catch (e: Exception) {
                Log.e("AppDatabaseCallback", "ERROR GENERAL en populateInitialData", e)
            } finally {
                Log.d("AppDatabaseCallback", "populateInitialData FINISHED (bloque finally)")
            }
        }
    }
}
