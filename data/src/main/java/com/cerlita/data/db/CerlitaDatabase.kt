package com.cerlita.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PreguntaEntity::class,
        RespuestaEntity::class,
        RondaEntity::class,
        PerfilEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CerlitaDatabase : RoomDatabase() {
    abstract fun preguntaDao(): PreguntaDao
    abstract fun respuestaDao(): RespuestaDao
    abstract fun rondaDao(): RondaDao
    abstract fun perfilDao(): PerfilDao

    companion object {
        /**
         * v1 -> v2: índice único de respuestas (ronda, pregunta, autor) para
         * que los reintentos P2P reemplacen en vez de duplicar.
         *
         * Antes de crear el índice se eliminan posibles filas repetidas de la
         * v1 (si existieran); de lo contrario CREATE UNIQUE fallaría.
         * No es destructiva: solo conserva la primera copia de cada grupo.
         */
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM respuestas WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM respuestas " +
                        "GROUP BY sesionId, preguntaId, autor)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_respuestas_sesionId_preguntaId_autor` " +
                        "ON `respuestas` (`sesionId`, `preguntaId`, `autor`)",
                )
            }
        }
    }
}
