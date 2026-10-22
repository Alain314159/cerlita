package com.cerlita.app.ui.screens.editor

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cerlita.core.designsystem.components.CerlitaCard
import com.cerlita.core.designsystem.components.CerlitaChip
import com.cerlita.core.designsystem.components.CerlitaFondo
import com.cerlita.domain.model.Pregunta
import com.cerlita.domain.model.TipoPregunta

@Composable
fun EditorPreguntasScreen(
    estado: EditorUiState,
    onAbrirDialogoNuevo: () -> Unit,
    onAbrirDialogoEdicion: (Pregunta) -> Unit,
    onCambiarDialogo: ((DialogoEditor) -> DialogoEditor) -> Unit,
    onGuardarDialogo: () -> Unit,
    onCerrarDialogo: () -> Unit,
    onToggleActiva: (Pregunta) -> Unit,
    onEliminar: (Pregunta) -> Unit,
    onVolver: () -> Unit,
) {
    val contexto = LocalContext.current
    var pendienteEliminar by remember { mutableStateOf<Pregunta?>(null) }

    CerlitaFondo {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onAbrirDialogoNuevo,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nueva pregunta")
                }
            },
        ) { inner ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Text("Mis preguntas", style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Crea preguntas solo para ustedes, actívalas o desactívalas, o propónlas al catálogo comunitario para todas las parejas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (estado.custom.isEmpty() && !estado.cargando) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Aún no tienen preguntas propias",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Toca \"Nueva pregunta\" y escribe algo que solo ustedes entenderían",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(estado.custom, key = { it.id }) { pregunta ->
                            ItemPregunta(
                                pregunta = pregunta,
                                onEditar = { onAbrirDialogoEdicion(pregunta) },
                                onProponer = {
                                    runCatching {
                                        contexto.startActivity(
                                            Intent(Intent.ACTION_VIEW, EditorPreguntasViewModel.urlPropuesta(pregunta)),
                                        )
                                    }
                                },
                                onEliminar = { pendienteEliminar = pregunta },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(88.dp)) }
                    }
                }
            }
        }
    }

    // Diálogo de creación/edición
    estado.dialogo?.let { dialogo ->
        DialogoPregunta(
            dialogo = dialogo,
            onCambiar = onCambiarDialogo,
            onGuardar = onGuardarDialogo,
            onCerrar = onCerrarDialogo,
        )
    }

    // Confirmación de borrado
    pendienteEliminar?.let { pregunta ->
        AlertDialog(
            onDismissRequest = { pendienteEliminar = null },
            title = { Text("¿Borrar la pregunta?") },
            text = { Text("\"${pregunta.texto}\"") },
            confirmButton = {
                TextButton(onClick = {
                    onEliminar(pregunta)
                    pendienteEliminar = null
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendienteEliminar = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun ItemPregunta(
    pregunta: Pregunta,
    onEditar: () -> Unit,
    onProponer: () -> Unit,
    onEliminar: () -> Unit,
) {
    CerlitaCard(modifier = Modifier.fillMaxWidth(), onClick = onEditar) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CerlitaChip(pregunta.tema, tono = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(8.dp))
                CerlitaChip(
                    when (pregunta.tipo) {
                        TipoPregunta.SI_NO -> "sí/no"
                        TipoPregunta.ESCALA -> "escala 1-5"
                        TipoPregunta.OPCIONES -> "opciones"
                        TipoPregunta.TEXTO_LIBRE -> "texto libre"
                    },
                )
                Spacer(modifier = Modifier.weight(1f))
                if (pregunta.activa) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Activa",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(pregunta.texto, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(10.dp))
            Row {
                TextButton(onClick = onProponer) {
                    Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.height(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Proponer al catálogo")
                }
                TextButton(onClick = onEliminar) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Borrar",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.height(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogoPregunta(
    dialogo: DialogoEditor,
    onCambiar: ((DialogoEditor) -> DialogoEditor) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(if (dialogo.editandoId == null) "Nueva pregunta" else "Editar pregunta") },
        text = {
            Column(modifier = Modifier.imePadding()) {
                OutlinedTextField(
                    value = dialogo.texto,
                    onValueChange = { texto -> onCambiar { it.copy(texto = texto) } },
                    label = { Text("La pregunta") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Tema
                var temaAbierto by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = temaAbierto,
                    onExpandedChange = { temaAbierto = it },
                ) {
                    OutlinedTextField(
                        value = dialogo.tema,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tema") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = temaAbierto) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = temaAbierto, onDismissRequest = { temaAbierto = false }) {
                        EditorPreguntasViewModel.TEMAS.forEach { tema ->
                            DropdownMenuItem(
                                text = { Text(tema) },
                                onClick = {
                                    onCambiar { it.copy(tema = tema) }
                                    temaAbierto = false
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Tipo
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TipoPregunta.entries.forEach { tipo ->
                        FilterChip(
                            selected = dialogo.tipo == tipo,
                            onClick = { onCambiar { it.copy(tipo = tipo) } },
                            label = {
                                Text(
                                    when (tipo) {
                                        TipoPregunta.SI_NO -> "Sí/No"
                                        TipoPregunta.ESCALA -> "1-5"
                                        TipoPregunta.OPCIONES -> "Opciones"
                                        TipoPregunta.TEXTO_LIBRE -> "Texto"
                                    },
                                )
                            },
                        )
                    }
                }

                if (dialogo.tipo == TipoPregunta.OPCIONES) {
                    Spacer(modifier = Modifier.height(12.dp))
                    (0 until 4).forEach { indice ->
                        val valorActual = when (indice) {
                            0 -> dialogo.opcion1
                            1 -> dialogo.opcion2
                            2 -> dialogo.opcion3
                            else -> dialogo.opcion4
                        }
                        OutlinedTextField(
                            value = valorActual,
                            onValueChange = { v ->
                                onCambiar {
                                    when (indice) {
                                        0 -> it.copy(opcion1 = v)
                                        1 -> it.copy(opcion2 = v)
                                        2 -> it.copy(opcion3 = v)
                                        else -> it.copy(opcion4 = v)
                                    }
                                }
                            },
                            label = { Text("Opción ${indice + 1}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        )
                    }
                }

                dialogo.error?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onGuardar) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) { Text("Cancelar") }
        },
    )
}
