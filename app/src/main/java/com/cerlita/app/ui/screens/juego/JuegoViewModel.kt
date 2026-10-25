package com.cerlita.app.ui.screens.juego

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerlita.domain.juego.FasePregunta
import com.cerlita.domain.juego.ReglasJuego
import com.cerlita.domain.model.Animal
import com.cerlita.domain.model.EstadoP2P
import com.cerlita.domain.model.Evaluacion
import com.cerlita.domain.model.MensajePareja
import com.cerlita.domain.model.ModoJuego
import com.cerlita.domain.model.ModoRonda
import com.cerlita.domain.model.Perfil
import com.cerlita.domain.model.Pregunta
import com.cerlita.domain.model.Respuesta
import com.cerlita.domain.model.Ronda
import com.cerlita.domain.model.TipoPregunta
import com.cerlita.domain.model.Veredicto
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

/** Una entrada del resumen: la pregunta, las respuestas de ambos y el veredicto. */
data class ResumenItem(
    val pregunta: Pregunta,
    val modoPregunta: ModoJuego,
    val respuestas: Map<Animal, Respuesta>,
    val evaluacion: Evaluacion?,
) {
    val ambasRespuestas: Boolean get() = respuestas.size >= 2

    /** Texto libre con las dos respuestas y sin veredicto aún: lo juzga la pareja. */
    val juicioPendiente: Boolean
        get() = ambasRespuestas && evaluacion == null &&
            pregunta.tipo == TipoPregunta.TEXTO_LIBRE
}

sealed class JuegoEstado {
    /** Sin ronda activa: selector de modo de juego. */
    data object Inactivo : JuegoEstado()

    /**
     * Compuerta de "pasa el móvil" (modo LOCAL): evita que el siguiente
     * jugador vea la respuesta del anterior antes de tiempo.
     */
    data class Compuerta(
        val rondaId: String,
        val pregunta: Pregunta,
        val indice: Int,
        val total: Int,
        val modoPregunta: ModoJuego,
        val fase: FasePregunta,
        val destinatario: Animal,
        val nombres: Map<Animal, String>,
        val modo: ModoRonda,
    ) : JuegoEstado()

    /** Pregunta en juego: un turno de escritura o la revelación (LOCAL). */
    data class PreguntaActual(
        val rondaId: String,
        val pregunta: Pregunta,
        val indice: Int,
        val total: Int,
        val modoPregunta: ModoJuego,
        val fase: FasePregunta,
        /** Quién escribe en este turno (en PAREJA, el dueño del teléfono). */
        val autorTurno: Animal,
        val valorActual: String,
        val respondida: Boolean,
        val modo: ModoRonda = ModoRonda.LOCAL,
        val respondioCompañero: Boolean = false,
        val nombreCompañero: String = "",
        val nombres: Map<Animal, String> = emptyMap(),
        val conectado: Boolean = true,
        /** En PAREJA+ADIVINA: este teléfono es quien adivina la pregunta actual. */
        val soyAdivino: Boolean = false,
        /** Solo fase REVELACION (LOCAL): las dos respuestas sobre la mesa. */
        val respuestasReveladas: Map<Animal, Respuesta> = emptyMap(),
        val evaluacion: Evaluacion? = null,
        val juicioPendiente: Boolean = false,
    ) : JuegoEstado()

    /** Ronda terminada: pregunta + las dos respuestas + veredictos, en vivo. */
    data class Resumen(
        val rondaId: String,
        val items: List<ResumenItem>,
        val modo: ModoRonda = ModoRonda.LOCAL,
        val modoJuego: ModoJuego = ModoJuego.AMBOS_RESPONDEN,
        val nombreCompañero: String = "",
        val nombres: Map<Animal, String> = emptyMap(),
        val compañeroTerminó: Boolean = false,
        val aciertos: Int = 0,
        val evaluadas: Int = 0,
    ) : JuegoEstado()

    /** No hay preguntas activas para jugar. */
    data object SinPreguntas : JuegoEstado()
}

