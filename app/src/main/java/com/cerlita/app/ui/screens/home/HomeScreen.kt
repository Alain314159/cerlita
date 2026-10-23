package com.cerlita.app.ui.screens.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerlita.core.designsystem.components.CerlitaBoton
import com.cerlita.core.designsystem.components.CerlitaCard
import com.cerlita.core.designsystem.components.CerlitaChip
import com.cerlita.core.designsystem.components.CerlitaFondo
import com.cerlita.core.designsystem.components.KoalaFace
import com.cerlita.core.designsystem.components.PigFace
import com.cerlita.core.designsystem.theme.AzulKoala

@Composable
fun HomeScreen(
    estado: HomeUiState,
    onJugar: () -> Unit,
    onPareja: () -> Unit,
    onEditor: () -> Unit,
    onPerfil: () -> Unit,
    onAjustes: () -> Unit,
) {
    CerlitaFondo {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Mascotas con animación de flotado suave (spring physics)
            val transicion = rememberInfiniteTransition(label = "mascotas")
            val flotarPig by transicion.animateFloat(
                initialValue = -6f,
                targetValue = 6f,
                animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                label = "flotarPig",
            )
            val flotarKoala by transicion.animateFloat(
                initialValue = 6f,
                targetValue = -6f,
                animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse),
                label = "flotarKoala",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                PigFace(
                    tamano = 88.dp,
                    modifier = Modifier.graphicsLayer { translationY = flotarPig },
                )
                Spacer(modifier = Modifier.width(16.dp))
                KoalaFace(
                    tamano = 88.dp,
                    modifier = Modifier.graphicsLayer { translationY = flotarKoala },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Cerlita",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = saludo(estado),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Estadísticas rápidas
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CerlitaChip("${estado.contadores.oficialesActivas} preguntas")
                CerlitaChip("${estado.contadores.customActivas} propias", tono = AzulKoala)
                CerlitaChip("${estado.contadores.totalRespuestas} respuestas", tono = MaterialTheme.colorScheme.tertiary)
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (!estado.perfil.estaCompleto) {
                CerlitaCard(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onPerfil,
                ) {
                    Text(
                        text = "Antes de jugar, cuéntanos quiénes son",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            CerlitaBoton(
                texto = if (estado.rondaEnCurso) "Continuar ronda" else "Jugar una ronda",
                onClick = onJugar,
                modifier = Modifier.fillMaxWidth().height(58.dp),
            )
            if (estado.rondaEnCurso) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tienes una ronda a medias esperándote",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Accesos
            CerlitaCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onEditor,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Crear preguntas", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Añade tus propias preguntas o propónlas para todos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CerlitaCard(
                    modifier = Modifier.weight(1f),
                    onClick = onPerfil,
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Perfil", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Nombres y mascotas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                CerlitaCard(
                    modifier = Modifier.weight(1f),
                    onClick = onAjustes,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(20.dp),
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Ajustes", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Modo pareja (Fase 2): P2P con Nearby Connections, sin internet
            CerlitaCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onPareja,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Jugar en pareja", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Sin internet: los teléfonos se encuentran por Bluetooth",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun saludo(estado: HomeUiState): String {
    val propio = estado.perfil.nombrePropio.trim()
    val pareja = estado.perfil.nombrePareja.trim()
    return when {
        propio.isNotEmpty() && pareja.isNotEmpty() -> "$propio y $pareja, este rato es de ustedes"
        propio.isNotEmpty() -> "Hola, $propio. ¿Preparado/a para reír juntos?"
        pareja.isNotEmpty() -> "¿Y $pareja dónde anda? Configura tu perfil"
        else -> "Un jueguito para conocerse mejor, sin pantallas de por medio… bueno, casi"
    }
}
