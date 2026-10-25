package com.cerlita.app.ui.screens.pareja

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerlita.core.designsystem.components.CaraAnimal
import com.cerlita.core.designsystem.components.CerlitaBoton
import com.cerlita.core.designsystem.components.CerlitaCard
import com.cerlita.core.designsystem.components.CerlitaChip
import com.cerlita.core.designsystem.components.CerlitaFondo
import com.cerlita.core.designsystem.theme.AzulKoala
import com.cerlita.core.designsystem.theme.RosaCerdita
import com.cerlita.domain.model.Animal
import com.cerlita.domain.model.EstadoP2P
import com.cerlita.domain.model.ModoJuego
import kotlinx.coroutines.delay

/**
 * Lobby del modo pareja: encontrar al otro teléfono por Bluetooth/Wi-Fi
 * Direct (Nearby Connections), sin internet ni datos móviles.
 */
@Composable
fun ParejaScreen(
    estado: ParejaUiState,
    onBuscar: () -> Unit,
    onConectar: (String) -> Unit,
    onElegirModo: (ModoJuego) -> Unit,
    onIniciarRonda: () -> Unit,
    onDesconectar: () -> Unit,
    onVolver: () -> Unit,
    onResultadoPermisos: (Map<String, Boolean>) -> Unit,
    onAbrirAjustesApp: () -> Unit,
    onLimpiarError: () -> Unit,
) {
    // Permisos runtime: "dispositivos cercanos" en 31+ (Android 12),
    // ubicación fina antes (el perfilador clásico de Bluetooth).
    val permisosNecesarios = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val lanzadorPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { resultado -> onResultadoPermisos(resultado) }

    // El error de transporte se auto-limpia tras unos segundos.
    LaunchedEffect(estado.error) {
        if (estado.error != null) {
            delay(4_000)
            onLimpiarError()
        }
    }

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
                    text = "Jugar en pareja",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            estado.error?.let {
                Spacer(modifier = Modifier.height(4.dp))
                CerlitaChip(it, tono = MaterialTheme.colorScheme.tertiary)
            }

            AnimatedContent(
                targetState = estado,
                transitionSpec = {
                    fadeIn(tween(260)) togetherWith fadeOut(tween(180))
                },
                label = "estados_pareja",
            ) { st ->
                when {
                    !st.perfil.estaCompleto -> SinPerfil(onVolver)
                    st.permisos == PermisosPareja.DENEGADO -> SinPermisos(
                        onReintentar = { lanzadorPermisos.launch(permisosNecesarios) },
                        onAbrirAjustes = onAbrirAjustesApp,
                    )

                    st.estadoP2P == EstadoP2P.CONECTADO -> Conectados(
                        st = st,
                        onIniciarRonda = onIniciarRonda,
                        onElegirModo = onElegirModo,
                        onDesconectar = onDesconectar,
                    )

                    st.estadoP2P == EstadoP2P.CONECTANDO -> Buscando(
                        st = st,
                        mensajeExtra = "Conectando con tu pareja…",
                        onConectar = onConectar,
                        onCancelar = onDesconectar,
                    )

                    st.estadoP2P == EstadoP2P.DESCONECTADO -> Desconectado(
                        onReintentar = {
                            lanzadorPermisos.launch(permisosNecesarios)
                            onBuscar()
                        },
                        onVolver = onVolver,
                    )

                    st.estadoP2P == EstadoP2P.BUSCANDO -> Buscando(
                        st = st,
                        mensajeExtra = null,
                        onConectar = onConectar,
                        onCancelar = onDesconectar,
                    )

                    else -> Inactivo(
                        st = st,
                        onBuscar = {
                            if (st.permisos == PermisosPareja.CONCEDIDO) {
                                onBuscar()
                            } else {
                                lanzadorPermisos.launch(permisosNecesarios)
                            }
                        },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Estados del lobby
// ------------------------------------------------------------------

@Composable
private fun SinPerfil(onVolver: () -> Unit) {
    ColumnaCentrada {
        CerlitaCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Antes de buscar a tu pareja, dinos quién eres",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "En Perfil se guardan tu nombre y tu animal. Con eso te presenta Cerlita en el otro teléfono.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(14.dp))
                CerlitaBoton(
                    texto = "Volver al inicio",
                    onClick = onVolver,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                )
            }
        }
    }
}

@Composable
private fun SinPermisos(
    onReintentar: () -> Unit,
    onAbrirAjustes: () -> Unit,
) {
    ColumnaCentrada {
        CerlitaCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = RosaCerdita,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Sin estos permisos no podemos encontrar a tu pareja",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Cerlita usa el Bluetooth solo para comunicar los dos teléfonos entre sí; nunca envía nada por internet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(14.dp))
                CerlitaBoton(
                    texto = "Reintentar",
                    onClick = onReintentar,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                )
                TextButton(onClick = onAbrirAjustes) { Text("Abrir ajustes de la app") }
            }
        }
    }
}

