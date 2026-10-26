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
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class Accent(
    val primary: Color, val container: Color, val onContainer: Color,
    val darkPrimary: Color, val darkContainer: Color
)

// Distinct accent hue families; clinical severity uses its separate palette.
private fun accent(preset: AppThemePreset) = when (preset) {
    AppThemePreset.SYSTEM -> Accent(Color(0xFF2346B5), Color(0xFFDFE5FF), Color(0xFF122459), Color(0xFFB5C4FF), Color(0xFF243C80))
    AppThemePreset.SAGE -> Accent(Color(0xFF006B3F), Color(0xFFBCF1D1), Color(0xFF003D23), Color(0xFF77DDA4), Color(0xFF004F30))
    AppThemePreset.SUNSET -> Accent(Color(0xFF795900), Color(0xFFFFEBAC), Color(0xFF403000), Color(0xFFE9CA61), Color(0xFF594500))
    AppThemePreset.COASTAL, AppThemePreset.FUCHSIA -> Accent(Color(0xFFA01575), Color(0xFFFFD8EF), Color(0xFF5C0841), Color(0xFFFFAADE), Color(0xFF721653))
    AppThemePreset.VANILLA -> Accent(Color(0xFF006B72), Color(0xFFBBEEF0), Color(0xFF003C42), Color(0xFF73D9DF), Color(0xFF135158))
    AppThemePreset.CHOCOLATE -> Accent(Color(0xFF455364), Color(0xFFDFE5ED), Color(0xFF202D3C), Color(0xFFB8C9DC), Color(0xFF344558))
    AppThemePreset.CREAM -> Accent(Color(0xFFAD3D12), Color(0xFFFFDBCC), Color(0xFF5B2106), Color(0xFFFFB28E), Color(0xFF773015))
}

private data class Surfaces(val background: Color, val surface: Color, val variant: Color)

private fun surfaces(dark: Boolean, amoled: Boolean): Surfaces = when {
    amoled -> Surfaces(Color.Black, Color.Black, Color(0xFF15171B))
    dark -> Surfaces(Color(0xFF171D24), Color(0xFF252F3A), Color(0xFF394655))
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
    uiPreferences: UiPreferences = LocalUiPreferences.current,
    content: @Composable () -> Unit
) {
    val dark = when (appearance) {
        AppAppearance.SYSTEM -> darkTheme
        AppAppearance.LIGHT -> false
        AppAppearance.DARK, AppAppearance.AMOLED -> true
    }
    val amoled = appearance == AppAppearance.AMOLED
    val context = LocalContext.current
    val scheme = remember(preset, dark, amoled, dynamicColor, context) {
        val a = accent(preset)
        val surfaces = surfaces(dark, amoled)
        if (dynamicColor && !amoled && preset == AppThemePreset.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) darkColorScheme(
            primary = a.darkPrimary, onPrimary = Color(0xFF172125),
            primaryContainer = a.darkContainer, onPrimaryContainer = a.container,
            secondary = Color(0xFFC1CAD4), onSecondary = Color(0xFF253238),
            secondaryContainer = Color(0xFF3B4852), onSecondaryContainer = Color(0xFFDFE5ED),
            tertiary = Color(0xFFD0BCFF), onTertiary = Color(0xFF33264D),
            tertiaryContainer = Color(0xFF4A3D65), onTertiaryContainer = Color(0xFFEADDFF),
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
            secondary = Color(0xFF4D5965), onSecondary = Color.White,
            secondaryContainer = Color(0xFFDFE5ED), onSecondaryContainer = Color(0xFF202D3C),
            tertiary = Color(0xFF65558F), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFEADDFF), onTertiaryContainer = Color(0xFF2A1745),
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
    }
    CompositionLocalProvider(LocalClinicalColors provides if (dark) DarkClinicalColors else LightClinicalColors) {
        ProvideUiPreferences(uiPreferences) {
            MaterialTheme(colorScheme = scheme, typography = Typography, shapes = AppShapes, content = content)
        }
    }
}

/** Apply only at full-window theme owners; embedded previews never change system bars. */
@Composable
fun AppSystemBars() {
    val window = LocalContext.current.findThemeActivity()?.window ?: return
    val scheme = MaterialTheme.colorScheme
    val light = scheme.background.luminance() > .5f
    val statusColor = scheme.background.toArgb()
    val navigationColor = (if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && light)
        scheme.inverseSurface else scheme.background).toArgb()
    DisposableEffect(window, statusColor, navigationColor, light) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val previousStatus = window.statusBarColor
        val previousNavigation = window.navigationBarColor
        val previousStatusIcons = controller.isAppearanceLightStatusBars
        val previousNavigationIcons = controller.isAppearanceLightNavigationBars
        window.statusBarColor = statusColor
        window.navigationBarColor = navigationColor
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
        onDispose {
            window.statusBarColor = previousStatus
            window.navigationBarColor = previousNavigation
            controller.isAppearanceLightStatusBars = previousStatusIcons
            controller.isAppearanceLightNavigationBars = previousNavigationIcons
        }
    }
}

private tailrec fun Context.findThemeActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findThemeActivity()
    else -> null
}
