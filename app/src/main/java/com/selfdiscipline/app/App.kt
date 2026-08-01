package com.selfdiscipline.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.selfdiscipline.app.ai.AiSettingsStore
import com.selfdiscipline.app.data.AppDatabase
import com.selfdiscipline.app.data.RecordRepository
import com.selfdiscipline.app.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        // 每次启动都恢复打卡提醒（防止闹钟丢失），同时覆盖 BOOT_COMPLETED 未送达的情况
        if (AppGraph.aiSettings.reminderEnabled()) {
            ReminderScheduler.schedule(
                this,
                AppGraph.aiSettings.reminderHour(),
                AppGraph.aiSettings.reminderMinute(),
            )
        }
    }
}

/** 轻量依赖容器：整个应用只需要一份数据库和仓库 */
object AppGraph {

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch { repository.refresh() }
    }

    val aiSettings: AiSettingsStore by lazy { AiSettingsStore(appContext) }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "san_jie_san_xiu.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    val repository: RecordRepository by lazy {
        RecordRepository(
            dao = database.dailyRecordDao(),
            aiChatDao = database.aiChatDao(),
            customAchievementDao = database.customAchievementDao(),
        )
    }
}
