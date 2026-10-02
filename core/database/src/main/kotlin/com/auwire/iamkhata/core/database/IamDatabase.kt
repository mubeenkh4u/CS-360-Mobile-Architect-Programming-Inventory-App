package com.auwire.iamkhata.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        ProductEntity::class,
        StockDocumentEntity::class,
        StockDocumentLineEntity::class,
        StockMovementEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class IamDatabase : RoomDatabase() {
    abstract fun datasetDao(): DatasetDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun auditDao(): AuditDao
    abstract fun inventoryDao(): InventoryDao

    companion object {
        /**
         * Adds the inventory journal without altering existing ledger/workbench
         * tables, preserving every v1 dataset, row and audit event.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS inventory_products (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sku TEXT NOT NULL,
                        name TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        reorderLevelMicros INTEGER NOT NULL,
                        defaultSaleRateMinor INTEGER,
                        defaultPurchaseRateMinor INTEGER,
                        active INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_products_sku ON inventory_products(sku)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_products_name ON inventory_products(name)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_products_active ON inventory_products(active)")

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stock_documents (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        kind TEXT NOT NULL,
                        status TEXT NOT NULL,
                        party TEXT NOT NULL,
                        reference TEXT NOT NULL,
                        effectiveAt INTEGER NOT NULL,
                        totalMinor INTEGER NOT NULL,
                        amountPaidMinor INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_documents_status ON stock_documents(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_documents_kind ON stock_documents(kind)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_documents_effectiveAt ON stock_documents(effectiveAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_documents_reference ON stock_documents(reference)")

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stock_document_lines (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        documentId INTEGER NOT NULL,
                        productId INTEGER NOT NULL,
                        quantityMicros INTEGER NOT NULL,
                        unitRateMinor INTEGER NOT NULL,
                        lineTotalMinor INTEGER NOT NULL,
                        FOREIGN KEY(documentId) REFERENCES stock_documents(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(productId) REFERENCES inventory_products(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_document_lines_documentId ON stock_document_lines(documentId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_document_lines_productId ON stock_document_lines(productId)")

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stock_movements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        productId INTEGER NOT NULL,
                        documentId INTEGER,
                        kind TEXT NOT NULL,
                        quantityDeltaMicros INTEGER NOT NULL,
                        effectiveAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        FOREIGN KEY(productId) REFERENCES inventory_products(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(documentId) REFERENCES stock_documents(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_productId ON stock_movements(productId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_documentId ON stock_movements(documentId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_effectiveAt ON stock_movements(effectiveAt)")
            }
        }

        /**
         * Adds editable-row lifecycle metadata.
         *
         * Existing rows remain FINAL. Rows already posted by inventory are
         * detected by their semantic STOCK_STATUS cell and locked to preserve
         * inventory ↔ Khata consistency.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE data_rows ADD COLUMN status TEXT NOT NULL DEFAULT 'FINAL'")
                database.execSQL("ALTER TABLE data_rows ADD COLUMN origin TEXT NOT NULL DEFAULT 'MANUAL'")
                database.execSQL("ALTER TABLE data_rows ADD COLUMN originRef TEXT")
                database.execSQL("ALTER TABLE data_rows ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_data_rows_datasetId_status ON data_rows(datasetId, status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_data_rows_isLocked ON data_rows(isLocked)")

                database.execSQL(
                    """
                    UPDATE data_rows
                    SET origin = 'STOCK_DOCUMENT',
                        isLocked = 1,
                        originRef = (
                            SELECT ref.rawValue
                            FROM cells ref
                            JOIN dataset_columns refCol ON refCol.id = ref.columnId
                            WHERE ref.rowId = data_rows.id
                              AND refCol.role = 'REFERENCE'
                            LIMIT 1
                        )
                    WHERE id IN (
                        SELECT statusCell.rowId
                        FROM cells statusCell
                        JOIN dataset_columns statusCol ON statusCol.id = statusCell.columnId
                        WHERE statusCol.role = 'STOCK_STATUS'
                          AND UPPER(statusCell.rawValue) = 'COMMITTED'
                    )
                    """.trimIndent(),
                )
            }
        }

        fun create(context: Context): IamDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                IamDatabase::class.java,
                "iam_khata.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
