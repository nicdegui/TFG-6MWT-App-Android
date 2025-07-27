package com.example.app6mwt.di

import android.content.Context
import com.example.app6mwt.data.PacienteRepository
import com.example.app6mwt.data.local.AppDatabase
import com.example.app6mwt.data.local.PacienteDao
import com.example.app6mwt.data.local.PruebaRealizadaDao
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

// Calificador para el CoroutineDispatcher de IO
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IoDispatcher

// Calificador para el CoroutineDispatcher Default (si lo necesitas en otro lugar)
// @Retention(AnnotationRetention.BINARY)
// @Qualifier
// annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // --- DISPATCHERS ---
    @Provides
    @Singleton
    @IoDispatcher
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    // --- COROUTINE SCOPES ---
    @Singleton
    @Provides
    fun providesApplicationCoroutineScope(
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): CoroutineScope {
        return CoroutineScope(SupervisorJob() + ioDispatcher)
    }

    // --- ROOM DATABASE Y DAO ---
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun providePacienteDao(appDatabase: AppDatabase): PacienteDao {
        return appDatabase.pacienteDao()
    }

    @Provides
    fun providePruebaRealizadaDao(appDatabase: AppDatabase): PruebaRealizadaDao {
        return appDatabase.pruebaRealizadaDao()
    }

    // --- REPOSITORIES ---
    @Provides
    @Singleton
    fun providePacienteRepository(pacienteDao: PacienteDao, pruebaRealizadaDao: PruebaRealizadaDao): PacienteRepository {
        return PacienteRepository(pacienteDao, pruebaRealizadaDao)
    }

    // --- SERIALIZATION ---  <--- NUEVA SECCIÓN O DONDE PREFIERAS UBICARLO ---
    @Provides
    @Singleton // Gson suele ser un singleton
    fun provideGson(): Gson {
        return Gson()
        // Si necesitas configuraciones especiales para Gson (ej. GsonBuilder):
        // return GsonBuilder()
        //     .registerTypeAdapter(Date::class.java, DateTypeAdapter()) // Ejemplo
        //     .create()
    }

    // ... Aquí irían tus proveedores para BluetoothService si los tenías en otro módulo
    // o si decides consolidarlos. Si ya los tienes en BluetoothServiceModule y funciona,
    // puedes dejarlos allí.
}
