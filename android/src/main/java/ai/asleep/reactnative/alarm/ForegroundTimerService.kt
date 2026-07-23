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
  private var isSnoozed: Boolean = false
  private var snoozeUntilISO: String? = null
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
        isSnoozed = intent.getBooleanExtra(EXTRA_IS_SNOOZED, false)
        snoozeUntilISO = intent.getStringExtra(EXTRA_SNOOZE_UNTIL_ISO)
        targetTimeMs = System.currentTimeMillis() + seconds * 1000
        startForegroundInternal(buildNotification())
        if (isSnoozed) {
          ReactNativeAlarmModule.sendAlarmSnoozedEvent(id, label, snoozeUntilISO ?: "")
          ReactNativeAlarmModule.sendAlarmStateChangedEvent(
            id = id,
            label = label,
            isRinging = false,
            isSnoozed = true,
            remainingSeconds = seconds,
            snoozeUntilISO = snoozeUntilISO
          )
        } else {
          ReactNativeAlarmModule.sendAlarmStartedEvent(id, label, seconds)
          ReactNativeAlarmModule.sendAlarmStateChangedEvent(
            id = id,
            label = label,
            isRinging = false,
            isSnoozed = false,
            remainingSeconds = seconds
          )
        }
        tick()
      }
      ACTION_PAUSE -> {
        if (!isPaused) {
          val now = System.currentTimeMillis()
          remainingWhenPaused = ((targetTimeMs - now) / 1000).coerceAtLeast(0)
          isPaused = true
          updateNotification()
          // Discrete transition: emit once on pause (previously carried by
          // updateNotification's per-second event, now edge-triggered).
          timerId?.let { id ->
            ReactNativeAlarmModule.sendAlarmStateChangedEvent(
              id = id,
              label = label,
              isRinging = false,
              isSnoozed = isSnoozed,
              remainingSeconds = remainingWhenPaused,
              snoozeUntilISO = if (isSnoozed) snoozeUntilISO else null,
              isPaused = true
            )
          }
        }
      }
      ACTION_RESUME -> {
        if (isPaused) {
          val now = System.currentTimeMillis()
          targetTimeMs = now + remainingWhenPaused * 1000
          isPaused = false
          updateNotification()
          // Discrete transition: emit once on resume.
          timerId?.let { id ->
            ReactNativeAlarmModule.sendAlarmStateChangedEvent(
              id = id,
              label = label,
              isRinging = false,
              isSnoozed = isSnoozed,
              remainingSeconds = remainingSeconds(),
              snoozeUntilISO = if (isSnoozed) snoozeUntilISO else null
            )
          }
          tick()
        }
      }
      ACTION_STOP -> {
        stopSelf()
      }
    }
    return START_STICKY
  }

  private fun startForegroundInternal(notification: Notification) {
    if (Build.VERSION.SDK_INT >= 29) {
      startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
    } else {
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
    // Notification-only refresh. Does NOT emit onAlarmStateChanged: tick() calls
    // this every second, and per-second bridge events were the analytics flood
    // removed in v0.2.0. Discrete transitions (pause/resume) emit explicitly in
    // onStartCommand; start/snooze/ringing/finish emit at their own sites.
    startForegroundInternal(buildNotification())
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
    ReactNativeAlarmModule.sendAlarmStartedEvent(id, label, 0)
    ReactNativeAlarmModule.sendAlarmStateChangedEvent(
      id = id,
      label = label,
      isRinging = true,
      isSnoozed = false,
      remainingSeconds = 0
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
    const val EXTRA_IS_SNOOZED = "extra_is_snoozed"
    const val EXTRA_SNOOZE_UNTIL_ISO = "extra_snooze_until_iso"

    fun start(context: Context, id: String, label: String?, seconds: Long, style: Map<String, Any?>, isSnoozed: Boolean = false, snoozeUntilISO: String? = null) {
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
      intent.putExtra(EXTRA_IS_SNOOZED, isSnoozed)
      snoozeUntilISO?.let { intent.putExtra(EXTRA_SNOOZE_UNTIL_ISO, it) }
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


