package com.selfdiscipline.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * 每日状态记录：一段文字 + 若干照片（照片仅存本地，不进 AI）。
 * 同一天允许多条记录。
 */
@Entity(tableName = "daily_log")
data class DailyLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val text: String,
    /** 照片文件路径，逗号分隔 */
    val photoPaths: String,
    /** AI 以医生身份对这条记录的回复 */
    val doctorReply: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface DailyLogDao {

    @Query("SELECT * FROM daily_log WHERE date = :date ORDER BY createdAt")
    suspend fun getByDate(date: String): List<DailyLog>

    @Query("SELECT * FROM daily_log ORDER BY date, createdAt")
    suspend fun getAll(): List<DailyLog>

    @Query("SELECT * FROM daily_log WHERE date BETWEEN :start AND :end ORDER BY date")
    suspend fun getBetween(start: String, end: String): List<DailyLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: DailyLog): Long

    @Query("DELETE FROM daily_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM daily_log")
    suspend fun deleteAll()
}
