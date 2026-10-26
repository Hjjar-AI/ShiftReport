package com.hos.rushdpatients.ui.report

/** Pure outcome transition, independent of translated messages and mutable UI state. */
internal data class ReportSendFailure(
    val publication: ReportStepOutcome?,
    val delivery: ReportStepOutcome,
    val canRetry: Boolean
)

internal fun reportSendFailure(
    publication: ReportStepOutcome?, delivery: ReportStepOutcome?, delivered: Int,
    definiteRejection: Boolean, uncertainDelivery: Boolean, cancelled: Boolean
): ReportSendFailure {
    val nextPublication = if (publication == ReportStepOutcome.IN_PROGRESS) {
        if (definiteRejection) ReportStepOutcome.NOT_STARTED else ReportStepOutcome.UNCERTAIN
    } else publication
    val nextDelivery = when {
        uncertainDelivery || (cancelled && delivery == ReportStepOutcome.IN_PROGRESS) -> ReportStepOutcome.UNCERTAIN
        delivered > 0 -> ReportStepOutcome.PARTIAL
        else -> ReportStepOutcome.NOT_STARTED
    }
    return ReportSendFailure(nextPublication, nextDelivery,
        !cancelled && delivered == 0 && nextPublication != ReportStepOutcome.UNCERTAIN && nextDelivery != ReportStepOutcome.UNCERTAIN)
}
