package com.auwire.iamkhata.core.model

/** Mutating cleaning operations intentionally kept small and explicit. */
enum class CleaningOperation {
    TRIM_WHITESPACE,
    LOWERCASE,
    UPPERCASE,
}

/** Result returned after a transactional transformation. */
data class TransformResult(
    val affectedRows: Int,
    val description: String,
)
