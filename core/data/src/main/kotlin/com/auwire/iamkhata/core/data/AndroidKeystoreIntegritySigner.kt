package com.auwire.iamkhata.core.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * HMAC-SHA-256 signer whose secret key is generated and retained by Android
 * Keystore. The key is never serialized into application storage or source.
 */
class AndroidKeystoreIntegritySigner(
    private val alias: String = "iam_khata_audit_hmac_v1",
) : IntegritySigner {
    override fun sign(message: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(loadOrCreateKey())
        return Base64.encodeToString(mac.doFinal(message), Base64.NO_WRAP)
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            "AndroidKeyStore",
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build(),
        )
        return generator.generateKey()
    }
}
