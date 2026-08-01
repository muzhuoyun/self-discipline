package com.selfdiscipline.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** AI 交互的类型 */
object AiKinds {
    const val REVIEW = "SHORT_REVIEW"        // 今日打卡短评
    const val AUTO_CHECK = "AUTO_CHECK"      // 详情页 AI 辅助判断
    const val WEEKLY = "WEEKLY_REPORT"       // 周报
    const val MONTHLY = "MONTHLY_REPORT"     // 月报
    const val ACHIEVEMENT = "ACHIEVEMENT_ADD" // AI 添加成就
}

/** 每一次与 AI 的交互记录 */
@Entity(tableName = "ai_chat_log")
data class AiChatLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val kind: String,
    val categoryKey: String? = null,
    val prompt: String,
    val response: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * AI 添加的自定义成就。
 *
 * 为保证「可以通过记录的数据查询」，AI 只能输出受控字段：
 * - metric：指标（TOTAL / 六类 key）
 * - window：STREAK（连续 N 天达标）/ CUMULATIVE（累计 N 天达标）
 * - targetValue：达标阈值（总分 0~60，单项 0~10）
 * - windowDays：周期天数 1~365
 */
@Entity(tableName = "custom_achievement")
data class CustomAchievement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val emoji: String,
    val title: String,
    val description: String,
    val metric: String,
    val window: String,
    val targetValue: Int,
    val windowDays: Int,
    val createdAt: Long = System.currentTimeMillis(),
)
