package com.justwen.androidnga.base.network.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Base64

class NgaLoginPasswordCipherTest {
    @Test
    fun outputHasExpectedShapeAndRandomPadding() {
        val first = NgaLoginPasswordCipher.encrypt("example-input")
        val second = NgaLoginPasswordCipher.encrypt("example-input")

        assertEquals(256, Base64.getDecoder().decode(first).size)
        assertEquals(256, Base64.getDecoder().decode(second).size)
        assertNotEquals(first, second)
    }

    @Test
    fun publicKeyFingerprintIsPinned() {
        assertEquals(
            "1d49cb2093d1577917a576910b23dea5c51053f47771696930a5a79acb5fe3cc",
            NgaLoginPasswordCipher.publicKeyFingerprint(),
        )
    }
}
