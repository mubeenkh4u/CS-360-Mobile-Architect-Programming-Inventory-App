package com.auwire.iamkhata.core.data

/** Signs audit-chain material without exposing key storage to repositories. */
fun interface IntegritySigner {
    fun sign(message: ByteArray): String
}
