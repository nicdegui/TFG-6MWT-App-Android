package com.example.app6mwt.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.example.app6mwt.ui.PruebaCompletaDetalles

class DataConverter {
    private val gson = Gson()


    @TypeConverter
    fun fromPruebaCompletaDetalles(value: PruebaCompletaDetalles?): String? {
        // Convierte el objeto PruebaCompletaDetalles a una cadena JSON.
        // Si el valor es nulo, devuelve nulo.
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toPruebaCompletaDetalles(value: String?): PruebaCompletaDetalles? {
        // Convierte una cadena JSON de vuelta a un objeto PruebaCompletaDetalles.
        // Si la cadena es nula, devuelve nulo.
        return value?.let { gson.fromJson(it, PruebaCompletaDetalles::class.java) }
    }

    // --- IMPORTANTE ---
    // Si dentro de PruebaCompletaDetalles o TestExecutionSummaryData tienes campos
    // como List<ObjetoComplejoNoEstándar> o Map<Key, ObjetoComplejoNoEstándar>
    // y Gson no sabe cómo serializarlos/deserializarlos por defecto,
    // podrías necesitar usar TypeToken aquí o registrar TypeAdapters personalizados
    // con tu instancia de `gson`.
    // Ejemplo (si fuera necesario para una lista de objetos personalizados):
    /*
    @TypeConverter
    fun fromMiListaDeObjetos(value: List<MiObjetoPersonalizado>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toMiListaDeObjetos(value: String?): List<MiObjetoPersonalizado>? {
        return value?.let {
            val listType = object : TypeToken<List<MiObjetoPersonalizado>>() {}.type
            gson.fromJson(it, listType)
        }
    }
    */
    // PERO para la estructura que has mostrado (TestExecutionSummaryData con listas y mapas
    // de data classes simples), Gson generalmente maneja esto bien sin TypeTokens explícitos
    // en los métodos del TypeConverter para el objeto principal (PruebaCompletaDetalles).
    // El TypeConverter principal solo se preocupa por convertir PruebaCompletaDetalles a/desde String.
    // Lo que está *dentro* de PruebaCompletaDetalles es manejado por la serialización recursiva de Gson.
}
