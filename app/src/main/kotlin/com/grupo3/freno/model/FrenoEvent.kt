package com.grupo3.freno.model

data class FrenoEvent(
    val id: String,
    val source: String,
    val sender: String,
    val preview: String,
    val reason: String,
    val whenLabel: String,
    val trusted: Boolean = false,
    val analysisStatus: AnalysisStatus = AnalysisStatus.COMPLETED,
    val risk: RiskLevel = RiskLevel.HIGH,
    val category: String = "OTHER",
    val reasonCode: String = "OTHER_SIGNAL",
    val action: RecommendedAction = RecommendedAction.VERIFY_KNOWN_CONTACT,
    val explanationSource: ExplanationSource = ExplanationSource.TEMPLATE,
    val contentIncomplete: Boolean = false,
)

enum class AnalysisStatus { ANALYZING, COMPLETED, FAILED }

enum class RiskLevel { HIGH, REVIEW, LOW, UNKNOWN }

enum class RecommendedAction {
    VERIFY_KNOWN_CONTACT,
    AVOID_LINK_AND_VERIFY,
    DO_NOT_SHARE_CODE,
    NONE,
}

enum class ExplanationSource { GEMINI, TEMPLATE, UNAVAILABLE }
