package ai.asleep.reactnative.alarm

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import android.app.KeyguardManager
import android.os.PowerManager
import android.graphics.Color
import android.util.Log

class AlarmRingingService : Service() {
  private var mediaPlayer: MediaPlayer? = null
  private var alarmId: String = "unknown"
  private var label: String? = null
  private var isRinging: Boolean = false
  private var notifStyle: NotificationHelper.Style = NotificationHelper.Style()
  private var showOverlayWhenUnlocked: Boolean = true
  private var overlayBgColor: Int? = null
  private var overlayTextColor: Int? = null
  private var overlayBtnBgColor: Int? = null
  private var overlayBtnTextColor: Int? = null
  private var snoozeMinutes: Int = 5

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_RING -> {
        alarmId = intent.getStringExtra(EXTRA_ID) ?: "unknown"
        label = intent.getStringExtra(EXTRA_LABEL)
        // Read optional style extras
        notifStyle = NotificationHelper.Style(
          timerChannelId = intent.getStringExtra(EXTRA_STYLE_TIMER_CHANNEL_ID) ?: NotificationHelper.CHANNEL_TIMERS_HIGH,
          alertChannelId = intent.getStringExtra(EXTRA_STYLE_ALERT_CHANNEL_ID) ?: NotificationHelper.CHANNEL_ALERTS,
          smallIconName = intent.getStringExtra(EXTRA_STYLE_SMALL_ICON),
          accentColor = if (intent.hasExtra(EXTRA_STYLE_ACCENT_COLOR)) intent.getIntExtra(EXTRA_STYLE_ACCENT_COLOR, 0) else null,
          useChronometer = intent.getBooleanExtra(EXTRA_STYLE_USE_CHRONOMETER, true)
        )
        showOverlayWhenUnlocked = intent.getBooleanExtra(EXTRA_STYLE_SHOW_OVERLAY, true)
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
        // Read snooze minutes if provided; default to 5
        snoozeMinutes = if (intent.hasExtra(EXTRA_STYLE_SNOOZE_MIN)) {
          intent.getIntExtra(EXTRA_STYLE_SNOOZE_MIN, 5)
        } else 5
        Log.d("RNAlarm", "RingingService ACTION_RING id=$alarmId label=$label showOverlayWhenUnlocked=$showOverlayWhenUnlocked style=$notifStyle overlayBg=$overlayBgColor overlayText=$overlayTextColor btnBg=$overlayBtnBgColor btnText=$overlayBtnTextColor snoozeMin=$snoozeMinutes")

        val km = getSystemService(KeyguardManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        val isLocked = (km?.isKeyguardLocked == true) || (km?.isDeviceLocked == true)
        val isInteractive = pm?.isInteractive == true
        val shouldFullScreen = isLocked || !isInteractive
        Log.d("RNAlarm", "RingingService deviceState isLocked=$isLocked isInteractive=$isInteractive -> shouldFullScreen=$shouldFullScreen")

        val notif = buildForegroundNotification(shouldFullScreen)
        if (Build.VERSION.SDK_INT >= 29) {
          startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
          startForeground(NOTIF_ID, notif)
        }
        // Cancel any pre-alarm countdown notification
        try {
          NotificationManagerCompat.from(this).cancel((alarmId + ":info").hashCode())
        } catch (_: Throwable) {}
        if (!isRinging) {
          startTone()
        }
        if (shouldFullScreen) {
          // Lock screen or not interactive: show full-screen activity only
          try {
            try { AlarmOverlayService.hide(this) } catch (_: Throwable) {}
            val fs = Intent(this, AlarmActivity::class.java)
              .putExtra(AlarmReceiver.EXTRA_ID, alarmId)
              .putExtra(AlarmReceiver.EXTRA_LABEL, label)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // pass overlay colors to activity for theming
            if (intent.hasExtra(EXTRA_STYLE_OVERLAY_BG)) {
              fs.putExtra(AlarmActivity.EXTRA_OVERLAY_BG, intent.getIntExtra(EXTRA_STYLE_OVERLAY_BG, 0))
            }
            if (intent.hasExtra(EXTRA_STYLE_OVERLAY_TEXT)) {
              fs.putExtra(AlarmActivity.EXTRA_OVERLAY_TEXT, intent.getIntExtra(EXTRA_STYLE_OVERLAY_TEXT, 0))
            }
            if (intent.hasExtra(EXTRA_STYLE_OVERLAY_BTN_BG)) {
              fs.putExtra(AlarmActivity.EXTRA_OVERLAY_BTN_BG, intent.getIntExtra(EXTRA_STYLE_OVERLAY_BTN_BG, 0))
            }
            if (intent.hasExtra(EXTRA_STYLE_OVERLAY_BTN_TEXT)) {
              fs.putExtra(AlarmActivity.EXTRA_OVERLAY_BTN_TEXT, intent.getIntExtra(EXTRA_STYLE_OVERLAY_BTN_TEXT, 0))
            }
            fs.putExtra(AlarmActivity.EXTRA_SNOOZE_MIN, snoozeMinutes)
            Log.d("RNAlarm", "RingingService startActivity AlarmActivity with bg=$overlayBgColor text=$overlayTextColor")
            startActivity(fs)
          } catch (_: Throwable) {}
        } else {
          // Device is unlocked and interactive: prefer overlay; avoid launching full-screen/app
          try {
            if (showOverlayWhenUnlocked && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))) {
              val styleMap = mutableMapOf<String, Any?>()
              notifStyle.accentColor?.let { styleMap["accentColor"] = it }
              styleMap["timerChannelId"] = notifStyle.timerChannelId
              styleMap["alertChannelId"] = notifStyle.alertChannelId
              styleMap["useChronometer"] = notifStyle.useChronometer
              // Overlay colors if provided
              if (intent.hasExtra(EXTRA_STYLE_OVERLAY_BG)) {
                styleMap["overlayBackgroundColor"] = intent.getIntExtra(EXTRA_STYLE_OVERLAY_BG, 0)
              }
              if (intent.hasExtra(EXTRA_STYLE_OVERLAY_TEXT)) {
                styleMap["overlayTextColor"] = intent.getIntExtra(EXTRA_STYLE_OVERLAY_TEXT, 0)
              }
              if (intent.hasExtra(EXTRA_STYLE_OVERLAY_BTN_BG)) {
                styleMap["overlayButtonBackgroundColor"] = intent.getIntExtra(EXTRA_STYLE_OVERLAY_BTN_BG, 0)
              }
              if (intent.hasExtra(EXTRA_STYLE_OVERLAY_BTN_TEXT)) {
                styleMap["overlayButtonTextColor"] = intent.getIntExtra(EXTRA_STYLE_OVERLAY_BTN_TEXT, 0)
              }
              styleMap["snoozeMinutes"] = snoozeMinutes
              Log.d("RNAlarm", "RingingService show overlay with styleMap bg=${styleMap["overlayBackgroundColor"]} text=${styleMap["overlayTextColor"]} btnBg=${styleMap["overlayButtonBackgroundColor"]} btnText=${styleMap["overlayButtonTextColor"]}")
              AlarmOverlayService.show(this, alarmId, label, styleMap)
            } else {
              // Fallback to activity if no overlay permission
              val fs = Intent(this, AlarmActivity::class.java)
                .putExtra(AlarmReceiver.EXTRA_ID, alarmId)
                .putExtra(AlarmReceiver.EXTRA_LABEL, label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
              if (intent.hasExtra(EXTRA_STYLE_OVERLAY_BG)) {
                fs.putExtra(AlarmActivity.EXTRA_OVERLAY_BG, intent.getIntExtra(EXTRA_STYLE_OVERLAY_BG, 0))
              }
              if (intent.hasExtra(EXTRA_STYLE_OVERLAY_TEXT)) {
                fs.putExtra(AlarmActivity.EXTRA_OVERLAY_TEXT, intent.getIntExtra(EXTRA_STYLE_OVERLAY_TEXT, 0))
              }
              if (intent.hasExtra(EXTRA_STYLE_OVERLAY_BTN_BG)) {
                fs.putExtra(AlarmActivity.EXTRA_OVERLAY_BTN_BG, intent.getIntExtra(EXTRA_STYLE_OVERLAY_BTN_BG, 0))
              }
              if (intent.hasExtra(EXTRA_STYLE_OVERLAY_BTN_TEXT)) {
                fs.putExtra(AlarmActivity.EXTRA_OVERLAY_BTN_TEXT, intent.getIntExtra(EXTRA_STYLE_OVERLAY_BTN_TEXT, 0))
              }
              fs.putExtra(AlarmActivity.EXTRA_SNOOZE_MIN, snoozeMinutes)
              Log.d("RNAlarm", "RingingService fallback AlarmActivity with bg=$overlayBgColor text=$overlayTextColor overlayPerm=${Settings.canDrawOverlays(this)} showOverlayWhenUnlocked=$showOverlayWhenUnlocked")
              startActivity(fs)
            }
          } catch (_: Throwable) {}
        }
      }
      ACTION_STOP -> {
        stopTone()
        try { AlarmOverlayService.hide(this) } catch (_: Throwable) {}
        Log.d("RNAlarm", "RingingService ACTION_STOP id=$alarmId")
        stopSelf()
      }
    }
    return START_NOT_STICKY
  }

  private fun buildForegroundNotification(fullScreen: Boolean): Notification {
    Log.d("RNAlarm", "RingingService buildForegroundNotification fullScreen=$fullScreen overlayBg=$overlayBgColor overlayText=$overlayTextColor")
    return NotificationHelper.buildAlarmAlertNotification(
      this,
      alarmId,
      label,
      fullScreen,
      notifStyle,
      overlayBgColor,
      overlayTextColor
    )
  }

  private fun startTone() {
    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    mediaPlayer = MediaPlayer().apply {
      setDataSource(this@AlarmRingingService, uri)
      setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
      )
      isLooping = true
      prepare()
      start()
    }
    isRinging = true
    Log.d("RNAlarm", "RingingService startTone id=$alarmId")
  }

  private fun stopTone() {
    mediaPlayer?.let {
      try { it.stop() } catch (_: Throwable) {}
      try { it.release() } catch (_: Throwable) {}
    }
    mediaPlayer = null
    isRinging = false
    Log.d("RNAlarm", "RingingService stopTone id=$alarmId")
  }

  override fun onDestroy() {
    stopTone()
    super.onDestroy()
  }

  companion object {
    const val NOTIF_ID = 41502
    const val ACTION_RING = "ai.asleep.reactnative.alarm.action.RING"
    const val ACTION_STOP = "ai.asleep.reactnative.alarm.action.STOP_RING"
    const val EXTRA_ID = "extra_id"
    const val EXTRA_LABEL = "extra_label"
    const val EXTRA_STYLE_TIMER_CHANNEL_ID = "style_timer_channel_id"
    const val EXTRA_STYLE_ALERT_CHANNEL_ID = "style_alert_channel_id"
    const val EXTRA_STYLE_SMALL_ICON = "style_small_icon"
    const val EXTRA_STYLE_ACCENT_COLOR = "style_accent_color"
    const val EXTRA_STYLE_USE_CHRONOMETER = "style_use_chronometer"
    const val EXTRA_STYLE_SHOW_OVERLAY = "style_show_overlay"
    const val EXTRA_STYLE_OVERLAY_BG = "style_overlay_bg"
    const val EXTRA_STYLE_OVERLAY_TEXT = "style_overlay_text"
    const val EXTRA_STYLE_OVERLAY_BTN_BG = "style_overlay_btn_bg"
    const val EXTRA_STYLE_OVERLAY_BTN_TEXT = "style_overlay_btn_text"
    const val EXTRA_STYLE_SNOOZE_MIN = "style_snooze_min"

    fun start(context: Context, id: String, label: String?, style: Map<String, Any?>? = null) {
      val i = Intent(context, AlarmRingingService::class.java)
        .setAction(ACTION_RING)
        .putExtra(EXTRA_ID, id)
        .putExtra(EXTRA_LABEL, label)
      if (style != null) {
        Log.d("RNAlarm", "RingingService.start id=$id label=$label style=$style")
        (style["timerChannelId"] as? String)?.let { i.putExtra(EXTRA_STYLE_TIMER_CHANNEL_ID, it) }
        (style["alertChannelId"] as? String)?.let { i.putExtra(EXTRA_STYLE_ALERT_CHANNEL_ID, it) }
        (style["smallIconName"] as? String)?.let { i.putExtra(EXTRA_STYLE_SMALL_ICON, it) }
        (style["accentColor"] as? Int)?.let { i.putExtra(EXTRA_STYLE_ACCENT_COLOR, it) }
        (style["useChronometer"] as? Boolean)?.let { i.putExtra(EXTRA_STYLE_USE_CHRONOMETER, it) }
        (style["showOverlayWhenUnlocked"] as? Boolean)?.let { i.putExtra(EXTRA_STYLE_SHOW_OVERLAY, it) }
        (style["overlayBackgroundColor"] as? Int)?.let { i.putExtra(EXTRA_STYLE_OVERLAY_BG, it) }
        (style["overlayTextColor"] as? Int)?.let { i.putExtra(EXTRA_STYLE_OVERLAY_TEXT, it) }
        (style["overlayButtonBackgroundColor"] as? Int)?.let { i.putExtra(EXTRA_STYLE_OVERLAY_BTN_BG, it) }
        (style["overlayButtonTextColor"] as? Int)?.let { i.putExtra(EXTRA_STYLE_OVERLAY_BTN_TEXT, it) }
        (style["snoozeMinutes"] as? Int)?.let { i.putExtra(EXTRA_STYLE_SNOOZE_MIN, it) }
      }
      if (Build.VERSION.SDK_INT >= 26) {
        context.startForegroundService(i)
      } else {
        context.startService(i)
      }
    }

    fun stop(context: Context) {
      val i = Intent(context, AlarmRingingService::class.java)
        .setAction(ACTION_STOP)
      context.startService(i)
    }

    fun snooze(context: Context, id: String, label: String?, minutes: Int, style: Map<String, Any?>? = null) {
      try {
        // Hide any overlay and stop current ringing
        try { AlarmOverlayService.hide(context) } catch (_: Throwable) {}
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val fireIntent = Intent(context, AlarmReceiver::class.java)
          .setAction(AlarmReceiver.ACTION_FIRE)
          .putExtra(AlarmReceiver.EXTRA_ID, id)
          .putExtra(AlarmReceiver.EXTRA_LABEL, label)
        // Persist style for next ring
        if (style != null) {
          (style["timerChannelId"] as? String)?.let { fireIntent.putExtra(EXTRA_STYLE_TIMER_CHANNEL_ID, it) }
          (style["alertChannelId"] as? String)?.let { fireIntent.putExtra(EXTRA_STYLE_ALERT_CHANNEL_ID, it) }
          (style["smallIconName"] as? String)?.let { fireIntent.putExtra(EXTRA_STYLE_SMALL_ICON, it) }
          (style["accentColor"] as? Int)?.let { fireIntent.putExtra(EXTRA_STYLE_ACCENT_COLOR, it) }
          (style["useChronometer"] as? Boolean)?.let { fireIntent.putExtra(EXTRA_STYLE_USE_CHRONOMETER, it) }
          (style["showOverlayWhenUnlocked"] as? Boolean)?.let { fireIntent.putExtra(EXTRA_STYLE_SHOW_OVERLAY, it) }
          (style["overlayBackgroundColor"] as? Int)?.let { fireIntent.putExtra(EXTRA_STYLE_OVERLAY_BG, it) }
          (style["overlayTextColor"] as? Int)?.let { fireIntent.putExtra(EXTRA_STYLE_OVERLAY_TEXT, it) }
          (style["overlayButtonBackgroundColor"] as? Int)?.let { fireIntent.putExtra(EXTRA_STYLE_OVERLAY_BTN_BG, it) }
          (style["overlayButtonTextColor"] as? Int)?.let { fireIntent.putExtra(EXTRA_STYLE_OVERLAY_BTN_TEXT, it) }
          (style["snoozeMinutes"] as? Int)?.let { fireIntent.putExtra(EXTRA_STYLE_SNOOZE_MIN, it) }
        }
        val pi = android.app.PendingIntent.getBroadcast(
          context,
          id.hashCode(),
          fireIntent,
          android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(android.app.AlarmManager::class.java)
        try {
          val info = android.app.AlarmManager.AlarmClockInfo(triggerAt, pi)
          am.setAlarmClock(info, pi)
        } catch (_: SecurityException) {
          am.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
        // Show info notification for snoozed time
        NotificationHelper.showCountdownInfoNotification(
          context, id, label, triggerAt, NotificationHelper.Style()
        )
      } finally {
        // Stop current ringing service
        stop(context)
      }
    }
  }
}


