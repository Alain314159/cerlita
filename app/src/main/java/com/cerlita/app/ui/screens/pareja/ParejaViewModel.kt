package com.cerlita.app.ui.screens.pareja

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerlita.domain.model.EndpointPareja
import com.cerlita.domain.model.EstadoP2P
import com.cerlita.domain.model.InfoJugador
import com.cerlita.domain.model.MensajePareja
import com.cerlita.domain.model.Perfil
import com.cerlita.domain.model.Ronda
import com.cerlita.domain.repository.CanalPareja
import com.cerlita.domain.repository.PerfilRepository
import com.cerlita.domain.repository.PreguntaRepository
import com.cerlita.domain.repository.RondaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Estado de los permisos de Nearby (Bluetooth en 31+, ubicación antes). */
enum class PermisosPareja { PENDIENTE, CONCEDIDO, DENEGADO }

data class ParejaUiState(
    val perfil: Perfil = Perfil(),
    val estadoP2P: EstadoP2P = EstadoP2P.INACTIVO,
    val encontrados: List<EndpointPareja> = emptyList(),
    val otro: InfoJugador? = null,
    val soyHost: Boolean = false,
    val permisos: PermisosPareja = PermisosPareja.PENDIENTE,
    val error: String? = null,
    /** One-shot: navegar a Juego porque la ronda pareja ya está en Room. */
    val irAJuego: Boolean = false,
)

/**
 * Lobby del modo pareja: busca al otro teléfono por Nearby Connections,
 * conecta y (como host) propone la ronda compartida.
 *
 * El invitado solo observa: cuando llega [MensajePareja.InicioRonda] sincroniza
 * las preguntas, persiste la ronda con el id del host y navega también.
 * Las respuestas cruzadas NO pasan por aquí: las maneja JuegoViewModel,
 * que sigue vivo mientras la ronda esté en pantalla.
 */
class ParejaViewModel(
    perfilRepository: PerfilRepository,
    private val preguntaRepository: PreguntaRepository,
    private val rondaRepository: RondaRepository,
    private val canalPareja: CanalPareja,
) : ViewModel() {

    private val _estado = MutableStateFlow(ParejaUiState())
    val estado: StateFlow<ParejaUiState> = _estado.asStateFlow()

    /** Guarda que ya iniciamos ronda (evita duplicar InicioRonda del host). */
    private var rondaYaIniciada = false

    init {
        // Espejo de todo el canal P2P hacia el estado de UI.
        viewModelScope.launch {
            canalPareja.estado.collect { e -> _estado.update { it.copy(estadoP2P = e) } }
        }
        viewModelScope.launch {
            canalPareja.encontrados.collect { e -> _estado.update { it.copy(encontrados = e) } }
        }
        viewModelScope.launch {
            canalPareja.otro.collect { o -> _estado.update { it.copy(otro = o) } }
        }
        viewModelScope.launch {
            canalPareja.soyHost.collect { h -> _estado.update { it.copy(soyHost = h) } }
        }
        viewModelScope.launch {
            canalPareja.errores.collect { err -> _estado.update { it.copy(error = err) } }
        }

        // Perfil -> estado (mi identidad al buscar: nombre + animal).
        viewModelScope.launch {
            perfilRepository.perfil.collect { p -> _estado.update { it.copy(perfil = p) } }
        }

        // Mensajes entrantes: aquí solo me importan como invitado.
        viewModelScope.launch {
            canalPareja.mensajes.collect { mensaje ->
                if (mensaje is MensajePareja.InicioRonda) alRecibirInicio(mensaje)
            }
        }
    }

    // ------------------------------------------------------------------
    // Permisos
    // ------------------------------------------------------------------

    /** Resultado del diálogo de permisos del sistema; concede y busca. */
    fun alConcederPermisos(resultado: Map<String, Boolean>) {
        val concedidos = resultado.values.all { it }
        _estado.update {
            it.copy(
                permisos = if (concedidos) PermisosPareja.CONCEDIDO else PermisosPareja.DENEGADO,
            )
        }
        if (concedidos) buscar()
    }

    // ------------------------------------------------------------------
    // Lobby
    // ------------------------------------------------------------------

    fun buscar() {
        val st = _estado.value
        if (!st.perfil.estaCompleto) {
            _estado.update { it.copy(error = "Completa tu perfil antes de buscar a tu pareja.") }
            return
        }
        canalPareja.buscar(InfoJugador(st.perfil.nombrePropio, st.perfil.animal))
    }

    fun conectarA(endpointId: String) {
        canalPareja.conectarA(endpointId)
    }

    fun desconectar() {
        rondaYaIniciada = false
        canalPareja.detener()
    }

    fun limpiarError() {
        _estado.update { it.copy(error = null) }
    }

    /** El host (quien conectó) arranca la ronda compartida. */
    fun iniciarRonda() {
        val st = _estado.value
        if (st.estadoP2P != EstadoP2P.CONECTADO || !st.soyHost) return
        viewModelScope.launch {
            // No secuestrar una ronda en curso (ni LOCAL ni PAREJA).
            if (rondaRepository.observeRondaActiva().first() != null) {
                _estado.update {
                    it.copy(error = "Ya hay una ronda en curso. Termínala antes de empezar otra.")
                }
                return@launch
            }
            val preguntas = preguntaRepository.getRandomPreguntas(Ronda.PREGUNTAS_POR_RONDA)
            if (preguntas.isEmpty()) {
                _estado.update {
                    it.copy(error = "No hay preguntas activas. Crea algunas en el editor.")
                }
                return@launch
            }
            val rondaId = UUID.randomUUID().toString()
            // Primero enviar y luego persistir: si el envío no llega, no me
            // queda una ronda fantasma esperando respuestas del otro.
            canalPareja.enviar(MensajePareja.InicioRonda(rondaId, preguntas))
            rondaRepository.crearRondaPareja(rondaId, preguntas.map { it.id })
            rondaYaIniciada = true
            _estado.update { it.copy(irAJuego = true) }
        }
    }

    /** Consumido por el NavHost justo después de navegar a Juego. */
    fun consumirNavegacion() {
        _estado.update { it.copy(irAJuego = false) }
    }

    override fun onCleared() {
        // Si salí del lobby sin ronda en marcha, apagar la radio P2P.
        // Con ronda activa, JuegoViewModel se hace cargo del canal.
        if (!rondaYaIniciada) canalPareja.detener()
    }

    // ------------------------------------------------------------------
    // Invitado
    // ------------------------------------------------------------------

    private suspend fun alRecibirInicio(mensaje: MensajePareja.InicioRonda) {
        if (rondaYaIniciada) return // duplicado del host: ignorar
        rondaYaIniciada = true
        // Preguntas del host primero (aunque su catálogo sea distinto),
        // luego la ronda con el id compartido y por último navegar.
        preguntaRepository.sincronizarExternas(mensaje.preguntas)
        rondaRepository.crearRondaPareja(mensaje.rondaId, mensaje.preguntas.map { it.id })
        _estado.update { it.copy(irAJuego = true) }
    }
}
