package com.hos.rushdpatients.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.hos.rushdpatients.ui.theme.AppAppearance
import com.hos.rushdpatients.ui.theme.AppFontScale
import com.hos.rushdpatients.ui.theme.AppThemePreset
import com.hos.rushdpatients.ui.theme.ThemePreferences
import com.hos.rushdpatients.ui.theme.ThemeUiState
import com.hos.rushdpatients.ui.theme.UiPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** App-private, unencrypted cosmetics. The explicit fields cannot accept clinical data or secrets. */
@Singleton
class DisplayPreferencesStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ThemeUiState())
    val state = mutableState.asStateFlow()

    private fun file() = context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)

    private fun read(file: SharedPreferences): ThemePreferences? {
        if (!file.getBoolean("initialized", false)) return null
        return ThemePreferences(
            preset = AppThemePreset.fromSetting(file.getString("accent", null)),
            appearance = AppAppearance.fromSetting(file.getString("appearance", null)),
            fontScale = AppFontScale.fromSetting(file.getString("font_scale", null)),
            uiPreferences = UiPreferences.decode(file.getString("ui_preferences", null))
        )
    }

    private fun write(value: ThemePreferences) {
        val saved = file().edit()
            .putString("accent", value.preset.name)
            .putString("appearance", value.appearance.name)
            .putString("font_scale", value.fontScale.name)
            .putString("ui_preferences", value.uiPreferences.encode())
            .putBoolean("initialized", true)
            .commit()
        if (!saved) throw IOException("Display preferences could not be saved")
        mutableState.value = ThemeUiState(preferences = value)
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (mutableState.value.preferences != null) return@withLock
            mutableState.value = ThemeUiState()
            try {
                val saved = read(file())
                mutableState.value = if (saved == null) ThemeUiState(needsInitialization = true)
                    else ThemeUiState(preferences = saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = ThemeUiState(loadFailed = true)
            }
        }
    }

    /** One-time copy of the four current display values; invoked only after protected access succeeds. */
    suspend fun initialize(readExisting: suspend () -> ThemePreferences) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val saved = read(file())
                if (saved != null) mutableState.value = ThemeUiState(preferences = saved)
                else write(readExisting())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = ThemeUiState(loadFailed = true)
            }
        }
    }

    suspend fun get(): ThemePreferences = withContext(Dispatchers.IO) {
        mutex.withLock {
            mutableState.value.preferences ?: checkNotNull(read(file())) { "Display preferences are not ready" }
        }
    }

    suspend fun update(transform: (ThemePreferences) -> ThemePreferences) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val saved = mutableState.value.preferences ?: checkNotNull(read(file())) { "Display preferences are not ready" }
            write(transform(saved))
        }
    }
}
