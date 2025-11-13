package ai.asleep.reactnative.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AlarmOverlayService : Service() {
  private var windowManager: WindowManager? = null
  private var overlayView: View? = null
  private var alarmId: String = "unknown"
  private var label: String? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_SHOW -> {
        alarmId = intent.getStringExtra(EXTRA_ID) ?: "unknown"
        label = intent.getStringExtra(EXTRA_LABEL)
        if (canDrawOverlays()) {
          showOverlay()
        }
      }
      ACTION_HIDE -> {
        hideOverlay()
        stopSelf()
      }
    }
    return START_NOT_STICKY
  }

  private fun canDrawOverlays(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Settings.canDrawOverlays(this)
    } else true
  }

  private fun showOverlay() {
    if (overlayView != null) return
    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(60, 80, 60, 80)
      setBackgroundColor(0xCC000000.toInt()) // semi-transparent black
      keepScreenOn = true
    }
    val title = TextView(this).apply {
      textSize = 22f
      setTextColor(0xFFFFFFFF.toInt())
      text = (label ?: "Alarm")
    }
    val stop = Button(this).apply {
      text = "Stop"
      setOnClickListener {
        AlarmRingingService.stop(this@AlarmOverlayService)
        hideOverlay()
        stopSelf()
      }
    }
    container.addView(title)
    container.addView(stop)
    overlayView = container

    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else
      WindowManager.LayoutParams.TYPE_PHONE

    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      type,
      // Focusable so button is clickable; not touch modal to allow back/home gestures
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP
    }
    windowManager?.addView(overlayView, params)
  }

  private fun hideOverlay() {
    if (overlayView != null) {
      try { windowManager?.removeView(overlayView) } catch (_: Throwable) {}
      overlayView = null
    }
  }

  override fun onDestroy() {
    hideOverlay()
    super.onDestroy()
  }

  companion object {
    const val ACTION_SHOW = "ai.asleep.reactnative.alarm.action.OVERLAY_SHOW"
    const val ACTION_HIDE = "ai.asleep.reactnative.alarm.action.OVERLAY_HIDE"
    const val EXTRA_ID = "extra_id"
    const val EXTRA_LABEL = "extra_label"

    fun show(context: Context, id: String, label: String?) {
      val i = Intent(context, AlarmOverlayService::class.java)
        .setAction(ACTION_SHOW)
        .putExtra(EXTRA_ID, id)
        .putExtra(EXTRA_LABEL, label)
      if (Build.VERSION.SDK_INT >= 26) {
        context.startForegroundService(i)
      } else {
        context.startService(i)
      }
    }

    fun hide(context: Context) {
      val i = Intent(context, AlarmOverlayService::class.java).setAction(ACTION_HIDE)
      context.startService(i)
    }
  }
}


