package com.cerlita.app.di

import android.content.Context
import androidx.room.Room
import com.cerlita.app.BuildConfig
import com.cerlita.core.ai.EmbeddingEngine
import com.cerlita.core.ai.GestorModeloIA
import com.cerlita.core.ai.MotorEmbeddingOnnx
import com.cerlita.core.common.DefaultDispatcherProvider
import com.cerlita.core.common.DispatcherProvider
import com.cerlita.core.network.CerlitaNearbyClient
import com.cerlita.data.datastore.AjustesDataStore
import com.cerlita.data.db.CerlitaDatabase
import com.cerlita.data.remote.CatalogoAssets
import com.cerlita.data.remote.CatalogoRemoto
import com.cerlita.data.repo.GestorCatalogo
import com.cerlita.data.repo.PerfilRepositoryImpl
import com.cerlita.data.repo.PreguntaRepositoryImpl
import com.cerlita.data.repo.RondaRepositoryImpl
import com.cerlita.domain.repository.CanalPareja
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
            .addMigrations(CerlitaDatabase.MIGRACION_1_2)
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

    /**
     * Modelo de IA local: descarga desde GitHub (zip), importación desde el
     * almacenamiento del teléfono y respaldo público en Descargas/Cerlita.
     * La configuración viene de local.properties vía BuildConfig.
     */
    val gestorModeloIA: GestorModeloIA = GestorModeloIA(
        context = context,
        dispatchers = dispatchers,
        config = GestorModeloIA.Config(
            urlApiRelease = BuildConfig.MODELO_URL_API_RELEASE,
            tokenGithub = BuildConfig.MODELO_TOKEN_GITHUB.takeIf { it.isNotBlank() },
            tamanoMaximoMb = BuildConfig.MODELO_TAMANO_MAX_MB,
        ),
    )

    /** Motor de embeddings ONNX: perezoso, no bloquea el arranque. */
    val motorEmbedding: EmbeddingEngine by lazy {
        MotorEmbeddingOnnx(gestorModeloIA, dispatchers)
    }

    val preguntaRepository: PreguntaRepository =
        PreguntaRepositoryImpl(preguntaDao, respuestaDao, dispatchers)

    val rondaRepository: RondaRepository =
        RondaRepositoryImpl(rondaDao, respuestaDao, dispatchers)

    val perfilRepository: PerfilRepository =
        PerfilRepositoryImpl(perfilDao, dispatchers)

    /**
     * Canal P2P de pareja (Nearby Connections). Singleton: debe sobrevivir
     * a la navegación para que las respuestas cruzadas lleguen a la ronda.
     */
    val canalPareja: CanalPareja = CerlitaNearbyClient(context)
}
