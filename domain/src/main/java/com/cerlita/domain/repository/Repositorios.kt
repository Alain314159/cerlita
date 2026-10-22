package com.cerlita.domain.repository

import com.cerlita.domain.model.CatalogoPreguntas
import com.cerlita.domain.model.Perfil
import com.cerlita.domain.model.Pregunta
import com.cerlita.domain.model.Respuesta
import com.cerlita.domain.model.Ronda
import com.cerlita.domain.model.TipoPregunta
import kotlinx.coroutines.flow.Flow

/** Banco de preguntas local (catálogo oficial + preguntas creadas por el usuario). */
interface PreguntaRepository {
    fun observePreguntasActivas(): Flow<List<Pregunta>>
    fun observePreguntasCustom(): Flow<List<Pregunta>>
    fun observeContadores(): Flow<ContadoresPreguntas>
    suspend fun getPregunta(id: String): Pregunta?
    suspend fun getPreguntasPorIds(ids: List<String>): List<Pregunta>
    suspend fun getRandomPreguntas(
        cantidad: Int,
        incluirOficiales: Boolean = true,
        incluirCustom: Boolean = true,
    ): List<Pregunta>
    suspend fun upsertCustom(texto: String, tema: String, tipo: TipoPregunta, opciones: List<String>): Pregunta
    suspend fun actualizarCustom(pregunta: Pregunta)
    suspend fun setActiva(id: String, activa: Boolean)
    suspend fun eliminar(id: String)
}

data class ContadoresPreguntas(
    val oficialesActivas: Int = 0,
    val customActivas: Int = 0,
    val totalRespuestas: Long = 0L,
)

/** Rondas y respuestas persistidas (offline-first). */
interface RondaRepository {
    fun observeRondaActiva(): Flow<Ronda?>
    fun observeRespuestasDeRonda(rondaId: String): Flow<List<Respuesta>>
    suspend fun crearRonda(preguntaIds: List<String>): Ronda
    suspend fun avanzarIndice(rondaId: String, indice: Int)
    suspend fun finalizarRonda(rondaId: String)
    suspend fun guardarRespuesta(respuesta: Respuesta)
}

/** Perfil local del jugador. */
interface PerfilRepository {
    val perfil: Flow<Perfil>
    suspend fun guardarPerfil(perfil: Perfil)
}

/** Catálogo remoto alojado en GitHub (raw URL del repositorio). */
interface CatalogoRemotoRepository {
    suspend fun buscarActualizaciones(): Result<CatalogoPreguntas>
}
