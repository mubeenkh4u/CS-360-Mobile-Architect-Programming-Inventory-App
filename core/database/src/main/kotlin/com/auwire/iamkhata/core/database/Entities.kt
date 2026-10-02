package com.auwire.iamkhata.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Top-level logical table. */
@Entity(tableName = "datasets")
data class DatasetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Runtime schema metadata for one dataset. */
@Entity(
    tableName = "dataset_columns",
    foreignKeys = [
        ForeignKey(
            entity = DatasetEntity::class,
            parentColumns = ["id"],
            childColumns = ["datasetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("datasetId"),
        Index(value = ["datasetId", "key"], unique = true),
        Index(value = ["datasetId", "position"]),
    ],
)
data class ColumnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val datasetId: Long,
    val key: String,
    val displayName: String,
    val type: String,
    val role: String,
    val position: Int,
    val required: Boolean,
    val isProtected: Boolean,
)

/** Stable row identity; values are stored separately as typed cells. */
@Entity(
    tableName = "data_rows",
    foreignKeys = [
        ForeignKey(
            entity = DatasetEntity::class,
            parentColumns = ["id"],
            childColumns = ["datasetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("datasetId"),
        Index(value = ["datasetId", "createdAt"]),
    ],
)
data class RowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val datasetId: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long = 1,
)

/**
 * Flexible cell storage with typed projections.
 *
 * rawValue preserves source fidelity. Projection columns keep analytical
 * operations typed and avoid reparsing large datasets on every query.
 */
@Entity(
    tableName = "cells",
    primaryKeys = ["rowId", "columnId"],
    foreignKeys = [
        ForeignKey(
            entity = RowEntity::class,
            parentColumns = ["id"],
            childColumns = ["rowId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ColumnEntity::class,
            parentColumns = ["id"],
            childColumns = ["columnId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("rowId"),
        Index("columnId"),
        Index(value = ["columnId", "normalizedValue"]),
        Index(value = ["columnId", "numericValue"]),
        Index(value = ["columnId", "instantValue"]),
    ],
)
data class CellEntity(
    val rowId: Long,
    val columnId: Long,
    val rawValue: String,
    val normalizedValue: String,
    val numericValue: Double?,
    val instantValue: Long?,
    val booleanValue: Boolean?,
)

/**
 * Append-only application audit event.
 *
 * Only mutation metadata and hashes are stored here; full business rows are not
 * duplicated into the audit log.
 */
@Entity(
    tableName = "audit_events",
    indices = [Index("createdAt"), Index(value = ["targetType", "targetId"])],
)
data class AuditEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val action: String,
    val targetType: String,
    val targetId: String,
    val payloadHash: String,
    val previousMac: String,
    val eventMac: String,
)

/** Metadata for a committed data-cleaning transformation. */
@Entity(
    tableName = "transform_events",
    foreignKeys = [
        ForeignKey(
            entity = DatasetEntity::class,
            parentColumns = ["id"],
            childColumns = ["datasetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("datasetId"), Index("createdAt")],
)
data class TransformEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val datasetId: Long,
    val columnId: Long,
    val operation: String,
    val affectedRows: Int,
    val createdAt: Long,
)
