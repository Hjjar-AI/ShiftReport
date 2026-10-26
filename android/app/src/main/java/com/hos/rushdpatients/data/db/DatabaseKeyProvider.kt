package com.hos.rushdpatients.data.db

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.SecureRandom
import java.nio.charset.StandardCharsets

internal class DatabaseKeyProvider(private val context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val preferences = EncryptedSharedPreferences.create(
        FILE_NAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getOrCreatePassphrase(): ByteArray {
        val stored = preferences.getString(KEY_PASSPHRASE, null)
        if (!stored.isNullOrBlank()) return stored.toByteArray(StandardCharsets.UTF_8)

        val database = context.getDatabasePath(com.hos.rushdpatients.config.AppConstants.DATABASE_NAME)
        check(listOf(database, java.io.File(database.path + "-wal"),
            java.io.File(database.path + "-shm"), java.io.File(database.path + "-journal")).none { it.exists() }) {
            "Database encryption key is unavailable; existing clinical storage was preserved"
        }
        val random = ByteArray(32).also(SecureRandom()::nextBytes)
        val encoded = Base64.encodeToString(random, Base64.NO_WRAP)
        check(preferences.edit().putString(KEY_PASSPHRASE, encoded).commit()) {
            "Unable to persist database encryption key"
        }
        return encoded.toByteArray(StandardCharsets.UTF_8)
    }

    private companion object {
        const val FILE_NAME = "shift_report_database_key"
        const val KEY_PASSPHRASE = "passphrase"
    }
}
