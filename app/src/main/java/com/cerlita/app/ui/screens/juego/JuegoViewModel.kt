package com.cerlita.app.ui.screens.juego

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerlita.domain.model.EstadoP2P
import com.cerlita.domain.model.MensajePareja
import com.cerlita.domain.model.ModoRonda
import com.cerlita.domain.model.Perfil
import com.cerlita.domain.model.Pregunta
import com.cerlita.domain.model.Respuesta
import com.cerlita.domain.model.Ronda
import com.cerlita.domain.model.TipoPregunta
import com.cerlita.domain.repository.CanalPareja
import com.cerlita.domain.repository.PerfilRepository
import com.cerlita.domain.repository.PreguntaRepository
import com.cerlita.domain.repository.RondaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Una entrada del resumen: la pregunta y las dos respuestas (si las hubo). */
data class ResumenItem(
    val pregunta: Pregunta,
    val mia: Respuesta?,
    val suya: Respuesta?,
) {
    /** Coinciden y no es texto libre: se celebra con un chip. */
    val coinciden: Boolean
        get() = mia != null && suya != null &&
            mia.valor == suya.valor &&
            pregunta.tipo != TipoPregunta.TEXTO_LIBRE
}

sealed class JuegoEstado {
    /** Sin ronda activa: pantalla de inicio de ronda. */
    data object Inactivo : JuegoEstado()

    /** Hay una ronda en curso y una pregunta por (re)mostrar. */
    data class PreguntaActual(
        val rondaId: String,
        val pregunta: Pregunta,
        val indice: Int,
        val total: Int,
        val valorActual: String,
        val respondida: Boolean,
        /** Modo de la ronda: LOCAL va solo, PAREJA cruza respuestas por P2P. */
        val modo: ModoRonda = ModoRonda.LOCAL,
        /** La pareja ya respondió ESTA pregunta (su valor no se revela). */
        val respondioCompañero: Boolean = false,
        val nombreCompañero: String = "",
        /** En modo PAREJA: enlace vivo con el otro teléfono. */
        val conectado: Boolean = true,
    ) : JuegoEstado()

    /** Ronda terminada: pregunta + las dos respuestas, en vivo. */
    data class Resumen(
        val rondaId: String,
        val items: List<ResumenItem>,
        val modo: ModoRonda = ModoRonda.LOCAL,
        val nombreCompañero: String = "",
        val compañeroTerminó: Boolean = false,
    ) : JuegoEstado()

    /** No hay preguntas activas para jugar. */
    data object SinPreguntas : JuegoEstado()
}

