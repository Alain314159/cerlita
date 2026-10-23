package com.cerlita.domain.repository

import com.cerlita.domain.model.CatalogoPreguntas
import com.cerlita.domain.model.EstadoP2P
import com.cerlita.domain.model.EndpointPareja
import com.cerlita.domain.model.InfoJugador
import com.cerlita.domain.model.MensajePareja
import com.cerlita.domain.model.Perfil
import com.cerlita.domain.model.Pregunta
import com.cerlita.domain.model.Respuesta
import com.cerlita.domain.model.Ronda
import com.cerlita.domain.model.TipoPregunta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

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

    /**
     * Persiste preguntas recibidas por P2P del otro teléfono (ronda pareja):
     * reemplaza por id, conservando su origen. Así el invitado juega aunque
     * su catálogo sea más viejo o no tenga la pregunta del host.
     */
    suspend fun sincronizarExternas(preguntas: List<Pregunta>)
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

    /** Ronda por id, sin importar su estado (para validar respuestas P2P). */
    suspend fun getRonda(rondaId: String): Ronda?

    suspend fun crearRonda(preguntaIds: List<String>): Ronda

    /**
     * Crea la ronda de pareja con el id que generó el host (compartido por
     * P2P): ambos teléfonos deben persistir la misma ronda para que las
     * respuestas cruzadas casen por id.
     */
    suspend fun crearRondaPareja(id: String, preguntaIds: List<String>): Ronda

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

/**
 * Canal de comunicación de pareja (P2P sin internet).
 *
 * Implementado por `CerlitaNearbyClient` sobre Google Nearby Connections.
 * El objeto vive en el contenedor de la app (singleton): sobrevive al cambio
 * de pantallas para que las respuestas lleguen aunque el lobby se cierre.
 */
interface CanalPareja {
    /** Estado actual del enlace. */
    val estado: StateFlow<EstadoP2P>

    /** Dispositivos Cerlita cercanos mientras se busca. */
    val encontrados: StateFlow<List<EndpointPareja>>

    /** Información del otro jugador (llega con su saludo). */
    val otro: StateFlow<InfoJugador?>

    /**
     * Verdad si este teléfono inició la conexión: quien conecta decide la
     * ronda (host). Determinístico, sin carreras.
     */
    val soyHost: StateFlow<Boolean>

    /** Flujo de mensajes entrantes ya decodificados. */
    val mensajes: SharedFlow<MensajePareja>

    /** Errores de transporte legibles (sin tecnicismos), para mostrar en UI. */
    val errores: SharedFlow<String>

    /** Empieza a anunciar (con mi nombre) y a descubrir a la vez. */
    fun buscar(miInfo: InfoJugador)

    /** Solicita conexión con un endpoint encontrado (me vuelve host). */
    fun conectarA(endpointId: String)

    /** Envía un mensaje al otro teléfono (no-op si no hay enlace). */
    fun enviar(mensaje: MensajePareja)

    /** Corta todo: endpoints, anuncios y descubrimiento. */
    fun detener()
}
