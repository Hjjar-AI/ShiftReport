package com.hos.rushdpatients.pdf

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.hos.rushdpatients.R

/** The bundled offline faces are shared with the UI; PDF export resolves them on IO. */
internal class PdfFonts(context: Context) {
    val regular: Typeface = checkNotNull(ResourcesCompat.getFont(context, R.font.tajawal_regular))
    val bold: Typeface = checkNotNull(ResourcesCompat.getFont(context, R.font.tajawal_bold))
}
