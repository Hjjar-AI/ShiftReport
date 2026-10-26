package com.hos.rushdpatients.pdf

import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfTextWrappingTest {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f }
    @Test fun longUnbrokenClinicalTextNeverEscapesColumnAfterThousandLines() {
        val text = "W".repeat(1500)
        val width = paint.measureText("W") * 1.1f
        val lines = PdfTextWrapper.wrap(text, paint, width)
        assertEquals(text, lines.joinToString(""))
        assertTrue(lines.size > 1000)
        assertTrue(lines.all { paint.measureText(it) <= width + 0.1f })
    }
    @Test fun narrowColumnsDoNotSplitUtf16SurrogatePairs() {
        val text = "💊".repeat(20)
        val lines = PdfTextWrapper.wrap(text, paint, 1f)
        assertEquals(text, lines.joinToString(""))
        assertTrue(lines.all { !Character.isHighSurrogate(it.last()) && !Character.isLowSurrogate(it.first()) })
    }
    @Test fun arabicClinicalLinesRetainEveryNonWhitespaceCharacterWithinMeasuredWidth() {
        val text = "نتيجة تجريبية — مراجعة 12.5 mg\nمتابعة طبية تجريبية ".repeat(20)
        val width = 150f
        val lines = PdfTextWrapper.wrap(text, paint, width)
        assertEquals(text.filterNot(Char::isWhitespace), lines.joinToString("").filterNot(Char::isWhitespace))
        assertTrue(lines.all { paint.measureText(it) <= width + 0.1f })
    }
    @Test fun invalidColumnWidthsFailExplicitly() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { width ->
            assertThrows(IllegalArgumentException::class.java) { PdfTextWrapper.wrap("clinical", paint, width) }
        }
    }
}
