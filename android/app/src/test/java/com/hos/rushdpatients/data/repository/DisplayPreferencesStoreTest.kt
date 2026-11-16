package com.hos.rushdpatients.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.hos.rushdpatients.ui.theme.*
import io.mockk.*
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real store/mutex, synthetic preferences only; no database, keystore or phone storage. */
class DisplayPreferencesStoreTest {
    private val context = mockk<Context>()
    private val file = mockk<SharedPreferences>()
    private val disk = mutableMapOf<String, Any>()
    private var commitSucceeds = true
    private var readFails = false
    private val commits = AtomicInteger()
    private val selected = ThemePreferences(AppThemePreset.COASTAL, AppAppearance.AMOLED,
        AppFontScale.EXTRA_LARGE, UiPreferences(UiDensity.RELAXED, UiControlSize.STANDARD, true))
    private lateinit var store: DisplayPreferencesStore

    @Before fun setup() {
        every { context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE) } returns file
        every { file.getBoolean(any(), any()) } answers {
            if (readFails) throw IOException("synthetic read failure")
            disk[firstArg<String>()] as? Boolean ?: secondArg<Boolean>()
        }
        every { file.getString(any(), any()) } answers {
            disk[firstArg<String>()] as? String ?: secondArg<String?>()
        }
        every { file.edit() } answers {
            val pending = mutableMapOf<String, Any>()
            mockk<SharedPreferences.Editor>().also { editor ->
                every { editor.putString(any(), any()) } answers {
                    pending[firstArg<String>()] = requireNotNull(secondArg<String?>()); editor
                }
                every { editor.putBoolean(any(), any()) } answers {
                    pending[firstArg<String>()] = secondArg<Boolean>(); editor
                }
                every { editor.commit() } answers {
                    commits.incrementAndGet()
                    if (commitSucceeds) disk.putAll(pending)
                    commitSucceeds
                }
            }
        }
        store = DisplayPreferencesStore(context)
    }

    @Test fun missingLocalFileWaitsForProtectedChoicesInsteadOfPublishingDefaults() = runBlocking {
        store.load()
        assertNull(store.state.value.preferences)
        assertTrue(store.state.value.needsInitialization)
        assertFalse(store.state.value.loadFailed)
        verify(exactly = 0) { file.edit() }
        store.initialize { selected }
        assertEquals(ThemeUiState(preferences = selected), store.state.value)
    }

    @Test fun firstCopyPersistsOnlyCosmeticsAndReloadsWithoutProtectedAccess() = runBlocking {
        store.initialize { selected }
        assertEquals(setOf("accent", "appearance", "font_scale", "ui_preferences", "initialized"), disk.keys)
        assertEquals(selected.preset.name, disk["accent"])
        assertEquals(selected.appearance.name, disk["appearance"])
        assertEquals(selected.fontScale.name, disk["font_scale"])
        assertEquals(selected.uiPreferences.encode(), disk["ui_preferences"])
        assertEquals(true, disk["initialized"])
        val nextLaunch = DisplayPreferencesStore(context)
        nextLaunch.load()
        assertEquals(selected, nextLaunch.get())
        assertFalse(nextLaunch.state.value.needsInitialization)
        nextLaunch.initialize { error("Initialized choices must not read protected settings again") }
        assertEquals(selected, nextLaunch.state.value.preferences)
        assertEquals(1, commits.get())
    }

    @Test fun repeatedLoadKeepsReadySnapshotInsteadOfShowingDefaultsOrLoading() = runBlocking {
        store.initialize { selected }
        readFails = true
        store.load()
        assertEquals(ThemeUiState(preferences = selected), store.state.value)
    }

    @Test fun readFailureIsRetryableAndDoesNotCreateDefaults() = runBlocking {
        readFails = true
        store.load()
        assertEquals(ThemeUiState(loadFailed = true), store.state.value)
        assertEquals(0, commits.get())
        readFails = false
        store.load()
        assertEquals(ThemeUiState(needsInitialization = true), store.state.value)
        store.initialize { selected }
        assertEquals(selected, store.get())
    }

    @Test fun failedProtectedReadDoesNotMarkTheOneTimeCopyComplete() = runBlocking {
        store.initialize { throw IOException("synthetic protected read failure") }
        assertEquals(ThemeUiState(loadFailed = true), store.state.value)
        assertTrue(disk.isEmpty())
        assertEquals(0, commits.get())
        store.initialize { selected }
        assertEquals(selected, store.get())
        assertEquals(1, commits.get())
    }

    @Test fun failedFirstCommitDoesNotPublishChoicesAndCanRetry() = runBlocking {
        commitSucceeds = false
        store.initialize { selected }
        assertEquals(ThemeUiState(loadFailed = true), store.state.value)
        commitSucceeds = true
        store.initialize { selected }
        assertEquals(ThemeUiState(preferences = selected), store.state.value)
        assertEquals(2, commits.get())
    }

    @Test fun failedUpdateKeepsPublishedSelectionAndRetryPreservesOtherFields() = runBlocking {
        store.initialize { selected }
        commitSucceeds = false
        val failure = runCatching { store.update { it.copy(appearance = AppAppearance.LIGHT) } }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(ThemeUiState(preferences = selected), store.state.value)
        assertEquals(selected, store.get())
        commitSucceeds = true
        store.update { it.copy(appearance = AppAppearance.LIGHT) }
        val expected = selected.copy(appearance = AppAppearance.LIGHT)
        assertEquals(expected, store.get())
        assertEquals(expected, DisplayPreferencesStore(context).get())
    }

    @Test fun simultaneousInitializersCopyProtectedChoicesOnlyOnce() = runBlocking {
        withTimeout(5000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val reads = AtomicInteger()
            val first = async {
                store.initialize { reads.incrementAndGet(); entered.complete(Unit); release.await(); selected }
            }
            entered.await()
            val second = async {
                store.initialize { reads.incrementAndGet(); ThemePreferences() }
            }
            try {
                release.complete(Unit)
                awaitAll(first, second)
                assertEquals(1, reads.get())
                assertEquals(1, commits.get())
                assertEquals(selected, store.get())
            } finally { release.complete(Unit) }
        }
    }

    @Test fun concurrentChangesToDifferentFieldsKeepBothSelectionsOnDisk() = runBlocking {
        store.initialize { ThemePreferences() }
        withTimeout(5000) {
            val start = CompletableDeferred<Unit>()
            val changes = listOf(
                async(Dispatchers.Default) { start.await(); store.update { it.copy(appearance = selected.appearance) } },
                async(Dispatchers.Default) { start.await(); store.update { it.copy(preset = selected.preset) } },
                async(Dispatchers.Default) { start.await(); store.update { it.copy(fontScale = selected.fontScale) } },
                async(Dispatchers.Default) { start.await(); store.update { it.copy(uiPreferences = selected.uiPreferences) } }
            )
            start.complete(Unit)
            changes.awaitAll()
        }
        assertEquals(selected, store.get())
        assertEquals(selected, DisplayPreferencesStore(context).get())
        assertEquals(5, commits.get())
    }

    @Test fun cancelledProtectedCopyPropagatesWithoutErrorStateOrWrite() = runBlocking {
        store.load()
        val before = store.state.value
        val failure = runCatching { store.initialize { throw CancellationException("synthetic cancel") } }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(before, store.state.value)
        assertEquals(0, commits.get())
        store.initialize { selected }
        assertEquals(selected, store.get())
    }

    @Test fun readAndUpdateBeforeInitializationCannotPersistImplicitDefaults() = runBlocking {
        assertTrue(runCatching { store.get() }.exceptionOrNull() is IllegalStateException)
        var transformed = false
        assertTrue(runCatching { store.update { transformed = true; it } }.exceptionOrNull() is IllegalStateException)
        assertFalse(transformed)
        assertEquals(0, commits.get())
        assertNull(store.state.value.preferences)
    }
}
