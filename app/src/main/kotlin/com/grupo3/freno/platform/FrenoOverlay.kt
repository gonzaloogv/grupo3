package com.grupo3.freno.platform

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.grupo3.freno.MainActivity
import com.grupo3.freno.data.FrenoEventStore
import com.grupo3.freno.model.FrenoEvent

/** Muestra una advertencia de pantalla completa sobre otras aplicaciones ante un riesgo alto. */
object FrenoOverlay {
    private const val TAG = "FrenoOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var currentWindowManager: WindowManager? = null
    private var currentReasonView: TextView? = null

    fun show(context: Context, event: FrenoEvent, onShown: () -> Unit) {
        mainHandler.post {
            if (MainActivity.isForeground ||
                !context.getSystemService(PowerManager::class.java).isInteractive ||
                context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            ) return@post
            if (currentView != null) {
                currentReasonView?.text = event.reason
                Log.i(TAG, "High-risk overlay updated")
                onShown()
                return@post
            }
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Overlay permission unavailable")
                return@post
            }

            val scale = context.resources.displayMetrics.density
            fun dp(value: Int) = (value * scale).toInt()
            val panel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(28), dp(48), dp(28), dp(32))
                setBackgroundColor(Color.rgb(200, 16, 36))
            }

            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            content.addView(TextView(context).apply {
                text = "!"
                setTextColor(Color.WHITE)
                textSize = 72f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.rgb(160, 10, 27))
                    shape = GradientDrawable.OVAL
                }
            }, LinearLayout.LayoutParams(dp(104), dp(104)).apply {
                bottomMargin = dp(32)
            })
            content.addView(TextView(context).apply {
                text = "ALTO"
                setTextColor(Color.WHITE)
                textSize = 56f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.03f
            })
            content.addView(TextView(context).apply {
                text = "Este mensaje puede ser una estafa"
                setTextColor(Color.WHITE)
                textSize = 29f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(12), 0, dp(24))
            })
            val reasonView = TextView(context).apply {
                text = event.reason
                setTextColor(Color.WHITE)
                textSize = 22f
                setLineSpacing(dp(5).toFloat(), 1f)
            }
            content.addView(reasonView)
            val scrollContent = ScrollView(context).apply {
                isFillViewport = true
                addView(content)
            }
            panel.addView(scrollContent, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
            panel.addView(TextView(context).apply {
                text = "ENTENDIDO"
                setTextColor(Color.rgb(160, 10, 27))
                textSize = 21f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(16).toFloat()
                }
                setOnClickListener {
                    FrenoEventStore.dismissAlert()
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64),
            ))

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }

            runCatching {
                windowManager.addView(panel, params)
                currentView = panel
                currentWindowManager = windowManager
                currentReasonView = reasonView
                Log.i(TAG, "High-risk overlay shown")
                onShown()
            }.onFailure { Log.e(TAG, "Could not show high-risk overlay", it) }
        }
    }

    fun dismiss() {
        mainHandler.post {
            currentView?.let { view ->
                runCatching { currentWindowManager?.removeView(view) }
                    .onFailure { Log.w(TAG, "Could not remove overlay", it) }
            }
            currentView = null
            currentWindowManager = null
            currentReasonView = null
        }
    }
}
