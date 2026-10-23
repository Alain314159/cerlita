package com.cerlita.data.remote

import android.content.Context
import com.cerlita.core.common.DispatcherProvider
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Lee el catálogo semilla empaquetado en los assets del módulo data. */
class CatalogoAssets(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun leer(): Result<CatalogoDTO> = withContext(dispatchers.io) {
        runCatching {
            context.assets.open(ARCHIVO).bufferedReader().use { it.readText() }
        }.mapCatching { texto -> JsonCerlita.decodeFromString(CatalogoDTO.serializer(), texto) }
    }

    companion object {
        const val ARCHIVO = "preguntas.json"
    }
}

/** Descarga el catálogo remoto desde la raw URL del repositorio GitHub. */
class CatalogoRemoto(private val dispatchers: DispatcherProvider) {

    suspend fun descargar(url: String = URL_BASE): Result<CatalogoDTO> = withContext(dispatchers.io) {
        runCatching {
            val conexion = URL(url).openConnection() as HttpURLConnection
            conexion.connectTimeout = CONECTAR_MS
            conexion.readTimeout = LEER_MS
            conexion.instanceFollowRedirects = true
            try {
                val codigo = conexion.responseCode
                if (codigo !in 200..299) error("HTTP $codigo al descargar el catálogo")
                conexion.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conexion.disconnect()
            }
        }.mapCatching { texto -> JsonCerlita.decodeFromString(CatalogoDTO.serializer(), texto) }
    }

    companion object {
        /** Catálogo canónico servido por GitHub Pages/raw del repositorio. */
        const val URL_BASE =
            "https://raw.githubusercontent.com/Alain314159/cerlita/main/content/preguntas.json"
        private const val CONECTAR_MS = 10_000
        private const val LEER_MS = 20_000
    }
}
