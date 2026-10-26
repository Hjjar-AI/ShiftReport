package com.hos.rushdpatients.testutil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.hos.rushdpatients.ui.about.LaunchSplashState
import com.hos.rushdpatients.ui.about.rememberLaunchSplashState

/** Exercises the real launch state across Activity recreation without opening clinical storage. */
class SplashProbeActivity : ComponentActivity() {
    internal lateinit var splash: LaunchSplashState
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            splash = rememberLaunchSplashState()
            Text(if (splash.visible) "synthetic splash ${splash.remainingSeconds}" else "finished")
        }
    }
    internal fun ready() = ::splash.isInitialized
}
