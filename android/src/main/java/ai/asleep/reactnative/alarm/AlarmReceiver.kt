package ai.asleep.reactnative.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val id = intent.getStringExtra(EXTRA_ID) ?: "unknown"
    val label = intent.getStringExtra(EXTRA_LABEL)
    when (intent.action) {
      ACTION_FIRE -> {
        NotificationHelper.showAlarmAlert(context, id, label)
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


