package com.hos.rushdpatients.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Android provider/fallback on both selected APIs; no provider replacement or storage. */
@RunWith(AndroidJUnit4::class)
class Pbkdf2CompatibilityTest {
    private val salt = ByteArray(16) { it.toByte() }
    private fun derive(password: String, rounds: Int = 17) = Pbkdf2Sha256.derive(password.toCharArray(), salt, rounds, 256)
    private fun vector(rounds: Int, expected: String) {
        val bytes = Pbkdf2Sha256.derive("password".toCharArray(), "salt".toByteArray(), rounds, 256)
        assertEquals(expected, bytes.joinToString("") { "%02x".format(it.toInt() and 255) })
    }
    @Test fun sha256VectorOneIteration() {
        vector(1, "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b")
    }
    @Test fun sha256VectorTwoIterations() {
        vector(2, "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43")
    }
    @Test fun sha256Vector4096Iterations() {
        vector(4096, "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a")
    }
    @Test fun zeroIterationsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { derive("synthetic", 0) }
    }
    @Test fun negativeIterationsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { derive("synthetic", -1) }
    }
    @Test fun emptySaltIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Pbkdf2Sha256.derive("synthetic".toCharArray(), byteArrayOf(), 1, 256) }
    }
    @Test fun unsupportedKeyLengthIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Pbkdf2Sha256.derive("synthetic".toCharArray(), salt, 1, 128) }
    }
    @Test fun suppliedPasswordAndSaltRemainOwnedByCaller() {
        val chars = "عربي-🔐".toCharArray(); val copy = chars.copyOf(); val originalSalt = salt.copyOf()
        Pbkdf2Sha256.derive(chars, salt, 17, 256)
        assertArrayEquals(copy, chars)
        assertArrayEquals(originalSalt, salt)
    }
    @Test fun returnedKeysDoNotShareMutableStorage() {
        val first = derive("synthetic"); val second = derive("synthetic"); val saved = second.copyOf()
        assertNotSame(first, second)
        first.fill(0)
        assertArrayEquals(saved, second)
    }
    @Test fun embeddedNulIsNotSilentlyDiscarded() {
        assertFalse(derive("a\u0000b").contentEquals(derive("ab")))
    }
    @Test fun unicodeDerivedKeyAuthenticatesCiphertextAndRejectsWrongPassword() {
        val plain = "تقرير تجريبي ☕".toByteArray()
        val iv = ByteArray(12) { (it + 20).toByte() }
        fun crypt(mode: Int, password: String, input: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode, SecretKeySpec(derive(password), "AES"), GCMParameterSpec(128, iv)); doFinal(input)
        }
        val encrypted = crypt(Cipher.ENCRYPT_MODE, "عربي-🔐", plain)
        assertArrayEquals(plain, crypt(Cipher.DECRYPT_MODE, "عربي-🔐", encrypted))
        assertThrows(AEADBadTagException::class.java) { crypt(Cipher.DECRYPT_MODE, "synthetic wrong password", encrypted) }
    }
}
