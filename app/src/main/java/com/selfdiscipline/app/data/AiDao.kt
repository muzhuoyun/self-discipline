package com.selfdiscipline.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AiChatDao {

    @Query("SELECT * FROM ai_chat_log ORDER BY createdAt")
    suspend fun getAll(): List<AiChatLog>

    @Insert
    suspend fun insert(log: AiChatLog)

    @Query("DELETE FROM ai_chat_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ai_chat_log WHERE kind = :kind")
    suspend fun deleteByKind(kind: String)

    @Query("DELETE FROM ai_chat_log WHERE kind = :kind AND date = :date AND categoryKey = :categoryKey")
    suspend fun deleteByKindDateCategory(kind: String, date: String, categoryKey: String)

    @Query("DELETE FROM ai_chat_log WHERE kind = :kind AND date = :date")
    suspend fun deleteByKindAndDate(kind: String, date: String)

    @Query("DELETE FROM ai_chat_log")
    suspend fun deleteAll()
}

@Dao
interface CustomAchievementDao {

    @Query("SELECT * FROM custom_achievement ORDER BY createdAt")
    suspend fun getAll(): List<CustomAchievement>

    @Insert
    suspend fun insertAll(achievements: List<CustomAchievement>)

    @Query("DELETE FROM custom_achievement")
    suspend fun deleteAll()
}
