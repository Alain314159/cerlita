package com.cerlita.core.ai

/**
 * Contrato del motor de embeddings local (IA en el dispositivo).
 *
 * ## Decisión técnica (octubre 2026)
 * Se descartó `all-MiniLM-L6-v2` porque es un modelo **solo inglés** y Cerlita
 * es una app en español. La alternativa elegida es **EmbeddingGemma-300m**
 * (Google DeepMind, Apache 2.0):
 *  - Mejor modelo multilingüe abierto <500M en MTEB (100+ idiomas).
 *  - 308M parámetros, salida 768d truncable a 128d vía Matryoshka (MRL),
 *    lo que reduce 6x el almacenamiento de vectores.
 *  - Cuantizado INT8 funciona en <200MB de RAM; compatible con ONNX Runtime
 *    (com.microsoft.onnxruntime:onnxruntime-android) y con LiteRT/MediaPipe.
 *  - Plan de integración: descarga bajo demanda en la Fase 3 (no se empaqueta
 *    en el APK para mantener la descarga inicial ligera).
 *
 * Alternativa ligera si hiciera falta un bundle mínimo:
 * `intfloat/multilingual-e5-small` (118M, 384d, ~30MB INT8, 100 idiomas).
 */
interface EmbeddingEngine {
    val dimensiones: Int

    /** Carga el modelo (una vez por proceso). Devuelve false si falló. */
    suspend fun inicializar(): Boolean

    /** Genera el embedding (L2-normalizado) de un texto corto. */
    suspend fun embed(texto: String): FloatArray
}
