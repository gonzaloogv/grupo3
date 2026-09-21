package com.grupo3.freno.orchestration

import com.grupo3.freno.model.ExplanationSource
import com.grupo3.freno.model.RecommendedAction
import com.grupo3.freno.model.RiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class HttpRiskAnalyzer(
    private val baseUrl: String = "http://192.168.0.220:8080",
    private val token: String = "replace-with-a-long-random-token",
) : RiskAnalyzer {

    override suspend fun analyze(request: AnalysisRequest): AnalysisResult = withContext(Dispatchers.IO) {
        val extractedUrls = extractUrls(request.text)
        val endpoint = URL("$baseUrl/v1/analyze")

        runCatching {
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Authorization", "Bearer $token")
            }

            val mappedSource = when (request.source.uppercase()) {
                "WHATSAPP" -> "WHATSAPP"
                else -> "SMS"
            }

            val payload = JSONObject().apply {
                put("eventId", request.eventId)
                put("source", mappedSource)
                put("text", request.text)
                put("contentIncomplete", request.contentIncomplete)
                put("urls", JSONArray(extractedUrls))
                put("locale", "es-AR")
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val statusCode = connection.responseCode
            if (statusCode == 200) {
                val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(responseText)

                val riskStr = json.optString("risk", "UNKNOWN")
                val categoryStr = json.optString("category", "UNKNOWN")
                val reasonCodeStr = json.optString("reasonCode", "UNKNOWN")
                val reasonSimpleStr = json.optString("reasonSimple", "Análisis completado sin justificación detallada.")
                val actionStr = json.optString("action", "NONE")
                val sourceStr = json.optString("explanationSource", "TEMPLATE")

                AnalysisResult(
                    risk = runCatching { RiskLevel.valueOf(riskStr) }.getOrDefault(RiskLevel.UNKNOWN),
                    category = categoryStr,
                    reasonCode = reasonCodeStr,
                    reasonSimple = reasonSimpleStr,
                    action = runCatching { RecommendedAction.valueOf(actionStr) }.getOrDefault(RecommendedAction.NONE),
                    explanationSource = runCatching { ExplanationSource.valueOf(sourceStr) }.getOrDefault(ExplanationSource.TEMPLATE),
                )
            } else {
                fallbackResult("El servidor respondió con código $statusCode.")
            }
        }.getOrElse { exception ->
            fallbackResult("No se pudo conectar con el servidor en $baseUrl: ${exception.localizedMessage ?: "error de red"}.")
        }
    }

    private fun extractUrls(text: String): List<String> {
        val regex = Regex("""https?://[^\s/$.?#].[^\s]*""", RegexOption.IGNORE_CASE)
        return regex.findAll(text).map { it.value.trimEnd('.', ',', ';', ')') }.take(3).toList()
    }

    private fun fallbackResult(message: String) = AnalysisResult(
        risk = RiskLevel.UNKNOWN,
        category = "UNKNOWN",
        reasonCode = "ANALYSIS_UNAVAILABLE",
        reasonSimple = message,
        action = RecommendedAction.NONE,
        explanationSource = ExplanationSource.UNAVAILABLE,
    )
}
