package com.cerlita.app.ui.screens.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerlita.domain.model.OrigenPregunta
import com.cerlita.domain.model.Pregunta
import com.cerlita.domain.model.TipoPregunta
import com.cerlita.domain.repository.ContadoresPreguntas
import com.cerlita.domain.repository.PreguntaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Temas reconocidos por el catálogo comunitario (unificados con el validador del repo). */
val TEMAS_CERLITA = listOf(
    "viajes", "comida", "infancia", "sueños", "valores",
    "aventura", "cotidianidad", "recuerdos", "futuro",
    "afecto", "cultura", "juego", "otro",
)

/** Estado del formulario de creación/edición. */
data class DialogoEditor(
    val editandoId: String? = null,
    val texto: String = "",
    val tema: String = TEMAS_CERLITA.first(),
    val tipo: TipoPregunta = TipoPregunta.SI_NO,
    val opcion1: String = "",
    val opcion2: String = "",
    val opcion3: String = "",
    val opcion4: String = "",
    val error: String? = null,
) {
    val opciones: List<String>
        get() = listOf(opcion1, opcion2, opcion3, opcion4).map { it.trim() }.filter { it.isNotEmpty() }

    fun con(pregunta: Pregunta): DialogoEditor = DialogoEditor(
        editandoId = pregunta.id,
        texto = pregunta.texto,
        tema = pregunta.tema,
        tipo = pregunta.tipo,
        opcion1 = pregunta.opciones.getOrNull(0).orEmpty(),
        opcion2 = pregunta.opciones.getOrNull(1).orEmpty(),
        opcion3 = pregunta.opciones.getOrNull(2).orEmpty(),
        opcion4 = pregunta.opciones.getOrNull(3).orEmpty(),
    )
}

data class EditorUiState(
    val cargando: Boolean = true,
    val custom: List<Pregunta> = emptyList(),
    val contadores: ContadoresPreguntas = ContadoresPreguntas(),
    val dialogo: DialogoEditor? = null,
)

class EditorPreguntasViewModel(
    private val repositorio: PreguntaRepository,
) : ViewModel() {

    private val _estado = MutableStateFlow(EditorUiState())
    val estado: StateFlow<EditorUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repositorio.observePreguntasCustom(),
                repositorio.observeContadores(),
            ) { custom, contadores ->
                EditorUiState(cargando = false, custom = custom, contadores = contadores)
            }.collect { base ->
                // Conservar el diálogo abierto mientras se actualiza la lista
                _estado.value = if (_estado.value.dialogo != null) {
                    base.copy(dialogo = _estado.value.dialogo)
                } else {
                    base
                }
            }
        }
    }

    fun abrirDialogoNuevo() {
        _estado.value = _estado.value.copy(dialogo = DialogoEditor())
    }

    fun abrirDialogoEdicion(pregunta: Pregunta) {
        _estado.value = _estado.value.copy(dialogo = DialogoEditor().con(pregunta))
    }

    fun cambiarDialogo(transform: (DialogoEditor) -> DialogoEditor) {
        val actual = _estado.value.dialogo ?: return
        _estado.value = _estado.value.copy(dialogo = transform(actual).copy(error = null))
    }

    fun cerrarDialogo() {
        _estado.value = _estado.value.copy(dialogo = null)
    }

    fun guardarDialogo() {
        val dialogo = _estado.value.dialogo ?: return
        val texto = dialogo.texto.trim()
        when {
            texto.length !in Pregunta.MIN_TEXTO..Pregunta.MAX_TEXTO ->
                cambiarDialogo { it.copy(error = "La pregunta debe tener entre ${Pregunta.MIN_TEXTO} y ${Pregunta.MAX_TEXTO} caracteres") }

            dialogo.tipo == TipoPregunta.OPCIONES && dialogo.opciones.size < 2 ->
                cambiarDialogo { it.copy(error = "Añade al menos 2 opciones") }

            else -> viewModelScope.launch {
                if (dialogo.editandoId != null) {
                    val original = repositorio.getPregunta(dialogo.editandoId) ?: return@launch
                    repositorio.actualizarCustom(
                        original.copy(
                            texto = texto,
                            tema = dialogo.tema,
                            tipo = dialogo.tipo,
                            opciones = dialogo.opciones,
                        ),
                    )
                } else {
                    repositorio.upsertCustom(
                        texto = texto,
                        tema = dialogo.tema,
                        tipo = dialogo.tipo,
                        opciones = dialogo.opciones,
                    )
                }
                _estado.value = _estado.value.copy(dialogo = null)
            }
        }
    }

    fun toggleActiva(pregunta: Pregunta) {
        viewModelScope.launch { repositorio.setActiva(pregunta.id, !pregunta.activa) }
    }

    fun eliminar(pregunta: Pregunta) {
        viewModelScope.launch { repositorio.eliminar(pregunta.id) }
    }

    companion object {
        private const val REPO = "https://github.com/Alain314159/cerlita/issues/new"

        /**
         * URL del formulario de issue con la propuesta precargada: así se
         * "suben" preguntas desde la app al catálogo comunitario sin
         * necesitar backend ni tokens (el APK nunca lleva credenciales).
         */
        fun urlPropuesta(pregunta: Pregunta): Uri = Uri.parse(REPO).buildUpon()
            .appendQueryParameter("template", "nueva-pregunta.md")
            .appendQueryParameter("title", "Pregunta: ${pregunta.texto}")
            .appendQueryParameter(
                "body",
                buildString {
                    appendLine("### La pregunta")
                    appendLine(pregunta.texto)
                    appendLine()
                    appendLine("### Tema")
                    appendLine(pregunta.tema)
                    appendLine()
                    appendLine("### Tipo de respuesta")
                    appendLine(pregunta.tipo.name.lowercase())
                    appendLine()
                    appendLine("### Opciones (solo si el tipo es \"opciones\")")
                    appendLine(pregunta.opciones.joinToString(", ").ifEmpty { "N/A" })
                    appendLine()
                    appendLine("### ¿De dónde sale esta pregunta?")
                    appendLine("Creada desde la app Cerlita (pregunta personal)")
                },
            )
            .build()
    }
}
