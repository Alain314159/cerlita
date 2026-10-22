package com.cerlita.core.designsystem.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.cerlita.core.designsystem.theme.FondoOscuro
import com.cerlita.core.designsystem.theme.RosaCerdita
import com.cerlita.core.designsystem.theme.SobreRosa

/** Fondo Cerlita: degradado vertical oscuro con corazones suaves. */
@Composable
fun CerlitaFondo(modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A1421), FondoOscuro, Color(0xFF150F19)),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            dibujarCorazon(
                centro = Offset(size.width * 0.12f, size.height * 0.10f),
                radio = size.minDimension * 0.055f,
                color = Color(0xFFFFB6C1).copy(alpha = 0.07f),
            )
            dibujarCorazon(
                centro = Offset(size.width * 0.88f, size.height * 0.16f),
                radio = size.minDimension * 0.07f,
                color = Color(0xFF9FB8D9).copy(alpha = 0.06f),
            )
            dibujarCorazon(
                centro = Offset(size.width * 0.80f, size.height * 0.82f),
                radio = size.minDimension * 0.045f,
                color = Color(0xFFC9B8F0).copy(alpha = 0.06f),
            )
            dibujarCorazon(
                centro = Offset(size.width * 0.07f, size.height * 0.72f),
                radio = size.minDimension * 0.038f,
                color = Color(0xFFFFB6C1).copy(alpha = 0.05f),
            )
        }
        contenido()
    }
}

/** Tarjeta redondeada estilo Cerlita. */
@Composable
fun CerlitaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = color,
            tonalElevation = 2.dp,
        ) {
            Column(contenido = contenido)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = color,
            tonalElevation = 2.dp,
        ) {
            Column(contenido = contenido)
        }
    }
}

/**
 * Botón principal de Cerlita: rosa, grande y con animación spring al presionar
 * (Material 3 Expressive: física visible, no curvas fijas).
 */
@Composable
fun CerlitaBoton(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icono: @Composable (() -> Unit)? = null,
    habilitado: Boolean = true,
    contenidoPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
) {
    val interaccion = remember { MutableInteractionSource() }
    val presionado by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (presionado) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "escala_boton",
    )
    Button(
        onClick = onClick,
        enabled = habilitado,
        interactionSource = interaccion,
        contentPadding = contenidoPadding,
        modifier = modifier.graphicsLayer {
            scaleX = escala
            scaleY = escala
        },
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = RosaCerdita,
            contentColor = SobreRosa,
        ),
    ) {
        if (icono != null) {
            Box(modifier = Modifier.size(24.dp)) { icono() }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = texto, style = MaterialTheme.typography.labelLarge)
    }
}

/** Chip redondito con tinte pastel para temas y estados. */
@Composable
fun CerlitaChip(
    texto: String,
    modifier: Modifier = Modifier,
    tono: Color = RosaCerdita,
) {
    Text(
        text = texto,
        color = tono,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(tono.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
