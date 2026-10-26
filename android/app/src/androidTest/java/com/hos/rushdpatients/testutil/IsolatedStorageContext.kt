package com.hos.rushdpatients.testutil

import java.io.File
import java.util.UUID

/** Namespaces file staging and encrypted preferences; never clears production caches. */
open class IsolatedStorageContext : IsolatedPreferencesContext() {
    private val root = File(super.getCacheDir(), "instrumentation_storage_${UUID.randomUUID()}").apply { mkdirs() }
    override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
    override fun getFilesDir() = File(root, "files").apply { mkdirs() }
    override fun getNoBackupFilesDir() = File(root, "no_backup").apply { mkdirs() }
    override fun getDatabasePath(name: String) = (if (File(name).isAbsolute) File(name) else File(root, "databases/$name")).apply { parentFile?.mkdirs() }
    fun cleanUp() { clearTestPreferences(); root.deleteRecursively() }
}

/** Provider roots must use the application cache; each renderer still owns unique preview files. */
class IsolatedPdfContext : IsolatedStorageContext() {
    override fun getCacheDir(): File = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
}
