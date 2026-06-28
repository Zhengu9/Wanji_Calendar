package com.example.chronosyncapp.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.chronosyncapp.util.NotificationHelper
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 日程提醒广播接收器
 * 收到 AlarmManager 的定时广播后，弹出日程通知
 */
class ScheduleReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "日程"
        val startTime = intent.getStringExtra("startTime") ?: ""
        NotificationHelper.showReminder(context, title, startTime)
    }

    companion object {
        /**
         * 为日程设置提醒闹钟（提前15分钟）
         */
        fun scheduleReminder(context: Context, eventId: Long, date: String, startTime: String, title: String) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val beijingZone = ZoneId.of("Asia/Shanghai")

            val localDate = LocalDate.parse(date)
            val localTime = LocalTime.parse(startTime)
            val triggerTime = ZonedDateTime.of(localDate, localTime, beijingZone)
                .minusMinutes(15)
                .toInstant()
                .toEpochMilli()

            // 如果已经过了提醒时间，不设置
            if (triggerTime <= System.currentTimeMillis()) return

            val intent = Intent(context, ScheduleReminderReceiver::class.java).apply {
                putExtra("title", title)
                putExtra("startTime", startTime)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, eventId.toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }

        /**
         * 取消日程的提醒闹钟
         */
        fun cancelReminder(context: Context, eventId: Long) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, ScheduleReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, eventId.toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
