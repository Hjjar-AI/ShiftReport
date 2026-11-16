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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
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
        window.decorView.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val splash = com.hos.rushdpatients.ui.about.rememberLaunchSplashState()
            AppContent(splash)
        }
    }
}

@androidx.compose.runtime.Composable
private fun MainActivity.AppContent(
    splash: com.hos.rushdpatients.ui.about.LaunchSplashState
) {
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val themeState by themeViewModel.state.collectAsStateWithLifecycle()
    val preferences = themeState.preferences
    val intro = remember(splash) { movableContentOf { LaunchIntro(splash) } }
    val fontScale = preferences?.fontScale ?: com.hos.rushdpatients.ui.theme.AppFontScale.NORMAL

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

    RushdPatientsTheme(preset = preferences?.preset ?: com.hos.rushdpatients.ui.theme.AppThemePreset.VANILLA,
        appearance = preferences?.appearance ?: AppAppearance.SYSTEM,
        uiPreferences = preferences?.uiPreferences ?: com.hos.rushdpatients.ui.theme.UiPreferences()) {
        AppSystemBars()
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalDensity provides scaledDensity
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                com.hos.rushdpatients.ui.StorageAccessGate(loading = {
                    if (preferences != null && splash.visible) intro()
                    else if (themeState.loadFailed) DisplayPreferencesFailure(themeViewModel::retry)
                }) {
                    val application = LocalContext.current.applicationContext
                    LaunchedEffect(themeState.needsInitialization) {
                        if (themeState.needsInitialization) {
                            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                                application, com.hos.rushdpatients.ui.StorageAccessEntryPoint::class.java)
                            themeViewModel.initializeFrom(entryPoint.settingsRepository())
                        }
                    }
                    if (preferences != null) {
                        if (splash.visible) intro() else WardAppRoot(activity = this@AppContent)
                    } else if (themeState.loadFailed) DisplayPreferencesFailure(themeViewModel::retry)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DisplayPreferencesFailure(onRetry: () -> Unit) {
    val uiText = com.hos.rushdpatients.ui.components.rememberUiStrings()
    com.hos.rushdpatients.ui.components.AppCenteredContent {
        com.hos.rushdpatients.ui.components.AppNotice(uiText(R.string.startup_display_preferences_failed),
            kind = com.hos.rushdpatients.ui.components.NoticeKind.ERROR)
        com.hos.rushdpatients.ui.components.AppButton(onClick = onRetry) {
            androidx.compose.material3.Text(uiText(R.string.ui_retry))
        }
    }
}

/** The launch deadline starts before storage loading; themed content shares that same state. */
@androidx.compose.runtime.Composable
private fun LaunchIntro(splash: com.hos.rushdpatients.ui.about.LaunchSplashState) {
    IntroAboutScreen(splash = true, onBack = splash::skip,
        onSplashPress = splash::press, onSplashRelease = splash::release)
}
