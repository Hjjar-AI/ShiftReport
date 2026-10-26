package com.hos.rushdpatients.testutil

import java.io.File
import java.util.UUID

/** Namespaces file staging and encrypted preferences; never clears production caches. */
class IsolatedStorageContext : IsolatedPreferencesContext() {
    private val root = File(super.getCacheDir(), "instrumentation_storage_${UUID.randomUUID()}").apply { mkdirs() }
    override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
    override fun getFilesDir() = File(root, "files").apply { mkdirs() }
    override fun getNoBackupFilesDir() = File(root, "no_backup").apply { mkdirs() }
    override fun getDatabasePath(name: String) = (if (File(name).isAbsolute) File(name) else File(root, "databases/$name")).apply { parentFile?.mkdirs() }
    fun cleanUp() { clearTestPreferences(); root.deleteRecursively() }
}
