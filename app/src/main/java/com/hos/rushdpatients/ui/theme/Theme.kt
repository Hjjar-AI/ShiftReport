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

// Muted natural accents; clinical severity remains independent of these palettes.
private fun accent(preset: AppThemePreset) = when (preset) {
    AppThemePreset.SYSTEM -> Accent(Color(0xFF38677D), Color(0xFFDDECF3), Color(0xFF203E4C), Color(0xFFA8CEDF), Color(0xFF294957))
    AppThemePreset.SAGE -> Accent(Color(0xFF406B59), Color(0xFFDDEDE3), Color(0xFF254435), Color(0xFFACD4BC), Color(0xFF2D4C3D))
    AppThemePreset.COASTAL -> Accent(Color(0xFF66577E), Color(0xFFEAE3F1), Color(0xFF41354F), Color(0xFFC9BCDC), Color(0xFF493D59))
    AppThemePreset.SUNSET -> Accent(Color(0xFF726322), Color(0xFFF3EDCC), Color(0xFF493F16), Color(0xFFE1D697), Color(0xFF514824))
    AppThemePreset.FUCHSIA -> Accent(Color(0xFF8A4B60), Color(0xFFF3E0E6), Color(0xFF562B3B), Color(0xFFE2B5C4), Color(0xFF603747))
    AppThemePreset.VANILLA -> Accent(Color(0xFF786449), Color(0xFFF1E7D6), Color(0xFF4C3D29), Color(0xFFDDCAA9), Color(0xFF544630))
    AppThemePreset.CHOCOLATE -> Accent(Color(0xFF6E5C54), Color(0xFFECE2DB), Color(0xFF44352F), Color(0xFFD2BDB2), Color(0xFF4D403A))
    AppThemePreset.CREAM -> Accent(Color(0xFF746B4C), Color(0xFFF0ECD9), Color(0xFF46402C), Color(0xFFD9D0AD), Color(0xFF504A35))
}

private data class Surfaces(val background: Color, val surface: Color, val variant: Color)

private fun surfaces(preset: AppThemePreset, dark: Boolean, amoled: Boolean): Surfaces = when {
    amoled -> Surfaces(Color.Black, Color.Black, Color(0xFF151515))
    dark -> when (preset) {
        AppThemePreset.COASTAL -> Surfaces(Color(0xFF17161C), Color(0xFF211F27), Color(0xFF33303C))
        AppThemePreset.CHOCOLATE -> Surfaces(Color(0xFF191716), Color(0xFF242120), Color(0xFF36312E))
        else -> Surfaces(Color(0xFF17191A), Color(0xFF212425), Color(0xFF323638))
    }
    else -> when (preset) {
        AppThemePreset.VANILLA -> Surfaces(Color(0xFFF8F4EC), Color(0xFFFFFCF5), Color(0xFFF0EADF))
        AppThemePreset.CREAM -> Surfaces(Color(0xFFF7F5EC), Color(0xFFFFFDF5), Color(0xFFEEEBDD))
        AppThemePreset.CHOCOLATE -> Surfaces(Color(0xFFF6F2EF), Color(0xFFFEFBF9), Color(0xFFECE6E2))
        else -> Surfaces(Color(0xFFF5F6F6), Color(0xFFFCFDFD), Color(0xFFECEFEF))
    }
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
    preset: AppThemePreset = AppThemePreset.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    appearance: AppAppearance = AppAppearance.SYSTEM,
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
        background = surfaces.background, onBackground = Color(0xFFE3EBEE),
        surface = surfaces.surface, onSurface = Color(0xFFE3EBEE),
        surfaceVariant = surfaces.variant, onSurfaceVariant = Color(0xFFC0CCD1),
        outline = Color(0xFF8B999F), outlineVariant = Color(0xFF465359),
        inverseSurface = Color(0xFFE3EBEE), inverseOnSurface = Color(0xFF253238),
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
        background = surfaces.background, onBackground = Color(0xFF202B30),
        surface = surfaces.surface, onSurface = Color(0xFF202B30),
        surfaceVariant = surfaces.variant, onSurfaceVariant = Color(0xFF4A5B63),
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
