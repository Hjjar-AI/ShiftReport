package com.hos.rushdpatients.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colors whose meaning stays stable across app themes and dynamic color. */
data class ClinicalColors(
    val normal: Color,
    val onNormal: Color,
    val normalContainer: Color,
    val onNormalContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val urgent: Color,
    val onUrgent: Color,
    val urgentContainer: Color,
    val onUrgentContainer: Color,
    val pending: Color,
    val onPending: Color,
    val pendingContainer: Color,
    val onPendingContainer: Color,
    val conflict: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color
)

internal val LightClinicalColors = ClinicalColors(
    normal = Color(0xFF0B6E69),
    onNormal = Color.White,
    normalContainer = Color(0xFFD7F2EE),
    onNormalContainer = Color(0xFF073B38),
    warning = Color(0xFF8A5100),
    onWarning = Color.White,
    warningContainer = Color(0xFFFFE7B2),
    onWarningContainer = Color(0xFF432A00),
    urgent = Color(0xFFB3261E),
    onUrgent = Color.White,
    urgentContainer = Color(0xFFFFDAD6),
    onUrgentContainer = Color(0xFF410002),
    pending = Color(0xFF6750A4),
    onPending = Color.White,
    pendingContainer = Color(0xFFEADDFF),
    onPendingContainer = Color(0xFF21005D),
    conflict = Color(0xFFBA1A1A),
    success = Color(0xFF146C43),
    successContainer = Color(0xFFD5F2E2),
    onSuccessContainer = Color(0xFF073821)
)

internal val DarkClinicalColors = ClinicalColors(
    normal = Color(0xFF76D7CE),
    onNormal = Color(0xFF003735),
    normalContainer = Color(0xFF124E4A),
    onNormalContainer = Color(0xFFC6F4EF),
    warning = Color(0xFFFFC45C),
    onWarning = Color(0xFF472A00),
    warningContainer = Color(0xFF5F3B00),
    onWarningContainer = Color(0xFFFFDEA0),
    urgent = Color(0xFFFFB4AB),
    onUrgent = Color(0xFF690005),
    urgentContainer = Color(0xFF93000A),
    onUrgentContainer = Color(0xFFFFDAD6),
    pending = Color(0xFFD0BCFF),
    onPending = Color(0xFF381E72),
    pendingContainer = Color(0xFF4F378B),
    onPendingContainer = Color(0xFFEADDFF),
    conflict = Color(0xFFFFB4AB),
    success = Color(0xFF79D6A3),
    successContainer = Color(0xFF155333),
    onSuccessContainer = Color(0xFFC0F0D2)
)

val LocalClinicalColors = staticCompositionLocalOf { LightClinicalColors }
