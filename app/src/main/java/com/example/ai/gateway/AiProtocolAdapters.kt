package com.example.ai.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface AiProtocolAdapter {
    suspend fun execute(
        config: AiProviderConfig,
        unifiedRequest: UnifiedAiRequest
    ): UnifiedAiResponse
}

class OpenAiProtocolAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) : AiProtocolAdapter {

    override suspend fun execute(
        config: AiProviderConfig,
        unifiedRequest: UnifiedAiRequest
    ): UnifiedAiResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val url = if (config.baseUrl.endsWith("/chat/completions")) {
            config.baseUrl
        } else {
            "${config.baseUrl.trimEnd('/')}/chat/completions"
        }

        val jsonBody = JSONObject().apply {
            put("model", config.modelName)
            put("temperature", unifiedRequest.temperature)
            put("max_tokens", unifiedRequest.maxTokens)

            val messagesArray = JSONArray()
            for (msg in unifiedRequest.messages) {
                messagesArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
            put("messages", messagesArray)
        }

        val requestBodyString = jsonBody.toString()
        val rawCurl = "curl -X POST \"$url\" \\\n" +
                "  -H \"Content-Type: application/json\" \\\n" +
                "  -H \"Authorization: Bearer ${config.maskedApiKey}\" \\\n" +
                "  -d '${requestBodyString.replace("'", "\\'")}'"

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBodyString.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")

        if (config.apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            val latency = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: responseBody
                } catch (e: Exception) {
                    responseBody.ifBlank { "HTTP Error ${response.code}: ${response.message}" }
                }
                return@withContext UnifiedAiResponse(
                    content = "",
                    latencyMs = latency,
                    providerName = config.name,
                    modelName = config.modelName,
                    rawCurlCommand = rawCurl,
                    isSuccess = false,
                    errorMessage = "Lỗi [${response.code}]: $errorMsg"
                )
            }

            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()

            val usage = json.optJSONObject("usage")
            val promptTokens = usage?.optInt("prompt_tokens") ?: 0
            val completionTokens = usage?.optInt("completion_tokens") ?: 0
            val totalTokens = usage?.optInt("total_tokens") ?: (promptTokens + completionTokens)

            UnifiedAiResponse(
                content = content.trim(),
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens,
                latencyMs = latency,
                providerName = config.name,
                modelName = config.modelName,
                rawCurlCommand = rawCurl,
                isSuccess = true
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            UnifiedAiResponse(
                content = "",
                latencyMs = latency,
                providerName = config.name,
                modelName = config.modelName,
                rawCurlCommand = rawCurl,
                isSuccess = false,
                errorMessage = "Lỗi kết nối: ${e.localizedMessage ?: e.message}"
            )
        }
    }
}

class AnthropicProtocolAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) : AiProtocolAdapter {

    override suspend fun execute(
        config: AiProviderConfig,
        unifiedRequest: UnifiedAiRequest
    ): UnifiedAiResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val url = if (config.baseUrl.endsWith("/messages")) {
            config.baseUrl
        } else {
            "${config.baseUrl.trimEnd('/')}/messages"
        }

        var systemPrompt = ""
        val messagesArray = JSONArray()

