package com.cerlita.domain.juego

import com.cerlita.domain.model.Animal
import com.cerlita.domain.model.ModoJuego
import com.cerlita.domain.model.TipoPregunta
import com.cerlita.domain.model.Veredicto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pruebas de las reglas puras: planificación, turnos y veredictos. */
class ReglasJuegoTest {

    // -----------------------------------------------------------------
    // planificarModos
    // -----------------------------------------------------------------

    @Test
    fun `modo fijo produce la misma modalidad en todas las preguntas`() {
        val modos = ReglasJuego.planificarModos(ModoJuego.ADIVINA, 5)

        assertEquals(List(5) { ModoJuego.ADIVINA }, modos)
    }

    @Test
    fun `mixto alterna ambos y adivina pregunta a pregunta`() {
        val modos = ReglasJuego.planificarModos(ModoJuego.MIXTO, 6)

        assertEquals(
            listOf(
                ModoJuego.AMBOS_RESPONDEN,
                ModoJuego.ADIVINA,
                ModoJuego.AMBOS_RESPONDEN,
                ModoJuego.ADIVINA,
                ModoJuego.AMBOS_RESPONDEN,
                ModoJuego.ADIVINA,
            ),
            modos,
        )
    }

    // -----------------------------------------------------------------
    // autorDeTurno
    // -----------------------------------------------------------------

    @Test
    fun `la cerdita abre las preguntas pares y el koala las impares`() {
        assertEquals(Animal.CERDITA, ReglasJuego.autorDeTurno(ModoJuego.AMBOS_RESPONDEN, 0, 0))
        assertEquals(Animal.KOALA, ReglasJuego.autorDeTurno(ModoJuego.AMBOS_RESPONDEN, 1, 0))
        assertEquals(Animal.CERDITA, ReglasJuego.autorDeTurno(ModoJuego.AMBOS_RESPONDEN, 4, 0))
    }

    @Test
    fun `el segundo turno siempre es el otro animal`() {
        assertEquals(Animal.KOALA, ReglasJuego.autorDeTurno(ModoJuego.ADIVINA, 0, 1))
        assertEquals(Animal.CERDITA, ReglasJuego.autorDeTurno(ModoJuego.ADIVINA, 1, 1))
    }

    // -----------------------------------------------------------------
    // inferirFase
    // -----------------------------------------------------------------

    @Test
    fun `sin respuestas es turno uno, con una es turno dos y con dos revelacion`() {
        assertEquals(FasePregunta.TURNO_1, ReglasJuego.inferirFase(emptyMap<Animal, String>()))
        assertEquals(
            FasePregunta.TURNO_2,
            ReglasJuego.inferirFase(mapOf(Animal.CERDITA to "Sí")),
        )
        assertEquals(
            FasePregunta.REVELACION,
            ReglasJuego.inferirFase(mapOf(Animal.CERDITA to "Sí", Animal.KOALA to "No")),
        )
    }

    // -----------------------------------------------------------------
    // veredictoAutomatico
    // -----------------------------------------------------------------

    @Test
    fun `si-no compara por igualdad exacta`() {
        assertEquals(
            Veredicto.AFIRMATIVO,
            ReglasJuego.veredictoAutomatico(TipoPregunta.SI_NO, "Sí", "Sí"),
        )
        assertEquals(
            Veredicto.NEGATIVO,
            ReglasJuego.veredictoAutomatico(TipoPregunta.SI_NO, "Sí", "No"),
        )
        // Tolerancia de mayúsculas y espacios, no de contenido
        assertEquals(
            Veredicto.AFIRMATIVO,
            ReglasJuego.veredictoAutomatico(TipoPregunta.SI_NO, "sí ", "Sí"),
        )
    }

    @Test
    fun `opciones compara por igualdad exacta`() {
        assertEquals(
            Veredicto.AFIRMATIVO,
            ReglasJuego.veredictoAutomatico(TipoPregunta.OPCIONES, "Playa", "Playa"),
        )
        assertEquals(
            Veredicto.NEGATIVO,
            ReglasJuego.veredictoAutomatico(TipoPregunta.OPCIONES, "Playa", "Montaña"),
        )
    }

    @Test
    fun `escala tolera una unidad de diferencia`() {
        assertEquals(
            Veredicto.AFIRMATIVO,
            ReglasJuego.veredictoAutomatico(TipoPregunta.ESCALA, "5", "5"),
        )
        assertEquals(
            Veredicto.INTERMEDIO,
            ReglasJuego.veredictoAutomatico(TipoPregunta.ESCALA, "4", "5"),
        )
        assertEquals(
            Veredicto.NEGATIVO,
            ReglasJuego.veredictoAutomatico(TipoPregunta.ESCALA, "2", "5"),
        )
    }

    @Test
    fun `escala corrupta pasa el juicio al humano`() {
        assertNull(ReglasJuego.veredictoAutomatico(TipoPregunta.ESCALA, "mucho", "5"))
        assertNull(ReglasJuego.veredictoAutomatico(TipoPregunta.ESCALA, "", ""))
    }

    @Test
    fun `texto libre siempre lo juzga la pareja`() {
        assertNull(
            ReglasJuego.veredictoAutomatico(TipoPregunta.TEXTO_LIBRE, "pizza", "pizza"),
        )
        assertNull(
            ReglasJuego.veredictoAutomatico(TipoPregunta.TEXTO_LIBRE, "pizza", "sushi"),
        )
    }
}
