package com.cerlita.app.ui.screens.juego

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerlita.domain.model.Perfil
import com.cerlita.domain.model.Pregunta
import com.cerlita.domain.model.Respuesta
import com.cerlita.domain.model.Ronda
import com.cerlita.domain.repository.PerfilRepository
import com.cerlita.domain.repository.PreguntaRepository
import com.cerlita.domain.repository.RondaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Una entrada del resumen: la pregunta y mi respuesta (si la hubo). */
data class ResumenItem(
    val pregunta: Pregunta,
    val respuesta: Respuesta?,
)

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
    ) : JuegoEstado()

    /** Ronda terminada: listado de pregunta + respuesta. */
    data class Resumen(
        val rondaId: String,
        val items: List<ResumenItem>,
    ) : JuegoEstado()

    /** No hay preguntas activas para jugar. */
    data object SinPreguntas : JuegoEstado()
}

@OptIn(ExperimentalCoroutinesApi::class)
class JuegoViewModel(
    private val rondaRepository: RondaRepository,
    private val preguntaRepository: PreguntaRepository,
    perfilRepository: PerfilRepository,
) : ViewModel() {

    private val _estado = MutableStateFlow<JuegoEstado>(JuegoEstado.Inactivo)
    val estado: StateFlow<JuegoEstado> = _estado.asStateFlow()

    private var perfil: Perfil = Perfil()
    private var rondaActual: Ronda? = null
    private var preguntasDeRonda: List<Pregunta> = emptyList()
    private var respuestasDeRonda: Map<String, Respuesta> = emptyMap()

    /** Borrador de la pregunta actual (texto libre). */
    private var borrador: String = ""

    /** Evita que al finalizar la ronda el flujo nulo machaque el resumen. */
    private var mostrarResumen = false

    init {
        // Cachear el perfil para saber quién responde (autor).
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

        // Respuestas de la ronda activa (para retomar y para el resumen).
        viewModelScope.launch {
            rondaRepository.observeRondaActiva()
                .flatMapLatest { ronda ->
                    if (ronda == null) flowOf(emptyList())
                    else rondaRepository.observeRespuestasDeRonda(ronda.id)
                }
                .collect { respuestas ->
                    respuestasDeRonda = respuestas.associateBy { it.preguntaId }
                    if (rondaActual != null) refrescar()
                }
        }
    }

    fun iniciarRonda(cantidad: Int = CANTIDAD_POR_RONDA) {
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
        viewModelScope.launch {
            rondaRepository.guardarRespuesta(
                Respuesta(
                    preguntaId = estadoActual.pregunta.id,
                    sesionId = ronda.id,
                    autor = perfil.animal,
                    valor = valor.trim(),
                ),
            )
            borrador = valor
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
        if (_estado.value is JuegoEstado.Resumen) mostrarResumen = false
        _estado.value = JuegoEstado.Inactivo
    }

    private suspend fun finalizar(rondaId: String) {
        mostrarResumen = true
        rondaRepository.finalizarRonda(rondaId)
        val items = preguntasDeRonda.map { ResumenItem(it, respuestasDeRonda[it.id]) }
        _estado.value = JuegoEstado.Resumen(rondaId, items)
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
        val respondida = respuestasDeRonda.containsKey(pregunta.id)
        _estado.value = JuegoEstado.PreguntaActual(
            rondaId = ronda.id,
            pregunta = pregunta,
            indice = indice,
            total = preguntasDeRonda.size,
            valorActual = if (respondida) {
                respuestasDeRonda[pregunta.id]?.valor.orEmpty()
            } else {
                borrador
            },
            respondida = respondida,
        )
    }

    companion object {
        const val CANTIDAD_POR_RONDA = 10
    }
}
