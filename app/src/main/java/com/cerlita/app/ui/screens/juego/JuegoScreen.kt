package com.cerlita.app.ui.screens.juego

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cerlita.core.designsystem.components.CerlitaBoton
import com.cerlita.core.designsystem.components.CerlitaCard
import com.cerlita.core.designsystem.components.CerlitaChip
import com.cerlita.core.designsystem.components.CerlitaFondo
import com.cerlita.core.designsystem.components.KoalaFace
import com.cerlita.core.designsystem.components.PigFace
import com.cerlita.core.designsystem.theme.AzulKoala
import com.cerlita.domain.model.ModoRonda
import com.cerlita.domain.model.Pregunta
import com.cerlita.domain.model.TipoPregunta

@Composable
fun JuegoScreen(
    estado: JuegoEstado,
    onResponder: (String) -> Unit,
    onCambiarBorrador: (String) -> Unit,
    onGuardarBorrador: () -> Unit,
    onAvanzar: () -> Unit,
    onIniciar: () -> Unit,
    onVolver: () -> Unit,
) {
    CerlitaFondo {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onVolver) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Volver",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = "Ronda",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            AnimatedContent(
                targetState = estado,
                transitionSpec = {
                    (fadeIn(tween(260)) + slideInHorizontally(tween(300)) { it / 8 })
                        .togetherWith(fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { -it / 10 })
                },
                label = "estados_juego",
            ) { estadoActual ->
                when (estadoActual) {
                    is JuegoEstado.Inactivo -> PantallaInicioRonda(
                        onIniciar = onIniciar,
                        onVolver = onVolver,
                    )
                    is JuegoEstado.PreguntaActual -> PantallaPregunta(
                        estado = estadoActual,
                        onResponder = onResponder,
                        onCambiarBorrador = onCambiarBorrador,
                        onGuardarBorrador = onGuardarBorrador,
                        onAvanzar = onAvanzar,
                    )
                    is JuegoEstado.Resumen -> PantallaResumen(
                        estado = estadoActual,
                        onVolver = onVolver,
                    )
                    is JuegoEstado.SinPreguntas -> PantallaSinPreguntas(onVolver = onVolver)
                }
            }
        }
    }
}

