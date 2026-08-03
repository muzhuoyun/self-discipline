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

        // 两组（三戒 / 七修）小计对比
        val jie = Metrics.groupTotal(record, Group.JIE)
        val xiu = Metrics.groupTotal(record, Group.XIU)

        val lead = when {
            bestScore - worstScore >= 4 -> "今日：${best.title}最佳，${worst.title}需努力"
            jie - xiu >= 15 -> "今日：三戒胜于七修"
            xiu - jie >= 15 -> "今日：七修胜于三戒"
            jie == xiu -> "今日：戒修均衡，稳步前行"
            else -> "今日：${if (jie > xiu) "三戒略胜" else "七修略胜"}，整体稳中有进"
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
