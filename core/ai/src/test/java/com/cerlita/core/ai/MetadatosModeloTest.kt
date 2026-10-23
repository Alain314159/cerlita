package com.cerlita.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MetadatosModeloTest {

    @Test
    fun `parsea json con campos por defecto e ignora desconocidos`() {
        val json = """
            {
              "formato": 1,
              "nombre": "sentence_similarity_spanish_es",
              "version": 1,
              "dimensiones": 768,
              "tokenizador": "wordpiece",
              "campoDelFuturo": "x"
            }
        """.trimIndent()
        val archivo = File.createTempFile("metadatos", ".json").apply {
            writeText(json)
            deleteOnExit()
        }
        val meta = MetadatosModelo.desdeArchivo(archivo).getOrThrow()
        assertEquals(1, meta.formato)
        assertEquals(768, meta.dimensiones)
        assertEquals("modelo.onnx", meta.archivoModelo)
        assertEquals("vocab.txt", meta.archivoTokenizador)
        assertEquals(128, meta.maxLen)
        assertEquals("media", meta.pooling)
    }

    @Test
    fun `falla limpio si falta archivo`() {
        val resultado = MetadatosModelo.desdeArchivo(File("/no/existe/metadatos.json"))
        assertTrue(resultado.isFailure)
    }
}