/**
 * Motor de la ronda. La fuente única de verdad es la ronda en Room, así que
 * todo sobrevive a rotaciones, a la muerte del proceso y (en modo PAREJA)
 * a que la pareja siga respondiendo desde el otro teléfono.
 *
 * En modo PAREJA este ViewModel solo ENVÍA: al responder, envía
 * [MensajePareja.RespuestaRecibida] por el canal; al terminar, anuncia
 * [MensajePareja.FinRonda]. La RECEPCIÓN vive en ReceptorP2P (alcance de
 * aplicación), y lo que llega se refleja aquí vía Room (Room es quien
 * notifica, no el canal): el "ya respondió" por pregunta y el resumen
 * compartido se actualizan solos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JuegoViewModel(
    private val rondaRepository: RondaRepository,
    private val preguntaRepository: PreguntaRepository,
    perfilRepository: PerfilRepository,
    private val canalPareja: CanalPareja,
) : ViewModel() {

    private val _estado = MutableStateFlow<JuegoEstado>(JuegoEstado.Inactivo)
    val estado: StateFlow<JuegoEstado> = _estado.asStateFlow()

    private var perfil: Perfil = Perfil()
    private var rondaActual: Ronda? = null
    private var preguntasDeRonda: List<Pregunta> = emptyList()

    /** preguntaId -> respuestas de ambos, si las hay. */
    private var respuestasDeRonda: Map<String, List<Respuesta>> = emptyMap()

    /** Borrador de la pregunta actual (texto libre). */
    private var borrador: String = ""

    /** Evita que al finalizar la ronda el flujo nulo machaque el resumen. */
    private var mostrarResumen = false
    private var rondaResumenId: String? = null
    private var modoResumen: ModoRonda = ModoRonda.LOCAL
    private var compañeroTerminó = false
    private var jobResumen: Job? = null

    init {
        // Cachear el perfil para saber quién responde (autor) y el nombre
        // con que se le llama a la pareja en pantalla.
        viewModelScope.launch {
            perfilRepository.perfil.collect { perfil = it }
        }

        // Fuente única de verdad: la ronda activa en Room. Sobrevive a
        // rotaciones y a la muerte del proceso (offline-first).
        viewModelScope.launch {
            rondaRepository.observeRondaActiva().collectLatest { ronda ->
                rondaActual = ronda
                if (ronda == null) {
                    if (!mostrarResumen) _estado.value = JuegoEstado.Inactivo
                    return@collectLatest
                }
                refrescar()
            }
        }

        // Respuestas de la ronda activa (mías y de mi pareja, vía Room).
        // También actualiza el resumen en vivo si ya está visible.
        viewModelScope.launch {
            rondaRepository.observeRondaActiva()
                .flatMapLatest { ronda ->
                    if (ronda == null) flowOf(emptyList<Respuesta>())
                    else rondaRepository.observeRespuestasDeRonda(ronda.id)
                }
                .collect { respuestas ->
                    respuestasDeRonda = respuestas.groupBy { it.preguntaId }
                    if (mostrarResumen) {
                        refrescarResumenVivo(respuestas)
                    } else if (rondaActual != null) {
                        refrescar()
                    }
                }
        }

        // En modo PAREJA, el estado del enlace se refleja en la pregunta
        // activa (indicador de conexión sin perder nada).
        viewModelScope.launch {
            canalPareja.estado.collect {
                if (rondaActual?.modo == ModoRonda.PAREJA && !mostrarResumen) refrescar()
            }
        }

        // Aviso de que la pareja terminó (para mostrarlo sobre el resumen).
        viewModelScope.launch {
            canalPareja.mensajes.collect { mensaje ->
                if (mensaje is MensajePareja.FinRonda &&
                    mensaje.rondaId == (rondaActual?.id ?: rondaResumenId)
                ) {
                    compañeroTerminó = true
                    if (mostrarResumen) refrescarResumenVivo(respuestasActuales())
                }
            }
        }
    }

    fun iniciarRonda(cantidad: Int = Ronda.PREGUNTAS_POR_RONDA) {
        viewModelScope.launch {
            mostrarResumen = false
            borrador = ""
            val preguntas = preguntaRepository.getRandomPreguntas(cantidad)
            if (preguntas.isEmpty()) {
                _estado.value = JuegoEstado.SinPreguntas
                return@launch
            }
            rondaRepository.crearRonda(preguntas.map { it.id })
        }
    }

    fun responder(valor: String) {
        val ronda = rondaActual ?: return
        val estadoActual = _estado.value as? JuegoEstado.PreguntaActual ?: return
        if (estadoActual.respondida) return
        val limpio = valor.trim()
        viewModelScope.launch {
            rondaRepository.guardarRespuesta(
                Respuesta(
                    preguntaId = estadoActual.pregunta.id,
                    sesionId = ronda.id,
                    autor = perfil.animal,
                    valor = limpio,
                ),
            )
            borrador = limpio
            // Modo pareja: cruzar la respuesta al otro teléfono.
            if (ronda.modo == ModoRonda.PAREJA) {
                canalPareja.enviar(
                    MensajePareja.RespuestaRecibida(
                        rondaId = ronda.id,
                        preguntaId = estadoActual.pregunta.id,
                        autor = perfil.animal,
                        valor = limpio,
                    ),
                )
            }
        }
    }

    fun actualizarBorrador(texto: String) {
        borrador = texto
        val actual = _estado.value as? JuegoEstado.PreguntaActual ?: return
        _estado.value = actual.copy(valorActual = texto)
    }

    /** Guarda la respuesta de la pregunta (atajo para texto libre). */
    fun responderBorrador() {
        val actual = _estado.value as? JuegoEstado.PreguntaActual ?: return
        if (borrador.isBlank()) return
        responder(borrador)
    }

    /** Avanza a la siguiente pregunta o finaliza la ronda. */
    fun avanzar() {
        val ronda = rondaActual ?: return
        val estadoActual = _estado.value as? JuegoEstado.PreguntaActual ?: return
        viewModelScope.launch {
            val nuevoIndice = estadoActual.indice + 1
            if (nuevoIndice >= estadoActual.total) {
                finalizar(ronda.id)
            } else {
                borrador = ""
                rondaRepository.avanzarIndice(ronda.id, nuevoIndice)
            }
        }
    }

    fun volverInicio() {
        jobResumen?.cancel()
        jobResumen = null
        if (_estado.value is JuegoEstado.Resumen) {
            mostrarResumen = false
            rondaResumenId = null
            compañeroTerminó = false
        }
        _estado.value = JuegoEstado.Inactivo
    }

    // ------------------------------------------------------------------
    // Privados
    // ------------------------------------------------------------------

    private suspend fun finalizar(rondaId: String) {
        mostrarResumen = true
        rondaResumenId = rondaId
        modoResumen = rondaActual?.modo ?: ModoRonda.LOCAL
        rondaRepository.finalizarRonda(rondaId)

        // Avisar al otro teléfono (si la ronda era de pareja).
        if (modoResumen == ModoRonda.PAREJA) {
            canalPareja.enviar(MensajePareja.FinRonda(rondaId))
        }

        // Resumen en vivo: mientras esté visible, las respuestas que sigan
        // llegando del otro teléfono aparecen solas (Room notifica).
        publicarResumen(respuestasActuales())
        jobResumen?.cancel()
        jobResumen = viewModelScope.launch {
            rondaRepository.observeRespuestasDeRonda(rondaId).collect { lista ->
                respuestasDeRonda = lista.groupBy { it.preguntaId }
                publicarResumen(lista)
            }
        }
    }

    private suspend fun refrescar() {
        val ronda = rondaActual ?: return
        if (mostrarResumen) return

        // Mantener el orden original de la ronda
        val preguntas = preguntaRepository.getPreguntasPorIds(ronda.preguntaIds)
        preguntasDeRonda = ronda.preguntaIds.mapNotNull { id -> preguntas.firstOrNull { it.id == id } }

        val indice = ronda.indiceActual
        if (indice >= preguntasDeRonda.size) {
            finalizar(ronda.id)
            return
        }
        val pregunta = preguntasDeRonda[indice]
        val dePregunta = respuestasDeRonda[pregunta.id].orEmpty()
        val mia = dePregunta.firstOrNull { it.autor == perfil.animal }
        val suya = dePregunta.firstOrNull { it.autor != perfil.animal }
        val enPareja = ronda.modo == ModoRonda.PAREJA
        _estado.value = JuegoEstado.PreguntaActual(
            rondaId = ronda.id,
            pregunta = pregunta,
            indice = indice,
            total = preguntasDeRonda.size,
            valorActual = mia?.valor ?: borrador,
            respondida = mia != null,
            modo = ronda.modo,
            respondioCompañero = suya != null,
            nombreCompañero = perfil.nombrePareja.ifBlank { "tu pareja" },
            conectado = !enPareja || canalPareja.estado.value == EstadoP2P.CONECTADO,
        )
    }

    private fun refrescarResumenVivo(respuestas: List<Respuesta>) {
        publicarResumen(respuestas)
    }

    private fun publicarResumen(respuestas: List<Respuesta>) {
        val rondaId = rondaResumenId ?: return
        val items = preguntasDeRonda.map { pregunta ->
            val dePregunta = respuestas.filter { it.preguntaId == pregunta.id }
            ResumenItem(
                pregunta = pregunta,
                mia = dePregunta.firstOrNull { it.autor == perfil.animal },
                suya = dePregunta.firstOrNull { it.autor != perfil.animal },
            )
        }
        _estado.value = JuegoEstado.Resumen(
            rondaId = rondaId,
            items = items,
            modo = modoResumen,
            nombreCompañero = perfil.nombrePareja.ifBlank { "tu pareja" },
            compañeroTerminó = compañeroTerminó,
        )
    }

    private fun respuestasActuales(): List<Respuesta> =
        respuestasDeRonda.values.flatten()
}
