package com.criticalbatteryoverlay.data

import android.content.Context
import android.content.SharedPreferences

class BatteryPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()
        }

    var batteryThreshold: Int
        get() = prefs.getInt(KEY_BATTERY_THRESHOLD, DEFAULT_THRESHOLD)
        set(value) {
            prefs.edit().putInt(KEY_BATTERY_THRESHOLD, value.coerceIn(1, 20)).apply()
        }

    var overlayOpacity: Float
        get() = prefs.getFloat(KEY_OVERLAY_OPACITY, DEFAULT_OPACITY)
        set(value) {
            prefs.edit().putFloat(KEY_OVERLAY_OPACITY, value.coerceIn(0.10f, 0.90f)).apply()
        }

    companion object {
        private const val PREFS_NAME = "critical_battery_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_BATTERY_THRESHOLD = "battery_threshold"
        private const val KEY_OVERLAY_OPACITY = "overlay_opacity"

        const val DEFAULT_THRESHOLD = 5
        const val DEFAULT_OPACITY = 0.35f
    }
}
