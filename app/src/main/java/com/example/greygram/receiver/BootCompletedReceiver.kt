package com.example.greygram.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.greygram.data.BatteryPreferences
import com.example.greygram.service.BatteryForegroundService

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val preferences = BatteryPreferences(context)
            if (preferences.isServiceEnabled) {
                BatteryForegroundService.startService(context)
            }
        }
    }
}
