package com.selfdiscipline.app.ai

import com.selfdiscipline.app.data.AiChatLog
import com.selfdiscipline.app.data.AiKinds
import com.selfdiscipline.app.data.Category
import com.selfdiscipline.app.data.CustomAchievement
import com.selfdiscipline.app.data.DailyLog
import com.selfdiscipline.app.data.DailyRecord
import com.selfdiscipline.app.data.Metrics
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 各场景的 Prompt 构造，以及 AI 输出 → 结构化数据的解析与校验。
 *
 * 关键设计：AI 只能输出受控的 JSON 结构，App 端白名单校验后才生效，
 * 保证「AI 添加的成就一定可以通过记录数据查询」。
 */
object AiPrompts {

    private const val ROLE =
        "你是一位温和、真诚的修身教练，陪伴用户践行「三戒七修」" +
            "（戒淫、戒馋、戒贪；修养、修体、修行、孝、诚、和、勤）。" +
            "你只基于用户提供的数据说话，不评判、不指责，像朋友一样给出鼓励与具体建议。"

    // ---------- 今日打卡短评 ----------

    fun reviewSystem(): String = "$ROLE\n" +
        "请根据用户今天的评分数据和对话记录，写一段 150~250 字的短评。要求：\n" +
        "1. 先肯定今天做得好的 1~2 点（可引用用户自己的话或对话细节）；\n" +
        "2. 再点出今天最值得改进的一项，并给出一个具体的、明天就能做的小建议；\n" +
        "3. 最后一句鼓励收尾；\n" +
        "4. 不要使用列表或标题，直接一段话；语气亲切自然、言之有物，避免空话。"

    fun reviewUser(record: DailyRecord, ruleSummary: String, dialogue: String = ""): String {
        val sb = StringBuilder()
        sb.append("今天的评分数据：\n")
        Category.entries.forEach { c ->
            sb.append("- ${c.title}：${Metrics.score(c, record)} / 10")
            if (c != Category.JIE_YIN) {
                val done = Metrics.criteria.getValue(c).withIndex()
                    .filter { Metrics.isChecked(c, record, it.index) }
                    .joinToString("、") { it.value.label }
                if (done.isNotEmpty()) sb.append("（完成：$done）")
            } else {
                Metrics.JIE_YIN_LEVELS.firstOrNull { it.score == record.jieYin }?.let {
                    sb.append("（${it.description}）")
                }
            }
            sb.append("\n")
        }
        sb.append("总分：${Metrics.total(record)} / 100\n")
        sb.append("规则总结：$ruleSummary\n")
        if (dialogue.isNotBlank()) sb.append(dialogue)
        return sb.toString()
    }

    // ---------- 今日状态的 AI 医生 ----------

    /**
     * AI 医生角色：以温和医生的视角审视今日状态记录，
     * 询问身体状况细节，给出日常健康建议（不做诊断、不替代就医）。
     */
    fun doctorSystem(): String = "$ROLE\n" +
        "现在请你以一位温和、专业的医生的视角，审视用户今日的状态记录，和用户交流身体状况。要求：\n" +
        "1. 先针对用户记录的状态给出共情与初步观察（如疲劳、情绪、饮食、睡眠相关的线索）；\n" +
        "2. 询问 1~2 个关键的身体细节（睡眠时长、饮食、精力、疼痛等，视记录内容而定）；\n" +
        "3. 给出 1~2 条温和、可执行的日常建议（作息、饮食、运动、放松）；\n" +
        "4. 语气像可靠的朋友医生，不使用恐吓性语言；明确说明不能替代专业医疗，严重不适请就医；\n" +
        "5. 回复 150~300 字，自然一段或短段落，不要 Markdown 标题。"

    /** 医生对话的用户输入：今日状态记录 + 用户的话 */
    fun doctorUser(statusSummary: String, input: String): String =
        if (statusSummary.isNotBlank()) "今日状态记录：\n$statusSummary\n\n用户的话：$input"
        else "用户的话：$input"

    /** 今日状态记录的文字摘要（医生用，含全部文字） */
    fun doctorStatusSummary(logs: List<DailyLog>): String {
        if (logs.isEmpty()) return ""
        return logs.mapNotNull { it.text.trim().takeIf { t -> t.isNotBlank() } }
            .joinToString("\n") { "- $it" }
    }

