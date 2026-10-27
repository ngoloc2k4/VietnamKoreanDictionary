package com.example.ai.gateway

import org.json.JSONArray
import org.json.JSONObject

enum class ProviderProtocol(val displayName: String, val defaultBaseUrl: String, val defaultModel: String) {
    OPENAI_COMPATIBLE(
        "OpenAI / Groq / DeepSeek / Ollama",
        "https://api.openai.com/v1",
        "gpt-4o-mini"
    ),
    ANTHROPIC_COMPATIBLE(
        "Anthropic Claude",
        "https://api.anthropic.com/v1",
        "claude-3-5-haiku-20241022"
    ),
    GEMINI(
        "Google Gemini",
        "https://generativelanguage.googleapis.com/v1beta",
        "gemini-2.5-flash"
    )
}

data class AiProviderConfig(
    val id: String,
    val name: String,
    val protocol: ProviderProtocol,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val maxTokens: Int = 1024,
    val temperature: Double = 0.7,
    val priority: Int = 1,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false
) {
    val maskedApiKey: String
        get() = if (apiKey.length > 8) "${apiKey.take(4)}...${apiKey.takeLast(4)}" else if (apiKey.isNotEmpty()) "••••••••" else "Chưa có key"
}

data class UnifiedAiMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

data class UnifiedAiRequest(
    val messages: List<UnifiedAiMessage>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 1024
)

data class UnifiedAiResponse(
    val content: String,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val latencyMs: Long = 0L,
    val providerName: String = "",
    val modelName: String = "",
    val rawCurlCommand: String = "",
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)
