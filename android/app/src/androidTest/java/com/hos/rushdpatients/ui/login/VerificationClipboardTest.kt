package com.hos.rushdpatients.ui.login

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VerificationClipboardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = compose.activity
    private val clipboard get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var previous: ClipData? = null
    private val doctor = Doctor(id = "synthetic", fullName = "أرز", firstName = "أرز", lastName = "شجرة", gender = Gender.MALE)
    private val nonce = "SYNTHETIC-CODE"
    private var visible by mutableStateOf(true)
    private var seconds by mutableStateOf(30)
    @Before fun captureClipboard() {
        compose.waitUntil(5000) { compose.activity.window.decorView.hasWindowFocus() }
        compose.runOnIdle { previous = clipboard.primaryClip }
    }
    @After fun restoreClipboard() {
        compose.runOnIdle {
            val label = clipboard.primaryClip?.description?.label?.toString()
            if (label in setOf("ShiftReport verification", "synthetic-user-replacement") || label == null) {
                previous?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip()
            }
        }
    }
    private fun show() {
        compose.setContent { MaterialTheme {
            if (visible) VerifyingView(LoginStep.Verifying(doctor, nonce, seconds), {}) else Text("finished")
        } }
    }
    private fun copy() = compose.onNodeWithText(context.getString(R.string.ui_copy)).performClick()
    @Test fun copiedCodeIsMarkedSensitiveAndExact() {
        show(); copy()
        compose.runOnIdle {
            val clip = requireNotNull(clipboard.primaryClip)
            assertEquals(nonce, clip.getItemAt(0).text.toString())
            assertTrue(clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true)
        }
    }
    @Test fun expiryClearsOnlyTheMatchingCodeAndDisablesCopy() {
        show(); copy(); compose.runOnIdle { seconds = 0 }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.ui_copy)).assertIsNotEnabled()
        compose.runOnIdle { assertNotEquals(nonce, clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
    }
    @Test fun leavingVerificationClearsItsCode() {
        show(); copy(); compose.runOnIdle { visible = false }; compose.waitForIdle()
        compose.runOnIdle { assertNotEquals(nonce, clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
    }
    @Test fun cleanupPreservesLaterUserClipboardContent() {
        show(); copy()
        compose.runOnIdle {
            clipboard.setPrimaryClip(ClipData.newPlainText("synthetic-user-replacement", "synthetic later copy"))
            visible = false
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("synthetic later copy", clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
    }
}
