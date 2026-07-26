package com.justwen.androidnga.base.network.login

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

internal object NgaLoginPasswordCipher {
    // Public login key observed in the NGA web login contract. The fingerprint is pinned in tests.
    private const val PUBLIC_KEY_BASE64 =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyKzZWDimCN1OCprqWUhF" +
            "UPhcwxDE62/BFVP6LtQHJu+65dm4YNmDvzitmcfaXW9YbhXnd4oP7j+6vpcgJQ+p" +
            "3ucySo1ZnqO0Bb2JKEtxpCmxe7IYXhFEkJqHpFYBTiAxQz2n2mX4JZy/ehBUSMjz" +
            "gzd0NdG6Ai1C42oCzYltUOjNWZUNHn1nqpElSWHnUWqkdN8+5ISP/ZMKiQdFANkE" +
            "qDGw3/34qyF+E/hVgrGF4/CcWNP/LJCdB6DYtx7VPlQZF0tP1s+q/++rC4rQ2wmV" +
            "l2V8zGh1j7ojZbt62hVjy6byK1E/2XYo97ZtL4KDW7F5jJMvSDRFR7901UR8hCdf" +
            "4wIDAQAB"

    private val encodedKey: ByteArray by lazy { Base64.getDecoder().decode(PUBLIC_KEY_BASE64) }
    private val publicKey: PublicKey by lazy {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(encodedKey))
    }

    fun encrypt(value: CharSequence): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8)))
    }

    fun publicKeyFingerprint(): String = MessageDigest.getInstance("SHA-256")
        .digest(encodedKey)
        .joinToString("") { "%02x".format(it) }
}
