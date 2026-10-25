package com.cerlita.domain.model

/**
 * Capa de dominio del modo pareja: todo lo que viaja entre la cerdita y el
 * koala por Nearby Connections (P2P, sin internet).
 *
 * La implementación del canal vive en `core:network` como `CanalPareja`
 * (definido junto al resto de interfaces en `domain/repository`); la app solo
 * ve estas abstracciones, así el ViewModel es testeable sin Play Services.
 */

/** Estados del canal P2P entre los dos teléfonos. */
enum class EstadoP2P {
    /** Sin actividad: no se ha buscado a nadie. */
    INACTIVO,

    /** Anunciando + descubriendo a la vez (ambos roles, P2P_CLUSTER). */
    BUSCANDO,

    /** Solicitud de conexión en curso. */
    CONECTANDO,

    /** Conexión establecida y saludo intercambiado. */
    CONECTADO,

    /** Se perdió el enlace a mitad de sesión; la ronda sigue en Room. */
    DESCONECTADO,
}

/** Dispositivo Cerlita cercano encontrado durante la búsqueda. */
data class EndpointPareja(
    /** Id efímero del endpoint Nearby (cambia en cada sesión). */
    val id: String,
    /** Nombre con el que el otro se anuncia (su perfil). */
    val nombre: String,
)

/** Identidad de un jugador: cómo se presenta al otro teléfono. */
data class InfoJugador(
    val nombre: String,
    val animal: Animal,
)

/**
 * Mensajes del protocolo Cerlita P2P. El canal los entrega ya decodificados;
 * cualquier tipo desconocido de un teléfono con versión más nueva se ignora
 * silenciosamente (compatibilidad hacia adelante).
 */
sealed interface MensajePareja {

    /** Presentación al establecer la conexión (ambos lados la envían). */
    data class Saludo(val info: InfoJugador) : MensajePareja

    /**
     * El host propone la ronda: id compartido de ronda + preguntas completas
     * (así el invitado juega aunque su catálogo sea distinto o más viejo)
     * + el modo de juego elegido y el modo efectivo de cada pregunta
     * (ambos teléfonos juegan la misma secuencia sin volver a hablar).
     */
    data class InicioRonda(
        val rondaId: String,
        val preguntas: List<Pregunta>,
        val modoJuego: ModoJuego = ModoJuego.AMBOS_RESPONDEN,
        val modosPorPregunta: List<ModoJuego> = emptyList(),
    ) : MensajePareja

    /** Una respuesta del otro jugador; se persiste en Room tal cual. */
    data class RespuestaRecibida(
        val rondaId: String,
        val preguntaId: String,
        val autor: Animal,
        val valor: String,
    ) : MensajePareja

    /** El otro jugador terminó la ronda (aviso informativo). */
    data class FinRonda(val rondaId: String) : MensajePareja
}
