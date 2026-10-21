package com.criticalbatteryoverlay.util

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.WindowManager

object RedOverlayManager {

    private var overlayView: View? = null
    private var isShowing = false

    fun showOverlay(context: Context, opacity: Float = 0.35f) {
        if (isShowing || overlayView != null) {
            updateOpacity(opacity)
            return
        }

        if (!PermissionUtils.canDrawOverlays(context)) {
            return
        }

        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val alphaInt = (opacity.coerceIn(0.10f, 0.90f) * 255).toInt()
            val redColor = Color.argb(alphaInt, 255, 0, 0)

            val view = View(context).apply {
                setBackgroundColor(redColor)
            }

            val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutParamsType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

            windowManager.addView(view, params)
            overlayView = view
            isShowing = true
        } catch (e: Exception) {
            overlayView = null
            isShowing = false
        }
    }

    fun hideOverlay(context: Context) {
        val view = overlayView ?: return
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(view)
        } catch (e: Exception) {
            // Detached
        } finally {
            overlayView = null
            isShowing = false
        }
    }

    fun updateOpacity(opacity: Float) {
        val view = overlayView ?: return
        try {
            val alphaInt = (opacity.coerceIn(0.10f, 0.90f) * 255).toInt()
            view.setBackgroundColor(Color.argb(alphaInt, 255, 0, 0))
        } catch (_: Exception) {}
    }

    fun isOverlayActive(): Boolean {
        return isShowing && overlayView != null
    }
}
