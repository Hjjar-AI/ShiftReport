package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppSearchField
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun WardPatientSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit
) {
    val uiText = rememberUiStrings()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    AppSearchField(query = query, onQueryChange = onQueryChange,
        label = uiText(R.string.ui_ward_patient_search_field_001),
        modifier = Modifier.fillMaxWidth().focusRequester(focus))
    // Only an explicit toolbar action opens the keyboard; restoring a query does not.
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focus.requestFocus()
            keyboard?.show()
            onFocusRequested()
        }
    }
}
