package com.hos.rushdpatients.testutil

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.UUID

/** Namespaces test preferences; production connection/session settings are never edited. */
open class IsolatedPreferencesContext : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
    private val prefix = "instrumentation_${UUID.randomUUID()}_"
    private val opened = mutableSetOf<String>()
    // Crypto keyset helpers also ask for applicationContext before opening preferences.
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val isolated = prefix + name
        opened += isolated
        return super.getSharedPreferences(isolated, mode)
    }
    fun clearTestPreferences() {
        for (name in opened) super.getSharedPreferences(name, MODE_PRIVATE).edit().clear().commit()
    }
}
