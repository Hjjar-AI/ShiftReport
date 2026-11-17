package com.hos.rushdpatients.ui.theme

import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color

internal data class AccentRoles(
    val primary: Color, val container: Color, val onContainer: Color,
    val darkPrimary: Color, val darkContainer: Color
)

// Muted accent families with soft containers; clinical severity uses its separate palette.
private fun accent(preset: AppThemePreset) = when (preset) {
    AppThemePreset.SYSTEM -> AccentRoles(Color(0xFF405B83), Color(0xFFE2E8F0), Color(0xFF23364E), Color(0xFFB4C6DE), Color(0xFF34475F))
    AppThemePreset.COASTAL -> AccentRoles(Color(0xFF944C70), Color(0xFFF3E3EB), Color(0xFF4D293C), Color(0xFFDDB3C8), Color(0xFF583E4C))
    AppThemePreset.VANILLA -> AccentRoles(Color(0xFF0D5663), Color(0xFFE0EBED), Color(0xFF203B41), Color(0xFFA3C9CF), Color(0xFF29494F))
    AppThemePreset.CREAM -> AccentRoles(Color(0xFF805B34), Color(0xFFEFE7DD), Color(0xFF44311D), Color(0xFFD8C0A3), Color(0xFF534330))
}

internal data class SurfaceRoles(val background: Color, val surface: Color, val variant: Color)

// Separate canvas, cards/dialogs and inset controls without large pure-white surfaces.
// AMOLED keeps the canvas black; near-black panels keep fields and cards distinguishable.
private fun surfaces(dark: Boolean, amoled: Boolean): SurfaceRoles = when {
    amoled -> SurfaceRoles(Color.Black, Color(0xFF11191D), Color(0xFF29383F))
    dark -> SurfaceRoles(Color(0xFF151C23), Color(0xFF26323C), Color(0xFF3A4B57))
    else -> SurfaceRoles(Color(0xFFD9E0DE), Color(0xFFEEF1ED), Color(0xFFCBD7D3))
}

/** Optional per-preset surfaces; empty defaults retain all shipped palette values. */
internal data class SurfaceOverrides(
    val light: SurfaceRoles? = null, val dark: SurfaceRoles? = null, val amoled: SurfaceRoles? = null
)
internal data class AppColorRoles(val accent: AccentRoles, val surfaces: SurfaceRoles)

internal fun appColorRoles(preset: AppThemePreset, dark: Boolean, amoled: Boolean,
    overrides: SurfaceOverrides = SurfaceOverrides()): AppColorRoles = AppColorRoles(
    accent(preset), (if (amoled) overrides.amoled else if (dark) overrides.dark else overrides.light)
        ?: surfaces(dark, amoled))

/** Used by palette controls to preview the actual selected appearance. */
fun appAccentColor(preset: AppThemePreset, dark: Boolean): Color =
    appColorRoles(preset, dark, false).accent.let { if (dark) it.darkPrimary else it.primary }

internal fun appColorScheme(preset: AppThemePreset, dark: Boolean, amoled: Boolean,
    dynamicColor: Boolean, context: android.content.Context): androidx.compose.material3.ColorScheme {
        val roles = appColorRoles(preset, dark, amoled)
        val a = roles.accent
        val surfaces = roles.surfaces
        return if (dynamicColor && !amoled && preset == AppThemePreset.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) darkColorScheme(
            primary = a.darkPrimary, onPrimary = Color(0xFF172125),
            primaryContainer = a.darkContainer, onPrimaryContainer = a.container,
            secondary = Color(0xFFC1CAD4), onSecondary = Color(0xFF253238),
            secondaryContainer = Color(0xFF3B4852), onSecondaryContainer = Color(0xFFDFE5ED),
            tertiary = Color(0xFFD0BCFF), onTertiary = Color(0xFF33264D),
            tertiaryContainer = Color(0xFF4A3D65), onTertiaryContainer = Color(0xFFEADDFF),
            background = surfaces.background, onBackground = Color(0xFFE1E7EC),
            surface = surfaces.surface, onSurface = Color(0xFFE1E7EC),
            surfaceVariant = surfaces.variant, onSurfaceVariant = Color(0xFFC3CDD3),
            outline = if (amoled) Color(0xFF9AAAB2) else Color(0xFFA1AFB6),
            outlineVariant = if (amoled) Color(0xFF4D626C) else Color(0xFF61747F),
            inverseSurface = Color(0xFFE8EDF4), inverseOnSurface = Color(0xFF253238),
            inversePrimary = a.primary, surfaceTint = if (amoled) Color.Black else a.darkPrimary,
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
            scrim = Color.Black
        ) else lightColorScheme(
            primary = a.primary, onPrimary = Color.White,
            primaryContainer = a.container, onPrimaryContainer = a.onContainer,
            secondary = Color(0xFF4D5965), onSecondary = Color.White,
            secondaryContainer = Color(0xFFDFE5ED), onSecondaryContainer = Color(0xFF202D3C),
            tertiary = Color(0xFF65558F), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFEADDFF), onTertiaryContainer = Color(0xFF2A1745),
            background = surfaces.background, onBackground = Color(0xFF202832),
            surface = surfaces.surface, onSurface = Color(0xFF202832),
            surfaceVariant = surfaces.variant, onSurfaceVariant = Color(0xFF485360),
            outline = Color(0xFF647875), outlineVariant = Color(0xFFA3B3AE),
            inverseSurface = Color(0xFF29353B), inverseOnSurface = Color(0xFFF0F5F7),
            inversePrimary = a.darkPrimary, surfaceTint = a.primary,
            error = Color(0xFFB3261E), onError = Color.White,
            errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
            scrim = Color.Black
        )
}
