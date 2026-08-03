package com.selfdiscipline.app.logic

import com.selfdiscipline.app.data.Category
import com.selfdiscipline.app.data.DailyRecord
import com.selfdiscipline.app.data.Group
import com.selfdiscipline.app.data.Metrics

/**
 * 每日一句话总结，由规则从真实数据生成。
 *
 * 它是「仪表盘」而不是「审判」：低分只说明明天该从哪里补起。
 */
object Summary {

    fun of(record: DailyRecord, yesterday: DailyRecord?): String {
        val total = Metrics.total(record)
        if (total == 100) return "今日：十全十美，知行合一！"

        val best = Category.entries.maxBy { Metrics.score(it, record) }
        val worst = Category.entries.minBy { Metrics.score(it, record) }
        val bestScore = Metrics.score(best, record)
        val worstScore = Metrics.score(worst, record)

        // 三组（三戒 / 三修 / 四德）小计对比
        val groupTotals = Group.entries.associateWith { Metrics.groupTotal(record, it) }
        val bestGroup = groupTotals.maxBy { it.value }.key
        val worstGroup = groupTotals.minBy { it.value }.key
        val bestGroupScore = groupTotals.getValue(bestGroup)
        val worstGroupScore = groupTotals.getValue(worstGroup)

        val lead = when {
            bestScore - worstScore >= 4 -> "今日：${best.title}最佳，${worst.title}需努力"
            bestGroupScore - worstGroupScore >= 10 -> "今日：${bestGroup.title}最佳，${worstGroup.title}需努力"
            bestGroupScore == worstGroupScore -> "今日：三组均衡，稳步前行"
            else -> "今日：${bestGroup.title}略胜，${worstGroup.title}可再进"
        }

        val level = when {
            total >= 85 -> "精进日，状态极佳！"
            total >= 75 -> "状态不错，继续保持！"
            total >= 50 -> "守住底线，明日更进一步。"
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
