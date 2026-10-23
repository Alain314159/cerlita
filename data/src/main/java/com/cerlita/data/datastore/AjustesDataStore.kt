package com.cerlita.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.almacenCerlita by preferencesDataStore(name = "cerlita_ajustes")

/** Ajustes persistentes (DataStore): versión del catálogo y flags de primera ejecución. */
class AjustesDataStore(private val context: Context) {

    private object Claves {
        val VERSION_CATALOGO = stringPreferencesKey("version_catalogo")
        val CATALOGO_SEMBRADO = booleanPreferencesKey("catalogo_sembrado")
        val ONBOARDING_LISTO = booleanPreferencesKey("onboarding_listo")
    }

    val versionCatalogo: Flow<String> =
        context.almacenCerlita.data.map { it[Claves.VERSION_CATALOGO] ?: "" }

    val catalogoSembrado: Flow<Boolean> =
        context.almacenCerlita.data.map { it[Claves.CATALOGO_SEMBRADO] ?: false }

    val onboardingListo: Flow<Boolean> =
        context.almacenCerlita.data.map { it[Claves.ONBOARDING_LISTO] ?: false }

    suspend fun guardarVersionCatalogo(version: String) {
        context.almacenCerlita.edit { it[Claves.VERSION_CATALOGO] = version }
    }

    suspend fun marcarCatalogoSembrado() {
        context.almacenCerlita.edit { it[Claves.CATALOGO_SEMBRADO] = true }
    }

    suspend fun marcarOnboardingListo() {
        context.almacenCerlita.edit { it[Claves.ONBOARDING_LISTO] = true }
    }
}
