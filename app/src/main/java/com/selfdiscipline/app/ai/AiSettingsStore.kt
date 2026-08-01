package com.selfdiscipline.app.ai

import android.content.Context

/** 大模型连接配置，保存到本地 SharedPreferences */
class AiSettingsStore(context: Context) {

    companion object {
        /** 默认接口基地址（DeepSeek，OpenAI 兼容） */
        const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"

        /** 默认模型名 */
        const val DEFAULT_MODEL = "deepseek-v4-flash"
    }

    private val sp = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    /** 未配置时返回 DeepSeek 默认地址 */
    fun baseUrl(): String = sp.getString("base_url", null)?.takeIf { it.isNotBlank() }
        ?: DEFAULT_BASE_URL

    fun apiKey(): String = sp.getString("api_key", "") ?: ""

    /** 未配置时返回默认模型 */
    fun model(): String = sp.getString("model", null)?.takeIf { it.isNotBlank() }
        ?: DEFAULT_MODEL

    fun save(baseUrl: String, apiKey: String, model: String) {
        sp.edit()
            .putString("base_url", baseUrl.toHalfWidth().trim().trimEnd('/').ifBlank { DEFAULT_BASE_URL })
            .putString("api_key", apiKey.toHalfWidth().trim())
            .putString("model", model.toHalfWidth().trim())
            .apply()
    }

    /** 全角转半角：中文输入法粘贴 URL 时常见，避免「http：／／」这种格式错误 */
    private fun String.toHalfWidth(): String = map { c ->
        when {
            c == '　' -> ' '
            c.code in 0xFF01..0xFF5E -> (c.code - 0xFEE0).toChar()
            else -> c
        }
    }.joinToString("")

    // ---------- 打卡提醒 ----------

    fun reminderEnabled(): Boolean = sp.getBoolean("reminder_enabled", false)

    fun reminderHour(): Int = sp.getInt("reminder_hour", 21)

    fun reminderMinute(): Int = sp.getInt("reminder_minute", 0)

    fun saveReminder(enabled: Boolean, hour: Int, minute: Int) {
        sp.edit()
            .putBoolean("reminder_enabled", enabled)
            .putInt("reminder_hour", hour)
            .putInt("reminder_minute", minute)
            .apply()
    }
}
