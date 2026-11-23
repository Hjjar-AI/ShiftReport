package com.hos.rushdpatients

import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.ui.about.rememberLaunchSplashState
import com.hos.rushdpatients.ui.demo.DemoWardScreen
import com.hos.rushdpatients.ui.demo.DemoWardViewModel
import com.hos.rushdpatients.ui.theme.AppAppearance
import com.hos.rushdpatients.ui.theme.AppSystemBars
import com.hos.rushdpatients.ui.theme.AppThemePreset
import com.hos.rushdpatients.ui.theme.RushdPatientsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DemoActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val splash = rememberLaunchSplashState()
            val viewModel: DemoWardViewModel = hiltViewModel()
            val appearance by viewModel.demoAppearance.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                RushdPatientsTheme(
                    preset = appearance?.accent ?: AppThemePreset.VANILLA,
                    appearance = appearance?.appearance ?: AppAppearance.LIGHT
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (splash.visible) {
                            AppSystemBars()
                            LaunchIntro(splash)
                        } else {
                            DemoWardScreen(onExit = ::finish, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
