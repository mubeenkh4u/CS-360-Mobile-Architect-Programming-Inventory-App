package com.auwire.iamkhata.core.model

/**
 * Runtime feature surface consumed by the workspace.
 *
 * The host application maps build/configuration flags into this immutable model,
 * allowing a feature to be disabled without changing repository contracts.
 */
data class WorkspaceFeatures(
    val cleaning: Boolean,
    val pivot: Boolean,
    val importExport: Boolean,
    val cloudSync: Boolean,
)