@Composable
private fun Inactivo(
    st: ParejaUiState,
    onBuscar: () -> Unit,
) {
    ColumnaCentrada {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CaraAnimal(st.perfil.animal == Animal.CERDITA, tamano = 92.dp)
            Spacer(modifier = Modifier.width(18.dp))
            // Su pareja, aún "al otro lado": el animal contrario.
            CaraAnimal(st.perfil.animal != Animal.CERDITA, tamano = 92.dp)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "El mismo juego, cada quien en su teléfono",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Los dos responden las mismas preguntas por separado y al final comparan. Funciona por Bluetooth, sin internet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(30.dp))
        CerlitaBoton(
            texto = "Buscar a mi pareja",
            onClick = onBuscar,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            icono = { Icon(Icons.Rounded.Favorite, contentDescription = null) },
        )
    }
}

@Composable
private fun Buscando(
    st: ParejaUiState,
    mensajeExtra: String?,
    onConectar: (String) -> Unit,
    onCancelar: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = mensajeExtra ?: "Buscando cerca… Acércate con el Bluetooth encendido",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(18.dp))

        if (st.encontrados.isEmpty()) {
            Text(
                text = "Todavía no aparece nadie. Que tu pareja también abra Cerlita y toque \"Buscar a mi pareja\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(st.encontrados, key = { it.id }) { endpoint ->
                    CerlitaCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onConectar(endpoint.id) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = AzulKoala,
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = endpoint.nombre,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Encontrado cerca",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            CerlitaChip("Conectar")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onCancelar) { Text("Dejar de buscar") }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

/** Selección compacta del modo de juego (host del modo pareja). */
@Composable
private fun SelectorModoPareja(modo: ModoJuego, onElegir: (ModoJuego) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "¿Cómo quieren jugar?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.height(210.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OpcionModo(
                    titulo = "Ambos responden",
                    descripcion = "Los dos responden lo mismo y ven cuánto se parecen.",
                    seleccionado = modo == ModoJuego.AMBOS_RESPONDEN,
                    onClick = { onElegir(ModoJuego.AMBOS_RESPONDEN) },
                )
            }
            item {
                OpcionModo(
                    titulo = "Adivina",
                    descripcion = "Cada quien adivina lo que responderá el otro.",
                    seleccionado = modo == ModoJuego.ADIVINA,
                    onClick = { onElegir(ModoJuego.ADIVINA) },
                )
            }
            item {
                OpcionModo(
                    titulo = "Mixto",
                    descripcion = "Alternan: unas para coincidir, otras para adivinar.",
                    seleccionado = modo == ModoJuego.MIXTO,
                    onClick = { onElegir(ModoJuego.MIXTO) },
                )
            }
        }
    }
}

@Composable
private fun OpcionModo(
    titulo: String,
    descripcion: String,
    seleccionado: Boolean,
    onClick: () -> Unit,
) {
    CerlitaCard(
        modifier = Modifier.fillMaxWidth(),
        color = if (seleccionado) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = if (seleccionado) "$titulo · elegido" else titulo,
                style = MaterialTheme.typography.titleMedium,
                color = if (seleccionado) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Conectados(
    st: ParejaUiState,
    onIniciarRonda: () -> Unit,
    onElegirModo: (ModoJuego) -> Unit,
    onDesconectar: () -> Unit,
) {
    val otro = st.otro
    ColumnaCentrada {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CaraAnimal(st.perfil.animal == Animal.CERDITA, tamano = 86.dp)
            Spacer(modifier = Modifier.width(20.dp))
            CerlitaChip("conectados", tono = RosaCerdita)
            Spacer(modifier = Modifier.width(20.dp))
            CaraAnimal(
                otro?.let { it.animal == Animal.CERDITA }
                    ?: (st.perfil.animal != Animal.CERDITA),
                tamano = 86.dp,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (otro != null) {
                "${st.perfil.nombrePropio} y ${otro.nombre}, listos"
            } else {
                "Conectados con tu pareja"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Quien conecta elige el modo y la ronda de diez preguntas. Las respuestas se cruzan solas al ir respondiendo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(26.dp))
        if (st.soyHost) {
            SelectorModoPareja(modo = st.modoJuego, onElegir = onElegirModo)
            Spacer(modifier = Modifier.height(16.dp))
            CerlitaBoton(
                texto = "Empezar ronda juntos",
                onClick = onIniciarRonda,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                icono = { Icon(Icons.Rounded.Check, contentDescription = null) },
            )
        } else {
            CerlitaChip(
                "Esperando a que ${otro?.nombre ?: "tu pareja"} elija y empiece la ronda…",
                tono = AzulKoala,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = onDesconectar) { Text("Desconectar") }
    }
}

@Composable
private fun Desconectado(
    onReintentar: () -> Unit,
    onVolver: () -> Unit,
) {
    ColumnaCentrada {
        Text(
            text = "Se perdió la conexión",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tranquilos: cada respuesta queda guardada en su teléfono. Pueden reconectarse y seguir donde iban.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        CerlitaBoton(
            texto = "Volver a buscar",
            onClick = onReintentar,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            icono = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onVolver) { Text("Salir del modo pareja") }
    }
}

/** Columna vertical centrada que ocupa todo el espacio disponible. */
@Composable
private fun ColumnaCentrada(contenido: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        contenido()
    }
}
