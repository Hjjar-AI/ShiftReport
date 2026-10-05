package com.hos.rushdpatients.data.db

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.SecureRandom
import java.nio.charset.StandardCharsets

internal class DatabaseKeyProvider(context: Context) {

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
