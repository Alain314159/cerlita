package com.cerlita.core.ai

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.cerlita.core.common.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/** Instalación terminada desde cualquier origen (descarga o importación). */
data class ResultadoInstalacion(
    val metadatos: MetadatosModelo,
    /** true si quedó copia pública en Descargas/Cerlita (sobrevive a desinstalar). */
    val respaldoEnDescargas: Boolean,
)

/**
 * Gestiona el ciclo de vida del modelo de IA local:
 *
 *  1. **Descarga**: consulta el release `modelo-ia-v1` del repositorio GitHub y
 *     baja el **zip comprimido** (mucho más ligero que el .onnx suelto) con
 *     progreso en vivo.
 *  2. **Instalación**: valida el manifiesto del zip y lo extrae de forma
 *     atómica a `filesDir/modelos/cerlita`.
 *  3. **Respaldo**: en Android 10+ guarda el zip en la carpeta pública
 *     `Descargas/Cerlita`, que **sobrevive a desinstalar la app**. Tras una
 *     reinstalación se recupera con "Importar desde el almacenamiento".
 *  4. **Importación**: el usuario elige el zip con el selector de archivos del
 *     sistema (SAF, sin permisos) y sigue exactamente el mismo camino de
 *     validación e instalación. Sirve también para zips pasados por cable o
 *     descargados por otros medios.
 *
 * La app funciona sin modelo: solo se usa para detectar preguntas duplicadas.
 */
