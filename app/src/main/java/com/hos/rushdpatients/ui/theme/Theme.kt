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

// Keep white/charcoal surfaces stable; apply a distinct accent to controls and section containers.
private fun accent(preset: AppThemePreset) = when (preset) {
    AppThemePreset.SYSTEM -> Accent(Color(0xFF006B68), Color(0xFFC2F0E8), Color(0xFF003D3A), Color(0xFF75DDD1), Color(0xFF00504A))
    AppThemePreset.SAGE -> Accent(Color(0xFF37662F), Color(0xFFD5F2BD), Color(0xFF163C12), Color(0xFFA2DB8C), Color(0xFF285022))
    AppThemePreset.COASTAL -> Accent(Color(0xFF1E55B3), Color(0xFFD8E5FF), Color(0xFF073379), Color(0xFF9FC4FF), Color(0xFF163F82))
    AppThemePreset.SUNSET -> Accent(Color(0xFF865600), Color(0xFFFFE2A0), Color(0xFF432B00), Color(0xFFFFD273), Color(0xFF594000))
    AppThemePreset.FUCHSIA -> Accent(Color(0xFF7F36A1), Color(0xFFF1D9FF), Color(0xFF4D1469), Color(0xFFE1B2FF), Color(0xFF622280))
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
        secondary = a.darkPrimary, onSecondary = Color(0xFF172125),
        secondaryContainer = a.darkContainer, onSecondaryContainer = a.container,
        tertiary = a.darkPrimary, onTertiary = Color(0xFF172125),
        tertiaryContainer = a.darkContainer, onTertiaryContainer = a.container,
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
        secondary = a.primary, onSecondary = Color.White,
        secondaryContainer = a.container, onSecondaryContainer = a.onContainer,
        tertiary = a.primary, onTertiary = Color.White,
        tertiaryContainer = a.container, onTertiaryContainer = a.onContainer,
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
