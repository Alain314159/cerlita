package com.cerlita.core.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manifiesto del paquete de modelo (archivo `metadatos.json` dentro del zip).
 *
 * ## Formato del zip "modelo Cerlita" v1
 *
 * ```
 * cerlita-modelo-ia-v1.zip
 * ├── metadatos.json    ← este manifiesto (obligatorio)
 * ├── modelo.onnx       ← pesos ONNX cuantizado (nombre declarado abajo)
 * └── vocab.txt         ← tokenizador WordPiece (nombre declarado abajo)
 * ```
 *
 * El zip se publica como adjunto del release `modelo-ia-v1` del repositorio
 * (o se importa a mano desde el almacenamiento del teléfono). La app valida
 * el manifiesto antes de instalar: así el mismo mecanismo sirve para BETO,
 * e5, MiniLM… y para futuras versiones del formato.
 *
 * Los zips se generan con `scripts/empaquetar_modelo.py`.
 */
@Serializable
data class MetadatosModelo(
    val formato: Int,
    val nombre: String,
    val version: Int,
    val dimensiones: Int,
    /** Por ahora solo "wordpiece" (BERT/BETO/e5/MiniLM). */
    val tokenizador: String = "wordpiece",
    @SerialName("archivoModelo") val archivoModelo: String = "modelo.onnx",
    @SerialName("archivoTokenizador") val archivoTokenizador: String = "vocab.txt",
    val maxLen: Int = 128,
    val normalizar: Boolean = true,
    /** Pooling de la salida: "media" (estándar sentence-transformers) o "cls". */
    val pooling: String = "media",
    val minusculas: Boolean = false,
    val quitarAcentos: Boolean = false,
    /** Prefijo de entrada: "ninguna" o "e5" (antepone "query: "). */
    val plantilla: String = "ninguna",
) {
    companion object {
        const val FORMATO_ACTUAL = 1
        const val NOMBRE_ARCHIVO = "metadatos.json"

        private val json = Json { ignoreUnknownKeys = true }

        fun desdeArchivo(archivo: File): Result<MetadatosModelo> = runCatching {
            if (!archivo.isFile) error("no existe ${archivo.name}")
            json.decodeFromString(serializer(), archivo.readText())
        }
    }
}
