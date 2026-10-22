package com.cerlita.data

import com.cerlita.data.remote.CatalogoDTO
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogoDTOTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parsea un catalogo completo con updated_at`() {
        val texto = """
            {
              "version": "1.0.0",
              "updated_at": "2026-10-22T10:00:00Z",
              "preguntas": [
                {
                  "id": "q_001",
                  "tema": "viajes",
                  "tipo": "si_no",
                  "texto": "¿Alguna vez han hecho un viaje improvisado juntos?",
                  "opciones": ["Sí", "No"]
                }
              ]
            }
        """.trimIndent()

        val dto = json.decodeFromString(CatalogoDTO.serializer(), texto)

        assertEquals("1.0.0", dto.version)
        assertEquals("2026-10-22T10:00:00Z", dto.actualizadoEn)
        assertEquals(1, dto.preguntas.size)
        assertEquals("viajes", dto.preguntas[0].tema)
        assertEquals(listOf("Sí", "No"), dto.preguntas[0].opciones)
    }

    @Test
    fun `tolera campos desconocidos y omision de opciones`() {
        val texto = """
            {
              "version": "1.1.0",
              "updated_at": "2026-11-01T08:00:00Z",
              "autor": "alguien del futuro",
              "preguntas": [
                { "id": "q_100", "tema": "sueños", "tipo": "texto_libre", "texto": "¿Qué pequeño sueño tienen juntos que aún no cumplen?" }
              ]
            }
        """.trimIndent()

        val dto = json.decodeFromString(CatalogoDTO.serializer(), texto)

        assertEquals("1.1.0", dto.version)
        assertEquals(1, dto.preguntas.size)
        assertTrue(dto.preguntas[0].opciones.isEmpty())
    }

    @Test(expected = Exception::class)
    fun `falla limpiamente con json invalido`() {
        json.decodeFromString(CatalogoDTO.serializer(), "no soy json")
    }
}
