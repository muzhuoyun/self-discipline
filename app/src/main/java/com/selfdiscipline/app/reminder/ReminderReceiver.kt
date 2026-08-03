package com.selfdiscipline.app.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.selfdiscipline.app.AppGraph
import com.selfdiscipline.app.MainActivity
import com.selfdiscipline.app.R

/**
 * 打卡提醒接收器：定时触发时发通知；设备重启后恢复闹钟。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 设备重启后恢复提醒
            val s = AppGraph.aiSettings
            if (s.reminderEnabled()) {
                ReminderScheduler.schedule(context, s.reminderHour(), s.reminderMinute())
            }
            return
        }

        // 到点：发通知
        ReminderScheduler.ensureChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_checkin)
            .setContentTitle("该打卡了 💪")
            .setContentText("今天的三戒七修还没打分，花 2 分钟记录一下，明天的你会感谢今天的自己。")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        Log.d("SJXS", "reminder: notifying id=1001 channel=${ReminderScheduler.CHANNEL_ID} enabled=${nm.areNotificationsEnabled()}")
        nm.notify(1001, notification)

        // 续排下一天的提醒（精确闹钟需要每次触发后重排）
        val s = AppGraph.aiSettings
        if (s.reminderEnabled()) {
            ReminderScheduler.schedule(context, s.reminderHour(), s.reminderMinute())
        }
    }
}
