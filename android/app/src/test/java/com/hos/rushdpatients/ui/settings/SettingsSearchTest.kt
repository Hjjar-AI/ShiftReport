package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SettingsSearchTest {
    @Test fun `nonadmin never discovers project management or reset sections`() {
        val available = availableSettingsSections(false, false)
        assertTrue(available.isNotEmpty())
        assertTrue(available.none { it.adminOnly })
        assertFalse(SettingsSectionId.RESET in available)
    }
    @Test fun `reset is restricted to permanent admin while ordinary admin sees management`() {
        val ordinary = availableSettingsSections(true, false)
        assertFalse(SettingsSectionId.RESET in ordinary)
        assertTrue(ordinary.any { it.adminOnly })
        assertEquals(SettingsSectionId.entries.toList(), availableSettingsSections(true, true))
    }
}
