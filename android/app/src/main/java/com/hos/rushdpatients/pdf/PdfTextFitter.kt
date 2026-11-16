package com.hos.rushdpatients.pdf

import android.graphics.Paint

/** Retains the largest representable font size that satisfies the layout's fit check. */
internal object PdfTextFitter {
    fun fit(source: Paint, minimumSize: Float = 0f, fits: (Paint) -> Boolean): Paint {
        val paint = Paint(source)
        if (fits(paint)) return paint

        var upper = paint.textSize
        var lower = minimumSize.coerceIn(0f, upper)
        paint.textSize = lower
        if (!fits(paint)) return paint

        while (true) {
            val middle = lower + (upper - lower) / 2f
            if (middle <= lower || middle >= upper) break
            paint.textSize = middle
            if (fits(paint)) lower = middle else upper = middle
        }
        paint.textSize = lower
        return paint
    }

    /** [text] must have the same direction formatting used when drawing it. */
    fun singleLine(text: String, source: Paint, maxWidth: Float, minimumSize: Float = 0f): Paint =
        fit(source, minimumSize) { it.measureText(text) <= maxWidth }
}
