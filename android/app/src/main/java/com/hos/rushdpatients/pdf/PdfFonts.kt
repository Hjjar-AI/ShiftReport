package com.hos.rushdpatients.pdf

import android.content.Context
import android.graphics.Typeface

/** Bundled offline PDF faces, independent of device fonts and app typography. */
internal class PdfFonts(context: Context) {
    val regular: Typeface = Typeface.createFromAsset(context.assets, "fonts/Tajawal-Regular.ttf")
    val bold: Typeface = Typeface.createFromAsset(context.assets, "fonts/Tajawal-Bold.ttf")
}
