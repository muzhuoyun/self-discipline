package com.selfdiscipline.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.selfdiscipline.app.AppGraph
import com.selfdiscipline.app.ai.AiPrompts
import com.selfdiscipline.app.ai.AiService
import com.selfdiscipline.app.ai.AiStreamState
import com.selfdiscipline.app.ai.AutoCheckOutcome
import com.selfdiscipline.app.ai.ChatTurn
import com.selfdiscipline.app.ai.toEntity
import com.selfdiscipline.app.data.AiKinds
import com.selfdiscipline.app.data.AiChatLog
import com.selfdiscipline.app.data.Category
import com.selfdiscipline.app.data.CustomAchievement
import com.selfdiscipline.app.data.DailyLog
import com.selfdiscipline.app.data.DailyRecord
import com.selfdiscipline.app.data.LogPhotoStore
import com.selfdiscipline.app.data.Metrics
import com.selfdiscipline.app.data.withCriterion
import com.selfdiscipline.app.data.withJieYin
import com.selfdiscipline.app.logic.AchievementEngine
import com.selfdiscipline.app.logic.Summary
import com.selfdiscipline.app.logic.toAchievementDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** 唯一的 ViewModel：数据量小，全量记录放在内存里即可 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AppGraph.repository
    private val aiService = AiService(AppGraph.aiSettings)

    val records: StateFlow<List<DailyRecord>> = repo.records
    val aiChats: StateFlow<List<AiChatLog>> = repo.aiChats
    val customAchievements: StateFlow<List<CustomAchievement>> = repo.customAchievements
    val dailyLogs: StateFlow<List<DailyLog>> = repo.dailyLogs

    // ---- AI 流式状态 ----
    private val _review = MutableStateFlow<AiStreamState>(AiStreamState.Idle)
    val review: StateFlow<AiStreamState> = _review.asStateFlow()

    private val _autoCheck = MutableStateFlow<AiStreamState>(AiStreamState.Idle)
    val autoCheck: StateFlow<AiStreamState> = _autoCheck.asStateFlow()
    private val _autoCheckOutcome = MutableStateFlow<AutoCheckOutcome?>(null)
    val autoCheckOutcome: StateFlow<AutoCheckOutcome?> = _autoCheckOutcome.asStateFlow()

    /**
     * 详情页 AI 判断的多轮会话，按「日期 + 条目」隔离存储。
     * 退出页面再进入同一条目，对话仍在；不同条目互不串扰。
     */
    private val _autoCheckSessions = MutableStateFlow<Map<String, List<ChatTurn>>>(emptyMap())
    val autoCheckSessions: StateFlow<Map<String, List<ChatTurn>>> = _autoCheckSessions.asStateFlow()

    fun sessionKey(date: LocalDate, category: Category): String = "${date}_${category.key}"

    fun sessionFor(date: LocalDate, category: Category): List<ChatTurn> =
        _autoCheckSessions.value[sessionKey(date, category)] ?: emptyList()

    /** 从数据库恢复该「日期 + 条目」的 AI 判断会话（应用重启后对话仍在） */
    fun loadAutoCheckSession(date: LocalDate, category: Category) {
        val key = sessionKey(date, category)
        if (_autoCheckSessions.value.containsKey(key)) return
        val logs = aiChats.value
            .filter {
                it.kind == AiKinds.AUTO_CHECK &&
                    it.date == date.toString() &&
                    it.categoryKey == category.key
            }
            .sortedBy { it.createdAt }
        if (logs.isEmpty()) return
        val turns = logs.flatMap { log ->
            listOfNotNull(
                ChatTurn(ChatTurn.ROLE_USER, AiPrompts.extractUserInput(log.prompt)),
                ChatTurn(ChatTurn.ROLE_ASSISTANT, log.response),
            )
        }
        _autoCheckSessions.value = _autoCheckSessions.value + (key to turns)
    }

    /** 周报与月报各自独立的状态与标题 */
    private val _weekly = MutableStateFlow<AiStreamState>(AiStreamState.Idle)
    val weekly: StateFlow<AiStreamState> = _weekly.asStateFlow()
    private val _weeklyLabel = MutableStateFlow("")
    val weeklyLabel: StateFlow<String> = _weeklyLabel.asStateFlow()

    private val _monthly = MutableStateFlow<AiStreamState>(AiStreamState.Idle)
    val monthly: StateFlow<AiStreamState> = _monthly.asStateFlow()
    private val _monthlyLabel = MutableStateFlow("")
    val monthlyLabel: StateFlow<String> = _monthlyLabel.asStateFlow()

    private val _suggest = MutableStateFlow<AiStreamState>(AiStreamState.Idle)
    val suggest: StateFlow<AiStreamState> = _suggest.asStateFlow()
    private val _suggestSummary = MutableStateFlow<String?>(null)
    val suggestSummary: StateFlow<String?> = _suggestSummary.asStateFlow()

    private var aiJob: Job? = null

    // ---------- 基础 ----------

    fun recordAt(date: LocalDate): DailyRecord? =
        records.value.firstOrNull { it.date == date.toString() }

    /** 勾选 / 取消某一天某一类的某项指标 */
    fun toggle(date: LocalDate, category: Category, index: Int, checked: Boolean) =
        viewModelScope.launch {
            val base = recordAt(date) ?: DailyRecord(date = date.toString())
            repo.save(base.withCriterion(category, index, checked))
        }

    /** 设置某一天的戒淫等级 */
    fun setJieYinLevel(date: LocalDate, level: Int) = viewModelScope.launch {
        val base = recordAt(date) ?: DailyRecord(date = date.toString())
        repo.save(base.withJieYin(level))
    }

    fun saveAiSettings(baseUrl: String, apiKey: String, model: String) {
        AppGraph.aiSettings.save(baseUrl, apiKey, model)
    }

    fun clearAiChats() = viewModelScope.launch { repo.clearAiChats() }

    // ---------- 今日打卡短评 ----------

    /** 打卡评价：每天只保留最新一份，重新评价时覆盖旧的；当天 AI 判断对话作为上下文 */
    fun checkIn() = viewModelScope.launch {
        // 先删除当天旧的评价记录（覆盖而不是累积）
        repo.deleteReviewFor(LocalDate.now().toString())
        val today = LocalDate.now()
        val record = recordAt(today) ?: DailyRecord(date = today.toString())
        val ruleSummary = Summary.of(record, null)
        // 当天的 AI 判断对话作为点评参考
        val dialogue = AiPrompts.dialogueContext(
            aiChats.value.filter { it.date == today.toString() }
        )
        stream(
            kind = AiKinds.REVIEW,
            system = AiPrompts.reviewSystem(),
            user = AiPrompts.reviewUser(record, ruleSummary, dialogue),
            state = _review,
        )
    }

    /** 今天的打卡短评（如果已打过卡） */
    fun todayReview(): AiChatLog? {
        val today = LocalDate.now().toString()
        return aiChats.value.lastOrNull { it.kind == AiKinds.REVIEW && it.date == today }
    }

    // ---------- 详情页 AI 辅助判断（支持多轮，按条目隔离） ----------

    fun runAutoCheck(date: LocalDate, category: Category, input: String) {
        _autoCheckOutcome.value = null
        val key = sessionKey(date, category)
        val history = _autoCheckSessions.value[key] ?: emptyList()
        stream(
            kind = AiKinds.AUTO_CHECK,
            categoryKey = category.key,
            system = if (category == Category.JIE_YIN) AiPrompts.autoCheckLevelSystem()
            else AiPrompts.autoCheckChecklistSystem(category),
            user = AiPrompts.autoCheckUser(category, input),
            history = history,
            state = _autoCheck,
        ) { fullText ->
            // 记录本轮：用户说了什么 + AI 回了什么
            _autoCheckSessions.value = _autoCheckSessions.value +
                (key to history + ChatTurn(ChatTurn.ROLE_USER, input) +
                    ChatTurn(ChatTurn.ROLE_ASSISTANT, fullText))
            val outcome = AiPrompts.parseAutoCheck(fullText)
            if (outcome != null) {
                // 只有本轮包含判断（等级或勾选）时才应用到记录；纯聊天不写记录
                val hasJudgement = outcome.level != null || outcome.items.isNotEmpty()
                if (hasJudgement) {
                    viewModelScope.launch {
                        val base = recordAt(date) ?: DailyRecord(date = date.toString())
                        var current = base
                        if (category == Category.JIE_YIN) {
                            outcome.level?.let { current = current.withJieYin(it) }
                        } else {
                            outcome.items.forEach { (index, checked) ->
                                if (index in Metrics.criteria.getValue(category).indices) {
                                    current = current.withCriterion(category, index, checked)
                                }
                            }
                        }
                        repo.save(current)
                    }
                }
            }
            _autoCheckOutcome.value = outcome
        }
    }

    // ---------- 周报 / 月报（上一周期） ----------

    /** 上一周（上周一 ~ 上周日）的范围与标题 */
    private fun lastWeekRange(today: LocalDate): Triple<LocalDate, LocalDate, String> {
        val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val start = thisMonday.minusDays(7)
        val end = start.plusDays(6)
        return Triple(start, end, "上周周报（${start.monthValue}月${start.dayOfMonth}日 ~ ${end.monthValue}月${end.dayOfMonth}日）")
    }

    /** 上一月（上月 1 号 ~ 月末）的范围与标题 */
    private fun lastMonthRange(today: LocalDate): Triple<LocalDate, LocalDate, String> {
        val lastMonth = today.minusMonths(1)
        val start = lastMonth.withDayOfMonth(1)
        val end = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth())
        return Triple(start, end, "上月月报（${start.monthValue}月1日 ~ ${end.monthValue}月${end.dayOfMonth}日）")
    }

    fun generateWeeklyReport() {
        val (start, end, label) = lastWeekRange(LocalDate.now())
        _weeklyLabel.value = label
        val records = records.value.filter { it.date >= start.toString() && it.date <= end.toString() }
        // 周期内的 AI 判断对话作为报告参考
        val dialogue = AiPrompts.dialogueContext(
            aiChats.value.filter { it.date >= start.toString() && it.date <= end.toString() }
        )
        stream(
            kind = AiKinds.WEEKLY,
            system = AiPrompts.weeklySystem(),
            user = "报告范围：$start ~ $end\n" + AiPrompts.weeklyUser(records, dialogue),
            state = _weekly,
        )
    }

    fun generateMonthlyReport() {
        val (start, end, label) = lastMonthRange(LocalDate.now())
        _monthlyLabel.value = label
        val records = records.value.filter { it.date >= start.toString() && it.date <= end.toString() }
        val dialogue = AiPrompts.dialogueContext(
            aiChats.value.filter { it.date >= start.toString() && it.date <= end.toString() }
        )
        stream(
            kind = AiKinds.MONTHLY,
            system = AiPrompts.monthlySystem(),
            user = "报告范围：$start ~ $end\n" + AiPrompts.monthlyUser(records, dialogue),
            state = _monthly,
        )
    }

    /** 所有已生成的周报/月报历史（按生成时间倒序） */
    fun reportHistory(): List<AiChatLog> = aiChats.value
        .filter { it.kind == AiKinds.WEEKLY || it.kind == AiKinds.MONTHLY }
        .sortedByDescending { it.createdAt }

    /** 永久清除当天该条目的 AI 判断对话（内存 + 记录） */
    fun clearAutoCheckSession(date: LocalDate, category: Category) = viewModelScope.launch {
        _autoCheckSessions.value = _autoCheckSessions.value - sessionKey(date, category)
        repo.deleteAutoCheckLog(date.toString(), category.key)
        _autoCheck.value = AiStreamState.Idle
        _autoCheckOutcome.value = null
    }

    /** 删除单条报告 */
    fun deleteReport(log: AiChatLog) = viewModelScope.launch { repo.deleteAiChatById(log.id) }

    /** 删除全部 AI 判断记录 */
    fun clearAllAutoCheck() = viewModelScope.launch {
        repo.deleteAiChatsByKind(AiKinds.AUTO_CHECK)
        _autoCheckSessions.value = emptyMap()
        _autoCheck.value = AiStreamState.Idle
        _autoCheckOutcome.value = null
    }

    /** 删除全部周报/月报 */
    fun clearAllReports() = viewModelScope.launch {
        repo.deleteAiChatsByKind(AiKinds.WEEKLY)
        repo.deleteAiChatsByKind(AiKinds.MONTHLY)
        _weekly.value = AiStreamState.Idle
        _monthly.value = AiStreamState.Idle
    }

    /** 清空所有用户数据（打卡 + AI 交互 + AI 成就 + 状态记录） */
    fun clearAllData() = viewModelScope.launch {
        LogPhotoStore.deleteAllPhotos(getApplication())
        repo.clearAll()
        _autoCheckSessions.value = emptyMap()
        _autoCheck.value = AiStreamState.Idle
        _autoCheckOutcome.value = null
        _weekly.value = AiStreamState.Idle
        _monthly.value = AiStreamState.Idle
        _review.value = AiStreamState.Idle
        _suggest.value = AiStreamState.Idle
        _suggestSummary.value = null
    }

    // ---------- 每日状态记录（文字 + 照片，照片纯本地） ----------

    fun dailyLogAt(date: LocalDate): DailyLog? =
        dailyLogs.value.firstOrNull { it.date == date.toString() }

    /**
     * 保存状态记录。编辑场景：保留已有照片、删除被移除的照片、压缩复制新增照片。
     */
    fun saveDailyLog(
        date: LocalDate,
        text: String,
        keepPaths: List<String>,
        removePaths: List<String>,
        newPhotos: List<android.net.Uri>,
    ) = viewModelScope.launch {
        val dateStr = date.toString()
        withContext(Dispatchers.IO) {
            removePaths.forEach { runCatching { File(it).delete() } }
        }
        val newPaths = withContext(Dispatchers.IO) {
            LogPhotoStore.savePhotos(getApplication(), dateStr, newPhotos)
        }
        repo.saveDailyLog(
            DailyLog(
                date = dateStr,
                text = text.trim(),
                photoPaths = (keepPaths + newPaths).joinToString(","),
            )
        )
    }

    /** 删除某天状态记录（连同照片文件） */
    fun deleteDailyLog(date: LocalDate) = viewModelScope.launch {
        LogPhotoStore.deletePhotosFor(getApplication(), date.toString())
        repo.deleteDailyLog(date.toString())
    }

    /** 删除全部状态记录（连同照片文件） */
    fun clearAllDailyLogs() = viewModelScope.launch {
        LogPhotoStore.deleteAllPhotos(getApplication())
        repo.clearDailyLogs()
    }

    // ---------- AI 添加成就 ----------

    fun requestAiAchievements() {
        _suggestSummary.value = null
        val records = records.value
        val defs = AchievementEngine.evaluate(records, customAchievements.value.mapNotNull { it.toAchievementDef() })
        stream(
            kind = AiKinds.ACHIEVEMENT,
            system = AiPrompts.achievementSystem(),
            user = AiPrompts.achievementUser(records, defs.map { it.def.title }),
            state = _suggest,
        ) { fullText ->
            val (specs, errors) = AiPrompts.parseAchievements(fullText)
            if (specs.isEmpty()) {
                _suggestSummary.value = "AI 返回的成就无法识别（${errors.firstOrNull() ?: "格式不正确"}），可重试。"
            } else {
                viewModelScope.launch {
                    repo.addCustomAchievements(specs.map { it.toEntity() })
                    _suggestSummary.value = "已添加 ${specs.size} 个新成就：" +
                        specs.joinToString("、") { it.title } +
                        if (errors.isNotEmpty()) "（${errors.size} 条无效）" else ""
                }
            }
        }
    }

    // ---------- 内部 ----------

    /** 统一的 SSE 流式执行：状态机 + 交互记录 + 完成后回调 */
    private fun stream(
        kind: String,
        system: String,
        user: String,
        state: MutableStateFlow<AiStreamState>,
        categoryKey: String? = null,
        history: List<ChatTurn> = emptyList(),
        onDone: ((String) -> Unit)? = null,
    ) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            state.value = AiStreamState.Loading
            val sb = StringBuilder()
            val result = aiService.streamChat(system, user, history = history) { delta ->
                sb.append(delta)
                state.value = AiStreamState.Streaming(sb.toString())
            }
            result.fold(
                onSuccess = { full ->
                    state.value = AiStreamState.Done(full)
                    repo.saveAiChat(
                        AiChatLog(
                            date = LocalDate.now().toString(),
                            kind = kind,
                            categoryKey = categoryKey,
                            prompt = user,
                            response = full,
                        )
                    )
                    onDone?.invoke(full)
                },
                onFailure = { e ->
                    state.value = AiStreamState.Error(e.message ?: "网络错误")
                },
            )
        }
    }
}
