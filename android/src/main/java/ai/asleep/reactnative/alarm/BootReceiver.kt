package ai.asleep.reactnative.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.format.DateTimeParseException

class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
      val storage = AlarmStorage(context)
      val list = storage.loadAll()
      val now = System.currentTimeMillis()
      val alarmManager = context.getSystemService(AlarmManager::class.java)
      list.forEach { a ->
        val whenMs = parseEpoch(a.dateISO) ?: return@forEach
        if (whenMs > now) {
          val fireIntent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_ID, a.id)
            .putExtra(AlarmReceiver.EXTRA_LABEL, a.label)
          val pi = PendingIntent.getBroadcast(
            context,
            a.id.hashCode(),
            fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
          )
          try {
            val info = AlarmManager.AlarmClockInfo(whenMs, pi)
            alarmManager.setAlarmClock(info, pi)
          } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, whenMs, pi)
          }
        }
      }
    }
  }

  private fun parseEpoch(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
      Instant.parse(iso).toEpochMilli()
    } catch (_: DateTimeParseException) {
      null
    }
  }
}


