package com.hos.rushdpatients.ui.components

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Resource lookup shared by composition, captured callbacks and UI state producers. */
class UiStrings(private val resources: Resources) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.resources)

    operator fun invoke(@StringRes id: Int, vararg arguments: Any?): String =
        com.hos.rushdpatients.util.ArabicNumbers.toAscii(
            if (arguments.isEmpty()) resources.getString(id) else resources.getString(id, *arguments))
}

@Composable
fun rememberUiStrings(): UiStrings {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { UiStrings(context.resources) }
}
