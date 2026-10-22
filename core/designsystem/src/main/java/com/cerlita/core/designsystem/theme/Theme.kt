package com.cerlita.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CerlitaFormas = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Cerlita es oscura por diseño: fondo #121212, acentos pastel cálidos.
 * El modo claro se ofrece como opción futura; por ahora fijamos oscuro
 * para que la marca sea consistente en todos los dispositivos.
 */
private fun esquemaCerlita() = darkColorScheme(
    primary = RosaCerdita,
    onPrimary = SobreRosa,
    primaryContainer = Color(0xFF5C2E38),
    onPrimaryContainer = RosaCerditaSuave,
    secondary = AzulKoala,
    onSecondary = SobreAzul,
    secondaryContainer = Color(0xFF2C3A50),
    onSecondaryContainer = AzulKoalaSuave,
    tertiary = Lavanda,
    onTertiary = Color(0xFF2A2240),
    tertiaryContainer = Color(0xFF3A2F55),
    onTertiaryContainer = Color(0xFFE4DCF5),
    background = FondoOscuro,
    onBackground = TextoClaro,
    surface = SuperficieOscuro,
    onSurface = TextoClaro,
    surfaceVariant = SuperficieElevada,
    onSurfaceVariant = TextoSuave,
    surfaceContainer = SuperficieOscuro,
    surfaceContainerHigh = SuperficieElevada,
    surfaceContainerHighest = Color(0xFF332C40),
    outline = BordeSuave,
    outlineVariant = Color(0xFF2B2635),
    error = ErrorSuave,
    onError = Color(0xFF4A1B14),
    errorContainer = Color(0xFF4A2320),
    onErrorContainer = Color(0xFFFFD9D3),
)

@Composable
fun CerlitaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = esquemaCerlita(),
        typography = CerlitaTipografia,
        shapes = CerlitaFormas,
        content = content,
    )
}
