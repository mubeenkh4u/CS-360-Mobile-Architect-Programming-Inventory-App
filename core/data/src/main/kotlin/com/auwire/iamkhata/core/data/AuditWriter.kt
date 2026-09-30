package com.auwire.iamkhata.core.data

import com.auwire.iamkhata.core.database.AuditDao
import com.auwire.iamkhata.core.database.AuditEventEntity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/**
 * Appends tamper-evident audit metadata.
 *
 * The payload is hashed before storage. The event MAC covers the previous MAC,
 * creating a chain that detects insertion, deletion and reordering while the
 * Keystore key remains protected.
 */
class AuditWriter(
    private val auditDao: AuditDao,
    private val signer: IntegritySigner,
) {
    suspend fun append(
        action: String,
        targetType: String,
        targetId: String,
        payloadSummary: String,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        val payloadHash = sha256(payloadSummary)
        val previousMac = auditDao.latestAuditEvent()?.eventMac.orEmpty()
        val canonical = listOf(
            timestamp.toString(),
            action,
            targetType,
            targetId,
            payloadHash,
            previousMac,
        ).joinToString("|")
        val eventMac = signer.sign(canonical.toByteArray(StandardCharsets.UTF_8))

        auditDao.insertAuditEvent(
            AuditEventEntity(
                createdAt = timestamp,
                action = action,
                targetType = targetType,
                targetId = targetId,
                payloadHash = payloadHash,
                previousMac = previousMac,
                eventMac = eventMac,
            ),
        )
    }

    private fun sha256(value: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(StandardCharsets.UTF_8)),
        )
}
