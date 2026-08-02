package com.selfdiscipline.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容的 SSE 流式对话客户端。
 * baseUrl 形如 http://host:port/v1，请求发往 {baseUrl}/chat/completions。
 */
class AiService(private val settings: AiSettingsStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    /**
     * 流式对话，[onDelta] 每次收到增量即回调（主线程）。
     * [history] 为之前的多轮对话（user/assistant 交替），保证上下文连续。
     * 返回完整文本；失败返回包含错误信息的 Result。
     */
    suspend fun streamChat(
        system: String,
        user: String,
        history: List<ChatTurn> = emptyList(),
        onDelta: (String) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        val base = settings.baseUrl()
        if (base.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("请先在设置中配置大模型地址"))
        }
        val bodyObj = JSONObject().apply {
            put("stream", true)
            // 关闭思维链（reasoning）：减少延迟与 token 消耗。
            // enable_thinking 为 DeepSeek 官方参数，reasoning_effort 兼容 OpenAI 风格服务；
            // 不支持的服务的会忽略这些字段。
            put("enable_thinking", false)
            put("reasoning_effort", "none")
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                history.forEach { turn ->
                    put(JSONObject().put("role", turn.role).put("content", turn.content))
                }
                put(JSONObject().put("role", "user").put("content", user))
            })
            settings.model().takeIf { it.isNotEmpty() }?.let { put("model", it) }
        }
        val request = Request.Builder()
            .url("$base/chat/completions")
            .post(bodyObj.toString().toRequestBody("application/json".toMediaType()))
            .header("Accept", "text/event-stream")
            .apply {
                settings.apiKey().takeIf { it.isNotBlank() }?.let {
                    header("Authorization", "Bearer $it")
                }
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()?.take(300) ?: response.message
                    return@withContext Result.failure(
                        IllegalStateException("请求失败（${response.code}）：$err")
                    )
                }
                val source = response.body!!.source()
                val sb = StringBuilder()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    runCatching {
                        // choices 可能为空（usage 事件）、delta.content 可能是 JSON null
                        // （推理模型的 thinking 阶段），统一跳过，避免拼出 "null"
                        val delta = JSONObject(data)
                            .optJSONArray("choices")?.optJSONObject(0)
                            ?.optJSONObject("delta") ?: return@runCatching
                        if (delta.isNull("content")) return@runCatching
                        val content = delta.optString("content", "")
                        if (content.isNotEmpty()) {
                            sb.append(content)
                            onDelta(content)
                        }
                    }
                }
                Result.success(sb.toString())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