        for (msg in unifiedRequest.messages) {
            if (msg.role == "system") {
                systemPrompt = msg.content
            } else {
                messagesArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
        }

        val jsonBody = JSONObject().apply {
            put("model", config.modelName)
            put("max_tokens", unifiedRequest.maxTokens)
            put("temperature", unifiedRequest.temperature)
            if (systemPrompt.isNotBlank()) {
                put("system", systemPrompt)
            }
            put("messages", messagesArray)
        }

        val requestBodyString = jsonBody.toString()
        val rawCurl = "curl -X POST \"$url\" \\\n" +
                "  -H \"Content-Type: application/json\" \\\n" +
                "  -H \"x-api-key: ${config.maskedApiKey}\" \\\n" +
                "  -H \"anthropic-version: 2023-06-01\" \\\n" +
                "  -d '${requestBodyString.replace("'", "\\'")}'"

        val request = Request.Builder()
            .url(url)
            .post(requestBodyString.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .addHeader("x-api-key", config.apiKey.trim())
            .addHeader("anthropic-version", "2023-06-01")
            .build()

        try {
            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext UnifiedAiResponse(
                    content = "",
                    latencyMs = latency,
                    providerName = config.name,
                    modelName = config.modelName,
                    rawCurlCommand = rawCurl,
                    isSuccess = false,
                    errorMessage = "Lỗi Claude [${response.code}]: $responseBody"
                )
            }

            val json = JSONObject(responseBody)
            val contentArray = json.optJSONArray("content")
            val content = contentArray?.optJSONObject(0)?.optString("text").orEmpty()

            val usage = json.optJSONObject("usage")
            val inputTokens = usage?.optInt("input_tokens") ?: 0
            val outputTokens = usage?.optInt("output_tokens") ?: 0

            UnifiedAiResponse(
                content = content.trim(),
                promptTokens = inputTokens,
                completionTokens = outputTokens,
                totalTokens = inputTokens + outputTokens,
                latencyMs = latency,
                providerName = config.name,
                modelName = config.modelName,
                rawCurlCommand = rawCurl,
                isSuccess = true
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            UnifiedAiResponse(
                content = "",
                latencyMs = latency,
                providerName = config.name,
                modelName = config.modelName,
                rawCurlCommand = rawCurl,
                isSuccess = false,
                errorMessage = "Lỗi kết nối Claude: ${e.localizedMessage ?: e.message}"
            )
        }
    }
}

class GeminiProtocolAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) : AiProtocolAdapter {

    override suspend fun execute(
        config: AiProviderConfig,
        unifiedRequest: UnifiedAiRequest
    ): UnifiedAiResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val base = config.baseUrl.trimEnd('/')
        val url = "$base/models/${config.modelName}:generateContent?key=${config.apiKey.trim()}"

        var systemInstruction: JSONObject? = null
        val contentsArray = JSONArray()

        for (msg in unifiedRequest.messages) {
            if (msg.role == "system") {
                systemInstruction = JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                }
            } else {
                val role = if (msg.role == "assistant") "model" else "user"
                contentsArray.put(JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                })
            }
        }

        val jsonBody = JSONObject().apply {
            if (systemInstruction != null) {
                put("systemInstruction", systemInstruction)
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", unifiedRequest.temperature)
                put("maxOutputTokens", unifiedRequest.maxTokens)
            })
        }

        val requestBodyString = jsonBody.toString()
        val rawCurl = "curl -X POST \"$base/models/${config.modelName}:generateContent?key=YOUR_API_KEY\" \\\n" +
                "  -H \"Content-Type: application/json\" \\\n" +
                "  -d '${requestBodyString.replace("'", "\\'")}'"

        val request = Request.Builder()
            .url(url)
            .post(requestBodyString.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext UnifiedAiResponse(
                    content = "",
                    latencyMs = latency,
                    providerName = config.name,
                    modelName = config.modelName,
                    rawCurlCommand = rawCurl,
                    isSuccess = false,
                    errorMessage = "Lỗi Gemini [${response.code}]: $responseBody"
                )
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val candidate0 = candidates?.optJSONObject(0)
            val parts = candidate0?.optJSONObject("content")?.optJSONArray("parts")
            val content = parts?.optJSONObject(0)?.optString("text").orEmpty()

            val usage = json.optJSONObject("usageMetadata")
            val promptTokens = usage?.optInt("promptTokenCount") ?: 0
            val candidateTokens = usage?.optInt("candidatesTokenCount") ?: 0

            UnifiedAiResponse(
                content = content.trim(),
                promptTokens = promptTokens,
                completionTokens = candidateTokens,
                totalTokens = promptTokens + candidateTokens,
                latencyMs = latency,
                providerName = config.name,
                modelName = config.modelName,
                rawCurlCommand = rawCurl,
                isSuccess = true
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            UnifiedAiResponse(
                content = "",
                latencyMs = latency,
                providerName = config.name,
                modelName = config.modelName,
                rawCurlCommand = rawCurl,
                isSuccess = false,
                errorMessage = "Lỗi kết nối Gemini: ${e.localizedMessage ?: e.message}"
            )
        }
    }
}
