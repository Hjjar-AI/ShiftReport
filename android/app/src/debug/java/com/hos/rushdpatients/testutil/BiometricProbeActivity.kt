package com.hos.rushdpatients.testutil

import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.hos.rushdpatients.domain.auth.BiometricHelper

/** Debug-only hardware probe; does not open a session or clinical database. */
class BiometricProbeActivity : FragmentActivity() {
    @Volatile var outcome: String = "waiting"
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Synthetic fingerprint verification" })
    }
    fun authenticate() {
        BiometricHelper(this).prompt(this,
            onSuccess = { outcome = "success" },
            onCancel = { outcome = "cancelled" },
            onError = { outcome = "error" })
    }
}
