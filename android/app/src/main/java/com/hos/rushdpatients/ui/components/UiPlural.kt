package com.hos.rushdpatients.ui.components

import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Arabic UI grammar must also work when the device's system language is not Arabic. */
@Composable
fun uiPlural(@PluralsRes resourceId: Int, quantity: Int): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = remember(context, configuration) {
        context.createConfigurationContext(Configuration(configuration).apply { setLocale(Locale("ar")) }).resources
    }
    return com.hos.rushdpatients.util.ArabicNumbers.toAscii(resources.getQuantityString(resourceId, quantity, uiNumber(quantity)))
}
