package com.cerlita.data.repo

import com.cerlita.core.common.DispatcherProvider
import com.cerlita.data.db.RespuestaDao
import com.cerlita.data.db.RondaDao
import com.cerlita.data.mapper.toDomain
import com.cerlita.data.mapper.toEntity
import com.cerlita.domain.model.EstadoRonda
import com.cerlita.domain.model.ModoRonda
import com.cerlita.domain.model.Respuesta
import com.cerlita.domain.model.Ronda
import com.cerlita.domain.repository.RondaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class RondaRepositoryImpl(
    private val rondaDao: RondaDao,
    private val respuestaDao: RespuestaDao,
    private val dispatchers: DispatcherProvider,
) : RondaRepository {

    override fun observeRondaActiva(): Flow<Ronda?> =
        rondaDao.observeActiva().map { it?.toDomain() }

    override fun observeRespuestasDeRonda(rondaId: String): Flow<List<Respuesta>> =
        respuestaDao.observeDeRonda(rondaId).map { lista -> lista.mapNotNull { it.toDomain() } }

    override suspend fun crearRonda(preguntaIds: List<String>): Ronda =
        withContext(dispatchers.io) {
            val ronda = Ronda(
                id = UUID.randomUUID().toString(),
                modo = ModoRonda.LOCAL,
                estado = EstadoRonda.EN_CURSO,
                preguntaIds = preguntaIds,
            )
            rondaDao.upsert(ronda.toEntity())
            ronda
        }

    override suspend fun avanzarIndice(rondaId: String, indice: Int) =
        withContext(dispatchers.io) { rondaDao.actualizarIndice(rondaId, indice) }

    override suspend fun finalizarRonda(rondaId: String) =
        withContext(dispatchers.io) { rondaDao.finalizar(rondaId, System.currentTimeMillis()) }

    override suspend fun guardarRespuesta(respuesta: Respuesta) {
        withContext(dispatchers.io) { respuestaDao.insertar(respuesta.toEntity()) }
    }
}
