package ai.asleep.reactnative.alarm

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class ForegroundTimerService : Service() {
  private val handler = Handler(Looper.getMainLooper())

  private var timerId: String? = null
  private var label: String? = null
  private var targetTimeMs: Long = 0L
  private var remainingWhenPaused: Long = 0L
  private var isPaused: Boolean = false
  private var notifStyle: NotificationHelper.Style = NotificationHelper.Style()
  private var overlayBgColor: Int? = null
  private var overlayTextColor: Int? = null
  private var overlayBtnBgColor: Int? = null
  private var overlayBtnTextColor: Int? = null
  private var snoozeMinutes: Int = 5
  private val logTag = "RNAlarm"

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START -> {
        val id = intent.getStringExtra(EXTRA_ID) ?: return START_NOT_STICKY
        val seconds = intent.getLongExtra(EXTRA_SECONDS, 0L)
        label = intent.getStringExtra(EXTRA_LABEL)
        timerId = id
        isPaused = false
        // Build style from extras if provided
        notifStyle = NotificationHelper.Style(
          timerChannelId = intent.getStringExtra(EXTRA_STYLE_TIMER_CHANNEL_ID) ?: NotificationHelper.CHANNEL_TIMERS_HIGH,
          alertChannelId = intent.getStringExtra(EXTRA_STYLE_ALERT_CHANNEL_ID) ?: NotificationHelper.CHANNEL_ALERTS,
          smallIconName = intent.getStringExtra(EXTRA_STYLE_SMALL_ICON),
          accentColor = if (intent.hasExtra(EXTRA_STYLE_ACCENT_COLOR)) intent.getIntExtra(EXTRA_STYLE_ACCENT_COLOR, 0) else null,
          useChronometer = intent.getBooleanExtra(EXTRA_STYLE_USE_CHRONOMETER, true)
        )
        if (intent.hasExtra(EXTRA_STYLE_OVERLAY_BG)) {
          overlayBgColor = intent.getIntExtra(EXTRA_STYLE_OVERLAY_BG, 0)
        }
        if (intent.hasExtra(EXTRA_STYLE_OVERLAY_TEXT)) {
          overlayTextColor = intent.getIntExtra(EXTRA_STYLE_OVERLAY_TEXT, 0)
        }
        if (intent.hasExtra(EXTRA_STYLE_OVERLAY_BTN_BG)) {
          overlayBtnBgColor = intent.getIntExtra(EXTRA_STYLE_OVERLAY_BTN_BG, 0)
        }
        if (intent.hasExtra(EXTRA_STYLE_OVERLAY_BTN_TEXT)) {
          overlayBtnTextColor = intent.getIntExtra(EXTRA_STYLE_OVERLAY_BTN_TEXT, 0)
        }
        if (intent.hasExtra(EXTRA_STYLE_SNOOZE_MIN)) {
          snoozeMinutes = intent.getIntExtra(EXTRA_STYLE_SNOOZE_MIN, 5)
        }
        android.util.Log.d(logTag, "FGTimer ACTION_START id=$id label=$label seconds=$seconds style=$notifStyle overlayBg=$overlayBgColor overlayText=$overlayTextColor btnBg=$overlayBtnBgColor btnText=$overlayBtnTextColor snoozeMin=$snoozeMinutes")
        targetTimeMs = System.currentTimeMillis() + seconds * 1000
        startForegroundInternal(buildNotification())
        tick()
      }
      ACTION_PAUSE -> {
        android.util.Log.d(logTag, "FGTimer ACTION_PAUSE id=${timerId}")
        if (!isPaused) {
          val now = System.currentTimeMillis()
          remainingWhenPaused = ((targetTimeMs - now) / 1000).coerceAtLeast(0)
          isPaused = true
          updateNotification()
        }
      }
      ACTION_RESUME -> {
        android.util.Log.d(logTag, "FGTimer ACTION_RESUME id=${timerId}")
        if (isPaused) {
          val now = System.currentTimeMillis()
          targetTimeMs = now + remainingWhenPaused * 1000
          isPaused = false
          updateNotification()
          tick()
        }
      }
      ACTION_STOP -> {
        android.util.Log.d(logTag, "FGTimer ACTION_STOP id=${timerId}")
        stopSelf()
      }
    }
    return START_STICKY
  }

  private fun startForegroundInternal(notification: Notification) {
    if (Build.VERSION.SDK_INT >= 29) {
      android.util.Log.d(logTag, "FGTimer startForeground (Q+) id=$NOTIF_ID")
      startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
    } else {
      android.util.Log.d(logTag, "FGTimer startForeground id=$NOTIF_ID")
      startForeground(NOTIF_ID, notification)
    }
  }

  private fun buildNotification(): Notification {
    val id = timerId ?: "unknown"
    val remainingSeconds = remainingSeconds()
    return NotificationHelper.buildTimerNotification(
      this,
      id,
      label,
      remainingSeconds,
      isPaused,
      notifStyle
    )
  }

  private fun updateNotification() {
    val notif = buildNotification()
    android.util.Log.d(logTag, "FGTimer updateNotification isPaused=$isPaused remain=${remainingSeconds()}")
    startForegroundInternal(notif)
  }

  private fun remainingSeconds(): Long {
    return if (isPaused) {
      remainingWhenPaused
    } else {
      val now = System.currentTimeMillis()
      ((targetTimeMs - now) / 1000).coerceAtLeast(0)
    }
  }

  private fun tick() {
    handler.removeCallbacksAndMessages(null)
    if (isPaused) return
    val remain = remainingSeconds()
    if (remain <= 0) {
      android.util.Log.d(logTag, "FGTimer tick finished id=$timerId -> start AlarmRingingService with overlayBg=$overlayBgColor overlayText=$overlayTextColor")
      finish()
      return
    }
    updateNotification()
    handler.postDelayed({ tick() }, 1000L)
  }

  private fun finish() {
    val id = timerId ?: "unknown"
    val styleMap = mapOf<String, Any?>(
      "timerChannelId" to notifStyle.timerChannelId,
      "alertChannelId" to notifStyle.alertChannelId,
      "smallIconName" to notifStyle.smallIconName,
      "accentColor" to notifStyle.accentColor,
      "useChronometer" to notifStyle.useChronometer,
      "overlayBackgroundColor" to overlayBgColor,
      "overlayTextColor" to overlayTextColor,
      "overlayButtonBackgroundColor" to overlayBtnBgColor,
      "overlayButtonTextColor" to overlayBtnTextColor,
      "snoozeMinutes" to snoozeMinutes
    )
    AlarmRingingService.start(this, id, label, styleMap)
    stopForeground(true)
    stopSelf()
  }

  override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    super.onDestroy()
  }

  companion object {
    const val NOTIF_ID = 41501

    const val ACTION_START = "ai.asleep.reactnative.alarm.action.START"
    const val ACTION_PAUSE = "ai.asleep.reactnative.alarm.action.PAUSE"
    const val ACTION_RESUME = "ai.asleep.reactnative.alarm.action.RESUME"
    const val ACTION_STOP = "ai.asleep.reactnative.alarm.action.STOP"

    const val EXTRA_ID = "extra_id"
    const val EXTRA_SECONDS = "extra_seconds"
    const val EXTRA_LABEL = "extra_label"
    const val EXTRA_STYLE_TIMER_CHANNEL_ID = "style_timer_channel_id"
    const val EXTRA_STYLE_ALERT_CHANNEL_ID = "style_alert_channel_id"
    const val EXTRA_STYLE_SMALL_ICON = "style_small_icon"
    const val EXTRA_STYLE_ACCENT_COLOR = "style_accent_color"
    const val EXTRA_STYLE_USE_CHRONOMETER = "style_use_chronometer"
    const val EXTRA_STYLE_OVERLAY_BG = "style_overlay_bg"
    const val EXTRA_STYLE_OVERLAY_TEXT = "style_overlay_text"
    const val EXTRA_STYLE_OVERLAY_BTN_BG = "style_overlay_btn_bg"
    const val EXTRA_STYLE_OVERLAY_BTN_TEXT = "style_overlay_btn_text"
    const val EXTRA_STYLE_SNOOZE_MIN = "style_snooze_min"

    fun start(context: Context, id: String, label: String?, seconds: Long, style: Map<String, Any?>) {
      val intent = Intent(context, ForegroundTimerService::class.java)
        .setAction(ACTION_START)
        .putExtra(EXTRA_ID, id)
        .putExtra(EXTRA_LABEL, label)
        .putExtra(EXTRA_SECONDS, seconds)
      (style["timerChannelId"] as? String)?.let { intent.putExtra(EXTRA_STYLE_TIMER_CHANNEL_ID, it) }
      (style["alertChannelId"] as? String)?.let { intent.putExtra(EXTRA_STYLE_ALERT_CHANNEL_ID, it) }
      (style["smallIconName"] as? String)?.let { intent.putExtra(EXTRA_STYLE_SMALL_ICON, it) }
      (style["accentColor"] as? Int)?.let { intent.putExtra(EXTRA_STYLE_ACCENT_COLOR, it) }
      (style["useChronometer"] as? Boolean)?.let { intent.putExtra(EXTRA_STYLE_USE_CHRONOMETER, it) }
      (style["overlayBackgroundColor"] as? Int)?.let { intent.putExtra(EXTRA_STYLE_OVERLAY_BG, it) }
      (style["overlayTextColor"] as? Int)?.let { intent.putExtra(EXTRA_STYLE_OVERLAY_TEXT, it) }
      (style["overlayButtonBackgroundColor"] as? Int)?.let { intent.putExtra(EXTRA_STYLE_OVERLAY_BTN_BG, it) }
      (style["overlayButtonTextColor"] as? Int)?.let { intent.putExtra(EXTRA_STYLE_OVERLAY_BTN_TEXT, it) }
      (style["snoozeMinutes"] as? Int)?.let { intent.putExtra(EXTRA_STYLE_SNOOZE_MIN, it) }
      // Prefer startService (app usually foreground). Fall back to FGS if needed.
      try {
        context.startService(intent)
      } catch (e: IllegalStateException) {
        if (Build.VERSION.SDK_INT >= 26) {
          context.startForegroundService(intent)
        } else {
          throw e
        }
      }
    }
  }
}


