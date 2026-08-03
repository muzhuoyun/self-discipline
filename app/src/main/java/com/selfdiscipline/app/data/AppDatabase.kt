package com.selfdiscipline.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DailyRecord::class, AiChatLog::class, CustomAchievement::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyRecordDao(): DailyRecordDao
    abstract fun aiChatDao(): AiChatDao
    abstract fun customAchievementDao(): CustomAchievementDao

    companion object {

        /** v2 → v3：新增四德（孝/诚/和/勤）四个掩码列，默认 0（旧记录总分不变） */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_record ADD COLUMN xiaoMask INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_record ADD COLUMN chengMask INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_record ADD COLUMN heMask INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_record ADD COLUMN qinMask INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v1 → v2：新增 AI 交互记录表和 AI 自定义成就表，保留原有打卡数据 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_chat_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, `kind` TEXT NOT NULL, `categoryKey` TEXT, " +
                        "`prompt` TEXT NOT NULL, `response` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_achievement` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`emoji` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                        "`metric` TEXT NOT NULL, `window` TEXT NOT NULL, " +
                        "`targetValue` INTEGER NOT NULL, `windowDays` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }
    }
}
