package com.selfdiscipline.app.logic

import com.selfdiscipline.app.ai.AchievementSpec
import com.selfdiscipline.app.data.Category
import com.selfdiscipline.app.data.CustomAchievement
import com.selfdiscipline.app.data.DailyRecord
import com.selfdiscipline.app.data.Metrics
import java.time.LocalDate

/** 成就达成条件 */
sealed interface Condition {
    /** 连续达标 [target] 天 */
    data class Streak(val target: Int, val predicate: (DailyRecord) -> Boolean) : Condition

    /** 累计达标 [target] 天 */
    data class Cumulative(val target: Int, val predicate: (DailyRecord) -> Boolean) : Condition
}

data class AchievementDef(
    val emoji: String,
    val title: String,
    val description: String,
    val condition: Condition,
    /** 是否为 AI 新增的成就 */
    val aiAdded: Boolean = false,
)

/** 完整的内置成就（11 项） */
val ACHIEVEMENTS = listOf(
    AchievementDef("🌱", "首次打卡", "记录第一天的分数", Condition.Streak(1) { true }),
    AchievementDef("🔥", "连续打卡 7 天", "连续 7 天都有记录", Condition.Streak(7) { true }),
    AchievementDef("🛡️", "戒淫连续 10 天 ≥8 分", "守住清净连续 10 天", Condition.Streak(10) {
        Metrics.score(Category.JIE_YIN, it) >= 8
    }),
    AchievementDef("🍽️", "戒馋连续 7 天 ≥8 分", "管住嘴连续 7 天", Condition.Streak(7) {
        Metrics.score(Category.JIE_CHAN, it) >= 8
    }),
    AchievementDef("🎯", "戒贪连续 7 天 ≥8 分", "不被欲望带走连续 7 天", Condition.Streak(7) {
        Metrics.score(Category.JIE_TAN, it) >= 8
    }),
    AchievementDef("😴", "修养连续 7 天 ≥8 分", "睡好觉连续 7 天", Condition.Streak(7) {
        Metrics.score(Category.XIU_YANG, it) >= 8
    }),
    AchievementDef("💪", "修体连续 30 天", "连续 30 天完成修体", Condition.Streak(30) {
        Metrics.score(Category.XIU_TI, it) > 0
    }),
    AchievementDef("📚", "修行连续 7 天 ≥8 分", "实干连续 7 天", Condition.Streak(7) {
        Metrics.score(Category.XIU_XING, it) >= 8
    }),
    AchievementDef("👑", "总分连续 7 天 ≥50", "总分保持一周 50 以上", Condition.Streak(7) {
        Metrics.total(it) >= 50
    }),
    AchievementDef("🏆", "单日满分", "某一天拿到 60 分满分", Condition.Streak(1) {
        Metrics.total(it) == 60
    }),
    AchievementDef("📈", "累计打卡 30 天", "累计记录满 30 天", Condition.Cumulative(30) { true }),
)

data class AchievementState(
    val def: AchievementDef,
    val unlocked: Boolean,
    val progressText: String,
    val fraction: Float,
)

object AchievementEngine {

    /** 内置成就 + AI 自定义成就一起评估 */
    fun evaluate(
        records: List<DailyRecord>,
        extra: List<AchievementDef> = emptyList(),
    ): List<AchievementState> {
        val defs = ACHIEVEMENTS + extra
        val sorted = records.sortedBy { it.date }
        return defs.map { def ->
            when (val c = def.condition) {
                is Condition.Streak -> {
                    val max = maxStreak(sorted, c.predicate)
                    val current = currentStreak(sorted, c.predicate)
                    val unlocked = max >= c.target
                    val text = when {
                        unlocked -> "已达成，继续巩固 🎉"
                        current > 0 -> "当前连续 $current 天（最高 $max）/ 目标 ${c.target} 天"
                        else -> "最高连续 $max 天 / 目标 ${c.target} 天"
                    }
                    AchievementState(def, unlocked, text, (max.toFloat() / c.target).coerceIn(0f, 1f))
                }
                is Condition.Cumulative -> {
                    val count = sorted.count { c.predicate(it) }
                    AchievementState(
                        def = def,
                        unlocked = count >= c.target,
                        progressText = if (count >= c.target) "已达成，继续坚持 🎉" else "累计 $count 天 / 目标 ${c.target} 天",
                        fraction = (count.toFloat() / c.target).coerceIn(0f, 1f),
                    )
                }
            }
        }
    }

    /** 截至最近一次记录（今天或昨天）的连续天数；已经两天没记录则视为中断。 */
    fun currentStreak(sorted: List<DailyRecord>, predicate: (DailyRecord) -> Boolean): Int {
        val last = sorted.lastOrNull() ?: return 0
        val lastDate = LocalDate.parse(last.date)
        val today = LocalDate.now()
        if (today.minusDays(1).isAfter(lastDate)) return 0
        val byDate = sorted.associateBy { it.date }
        var streak = 0
        var d = lastDate
        while (true) {
            val r = byDate[d.toString()] ?: break
            if (!predicate(r)) break
            streak++
            d = d.minusDays(1)
        }
        return streak
    }

    /** 历史最长连续天数 */
    private fun maxStreak(sorted: List<DailyRecord>, predicate: (DailyRecord) -> Boolean): Int {
        var max = 0
        var run = 0
        var prev: LocalDate? = null
        for (r in sorted) {
            val d = LocalDate.parse(r.date)
            run = if (prev != null && d == prev.plusDays(1)) run + 1 else 1
            if (!predicate(r)) run = 0
            if (run > max) max = run
            prev = d
        }
        return max
    }
}

/** 白名单校验后的 AI 成就规格 → 可评估的成就定义 */
fun AchievementSpec.toAchievementDef(): AchievementDef {
    val cat = Category.entries.firstOrNull { it.key.equals(metric, ignoreCase = true) }
    val scoreFn: (DailyRecord) -> Int = when {
        metric.equals("TOTAL", ignoreCase = true) -> { r: DailyRecord -> Metrics.total(r) }
        cat != null -> { r: DailyRecord -> Metrics.score(cat, r) }
        else -> { _: DailyRecord -> 0 }
    }
    val condition = if (window.equals("CUMULATIVE", ignoreCase = true)) {
        Condition.Cumulative(windowDays) { scoreFn(it) >= targetValue }
    } else {
        Condition.Streak(windowDays) { scoreFn(it) >= targetValue }
    }
    return AchievementDef(
        emoji = emoji,
        title = title,
        description = description,
        condition = condition,
        aiAdded = true,
    )
}

/** 数据库里的自定义成就 → 可评估的成就定义 */
fun CustomAchievement.toAchievementDef(): AchievementDef? {
    val validMetrics = setOf("TOTAL", "JIE_YIN", "JIE_CHAN", "JIE_TAN", "XIU_YANG", "XIU_TI", "XIU_XING")
    if (metric.uppercase() !in validMetrics) return null
    if (window.uppercase() !in setOf("STREAK", "CUMULATIVE")) return null
    return AchievementSpec(
        emoji = emoji,
        title = title,
        description = description,
        metric = metric.uppercase(),
        window = window.uppercase(),
        targetValue = targetValue,
        windowDays = windowDays,
    ).toAchievementDef()
}
