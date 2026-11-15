package com.hos.rushdpatients.domain.auth

import android.util.Base64
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.util.Pbkdf2Sha256
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordHasher @Inject constructor() {

    private val random = SecureRandom()

    fun hash(password: String): String {
        val salt = ByteArray(AppConstants.SALT_LENGTH_BYTES).also { random.nextBytes(it) }
        val hash = pbkdf2(password, salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verify(password: String, stored: String?): Boolean {
        if (stored.isNullOrBlank()) return false
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = runCatching { Base64.decode(parts[0], Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(parts[1], Base64.NO_WRAP) }.getOrNull() ?: return false
        val actual = pbkdf2(password, salt)
        return constantTimeEquals(expected, actual)
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val chars = password.toCharArray()
        return try {
            Pbkdf2Sha256.derive(chars, salt, AppConstants.PBKDF2_ITERATIONS, AppConstants.PBKDF2_KEY_LENGTH_BITS)
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }
}
