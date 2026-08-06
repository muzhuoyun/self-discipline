package com.selfdiscipline.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 仓库：整个应用的数据量很小，启动时全量载入内存，
 * 任何修改同步写库并更新内存态（以磁盘为准）。
 */
class RecordRepository(
    private val dao: DailyRecordDao,
    private val aiChatDao: AiChatDao,
    private val customAchievementDao: CustomAchievementDao,
    private val dailyLogDao: DailyLogDao,
) {

    private val _records = MutableStateFlow<List<DailyRecord>>(emptyList())
    val records: StateFlow<List<DailyRecord>> = _records.asStateFlow()

    private val _aiChats = MutableStateFlow<List<AiChatLog>>(emptyList())
    val aiChats: StateFlow<List<AiChatLog>> = _aiChats.asStateFlow()

    private val _customAchievements = MutableStateFlow<List<CustomAchievement>>(emptyList())
    val customAchievements: StateFlow<List<CustomAchievement>> = _customAchievements.asStateFlow()

    private val _dailyLogs = MutableStateFlow<List<DailyLog>>(emptyList())
    val dailyLogs: StateFlow<List<DailyLog>> = _dailyLogs.asStateFlow()

    suspend fun refresh() {
        _records.value = dao.getAll()
        _aiChats.value = aiChatDao.getAll()
        _customAchievements.value = customAchievementDao.getAll()
        _dailyLogs.value = dailyLogDao.getAll()
    }

    suspend fun save(record: DailyRecord) {
        dao.upsert(record)
        _records.value = dao.getAll()
    }

    suspend fun saveAiChat(log: AiChatLog) {
        aiChatDao.insert(log)
        _aiChats.value = aiChatDao.getAll()
    }

    suspend fun deleteAiChatById(id: Long) {
        aiChatDao.deleteById(id)
        _aiChats.value = aiChatDao.getAll()
    }

    suspend fun deleteAiChatsByKind(kind: String) {
        aiChatDao.deleteByKind(kind)
        _aiChats.value = aiChatDao.getAll()
    }

    /** 删除某一天某个条目的 AI 判断记录 */
    suspend fun deleteAutoCheckLog(date: String, categoryKey: String) {
        aiChatDao.deleteByKindDateCategory(AiKinds.AUTO_CHECK, date, categoryKey)
        _aiChats.value = aiChatDao.getAll()
    }

    /** 删除某一天的打卡评价（重新评价时覆盖旧的一份，每天只保留一份） */
    suspend fun deleteReviewFor(date: String) {
        aiChatDao.deleteByKindAndDate(AiKinds.REVIEW, date)
        _aiChats.value = aiChatDao.getAll()
    }

    suspend fun clearAiChats() {
        aiChatDao.deleteAll()
        _aiChats.value = emptyList()
    }

    /** 清空所有用户数据：打卡记录 + AI 交互 + AI 成就 */
    suspend fun clearAll() {
        dao.deleteAll()
        aiChatDao.deleteAll()
        customAchievementDao.deleteAll()
        refresh()
    }

    // ---------- 每日状态记录 ----------

    /** 保存状态记录，返回数据库生成的 id */
    suspend fun saveDailyLog(log: DailyLog): Long {
        val id = dailyLogDao.upsert(log)
        _dailyLogs.value = dailyLogDao.getAll()
        return id
    }

    /** 删除一条状态记录（照片文件由调用方清理） */
    suspend fun deleteDailyLog(id: Long) {
        dailyLogDao.deleteById(id)
        _dailyLogs.value = dailyLogDao.getAll()
    }

    /** 删除全部状态记录（照片文件由调用方清理） */
    suspend fun clearDailyLogs() {
        dailyLogDao.deleteAll()
        _dailyLogs.value = emptyList()
    }

    suspend fun addCustomAchievements(items: List<CustomAchievement>) {
        customAchievementDao.insertAll(items)
        _customAchievements.value = customAchievementDao.getAll()
    }
}
