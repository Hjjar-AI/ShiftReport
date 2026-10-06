package com.hos.rushdpatients.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF90E0EF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF003544),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF004E68),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFCAF0F8),
    secondary = androidx.compose.ui.graphics.Color(0xFFADB5BD),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF212529),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF343A40),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFE9ECEF),
    tertiary = androidx.compose.ui.graphics.Color(0xFFFFC857),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF392A00),
    background = androidx.compose.ui.graphics.Color(0xFF101418),
    surface = androidx.compose.ui.graphics.Color(0xFF1B2025),
    onBackground = androidx.compose.ui.graphics.Color(0xFFF8F9FA),
    onSurface = androidx.compose.ui.graphics.Color(0xFFF8F9FA),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF343A40),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFDEE2E6),
    outline = androidx.compose.ui.graphics.Color(0xFFADB5BD)
)

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF0B5266),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFCDEBF2),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF07333E),
    secondary = androidx.compose.ui.graphics.Color(0xFF42636A),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    tertiary = androidx.compose.ui.graphics.Color(0xFF5B5F86),
    onTertiary = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFFF6FAFB),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onBackground = androidx.compose.ui.graphics.Color(0xFF172023),
    onSurface = androidx.compose.ui.graphics.Color(0xFF172023),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFDFE9EB),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF3F494C),
    outline = androidx.compose.ui.graphics.Color(0xFF6F797C)
)

private val SageColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF4F6527),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFE4E8C7),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF283618),
    secondary = androidx.compose.ui.graphics.Color(0xFF82501B),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFF3D8B5),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF42280B),
    tertiary = androidx.compose.ui.graphics.Color(0xFF944513),
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDCBE),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF512400),
    background = androidx.compose.ui.graphics.Color(0xFFFEFAE0),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFDF1),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE9E6D0),
    onBackground = androidx.compose.ui.graphics.Color(0xFF202313),
    onSurface = androidx.compose.ui.graphics.Color(0xFF202313),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF4A4C3C),
    outline = androidx.compose.ui.graphics.Color(0xFF777867)
)

private val CoastalColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF006A9E),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFCAF0F8),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF023E8A),
    secondary = androidx.compose.ui.graphics.Color(0xFF007F98),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFADE8F4),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF004C5A),
    tertiary = androidx.compose.ui.graphics.Color(0xFF435A94),
    onTertiary = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFFF5FCFE),
    surface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFDDEFF3),
    onBackground = androidx.compose.ui.graphics.Color(0xFF102126),
    onSurface = androidx.compose.ui.graphics.Color(0xFF102126),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF3C4B50),
    outline = androidx.compose.ui.graphics.Color(0xFF6B7A7F)
)

private val SunsetColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFFFD900),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF332B00),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF594B00),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFFFE980),
    secondary = androidx.compose.ui.graphics.Color(0xFF83E4F5),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF00363F),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF004D5A),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFCAF0F8),
    tertiary = androidx.compose.ui.graphics.Color(0xFFFFBF00),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF352A00),
    background = androidx.compose.ui.graphics.Color(0xFF000814),
    surface = androidx.compose.ui.graphics.Color(0xFF00152C),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF003566),
    onBackground = androidx.compose.ui.graphics.Color(0xFFF4F7FA),
    onSurface = androidx.compose.ui.graphics.Color(0xFFF4F7FA),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFCAE6F5),
    outline = androidx.compose.ui.graphics.Color(0xFF90B7CD)
)

private val FuchsiaColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF714D88),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFE9DDF1),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF342640),
    secondary = androidx.compose.ui.graphics.Color(0xFF94496F),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFF4D7E7),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF4B2338),
    tertiary = androidx.compose.ui.graphics.Color(0xFF454B73),
    onTertiary = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFFFCF8FC),
    surface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFF0E6EF),
    onBackground = androidx.compose.ui.graphics.Color(0xFF24202A),
    onSurface = androidx.compose.ui.graphics.Color(0xFF24202A),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF514A55),
    outline = androidx.compose.ui.graphics.Color(0xFF7D747F)
)

@Composable
fun RushdPatientsTheme(
    preset: AppThemePreset = AppThemePreset.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when (preset) {
        AppThemePreset.SAGE -> SageColorScheme
        AppThemePreset.COASTAL -> CoastalColorScheme
        AppThemePreset.SUNSET -> SunsetColorScheme
        AppThemePreset.FUCHSIA -> FuchsiaColorScheme
        AppThemePreset.SYSTEM -> if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        } else if (darkTheme) DarkColorScheme else LightColorScheme
    }

    val clinicalColors = when (preset) {
        AppThemePreset.SUNSET -> DarkClinicalColors
        AppThemePreset.SYSTEM -> if (darkTheme) DarkClinicalColors else LightClinicalColors
        else -> LightClinicalColors
    }

    CompositionLocalProvider(LocalClinicalColors provides clinicalColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