    /** 每日状态记录的文字摘要，作为点评 / 周报 / 月报的上下文参考 */
    fun statusLogsContext(logs: List<DailyLog>): String {
        if (logs.isEmpty()) return ""
        val sb = StringBuilder("用户状态记录（用户自己写下的当日感受，请作为参考）：\n")
        logs.groupBy { it.date }.entries.sortedBy { it.key }.forEach { (date, dayLogs) ->
            val d = runCatching { LocalDate.parse(date) }.getOrNull()
            val label = if (d != null) "${d.monthValue}月${d.dayOfMonth}日" else date
            val texts = dayLogs.mapNotNull { it.text.trim().takeIf { t -> t.isNotBlank() } }
                .map { it.take(60) }
            if (texts.isNotEmpty()) {
                sb.append("- $label：")
                sb.append(texts.joinToString("；"))
                sb.append("\n")
            }
        }
        return sb.toString()
    }

    /**
     * 用户与 AI 判断页的多轮对话摘要——比固定选项更能反映真实状态，
     * 作为点评 / 周报 / 月报的上下文参考。
     */
    fun dialogueContext(logs: List<AiChatLog>): String {
        if (logs.isEmpty()) return ""
        val sb = StringBuilder("用户对话记录（比固定选项更能反映真实状态，请作为参考）：\n")
        // 按日期分组，每天最多 2 轮
        logs.filter { it.kind == AiKinds.AUTO_CHECK }
            .sortedBy { it.createdAt }
            .groupBy { it.date }
            .entries
            .sortedBy { it.key }
            .forEach { (date, dayLogs) ->
                dayLogs.take(2).forEach { log ->
                    val userText = extractUserInput(log.prompt).trim().take(100)
                    val aiReply = runCatching {
                        extractJson(log.response)?.let { JSONObject(it).optString("reply") }
                    }.getOrNull()?.trim()?.take(100)
                    val d = runCatching { LocalDate.parse(date) }.getOrNull()
                    val label = if (d != null) "${d.monthValue}月${d.dayOfMonth}日" else date
                    sb.append("- $label（${log.categoryKey ?: ""}）：用户：$userText")
                    if (!aiReply.isNullOrBlank()) sb.append("；AI：$aiReply")
                    sb.append("\n")
                }
            }
        return sb.toString()
    }

    // ---------- 详情页 AI 辅助判断 ----------

    /**
     * 单选（戒淫）提示词：先回应用户意图（闲聊/提问/澄清），
     * 有可判断的信息时再选择最贴近的等级。
     */
    fun autoCheckLevelSystem(): String = "$ROLE\n" +
        "用户可能会说任何话：闲聊、提问、或者描述自己当天在「戒淫」方面的实际情况。请分两步：\n" +
        "第一步（意图回应）：用友好、有温度的话回应或澄清用户的话（100~200 字，可适当展开、共情）。" +
        "如果用户只是聊天或提问，这一步就是全部，不要做任何判断。\n" +
        "第二步（等级判断）：只有用户确实描述了实际情况，才在四个等级中判断最贴近的一个：" +
        "10 分：无接触、无冲动，清净自在；8 分：有冲动，但成功克制；5 分：接触了一点，及时停止；0 分：完全放纵。" +
        "先抓住关键行为（有没有接触、有没有冲动、有没有成功克制），描述不足时选最保守（偏低）的等级。\n" +
        "输出格式（严格 JSON，reply 必须有，level 可有可无）：" +
        "{\"reply\":\"先回应用户的话\",\"level\":8,\"reason\":\"判断理由（60~100字，讲清依据）\"}"

    /**
     * 多选（勾选类）提示词：先回应用户意图，有可判断的信息时再走两阶段判断
     * （先识别涉及哪些选项，再判断该不该勾选）。
     */
    fun autoCheckChecklistSystem(category: Category): String {
        val list = Metrics.criteria.getValue(category)
            .withIndex().joinToString("\n") { "${it.index}. ${it.value.label}（${it.value.points}分）" }
        return "$ROLE\n" +
            "用户可能会说任何话：闲聊、提问、或者描述自己当天在「${category.title}」方面的实际情况。请分两步：\n" +
            "第一步（意图回应）：用友好、有温度的话回应或澄清用户的话（100~200 字，可适当展开、共情）。" +
            "如果用户只是聊天或提问，这一步就是全部，不要做任何判断。\n" +
            "第二步（判断）：只有用户确实描述了实际情况，才判断。判断分两步：\n" +
            "  1）涉及识别：先判断描述涉及下面选项中的哪几个（可能一个，也可能多个）；\n" +
            "  2）符合判断：只对涉及到的选项，判断所说情况是否符合该项标准——符合勾选，不符合不勾选。\n" +
            "规则：结果里只出现涉及到的选项；没提到的选项不要出现；模糊时宁可少判。\n" +
            "选项列表：\n$list\n" +
            "输出格式（严格 JSON，reply 必须有，items 可空）：" +
            "{\"reply\":\"先回应用户的话\",\"items\":[{\"index\":0,\"checked\":true,\"reason\":\"判断理由（60~100字，讲清依据）\"}]}"
    }

