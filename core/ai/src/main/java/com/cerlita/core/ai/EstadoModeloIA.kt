package com.cerlita.core.ai

/**
 * Ciclo de vida del modelo de IA local, expuesto como StateFlow para la UI
 * (tarjeta "Modelo de IA" en Ajustes).
 */
sealed interface EstadoModeloIA {

    /** Sin modelo: la app funciona con normalidad, solo sin detección de duplicados. */
    data object NoInstalado : EstadoModeloIA

    /** Descargando el zip desde GitHub con progreso (0..1). */
    data class Descargando(
        val progreso: Float,
        val mbTotales: Float,
        val mbDescargados: Float,
    ) : EstadoModeloIA

    /** Copiando el zip elegido desde el almacenamiento del teléfono (selector SAF). */
    data object Importando : EstadoModeloIA

    /** Validando y extrayendo el zip a almacenamiento interno. */
    data object Instalando : EstadoModeloIA

    /** Modelo usable por [MotorEmbeddingOnnx]. */
    data class Instalado(
        val metadatos: MetadatosModelo,
        val tamanoMb: Float,
    ) : EstadoModeloIA

    data class Error(val mensaje: String) : EstadoModeloIA
}
