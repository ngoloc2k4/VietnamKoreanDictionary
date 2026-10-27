package com.example.ai.gateway

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MultiProviderAiGateway(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("floss_ai_gateway_prefs", Context.MODE_PRIVATE)

    private val openAiAdapter = OpenAiProtocolAdapter()
    private val anthropicAdapter = AnthropicProtocolAdapter()
    private val geminiAdapter = GeminiProtocolAdapter()

    private val _providers = MutableStateFlow<List<AiProviderConfig>>(emptyList())
    val providers: StateFlow<List<AiProviderConfig>> = _providers.asStateFlow()

    private val _lastDebugResponse = MutableStateFlow<UnifiedAiResponse?>(null)
    val lastDebugResponse: StateFlow<UnifiedAiResponse?> = _lastDebugResponse.asStateFlow()

    init {
        loadProviders()
    }

    private fun loadProviders() {
        val savedJson = prefs.getString("providers_json", null)
        if (savedJson.isNullOrBlank()) {
            val defaults = getDefaultPresets()
            _providers.value = defaults
            saveProviders(defaults)
        } else {
            try {
                val array = JSONArray(savedJson)
                val list = mutableListOf<AiProviderConfig>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        AiProviderConfig(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            protocol = ProviderProtocol.valueOf(obj.getString("protocol")),
                            baseUrl = obj.getString("baseUrl"),
                            apiKey = obj.optString("apiKey", ""),
                            modelName = obj.getString("modelName"),
                            maxTokens = obj.optInt("maxTokens", 1024),
                            temperature = obj.optDouble("temperature", 0.7),
                            priority = obj.optInt("priority", i + 1),
                            isEnabled = obj.optBoolean("isEnabled", true),
                            isDefault = obj.optBoolean("isDefault", i == 0)
                        )
                    )
                }
                _providers.value = list
            } catch (e: Exception) {
                _providers.value = getDefaultPresets()
            }
        }
    }

    private fun saveProviders(list: List<AiProviderConfig>) {
        val array = JSONArray()
        for (p in list) {
            array.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("protocol", p.protocol.name)
                put("baseUrl", p.baseUrl)
                put("apiKey", p.apiKey)
                put("modelName", p.modelName)
                put("maxTokens", p.maxTokens)
                put("temperature", p.temperature)
                put("priority", p.priority)
                put("isEnabled", p.isEnabled)
                put("isDefault", p.isDefault)
            })
        }
        prefs.edit().putString("providers_json", array.toString()).apply()
        _providers.value = list
    }

    fun addOrUpdateProvider(config: AiProviderConfig) {
        val current = _providers.value.toMutableList()
        val index = current.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            current[index] = config
        } else {
            current.add(config)
        }

        if (config.isDefault) {
            for (i in 0 until current.size) {
                if (current[i].id != config.id) {
                    current[i] = current[i].copy(isDefault = false)
                }
            }
        }
        current.sortBy { it.priority }
        saveProviders(current)
    }

    fun deleteProvider(id: String) {
        val current = _providers.value.filter { it.id != id }.toMutableList()
        if (current.isNotEmpty() && current.none { it.isDefault }) {
            current[0] = current[0].copy(isDefault = true)
        }
        saveProviders(current)
    }

    fun setDefaultProvider(id: String) {
        val current = _providers.value.map { it.copy(isDefault = it.id == id) }
        saveProviders(current)
    }

    fun resetToDefaults() {
        val defaults = getDefaultPresets()
        saveProviders(defaults)
    }

    suspend fun executeWithFallback(request: UnifiedAiRequest): UnifiedAiResponse {
        val activeProviders = _providers.value
            .filter { it.isEnabled }
            .sortedWith(compareByDescending<AiProviderConfig> { it.isDefault }.thenBy { it.priority })

        if (activeProviders.isEmpty()) {
            val emptyRes = UnifiedAiResponse(
                content = "",
                isSuccess = false,
                errorMessage = "Chưa kích hoạt AI Provider nào! Vui lòng vào Cài đặt để cấu hình API Key."
            )
            _lastDebugResponse.value = emptyRes
            return emptyRes
        }

        var lastError = ""
        for (provider in activeProviders) {
            // Check if API key is present for providers that require it
            if (provider.apiKey.isBlank() && !provider.baseUrl.contains("localhost") && !provider.baseUrl.contains("10.0.2.2")) {
                lastError = "Provider ${provider.name} chưa điền API Key."
                continue
            }

            val adapter = getAdapter(provider.protocol)
            val response = adapter.execute(provider, request)
            _lastDebugResponse.value = response

            if (response.isSuccess && response.content.isNotBlank()) {
                return response
            } else {
                lastError = "${provider.name}: ${response.errorMessage ?: "Không có phản hồi"}"
                // Proceed to next provider in fallback chain
            }
        }

        val failResponse = UnifiedAiResponse(
            content = "",
            isSuccess = false,
            errorMessage = "Tất cả providers trong chuỗi Fallback đều thất bại. Chi tiết: $lastError"
        )
        _lastDebugResponse.value = failResponse
        return failResponse
    }

    suspend fun testConnection(config: AiProviderConfig): UnifiedAiResponse {
        val testRequest = UnifiedAiRequest(
            messages = listOf(
                UnifiedAiMessage("user", "Chào bạn, hãy trả lời bằng 1 từ: 'OK'.")
            ),
            temperature = 0.1,
            maxTokens = 30
        )
        val adapter = getAdapter(config.protocol)
        val result = adapter.execute(config, testRequest)
        _lastDebugResponse.value = result
        return result
    }

    private fun getAdapter(protocol: ProviderProtocol): AiProtocolAdapter {
        return when (protocol) {
            ProviderProtocol.OPENAI_COMPATIBLE -> openAiAdapter
            ProviderProtocol.ANTHROPIC_COMPATIBLE -> anthropicAdapter
            ProviderProtocol.GEMINI -> geminiAdapter
        }
    }

    fun getDefaultPresets(): List<AiProviderConfig> {
        return listOf(
            AiProviderConfig(
                id = "preset_groq",
                name = "Groq (Llama 3.3 70B)",
                protocol = ProviderProtocol.OPENAI_COMPATIBLE,
                baseUrl = "https://api.groq.com/openai/v1",
                apiKey = "",
                modelName = "llama-3.3-70b-versatile",
                priority = 1,
                isDefault = true
            ),
            AiProviderConfig(
                id = "preset_openrouter",
                name = "OpenRouter",
                protocol = ProviderProtocol.OPENAI_COMPATIBLE,
                baseUrl = "https://openrouter.ai/api/v1",
                apiKey = "",
                modelName = "google/gemini-2.0-flash-exp:free",
                priority = 2,
                isDefault = false
            ),
            AiProviderConfig(
                id = "preset_deepseek",
                name = "DeepSeek (V3)",
                protocol = ProviderProtocol.OPENAI_COMPATIBLE,
                baseUrl = "https://api.deepseek.com/v1",
                apiKey = "",
                modelName = "deepseek-chat",
                priority = 3,
                isDefault = false
            ),
            AiProviderConfig(
                id = "preset_gemini",
                name = "Google Gemini",
                protocol = ProviderProtocol.GEMINI,
                baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                apiKey = "",
                modelName = "gemini-2.5-flash",
                priority = 4,
                isDefault = false
            ),
            AiProviderConfig(
                id = "preset_openai",
                name = "OpenAI (GPT-4o mini)",
                protocol = ProviderProtocol.OPENAI_COMPATIBLE,
                baseUrl = "https://api.openai.com/v1",
                apiKey = "",
                modelName = "gpt-4o-mini",
                priority = 5,
                isDefault = false
            ),
            AiProviderConfig(
                id = "preset_ollama",
                name = "Ollama (Local / LAN)",
                protocol = ProviderProtocol.OPENAI_COMPATIBLE,
                baseUrl = "http://10.0.2.2:11434/v1",
                apiKey = "",
                modelName = "qwen2.5:0.5b",
                priority = 6,
                isDefault = false
            )
        )
    }
}
