package com.selfdiscipline.app.data

/** 一项可勾选的指标 */
data class Criterion(val label: String, val points: Int)

/** 戒淫的等级选项 */
data class JieYinLevel(val score: Int, val description: String)

/**
 * 三戒三修的评分规则（满分 60）。
 *
 * 戒淫：单选等级。其余五类：逐项勾选，选中即得对应分值。
 */
object Metrics {

    val JIE_YIN_LEVELS = listOf(
        JieYinLevel(10, "无接触、无冲动，清净自在"),
        JieYinLevel(8, "有冲动，但成功克制"),
        JieYinLevel(5, "接触了一点，及时停止"),
        JieYinLevel(0, "完全放纵"),
    )

    val criteria: Map<Category, List<Criterion>> = mapOf(
        Category.JIE_CHAN to listOf(
            Criterion("三餐规律", 3),
            Criterion("七八分饱", 3),
            Criterion("无高糖饮料 / 零食", 2),
            Criterion("无暴饮暴食", 2),
        ),
        Category.JIE_TAN to listOf(
            Criterion("没有冲动消费", 3),
            Criterion("不攀比", 2),
            Criterion("不长时间刷无意义内容", 3),
            Criterion("珍惜已有资源", 2),
        ),
        Category.XIU_YANG to listOf(
            Criterion("睡眠 7~9 小时", 4),
            Criterion("按时睡觉", 3),
            Criterion("起床后精神良好", 3),
        ),
        Category.XIU_TI to listOf(
            Criterion("力量训练", 4),
            Criterion("有氧运动", 3),
            Criterion("拉伸或步数达标", 3),
        ),
        Category.XIU_XING to listOf(
            Criterion("完成今天最重要的一件事", 4),
            Criterion("不拖延", 2),
            Criterion("今天创造了实际价值", 2),
            Criterion("空想时间在目标范围内", 2),
        ),
    )

    /** 单类指标满分 */
    const val maxScore = 10

    /** 位掩码中第 [index] 位是否置位 */
    fun isChecked(category: Category, record: DailyRecord, index: Int): Boolean =
        maskOf(category)(record) and (1 shl index) != 0

    fun score(category: Category, record: DailyRecord): Int = when (category) {
        Category.JIE_YIN -> record.jieYin
        else -> bitScore(maskOf(category)(record), criteria.getValue(category))
    }

    fun total(record: DailyRecord): Int = Category.entries.sumOf { score(it, record) }

    /** 三戒或三修的小计 */
    fun groupTotal(record: DailyRecord, jie: Boolean): Int =
        Category.entries.filter { it.isJie == jie }.sumOf { score(it, record) }

    private fun maskOf(category: Category): (DailyRecord) -> Int = { r ->
        when (category) {
            Category.JIE_CHAN -> r.jieChanMask
            Category.JIE_TAN -> r.jieTanMask
            Category.XIU_YANG -> r.xiuYangMask
            Category.XIU_TI -> r.xiuTiMask
            Category.XIU_XING -> r.xiuXingMask
            Category.JIE_YIN -> 0
        }
    }

    private fun bitScore(mask: Int, list: List<Criterion>): Int =
        list.withIndex().sumOf { (i, c) -> if (mask and (1 shl i) != 0) c.points else 0 }
}

/** 勾选 / 取消某一项指标，返回新记录 */
fun DailyRecord.withCriterion(category: Category, index: Int, checked: Boolean): DailyRecord {
    if (category == Category.JIE_YIN) return this
    val bit = 1 shl index
    return when (category) {
        Category.JIE_CHAN -> copy(jieChanMask = if (checked) jieChanMask or bit else jieChanMask and bit.inv())
        Category.JIE_TAN -> copy(jieTanMask = if (checked) jieTanMask or bit else jieTanMask and bit.inv())
        Category.XIU_YANG -> copy(xiuYangMask = if (checked) xiuYangMask or bit else xiuYangMask and bit.inv())
        Category.XIU_TI -> copy(xiuTiMask = if (checked) xiuTiMask or bit else xiuTiMask and bit.inv())
        Category.XIU_XING -> copy(xiuXingMask = if (checked) xiuXingMask or bit else xiuXingMask and bit.inv())
        Category.JIE_YIN -> this
    }
}

/** 设置戒淫等级，返回新记录 */
fun DailyRecord.withJieYin(level: Int): DailyRecord = copy(jieYin = level)