class GestorModeloIA(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val config: Config = Config(),
) {

    data class Config(
        /** Endpoint del release que contiene el zip del modelo. */
        val urlApiRelease: String =
            "https://api.github.com/repos/Alain314159/cerlita/releases/tags/modelo-ia-v1",
        /** Opcional: solo necesario si el repositorio es privado. */
        val tokenGithub: String? = null,
        /** Tope de seguridad para descargar/aceptar un paquete. */
        val tamanoMaximoMb: Int = 400,
    )

    private val _estado = MutableStateFlow<EstadoModeloIA>(EstadoModeloIA.NoInstalado)
    val estado: StateFlow<EstadoModeloIA> = _estado.asStateFlow()

    /** Carpeta interna donde vive el modelo instalado (se borra al desinstalar la app). */
    val directorioInstalacion: File
        get() = File(context.filesDir, DIRECTORIO)

    init {
        detectarInstalado()
    }

    /** Refleja en el estado un modelo ya instalado por una ejecución anterior. */
    fun detectarInstalado() {
        val metadatos = metadatosInstalados()
        _estado.value = if (metadatos != null) {
            EstadoModeloIA.Instalado(metadatos, tamanoMb(directorioInstalacion))
        } else {
            EstadoModeloIA.NoInstalado
        }
    }

    fun metadatosInstalados(): MetadatosModelo? = MetadatosModelo.desdeArchivo(
        File(directorioInstalacion, MetadatosModelo.NOMBRE_ARCHIVO),
    ).getOrNull()

    // ─────────────────────────────────────────────────────────────────────
    //  Descarga desde GitHub (zip comprimido del release "modelo-ia-v1")
    // ─────────────────────────────────────────────────────────────────────

    suspend fun descargarDesdeGithub(): Result<ResultadoInstalacion> = withContext(dispatchers.io) {
        runCatching {
            val (url, bytes) = buscarAssetModelo().getOrThrow()
            val mbTotales = bytes / (1024f * 1024f)
            if (bytes > 0 && mbTotales > config.tamanoMaximoMb) {
                error("El modelo publicado pesa %.0f MB y el límite configurado es %d MB".format(mbTotales, config.tamanoMaximoMb))
            }

            val zip = File(context.cacheDir, NOMBRE_ZIP_CACHE)
            _estado.value = EstadoModeloIA.Descargando(0f, mbTotales, 0f)
            var ultimoReporte = -1L
            descargarArchivo(url, zip) { leidos, total ->
                val efectivo = if (total > 0) total else bytes
                if (leidos - ultimoReporte > UMBRAL_PROGRESO_BYTES || leidos >= efectivo) {
                    ultimoReporte = leidos
                    _estado.value = EstadoModeloIA.Descargando(
                        progreso = if (efectivo > 0) leidos.toFloat() / efectivo else 0f,
                        mbTotales = efectivo / (1024f * 1024f),
                        mbDescargados = leidos / (1024f * 1024f),
                    )
                }
            }

            // Instala conservando el zip para el respaldo público en Descargas.
            val metadatos = instalarDesdeZip(zip, conservarZip = true).getOrThrow()
            val respaldo = guardarRespaldoEnDescargas(zip)
            zip.delete()
            ResultadoInstalacion(metadatos, respaldo)
        }.onFailure {
            if (it is CancellationException) throw it
            _estado.value = EstadoModeloIA.Error(mensajeLegible(it))
            File(context.cacheDir, NOMBRE_ZIP_CACHE).delete()
        }
    }

    /** Consulta el release y devuelve (url del zip, tamaño en bytes). */
    private fun buscarAssetModelo(): Result<Pair<String, Long>> = runCatching {
        val conexion = URL(config.urlApiRelease).openConnection() as HttpURLConnection
        conexion.connectTimeout = CONECTAR_MS
        conexion.readTimeout = LEER_MS
        conexion.setRequestProperty("Accept", "application/vnd.github+json")
        autorizar(conexion)
        try {
            when (conexion.responseCode) {
                404 -> error("Todavía no hay modelo publicado en GitHub (${nombreRelease()}). Importa el zip desde el almacenamiento mientras tanto.")
                in 200..299 -> {}
                else -> error("GitHub respondió HTTP ${conexion.responseCode}")
            }
            val texto = conexion.inputStream.bufferedReader().use { it.readText() }
            val assets = Json.parseToJsonElement(texto).jsonObject["assets"]?.jsonArray
                ?: error("El release no tiene archivos adjuntos")
            val asset = assets.map { it.jsonObject }
                .firstOrNull { (it["name"]?.jsonPrimitive?.content ?: "").endsWith(".zip") }
                ?: error("El release no incluye ningún .zip del modelo")
            val url = asset["browser_download_url"]?.jsonPrimitive?.content
                ?: error("El archivo del release no tiene URL de descarga")
            val tamano = asset["size"]?.jsonPrimitive?.long ?: 0L
            url to tamano
        } finally {
            conexion.disconnect()
        }
    }

    /** Descarga con seguimiento de progreso; cancelable desde la corrutina llamadora. */
    private suspend fun descargarArchivo(url: String, destino: File, enProgreso: (Long, Long) -> Unit) {
        val conexion = URL(url).openConnection() as HttpURLConnection
        conexion.connectTimeout = CONECTAR_MS
        conexion.readTimeout = LEER_MS
        conexion.instanceFollowRedirects = true
        autorizar(conexion)
        try {
            val codigo = conexion.responseCode
            if (codigo !in 200..299) error("HTTP $codigo al descargar el modelo")
            val total = conexion.contentLengthLong
            conexion.inputStream.use { entrada ->
                destino.outputStream().use { salida ->
                    val buffer = ByteArray(TAMANO_BUFFER)
                    var leidos = 0L
                    while (true) {
                        val n = entrada.read(buffer)
                        if (n < 0) break
                        salida.write(buffer, 0, n)
                        leidos += n
                        currentCoroutineContext().ensureActive()
                        enProgreso(leidos, total)
                    }
                }
            }
        } finally {
            conexion.disconnect()
        }
    }

    private fun autorizar(conexion: HttpURLConnection) {
        config.tokenGithub?.takeIf { it.isNotBlank() }?.let {
            conexion.setRequestProperty("Authorization", "Bearer $it")
        }
    }

    private fun nombreRelease(): String = config.urlApiRelease.substringAfterLast("/")

    // ─────────────────────────────────────────────────────────────────────
    //  Importación desde el almacenamiento del teléfono (SAF, sin permisos)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Instala el zip elegido con el selector del sistema (Descargas, "Mis
     * archivos", unidad USB…). Es el camino de recuperación tras desinstalar
     * y reinstalar la app usando la copia de Descargas/Cerlita.
     */
    suspend fun importarDesdeUri(uri: Uri): Result<ResultadoInstalacion> = withContext(dispatchers.io) {
        runCatching {
            _estado.value = EstadoModeloIA.Importando
            val zip = File(context.cacheDir, NOMBRE_ZIP_CACHE)
            context.contentResolver.openInputStream(uri)
                ?.use { flujo -> zip.outputStream().use { flujo.copyTo(it, TAMANO_BUFFER) } }
                ?: error("No se pudo abrir el archivo seleccionado")

            val tamano = zip.length()
            if (tamano < 1024L) error("El archivo seleccionado está vacío o corrupto")
            if (tamano > config.tamanoMaximoMb * 1024L * 1024L) {
                error("El zip pesa más de ${config.tamanoMaximoMb} MB")
            }
            val metadatos = instalarDesdeZip(zip, conservarZip = false).getOrThrow()
            ResultadoInstalacion(metadatos, respaldoEnDescargas = false)
        }.onFailure {
            if (it is CancellationException) throw it
            _estado.value = EstadoModeloIA.Error(mensajeLegible(it))
            File(context.cacheDir, NOMBRE_ZIP_CACHE).delete()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Instalación común (validación + extracción atómica)
    // ─────────────────────────────────────────────────────────────────────

    private suspend fun instalarDesdeZip(zip: File, conservarZip: Boolean): Result<MetadatosModelo> =
        withContext(dispatchers.io) {
            runCatching {
                _estado.value = EstadoModeloIA.Instalando
                val dirTemporal = File(context.cacheDir, DIRECTORIO_TEMPORAL)
                dirTemporal.deleteRecursively()
                if (!dirTemporal.mkdirs()) error("No se pudo preparar la carpeta temporal")

                extraerZip(zip, dirTemporal)

                val metadatos = MetadatosModelo.desdeArchivo(
                    File(dirTemporal, MetadatosModelo.NOMBRE_ARCHIVO),
                ).getOrElse {
                    error("El zip no trae un metadatos.json válido (¿es un paquete Cerlita?): ${it.message}")
                }
                validarPaquete(metadatos, dirTemporal)

                // Instalación atómica: o queda el modelo anterior, o el nuevo.
                val destino = directorioInstalacion
                destino.parentFile?.mkdirs()
                if (destino.exists()) destino.deleteRecursively()
                if (!dirTemporal.renameTo(destino)) {
                    dirTemporal.copyRecursively(destino, overwrite = true)
                    dirTemporal.deleteRecursively()
                }
                if (!conservarZip) zip.delete()

                _estado.value = EstadoModeloIA.Instalado(metadatos, tamanoMb(directorioInstalacion))
                metadatos
            }
        }

    /** Extracción con protección "zip slip" y sanity checks de tamaño/cantidad. */
    private fun extraerZip(zip: File, dirDestino: File) {
        val raiz = dirDestino.canonicalPath + File.separator
        ZipFile(zip).use { archivoZip ->
            val entradas = archivoZip.entries().asSequence().toList()
            if (entradas.size > MAX_ARCHIVOS_ZIP) {
                error("El zip tiene demasiados archivos (${entradas.size}); ¿es un paquete Cerlita?")
            }
            for (entrada in entradas) {
                val destino = File(dirDestino, entrada.name)
                if (!destino.canonicalPath.startsWith(raiz)) {
                    error("El zip contiene rutas inválidas: ${entrada.name}")
                }
                if (entrada.isDirectory) {
                    destino.mkdirs()
                } else {
                    destino.parentFile?.mkdirs()
                    archivoZip.getInputStream(entrada).use { flujo ->
                        destino.outputStream().use { flujo.copyTo(it, TAMANO_BUFFER) }
                    }
                }
            }
        }
    }

    private fun validarPaquete(metadatos: MetadatosModelo, dir: File) {
        if (metadatos.formato != MetadatosModelo.FORMATO_ACTUAL) {
            error("Versión de paquete no soportada (formato ${metadatos.formato}; esta app entiende ${MetadatosModelo.FORMATO_ACTUAL}). Actualiza Cerlita.")
        }
        if (metadatos.tokenizador != TOKENIZADOR_WORDPIECE) {
            error("El paquete usa el tokenizador '${metadatos.tokenizador}', aún no soportado. Usa un modelo WordPiece (BETO/BERT/e5/MiniLM).")
        }
        if (metadatos.dimensiones !in 32..2048) error("Dimensión de embedding inválida: ${metadatos.dimensiones}")
        if (metadatos.maxLen !in 8..512) error("Longitud máxima inválida: ${metadatos.maxLen}")

        val raiz = dir.canonicalPath + File.separator
        for (nombre in listOf(metadatos.archivoModelo, metadatos.archivoTokenizador)) {
            val archivo = File(dir, nombre)
            if (!archivo.canonicalPath.startsWith(raiz)) error("metadatos.json declara rutas inválidas: $nombre")
        }
        val archivoModelo = File(dir, metadatos.archivoModelo)
        if (!archivoModelo.isFile || archivoModelo.length() < TAMANO_MINIMO_ONNX) {
            error("Falta ${metadatos.archivoModelo} o pesa menos de 100 KB")
        }
        if (!File(dir, metadatos.archivoTokenizador).isFile) {
            error("Falta el tokenizador ${metadatos.archivoTokenizador}")
        }
    }

    suspend fun desinstalar(): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            directorioInstalacion.deleteRecursively()
            File(context.cacheDir, NOMBRE_ZIP_CACHE).delete()
            _estado.value = EstadoModeloIA.NoInstalado
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Respaldo público en Descargas/Cerlita (sobrevive a desinstalar)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Copia el zip a la colección pública de Descargas (Android 10+). Sin
     * permisos: MediaStore permite a cada app contribuir con sus propios
     * archivos. El archivo es visible para el usuario y para el selector SAF
     * tras una reinstalación.
     */
    private fun guardarRespaldoEnDescargas(zip: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val resolver = context.contentResolver
            borrarRespaldoAnterior()

            val valores = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, NOMBRE_ZIP_RESPALDO)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, CARPETA_RESPALDO)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
                ?: return false
            resolver.openOutputStream(uri)?.use { salida ->
                zip.inputStream().use { entrada -> entrada.copyTo(salida, TAMANO_BUFFER) }
            } ?: return false
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Elimina la copia anterior con el mismo nombre para no acumular respaldos. */
    private fun borrarRespaldoAnterior() {
        try {
            val resolver = context.contentResolver
            val antiguos = mutableListOf<Uri>()
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf("$CARPETA_RESPALDO/", NOMBRE_ZIP_RESPALDO),
                null,
            )?.use { cursor ->
                val columna = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                while (cursor.moveToNext()) {
                    antiguos += Uri.withAppendedPath(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cursor.getLong(columna).toString(),
                    )
                }
            }
            antiguos.forEach { runCatching { resolver.delete(it, null, null) } }
        } catch (_: Exception) {
            // El respaldo es best-effort: nunca debe tumbar una instalación.
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Utilidades
    // ─────────────────────────────────────────────────────────────────────

    private fun tamanoMb(dir: File): Float {
        var bytes = 0L
        dir.walkTopDown().forEach { archivo -> if (archivo.isFile) bytes += archivo.length() }
        return (bytes / (1024f * 1024f))
    }

    private fun mensajeLegible(t: Throwable): String {
        val detalle = (t.cause?.message ?: t.message)?.take(160)?.replace('\n', ' ')
            ?: "error desconocido"
        return if (t is IOException) "Problema de red: $detalle" else detalle
    }

    companion object {
        const val DIRECTORIO = "modelos/cerlita"
        private const val DIRECTORIO_TEMPORAL = "modelo-tmp"
        private const val NOMBRE_ZIP_CACHE = "cerlita-modelo-ia.zip"
        private const val NOMBRE_ZIP_RESPALDO = "cerlita-modelo-ia.zip"
        private const val CARPETA_RESPALDO = "Download/Cerlita"
        private const val TOKENIZADOR_WORDPIECE = "wordpiece"

        private const val TAMANO_BUFFER = 64 * 1024
        private const val UMBRAL_PROGRESO_BYTES = 512L * 1024
        private const val MAX_ARCHIVOS_ZIP = 32
        private const val TAMANO_MINIMO_ONNX = 100L * 1024
        private const val CONECTAR_MS = 15_000
        private const val LEER_MS = 60_000
    }
}
