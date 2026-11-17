package com.hos.rushdpatients.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class UiDensity { COMPACT, NORMAL, RELAXED }

@Serializable
enum class UiControlSize { DEFAULT, COMPACT, STANDARD, COMFORTABLE;
    fun visualHeight(default: Dp): Dp = when (this) {
        DEFAULT -> default
        COMPACT -> UiSize.controlCompact
        STANDARD -> UiSize.controlStandard
        COMFORTABLE -> UiSize.controlComfortable
    }
    val iconSize: Dp get() = when (this) {
        COMPACT -> UiSize.iconSmall
        STANDARD -> UiSize.iconMedium
        DEFAULT, COMFORTABLE -> UiSize.icon
    }
}

/** Device-local presentation only; excluded from Telegram snapshots and clinical exports. */
@Serializable
data class UiPreferences(
    val density: UiDensity = UiDensity.NORMAL,
    val controlSize: UiControlSize = UiControlSize.COMFORTABLE,
    val reducedMotion: Boolean = false
) {
    fun encode(): String = codec.encodeToString(this)
    companion object {
        private val codec = Json
        fun decode(value: String?): UiPreferences = try {
            if (value == null) UiPreferences() else codec.decodeFromString<UiPreferences>(value).copy(density = UiDensity.NORMAL)
        } catch (_: SerializationException) { UiPreferences() }
          catch (_: IllegalArgumentException) { UiPreferences() }
    }
}

val LocalUiPreferences = staticCompositionLocalOf { UiPreferences() }
val LocalUiStyle = staticCompositionLocalOf { UiStyleCatalog.current }

@Composable
fun ProvideUiPreferences(preferences: UiPreferences, content: @Composable () -> Unit) {
    val selected = remember(preferences) { preferences.copy(density = UiDensity.NORMAL) }
    val base = UiStyleCatalog.current
    val style = remember(selected.reducedMotion) {
        if (selected.reducedMotion) base.copy(expandMillis = 0, collapseMillis = 0, highlightMillis = 0, indicatorMillis = 0, placementMillis = 0) else base
    }
    CompositionLocalProvider(LocalUiPreferences provides selected, LocalUiStyle provides style, content = content)
}

/** Presentation helpers retain today's Normal values; retired density preferences are inert. */
@Composable
fun uiContentPadding(normal: PaddingValues = UiPadding.card): PaddingValues = normal

@Composable
fun uiContentGap(): Dp = UiSpacing.small

/** Layout thresholds grow for large text, and never narrow when the app font is smaller. */
@Composable
fun uiLayoutScale(): Float = LocalDensity.current.fontScale.coerceAtLeast(1f)

@Composable
fun uiListGap(): Dp = LocalUiStyle.current.listGap
