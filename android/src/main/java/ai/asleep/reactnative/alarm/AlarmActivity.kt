package ai.asleep.reactnative.alarm

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.content.res.ColorStateList
import android.util.Log

class AlarmActivity : Activity() {
  private var alarmId: String = "unknown"
  private var label: String? = null
  private var overlayBgColor: Int? = null
  private var overlayTextColor: Int? = null
  private var overlayBtnBgColor: Int? = null
  private var overlayBtnTextColor: Int? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    alarmId = intent.getStringExtra(AlarmReceiver.EXTRA_ID) ?: "unknown"
    label = intent.getStringExtra(AlarmReceiver.EXTRA_LABEL)
    if (intent.hasExtra(EXTRA_OVERLAY_BG)) overlayBgColor = intent.getIntExtra(EXTRA_OVERLAY_BG, 0)
    if (intent.hasExtra(EXTRA_OVERLAY_TEXT)) overlayTextColor = intent.getIntExtra(EXTRA_OVERLAY_TEXT, 0)
    if (intent.hasExtra(EXTRA_OVERLAY_BTN_BG)) overlayBtnBgColor = intent.getIntExtra(EXTRA_OVERLAY_BTN_BG, 0)
    if (intent.hasExtra(EXTRA_OVERLAY_BTN_TEXT)) overlayBtnTextColor = intent.getIntExtra(EXTRA_OVERLAY_BTN_TEXT, 0)
    Log.d("RNAlarm", "AlarmActivity onCreate id=$alarmId label=$label bg=$overlayBgColor text=$overlayTextColor btnBg=$overlayBtnBgColor btnText=$overlayBtnTextColor")

    // Ensure screen turns on and shows above lock screen
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
          WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
          WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
      )
    }

    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(60, 80, 60, 80)
      setBackgroundColor(overlayBgColor ?: 0xCC000000.toInt())
    }
    val title = TextView(this).apply {
      textSize = 26f
      text = label ?: "Alarm"
      setTextColor(overlayTextColor ?: 0xFFFFFFFF.toInt())
    }
    val stop = Button(this).apply {
      text = "Stop"
      setOnClickListener { stopAlarmAndFinish() }
      (overlayBtnTextColor ?: overlayTextColor)?.let { try { setTextColor(it) } catch (_: Throwable) {} }
      overlayBtnBgColor?.let { c ->
        try { backgroundTintList = ColorStateList.valueOf(c) } catch (_: Throwable) {}
      }
    }
    container.addView(title)
    container.addView(stop)
    setContentView(container)
    Log.d("RNAlarm", "AlarmActivity content set with bgApplied=${overlayBgColor != null} textApplied=${overlayTextColor != null}")
  }

  private fun stopAlarmAndFinish() {
    // Stop ringing service if running
    try {
      AlarmRingingService.stop(this)
    } catch (_: Throwable) {}
    // Dismiss notification if present
    val mgr = getSystemService(android.app.NotificationManager::class.java)
    mgr.cancel(alarmId.hashCode())
    // Mark disabled in storage
    val storage = AlarmStorage(this)
    val items = storage.loadAll().map {
      if (it.id == alarmId) it.copy(enabled = false) else it
    }
    storage.saveAll(items)
    finish()
  }

  override fun onDestroy() {
    super.onDestroy()
  }

  companion object {
    const val EXTRA_OVERLAY_BG = "alarm_activity_overlay_bg"
    const val EXTRA_OVERLAY_TEXT = "alarm_activity_overlay_text"
    const val EXTRA_OVERLAY_BTN_BG = "alarm_activity_overlay_btn_bg"
    const val EXTRA_OVERLAY_BTN_TEXT = "alarm_activity_overlay_btn_text"
  }
}

