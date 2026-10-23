package com.cerlita.data.repo

import com.cerlita.core.common.DispatcherProvider
import com.cerlita.data.db.PreguntaDao
import com.cerlita.data.db.RespuestaDao
import com.cerlita.data.mapper.toDomain
import com.cerlita.data.mapper.toEntity
import com.cerlita.domain.model.OrigenPregunta
import com.cerlita.domain.model.Pregunta
import com.cerlita.domain.model.TipoPregunta
import com.cerlita.domain.repository.ContadoresPreguntas
import com.cerlita.domain.repository.PreguntaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class PreguntaRepositoryImpl(
    private val dao: PreguntaDao,
    private val respuestaDao: RespuestaDao,
    private val dispatchers: DispatcherProvider,
) : PreguntaRepository {

    override fun observePreguntasActivas(): Flow<List<Pregunta>> =
        dao.observeActivas().map { lista -> lista.mapNotNull { it.toDomain() } }

    override fun observePreguntasCustom(): Flow<List<Pregunta>> =
        dao.observeCustom().map { lista -> lista.mapNotNull { it.toDomain() } }

    override fun observeContadores(): Flow<ContadoresPreguntas> = combine(
        dao.observeConteoActivas(OrigenPregunta.OFICIAL.name),
        dao.observeConteoActivas(OrigenPregunta.CUSTOM.name),
        respuestaDao.observeTotal(),
    ) { oficiales, custom, respuestas ->
        ContadoresPreguntas(
            oficialesActivas = oficiales,
            customActivas = custom,
            totalRespuestas = respuestas,
        )
    }

    override suspend fun getPregunta(id: String): Pregunta? =
        withContext(dispatchers.io) { dao.porId(id)?.toDomain() }

    override suspend fun getPreguntasPorIds(ids: List<String>): List<Pregunta> =
        withContext(dispatchers.io) { dao.porIds(ids).mapNotNull { it.toDomain() } }

    override suspend fun getRandomPreguntas(
        cantidad: Int,
        incluirOficiales: Boolean,
        incluirCustom: Boolean,
    ): List<Pregunta> = withContext(dispatchers.io) {
        dao.alAzar(cantidad, incluirOficiales, incluirCustom).mapNotNull { it.toDomain() }
    }

    override suspend fun upsertCustom(
        texto: String,
        tema: String,
        tipo: TipoPregunta,
        opciones: List<String>,
    ): Pregunta = withContext(dispatchers.io) {
        val pregunta = Pregunta(
            id = "custom_" + UUID.randomUUID().toString().substring(0, 8),
            tema = tema.trim().lowercase(),
            tipo = tipo,
            texto = texto.trim(),
            opciones = opciones,
            origen = OrigenPregunta.CUSTOM,
        )
        val entidad = pregunta.toEntity(System.currentTimeMillis())
        dao.upsert(entidad)
        pregunta
    }

    override suspend fun actualizarCustom(pregunta: Pregunta) = withContext(dispatchers.io) {
        require(pregunta.id.startsWith("custom_")) { "Solo se pueden editar preguntas propias" }
        dao.upsert(pregunta.toEntity(System.currentTimeMillis()))
    }

    override suspend fun setActiva(id: String, activa: Boolean) = withContext(dispatchers.io) {
        dao.cambiarActiva(id, activa)
    }

    override suspend fun eliminar(id: String) = withContext(dispatchers.io) {
        dao.borrarPorId(id)
    }

    override suspend fun sincronizarExternas(preguntas: List<Pregunta>) =
        withContext(dispatchers.io) {
            if (preguntas.isEmpty()) return@withContext
            val ahora = System.currentTimeMillis()
            // REPLACE por id: llega la versión del host tal cual, con su origen.
            dao.insertarTodas(preguntas.map { it.toEntity(ahora) })
        }
}
