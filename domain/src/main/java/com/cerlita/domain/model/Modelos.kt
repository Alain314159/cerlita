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

/** Modo de juego de una ronda. */
enum class ModoRonda { LOCAL, PAREJA }

/** Estado de una ronda. */
enum class EstadoRonda { EN_CURSO, FINALIZADA }

/** Ronda de juego: una secuencia de preguntas respondidas por los dos. */
data class Ronda(
    val id: String,
    val modo: ModoRonda,
    val estado: EstadoRonda,
    val preguntaIds: List<String>,
    val indiceActual: Int = 0,
    val creada: Long = System.currentTimeMillis(),
    val finalizada: Long? = null,
) {
    companion object {
        /** Longitud estándar de una ronda, en LOCAL y en PAREJA. */
        const val PREGUNTAS_POR_RONDA = 10
    }
}

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
