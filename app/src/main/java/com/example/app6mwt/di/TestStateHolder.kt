package com.example.app6mwt.di

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

// Data class para los datos de recuperación MODIFICADA
data class RecoveryData(
    val spo2: Int? = null, // Hacemos nullable para el caso de timeout sin datos
    val hr: Int? = null,   // Hacemos nullable para el caso de timeout sin datos
    val isRecoveryPeriodOver: Boolean,
    val wasDataCapturedDuringPeriod: Boolean
)

@Singleton
class TestStateHolder @Inject constructor() {
    // Usaremos replay = 1 para que el último estado emitido esté disponible para nuevos colectores
    // y emitimos un valor inicial si es necesario, o lo dejamos para la primera emisión real.
    // Considera MutableStateFlow si siempre quieres tener un valor inicial y solo el último.
    // SharedFlow con replay=1 es bueno si quieres "eventos" pero TestResultsVM podría llegar tarde.
    // Para este caso, un MutableStateFlow podría ser incluso más adecuado si solo interesa el *último* estado de recuperación.
    // Vamos a probar con SharedFlow(replay=1) por ahora, ya que es lo que tienes.
    private val _recoveryDataFlow = MutableSharedFlow<RecoveryData>(replay = 1)
    val recoveryDataFlow = _recoveryDataFlow.asSharedFlow()

    suspend fun postRecoveryData(data: RecoveryData) {
        // Si usamos MutableSharedFlow(replay=1), tryEmit es seguro desde coroutines.
        // Si fuera un StateFlow, sería _recoveryDataFlow.value = data
        val emitted = _recoveryDataFlow.tryEmit(data) // tryEmit es no suspendible
        if (!emitted) {
            // Esto podría pasar si el buffer está lleno y no hay suscriptores rápidos,
            // pero con replay=1 y un único emisor y colector principal, debería ser raro.
            // Para más robustez, podrías usar _recoveryDataFlow.emit(data) si esperas backpressure.
            // Dado el uso, tryEmit debería estar bien.
            println("WARN: TestStateHolder - No se pudo emitir RecoveryData inmediatamente.")
            // _recoveryDataFlow.emit(data) // Alternativa suspendible si tryEmit falla y es crítico
        }
    }

    // Nueva función para limpiar/resetear el estado si es necesario al iniciar una nueva prueba
    fun resetRecoveryState() {
        // Esto es útil si TestResultsViewModel podría observar datos viejos de una prueba anterior
        // si se navega muy rápido.
        // Si TestExecutionViewModel siempre emite un nuevo estado (incluso de timeout),
        // esto podría no ser estrictamente necesario, pero es una buena práctica de limpieza.
        // Considera el valor inicial que tendría sentido.
        // Por ejemplo, un estado "pendiente"
        _recoveryDataFlow.tryEmit(
            RecoveryData(
                spo2 = null,
                hr = null,
                isRecoveryPeriodOver = false,
                wasDataCapturedDuringPeriod = false
            )
        )
    }
}
