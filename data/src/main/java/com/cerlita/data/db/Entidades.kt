package com.cerlita.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "preguntas")
data class PreguntaEntity(
    @PrimaryKey val id: String,
    val tema: String,
    val tipo: String,
    val texto: String,
    val opcionesJson: String = "[]",
    val origen: String,
    val activa: Boolean = true,
    val agregadaEn: Long,
)

@Entity(
    tableName = "respuestas",
    indices = [
        Index("sesionId"),
        Index("preguntaId"),
        // Una respuesta por persona y pregunta dentro de una ronda: hace
        // idempotentes los reintentos P2P (mismo mensaje -> reemplaza, no duplica).
        Index(value = ["sesionId", "preguntaId", "autor"], unique = true),
    ],
)
data class RespuestaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val preguntaId: String,
    val sesionId: String,
    val autor: String,
    val valor: String,
    val fecha: Long,
)

@Entity(tableName = "rondas")
data class RondaEntity(
    @PrimaryKey val id: String,
    val modo: String,
    val estado: String,
    val preguntaIdsJson: String,
    val indiceActual: Int,
    val creada: Long,
    val finalizada: Long? = null,
)

@Entity(tableName = "perfil")
data class PerfilEntity(
    @PrimaryKey val id: Int = 1,
    val nombrePropio: String,
    val animal: String,
    val nombrePareja: String,
    val actualizado: Long,
)
