package ai.asleep.reactnative.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val id = intent.getStringExtra(EXTRA_ID) ?: "unknown"
    val label = intent.getStringExtra(EXTRA_LABEL)
    when (intent.action) {
      ACTION_FIRE -> {
        // Start foreground ringing service which posts a full-screen alert and loops sound
        // Prefer style persisted in the broadcast extras; fallback to global config.
        val styleMap = mutableMapOf<String, Any?>()
        val hasStyleExtras =
          intent.hasExtra(AlarmRingingService.EXTRA_STYLE_TIMER_CHANNEL_ID) ||
          intent.hasExtra(AlarmRingingService.EXTRA_STYLE_ALERT_CHANNEL_ID) ||
          intent.hasExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_BG) ||
          intent.hasExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_TEXT) ||
          intent.hasExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_BTN_BG) ||
          intent.hasExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_BTN_TEXT)
        if (hasStyleExtras) {
          intent.getStringExtra(AlarmRingingService.EXTRA_STYLE_TIMER_CHANNEL_ID)?.let {
            styleMap["timerChannelId"] = it
          }
          intent.getStringExtra(AlarmRingingService.EXTRA_STYLE_ALERT_CHANNEL_ID)?.let {
            styleMap["alertChannelId"] = it
          }
          intent.getStringExtra(AlarmRingingService.EXTRA_STYLE_SMALL_ICON)?.let {
            styleMap["smallIconName"] = it
          }
          if (intent.hasExtra(AlarmRingingService.EXTRA_STYLE_ACCENT_COLOR)) {
            styleMap["accentColor"] = intent.getIntExtra(AlarmRingingService.EXTRA_STYLE_ACCENT_COLOR, 0)
          }
          styleMap["useChronometer"] =
            intent.getBooleanExtra(AlarmRingingService.EXTRA_STYLE_USE_CHRONOMETER, true)
          styleMap["showOverlayWhenUnlocked"] =
            intent.getBooleanExtra(AlarmRingingService.EXTRA_STYLE_SHOW_OVERLAY, true)
          if (intent.hasExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_BG)) {
            styleMap["overlayBackgroundColor"] =
              intent.getIntExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_BG, 0)
          }
          if (intent.hasExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_TEXT)) {
            styleMap["overlayTextColor"] =
              intent.getIntExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_TEXT, 0)
          }
          if (intent.hasExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_BTN_BG)) {
            styleMap["overlayButtonBackgroundColor"] =
              intent.getIntExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_BTN_BG, 0)
          }
          if (intent.hasExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_BTN_TEXT)) {
            styleMap["overlayButtonTextColor"] =
              intent.getIntExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_BTN_TEXT, 0)
          }
        } else {
          try {
            styleMap.putAll(styleToMap(ConfigHolder.globalStyle))
          } catch (_: Throwable) {}
        }
        Log.d("RNAlarm", "AlarmReceiver ACTION_FIRE id=$id label=$label style=$styleMap (fromExtras=$hasStyleExtras)")
        AlarmRingingService.start(context, id, label, styleMap)
        // Storage mark enabled=false
        val storage = AlarmStorage(context)
        val items = storage.loadAll().map {
          if (it.id == id) it.copy(enabled = false) else it
        }
        storage.saveAll(items)
      }
      ACTION_DISMISS -> {
        // Dismiss alert notification
        val mgr = context.getSystemService(android.app.NotificationManager::class.java)
        mgr.cancel(id.hashCode())
      }
    }
  }

  companion object {
    const val ACTION_FIRE = "ai.asleep.reactnative.alarm.ALARM_FIRE"
    const val ACTION_DISMISS = "ai.asleep.reactnative.alarm.ALARM_DISMISS"

    const val EXTRA_ID = "extra_id"
    const val EXTRA_LABEL = "extra_label"
  }
}


