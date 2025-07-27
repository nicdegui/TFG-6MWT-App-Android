package com.example.app6mwt.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pacientes")
data class Paciente(
    @PrimaryKey val id: String,
    var nombre: String,
    var tieneHistorial: Boolean = false,
    var ultimoAccesoTimestamp: Long = System.currentTimeMillis()
)
