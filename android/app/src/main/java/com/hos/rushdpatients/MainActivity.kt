package com.hos.rushdpatients

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import com.hos.rushdpatients.ui.about.IntroAboutScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.movableContentOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.fragment.app.FragmentActivity
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.ui.WardAppRoot
import com.hos.rushdpatients.ui.theme.RushdPatientsTheme
import com.hos.rushdpatients.ui.theme.AppSystemBars
import com.hos.rushdpatients.ui.theme.AppAppearance
import com.hos.rushdpatients.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val splash = com.hos.rushdpatients.ui.about.rememberLaunchSplashState()
            // Keep one intro composition as storage becomes ready and saved appearance is applied.
            val intro = remember(splash) { movableContentOf { LaunchIntro(splash) } }
            // Encrypted appearance preferences become readable only after this gate succeeds.
            RushdPatientsTheme(appearance = AppAppearance.SYSTEM) {
                AppSystemBars()
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        com.hos.rushdpatients.ui.StorageAccessGate(loading = intro) { AppContent(splash, intro) }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun MainActivity.AppContent(
    splash: com.hos.rushdpatients.ui.about.LaunchSplashState,
    intro: @androidx.compose.runtime.Composable () -> Unit
) {
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val themePreset by themeViewModel.preset.collectAsStateWithLifecycle()
    val appearance by themeViewModel.appearance.collectAsStateWithLifecycle()
    val fontScale by themeViewModel.fontScale.collectAsStateWithLifecycle()
    val uiPreferences by themeViewModel.uiPreferences.collectAsStateWithLifecycle()

    val baseDensity = LocalDensity.current
    // Only fontScale changes; density (px per dp) stays as the system set it.
    // The multiplier stacks on top of any system font scale the user has set.
    val scaledDensity = remember(baseDensity, fontScale) {
        Density(
            density = baseDensity.density,
            fontScale = (baseDensity.fontScale * fontScale.multiplier)
                .coerceIn(0.92f, 2.0f)
        )
    }

    RushdPatientsTheme(preset = themePreset, appearance = appearance, uiPreferences = uiPreferences) {
        AppSystemBars()
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalDensity provides scaledDensity
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                if (splash.visible) intro() else WardAppRoot(activity = this@AppContent)
            }
        }
    }
}

/** The launch deadline starts before storage loading; themed content shares that same state. */
@androidx.compose.runtime.Composable
private fun LaunchIntro(splash: com.hos.rushdpatients.ui.about.LaunchSplashState) {
    IntroAboutScreen(splash = true, onBack = splash::skip)
}
