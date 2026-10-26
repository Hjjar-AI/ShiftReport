package com.hos.rushdpatients.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
        COMPACT -> 32.dp
        STANDARD -> 40.dp
        COMFORTABLE -> 48.dp
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
    val profile: String = "current",
    val density: UiDensity = UiDensity.NORMAL,
    val controlSize: UiControlSize = UiControlSize.COMFORTABLE,
    val reducedMotion: Boolean = false,
    val autoHideNavigation: Boolean = false,
    val compactAddAction: Boolean = true,
    val expandPatientHeader: Boolean = false
) {
    fun simplified(): UiPreferences = copy(
        profile = "current", density = UiDensity.NORMAL,
        controlSize = UiControlSize.COMFORTABLE, autoHideNavigation = false,
        compactAddAction = true, expandPatientHeader = false
    )
    fun encode(): String = codec.encodeToString(simplified())
    companion object {
        private val codec = Json { ignoreUnknownKeys = true }
        fun decode(value: String?): UiPreferences = try {
            (if (value == null) UiPreferences() else codec.decodeFromString<UiPreferences>(value)).simplified()
        } catch (_: SerializationException) { UiPreferences() }
          catch (_: IllegalArgumentException) { UiPreferences() }
    }
}

val LocalUiPreferences = staticCompositionLocalOf { UiPreferences() }
val LocalUiStyle = staticCompositionLocalOf { UiStyleCatalog.current }

@Composable
fun ProvideUiPreferences(preferences: UiPreferences, content: @Composable () -> Unit) {
    val selected = preferences.simplified()
    val base = UiStyleCatalog.current
    val style = if (selected.reducedMotion) base.copy(expandMillis = 0, collapseMillis = 0, highlightMillis = 0, indicatorMillis = 0, placementMillis = 0) else base
    CompositionLocalProvider(LocalUiPreferences provides selected, LocalUiStyle provides style, content = content)
}

/** Local overrides support a compact toolbar or relaxed section without another persisted setting. */
@Composable
fun ProvideUiDefaults(density: UiDensity = LocalUiPreferences.current.density,
    size: UiControlSize = LocalUiPreferences.current.controlSize, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUiPreferences provides LocalUiPreferences.current.copy(density = density, controlSize = size),
        content = content)
}

@Composable
fun uiContentPadding(normal: PaddingValues = UiPadding.card): PaddingValues = when (LocalUiPreferences.current.density) {
    UiDensity.COMPACT -> LocalUiStyle.current.compactDensity.padding
    UiDensity.NORMAL -> if (LocalUiStyle.current.id == "relaxed") LocalUiStyle.current.normalDensity.padding else normal
    UiDensity.RELAXED -> UiStyleCatalog.relaxed.normalDensity.padding
}

@Composable
fun uiContentGap(): Dp = when (LocalUiPreferences.current.density) {
    UiDensity.COMPACT -> UiSpacing.tiny
    UiDensity.NORMAL -> if (LocalUiStyle.current.id == "relaxed") LocalUiStyle.current.normalDensity.sectionGap else UiSpacing.small
    UiDensity.RELAXED -> UiSpacing.section
}

/** Layout thresholds grow for large text, and never narrow when the app font is smaller. */
@Composable
fun uiLayoutScale(): Float = LocalDensity.current.fontScale.coerceAtLeast(1f)

@Composable
fun uiListGap(): Dp = when (LocalUiPreferences.current.density) {
    UiDensity.COMPACT -> UiSpacing.small
    UiDensity.NORMAL -> LocalUiStyle.current.listGap
    UiDensity.RELAXED -> UiSpacing.section
}
