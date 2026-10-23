package com.cerlita.core.network

import com.cerlita.domain.model.Animal
import com.cerlita.domain.model.InfoJugador
import com.cerlita.domain.model.MensajePareja
import com.cerlita.domain.model.OrigenPregunta
import com.cerlita.domain.model.Pregunta
import com.cerlita.domain.model.TipoPregunta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Protocolo de bytes Cerlita P2P.
 *
 * Cada payload es un JSON único (`SobreP2P`) con un campo `tipo` y exactamente
 * un cuerpo por tipo. Un teléfono con versión más nueva que agregue tipos
 * desconocidos no rompe al otro: [ProtocoloP2P.decodificar] devuelve null y
 * el mensaje se ignora silenciosamente (compatibilidad hacia adelante).
 *
 * Estructura de ejemplo (inicio de ronda):
 * ```
 * {"tipo":"inicio","inicio":{"rondaId":"…","preguntas":[
 *   {"id":"q_001","tema":"viajes","tipo":"SI_NO","texto":"…","opciones":[]}]}}
 * ```
 */
internal object ProtocoloP2P {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = false
    }

    internal const val TIPO_SALUDO = "saludo"
    internal const val TIPO_INICIO = "inicio"
    internal const val TIPO_RESPUESTA = "respuesta"
    internal const val TIPO_FIN = "fin"

    // ------------------------------------------------------------------
    // DTOs de transporte (nada de tipos de dominio serializados directo:
    // el cable es tolerante y versionable)
    // ------------------------------------------------------------------

    @Serializable
    internal data class SobreP2P(
        val tipo: String,
        val saludo: SaludoDTO? = null,
        val inicio: InicioRondaDTO? = null,
        val respuesta: RespuestaDTO? = null,
        val fin: FinRondaDTO? = null,
    )

    @Serializable
    internal data class SaludoDTO(
        val nombre: String,
        val animal: String,
    )

    @Serializable
    internal data class InicioRondaDTO(
        val rondaId: String,
        val preguntas: List<PreguntaDTO> = emptyList(),
    )

    @Serializable
    internal data class PreguntaDTO(
        val id: String,
        val tema: String,
        val tipo: String,
        val texto: String,
        val opciones: List<String> = emptyList(),
        val origen: String = "OFICIAL",
    )

    @Serializable
    internal data class RespuestaDTO(
        val rondaId: String,
        val preguntaId: String,
        val autor: String,
        val valor: String,
    )

    @Serializable
    internal data class FinRondaDTO(
        val rondaId: String,
    )

    // ------------------------------------------------------------------
    // Codificación: dominio -> bytes
    // ------------------------------------------------------------------

    fun codificar(mensaje: MensajePareja): ByteArray = json.encodeToString(
        SobreP2P.serializer(),
        mensaje.aSobre(),
    ).encodeToByteArray()

    private fun MensajePareja.aSobre(): SobreP2P = when (this) {
        is MensajePareja.Saludo -> SobreP2P(
            tipo = TIPO_SALUDO,
            saludo = SaludoDTO(nombre = info.nombre, animal = info.animal.name),
        )

        is MensajePareja.InicioRonda -> SobreP2P(
            tipo = TIPO_INICIO,
            inicio = InicioRondaDTO(
                rondaId = rondaId,
                preguntas = preguntas.map { it.aDTO() },
            ),
        )

        is MensajePareja.RespuestaRecibida -> SobreP2P(
            tipo = TIPO_RESPUESTA,
            respuesta = RespuestaDTO(
                rondaId = rondaId,
                preguntaId = preguntaId,
                autor = autor.name,
                valor = valor,
            ),
        )

        is MensajePareja.FinRonda -> SobreP2P(
            tipo = TIPO_FIN,
            fin = FinRondaDTO(rondaId = rondaId),
        )
    }

    private fun Pregunta.aDTO(): PreguntaDTO = PreguntaDTO(
        id = id,
        tema = tema,
        tipo = tipo.name,
        texto = texto,
        opciones = opciones,
        origen = origen.name,
    )

    // ------------------------------------------------------------------
    // Decodificación: bytes -> dominio (tolerante a errores y versiones)
    // ------------------------------------------------------------------

    /** Decodifica un payload entrante; null si es basura o tipo desconocido. */
    fun decodificar(bytes: ByteArray): MensajePareja? = runCatching {
        json.decodeFromString(SobreP2P.serializer(), bytes.decodeToString())
    }.getOrNull()?.aMensaje()

    private fun SobreP2P.aMensaje(): MensajePareja? = when (tipo) {
        TIPO_SALUDO -> saludo?.let {
            MensajePareja.Saludo(
                InfoJugador(
                    nombre = it.nombre,
                    animal = it.animal.comoAnimal() ?: Animal.CERDITA,
                ),
            )
        }

        TIPO_INICIO -> inicio?.let {
            val preguntas = it.preguntas.mapNotNull { p -> p.aDominio() }
            if (preguntas.isEmpty()) return@let null
            MensajePareja.InicioRonda(rondaId = it.rondaId, preguntas = preguntas)
        }

        TIPO_RESPUESTA -> respuesta?.let { r ->
            val autor = r.autor.comoAnimal() ?: return@let null
            MensajePareja.RespuestaRecibida(
                rondaId = r.rondaId,
                preguntaId = r.preguntaId,
                autor = autor,
                valor = r.valor,
            )
        }

        TIPO_FIN -> fin?.let { MensajePareja.FinRonda(rondaId = it.rondaId) }

        // Tipo de una versión futura: se ignora y se sigue jugando.
        else -> null
    }

    private fun PreguntaDTO.aDominio(): Pregunta? {
        val tipo = tipo.comoTipo() ?: return null
        if (texto.isBlank()) return null
        if (tipo == TipoPregunta.OPCIONES && opciones.size !in 2..Pregunta.MAX_OPCIONES) return null
        return Pregunta(
            id = id,
            tema = tema,
            tipo = tipo,
            texto = texto,
            opciones = opciones,
            origen = origen.comoOrigen() ?: OrigenPregunta.OFICIAL,
            activa = true,
        )
    }
}

// ------------------------------------------------------------------
// Parsing tolerante (mismo criterio que data/mapper/Mapeadores.kt)
// ------------------------------------------------------------------

private fun String.comoTipo(): TipoPregunta? =
    runCatching { TipoPregunta.valueOf(trim().uppercase()) }.getOrNull()

private fun String.comoAnimal(): Animal? =
    runCatching { Animal.valueOf(trim().uppercase()) }.getOrNull()

private fun String.comoOrigen(): OrigenPregunta? =
    runCatching { OrigenPregunta.valueOf(trim().uppercase()) }.getOrNull()
