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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
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
import com.cerlita.domain.juego.FasePregunta
import com.cerlita.domain.model.Animal
import com.cerlita.domain.model.Evaluacion
import com.cerlita.domain.model.ModoJuego
import com.cerlita.domain.model.ModoRonda
import com.cerlita.domain.model.Pregunta
import com.cerlita.domain.model.Respuesta
import com.cerlita.domain.model.TipoPregunta
import com.cerlita.domain.model.Veredicto

@Composable
fun JuegoScreen(
    estado: JuegoEstado,
    onResponder: (String) -> Unit,
    onCambiarBorrador: (String) -> Unit,
    onGuardarBorrador: () -> Unit,
    onAvanzar: () -> Unit,
    onIniciar: (ModoJuego) -> Unit,
    onJuzgar: (String, Veredicto) -> Unit,
    onAbrirCompuerta: () -> Unit,
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
                    is JuegoEstado.Inactivo -> PantallaElegirModo(
                        onIniciar = onIniciar,
                        onVolver = onVolver,
                    )

                    is JuegoEstado.Compuerta -> PantallaCompuerta(
                        estado = estadoActual,
                        onAbrir = onAbrirCompuerta,
                    )

                    is JuegoEstado.PreguntaActual -> PantallaPregunta(
                        estado = estadoActual,
                        onResponder = onResponder,
                        onCambiarBorrador = onCambiarBorrador,
                        onGuardarBorrador = onGuardarBorrador,
                        onAvanzar = onAvanzar,
                        onJuzgar = onJuzgar,
                    )

                    is JuegoEstado.Resumen -> PantallaResumen(
                        estado = estadoActual,
                        onJuzgar = onJuzgar,
                        onVolver = onVolver,
                    )

                    is JuegoEstado.SinPreguntas -> PantallaSinPreguntas(onVolver = onVolver)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Inactivo: selector de modo de juego
// ---------------------------------------------------------------------

@Composable
private fun PantallaElegirModo(onIniciar: (ModoJuego) -> Unit, onVolver: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollSiempre(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            PigFace(tamano = 72.dp)
            Spacer(modifier = Modifier.width(14.dp))
            KoalaFace(tamano = 72.dp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "¿Cómo quieren jugar?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Diez preguntas, un solo móvil que se pasa entre los dos. " +
                "La comparación la hacen ustedes: nadie conoce sus ironías mejor.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(22.dp))

        TarjetaModo(
            titulo = "Ambos responden",
            descripcion = "Los dos responden lo mismo y comprueban cuánto se parecen.",
            icono = { Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onIniciar(ModoJuego.AMBOS_RESPONDEN) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        TarjetaModo(
            titulo = "Adivina",
            descripcion = "Uno adivina lo que responderá el otro. ¿Se conocen tanto como creen?",
            icono = { Icon(Icons.Rounded.Search, contentDescription = null, tint = AzulKoala) },
            onClick = { onIniciar(ModoJuego.ADIVINA) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        TarjetaModo(
            titulo = "Mixto",
            descripcion = "Mitad y mitad: preguntas para coincidir y para adivinar, alternadas.",
            icono = { Icon(Icons.Rounded.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
            onClick = { onIniciar(ModoJuego.MIXTO) },
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Para jugar cada uno desde su teléfono, entren al modo pareja desde el inicio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onVolver) { Text("Volver al inicio") }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun TarjetaModo(
    titulo: String,
    descripcion: String,
    icono: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    CerlitaCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            icono()
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(titulo, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    descripcion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------
// Compuerta: pasa el móvil
// ---------------------------------------------------------------------

@Composable
private fun PantallaCompuerta(estado: JuegoEstado.Compuerta, onAbrir: () -> Unit) {
    val nombre = estado.nombres[estado.destinatario] ?: "tu pareja"
    val instruccion = instruccionCompuerta(estado)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollSiempre(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MascotaDe(estado.destinatario, tamano = 96.dp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Pasa el móvil a $nombre",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = instruccion,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(modifier = Modifier.height(30.dp))
        CerlitaBoton(
            texto = "Ya lo tengo, soy $nombre",
            onClick = onAbrir,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun instruccionCompuerta(estado: JuegoEstado.Compuerta): String {
    val nombres = estado.nombres
    val destinatario = nombres[estado.destinatario] ?: "tu pareja"
    val otro = nombres.entries.firstOrNull { it.key != estado.destinatario }?.value ?: "tu pareja"
    return when {
        estado.modoPregunta == ModoJuego.ADIVINA && estado.fase == FasePregunta.TURNO_1 ->
            "$destinatario va a adivinar lo que responderá $otro. Que $otro no mire."

        estado.modoPregunta == ModoJuego.ADIVINA ->
            "$otro ya escribió su adivinanza. Ahora $destinatario responde con la verdad."

        estado.fase == FasePregunta.TURNO_1 ->
            "Empieza $destinatario. Después le toca a $otro con la misma pregunta."

        else ->
            "$otro ya respondió esta pregunta. Ahora $destinatario, sin espiar."
    }
}

// ---------------------------------------------------------------------
// Pregunta: turno de escritura o revelación
// ---------------------------------------------------------------------

@Composable
private fun PantallaPregunta(
    estado: JuegoEstado.PreguntaActual,
    onResponder: (String) -> Unit,
    onCambiarBorrador: (String) -> Unit,
    onGuardarBorrador: () -> Unit,
    onAvanzar: () -> Unit,
    onJuzgar: (String, Veredicto) -> Unit,
) {
    val pregunta = estado.pregunta
    val enPareja = estado.modo == ModoRonda.PAREJA
    val enRevelacion = estado.fase == FasePregunta.REVELACION

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

        Spacer(modifier = Modifier.height(20.dp))

        if (enRevelacion) {
            PantallaRevelacion(
                estado = estado,
                onJuzgar = onJuzgar,
                onAvanzar = onAvanzar,
            )
        } else {
            PantallaTurno(
                estado = estado,
                enPareja = enPareja,
                pregunta = pregunta,
                onResponder = onResponder,
                onCambiarBorrador = onCambiarBorrador,
                onGuardarBorrador = onGuardarBorrador,
                onAvanzar = onAvanzar,
            )
        }
    }
}

@Composable
private fun PantallaTurno(
    estado: JuegoEstado.PreguntaActual,
    enPareja: Boolean,
    pregunta: Pregunta,
    onResponder: (String) -> Unit,
    onCambiarBorrador: (String) -> Unit,
    onGuardarBorrador: () -> Unit,
    onAvanzar: () -> Unit,
) {
    val nombreAutor = estado.nombres[estado.autorTurno] ?: "quien tiene el móvil"
    val (titulo, subtitulo) = encabezadoTurno(estado, enPareja)

    Row(verticalAlignment = Alignment.CenterVertically) {
        MascotaDe(estado.autorTurno, tamano = 44.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitulo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    CerlitaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            CerlitaChip(pregunta.tema)
            if (enPareja && estado.respondioCompañero) {
                Spacer(modifier = Modifier.height(8.dp))
                CerlitaChip("${estado.nombreCompañero} ya respondió", tono = AzulKoala)
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
                pista = pistaTurno(estado, enPareja),
                onResponder = onResponder,
                onCambiarBorrador = onCambiarBorrador,
                onGuardarBorrador = onGuardarBorrador,
            )
        }
    }

    Spacer(modifier = Modifier.height(22.dp))

    if (estado.respondida) {
        CerlitaBoton(
            texto = if (enPareja) "Siguiente" else "Listo, pasa el móvil",
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

private fun encabezadoTurno(estado: JuegoEstado.PreguntaActual, enPareja: Boolean): Pair<String, String> {
    val nombres = estado.nombres
    val autor = nombres[estado.autorTurno] ?: "quien tiene el móvil"
    val otro = nombres.entries.firstOrNull { it.key != estado.autorTurno }?.value ?: "tu pareja"
    return when {
        enPareja && estado.modoPregunta == ModoJuego.ADIVINA && estado.soyAdivino ->
            "Tu adivinanza, $autor" to "Escribe lo que crees que responderá $otro."

        enPareja && estado.modoPregunta == ModoJuego.ADIVINA ->
            "Responde con la verdad, $autor" to "A $otro le toca adivinar esto."

        !enPareja && estado.modoPregunta == ModoJuego.ADIVINA && estado.fase == FasePregunta.TURNO_1 ->
            "Adivina, $autor" to "¿Qué crees que responderá $otro?"

        !enPareja && estado.modoPregunta == ModoJuego.ADIVINA ->
            "La verdad, $autor" to "Responde lo que de verdad piensas."

        !enPareja && estado.fase == FasePregunta.TURNO_1 ->
            "Tu turno, $autor" to "Después le pasas el móvil a $otro."

        else ->
            "Tu turno, $autor" to "Responde sin espiar la respuesta anterior."
    }
}

private fun pistaTurno(estado: JuegoEstado.PreguntaActual, enPareja: Boolean): String =
    if (estado.modoPregunta == ModoJuego.ADIVINA &&
        (estado.fase == FasePregunta.TURNO_1 || (enPareja && estado.soyAdivino))
    ) {
        "Lo que responda la otra persona…"
    } else {
        "Escribe con calma…"
    }

// ---------------------------------------------------------------------
// Revelación (LOCAL): las dos respuestas + veredicto
// ---------------------------------------------------------------------

@Composable
private fun PantallaRevelacion(
    estado: JuegoEstado.PreguntaActual,
    onJuzgar: (String, Veredicto) -> Unit,
    onAvanzar: () -> Unit,
) {
    val pregunta = estado.pregunta
    val adivina = estado.modoPregunta == ModoJuego.ADIVINA

    CerlitaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            CerlitaChip(if (adivina) "Adivinanza" else "Ambos responden", tono = MaterialTheme.colorScheme.tertiary)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = pregunta.texto,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(20.dp))

            val primero = com.cerlita.domain.juego.ReglasJuego.autorDeTurno(
                estado.modoPregunta,
                estado.indice,
                0,
            )
            val segundo = if (primero == Animal.CERDITA) Animal.KOALA else Animal.CERDITA

            FilaRespuestaRevelada(
                autor = primero,
                etiqueta = if (adivina) "Su adivinanza" else null,
                respuesta = estado.respuestasReveladas[primero],
                nombres = estado.nombres,
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilaRespuestaRevelada(
                autor = segundo,
                etiqueta = if (adivina) "Lo que respondió" else null,
                respuesta = estado.respuestasReveladas[segundo],
                nombres = estado.nombres,
            )

            Spacer(modifier = Modifier.height(20.dp))
            ZonaJuicio(
                modoPregunta = estado.modoPregunta,
                evaluacion = estado.evaluacion,
                juicioPendiente = estado.juicioPendiente,
                onJuzgar = { veredicto -> onJuzgar(pregunta.id, veredicto) },
            )
        }
    }

    Spacer(modifier = Modifier.height(22.dp))

    if (!estado.juicioPendiente) {
        CerlitaBoton(
            texto = "Siguiente",
            onClick = onAvanzar,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            icono = { Icon(Icons.Rounded.Check, contentDescription = null) },
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun FilaRespuestaRevelada(
    autor: Animal,
    etiqueta: String?,
    respuesta: Respuesta?,
    nombres: Map<Animal, String>,
) {
    val nombre = nombres[autor] ?: "quien respondió"
    CerlitaCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MascotaDe(autor, tamano = 34.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = etiqueta?.let { "$nombre · $it" } ?: nombre,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = respuesta?.valor?.ifBlank { " " } ?: "sin respuesta",
                style = MaterialTheme.typography.bodyLarge,
                color = if (respuesta != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * El corazón del rediseño: el veredicto. Tipos cerrados -> chip automático;
 * texto libre -> tres botones que oprime la pareja, juntas y en vivo.
 */
@Composable
private fun ZonaJuicio(
    modoPregunta: ModoJuego,
    evaluacion: Evaluacion?,
    juicioPendiente: Boolean,
    onJuzgar: (Veredicto) -> Unit,
) {
    if (evaluacion != null) {
        val texto = etiquetaVeredicto(modoPregunta, evaluacion.veredicto)
        CerlitaChip(
            texto,
            tono = when (evaluacion.veredicto) {
                Veredicto.AFIRMATIVO -> MaterialTheme.colorScheme.primary
                Veredicto.INTERMEDIO -> MaterialTheme.colorScheme.tertiary
                Veredicto.NEGATIVO -> MaterialTheme.colorScheme.outline
            },
        )
        return
    }

    if (!juicioPendiente) {
        CerlitaChip("Comparando…")
        return
    }

    Text(
        text = if (modoPregunta == ModoJuego.ADIVINA) {
            "¿Acertó la adivinanza? Juzguen juntos"
        } else {
            "¿Se parecen las respuestas? Juzguen juntos"
        },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(12.dp))
    if (modoPregunta == ModoJuego.ADIVINA) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CerlitaBoton(
                texto = "Acertó",
                onClick = { onJuzgar(Veredicto.AFIRMATIVO) },
                modifier = Modifier.weight(1f).height(52.dp),
            )
            CerlitaBoton(
                texto = "Más o menos",
                onClick = { onJuzgar(Veredicto.INTERMEDIO) },
                modifier = Modifier.weight(1f).height(52.dp),
            )
            CerlitaBoton(
                texto = "Falló",
                onClick = { onJuzgar(Veredicto.NEGATIVO) },
                modifier = Modifier.weight(1f).height(52.dp),
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CerlitaBoton(
                texto = "Sí",
                onClick = { onJuzgar(Veredicto.AFIRMATIVO) },
                modifier = Modifier.weight(1f).height(52.dp),
            )
            CerlitaBoton(
                texto = "Parecido",
                onClick = { onJuzgar(Veredicto.INTERMEDIO) },
                modifier = Modifier.weight(1f).height(52.dp),
            )
            CerlitaBoton(
                texto = "No",
                onClick = { onJuzgar(Veredicto.NEGATIVO) },
                modifier = Modifier.weight(1f).height(52.dp),
            )
        }
    }
}

private fun etiquetaVeredicto(modoPregunta: ModoJuego, veredicto: Veredicto): String =
    if (modoPregunta == ModoJuego.ADIVINA) {
        when (veredicto) {
            Veredicto.AFIRMATIVO -> "¡Acertó!"
            Veredicto.INTERMEDIO -> "Más o menos"
            Veredicto.NEGATIVO -> "Falló"
        }
    } else {
        when (veredicto) {
            Veredicto.AFIRMATIVO -> "Coinciden"
            Veredicto.INTERMEDIO -> "Casi coinciden"
            Veredicto.NEGATIVO -> "Distintas"
        }
    }

// ---------------------------------------------------------------------
// Zona de respuesta por tipo (entrada)
// ---------------------------------------------------------------------

@Composable
private fun ZonaRespuesta(
    pregunta: Pregunta,
    valorActual: String,
    respondida: Boolean,
    pista: String,
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
                    placeholder = { Text(pista) },
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
            text = "Respuesta guardada: $valor",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------
// Resumen de la ronda
// ---------------------------------------------------------------------

@Composable
private fun PantallaResumen(
    estado: JuegoEstado.Resumen,
    onJuzgar: (String, Veredicto) -> Unit,
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
        if (estado.evaluadas > 0) {
            Text(
                text = "${
                    when (estado.modoJuego) {
                        ModoJuego.ADIVINA -> "Adivinanzas acertadas"
                        else -> "Coincidencias"
                    }
                }: ${estado.aciertos} de ${estado.evaluadas}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(
            text = if (enPareja) {
                "Miren lo que respondió cada quien. Si ${estado.nombreCompañero} sigue " +
                    "contestando, aparecerá aquí en vivo. Las de texto libre las juzgan ustedes."
            } else {
                "Todo lo que respondieron, pregunta por pregunta. Las respuestas escritas " +
                    "las juzgaron ustedes mismos: nadie conoce sus matices mejor."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (enPareja && estado.compañeroTerminó) {
            Spacer(modifier = Modifier.height(8.dp))
            CerlitaChip("${estado.nombreCompañero} también terminó", tono = AzulKoala)
        }
        Spacer(modifier = Modifier.height(18.dp))
        estado.items.forEach { item ->
            TarjetaResumenItem(
                item = item,
                nombres = estado.nombres,
                enPareja = enPareja,
                onJuzgar = onJuzgar,
            )
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
private fun TarjetaResumenItem(
    item: ResumenItem,
    nombres: Map<Animal, String>,
    enPareja: Boolean,
    onJuzgar: (String, Veredicto) -> Unit,
) {
    CerlitaCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CerlitaChip(
                    when (item.modoPregunta) {
                        ModoJuego.ADIVINA -> "Adivina"
                        else -> "Ambos"
                    },
                    tono = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                CerlitaChip(item.pregunta.tema)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = item.pregunta.texto,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (enPareja || item.respuestas.isNotEmpty()) {
                item.respuestas.forEach { (autor, respuesta) ->
                    LineaRespuesta(
                        autor = autor,
                        nombre = nombres[autor] ?: "alguien",
                        valor = respuesta.valor,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            } else {
                Text(
                    text = "Sin respuesta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            ZonaJuicio(
                modoPregunta = item.modoPregunta,
                evaluacion = item.evaluacion,
                juicioPendiente = item.juicioPendiente,
                onJuzgar = { veredicto -> onJuzgar(item.pregunta.id, veredicto) },
            )
        }
    }
}

@Composable
private fun LineaRespuesta(autor: Animal, nombre: String, valor: String) {
    Row(verticalAlignment = Alignment.Top) {
        MascotaDe(autor, tamano = 26.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = nombre,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valor,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------------
// Comunes
// ---------------------------------------------------------------------

@Composable
private fun MascotaDe(animal: Animal, tamano: androidx.compose.ui.unit.Dp) {
    if (animal == Animal.CERDITA) {
        PigFace(tamano = tamano)
    } else {
        KoalaFace(tamano = tamano)
    }
}

@Composable
private fun PantallaSinPreguntas(onVolver: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
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
