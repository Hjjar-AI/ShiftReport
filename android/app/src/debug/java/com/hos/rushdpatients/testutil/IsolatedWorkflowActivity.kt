package com.hos.rushdpatients.testutil

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint

/** Instrumentation supplies isolated models before composition; no default clinical workspace. */
@AndroidEntryPoint
class IsolatedWorkflowActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkNotNull(install) { "An isolated fixture must be installed before launch" }(this)
        setContent { checkNotNull(content)(this) }
    }
    companion object {
        var install: ((IsolatedWorkflowActivity) -> Unit)? = null
        var content: (@Composable (IsolatedWorkflowActivity) -> Unit)? = null
        fun clearFixture() { install = null; content = null }
    }
}
