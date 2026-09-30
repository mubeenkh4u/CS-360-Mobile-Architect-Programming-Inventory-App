package com.auwire.iamkhata.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Canonical local source of truth.
 *
 * Destructive migration is intentionally not enabled. Every future schema
 * version must provide an explicit migration.
 */
@Database(
    entities = [
        DatasetEntity::class,
        ColumnEntity::class,
        RowEntity::class,
        CellEntity::class,
        AuditEventEntity::class,
        TransformEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class IamDatabase : RoomDatabase() {
    abstract fun datasetDao(): DatasetDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun auditDao(): AuditDao

    companion object {
        fun create(context: Context): IamDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                IamDatabase::class.java,
                "iam_khata.db",
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
