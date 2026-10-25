package com.cerlita.domain.juego

import com.cerlita.domain.model.Animal
import com.cerlita.domain.model.ModoJuego
import com.cerlita.domain.model.TipoPregunta
import com.cerlita.domain.model.Veredicto
import kotlin.math.abs

/**
 * Fases de una pregunta dentro de la ronda (en LOCAL se recorren en un
 * mismo móvil pasándoselo; en PAREJA cada teléfono vive solo su respuesta
 * y la revelación llega con el resumen).
 */
enum class FasePregunta { TURNO_1, TURNO_2, REVELACION }

/**
 * Reglas puras de Cerlita: cómo se planifica una ronda, quién escribe en
 * cada turno y cómo se comparan dos respuestas. Sin IA, sin Android:
 * todo lo que está aquí es determinista y testeable.
 *
 * El "motor de comparación" es la pareja misma: los tipos cerrados se
 * comparan por igualdad (y tolerancia ±1 en la escala) y el texto libre
 * lo juzgan ellos con tres botones, porque ningún modelo conoce sus
 * ironías ni sus apodos. La detección de preguntas duplicadas del
 * catálogo, en cambio, vive en GitHub Actions (ver scripts/dedup_preguntas.py).
 */
object ReglasJuego {

    /**
     * Modo efectivo de cada pregunta de la ronda. En MIXTO alterna pregunta
     * a pregunta para que nadie se quede siempre adivinando. Determinista:
     * ambos teléfonos calculan lo mismo sin necesidad de comunicarse.
     */
    fun planificarModos(modoJuego: ModoJuego, cantidad: Int): List<ModoJuego> =
        when (modoJuego) {
            ModoJuego.MIXTO -> List(cantidad) { i ->
                if (i % 2 == 0) ModoJuego.AMBOS_RESPONDEN else ModoJuego.ADIVINA
            }
            else -> List(cantidad) { modoJuego }
        }

    /**
     * Quién escribe en el turno [turno] (0 o 1) de la pregunta número
     * [indice] de la ronda.
     *
     *  - AMBOS_RESPONDEN: cada quien responde por su cuenta; alterna quién
     *    abre para que no siempre empiece el mismo.
     *  - ADIVINA: en el turno 1 escribe quien ADIVINA; en el turno 2
     *    responde la verdad quien es adivinado. La cerdita adivina las
     *    preguntas pares y el koala las impares: regla compartida que
     *    ambos teléfonos deducen igual sin hablar.
     */
    fun autorDeTurno(modoPregunta: ModoJuego, indice: Int, turno: Int): Animal {
        val primero = if (indice % 2 == 0) Animal.CERDITA else Animal.KOALA
        val segundo = if (primero == Animal.CERDITA) Animal.KOALA else Animal.CERDITA
        return if (turno <= 0) primero else segundo
    }

    /**
     * Fase de la pregunta actual a partir de las respuestas ya guardadas
     * (llave: autor). Como Room es la fuente de verdad, esto también
     * recupera la partida tras matar el proceso o desconectar el P2P.
     */
    fun inferirFase(respuestasPorAutor: Map<Animal, *>): FasePregunta = when {
        respuestasPorAutor.isEmpty() -> FasePregunta.TURNO_1
        respuestasPorAutor.size == 1 -> FasePregunta.TURNO_2
        else -> FasePregunta.REVELACION
    }

    /**
     * Veredicto automático para tipos cerrados; null cuando el juicio
     * corresponde a un humano (texto libre).
     *
     *  - SI_NO / OPCIONES: igualdad exacta.
     *  - ESCALA (1..5): igual = AFIRMATIVO, diferencia 1 = INTERMEDIO,
     *    dos o más = NEGATIVO.
     */
    fun veredictoAutomatico(tipo: TipoPregunta, valor1: String, valor2: String): Veredicto? {
        val a = valor1.trim()
        val b = valor2.trim()
        return when (tipo) {
            TipoPregunta.SI_NO,
            TipoPregunta.OPCIONES,
            -> if (a.equals(b, ignoreCase = true)) Veredicto.AFIRMATIVO else Veredicto.NEGATIVO

            TipoPregunta.ESCALA -> {
                val na = a.toIntOrNull()
                val nb = b.toIntOrNull()
                if (na == null || nb == null) {
                    null // valor corrupto: que lo juzgue un humano
                } else {
                    when (abs(na - nb)) {
                        0 -> Veredicto.AFIRMATIVO
                        1 -> Veredicto.INTERMEDIO
                        else -> Veredicto.NEGATIVO
                    }
                }
            }

            TipoPregunta.TEXTO_LIBRE -> null // juicio humano: Sí / Parecido / No
        }
    }
}
