package com.hos.rushdpatients.domain.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class PasswordHasherTest {
    @Test fun randomSaltRetainsVerificationAndRejectsWrongPassword() {
        val hasher = PasswordHasher()
        val synthetic = "synthetic-test-passphrase"
        val first = hasher.hash(synthetic)
        val second = hasher.hash(synthetic)
        assertNotEquals(first, second)
        assertTrue(hasher.verify(synthetic, first))
        assertFalse(hasher.verify("different", first))
        listOf(null, "", "malformed").forEach { assertFalse(hasher.verify(synthetic, it)) }
    }
    @Test fun arabicAndSupplementaryPasswordRetainsVerification() {
        val hasher = PasswordHasher(); val password = "عبارة تجريبية 🔐"
        val stored = hasher.hash(password)
        assertTrue(hasher.verify(password, stored))
        assertFalse(hasher.verify("عبارة تجريبية", stored))
    }
    @Test fun surroundingWhitespaceRemainsSignificant() {
        val hasher = PasswordHasher(); val stored = hasher.hash(" synthetic ")
        assertTrue(hasher.verify(" synthetic ", stored))
        assertFalse(hasher.verify("synthetic", stored))
    }
    @Test fun changedDerivedHashCannotAuthenticateOriginalPassword() {
        val hasher = PasswordHasher(); val stored = hasher.hash("synthetic")
        val parts = stored.split(':')
        val derived = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
        derived[0] = (derived[0].toInt() xor 1).toByte()
        val damaged = parts[0] + ":" + android.util.Base64.encodeToString(derived, android.util.Base64.NO_WRAP)
        assertFalse(hasher.verify("synthetic", damaged))
    }
    @Test fun unexpectedStoredComponentCountIsRejected() {
        val hasher = PasswordHasher()
        assertFalse(hasher.verify("synthetic", "one:two:three"))
        assertFalse(hasher.verify("synthetic", "one"))
    }
    @Test fun storedSaltAndHashHaveCurrentCryptographicLengths() {
        val stored = PasswordHasher().hash("synthetic").split(':')
        assertEquals(2, stored.size)
        assertEquals(com.hos.rushdpatients.config.AppConstants.SALT_LENGTH_BYTES,
            android.util.Base64.decode(stored[0], android.util.Base64.NO_WRAP).size)
        assertEquals(com.hos.rushdpatients.config.AppConstants.PBKDF2_KEY_LENGTH_BITS / 8,
            android.util.Base64.decode(stored[1], android.util.Base64.NO_WRAP).size)
    }
}
