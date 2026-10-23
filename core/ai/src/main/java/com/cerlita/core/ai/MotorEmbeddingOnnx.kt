package com.cerlita.core.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import com.cerlita.core.common.DispatcherProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

/**
 * Implementación de [EmbeddingEngine] sobre ONNX Runtime, ejecutando el
 * modelo instalado por [GestorModeloIA] (el modelo NO se empaqueta en el APK).
 *
 * Estrategia de ejecutores en cascada, pensada para gama baja:
 *  1. **NNAPI** → delega en la unidad de aceleración del SoC (GPU/DSP/NPU)
 *  2. **XNNPACK** → CPU con kernels afinados para móviles
 *  3. **CPU genérico** → último recurso
 *
 * La sesión se crea una vez por proceso y es thread-safe. La primera
 * inferencia fuerza la compilación de kernels, así que [inicializar] hace
 * una pasada fantasma de calentamiento.
 */
class MotorEmbeddingOnnx(
    private val gestor: GestorModeloIA,
    private val dispatchers: DispatcherProvider,
) : EmbeddingEngine {

    private val entorno: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val candado = Mutex()

    private var sesion: OrtSession? = null
    private var tokenizador: TokenizadorWordPiece? = null
    private var metadatos: MetadatosModelo? = null

    override val dimensiones: Int
        get() = metadatos?.dimensiones ?: 0

    override suspend fun inicializar(): Boolean = candado.withLock {
        withContext(dispatchers.default) {
            if (sesion != null) return@withContext true
            val meta = gestor.metadatosInstalados() ?: return@withContext false
            try {
                val rutaModelo = File(gestor.directorioInstalacion, meta.archivoModelo).absolutePath
                sesion = crearSesion(rutaModelo)
                tokenizador = TokenizadorWordPiece.desdeArchivo(
                    File(gestor.directorioInstalacion, meta.archivoTokenizador),
                    meta,
                )
                metadatos = meta
                // Calentamiento fuera del primer uso real (NNAPI puede tardar
                // varios segundos en compilar los kernels del modelo).
                runCatching { inferir("calentamiento") }
                true
            } catch (_: Throwable) {
                sesion = null
                tokenizador = null
                metadatos = null
                false
            }
        }
    }

    override suspend fun embed(texto: String): FloatArray = withContext(dispatchers.default) {
        inferir(texto)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Inferencia
    // ─────────────────────────────────────────────────────────────────────

    private fun inferir(texto: String): FloatArray {
        val sesionLocal = sesion ?: error("El motor de IA no está inicializado (¿hay modelo instalado?)")
        val tokenizadorLocal = tokenizador ?: error("Tokenizador no inicializado")
        val meta = metadatos ?: error("Modelo sin metadatos")

        val codificacion = tokenizadorLocal.codificar(aplicarPlantilla(meta, texto))
        val ids = arrayOf(codificacion.ids)
        val mascara = arrayOf(codificacion.mascara)

        val tensores = ArrayList<Pair<String, OnnxTensor>>(3)
        try {
            for (nombre in sesionLocal.inputNames) {
                val tensor = when {
                    nombre.contains("input_ids", ignoreCase = true) ->
                        OnnxTensor.createTensor(entorno, ids)

                    nombre.contains("attention", ignoreCase = true) ->
                        OnnxTensor.createTensor(entorno, mascara)

                    nombre.contains("token_type", ignoreCase = true) ->
                        OnnxTensor.createTensor(entorno, Array(1) { LongArray(ids[0].size) })

                    else -> error("El modelo declara una entrada no soportada: '$nombre'")
                }
                tensores += nombre to tensor
            }

            sesionLocal.run(tensores.toMap()).use { resultado ->
                val salida = buscarSalidaEmbedding(resultado)
                val vector = vectorDesdeSalida(salida, codificacion.mascara)
                return if (meta.normalizar) normalizarL2(vector) else vector
            }
        } finally {
            tensores.forEach { it.second.close() }
        }
    }

    /**
     * Prefiere la salida tipo "sentence_embedding"/"pooler" si el export la
     * trae; si solo hay last_hidden_state (export feature-extraction de
     * optimum), se hace pooling en la app.
     */
    private fun buscarSalidaEmbedding(resultado: OrtSession.Result): Any {
        var primera: Any? = null
        for (entrada in resultado) {
            val nombre = entrada.key
            if (nombre.contains("embedding", ignoreCase = true) ||
                nombre.contains("pooler", ignoreCase = true)
            ) {
                return entrada.value.value
            }
            if (primera == null) primera = entrada.value.value
        }
        return primera ?: error("El modelo no produjo salidas")
    }

    /** Acepta salidas [1][dim] (ya embebida) o [1][seq][dim] (hay que agregar). */
    private fun vectorDesdeSalida(salida: Any, mascara: LongArray): FloatArray {
        if (salida !is Array<*> || salida.isEmpty()) error("Salida del modelo vacía")
        return when {
            salida[0] is FloatArray -> {
                // [1][dim] → embedding directo
                @Suppress("UNCHECKED_CAST")
                val filas = salida as Array<FloatArray>
                filas[0].copyOf()
            }

            salida[0] is Array<*> -> {
                // [1][seq][dim] → pooling según manifiesto
                @Suppress("UNCHECKED_CAST")
                val oculto = salida[0] as Array<FloatArray>
                when (metadatos?.pooling?.lowercase()) {
                    "cls" -> oculto[0].copyOf()
                    else -> poolingMedio(oculto, mascara)
                }
            }

            else -> error("Forma de salida no soportada (${salida[0]!!::class.simpleName})")
        }
    }

    private fun poolingMedio(oculto: Array<FloatArray>, mascara: LongArray): FloatArray {
        val dim = oculto[0].size
        val suma = FloatArray(dim)
        var contador = 0
        for (i in oculto.indices) {
            if (i < mascara.size && mascara[i] == 1L) {
                contador++
                val fila = oculto[i]
                for (j in 0 until dim) suma[j] += fila[j]
            }
        }
        if (contador == 0) return suma
        val inverso = 1f / contador
        return FloatArray(dim) { suma[it] * inverso }
    }

    private fun normalizarL2(vector: FloatArray): FloatArray {
        var norma = 0f
        for (x in vector) norma += x * x
        norma = sqrt(norma)
        if (norma < 1e-12f) return vector
        return FloatArray(vector.size) { vector[it] / norma }
    }

    private fun aplicarPlantilla(meta: MetadatosModelo, texto: String): String =
        when (meta.plantilla.lowercase()) {
            "e5" -> "query: $texto"
            else -> texto
        }

    private fun crearSesion(ruta: String): OrtSession {
        // 1) NNAPI: acelerador del SoC. Suele fallar con modelos INT8 en
        //    gama baja; probar y caer al siguiente.
        try {
            val opciones = SessionOptions()
            opciones.addNnapi()
            return entorno.createSession(ruta, opciones)
        } catch (_: Throwable) {
        }

        // 2) XNNPACK: CPU con kernels afinados para ARM.
        try {
            val opciones = SessionOptions()
            opciones.addXnnpack(mapOf("intra_op_num_threads" to "2"))
            return entorno.createSession(ruta, opciones)
        } catch (_: Throwable) {
        }

        // 3) CPU genérico.
        val opciones = SessionOptions()
        opciones.setIntraOpNumThreads(2)
        return entorno.createSession(ruta, opciones)
    }
}
