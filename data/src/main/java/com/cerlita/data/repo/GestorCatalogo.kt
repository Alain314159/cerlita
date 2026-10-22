package com.cerlita.data.repo

import com.cerlita.core.common.DispatcherProvider
import com.cerlita.data.datastore.AjustesDataStore
import com.cerlita.data.db.CerlitaDatabase
import com.cerlita.data.mapper.reemplazarCatalogoOficial
import com.cerlita.data.remote.CatalogoAssets
import com.cerlita.data.remote.CatalogoRemoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Resultado de una búsqueda de actualizaciones del catálogo. */
sealed class ResultadoActualizacion {
    data class AlDia(val version: String) : ResultadoActualizacion()
    data class Actualizado(val desde: String, val hacia: String, val preguntas: Int) : ResultadoActualizacion()
    data class Error(val mensaje: String) : ResultadoActualizacion()
}

/**
 * Orquesta el ciclo de vida del catálogo de preguntas:
 *  1. [sembrarCatalogoInicial]: en el primer arranque, siembra el catálogo
 *     empaquetado en assets (offline-first).
 *  2. [buscarEInstalar]: descarga el JSON canónico del repositorio GitHub
 *     (raw URL) y, si la versión cambia, reemplaza en una transacción Room
 *     todas las preguntas OFICIAL. Las CUSTOM del usuario nunca se tocan.
 */
class GestorCatalogo(
    private val db: CerlitaDatabase,
    private val remoto: CatalogoRemoto,
    private val assets: CatalogoAssets,
    private val ajustes: AjustesDataStore,
    private val dispatchers: DispatcherProvider,
) {
    val versionInstalada: Flow<String> = ajustes.versionCatalogo

    suspend fun sembrarCatalogoInicial(): Boolean = withContext(dispatchers.io) {
        if (ajustes.catalogoSembrado.first()) return@withContext false
        val dto = assets.leer().getOrNull() ?: return@withContext false
        val cantidad = db.reemplazarCatalogoOficial(dto, System.currentTimeMillis())
        ajustes.guardarVersionCatalogo(dto.version)
        ajustes.marcarCatalogoSembrado()
        cantidad > 0
    }

    suspend fun buscarEInstalar(): ResultadoActualizacion = withContext(dispatchers.io) {
        val descarga = remoto.descargar()
        val dto = descarga.getOrNull()
            ?: return@withContext ResultadoActualizacion.Error(
                descarga.exceptionOrNull()?.message ?: "No se pudo descargar el catálogo",
            )
        val versionLocal = ajustes.versionCatalogo.first()
        if (versionLocal == dto.version) {
            return@withContext ResultadoActualizacion.AlDia(versionLocal)
        }
        val cantidad = db.reemplazarCatalogoOficial(dto, System.currentTimeMillis())
        ajustes.guardarVersionCatalogo(dto.version)
        ResultadoActualizacion.Actualizado(
            desde = versionLocal.ifEmpty { "—" },
            hacia = dto.version,
            preguntas = cantidad,
        )
    }
}
