package com.hos.rushdpatients.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class Pbkdf2Sha256Test {
    private val salt = ByteArray(16) { it.toByte() }

    private fun fallback(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val method = Pbkdf2Sha256::class.java.getDeclaredMethod(
            "deriveWithMac", CharArray::class.java, ByteArray::class.java, Int::class.javaPrimitiveType
        ).apply { isAccessible = true }
        return method.invoke(Pbkdf2Sha256, password.toCharArray(), salt, iterations) as ByteArray
    }

    private fun existing(password: String, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
    }

    @Test fun knownSha256Vectors() {
        val vectors = listOf(
            1 to "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            2 to "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            4096 to "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a"
        )
        for ((iterations, expected) in vectors) {
            val actual = fallback("password", "salt".toByteArray(), iterations)
            assertEquals(expected, actual.joinToString("") { "%02x".format(it.toInt() and 255) })
        }
    }

    @Test fun unicodeAndLongPasswordsMatchExistingAtBothStoredWorkFactors() {
        for (password in listOf("123456", "عبارة مرور تجريبية", "mixed-عربي-🔐", "a".repeat(100), "nul\u0000password")) {
            for (iterations in listOf(120_000, 210_000)) {
                val expected = existing(password, iterations)
                assertArrayEquals(expected, fallback(password, salt, iterations))
                assertArrayEquals(expected, Pbkdf2Sha256.derive(password.toCharArray(), salt, iterations, 256))
            }
        }
    }

    @Test fun existingEncryptedContentOpensWithFallbackAndViceVersa() {
        val password = "كلمة مرور تجريبية 🔐"
        val originalKey = SecretKeySpec(existing(password, 210_000), "AES")
        val fallbackKey = SecretKeySpec(fallback(password, salt, 210_000), "AES")
        val plain = "dummy connection or backup content".toByteArray()
        val iv = ByteArray(12) { (it + 16).toByte() }
        fun crypt(mode: Int, key: SecretKeySpec, data: ByteArray): ByteArray =
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, key, GCMParameterSpec(128, iv))
                doFinal(data)
            }
        val encrypted = crypt(Cipher.ENCRYPT_MODE, originalKey, plain)
        assertArrayEquals(plain, crypt(Cipher.DECRYPT_MODE, fallbackKey, encrypted))
        assertArrayEquals(encrypted, crypt(Cipher.ENCRYPT_MODE, fallbackKey, plain))
        val wrongKey = SecretKeySpec(fallback("wrong password", salt, 210_000), "AES")
        assertThrows(AEADBadTagException::class.java) { crypt(Cipher.DECRYPT_MODE, wrongKey, encrypted) }
        val damaged = encrypted.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThrows(AEADBadTagException::class.java) { crypt(Cipher.DECRYPT_MODE, fallbackKey, damaged) }
    }

    @Test fun missingKeyFactoryUsesFallback() {
        // Remove only providers of this factory, then restore them at their original positions.
        val providers = java.security.Security.getProviders().toList()
        try {
            providers.filter { it.getService("SecretKeyFactory", "PBKDF2WithHmacSHA256") != null }
                .forEach { java.security.Security.removeProvider(it.name) }
            val expected = fallback("dummy password", salt, 2)
            assertArrayEquals(expected, Pbkdf2Sha256.derive("dummy password".toCharArray(), salt, 2, 256))
        } finally {
            providers.forEachIndexed { index, provider ->
                java.security.Security.insertProviderAt(provider, index + 1)
            }
        }
    }
}
