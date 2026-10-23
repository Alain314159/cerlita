package com.cerlita.app.ui.screens.ajustes

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerlita.core.ai.EstadoModeloIA
import com.cerlita.core.ai.GestorModeloIA
import com.cerlita.data.repo.GestorCatalogo
import com.cerlita.data.repo.ResultadoActualizacion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AjustesUiState(
    val versionCatalogo: String = "",
    val buscando: Boolean = false,
    val resultado: ResultadoActualizacion? = null,
    val estadoModelo: EstadoModeloIA = EstadoModeloIA.NoInstalado,
    val mensajeModelo: String? = null,
) {
    val mensajeResultado: String?
        get() = when (val r = resultado) {
            is ResultadoActualizacion.AlDia -> "Ya tienes la versión más reciente (${r.version})"
            is ResultadoActualizacion.Actualizado -> "Catálogo actualizado: ${r.preguntas} preguntas nuevas (v${r.hacia})"
            is ResultadoActualizacion.Error -> "No se pudo actualizar: ${r.mensaje}"
            null -> null
        }

    /** Descarga/importación/instalación en curso: botones del modelo deshabilitados. */
    val operandoModelo: Boolean
        get() = estadoModelo is EstadoModeloIA.Descargando ||
            estadoModelo is EstadoModeloIA.Importando ||
            estadoModelo is EstadoModeloIA.Instalando
}

class AjustesViewModel(
    private val gestorCatalogo: GestorCatalogo,
    private val gestorModelo: GestorModeloIA,
) : ViewModel() {

    private val _estado = MutableStateFlow(AjustesUiState())
    val estado: StateFlow<AjustesUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            gestorCatalogo.versionInstalada.collect { version ->
                _estado.update { it.copy(versionCatalogo = version) }
            }
        }
        viewModelScope.launch {
            gestorModelo.estado.collect { modelo ->
                _estado.update { it.copy(estadoModelo = modelo) }
            }
        }
    }

    fun buscarActualizaciones() {
        if (_estado.value.buscando) return
        _estado.value = _estado.value.copy(buscando = true, resultado = null)
        viewModelScope.launch {
            val resultado = gestorCatalogo.buscarEInstalar()
            _estado.value = _estado.value.copy(buscando = false, resultado = resultado)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Modelo de IA local
    // ─────────────────────────────────────────────────────────────────────

    fun descargarModelo() {
        if (_estado.value.operandoModelo) return
        _estado.update { it.copy(mensajeModelo = null) }
        viewModelScope.launch {
            gestorModelo.descargarDesdeGithub()
                .onSuccess { resultado ->
                    _estado.update {
                        it.copy(
                            mensajeModelo = if (resultado.respaldoEnDescargas) {
                                "Modelo instalado. Copia guardada en Descargas/Cerlita: podrás recuperarla aunque desinstales la app."
                            } else {
                                "Modelo instalado. En este Android no hay copia automática: conserva el zip por si reinstalas."
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _estado.update { it.copy(mensajeModelo = "No se pudo descargar: ${error.message}") }
                }
        }
    }

    fun importarModelo(uri: Uri) {
        if (_estado.value.operandoModelo) return
        _estado.update { it.copy(mensajeModelo = null) }
        viewModelScope.launch {
            gestorModelo.importarDesdeUri(uri)
                .onSuccess { resultado ->
                    _estado.update {
                        it.copy(mensajeModelo = "Modelo importado: ${resultado.metadatos.nombre} v${resultado.metadatos.version}.")
                    }
                }
                .onFailure { error ->
                    _estado.update { it.copy(mensajeModelo = "No se pudo importar: ${error.message}") }
                }
        }
    }

    fun eliminarModelo() {
        if (_estado.value.operandoModelo) return
        viewModelScope.launch {
            gestorModelo.desinstalar()
            _estado.update { it.copy(mensajeModelo = "Modelo eliminado. Todo lo demás de Cerlita funciona igual.") }
        }
    }
}
