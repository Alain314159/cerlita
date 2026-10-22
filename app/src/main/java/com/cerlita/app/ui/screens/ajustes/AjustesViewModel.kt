package com.cerlita.app.ui.screens.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerlita.data.repo.GestorCatalogo
import com.cerlita.data.repo.ResultadoActualizacion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AjustesUiState(
    val versionCatalogo: String = "",
    val buscando: Boolean = false,
    val resultado: ResultadoActualizacion? = null,
) {
    val mensajeResultado: String?
        get() = when (val r = resultado) {
            is ResultadoActualizacion.AlDia -> "Ya tienes la versión más reciente (${r.version})"
            is ResultadoActualizacion.Actualizado -> "Catálogo actualizado: ${r.preguntas} preguntas nuevas (v${r.hacia})"
            is ResultadoActualizacion.Error -> "No se pudo actualizar: ${r.mensaje}"
            null -> null
        }
}

class AjustesViewModel(
    private val gestorCatalogo: GestorCatalogo,
) : ViewModel() {

    private val _estado = MutableStateFlow(AjustesUiState())
    val estado: StateFlow<AjustesUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            gestorCatalogo.versionInstalada.collect { version ->
                _estado.value = _estado.value.copy(versionCatalogo = version)
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
}