@Composable
private fun PantallaInicioRonda(onIniciar: () -> Unit, onVolver: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollSiempre(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row {
            PigFace(tamano = 72.dp)
            Spacer(modifier = Modifier.width(14.dp))
            KoalaFace(tamano = 72.dp)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Diez preguntas para reír y conocerse mejor",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Responde tranquilo en tu teléfono. Para jugar juntos y cruzar las respuestas al final, entren al modo pareja desde el inicio.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(30.dp))
        CerlitaBoton(
            texto = "Empezar",
            onClick = onIniciar,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onVolver) { Text("Volver al inicio") }
    }
}

@Composable
private fun PantallaPregunta(
    estado: JuegoEstado.PreguntaActual,
    onResponder: (String) -> Unit,
    onCambiarBorrador: (String) -> Unit,
    onGuardarBorrador: () -> Unit,
    onAvanzar: () -> Unit,
) {
    val pregunta = estado.pregunta
    val enPareja = estado.modo == ModoRonda.PAREJA
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollSiempre()
            .padding(bottom = 8.dp),
    ) {
        // Progreso
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${estado.indice + 1} de ${estado.total}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(14.dp))
            LinearProgressIndicator(
                progress = { (estado.indice + 1f) / estado.total },
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (enPareja && !estado.conectado) {
            Spacer(modifier = Modifier.height(10.dp))
            CerlitaChip(
                "Se perdió el enlace con ${estado.nombreCompañero}; todo queda guardado",
                tono = MaterialTheme.colorScheme.tertiary,
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        CerlitaCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                CerlitaChip(pregunta.tema)
                if (enPareja && estado.respondioCompañero) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CerlitaChip(
                        "${estado.nombreCompañero} ya respondió",
                        tono = AzulKoala,
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = pregunta.texto,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(22.dp))
                ZonaRespuesta(
                    pregunta = pregunta,
                    valorActual = estado.valorActual,
                    respondida = estado.respondida,
                    onResponder = onResponder,
                    onCambiarBorrador = onCambiarBorrador,
                    onGuardarBorrador = onGuardarBorrador,
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        if (estado.respondida) {
            CerlitaBoton(
                texto = "Siguiente",
                onClick = onAvanzar,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                icono = { Icon(Icons.Rounded.Check, contentDescription = null) },
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onAvanzar) { Text("Saltar") }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ZonaRespuesta(
    pregunta: Pregunta,
    valorActual: String,
    respondida: Boolean,
    onResponder: (String) -> Unit,
    onCambiarBorrador: (String) -> Unit,
    onGuardarBorrador: () -> Unit,
) {
    when (pregunta.tipo) {
        TipoPregunta.SI_NO -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CerlitaBoton(
                    texto = "Sí",
                    onClick = { onResponder("Sí") },
                    modifier = Modifier.weight(1f).height(52.dp),
                    habilitado = !respondida,
                )
                CerlitaBoton(
                    texto = "No",
                    onClick = { onResponder("No") },
                    modifier = Modifier.weight(1f).height(52.dp),
                    habilitado = !respondida,
                )
            }
            RespuestaGuardada(valorActual, respondida)
        }

        TipoPregunta.ESCALA -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                (1..5).forEach { nivel ->
                    val elegido = respondida && valorActual == nivel.toString()
                    androidx.compose.material3.FilterChip(
                        selected = elegido,
                        enabled = !respondida,
                        onClick = { onResponder(nivel.toString()) },
                        label = { Text(nivel.toString()) },
                    )
                }
            }
            Text(
                text = "1 = para nada · 5 = muchísimo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RespuestaGuardada(valorActual, respondida)
        }

        TipoPregunta.OPCIONES -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pregunta.opciones.forEach { opcion ->
                    val elegido = respondida && valorActual == opcion
                    androidx.compose.material3.FilterChip(
                        selected = elegido,
                        enabled = !respondida,
                        onClick = { onResponder(opcion) },
                        label = { Text(opcion) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            RespuestaGuardada(valorActual, respondida)
        }

        TipoPregunta.TEXTO_LIBRE -> {
            if (respondida) {
                Text(
                    text = valorActual,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                OutlinedTextField(
                    value = valorActual,
                    onValueChange = onCambiarBorrador,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Escribe con calma…") },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Spacer(modifier = Modifier.height(10.dp))
                CerlitaBoton(
                    texto = "Guardar respuesta",
                    onClick = onGuardarBorrador,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    habilitado = valorActual.isNotBlank(),
                )
            }
        }
    }
}

@Composable
private fun RespuestaGuardada(valor: String, respondida: Boolean) {
    if (respondida && valor.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tu respuesta: $valor",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PantallaResumen(
    estado: JuegoEstado.Resumen,
    onVolver: () -> Unit,
) {
    val enPareja = estado.modo == ModoRonda.PAREJA
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollSiempre(),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            PigFace(tamano = 56.dp)
            Spacer(modifier = Modifier.width(12.dp))
            KoalaFace(tamano = 56.dp)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Ronda terminada",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (enPareja) {
                "Miren lo que respondió cada quien. Si ${estado.nombreCompañero} sigue contestando, aparecerá aquí en vivo."
            } else {
                "Estas son tus respuestas de hoy. Cuando jueguen en pareja, aquí verán las dos columnas y sus coincidencias."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (enPareja && estado.compañeroTerminó) {
            Spacer(modifier = Modifier.height(8.dp))
            CerlitaChip(
                "${estado.nombreCompañero} también terminó",
                tono = AzulKoala,
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        estado.items.forEach { item ->
            CerlitaCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = item.pregunta.texto,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (enPareja) {
                        LineaRespuesta("Tú", item.mia?.valor, esMia = true)
                        Spacer(modifier = Modifier.height(4.dp))
                        LineaRespuesta(estado.nombreCompañero, item.suya?.valor, esMia = false)
                        if (item.coinciden) {
                            Spacer(modifier = Modifier.height(8.dp))
                            CerlitaChip(
                                "Coinciden",
                                tono = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Text(
                            text = item.mia?.valor ?: "Sin respuesta",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.mia != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        CerlitaBoton(
            texto = "Volver al inicio",
            onClick = onVolver,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LineaRespuesta(nombre: String, valor: String?, esMia: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.Favorite,
            contentDescription = null,
            tint = if (esMia) MaterialTheme.colorScheme.primary else AzulKoala,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$nombre: ${valor ?: "sin respuesta todavía"}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (valor != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun PantallaSinPreguntas(onVolver: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No hay preguntas activas todavía",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Activa el catálogo en Ajustes o crea tus propias preguntas en el editor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(20.dp))
            TextButton(onClick = onVolver) { Text("Volver") }
        }
    }
}

/** Scroll vertical siempre disponible (para pantallas pequeñas). */
@Composable
private fun Modifier.verticalScrollSiempre(): Modifier =
    this.verticalScroll(rememberScrollState())
