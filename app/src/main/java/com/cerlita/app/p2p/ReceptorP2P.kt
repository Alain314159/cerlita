package com.cerlita.app.p2p

import com.cerlita.domain.model.EstadoRonda
import com.cerlita.domain.model.MensajePareja
import com.cerlita.domain.model.ModoRonda
import com.cerlita.domain.model.Respuesta
import com.cerlita.domain.repository.CanalPareja
import com.cerlita.domain.repository.RondaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Receptor P2P a nivel de aplicación: persiste en Room las respuestas de la
 * pareja aunque la pantalla de juego esté cerrada o el ViewModel muerto.
 *
 * Un SharedFlow sin suscriptores descarta los mensajes, así que la única
 * forma de no perder respuestas cruzadas es colectar en un alcance que viva
 * tanto como el proceso. La escritura es idempotente gracias al índice único
 * (ronda, pregunta, autor), así que da igual que la pantalla de juego también
 * escuche: la segunda escritura reemplaza, no duplica.
 */
class ReceptorP2P(
    canal: CanalPareja,
    private val rondaRepository: RondaRepository,
    alcance: CoroutineScope,
) {
    init {
        alcance.launch {
            canal.mensajes.collect { mensaje ->
                if (mensaje !is MensajePareja.RespuestaRecibida) return@collect
                // Solo si la ronda pareja existe aquí y sigue en curso; una
                // respuesta de una ronda vieja no se resucita.
                val ronda = rondaRepository.getRonda(mensaje.rondaId) ?: return@collect
                if (ronda.modo != ModoRonda.PAREJA || ronda.estado != EstadoRonda.EN_CURSO) {
                    return@collect
                }
                rondaRepository.guardarRespuesta(
                    Respuesta(
                        preguntaId = mensaje.preguntaId,
                        sesionId = mensaje.rondaId,
                        autor = mensaje.autor,
                        valor = mensaje.valor,
                    ),
                )
            }
        }
    }
}
