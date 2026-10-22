package com.cerlita.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PreguntaEntity::class,
        RespuestaEntity::class,
        RondaEntity::class,
        PerfilEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CerlitaDatabase : RoomDatabase() {
    abstract fun preguntaDao(): PreguntaDao
    abstract fun respuestaDao(): RespuestaDao
    abstract fun rondaDao(): RondaDao
    abstract fun perfilDao(): PerfilDao
}
