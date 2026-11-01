package com.hos.rushdpatients.ui.demo

import android.content.Context
import com.hos.rushdpatients.ui.theme.AppAppearance
import com.hos.rushdpatients.ui.theme.AppThemePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal data class DemoAppearance(
    val appearance: AppAppearance = AppAppearance.LIGHT,
    val accent: AppThemePreset = AppThemePreset.VANILLA
)

/** Device-local cosmetic preferences only; no demo records, credentials or live-project settings. */
internal class DemoAppearanceStore(context: Context) {
    private val appContext = context.applicationContext
    private fun preferences() = appContext.getSharedPreferences("demo_appearance", Context.MODE_PRIVATE)

    suspend fun load(): DemoAppearance = withContext(Dispatchers.IO) {
        val stored = preferences()
        DemoAppearance(
            appearance = stored.getString("appearance", null)?.let(AppAppearance::fromSetting) ?: AppAppearance.LIGHT,
            accent = AppThemePreset.fromSetting(stored.getString("accent", null))
        )
    }

    // Finish this small queued preference write even if the demo session exits.
    suspend fun save(value: DemoAppearance) = withContext(Dispatchers.IO + NonCancellable) {
        preferences().edit().putString("appearance", value.appearance.name)
            .putString("accent", value.accent.name).apply()
    }
}
