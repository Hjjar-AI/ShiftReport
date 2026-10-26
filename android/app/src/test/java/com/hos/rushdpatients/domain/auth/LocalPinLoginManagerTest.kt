package com.hos.rushdpatients.domain.auth

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.util.DispatcherProvider
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LocalPinLoginManagerTest {
    private val doctors = mockk<DoctorRepository>()
    private val settings = mockk<SettingsRepository>()
    private val hasher = mockk<PasswordHasher>()
    private val dispatchers = mockk<DispatcherProvider> { every { io } returns Dispatchers.Unconfined }
    private val manager = LocalPinLoginManager(doctors, settings, hasher, dispatchers)
    private val doctor = Fixtures.doctor(telegramId = 123).copy(extraOptions = setOf("pin:synthetic-hash"))
    private val key = "local_pin_login:${doctor.id}"
    // Synthetic input; the hasher is mocked, no enrolled PIN or credential is used.
    private val pin = "2468"
    @Before fun setup() {
        coEvery { doctors.getActiveById(doctor.id) } returns doctor
        coEvery { settings.get(key) } returns null
        coEvery { settings.put(key, any()) } just Runs
        coEvery { settings.delete(key) } just Runs
        every { hasher.verify(pin, "synthetic-hash") } returns true
    }
    @Test fun `invalid pin formats never read identity or consume attempts`() = runBlocking<Unit> {
        listOf("", "123", "123456789", "12a4", " 2468").forEach {
            assertThrows(LocalPinException::class.java) { runBlocking { manager.verify(doctor.id, it) } }
        }
        coVerify(exactly = 0) { doctors.getActiveById(any()) }
        coVerify(exactly = 0) { settings.put(any(), any()) }
    }
    @Test fun `missing deleted unenrolled or unlinked identities cannot log in`() = runBlocking<Unit> {
        coEvery { doctors.getActiveById(doctor.id) } returns null
        assertThrows(IllegalStateException::class.java) { runBlocking { manager.verify(doctor.id, pin) } }
        for (invalid in listOf(doctor.copy(extraOptions = emptySet()), doctor.copy(telegramId = null))) {
            coEvery { doctors.getActiveById(doctor.id) } returns invalid
            assertThrows(Exception::class.java) { runBlocking { manager.verify(doctor.id, pin) } }
        }
        verify(exactly = 0) { hasher.verify(any(), any()) }
    }
    @Test fun `successful login counts before hashing then returns live privileges`() = runBlocking<Unit> {
        val live = doctor.copy(rank = 1)
        coEvery { doctors.getActiveById(doctor.id) } returnsMany listOf(doctor, live)
        assertEquals(live, manager.verify(doctor.id, pin))
        coVerifyOrder {
            settings.put(key, "1:0:0")
            hasher.verify(pin, "synthetic-hash")
            doctors.getActiveById(doctor.id)
            settings.delete(key)
        }
    }
    @Test fun `failed login retains incremented counter`() = runBlocking<Unit> {
        coEvery { settings.get(key) } returns "2:0"
        every { hasher.verify(pin, any()) } returns false
        assertThrows(LocalPinException::class.java) { runBlocking { manager.verify(doctor.id, pin) } }
        coVerify { settings.put(key, "3:0:0") }
        coVerify(exactly = 0) { settings.delete(key) }
    }
    @Test fun `threshold failure persists a future lockout`() = runBlocking<Unit> {
        coEvery { settings.get(key) } returns "${AppConstants.LOGIN_MAX_ATTEMPTS - 1}:0"
        every { hasher.verify(pin, any()) } returns false
        val before = System.currentTimeMillis()
        assertThrows(LocalPinException::class.java) { runBlocking { manager.verify(doctor.id, pin) } }
        coVerify { settings.put(key, match {
            val parts = it.split(':')
            parts.size == 3 && parts[2] == "1" &&
                parts[0].toInt() == AppConstants.LOGIN_MAX_ATTEMPTS &&
                parts[1].toLong() in (before + 300_000)..(System.currentTimeMillis() + 300_000)
        }) }
        coVerify(exactly = 0) { settings.delete(key) }
    }
    @Test fun `active lockout prevents hashing and further counter changes`() = runBlocking<Unit> {
        coEvery { settings.get(key) } returns "5:${Long.MAX_VALUE}"
        assertThrows(LocalPinException::class.java) { runBlocking { manager.verify(doctor.id, pin) } }
        verify(exactly = 0) { hasher.verify(any(), any()) }
        coVerify(exactly = 0) { settings.put(any(), any()) }
    }
    @Test fun `expired lockout restarts attempts at one`() = runBlocking<Unit> {
        coEvery { settings.get(key) } returns "5:1"
        manager.verify(doctor.id, pin)
        coVerify { settings.put(key, "1:0:0") }
    }
    @Test fun `changed account pin or removed doctor after hashing cannot start login`() = runBlocking<Unit> {
        for (changed in listOf(null, doctor.copy(telegramId = 456), doctor.copy(extraOptions = setOf("pin:changed")))) {
            coEvery { doctors.getActiveById(doctor.id) } returnsMany listOf(doctor, changed)
            assertThrows(Exception::class.java) { runBlocking { manager.verify(doctor.id, pin) } }
        }
        coVerify(exactly = 0) { settings.delete(key) }
    }
    @Test fun `cancellation during hashing keeps the attempt and propagates`() = runBlocking<Unit> {
        val cancellation = CancellationException("cancelled")
        every { hasher.verify(pin, any()) } throws cancellation
        val caught = assertThrows(CancellationException::class.java) { runBlocking { manager.verify(doctor.id, pin) } }
        assertSame(cancellation, caught)
        coVerify { settings.put(key, "1:0:0") }
        coVerify(exactly = 0) { settings.delete(key) }
    }
    @Test fun `malformed attempt state fails closed before hashing`() = runBlocking<Unit> {
        listOf("broken", "bad:0", "1:bad", "1:0:extra").forEach {
            coEvery { settings.get(key) } returns it
            assertThrows(Exception::class.java) { runBlocking { manager.verify(doctor.id, pin) } }
        }
        verify(exactly = 0) { hasher.verify(any(), any()) }
    }
}
