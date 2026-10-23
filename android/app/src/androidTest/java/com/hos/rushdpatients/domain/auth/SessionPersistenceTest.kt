package com.hos.rushdpatients.domain.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.data.model.Role
import com.hos.rushdpatients.testutil.IsolatedStorageContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SessionPersistenceTest {
    private val context = IsolatedStorageContext()
    @After fun cleanup() = context.cleanUp()
    @Test fun encryptedSessionSurvivesRecreationAndUnlockUpdatesTimestamp() {
        val session = Session("synthetic-doctor", "أرز", 123, Role.ADMIN, Instant.ofEpochMilli(1000))
        SessionManager(context).start(session)
        val restored = SessionManager(context)
        assertEquals(session, restored.current())
        restored.markUnlocked()
        assertTrue(requireNotNull(restored.current()).unlockedAt > session.unlockedAt)
        val unlocked = requireNotNull(restored.current())
        val reopened = requireNotNull(SessionManager(context).current())
        assertEquals(unlocked.doctorId, reopened.doctorId)
        assertEquals(unlocked.role, reopened.role)
        assertEquals(unlocked.unlockedAt.toEpochMilli(), reopened.unlockedAt.toEpochMilli())
    }
    @Test fun clearRemovesSessionWithoutChangingOtherNamespacedPreferences() {
        val projects = com.hos.rushdpatients.config.ProjectConfigStore(context)
        projects.saveDraft(com.hos.rushdpatients.config.ProjectConfig(hospitalName = "تجريبي"))
        val sessions = SessionManager(context)
        sessions.start(Session("synthetic", "أرز", null, Role.NON_ADMIN, Instant.now()))
        sessions.clear()
        assertNull(SessionManager(context).current())
        assertEquals("تجريبي", com.hos.rushdpatients.config.ProjectConfigStore(context).current().hospitalName)
    }
}
