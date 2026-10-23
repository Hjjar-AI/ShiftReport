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
}
