package com.example.chronosyncapp.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.chronosyncapp.AppDatabase
import com.example.chronosyncapp.R
import com.example.chronosyncapp.receiver.ScheduleReminderReceiver
import com.example.chronosyncapp.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * 日程提醒前台服务
 * 遍历所有未到期的日程，用 AlarmManager 注册提醒
 */
class ScheduleReminderService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 创建通知渠道
        NotificationHelper.createChannel(this)

        // 前台服务通知
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tab_calendar)
            .setContentTitle("日程提醒服务")
            .setContentText("正在运行，确保日程提醒及时送达")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1001, notification)

        // 异步注册所有日程提醒
        CoroutineScope(Dispatchers.IO).launch {
            registerAllReminders()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun registerAllReminders() {
        try {
            val db = AppDatabase.get(this)
            val events = db.scheduleEventDao().getAllEvents()
            val now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
            val todayStr = now.toLocalDate().toString()
            val nowTime = now.toLocalTime()

            for (event in events) {
                // 跳过已过期的日程
                if (event.date < todayStr) continue
                if (event.date == todayStr && LocalTime.parse(event.startTime) <= nowTime) continue

                ScheduleReminderReceiver.scheduleReminder(
                    this, event.id, event.date, event.startTime, event.title
                )
            }
        } catch (_: Exception) {
            // 静默处理
        }
    }
}
