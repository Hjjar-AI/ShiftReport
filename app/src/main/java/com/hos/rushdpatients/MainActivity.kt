package com.hos.rushdpatients

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.hos.rushdpatients.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themePreset by themeViewModel.preset.collectAsStateWithLifecycle()
            val fontScale by themeViewModel.fontScale.collectAsStateWithLifecycle()

            val baseDensity = LocalDensity.current
            // Only fontScale changes; density (px per dp) stays as the system set it.
            // The multiplier stacks on top of any system font scale the user has set.
            val scaledDensity = remember(baseDensity, fontScale) {
                Density(
                    density = baseDensity.density,
                    fontScale = (baseDensity.fontScale * fontScale.multiplier)
                        .coerceIn(0.85f, 2.0f)
                )
            }

            RushdPatientsTheme(preset = themePreset) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalDensity provides scaledDensity
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        WardAppRoot(activity = this)
                    }
                }
            }
        }
    }
}
