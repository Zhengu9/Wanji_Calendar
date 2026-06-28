package com.example.chronosyncapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机广播接收器 - 阶段4实现 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 阶段4实现：启动ScheduleReminderService
    }
}
