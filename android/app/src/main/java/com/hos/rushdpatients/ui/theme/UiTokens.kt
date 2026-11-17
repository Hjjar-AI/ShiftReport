package com.hos.rushdpatients.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Card density changes internal rhythm, never typography or action target sizes. */
data class PatientCardDensity(val padding: PaddingValues, val sectionGap: Dp)

object UiPatientCard {
    val compact = PatientCardDensity(
        PaddingValues(horizontal = UiSpacing.screen, vertical = UiSpacing.medium), UiSpacing.small)
    val normal = PatientCardDensity(
        PaddingValues(horizontal = UiSpacing.section, vertical = UiSpacing.screen), UiSpacing.screen)
    val listGap = UiSpacing.screen
    @Composable
    fun density(): PatientCardDensity {
        // Normal intentionally retains the compact card geometry used by the shipped UI.
        return compact
    }

    @Composable
    fun border(selected: Boolean = false): BorderStroke {
        val target = if (selected) UiBorder.stroke(selected = true) else UiBorder.cardStroke()
        val targetColor = if (selected) MaterialTheme.colorScheme.primary else
            lerp(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outline, UiOpacity.cardOutlineEmphasis)
        val color by animateColorAsState(targetColor,
            tween(LocalUiStyle.current.indicatorMillis), label = "patientSelectionBorder")
        return BorderStroke(target.width, color)
    }
}

/** Neutral surfaces and interactive emphasis; clinical severity has its own palette. */
object UiBorder {
    val hairline = 0.5.dp
    val strong = 1.5.dp
    val normalWidth = 1.dp
    val selectedWidth = 2.dp
    val focusedWidth = 2.dp

    @Composable
    fun cardStroke(): BorderStroke = BorderStroke(LocalUiStyle.current.normalBorder,
        lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant, UiOpacity.cardStrokeBlend))

    @Composable
    fun stroke(selected: Boolean = false, focused: Boolean = false): BorderStroke = BorderStroke(
        when { focused -> focusedWidth; selected -> LocalUiStyle.current.selectedBorder; else -> LocalUiStyle.current.normalBorder },
        if (selected || focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
}
