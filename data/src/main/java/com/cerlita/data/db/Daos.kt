package com.cerlita.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PreguntaDao {

    @Query("SELECT * FROM preguntas WHERE activa = 1 ORDER BY tema, texto")
    fun observeActivas(): Flow<List<PreguntaEntity>>

    @Query("SELECT * FROM preguntas WHERE origen = 'CUSTOM' ORDER BY agregadaEn DESC")
    fun observeCustom(): Flow<List<PreguntaEntity>>

    @Query("SELECT COUNT(*) FROM preguntas WHERE activa = 1 AND origen = :origen")
    fun observeConteoActivas(origen: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM preguntas WHERE origen = 'OFICIAL'")
    suspend fun contarOficiales(): Int

    @Query("SELECT * FROM preguntas WHERE id = :id")
    suspend fun porId(id: String): PreguntaEntity?

    @Query("SELECT * FROM preguntas WHERE id IN (:ids)")
    suspend fun porIds(ids: List<String>): List<PreguntaEntity>

    @Query(
        """
        SELECT * FROM preguntas
        WHERE activa = 1
          AND ((:incluirOficiales AND origen = 'OFICIAL')
            OR (:incluirCustom AND origen = 'CUSTOM'))
        ORDER BY RANDOM()
        LIMIT :cantidad
        """,
    )
    suspend fun alAzar(cantidad: Int, incluirOficiales: Boolean, incluirCustom: Boolean): List<PreguntaEntity>

    @Upsert
    suspend fun upsert(pregunta: PreguntaEntity)

    @Query("UPDATE preguntas SET activa = :activa WHERE id = :id")
    suspend fun cambiarActiva(id: String, activa: Boolean)

    @Query("DELETE FROM preguntas WHERE id = :id")
    suspend fun borrarPorId(id: String)

    @Query("DELETE FROM preguntas WHERE origen = 'OFICIAL'")
    suspend fun borrarOficiales()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodas(preguntas: List<PreguntaEntity>)
}

@Dao
interface RespuestaDao {

    /** REPLACE gracias al índice único (ronda, pregunta, autor). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(respuesta: RespuestaEntity): Long

    @Query("SELECT * FROM respuestas WHERE sesionId = :rondaId ORDER BY fecha")
    fun observeDeRonda(rondaId: String): Flow<List<RespuestaEntity>>

    @Query("SELECT COUNT(*) FROM respuestas")
    fun observeTotal(): Flow<Long>
}

@Dao
interface RondaDao {

    @Upsert
    suspend fun upsert(ronda: RondaEntity)

    @Query("SELECT * FROM rondas WHERE estado = 'EN_CURSO' ORDER BY creada DESC LIMIT 1")
    fun observeActiva(): Flow<RondaEntity?>

    @Query("UPDATE rondas SET indiceActual = :indice WHERE id = :id")
    suspend fun actualizarIndice(id: String, indice: Int)

    @Query("UPDATE rondas SET estado = 'FINALIZADA', finalizada = :fecha WHERE id = :id")
    suspend fun finalizar(id: String, fecha: Long)

    @Query("SELECT * FROM rondas WHERE id = :id")
    suspend fun porId(id: String): RondaEntity?
}

@Dao
interface EvaluacionDao {

    @Query("SELECT * FROM evaluaciones WHERE sesionId = :rondaId ORDER BY fecha")
    fun observeDeRonda(rondaId: String): Flow<List<EvaluacionEntity>>

    @Upsert
    suspend fun guardar(evaluacion: EvaluacionEntity)
}

@Dao
interface PerfilDao {

    @Query("SELECT * FROM perfil WHERE id = 1")
    fun observe(): Flow<PerfilEntity?>

    @Upsert
    suspend fun upsert(perfil: PerfilEntity)
}
