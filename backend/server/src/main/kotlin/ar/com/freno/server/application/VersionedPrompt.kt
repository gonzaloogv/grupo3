package ar.com.freno.server.application

import java.security.MessageDigest

class VersionedPrompt private constructor(val version: String, val text: String) {
    val sha256: String = sha256(text.toByteArray(Charsets.UTF_8))

    companion object {
        fun load(version: String): VersionedPrompt {
            require(version.matches(Regex("freno-v[0-9]+"))) { "Invalid PROMPT_VERSION" }
            val resource = VersionedPrompt::class.java.getResourceAsStream("/prompts/$version.txt")
            requireNotNull(resource) { "Unknown PROMPT_VERSION: $version" }
            val text = resource.bufferedReader(Charsets.UTF_8).use { it.readText() }
                .replace("\r\n", "\n").trim()
            require(text.isNotBlank()) { "Empty prompt resource" }
            return VersionedPrompt(version, text)
        }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