    fun autoCheckUser(category: Category, input: String): String =
        "「${category.title}」当天实际情况描述：\n$input"

    /** 从记录的 prompt 中还原用户原始输入（去掉「××」当天实际情况描述： 前缀） */
    fun extractUserInput(prompt: String): String =
        prompt.substringAfter("：").substringAfter("\n").trim()

    /** 从医生对话记录的 prompt 中还原用户原话 */
    fun extractUserReply(prompt: String): String =
        prompt.substringAfter("用户的话：", prompt).trim()

    // ---------- 周报 / 月报 ----------

    fun weeklySystem(): String = "$ROLE\n" +
        "请根据用户本周（周一~今天）的评分记录和对话记录写一份周报，500 字以内。分三段：\n" +
        "1. 本周总体表现（用数据说话：平均分、趋势）；\n" +
        "2. 亮点与问题（哪项最稳、哪项波动最大，可引用用户自己说过的话）；\n" +
        "3. 下周建议（2~3 条具体的、可执行的小目标）。\n" +
        "语气温和有力量，不要用 Markdown 标题。"

    fun weeklyUser(records: List<DailyRecord>, dialogue: String = ""): String =
        recordsData(records) + if (dialogue.isNotBlank()) "\n$dialogue" else ""

    fun monthlySystem(): String = "$ROLE\n" +
        "请根据用户本月（1 号~今天）的评分记录和对话记录写一份月报，800 字以内。分四段：\n" +
        "1. 本月总体表现（平均分、趋势）；\n" +
        "2. 高光时刻（最好的几天、突破的项目）；\n" +
        "3. 需要留意的规律（低分集中在什么场景/项目，可引用用户自己说过的话）；\n" +
        "4. 下月建议（2~3 条）。\n" +
        "语气温和有力量，不要用 Markdown 标题。"

    fun monthlyUser(records: List<DailyRecord>, dialogue: String = ""): String =
        recordsData(records) + if (dialogue.isNotBlank()) "\n$dialogue" else ""

    private fun recordsData(records: List<DailyRecord>): String {
        val sb = StringBuilder("逐日评分记录（总分/戒淫/戒馋/戒贪/修养/修体/修行）：\n")
        records.sortedBy { it.date }.forEach { r ->
            sb.append("- ${r.date}：${Metrics.total(r)}分 /")
            Category.entries.forEach { c -> sb.append(" ${c.title}${Metrics.score(c, r)}") }
            sb.append("\n")
        }
        return sb.toString()
    }

    // ---------- AI 添加成就 ----------

    fun achievementSystem(): String = "$ROLE\n" +
        "根据用户的历史表现数据，设计 1~3 个更有挑战性的新成就（要比用户已有的成就更难）。\n" +
        "成就必须是「通过每日记录数据可查询」的，只能使用以下受控字段：\n" +
        "- metric：指标，只能是 TOTAL（总分）、JIE_YIN、JIE_CHAN、JIE_TAN、XIU_YANG、XIU_TI、XIU_XING、XIAO（孝）、CHENG（诚）、HE（和）、QIN（勤）之一\n" +
        "- window：STREAK = 连续 N 天达标；CUMULATIVE = 累计 N 天达标\n" +
        "- target_value：达标阈值（metric 为 TOTAL 时 0~100，其他 0~10）\n" +
        "- window_days：周期天数（1~365）\n" +
        "只输出 JSON，不要代码块、不要其他文字：\n" +
        "{\"achievements\":[{\"emoji\":\"🔥\",\"title\":\"成就名（12字内）\",\"description\":\"一句话描述\",\"metric\":\"JIE_CHAN\",\"window\":\"STREAK\",\"target_value\":10,\"window_days\":5}]}"

