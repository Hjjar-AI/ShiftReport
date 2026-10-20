package com.hos.rushdpatients.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class Accent(
    val primary: Color, val container: Color, val onContainer: Color,
    val darkPrimary: Color, val darkContainer: Color
)

// Distinct hue families with tinted surfaces; clinical severity uses its separate palette.
private fun accent(preset: AppThemePreset) = when (preset) {
    AppThemePreset.SYSTEM -> Accent(Color(0xFF2346B5), Color(0xFFDFE5FF), Color(0xFF122459), Color(0xFFB5C4FF), Color(0xFF243C80))
    AppThemePreset.SAGE -> Accent(Color(0xFF006B3F), Color(0xFFBCF1D1), Color(0xFF003D23), Color(0xFF77DDA4), Color(0xFF004F30))
    AppThemePreset.COASTAL -> Accent(Color(0xFF7026A0), Color(0xFFF0D9FF), Color(0xFF400D60), Color(0xFFDFB2FF), Color(0xFF50226B))
    AppThemePreset.SUNSET -> Accent(Color(0xFF795900), Color(0xFFFFEBAC), Color(0xFF403000), Color(0xFFE9CA61), Color(0xFF594500))
    AppThemePreset.FUCHSIA -> Accent(Color(0xFFA01575), Color(0xFFFFD8EF), Color(0xFF5C0841), Color(0xFFFFAADE), Color(0xFF721653))
    AppThemePreset.VANILLA -> Accent(Color(0xFF006B72), Color(0xFFBBEEF0), Color(0xFF003C42), Color(0xFF73D9DF), Color(0xFF135158))
    AppThemePreset.CHOCOLATE -> Accent(Color(0xFF455364), Color(0xFFDFE5ED), Color(0xFF202D3C), Color(0xFFB8C9DC), Color(0xFF344558))
    AppThemePreset.CREAM -> Accent(Color(0xFFAD3D12), Color(0xFFFFDBCC), Color(0xFF5B2106), Color(0xFFFFB28E), Color(0xFF773015))
}

private data class Surfaces(val background: Color, val surface: Color, val variant: Color)

private fun surfaces(preset: AppThemePreset, dark: Boolean, amoled: Boolean): Surfaces = when {
    amoled -> Surfaces(Color.Black, Color.Black, Color(0xFF15171B))
    dark -> when (preset) {
        AppThemePreset.SYSTEM -> Surfaces(Color(0xFF101B2B), Color(0xFF19283D), Color(0xFF2A3C55))
        AppThemePreset.SAGE -> Surfaces(Color(0xFF101E16), Color(0xFF1A2D20), Color(0xFF2B4232))
        AppThemePreset.COASTAL -> Surfaces(Color(0xFF21152D), Color(0xFF30203E), Color(0xFF443054))
        AppThemePreset.SUNSET -> Surfaces(Color(0xFF291C10), Color(0xFF3B2A19), Color(0xFF513D28))
        AppThemePreset.FUCHSIA -> Surfaces(Color(0xFF2B131D), Color(0xFF3C1E2B), Color(0xFF532C3D))
        AppThemePreset.VANILLA -> Surfaces(Color(0xFF0B2327), Color(0xFF15363B), Color(0xFF275057))
        AppThemePreset.CHOCOLATE -> Surfaces(Color(0xFF171D24), Color(0xFF252F3A), Color(0xFF394655))
        AppThemePreset.CREAM -> Surfaces(Color(0xFF291810), Color(0xFF3B251B), Color(0xFF51372A))
    }
    else -> Surfaces(Color(0xFFF8FAFC), Color.White, Color(0xFFE8EEF2))
}

/** Used by palette controls to preview the actual selected appearance. */
fun appAccentColor(preset: AppThemePreset, dark: Boolean): Color =
    accent(preset).let { if (dark) it.darkPrimary else it.primary }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun RushdPatientsTheme(
    preset: AppThemePreset = AppThemePreset.VANILLA,
    darkTheme: Boolean = isSystemInDarkTheme(),
    appearance: AppAppearance = AppAppearance.LIGHT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (appearance) {
        AppAppearance.SYSTEM -> darkTheme
        AppAppearance.LIGHT -> false
        AppAppearance.DARK, AppAppearance.AMOLED -> true
    }
    val amoled = appearance == AppAppearance.AMOLED
    val a = accent(preset)
    val surfaces = surfaces(preset, dark, amoled)
    val scheme = if (dynamicColor && !amoled && preset == AppThemePreset.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) darkColorScheme(
        primary = a.darkPrimary, onPrimary = Color(0xFF172125),
        primaryContainer = a.darkContainer, onPrimaryContainer = a.container,
        secondary = a.darkPrimary, onSecondary = Color(0xFF172125),
        secondaryContainer = a.darkContainer, onSecondaryContainer = a.container,
        tertiary = a.darkPrimary, onTertiary = Color(0xFF172125),
        tertiaryContainer = a.darkContainer, onTertiaryContainer = a.container,
        background = surfaces.background, onBackground = Color(0xFFE8EDF4),
        surface = surfaces.surface, onSurface = Color(0xFFE8EDF4),
        surfaceVariant = surfaces.variant, onSurfaceVariant = Color(0xFFC8CFD8),
        outline = Color(0xFF8B999F), outlineVariant = Color(0xFF465359),
        inverseSurface = Color(0xFFE8EDF4), inverseOnSurface = Color(0xFF253238),
        inversePrimary = a.primary, surfaceTint = if (amoled) Color.Black else a.darkPrimary,
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        scrim = Color.Black
    ) else lightColorScheme(
        primary = a.primary, onPrimary = Color.White,
        primaryContainer = a.container, onPrimaryContainer = a.onContainer,
        secondary = a.primary, onSecondary = Color.White,
        secondaryContainer = a.container, onSecondaryContainer = a.onContainer,
        tertiary = a.primary, onTertiary = Color.White,
        tertiaryContainer = a.container, onTertiaryContainer = a.onContainer,
        background = surfaces.background, onBackground = Color(0xFF202832),
        surface = surfaces.surface, onSurface = Color(0xFF202832),
        surfaceVariant = surfaces.variant, onSurfaceVariant = Color(0xFF485360),
        outline = Color(0xFF72838B), outlineVariant = Color(0xFFD0DADD),
        inverseSurface = Color(0xFF29353B), inverseOnSurface = Color(0xFFF0F5F7),
        inversePrimary = a.darkPrimary, surfaceTint = a.primary,
        error = Color(0xFFB3261E), onError = Color.White,
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        scrim = Color.Black
    )
    CompositionLocalProvider(LocalClinicalColors provides if (dark) DarkClinicalColors else LightClinicalColors) {
        MaterialTheme(colorScheme = scheme, typography = Typography, shapes = AppShapes, content = content)
    }
}
