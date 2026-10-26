package com.hos.rushdpatients.testutil

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.repository.*
import com.hos.rushdpatients.domain.auth.*
import com.hos.rushdpatients.domain.report.ReportDeliveryLedger
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import java.time.Instant

abstract class DeliveryFixture(
    protected val actor: Doctor = Fixtures.doctor(telegramId = 123, rank = 1)
) {
    protected val settings = mockk<SettingsRepository>()
    protected val sessions = mockk<SessionManager>()
    protected val doctors = mockk<DoctorRepository>()
    protected val raw = MutableStateFlow<String?>(null)
    protected fun ledger() = ReportDeliveryLedger(settings, sessions, doctors)
    protected fun acceptLedgerWrites() {
        coEvery { settings.put("report_delivery_ledger", any()) } answers { raw.value = secondArg(); Unit }
    }
    @Before fun openLedger() {
        coEvery { settings.get("report_delivery_ledger") } answers { raw.value }
        acceptLedgerWrites()
        every { settings.observe("report_delivery_ledger") } returns raw
        every { sessions.current() } returns Session(actor.id, actor.fullName, actor.telegramId, actor.role, Instant.EPOCH)
        coEvery { doctors.getActiveById(actor.id) } returns actor
    }
}
