package com.cerlita.core.ai

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenizadorWordPieceTest {

    /** Vocabulario en miniatura: el índice de cada línea es su id. */
    private val piezas = listOf(
        "[PAD]", // 0
        "[UNK]", // 1
        "[CLS]", // 2
        "[SEP]", // 3
        "hola", // 4
        "mundo", // 5
        "cerlita", // 6
        "pareja", // 7
        ",", // 8
        ".", // 9
        "¿", // 10
        "?", // 11
        "##s", // 12
        "##ita", // 13
    )

    private fun metadatos(minusculas: Boolean = false, maxLen: Int = 32) = MetadatosModelo(
        formato = 1,
        nombre = "prueba",
        version = 1,
        dimensiones = 8,
        tokenizador = "wordpiece",
        maxLen = maxLen,
        minusculas = minusculas,
    )

    @Test
    fun `frase simple produce ids y mascara correctos`() {
        val tokenizador = TokenizadorWordPiece.desdePiezas(piezas, metadatos())
        val c = tokenizador.codificar("hola mundo")
        assertArrayEquals(longArrayOf(2, 4, 5, 3), c.ids)
        assertArrayEquals(longArrayOf(1, 1, 1, 1), c.mascara)
    }

    @Test
    fun `piezas de continuacion se ensamblan`() {
        val tokenizador = TokenizadorWordPiece.desdePiezas(piezas, metadatos())
        // "cerlitas" no está en vocab, pero "cerlita" + "##s" sí
        val c = tokenizador.codificar("cerlitas")
        assertArrayEquals(longArrayOf(2, 6, 12, 3), c.ids)
    }

    @Test
    fun `palabra fuera de vocabulario produce UNK`() {
        val tokenizador = TokenizadorWordPiece.desdePiezas(piezas, metadatos())
        val c = tokenizador.codificar("zorromistico")
        assertArrayEquals(longArrayOf(2, 1, 3), c.ids)
    }

    @Test
    fun `puntuacion se aisla como token propio`() {
        val tokenizador = TokenizadorWordPiece.desdePiezas(piezas, metadatos())
        val c = tokenizador.codificar("hola, mundo.")
        assertArrayEquals(longArrayOf(2, 4, 8, 5, 9, 3), c.ids)
    }

    @Test
    fun `signos de interrogacion espanoles se aislan`() {
        val tokenizador = TokenizadorWordPiece.desdePiezas(piezas, metadatos())
        val c = tokenizador.codificar("¿hola?")
        assertArrayEquals(longArrayOf(2, 10, 4, 11, 3), c.ids)
    }

    @Test
    fun `truncado conserva SEP final`() {
        val tokenizador = TokenizadorWordPiece.desdePiezas(piezas, metadatos(maxLen = 4))
        val c = tokenizador.codificar("hola mundo cerlita pareja")
        // [CLS] hola mundo [SEP] — el [SEP] nunca se pierde al recortar
        assertArrayEquals(longArrayOf(2, 4, 5, 3), c.ids)
    }

    @Test
    fun `minusculas configurables desde metadatos`() {
        val tokenizador = TokenizadorWordPiece.desdePiezas(piezas, metadatos(minusculas = true))
        assertArrayEquals(longArrayOf(2, 4, 3), tokenizador.codificar("HOLA").ids)

        // Sin minúsculas (BETO cased): "HOLA" es OOV
        val cased = TokenizadorWordPiece.desdePiezas(piezas, metadatos(minusculas = false))
        assertArrayEquals(longArrayOf(2, 1, 3), cased.codificar("HOLA").ids)
    }

    @Test
    fun `longitud dinamica sin relleno`() {
        val tokenizador = TokenizadorWordPiece.desdePiezas(piezas, metadatos(maxLen = 128))
        val c = tokenizador.codificar("hola")
        assertEquals(3, c.ids.size) // [CLS] hola [SEP]
    }
}
