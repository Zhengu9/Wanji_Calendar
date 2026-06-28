package com.example.chronosyncapp.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** 日程提醒前台服务 - 阶段4实现 */
class ScheduleReminderService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 阶段4实现：注册所有日程的AlarmManager提醒
        stopSelf()
        return START_NOT_STICKY
    }
}
