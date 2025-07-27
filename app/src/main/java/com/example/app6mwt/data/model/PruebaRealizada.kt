package com.example.app6mwt.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.app6mwt.data.local.DataConverter
import com.example.app6mwt.ui.PruebaCompletaDetalles

@Entity(
    tableName = "pruebas_realizadas",
    foreignKeys = [ForeignKey(entity = Paciente::class,
        parentColumns = ["id"],
        childColumns = ["pacienteId"],
        onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["pacienteId"])
    ]
)

@TypeConverters(DataConverter::class)
data class PruebaRealizada(
    @PrimaryKey(autoGenerate = true)
    val pruebaId: Int = 0,
    val pacienteId: String,
    val fechaTimestamp: Long,
    val numeroPruebaPaciente: Int,
    val distanciaRecorrida: Float,
    val porcentajeTeorico: Float,
    val spo2min: Int,
    val stops: Int,
    val datosCompletos: PruebaCompletaDetalles?
)
