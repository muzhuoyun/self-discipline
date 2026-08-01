package com.selfdiscipline.app.logic

import com.selfdiscipline.app.data.Category
import com.selfdiscipline.app.data.DailyRecord
import com.selfdiscipline.app.data.Metrics

/**
 * 每日一句话总结，由规则从真实数据生成。
 *
 * 它是「仪表盘」而不是「审判」：低分只说明明天该从哪里补起。
 */
object Summary {

    fun of(record: DailyRecord, yesterday: DailyRecord?): String {
        val total = Metrics.total(record)
        if (total == 60) return "今日：知行合一，完美收官！"

        val best = Category.entries.maxBy { Metrics.score(it, record) }
        val worst = Category.entries.minBy { Metrics.score(it, record) }
        val bestScore = Metrics.score(best, record)
        val worstScore = Metrics.score(worst, record)

        val jie = Metrics.groupTotal(record, jie = true)
        val xiu = Metrics.groupTotal(record, jie = false)
        val relation = when {
            jie > xiu -> "戒胜于修"
            xiu > jie -> "修胜于戒"
            else -> "知行并进"
        }

        val lead = if (bestScore - worstScore >= 4) {
            "今日：${best.title}最佳，${worst.title}需努力"
        } else {
            "今日：$relation"
        }

        val level = when {
            total >= 55 -> "精进日，状态极佳！"
            total >= 45 -> "状态不错，继续保持！"
            total >= 30 -> "守住底线，明日更进一步。"
            else -> "调整日：明天从${worst.title}开始，先多拿 5 分。"
        }

        val delta = yesterday?.let { total - Metrics.total(it) }
        val diff = when {
            delta == null || delta == 0 -> ""
            delta > 0 -> "较昨日 +$delta"
            else -> "较昨日 $delta"
        }

        return listOf(lead, level, diff).filter { it.isNotEmpty() }.joinToString("")
    }
}
