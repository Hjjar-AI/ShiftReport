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

// One quiet surface family across presets; only the primary accent changes.
private fun accent(preset: AppThemePreset) = when (preset) {
    AppThemePreset.SYSTEM -> Accent(Color(0xFF0B6265), Color(0xFFD5EEED), Color(0xFF123B3C), Color(0xFF94D5D2), Color(0xFF174C4D))
    AppThemePreset.SAGE -> Accent(Color(0xFF52643D), Color(0xFFE0EAD2), Color(0xFF293A1B), Color(0xFFBDCEA5), Color(0xFF364A27))
    AppThemePreset.COASTAL -> Accent(Color(0xFF285F82), Color(0xFFDCEAF4), Color(0xFF183B53), Color(0xFFA2CAE6), Color(0xFF23475F))
    AppThemePreset.SUNSET -> Accent(Color(0xFF445A77), Color(0xFFE0E7F2), Color(0xFF24364E), Color(0xFFB3C7E4), Color(0xFF304460))
    AppThemePreset.FUCHSIA -> Accent(Color(0xFF69557C), Color(0xFFECE3F2), Color(0xFF3D2F4B), Color(0xFFD0BDE3), Color(0xFF4C3B5E))
}

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
        AppAppearance.DARK -> true
    }
    val a = accent(preset)
    val scheme = if (dynamicColor && preset == AppThemePreset.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) darkColorScheme(
        primary = a.darkPrimary, onPrimary = Color(0xFF172125),
        primaryContainer = a.darkContainer, onPrimaryContainer = a.container,
        secondary = Color(0xFFBBCAD0), onSecondary = Color(0xFF253238),
        secondaryContainer = Color(0xFF34434A), onSecondaryContainer = Color(0xFFDCE6EA),
        tertiary = Color(0xFFBBCAD0), onTertiary = Color(0xFF253238),
        tertiaryContainer = Color(0xFF34434A), onTertiaryContainer = Color(0xFFDCE6EA),
        background = Color(0xFF111719), onBackground = Color(0xFFE3EBEE),
        surface = Color(0xFF192124), onSurface = Color(0xFFE3EBEE),
        surfaceVariant = Color(0xFF2C363B), onSurfaceVariant = Color(0xFFC0CCD1),
        outline = Color(0xFF8B999F), outlineVariant = Color(0xFF465359),
        inverseSurface = Color(0xFFE3EBEE), inverseOnSurface = Color(0xFF253238),
        inversePrimary = a.primary, surfaceTint = a.darkPrimary,
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        scrim = Color.Black
    ) else lightColorScheme(
        primary = a.primary, onPrimary = Color.White,
        primaryContainer = a.container, onPrimaryContainer = a.onContainer,
        secondary = Color(0xFF485D66), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE4EBEE), onSecondaryContainer = Color(0xFF293A42),
        tertiary = Color(0xFF485D66), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE4EBEE), onTertiaryContainer = Color(0xFF293A42),
        background = Color(0xFFF5F7F8), onBackground = Color(0xFF202B30),
        surface = Color.White, onSurface = Color(0xFF202B30),
        surfaceVariant = Color(0xFFEBF0F2), onSurfaceVariant = Color(0xFF4A5B63),
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