/**
 * Motor de la ronda en sus tres modos (AMBOS_RESPONDEN / ADIVINA / MIXTO).
 * La fuente única de verdad sigue siendo Room: ronda, respuestas y
 * evaluaciones sobreviven a rotaciones, a la muerte del proceso y (en modo
 * PAREJA) a que el otro teléfono siga respondiendo.
 *
 * El "motor de comparación" es humano: los tipos cerrados se comparan solos
 * (ReglasJuego.veredictoAutomatico) y el texto libre lo juzga la pareja con
 * tres botones. Sin IA en el teléfono.
 *
 * En modo PAREJA este ViewModel solo ENVÍA: al responder, envía
 * [MensajePareja.RespuestaRecibida]; al terminar, [MensajePareja.FinRonda].
 * La RECEPCIÓN vive en ReceptorP2P (alcance de aplicación) y lo que llega
 * se refleja aquí vía Room.
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

    /** preguntaId -> autor -> respuesta. */
    private var respuestasDeRonda: Map<String, Map<Animal, Respuesta>> = emptyMap()

    /** preguntaId -> veredicto emitido (auto o humano). */
    private var evaluacionesDeRonda: Map<String, Evaluacion> = emptyMap()

    /** Borrador de texto libre, ligado a la pregunta y a quién escribe. */
    private var borrador: String = ""
    private var borradorKey: String = ""

    /** Llaves "rondaId:indice:fase" de compuertas ya abiertas. */
    private val compuertasAbiertas = mutableSetOf<String>()

    private var mostrarResumen = false
    private var rondaResumenId: String? = null
    private var rondaDeResumen: Ronda? = null
    private var modoResumen: ModoRonda = ModoRonda.LOCAL
    private var compañeroTerminó = false
    private var jobResumen: Job? = null
    private var jobResumenEval: Job? = null

    init {
        // Cachear el perfil: quién soy (autor de mis respuestas) y cómo se
        // llama el otro en pantalla.
        viewModelScope.launch {
            perfilRepository.perfil.collect { perfil = it }
        }

        // Fuente única de verdad: la ronda activa en Room (offline-first).
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

        // Respuestas de la ronda activa (mías y las del otro, vía Room).
        viewModelScope.launch {
            rondaRepository.observeRondaActiva()
                .flatMapLatest { ronda ->
                    if (ronda == null) flowOf(emptyList<Respuesta>())
                    else rondaRepository.observeRespuestasDeRonda(ronda.id)
                }
                .collect { respuestas ->
                    // Con el resumen en pantalla y la ronda ya finalizada,
                    // el vivo lo mantienen jobResumen/jobResumenEval.
                    if (mostrarResumen && rondaActual == null) return@collect
                    respuestasDeRonda = respuestas.groupBy { it.preguntaId }
                        .mapValues { (_, lista) -> lista.associateBy { it.autor } }
                    if (mostrarResumen) {
                        publicarResumen(respuestas)
                    } else if (rondaActual != null) {
                        refrescar()
                    }
                    emitirVeredictosAutomaticos()
                }
        }

        // Evaluaciones de la ronda activa (veredictos auto y humanos).
        viewModelScope.launch {
            rondaRepository.observeRondaActiva()
                .flatMapLatest { ronda ->
                    if (ronda == null) flowOf(emptyList<Evaluacion>())
                    else rondaRepository.observeEvaluacionesDeRonda(ronda.id)
                }
                .collect { evaluaciones ->
                    if (mostrarResumen && rondaActual == null) return@collect
                    evaluacionesDeRonda = evaluaciones.associateBy { it.preguntaId }
                    if (mostrarResumen) {
                        publicarResumen(respuestasActuales())
                    } else if (rondaActual != null) {
                        refrescar()
                    }
                }
        }

        // En modo PAREJA, el estado del enlace se refleja en la pregunta.
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
                    if (mostrarResumen) publicarResumen(respuestasActuales())
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Acciones públicas
    // ------------------------------------------------------------------

    /** Arranca una ronda local con el modo de juego elegido. */
    fun iniciarRonda(modoJuego: ModoJuego, cantidad: Int = Ronda.PREGUNTAS_POR_RONDA) {
        viewModelScope.launch {
            mostrarResumen = false
            rondaResumenId = null
            rondaDeResumen = null
            compañeroTerminó = false
            compuertasAbiertas.clear()
            borrador = ""
            borradorKey = ""
            val preguntas = preguntaRepository.getRandomPreguntas(cantidad)
            if (preguntas.isEmpty()) {
                _estado.value = JuegoEstado.SinPreguntas
                return@launch
            }
            rondaRepository.crearRonda(preguntas.map { it.id }, modoJuego)
        }
    }

    /** El destinatario de la compuerta tomó el móvil: a jugar su turno. */
    fun abrirCompuerta() {
        val actual = _estado.value as? JuegoEstado.Compuerta ?: return
        compuertasAbiertas += "${actual.rondaId}:${actual.indice}:${actual.fase.name}"
        viewModelScope.launch { refrescar() }
    }

    fun responder(valor: String) {
        val ronda = rondaActual ?: return
        val estadoActual = _estado.value as? JuegoEstado.PreguntaActual ?: return
        if (estadoActual.fase == FasePregunta.REVELACION || estadoActual.respondida) return
        val limpio = valor.trim()
        if (limpio.isEmpty()) return
        // En LOCAL escribe quien tiene el móvil según el turno; en PAREJA,
        // el dueño de este teléfono.
        val autor = if (ronda.modo == ModoRonda.PAREJA) perfil.animal else estadoActual.autorTurno
        viewModelScope.launch {
            rondaRepository.guardarRespuesta(
                Respuesta(
                    preguntaId = estadoActual.pregunta.id,
                    sesionId = ronda.id,
                    autor = autor,
                    valor = limpio,
                ),
            )
            borrador = limpio
            borradorKey = "${estadoActual.pregunta.id}:$autor"
            // Modo pareja: cruzar la respuesta al otro teléfono.
            if (ronda.modo == ModoRonda.PAREJA) {
                canalPareja.enviar(
                    MensajePareja.RespuestaRecibida(
                        rondaId = ronda.id,
                        preguntaId = estadoActual.pregunta.id,
                        autor = autor,
                        valor = limpio,
                    ),
                )
            }
        }
    }

    fun actualizarBorrador(texto: String) {
        val actual = _estado.value as? JuegoEstado.PreguntaActual ?: return
        borrador = texto
        borradorKey = "${actual.pregunta.id}:${actual.autorTurno}"
        _estado.value = actual.copy(valorActual = texto)
    }

    /** Guarda la respuesta de texto libre (atajo del borrador). */
    fun responderBorrador() {
        val actual = _estado.value as? JuegoEstado.PreguntaActual ?: return
        if (borrador.isBlank()) return
        responder(borrador)
    }

    /**
     * Juicio humano sobre una pregunta (texto libre): los botones
     * Sí/Parecido/No (AMBOS_RESPONDEN) o Acertó/Más o menos/Falló (ADIVINA).
     */
    fun juzgar(preguntaId: String, veredicto: Veredicto) {
        val ronda = rondaActual ?: rondaDeResumen ?: return
        val rondaId = rondaActual?.id ?: rondaResumenId ?: return
        val respuestas = respuestasDeRonda[preguntaId].orEmpty()
        if (respuestas.size < 2) return // se juzga con las dos respuestas
        val indice = preguntasDeRonda.indexOfFirst { it.id == preguntaId }
        if (indice < 0) return
        viewModelScope.launch {
            rondaRepository.guardarEvaluacion(
                Evaluacion(
                    sesionId = rondaId,
                    preguntaId = preguntaId,
                    modoPregunta = ronda.modoDe(indice),
                    veredicto = veredicto,
                    automatico = false,
                ),
            )
        }
    }

    /** Avanza a la siguiente pregunta o finaliza la ronda. */
    fun avanzar() {
        val ronda = rondaActual ?: return
        val estadoActual = _estado.value as? JuegoEstado.PreguntaActual ?: return
        // Revelación LOCAL: primero el veredicto (auto o humano), luego avanzar.
        if (estadoActual.fase == FasePregunta.REVELACION && estadoActual.juicioPendiente) return
        viewModelScope.launch {
            val nuevoIndice = estadoActual.indice + 1
            if (nuevoIndice >= estadoActual.total) {
                finalizar(ronda.id)
            } else {
                borrador = ""
                borradorKey = ""
                rondaRepository.avanzarIndice(ronda.id, nuevoIndice)
            }
        }
    }

    fun volverInicio() {
        jobResumen?.cancel()
        jobResumen = null
        jobResumenEval?.cancel()
        jobResumenEval = null
        if (_estado.value is JuegoEstado.Resumen) {
            mostrarResumen = false
            rondaResumenId = null
            rondaDeResumen = null
            compañeroTerminó = false
        }
        _estado.value = JuegoEstado.Inactivo
    }

    // ------------------------------------------------------------------
    // Privados
    // ------------------------------------------------------------------

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
        val modoPregunta = ronda.modoDe(indice)
        val dePregunta = respuestasDeRonda[pregunta.id].orEmpty()
        val nombres = nombresParaUi()
        val enPareja = ronda.modo == ModoRonda.PAREJA

        if (enPareja) {
            val mia = dePregunta[perfil.animal]
            val suya = dePregunta.entries.firstOrNull { it.key != perfil.animal }?.value
            val soyAdivino =
                modoPregunta == ModoJuego.ADIVINA &&
                    ReglasJuego.autorDeTurno(modoPregunta, indice, 0) == perfil.animal
            _estado.value = JuegoEstado.PreguntaActual(
                rondaId = ronda.id,
                pregunta = pregunta,
                indice = indice,
                total = preguntasDeRonda.size,
                modoPregunta = modoPregunta,
                fase = FasePregunta.TURNO_1, // en PAREJA cada teléfono tiene un solo turno
                autorTurno = perfil.animal,
                valorActual = mia?.valor ?: borradorSiCorresponde(pregunta.id, perfil.animal),
                respondida = mia != null,
                modo = ronda.modo,
                respondioCompañero = suya != null,
                nombreCompañero = perfil.nombrePareja.ifBlank { "tu pareja" },
                nombres = nombres,
                conectado = canalPareja.estado.value == EstadoP2P.CONECTADO,
                soyAdivino = soyAdivino,
            )
            return
        }

        // LOCAL: máquina de fases pasando el móvil entre los dos.
        val fase = ReglasJuego.inferirFase(dePregunta)
        when (fase) {
            FasePregunta.TURNO_1, FasePregunta.TURNO_2 -> {
                val turno = if (fase == FasePregunta.TURNO_1) 0 else 1
                val autor = ReglasJuego.autorDeTurno(modoPregunta, indice, turno)
                val llave = "${ronda.id}:$indice:${fase.name}"
                if (llave !in compuertasAbiertas) {
                    _estado.value = JuegoEstado.Compuerta(
                        rondaId = ronda.id,
                        pregunta = pregunta,
                        indice = indice,
                        total = preguntasDeRonda.size,
                        modoPregunta = modoPregunta,
                        fase = fase,
                        destinatario = autor,
                        nombres = nombres,
                        modo = ronda.modo,
                    )
                } else {
                    val guardada = dePregunta[autor]
                    _estado.value = JuegoEstado.PreguntaActual(
                        rondaId = ronda.id,
                        pregunta = pregunta,
                        indice = indice,
                        total = preguntasDeRonda.size,
                        modoPregunta = modoPregunta,
                        fase = fase,
                        autorTurno = autor,
                        valorActual = guardada?.valor ?: borradorSiCorresponde(pregunta.id, autor),
                        respondida = guardada != null,
                        modo = ronda.modo,
                        nombres = nombres,
                    )
                }
            }

            FasePregunta.REVELACION -> {
                val evaluacion = evaluacionesDeRonda[pregunta.id]
                _estado.value = JuegoEstado.PreguntaActual(
                    rondaId = ronda.id,
                    pregunta = pregunta,
                    indice = indice,
                    total = preguntasDeRonda.size,
                    modoPregunta = modoPregunta,
                    fase = fase,
                    autorTurno = ReglasJuego.autorDeTurno(modoPregunta, indice, 0),
                    valorActual = "",
                    respondida = true,
                    modo = ronda.modo,
                    nombres = nombres,
                    respuestasReveladas = dePregunta,
                    evaluacion = evaluacion,
                    // El veredicto automático de los tipos cerrados ya lo
                    // emite el colector; queda pendiente solo el texto libre.
                    juicioPendiente = evaluacion == null &&
                        pregunta.tipo == TipoPregunta.TEXTO_LIBRE,
                )
            }
        }
    }

    /** Veredictos automáticos de toda la ronda con dos respuestas y sin juicio. */
    private suspend fun emitirVeredictosAutomaticos() {
        val ronda = rondaActual ?: rondaDeResumen ?: return
        val rondaId = rondaActual?.id ?: rondaResumenId ?: return
        preguntasDeRonda.forEachIndexed { indice, pregunta ->
            if (evaluacionesDeRonda.containsKey(pregunta.id)) return@forEachIndexed
            val dePregunta = respuestasDeRonda[pregunta.id].orEmpty()
            if (dePregunta.size < 2) return@forEachIndexed
            val valores = dePregunta.values.map { it.valor }
            val auto = ReglasJuego.veredictoAutomatico(
                pregunta.tipo,
                valores[0],
                valores[1],
            ) ?: return@forEachIndexed
            rondaRepository.guardarEvaluacion(
                Evaluacion(
                    sesionId = rondaId,
                    preguntaId = pregunta.id,
                    modoPregunta = ronda.modoDe(indice),
                    veredicto = auto,
                    automatico = true,
                ),
            )
        }
    }

    private suspend fun finalizar(rondaId: String) {
        val ronda = rondaActual ?: return
        mostrarResumen = true
        rondaResumenId = rondaId
        rondaDeResumen = ronda
        modoResumen = ronda.modo
        rondaRepository.finalizarRonda(rondaId)

        // Avisar al otro teléfono (si la ronda era de pareja).
        if (modoResumen == ModoRonda.PAREJA) {
            canalPareja.enviar(MensajePareja.FinRonda(rondaId))
        }

        // Resumen en vivo: lo que siga llegando (respuestas del otro
        // teléfono, veredictos) aparece solo (Room notifica).
        publicarResumen(respuestasActuales())
        jobResumen?.cancel()
        jobResumen = viewModelScope.launch {
            rondaRepository.observeRespuestasDeRonda(rondaId).collect { lista ->
                respuestasDeRonda = lista.groupBy { it.preguntaId }
                    .mapValues { (_, l) -> l.associateBy { it.autor } }
                emitirVeredictosAutomaticos()
                publicarResumen(lista)
            }
        }
        jobResumenEval?.cancel()
        jobResumenEval = viewModelScope.launch {
            rondaRepository.observeEvaluacionesDeRonda(rondaId).collect { lista ->
                evaluacionesDeRonda = lista.associateBy { it.preguntaId }
                publicarResumen(respuestasActuales())
            }
        }
    }

    private fun publicarResumen(respuestas: List<Respuesta>) {
        val rondaId = rondaResumenId ?: return
        val ronda = rondaDeResumen
        val porPregunta = respuestas.groupBy { it.preguntaId }
            .mapValues { (_, lista) -> lista.associateBy { it.autor } }
        val items = preguntasDeRonda.mapIndexed { indice, pregunta ->
            ResumenItem(
                pregunta = pregunta,
                modoPregunta = ronda?.modoDe(indice) ?: ModoJuego.AMBOS_RESPONDEN,
                respuestas = porPregunta[pregunta.id].orEmpty(),
                evaluacion = evaluacionesDeRonda[pregunta.id],
            )
        }
        _estado.value = JuegoEstado.Resumen(
            rondaId = rondaId,
            items = items,
            modo = modoResumen,
            modoJuego = ronda?.modoJuego ?: ModoJuego.AMBOS_RESPONDEN,
            nombreCompañero = perfil.nombrePareja.ifBlank { "tu pareja" },
            nombres = nombresParaUi(),
            compañeroTerminó = compañeroTerminó,
            aciertos = evaluacionesDeRonda.values.count { it.veredicto == Veredicto.AFIRMATIVO },
            evaluadas = evaluacionesDeRonda.size,
        )
    }

    private fun respuestasActuales(): List<Respuesta> =
        respuestasDeRonda.values.flatMap { it.values }

    private fun borradorSiCorresponde(preguntaId: String, autor: Animal): String =
        if (borradorKey == "$preguntaId:$autor") borrador else ""

    private fun nombresParaUi(): Map<Animal, String> {
        val otro = if (perfil.animal == Animal.CERDITA) Animal.KOALA else Animal.CERDITA
        val nombrePropio = perfil.nombrePropio.ifBlank {
            if (perfil.animal == Animal.CERDITA) "Cerdita" else "Koala"
        }
        val nombreOtro = perfil.nombrePareja.ifBlank {
            if (otro == Animal.CERDITA) "Cerdita" else "Koala"
        }
        return mapOf(perfil.animal to nombrePropio, otro to nombreOtro)
    }
}
