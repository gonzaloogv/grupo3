package ar.com.freno.shared.contract

import kotlinx.serialization.Serializable

@Serializable
data class AnalysisRequest(
    val eventId: String,
    val source: NotificationSource,
    val text: String,
    val contentIncomplete: Boolean,
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
)

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
