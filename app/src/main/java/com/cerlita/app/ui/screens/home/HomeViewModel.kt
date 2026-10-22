package com.cerlita.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerlita.domain.model.Perfil
import com.cerlita.domain.repository.ContadoresPreguntas
import com.cerlita.domain.repository.PerfilRepository
import com.cerlita.domain.repository.PreguntaRepository
import com.cerlita.domain.repository.RondaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HomeUiState(
    val perfil: Perfil = Perfil(),
    val contadores: ContadoresPreguntas = ContadoresPreguntas(),
    val rondaEnCurso: Boolean = false,
)

class HomeViewModel(
    perfilRepository: PerfilRepository,
    preguntaRepository: PreguntaRepository,
    rondaRepository: RondaRepository,
) : ViewModel() {

    private val _estado = MutableStateFlow(HomeUiState())
    val estado: StateFlow<HomeUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                perfilRepository.perfil,
                preguntaRepository.observeContadores(),
                rondaRepository.observeRondaActiva(),
            ) { perfil, contadores, ronda ->
                HomeUiState(
                    perfil = perfil,
                    contadores = contadores,
                    rondaEnCurso = ronda != null,
                )
            }.collect { _estado.value = it }
        }
    }
}
