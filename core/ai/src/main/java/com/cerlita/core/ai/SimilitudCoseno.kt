package com.cerlita.core.ai

import kotlin.math.sqrt

/**
 * Similitud del coseno para embeddings L2-normalizados.
 * Umbral de diseño: > 0.75 = "muy en común" entre respuestas de la pareja.
 */
object SimilitudCoseno {

    fun calcular(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "Vectores de distinta dimensión: ${a.size} vs ${b.size}" }
        var producto = 0.0
        var normaA = 0.0
        var normaB = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            producto += x * y
            normaA += x * x
            normaB += y * y
        }
        if (normaA == 0.0 || normaB == 0.0) return 0.0
        return producto / (sqrt(normaA) * sqrt(normaB))
    }

    fun sonMuySimilares(a: FloatArray, b: FloatArray, umbral: Double = 0.75): Boolean =
        calcular(a, b) > umbral
}
