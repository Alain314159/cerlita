package com.cerlita.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cerlita.core.designsystem.theme.AzulKoala
import com.cerlita.core.designsystem.theme.RosaCerdita

/**
 * Mascotas de Cerlita dibujadas 100% con Canvas: sin assets, sin descargas,
 * escalables y con estilo kawaii. Sistema interno de coordenadas 100x100.
 */

private val Tinta = Color(0xFF3A2E33)
private val RosaRubor = Color(0xFFFF9FB0)

/** Cara de la cerdita. */
@Composable
fun PigFace(modifier: Modifier = Modifier, tamano: Dp = 72.dp) {
    Canvas(modifier.size(tamano)) {
        val s = size.minDimension / 100f
        val rosa = RosaCerdita
        val rosaHocico = Color(0xFFF59FAD)
        val rosaOrejas = Color(0xFFE98CA1)

        // Orejas (detrás de la cabeza)
        drawOrejas(s, color = rosaOrejas, radio = 15f, centroY = 24f)
        // Cabeza
        drawCircle(rosa, radius = 37f * s, center = Offset(50f * s, 55f * s))
        // Hocico
        drawCircle(rosaHocico, radius = 13f * s, center = Offset(50f * s, 63f * s))
        drawCircle(Tinta, radius = 2.4f * s, center = Offset(45f * s, 63f * s))
        drawCircle(Tinta, radius = 2.4f * s, center = Offset(55f * s, 63f * s))
        // Ojitos felices
        drawOjosFelices(s, centroY = 46f)
        // Rubor
        drawRubor(s, color = RosaRubor.copy(alpha = 0.55f), centroY = 62f)
    }
}

/** Cara del koala. */
@Composable
fun KoalaFace(modifier: Modifier = Modifier, tamano: Dp = 72.dp) {
    Canvas(modifier.size(tamano)) {
        val s = size.minDimension / 100f
        val grisAzul = AzulKoala
        val grisAzulOscuro = Color(0xFF8CA3C4)
        val grisAzulClaro = Color(0xFFD3E0F2)

        // Orejas grandes de koala
        drawCircle(grisAzulOscuro, radius = 19f * s, center = Offset(16f * s, 28f * s))
        drawCircle(grisAzulOscuro, radius = 19f * s, center = Offset(84f * s, 28f * s))
        drawCircle(grisAzulClaro, radius = 10f * s, center = Offset(16f * s, 28f * s))
        drawCircle(grisAzulClaro, radius = 10f * s, center = Offset(84f * s, 28f * s))
        // Cabeza
        drawCircle(grisAzul, radius = 36f * s, center = Offset(50f * s, 55f * s))
        // Nariz alargada de koala
        drawOval(
            color = Color(0xFF46536B),
            topLeft = Offset(44.5f * s, 55f * s),
            size = Size(11f * s, 16f * s),
        )
        // Ojitos felices
        drawOjosFelices(s, centroY = 46f, separacion = 14f)
        // Rubor rosado (contraste tierno con el pelaje azul)
        drawRubor(s, color = RosaRubor.copy(alpha = 0.50f), centroY = 64f)
    }
}

private fun DrawScope.drawOrejas(s: Float, color: Color, radio: Float, centroY: Float) {
    drawCircle(color, radius = radio * s, center = Offset(24f * s, centroY * s))
    drawCircle(color, radius = radio * s, center = Offset(76f * s, centroY * s))
}

private fun DrawScope.drawOjosFelices(s: Float, centroY: Float, separacion: Float = 16f) {
    val radio = 7f
    listOf(50f - separacion, 50f + separacion).forEach { x ->
        drawArc(
            color = Tinta,
            startAngle = 195f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset((x - radio) * s, (centroY - radio) * s),
            size = Size(radio * 2 * s, radio * 2 * s),
            style = Stroke(width = 3.4f * s, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawRubor(s: Float, color: Color, centroY: Float) {
    drawCircle(color, radius = 6f * s, center = Offset(22f * s, centroY * s))
    drawCircle(color, radius = 6f * s, center = Offset(78f * s, centroY * s))
}

/** Corazón geométrico: dos círculos + triángulo. Para decorar fondos. */
fun DrawScope.dibujarCorazon(centro: Offset, radio: Float, color: Color) {
    drawCircle(color, radius = radio * 0.5f, center = centro + Offset(-radio * 0.26f, -radio * 0.18f))
    drawCircle(color, radius = radio * 0.5f, center = centro + Offset(radio * 0.26f, -radio * 0.18f))
    val mitad = radio * 0.72f
    val path = Path().apply {
        moveTo(centro.x - mitad, centro.y - radio * 0.08f)
        lineTo(centro.x + mitad, centro.y - radio * 0.08f)
        lineTo(centro.x, centro.y + radio * 0.9f)
        close()
    }
    drawPath(path, color)
}
