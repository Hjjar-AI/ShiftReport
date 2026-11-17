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

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun RushdPatientsTheme(
    preset: AppThemePreset = AppThemePreset.VANILLA,
    darkTheme: Boolean = isSystemInDarkTheme(),
    appearance: AppAppearance = AppAppearance.SYSTEM,
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
        appColorScheme(preset, dark, amoled, dynamicColor, context)
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
