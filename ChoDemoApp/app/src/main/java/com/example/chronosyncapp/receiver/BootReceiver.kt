package com.example.chronosyncapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.chronosyncapp.service.ScheduleReminderService

/**
 * 开机广播接收器
 * 设备重启后自动启动日程提醒服务，重新注册所有闹钟
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, ScheduleReminderService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
