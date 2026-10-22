package com.cerlita.app.di

import android.content.Context
import androidx.room.Room
import com.cerlita.core.common.DefaultDispatcherProvider
import com.cerlita.core.common.DispatcherProvider
import com.cerlita.data.datastore.AjustesDataStore
import com.cerlita.data.db.CerlitaDatabase
import com.cerlita.data.remote.CatalogoAssets
import com.cerlita.data.remote.CatalogoRemoto
import com.cerlita.data.repo.GestorCatalogo
import com.cerlita.data.repo.PerfilRepositoryImpl
import com.cerlita.data.repo.PreguntaRepositoryImpl
import com.cerlita.data.repo.RondaRepositoryImpl
import com.cerlita.domain.repository.PerfilRepository
import com.cerlita.domain.repository.PreguntaRepository
import com.cerlita.domain.repository.RondaRepository

/**
 * Inyección manual de dependencias (app pequeña, sin Hilt): un solo lugar
 * donde se construye el grafo. Si el proyecto crece, migrar a Hilt.
 */
class Contenedor(context: Context) {

    val dispatchers: DispatcherProvider = DefaultDispatcherProvider()

    private val db: CerlitaDatabase =
        Room.databaseBuilder(context, CerlitaDatabase::class.java, "cerlita.db")
            .build()

    private val preguntaDao = db.preguntaDao()
    private val respuestaDao = db.respuestaDao()
    private val rondaDao = db.rondaDao()
    private val perfilDao = db.perfilDao()

    private val ajustesDataStore = AjustesDataStore(context)

    val gestorCatalogo: GestorCatalogo = GestorCatalogo(
        db = db,
        remoto = CatalogoRemoto(dispatchers),
        assets = CatalogoAssets(context, dispatchers),
        ajustes = ajustesDataStore,
        dispatchers = dispatchers,
    )

    val preguntaRepository: PreguntaRepository =
        PreguntaRepositoryImpl(preguntaDao, respuestaDao, dispatchers)

    val rondaRepository: RondaRepository =
        RondaRepositoryImpl(rondaDao, respuestaDao, dispatchers)

    val perfilRepository: PerfilRepository =
        PerfilRepositoryImpl(perfilDao, dispatchers)
}
