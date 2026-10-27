package com.example.util

import com.example.ai.gateway.MultiProviderAiGateway
import com.example.ai.gateway.UnifiedAiMessage
import com.example.ai.gateway.UnifiedAiRequest
import com.example.ai.tier2.LocalTier2InferenceEngine
import com.example.data.model.WordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class TranslationEngine(
    val id: String,
    val title: String,
    val subtitle: String,
    val isOnline: Boolean
) {
    GOOGLE_TRANSLATE(
        id = "google",
        title = "Google Translate",
        subtitle = "Tốc độ nhanh, dịch thông dụng (Miễn phí, không cần Key)",
        isOnline = true
    ),
    DEEPL(
        id = "deepl",
        title = "DeepL",
        subtitle = "Văn phong tự nhiên, chuẩn sắc thái bản ngữ",
        isOnline = true
    ),
    LOCAL_MODEL(
        id = "local_tier2",
        title = "Model Local (Tầng 2)",
        subtitle = "Ngoại tuyến 100%, bảo mật on-device, kèm phân tích ngữ pháp",
        isOnline = false
    ),
    CLOUD_AI(
        id = "cloud_ai",
        title = "AI Model lớn (Online)",
        subtitle = "Groq, OpenRouter, Gemini, Claude (Cần cấu hình API Key)",
        isOnline = true
    )
}

object BilingualTranslationService {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Dịch thông qua Google Translate unofficial endpoint (nhanh, chuẩn, không cần key)
     */
    suspend fun translateWithGoogle(text: String, fromKorean: Boolean): String = withContext(Dispatchers.IO) {
        val sl = if (fromKorean) "ko" else "vi"
        val tl = if (fromKorean) "vi" else "ko"
        val encoded = URLEncoder.encode(text, "UTF-8")

        // 1. Thử clients5.google.com dict-chrome-ex endpoint
        try {
            val url1 = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=$sl&tl=$tl&q=$encoded"
            val req1 = Request.Builder()
                .url(url1)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val res1 = httpClient.newCall(req1).execute()
            if (res1.isSuccessful) {
                val body = res1.body?.string() ?: ""
                if (body.startsWith("[")) {
                    val jsonArray = JSONArray(body)
                    val sb = StringBuilder()
                    for (i in 0 until jsonArray.length()) {
                        sb.append(jsonArray.getString(i))
                    }
                    val result = sb.toString().trim()
                    if (result.isNotEmpty()) return@withContext result
                }
            }
        } catch (_: Exception) {}

        // 2. Thử translate.googleapis.com fallback
        try {
            val url2 = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encoded"
            val req2 = Request.Builder()
                .url(url2)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build()
            val res2 = httpClient.newCall(req2).execute()
            if (res2.isSuccessful) {
                val body = res2.body?.string() ?: ""
                if (body.startsWith("[")) {
                    val outer = JSONArray(body)
                    val inner = outer.getJSONArray(0)
                    val sb = StringBuilder()
                    for (i in 0 until inner.length()) {
                        val part = inner.getJSONArray(i)
                        sb.append(part.getString(0))
                    }
                    val result = sb.toString().trim()
                    if (result.isNotEmpty()) return@withContext result
                }
            }
        } catch (_: Exception) {}

        throw Exception("Không thể kết nối đến Google Translate. Vui lòng kiểm tra lại kết nối mạng.")
    }

