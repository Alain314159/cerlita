package com.cerlita.app.ui.screens.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerlita.domain.model.Perfil
import com.cerlita.domain.repository.PerfilRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PerfilUiState(
    val perfil: Perfil = Perfil(),
    val guardado: Boolean = false,
) {
    val puedeGuardar: Boolean
        get() = perfil.nombrePropio.isNotBlank() && perfil.nombrePareja.isNotBlank()
}

class PerfilViewModel(
    private val repositorio: PerfilRepository,
) : ViewModel() {

    private val _estado = MutableStateFlow(PerfilUiState())
    val estado: StateFlow<PerfilUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            repositorio.perfil.collect { perfil ->
                _estado.value = _estado.value.copy(perfil = perfil, guardado = false)
            }
        }
    }

    fun actualizar(transform: (Perfil) -> Perfil) {
        _estado.value = _estado.value.copy(perfil = transform(_estado.value.perfil), guardado = false)
    }

    fun guardar() {
        val actual = _estado.value
        if (!actual.puedeGuardar) return
        viewModelScope.launch {
            repositorio.guardarPerfil(actual.perfil)
            _estado.value = actual.copy(guardado = true)
        }
    }
}
