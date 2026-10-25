package com.cerlita.domain.model

/**
 * Animal que representa a cada miembro de la pareja dentro del juego.
 * Cada teléfono es un animal; los vectores y respuestas se asocian a él.
 */
enum class Animal { CERDITA, KOALA }

/** Tipos de pregunta soportados por el motor de juego. */
enum class TipoPregunta { SI_NO, ESCALA, OPCIONES, TEXTO_LIBRE }

/** Origen de una pregunta dentro del catálogo. */
enum class OrigenPregunta { OFICIAL, CUSTOM }

/** Pregunta del catálogo. */
data class Pregunta(
    val id: String,
    val tema: String,
    val tipo: TipoPregunta,
    val texto: String,
    val opciones: List<String> = emptyList(),
    val origen: OrigenPregunta = OrigenPregunta.OFICIAL,
    val activa: Boolean = true,
) {
    val esValida: Boolean
        get() = texto.length in MIN_TEXTO..MAX_TEXTO &&
            (tipo != TipoPregunta.OPCIONES || opciones.size in 2..MAX_OPCIONES)

    companion object {
        const val MIN_TEXTO = 8
        const val MAX_TEXTO = 220
        const val MAX_OPCIONES = 4
    }
}

/** Respuesta de un jugador a una pregunta, dentro de una ronda. */
data class Respuesta(
    val id: Long = 0L,
    val preguntaId: String,
    val sesionId: String,
    val autor: Animal,
    val valor: String,
    val fecha: Long = System.currentTimeMillis(),
)

/** Transporte de la ronda: un móvil compartido (LOCAL) o dos móviles P2P (PAREJA). */
enum class ModoRonda { LOCAL, PAREJA }

/**
 * Modo de juego de la ronda. La comparación la emiten los propios jugadores:
 * los tipos cerrados (sí/no, escala, opciones) se comparan solos y el texto
 * libre lo juzga la pareja con botones de tres opciones. Sin IA en el teléfono.
 */
enum class ModoJuego { AMBOS_RESPONDEN, ADIVINA, MIXTO }

/**
 * Juicio sobre una revelación:
 *  - AMBOS_RESPONDEN: ¿se parecen las dos respuestas? (Sí / Parecido / No)
 *  - ADIVINA: ¿acertó la adivinanza? (Acertó / Más o menos / Falló)
 * En tipos cerrados lo calcula ReglasJuego; en texto libre lo emite un humano.
 */
enum class Veredicto { AFIRMATIVO, INTERMEDIO, NEGATIVO }

/** Estado de una ronda. */
enum class EstadoRonda { EN_CURSO, FINALIZADA }

/** Ronda de juego: una secuencia de preguntas respondidas por los dos. */
data class Ronda(
    val id: String,
    val modo: ModoRonda,
    val modoJuego: ModoJuego,
    val estado: EstadoRonda,
    val preguntaIds: List<String>,
    /** Modo efectivo de cada pregunta (paralelo a [preguntaIds]; en MIXTO alterna). */
    val modosPorPregunta: List<ModoJuego> = emptyList(),
    val indiceActual: Int = 0,
    val creada: Long = System.currentTimeMillis(),
    val finalizada: Long? = null,
) {
    /** Modo efectivo de la pregunta en [indice] (tolera rondas migradas sin lista). */
    fun modoDe(indice: Int): ModoJuego =
        modosPorPregunta.getOrNull(indice) ?: modoJuego

    companion object {
        /** Longitud estándar de una ronda, en LOCAL y en PAREJA. */
        const val PREGUNTAS_POR_RONDA = 10
    }
}

/**
 * Veredicto emitido sobre una pregunta de la ronda: automático (tipos
 * cerrados) o humano (texto libre). En modo PAREJA cada teléfono emite el
 * suyo: es un juego cooperativo y el momento de juzgar lo viven juntos.
 */
data class Evaluacion(
    val id: Long = 0L,
    val sesionId: String,
    val preguntaId: String,
    val modoPregunta: ModoJuego,
    val veredicto: Veredicto,
    val automatico: Boolean,
    val fecha: Long = System.currentTimeMillis(),
)

/** Perfil local: quién soy en la pareja (nombre + animal) y cómo se llama el otro. */
data class Perfil(
    val nombrePropio: String = "",
    val animal: Animal = Animal.CERDITA,
    val nombrePareja: String = "",
) {
    val estaCompleto: Boolean get() = nombrePropio.isNotBlank() && nombrePareja.isNotBlank()
}

/** Resultado de descargar el catálogo remoto. */
data class CatalogoPreguntas(
    val version: String,
    val actualizadoEn: Long,
    val preguntas: List<Pregunta>,
)
