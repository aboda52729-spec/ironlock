package com.example.ironlock

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Typeface

class BlockOverlayController(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isAdded = false

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlayView()
    }

    private fun createOverlayView() {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E6000000")) // Semi-transparent black for better feel
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            // Make sure the layout fills the entire screen including status bar
            fitsSystemWindows = false
        }

        val textTitle = TextView(context).apply {
            text = "IRON LOCK"
            textSize = 42f
            setTextColor(Color.parseColor("#E50914")) // Netflix Red
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTypeface(null, Typeface.BOLD)
        }

        val textStatus = TextView(context).apply {
            text = "الجلسة نشطة - لا مفر"
            textSize = 24f
            setTextColor(Color.WHITE)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, 32, 0, 0)
        }

        val textMessage = TextView(context).apply {
            text = "لقد اخترت التركيز. الهاتف الآن في وضع القفل حتى تنتهي الجلسة."
            textSize = 18f
            setTextColor(Color.parseColor("#CCCCCC"))
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, 48, 0, 0)
        }

        layout.addView(textTitle)
        layout.addView(textStatus)
        layout.addView(textMessage)

        overlayView = layout
    }

    fun show() {
        if (!isAdded && overlayView != null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER
            params.windowAnimations = android.R.style.Animation_Toast // Smooth fade in
            
            try {
                windowManager?.addView(overlayView, params)
                isAdded = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hide() {
        if (isAdded && overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
                isAdded = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
