package com.hos.rushdpatients.ui.ward

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

/** Production dialog, recording controller only: never opens storage or transport. */
@RunWith(AndroidJUnit4::class)
class FetchDataDialogTest {
    @get:Rule val compose = createComposeRule()
    private val confirm = "استبدل النسخة المنشورة بنسختي الحالية"
    private val calls = mutableListOf<String>()
    private var dismissed = 0
    private var showing by mutableStateOf(true)
    private val controller = Proxy.newProxyInstance(WardController::class.java.classLoader,
        arrayOf(WardController::class.java)) { proxy, method, arguments ->
        when (method.name) {
            "toString" -> "Synthetic fetch-dialog controller"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === arguments?.firstOrNull()
            "dismissPublicationReview", "bringLatestData", "reviewPendingPublication", "confirmPendingPublication" -> {
                calls += method.name
                Unit
            }
            else -> error("Unexpected controller call: ${method.name}")
        }
    } as WardController

    private fun show(pendingChoice: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    if (showing) WardDataReviewDialogs(
                        state = WardUiState(fetchChoiceRequired = pendingChoice),
                        viewModel = controller, confirmLatest = !pendingChoice,
                        onDismissLatest = { dismissed++; showing = false })
                }
            }
        }
    }

    @Test fun titleActionAndExplanationClearlyDescribeFetchingWithoutSideEffects() {
        show()
        compose.onNodeWithText("جلب أحدث نسخة").assertIsDisplayed()
        compose.onNodeWithText(confirm).assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("إلغاء").assertIsDisplayed()
        compose.onNodeWithText("تعديلاتك غير المنشورة", substring = true).assertExists()
        compose.onNodeWithText("تراجع عن آخر جلب", substring = true).assertExists()
        compose.onNodeWithText("لن يغيّر الجلب النسخة المنشورة", substring = true).assertExists()
        compose.onNodeWithText("استبدال نسختي بالنسخة المنشورة").assertDoesNotExist()
        compose.runOnIdle { assertTrue(calls.isEmpty()); assertEquals(0, dismissed) }
    }

    @Test fun cancelDismissesWithoutFetchingOrPublishing() {
        show()
        compose.onNodeWithText("إلغاء").performClick()
        compose.onNodeWithText("جلب أحدث نسخة").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, dismissed)
            assertEquals(listOf("dismissPublicationReview"), calls)
        }
    }

    @Test fun correctedActionDismissesThenFetchesExactlyOnceWithoutPublishing() {
        show()
        compose.onNodeWithText(confirm).performClick()
        compose.onNodeWithText(confirm).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, dismissed)
            assertEquals(listOf("dismissPublicationReview", "bringLatestData"), calls)
        }
    }

    @Test fun pendingLocalChangesUseTheSameFetchActionAndNeverPublish() {
        show(pendingChoice = true)
        compose.onNodeWithText("جلب أحدث نسخة").assertIsDisplayed()
        compose.onNodeWithText(confirm).performClick()
        compose.runOnIdle {
            assertEquals(1, dismissed)
            assertEquals(listOf("dismissPublicationReview", "bringLatestData"), calls)
        }
    }
}
