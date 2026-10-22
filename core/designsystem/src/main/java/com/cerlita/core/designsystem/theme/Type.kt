package com.cerlita.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cerlita.core.designsystem.R

/**
 * Fuentes variables (Nunito y Quicksand, OFL). Se selecciona el peso por
 * eje de variación: una sola TTF cubre todos los pesos y mantiene el APK
 * pequeño. Requiere API 26+ (minSdk de Cerlita).
 */
private fun fuenteVariable(resId: Int, peso: Int): Font = Font(
    resId = resId,
    weight = FontWeight(peso),
    variationSettings = FontVariation.Settings(FontVariation.weight(peso)),
)

val Nunito = FontFamily(
    fuenteVariable(R.font.nunito_variable, 400),
    fuenteVariable(R.font.nunito_variable, 500),
    fuenteVariable(R.font.nunito_variable, 600),
    fuenteVariable(R.font.nunito_variable, 700),
    fuenteVariable(R.font.nunito_variable, 800),
)

val Quicksand = FontFamily(
    fuenteVariable(R.font.quicksand_variable, 500),
    fuenteVariable(R.font.quicksand_variable, 600),
    fuenteVariable(R.font.quicksand_variable, 700),
)

/**
 * Tipografía Cerlita: títulos redonditos en Quicksand, cuerpo amable en Nunito.
 */
val CerlitaTipografia = Typography(
    displayLarge = TextStyle(
        fontFamily = Quicksand,
        fontWeight = FontWeight(700),
        fontSize = 52.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.5).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Quicksand,
        fontWeight = FontWeight(700),
        fontSize = 36.sp,
        lineHeight = 42.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Quicksand,
        fontWeight = FontWeight(700),
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Quicksand,
        fontWeight = FontWeight(600),
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Quicksand,
        fontWeight = FontWeight(700),
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight(700),
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight(500),
        fontSize = 17.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight(500),
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight(500),
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight(800),
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight(700),
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight(700),
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp,
    ),
)