    fun achievementUser(records: List<DailyRecord>, currentTitles: List<String>): String {
        val sb = StringBuilder()
        sb.append("历史数据摘要（只统计有记录的天数，共 ${records.size} 天）：\n")
        Category.entries.forEach { c ->
            val scores = records.map { Metrics.score(c, it) }
            if (scores.isNotEmpty()) {
                sb.append("- ${c.title}：平均 ${"%.1f".format(scores.average())}，最高 ${scores.max()}，≥8分 ${scores.count { it >= 8 }} 天\n")
            }
        }
        if (records.isNotEmpty()) {
            val totals = records.map { Metrics.total(it) }
            sb.append("- 总分：平均 ${"%.1f".format(totals.average())}，最高 ${totals.max()}，≥75分 ${totals.count { it >= 75 }} 天\n")
        }
        sb.append("\n已有成就：${currentTitles.joinToString("、")}\n")
        sb.append("请设计比这些更难的新成就。")
        return sb.toString()
    }

    // ---------- JSON 解析与校验 ----------

    /** 从任意文本（可能带 markdown 代码块）中提取最外层 JSON 对象 */
    fun extractJson(text: String): String? {
        var t = text
        Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(t)?.let { t = it.groupValues[1] }
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        return if (start >= 0 && end > start) t.substring(start, end + 1) else null
    }

    fun parseAutoCheck(text: String): AutoCheckOutcome? {
        val json = extractJson(text) ?: return null
        return runCatching {
            val obj = JSONObject(json)
            val reply = obj.optString("reply").takeIf { it.isNotBlank() } ?: ""
            val items = mutableMapOf<Int, Boolean>()
            val reasons = mutableMapOf<Int, String>()
            obj.optJSONArray("items")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val index = item.optInt("index", -1)
                    if (index >= 0) {
                        items[index] = item.optBoolean("checked", false)
                        item.optString("reason").takeIf { it.isNotBlank() }?.let { reasons[index] = it }
                    }
                }
            }
            AutoCheckOutcome(
                reply = reply,
                items = items,
                reasons = reasons,
                level = obj.optInt("level", -1).takeIf { it in listOf(0, 5, 8, 10) },
                levelReason = obj.optString("reason").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

    /** 流式显示时：只展示 JSON 之前的回复文本，隐藏尾部 JSON */
    fun streamDisplayText(text: String): String {
        val idx = text.indexOf('{')
        return if (idx >= 0) text.substring(0, idx).trim() else text
    }

    /** 校验 AI 输出的成就 JSON，返回合法的成就列表和每条的校验结果 */
    fun parseAchievements(text: String): Pair<List<AchievementSpec>, List<String>> {
        val json = extractJson(text) ?: return emptyList<AchievementSpec>() to listOf("AI 输出不是有效的 JSON")
        val valid = mutableListOf<AchievementSpec>()
        val errors = mutableListOf<String>()
        runCatching {
            val arr = json.run { JSONObject(this) }.optJSONArray("achievements") ?: JSONArray().apply { put(JSONObject(json)) }
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val spec = parseOne(item)
                if (spec != null) valid.add(spec) else errors.add("第 ${i + 1} 条格式不正确")
            }
        }.onFailure { errors.add("解析失败：${it.message?.take(50)}") }
        return valid to errors
    }

    private fun parseOne(item: JSONObject): AchievementSpec? {
        val metric = item.optString("metric").uppercase()
        val validMetrics = setOf(
            "TOTAL", "JIE_YIN", "JIE_CHAN", "JIE_TAN", "XIU_YANG", "XIU_TI", "XIU_XING",
            "XIAO", "CHENG", "HE", "QIN",
        )
        if (metric !in validMetrics) return null
        val window = item.optString("window").uppercase()
        if (window !in setOf("STREAK", "CUMULATIVE")) return null
        val value = item.optInt("target_value", -1)
        val max = if (metric == "TOTAL") 100 else 10
        if (value < 0 || value > max) return null
        val days = item.optInt("window_days", 0)
        if (days !in 1..365) return null
        val emoji = item.optString("emoji").takeIf { it.isNotBlank() } ?: "🏅"
        val title = item.optString("title").takeIf { it.isNotBlank() } ?: return null
        val desc = item.optString("description").takeIf { it.isNotBlank() } ?: title
        return AchievementSpec(
            emoji = emoji,
            title = title.take(20),
            description = desc.take(60),
            metric = metric,
            window = window,
            targetValue = value,
            windowDays = days,
        )
    }
}

/** AI 输出的成就规格（经校验后） */
data class AchievementSpec(
    val emoji: String,
    val title: String,
    val description: String,
    val metric: String,
    val window: String,
    val targetValue: Int,
    val windowDays: Int,
)

/** 规格 → 数据库实体 */
fun AchievementSpec.toEntity() = CustomAchievement(
    emoji = emoji,
    title = title,
    description = description,
    metric = metric,
    window = window,
    targetValue = targetValue,
    windowDays = windowDays,
)
