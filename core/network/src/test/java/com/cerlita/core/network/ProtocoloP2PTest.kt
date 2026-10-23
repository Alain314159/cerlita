package com.cerlita.core.network

import com.cerlita.domain.model.Animal
import com.cerlita.domain.model.InfoJugador
import com.cerlita.domain.model.MensajePareja
import com.cerlita.domain.model.OrigenPregunta
import com.cerlita.domain.model.Pregunta
import com.cerlita.domain.model.TipoPregunta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del protocolo de bytes P2P (codificar -> decodificar). */
class ProtocoloP2PTest {

    private val pregunta = Pregunta(
        id = "q_001",
        tema = "viajes",
        tipo = TipoPregunta.SI_NO,
        texto = "¿Alguna vez han hecho un viaje improvisado juntos?",
        opciones = emptyList(),
        origen = OrigenPregunta.OFICIAL,
    )

    @Test
    fun `saludo sobrevive al viaje de ida y vuelta`() {
        val original = MensajePareja.Saludo(InfoJugador("Ana", Animal.CERDITA))

        val recibido = ProtocoloP2P.decodificar(ProtocoloP2P.codificar(original))

        assertEquals(original, recibido)
    }

    @Test
    fun `inicio de ronda lleva preguntas completas`() {
        val custom = pregunta.copy(
            id = "custom_ab12",
            texto = "¿Cuál es nuestro ritual secreto de los domingos?",
            tipo = TipoPregunta.TEXTO_LIBRE,
            origen = OrigenPregunta.CUSTOM,
        )
        val original = MensajePareja.InicioRonda(
            rondaId = "ronda-xyz",
            preguntas = listOf(pregunta, custom),
        )

        val recibido = ProtocoloP2P.decodificar(ProtocoloP2P.codificar(original))

        assertEquals(original, recibido)
    }

    @Test
    fun `respuesta cruzada conserva autor y valor`() {
        val original = MensajePareja.RespuestaRecibida(
            rondaId = "ronda-xyz",
            preguntaId = "q_001",
            autor = Animal.KOALA,
            valor = "¡Sí! Aquella vez que perdimos el tren",
        )

        val recibido = ProtocoloP2P.decodificar(ProtocoloP2P.codificar(original))

        assertEquals(original, recibido)
    }

    @Test
    fun `fin de ronda es un aviso ligero`() {
        val original = MensajePareja.FinRonda("ronda-xyz")

        val recibido = ProtocoloP2P.decodificar(ProtocoloP2P.codificar(original))

        assertEquals(original, recibido)
    }

    @Test
    fun `bytes corruptos devuelven null en vez de explotar`() {
        assertNull(ProtocoloP2P.decodificar("esto no es json".encodeToByteArray()))
        assertNull(ProtocoloP2P.decodificar(ByteArray(0)))
        assertNull(ProtocoloP2P.decodificar("{\"tipo\":\"respuesta\"}".encodeToByteArray()))
    }

    @Test
    fun `tipo desconocido de una version futura se ignora`() {
        val futuro = "{\"tipo\":\"fotos\",\"datos\":{\"x\":1}}"

        assertNull(ProtocoloP2P.decodificar(futuro.encodeToByteArray()))
    }

    @Test
    fun `inicio de ronda sin preguntas validas se descarta`() {
        val roto = "{\"tipo\":\"inicio\",\"inicio\":{\"rondaId\":\"r\"," +
            "\"preguntas\":[{\"id\":\"p\",\"tema\":\"x\",\"tipo\":\"INEXISTENTE\",\"texto\":\"sin tipo\"}]}}"

        assertNull(ProtocoloP2P.decodificar(roto.encodeToByteArray()))
    }

    @Test
    fun `pregunta de opciones incompleta no se reconstruye`() {
        val dto = ProtocoloP2P.codificar(
            MensajePareja.InicioRonda(
                rondaId = "r",
                preguntas = listOf(
                    Pregunta(
                        id = "p",
                        tema = "x",
                        tipo = TipoPregunta.OPCIONES,
                        texto = "una de opciones con lista corta",
                        opciones = listOf("única"),
                    ),
                ),
            ),
        )

        // El codificador la empaqueta, pero el decodificador la filtra y el
        // mensaje completo se vuelve inútil (sin preguntas) -> null.
        assertNull(ProtocoloP2P.decodificar(dto))
        assertTrue(dto.isNotEmpty())
    }
}
