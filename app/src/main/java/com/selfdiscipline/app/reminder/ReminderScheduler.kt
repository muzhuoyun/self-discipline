package com.selfdiscipline.app.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * 打卡提醒：AlarmManager 每天定时触发一次（非精确闹钟，无需特殊权限）。
 */
object ReminderScheduler {

    const val CHANNEL_ID = "check_in_reminder"
    private const val REQUEST_CODE = 1001

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "打卡提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "每天定时提醒你为三戒三修四德打卡"
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * 设置一次精确的每日提醒（[hour]:[minute]，已过则顺延到明天）。
     * 使用 setExactAndAllowWhileIdle + USE_EXACT_ALARM 权限（安装时自动授予），
     * 保证准点触发；触发后由 Receiver 自动续排下一天。
     */
    fun schedule(context: Context, hour: Int, minute: Int) {
        ensureChannel(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val triggerAt = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        am.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt.timeInMillis,
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
