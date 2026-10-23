package com.cerlita.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Parser JSON tolerante del catálogo Cerlita. */
internal val JsonCerlita = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Formato del catálogo remoto (content/preguntas.json del repositorio):
 * {
 *   "version": "1.0.0",
 *   "updated_at": "2026-10-22T10:00:00Z",
 *   "preguntas": [
 *     { "id": "q_001", "tema": "viajes", "tipo": "si_no",
 *       "texto": "...", "opciones": ["Sí","No"] }
 *   ]
 * }
 */
@Serializable
data class CatalogoDTO(
    val version: String,
    @SerialName("updated_at") val actualizadoEn: String? = null,
    val preguntas: List<PreguntaDTO> = emptyList(),
)

@Serializable
data class PreguntaDTO(
    val id: String,
    val tema: String,
    val tipo: String,
    val texto: String,
    val opciones: List<String> = emptyList(),
)
