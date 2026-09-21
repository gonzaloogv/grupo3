package ar.com.freno.shared.contract

import kotlinx.serialization.Serializable

@Serializable
data class AnalysisRequest(
    val eventId: String,
    val source: NotificationSource,
    val text: String,
    val contentIncomplete: Boolean,
    val urls: List<String> = emptyList(),
    val locale: String,
)

@Serializable
data class AnalysisResult(
    val eventId: String,
    val risk: Risk,
    val category: Category,
    val reasonCode: ReasonCode,
    val reasonSimple: String,
    val action: Action,
    val analyzer: Analyzer,
    val model: String?,
    val promptVersion: String,
    val explanationSource: ExplanationSource,
    val decisionSources: List<DecisionSource>,
    val urlAssessment: UrlAssessment,
)

@Serializable
data class UrlAssessment(
    val status: UrlAssessmentStatus,
    val provider: UrlAssessmentProvider,
    val threatTypes: List<UrlThreatType>,
)

@Serializable
enum class DecisionSource {
    GEMINI,
    GOOGLE_SAFE_BROWSING,
    LOCAL_POLICY,
}

@Serializable
enum class UrlAssessmentStatus {
    NO_URL,
    MATCH,
    NO_MATCH,
    UNAVAILABLE,
}

@Serializable
enum class UrlAssessmentProvider {
    NONE,
    GOOGLE_SAFE_BROWSING,
}

@Serializable
enum class UrlThreatType {
    SOCIAL_ENGINEERING,
    MALWARE,
}

@Serializable
enum class NotificationSource {
    SMS,
    WHATSAPP,
}

@Serializable
enum class Risk {
    HIGH,
    REVIEW,
    LOW,
    UNKNOWN,
}

@Serializable
enum class Category {
    FAMILY_IMPERSONATION,
    BANK_PHISHING,
    CODE_REQUEST,
    OTHER,
    NONE,
    UNKNOWN,
}

@Serializable
enum class ReasonCode {
    NEW_NUMBER_AND_URGENT_PAYMENT,
    CREDENTIAL_REQUEST,
    CODE_SHARING_REQUEST,
    INSUFFICIENT_CONTEXT,
    NO_CLEAR_SIGNAL,
    OTHER_SIGNAL,
    ANALYSIS_UNAVAILABLE,
    URL_LISTED_AS_THREAT,
}

@Serializable
enum class Action {
    VERIFY_KNOWN_CONTACT,
    AVOID_LINK_AND_VERIFY,
    DO_NOT_SHARE_CODE,
    NONE,
}

@Serializable
enum class Analyzer {
    GEMINI,
    UNAVAILABLE,
    FAKE,
}

@Serializable
enum class ExplanationSource {
    GEMINI,
    TEMPLATE,
    UNAVAILABLE,
}
