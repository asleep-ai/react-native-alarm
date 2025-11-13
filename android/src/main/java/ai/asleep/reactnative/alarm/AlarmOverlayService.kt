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
import android.util.Log
import android.content.res.ColorStateList

class AlarmOverlayService : Service() {
  private var windowManager: WindowManager? = null
  private var overlayView: View? = null
  private var alarmId: String = "unknown"
  private var label: String? = null
  private var overlayBgColor: Int? = null
  private var overlayTextColor: Int? = null
  private var overlayBtnBgColor: Int? = null
  private var overlayBtnTextColor: Int? = null
  private var snoozeMinutes: Int = 5

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_SHOW -> {
        alarmId = intent.getStringExtra(EXTRA_ID) ?: "unknown"
        label = intent.getStringExtra(EXTRA_LABEL)
        if (intent.hasExtra(EXTRA_OVERLAY_BG)) overlayBgColor = intent.getIntExtra(EXTRA_OVERLAY_BG, 0)
        if (intent.hasExtra(EXTRA_OVERLAY_TEXT)) overlayTextColor = intent.getIntExtra(EXTRA_OVERLAY_TEXT, 0)
        if (intent.hasExtra(EXTRA_OVERLAY_BTN_BG)) overlayBtnBgColor = intent.getIntExtra(EXTRA_OVERLAY_BTN_BG, 0)
        if (intent.hasExtra(EXTRA_OVERLAY_BTN_TEXT)) overlayBtnTextColor = intent.getIntExtra(EXTRA_OVERLAY_BTN_TEXT, 0)
        // Read snooze minutes if provided; default to 5
        snoozeMinutes = if (intent.hasExtra(EXTRA_OVERLAY_SNOOZE_MIN)) {
          intent.getIntExtra(EXTRA_OVERLAY_SNOOZE_MIN, 5)
        } else 5
        Log.d("RNAlarm", "OverlayService ACTION_SHOW id=$alarmId label=$label bg=$overlayBgColor text=$overlayTextColor btnBg=$overlayBtnBgColor btnText=$overlayBtnTextColor snoozeMin=$snoozeMinutes canDraw=${canDrawOverlays()}")
        if (canDrawOverlays()) {
          showOverlay()
        }
      }
      ACTION_HIDE -> {
        Log.d("RNAlarm", "OverlayService ACTION_HIDE id=$alarmId")
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

    Log.d("RNAlarm", "OverlayService showOverlay apply bg=${overlayBgColor ?: 0xCC000000.toInt()} text=${overlayTextColor ?: 0xFFFFFFFF.toInt()}")
    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(60, 80, 60, 80)
      setBackgroundColor(overlayBgColor ?: 0xCC000000.toInt()) // semi-transparent if not provided
      keepScreenOn = true
      isClickable = true
      isFocusable = true
    }
    container.setOnClickListener {
      try {
        val fs = Intent(this, AlarmActivity::class.java)
          .putExtra(AlarmReceiver.EXTRA_ID, alarmId)
          .putExtra(AlarmReceiver.EXTRA_LABEL, label)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        overlayBgColor?.let { fs.putExtra(AlarmActivity.EXTRA_OVERLAY_BG, it) }
        overlayTextColor?.let { fs.putExtra(AlarmActivity.EXTRA_OVERLAY_TEXT, it) }
        overlayBtnBgColor?.let { fs.putExtra(AlarmActivity.EXTRA_OVERLAY_BTN_BG, it) }
        overlayBtnTextColor?.let { fs.putExtra(AlarmActivity.EXTRA_OVERLAY_BTN_TEXT, it) }
        fs.putExtra(AlarmActivity.EXTRA_SNOOZE_MIN, snoozeMinutes)
        startActivity(fs)
      } catch (_: Throwable) {}
      hideOverlay()
      stopSelf()
    }
    val title = TextView(this).apply {
      textSize = 22f
      setTextColor(overlayTextColor ?: 0xFFFFFFFF.toInt())
      text = (label ?: "Alarm")
    }
    val stop = Button(this).apply {
      text = "Stop"
      (overlayBtnTextColor ?: overlayTextColor)?.let { try { setTextColor(it) } catch (_: Throwable) {} }
      overlayBtnBgColor?.let { c ->
        try { backgroundTintList = ColorStateList.valueOf(c) } catch (_: Throwable) {}
      }
      setOnClickListener {
        AlarmRingingService.stop(this@AlarmOverlayService)
        hideOverlay()
        stopSelf()
      }
    }
    val snooze = Button(this).apply {
      text = "Snooze"
      (overlayBtnTextColor ?: overlayTextColor)?.let { try { setTextColor(it) } catch (_: Throwable) {} }
      overlayBtnBgColor?.let { c ->
        try { backgroundTintList = ColorStateList.valueOf(c) } catch (_: Throwable) {}
      }
      setOnClickListener {
        val style = mutableMapOf<String, Any?>()
        overlayBgColor?.let { style["overlayBackgroundColor"] = it }
        overlayTextColor?.let { style["overlayTextColor"] = it }
        overlayBtnBgColor?.let { style["overlayButtonBackgroundColor"] = it }
        overlayBtnTextColor?.let { style["overlayButtonTextColor"] = it }
        AlarmRingingService.snooze(this@AlarmOverlayService, alarmId, label, snoozeMinutes, style)
        hideOverlay()
        stopSelf()
      }
    }
    container.addView(title)
    container.addView(snooze)
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
      Log.d("RNAlarm", "OverlayService hideOverlay removed view for id=$alarmId")
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
    const val EXTRA_OVERLAY_BG = "extra_overlay_bg"
    const val EXTRA_OVERLAY_TEXT = "extra_overlay_text"
    const val EXTRA_OVERLAY_BTN_BG = "extra_overlay_btn_bg"
    const val EXTRA_OVERLAY_BTN_TEXT = "extra_overlay_btn_text"
    const val EXTRA_OVERLAY_SNOOZE_MIN = "extra_overlay_snooze_min"

    fun show(context: Context, id: String, label: String?, style: Map<String, Any?>? = null) {
      val i = Intent(context, AlarmOverlayService::class.java)
        .setAction(ACTION_SHOW)
        .putExtra(EXTRA_ID, id)
        .putExtra(EXTRA_LABEL, label)
      if (style != null) {
        (style["overlayBackgroundColor"] as? Int)?.let { i.putExtra(EXTRA_OVERLAY_BG, it) }
        (style["overlayTextColor"] as? Int)?.let { i.putExtra(EXTRA_OVERLAY_TEXT, it) }
        (style["overlayButtonBackgroundColor"] as? Int)?.let { i.putExtra(EXTRA_OVERLAY_BTN_BG, it) }
        (style["overlayButtonTextColor"] as? Int)?.let { i.putExtra(EXTRA_OVERLAY_BTN_TEXT, it) }
        (style["snoozeMinutes"] as? Int)?.let { i.putExtra(EXTRA_OVERLAY_SNOOZE_MIN, it) }
        Log.d("RNAlarm", "OverlayService.show() id=$id label=$label styleBg=${style["overlayBackgroundColor"]} styleText=${style["overlayTextColor"]} btnBg=${style["overlayButtonBackgroundColor"]} btnText=${style["overlayButtonTextColor"]} snoozeMin=${style["snoozeMinutes"]}")
      }
      // Always use startService; this overlay service does not post a foreground notification.
      // It's started from a foreground ringing service, so it's allowed without FGS.
      context.startService(i)
    }

    fun hide(context: Context) {
      val i = Intent(context, AlarmOverlayService::class.java).setAction(ACTION_HIDE)
      context.startService(i)
    }
  }
}


