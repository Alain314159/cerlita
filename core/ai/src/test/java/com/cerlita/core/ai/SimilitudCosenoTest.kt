package com.cerlita.core.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class SimilitudCosenoTest {

    @Test
    fun `vectores identicos dan similitud 1`() {
        val a = floatArrayOf(1f, 0f, 0.5f)
        assertEquals(1.0, SimilitudCoseno.calcular(a, a.copyOf()), 1e-9)
    }

    @Test
    fun `vectores ortogonales dan similitud 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0.0, SimilitudCoseno.calcular(a, b), 1e-9)
    }

    @Test
    fun `similitud conocida de dos vectores`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(3f, 2f, 1f)
        // cos = (3+4+3) / (sqrt(14) * sqrt(14)) = 10/14
        assertEquals(10.0 / 14.0, SimilitudCoseno.calcular(a, b), 1e-9)
    }

    @Test
    fun `vector nulo devuelve 0 y no lanza excepcion`() {
        val a = floatArrayOf(0f, 0f)
        val b = floatArrayOf(1f, 1f)
        assertEquals(0.0, SimilitudCoseno.calcular(a, b), 1e-9)
    }

    @Test
    fun `umbral de muy similar funciona`() {
        val a = floatArrayOf(1f, 1f, 1f)
        val b = floatArrayOf(0.9f, 1.0f, 1.1f)
        assert(SimilitudCoseno.sonMuySimilares(a, b))
        assert(!SimilitudCoseno.sonMuySimilares(a, floatArrayOf(1f, 0f, 0f)))
    }
}