    /**
     * Dịch thông qua DeepL
     */
    suspend fun translateWithDeepL(
        text: String,
        fromKorean: Boolean,
        deepLApiKey: String? = null
    ): String = withContext(Dispatchers.IO) {
        val sl = if (fromKorean) "KO" else "VI"
        val tl = if (fromKorean) "VI" else "KO"

        // 1. Thử DeepL Official API nếu có key
        if (!deepLApiKey.isNullOrBlank()) {
            try {
                val url = if (deepLApiKey.endsWith(":fx")) "https://api-free.deepl.com/v2/translate" else "https://api.deepl.com/v2/translate"
                val json = JSONObject().apply {
                    put("text", JSONArray().put(text))
                    put("source_lang", sl)
                    put("target_lang", tl)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "DeepL-Auth-Key $deepLApiKey")
                    .post(body)
                    .build()
                val res = httpClient.newCall(req).execute()
                if (res.isSuccessful) {
                    val resBody = res.body?.string() ?: ""
                    val jsonObj = JSONObject(resBody)
                    val translations = jsonObj.getJSONArray("translations")
                    if (translations.length() > 0) {
                        return@withContext translations.getJSONObject(0).getString("text")
                    }
                }
            } catch (e: Exception) {
                AppLogManager.log("DeepL", "Lỗi DeepL Official API: ${e.message}", "WARN")
            }
        }

        // 2. Thử DeepLX web public proxy
        val deeplxEndpoints = listOf(
            "https://api.deeplx.org/translate",
            "https://deeplx.misen.top/translate"
        )
        for (proxyUrl in deeplxEndpoints) {
            try {
                val json = JSONObject().apply {
                    put("text", text)
                    put("source_lang", sl)
                    put("target_lang", tl)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url(proxyUrl)
                    .post(body)
                    .build()
                val res = httpClient.newCall(req).execute()
                if (res.isSuccessful) {
                    val resBody = res.body?.string() ?: ""
                    val jsonObj = JSONObject(resBody)
                    if (jsonObj.optInt("code") == 200 && jsonObj.has("data")) {
                        val data = jsonObj.getString("data")
                        if (data.isNotBlank() && !data.startsWith("http")) {
                            return@withContext data.trim()
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback sang Google Translate kèm chú thích
        try {
            val googleResult = translateWithGoogle(text, fromKorean)
            return@withContext "$googleResult\n\n*(Ghi chú: DeepL yêu cầu API Key riêng, hệ thống đang dùng bản dịch đối chứng tự động)*"
        } catch (_: Exception) {
            throw Exception("DeepL hiện không khả dụng. Bạn có thể chuyển sang 'Google Translate' hoặc 'Model Local' ngoại tuyến.")
        }
    }

    /**
     * Dịch qua Model Local (Tầng 2) - Hoàn toàn ngoại tuyến on-device
     */
    suspend fun translateWithLocalModel(
        text: String,
        fromKorean: Boolean,
        localWords: List<WordEntity>,
        activeModelName: String?
    ): String = withContext(Dispatchers.Default) {
        val query = if (fromKorean) "Dịch sang tiếng Việt: $text" else "Dịch sang tiếng Hàn: $text"
        val modelToUse = activeModelName ?: "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        val res = LocalTier2InferenceEngine.executeOfflineInference(
            query = query,
            localWords = localWords,
            modelFileName = modelToUse
        )
        res.content
    }

    /**
     * Dịch qua AI Model lớn hơn (Online - Cloud Gateway)
     */
    suspend fun translateWithCloudAi(
        text: String,
        fromKorean: Boolean,
        gateway: MultiProviderAiGateway
    ): String = withContext(Dispatchers.IO) {
        val fromLang = if (fromKorean) "tiếng Hàn" else "tiếng Việt"
        val toLang = if (fromKorean) "tiếng Việt" else "tiếng Hàn"
        val prompt = "Hãy dịch câu/đoạn văn sau từ $fromLang sang $toLang một cách tự nhiên và chính xác nhất:\n\n\"$text\"\n\nCung cấp bản dịch chính và các phương án tương đương theo sắc thái lịch sự/trang trọng."

        val request = UnifiedAiRequest(
            messages = listOf(
                UnifiedAiMessage(
                    role = "system",
                    content = "Bạn là một biên dịch viên chuyên nghiệp song ngữ Hàn - Việt. Luôn dịch chính xác, tự nhiên, và giải thích ngắn gọn khi cần thiết."
                ),
                UnifiedAiMessage(
                    role = "user",
                    content = prompt
                )
            ),
            temperature = 0.3,
            maxTokens = 800
        )
        val res = gateway.executeWithFallback(request)
        if (res.isSuccess && res.content.isNotBlank()) {
            res.content
        } else {
            throw Exception(res.errorMessage ?: "Không nhận được phản hồi từ AI Model lớn.")
        }
    }
}
