package com.hos.rushdpatients.domain.export

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.testutil.IsolatedStorageContext
import com.hos.rushdpatients.util.DefaultDispatcherProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DocumentProviderFailureTest {
    private val context = IsolatedStorageContext()
    private val store = PendingExportStore(context, DefaultDispatcherProvider())
    private val destinations = mutableListOf<Uri>()
    private fun destination(fault: String = "none") = Uri.parse(
        "content://com.hos.rushdpatients.debug.faultdocuments/${UUID.randomUUID()}?fault=$fault"
    ).also(destinations::add)
    private val bytes = "synthetic encrypted export payload — شاي ☕".toByteArray()
    @After fun cleanup() {
        val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
        destinations.forEach { resolver.delete(it, null, null) }
        context.cleanUp()
    }
    private suspend fun rejected(fault: String) {
        val id = store.stage(bytes)
        try { store.write(destination(fault), id); fail("Provider fault accepted: $fault") }
        catch (_: Exception) { }
        // A provider failure must not destroy the encrypted recovery copy.
        val good = destination()
        store.write(good, id)
        assertArrayEquals(bytes, context.contentResolver.openInputStream(good)!!.use { it.readBytes() })
        store.discard(id)
    }
    @Test fun normalProviderWritesAndVerifiesExactBytes() = runBlocking<Unit> {
        val id = store.stage(bytes); val uri = destination()
        store.write(uri, id)
        assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        store.discard(id)
    }
    @Test fun revokedWritePermissionRetainsRecoverableStaging() = runBlocking<Unit> { rejected("denied") }
    @Test fun unavailableDestinationRetainsRecoverableStaging() = runBlocking<Unit> { rejected("unavailable") }
    @Test fun simulatedFullStorageRetainsRecoverableStaging() = runBlocking<Unit> { rejected("full") }
    @Test fun partialProviderWriteCannotReportSuccess() = runBlocking<Unit> { rejected("partial") }
    @Test fun corruptedReadbackCannotReportSuccess() = runBlocking<Unit> { rejected("corrupt") }
    @Test fun revokedReadbackPermissionCannotReportSuccess() = runBlocking<Unit> { rejected("read-denied") }
}
