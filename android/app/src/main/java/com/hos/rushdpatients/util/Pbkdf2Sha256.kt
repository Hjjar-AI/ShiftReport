package com.hos.rushdpatients.util

import com.hos.rushdpatients.config.AppConstants
import java.nio.CharBuffer
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Same PBKDF2-HMAC-SHA256 bytes on API 23–25, where the key factory is unavailable. */
internal object Pbkdf2Sha256 {
    fun derive(password: CharArray, salt: ByteArray, iterations: Int, keyLengthBits: Int): ByteArray {
        require(iterations > 0 && salt.isNotEmpty() && keyLengthBits == 256)
        val factory = try {
            SecretKeyFactory.getInstance(AppConstants.PBKDF2_ALGORITHM)
        } catch (_: NoSuchAlgorithmException) {
            return deriveWithMac(password, salt, iterations)
        }
        val spec = PBEKeySpec(password, salt, iterations, keyLengthBits)
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun deriveWithMac(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        // Android's SHA256 PBE factory uses UTF-8, including Arabic and supplementary characters.
        // Encode directly from chars so no immutable password String is created here.
        val encoded = Charsets.UTF_8.encode(CharBuffer.wrap(password))
        val passwordBytes = ByteArray(encoded.remaining())
        encoded.get(passwordBytes)
        if (encoded.hasArray()) encoded.array().fill(0)
        var u = ByteArray(0)
        val result = ByteArray(32)
        try {
            val mac = Mac.getInstance("HmacSHA256")
            // HMAC pads both an empty key and a one-zero-byte key to the same block.
            mac.init(SecretKeySpec(if (passwordBytes.isEmpty()) byteArrayOf(0) else passwordBytes, "HmacSHA256"))
            // A 256-bit derived key needs only block 1: U1 = PRF(password, salt || INT_32_BE(1)).
            mac.update(salt)
            u = mac.doFinal(byteArrayOf(0, 0, 0, 1))
            u.copyInto(result)
            repeat(iterations - 1) {
                val next = mac.doFinal(u)
                u.fill(0)
                u = next
                for (index in result.indices) {
                    result[index] = (result[index].toInt() xor u[index].toInt()).toByte()
                }
            }
            return result
        } catch (e: Exception) {
            result.fill(0)
            throw e
        } finally {
            u.fill(0)
            passwordBytes.fill(0)
        }
    }
}
