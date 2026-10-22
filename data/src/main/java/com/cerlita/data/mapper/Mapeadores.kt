package com.cerlita.data.mapper

import androidx.room.withTransaction
import com.cerlita.data.db.CerlitaDatabase
import com.cerlita.data.db.PerfilEntity
import com.cerlita.data.db.PreguntaEntity
import com.cerlita.data.db.RespuestaEntity
import com.cerlita.data.db.RondaEntity
import com.cerlita.data.remote.CatalogoDTO
import com.cerlita.data.remote.PreguntaDTO
import com.cerlita.data.remote.JsonCerlita
import com.cerlita.domain.model.Animal
import com.cerlita.domain.model.OrigenPregunta
import com.cerlita.domain.model.Perfil
import com.cerlita.domain.model.Pregunta
import com.cerlita.domain.model.Respuesta
import com.cerlita.domain.model.Ronda
import com.cerlita.domain.model.EstadoRonda
import com.cerlita.domain.model.ModoRonda
import com.cerlita.domain.model.TipoPregunta
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

private val serializadorOpciones = ListSerializer(String.serializer())

private fun String.comoTipo(): TipoPregunta? =
    runCatching { TipoPregunta.valueOf(trim().uppercase()) }.getOrNull()

private fun String.comoAnimal(): Animal? =
    runCatching { Animal.valueOf(trim().uppercase()) }.getOrNull()

private fun String.comoOrigen(): OrigenPregunta? =
    runCatching { OrigenPregunta.valueOf(trim().uppercase()) }.getOrNull()

// ---------------------------------------------------------------------------
// Pregunta: dominio <-> entidad <-> DTO
// ---------------------------------------------------------------------------

fun PreguntaEntity.toDomain(): Pregunta? {
    val tipo = tipo.comoTipo() ?: return null
    val opciones = runCatching { JsonCerlita.decodeFromString(serializadorOpciones, opcionesJson) }
        .getOrElse { emptyList() }
    return Pregunta(
        id = id,
        tema = tema,
        tipo = tipo,
        texto = texto,
        opciones = opciones,
        origen = origen.comoOrigen() ?: OrigenPregunta.OFICIAL,
        activa = activa,
    )
}

fun Pregunta.toEntity(agregadaEn: Long): PreguntaEntity = PreguntaEntity(
    id = id,
    tema = tema,
    tipo = tipo.name,
    texto = texto,
    opcionesJson = JsonCerlita.encodeToString(serializadorOpciones, opciones),
    origen = origen.name,
    activa = activa,
    agregadaEn = agregadaEn,
)

/** DTO del catálogo remoto -> entidad (filtrando las inválidas). */
fun PreguntaDTO.toEntity(agregadaEn: Long): PreguntaEntity? {
    val tipo = tipo.comoTipo() ?: return null
    if (texto.length !in Pregunta.MIN_TEXTO..Pregunta.MAX_TEXTO) return null
    if (tipo == TipoPregunta.OPCIONES && opciones.size !in 2..Pregunta.MAX_OPCIONES) return null
    return PreguntaEntity(
        id = id,
        tema = tema.trim().lowercase(),
        tipo = tipo.name,
        texto = texto.trim(),
        opcionesJson = JsonCerlita.encodeToString(serializadorOpciones, opciones),
        origen = OrigenPregunta.OFICIAL.name,
        activa = true,
        agregadaEn = agregadaEn,
    )
}

/**
 * Reemplazo atómico del catálogo oficial: borra las OFICIAL e inserta las
 * nuevas dentro de una transacción Room.
 */
suspend fun CerlitaDatabase.reemplazarCatalogoOficial(dto: CatalogoDTO, ahora: Long): Int {
    val entidades = dto.preguntas.mapNotNull { it.toEntity(ahora) }
    withTransaction {
        preguntaDao().borrarOficiales()
        preguntaDao().insertarTodas(entidades)
    }
    return entidades.size
}

// ---------------------------------------------------------------------------
// Respuesta / Ronda / Perfil
// ---------------------------------------------------------------------------

fun RespuestaEntity.toDomain(): Respuesta? {
    val autor = autor.comoAnimal() ?: return null
    return Respuesta(
        id = id,
        preguntaId = preguntaId,
        sesionId = sesionId,
        autor = autor,
        valor = valor,
        fecha = fecha,
    )
}

fun Respuesta.toEntity(): RespuestaEntity = RespuestaEntity(
    id = id,
    preguntaId = preguntaId,
    sesionId = sesionId,
    autor = autor.name,
    valor = valor,
    fecha = fecha,
)

fun RondaEntity.toDomain(): Ronda? {
    val modo = runCatching { ModoRonda.valueOf(modo) }.getOrNull() ?: return null
    val estado = runCatching { EstadoRonda.valueOf(estado) }.getOrNull() ?: return null
    val ids = runCatching {
        JsonCerlita.decodeFromString(serializadorOpciones, preguntaIdsJson)
    }.getOrElse { emptyList() }
    return Ronda(
        id = id,
        modo = modo,
        estado = estado,
        preguntaIds = ids,
        indiceActual = indiceActual,
        creada = creada,
        finalizada = finalizada,
    )
}

fun Ronda.toEntity(): RondaEntity = RondaEntity(
    id = id,
    modo = modo.name,
    estado = estado.name,
    preguntaIdsJson = JsonCerlita.encodeToString(serializadorOpciones, preguntaIds),
    indiceActual = indiceActual,
    creada = creada,
    finalizada = finalizada,
)

fun PerfilEntity.toDomain(): Perfil? {
    val animal = animal.comoAnimal() ?: return null
    return Perfil(
        nombrePropio = nombrePropio,
        animal = animal,
        nombrePareja = nombrePareja,
    )
}

fun Perfil.toEntity(ahora: Long): PerfilEntity = PerfilEntity(
    id = 1,
    nombrePropio = nombrePropio.trim(),
    animal = animal.name,
    nombrePareja = nombrePareja.trim(),
    actualizado = ahora,
)
