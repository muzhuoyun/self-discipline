package com.selfdiscipline.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** 每日状态记录：一段文字 + 若干照片（照片仅存本地，不进 AI） */
@Entity(tableName = "daily_log")
data class DailyLog(
    @PrimaryKey val date: String,
    val text: String,
    /** 照片文件路径，逗号分隔 */
    val photoPaths: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface DailyLogDao {

    @Query("SELECT * FROM daily_log WHERE date = :date")
    suspend fun getByDate(date: String): DailyLog?

    @Query("SELECT * FROM daily_log ORDER BY date")
    suspend fun getAll(): List<DailyLog>

    @Query("SELECT * FROM daily_log WHERE date BETWEEN :start AND :end ORDER BY date")
    suspend fun getBetween(start: String, end: String): List<DailyLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: DailyLog)

    @Query("DELETE FROM daily_log WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM daily_log")
    suspend fun deleteAll()
}
