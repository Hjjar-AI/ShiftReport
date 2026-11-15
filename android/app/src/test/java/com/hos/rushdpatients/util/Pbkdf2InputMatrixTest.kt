package com.hos.rushdpatients.util

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Independent JCE reference versus the fallback, including UTF-8 and HMAC block boundaries. */
@RunWith(Parameterized::class)
class Pbkdf2InputMatrixTest(private val caseName: String, private val password: String, private val rounds: Int) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): Collection<Array<Any>> = buildList {
            val inputs = listOf("ascii" to "synthetic", "arabic" to "عبارة تجريبية",
                "mixed" to "dummy-عربي-🔐", "empty" to "", "embedded-nul" to "a\u0000b",
                "whitespace" to " synthetic \t", "hmac-block" to "x".repeat(64),
                "over-hmac-block" to "ش".repeat(65))
            for ((name, password) in inputs) for (rounds in listOf(1, 2, 17, 4096))
                add(arrayOf<Any>("$name/$rounds", password, rounds))
        }
    }
    @Test fun fallbackAndPublicEntryMatchIndependentReferenceWithoutMutatingInputs() {
        val salt = ByteArray(16) { (it + 1).toByte() }
        val originalSalt = salt.copyOf()
        val chars = password.toCharArray()
        val originalChars = chars.copyOf()
        val spec = PBEKeySpec(chars, salt, rounds, 256)
        val expected = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        val method = Pbkdf2Sha256::class.java.getDeclaredMethod("deriveWithMac", CharArray::class.java,
            ByteArray::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
        val actual = method.invoke(Pbkdf2Sha256, chars, salt, rounds) as ByteArray
        assertArrayEquals(caseName, expected, actual)
        assertArrayEquals(caseName, expected, Pbkdf2Sha256.derive(chars, salt, rounds, 256))
        assertArrayEquals(caseName, originalChars, chars)
        assertArrayEquals(caseName, originalSalt, salt)
        assertEquals(caseName, 32, actual.size)
    }
}
