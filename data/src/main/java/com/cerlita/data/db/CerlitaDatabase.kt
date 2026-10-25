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
        EvaluacionEntity::class,
        PerfilEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class CerlitaDatabase : RoomDatabase() {
    abstract fun preguntaDao(): PreguntaDao
    abstract fun respuestaDao(): RespuestaDao
    abstract fun rondaDao(): RondaDao
    abstract fun evaluacionDao(): EvaluacionDao
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

        /**
         * v2 -> v3: rondas con modo de juego (los 3 modos con juicio humano)
         * y tabla de evaluaciones (veredicto por pregunta de la ronda).
         *
         * Las rondas viejas quedan como AMBOS_RESPONDEN; su lista de modos
         * se reconstruye al leerlas (Ronda.modoDe tolera listas vacías).
         */
        val MIGRACION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE rondas ADD COLUMN modoJuego TEXT NOT NULL DEFAULT 'AMBOS_RESPONDEN'",
                )
                db.execSQL(
                    "ALTER TABLE rondas ADD COLUMN modosJson TEXT NOT NULL DEFAULT '[]'",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `evaluaciones` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sesionId` TEXT NOT NULL, " +
                        "`preguntaId` TEXT NOT NULL, " +
                        "`modoPregunta` TEXT NOT NULL, " +
                        "`veredicto` TEXT NOT NULL, " +
                        "`automatico` INTEGER NOT NULL, " +
                        "`fecha` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_evaluaciones_sesionId` " +
                        "ON `evaluaciones` (`sesionId`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_evaluaciones_sesionId_preguntaId` " +
                        "ON `evaluaciones` (`sesionId`, `preguntaId`)",
                )
            }
        }
    }
}
