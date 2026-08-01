package com.selfdiscipline.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailyRecordDao {

    @Query("SELECT * FROM daily_record")
    suspend fun getAll(): List<DailyRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DailyRecord)

    @Query("DELETE FROM daily_record")
    suspend fun deleteAll()
}
