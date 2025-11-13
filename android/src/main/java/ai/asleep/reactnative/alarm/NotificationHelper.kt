package ai.asleep.reactnative.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.TaskStackBuilder
import java.text.DateFormat
import java.util.Date

internal object NotificationHelper {
  const val CHANNEL_TIMERS = "react_native_alarm_timers"
  const val CHANNEL_ALERTS = "react_native_alarm_alerts"

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = context.getSystemService(NotificationManager::class.java)

    // Timers channel (ongoing)
    if (nm.getNotificationChannel(CHANNEL_TIMERS) == null) {
      val ch = NotificationChannel(
        CHANNEL_TIMERS,
        "Alarms & timers (ongoing)",
        NotificationManager.IMPORTANCE_LOW
      )
      ch.description = "Ongoing countdown timers"
      nm.createNotificationChannel(ch)
    }

    // Alerts channel (fires at the end)
    if (nm.getNotificationChannel(CHANNEL_ALERTS) == null) {
      val ch = NotificationChannel(
        CHANNEL_ALERTS,
        "Alarm alerts",
        NotificationManager.IMPORTANCE_HIGH
      )
      ch.description = "Alarm alerts when time is up"
      val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
      val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
      ch.setSound(alarmUri, attrs)
      ch.enableVibration(true)
      nm.createNotificationChannel(ch)
    }
  }

  fun buildTimerNotification(
    context: Context,
    id: String,
    label: String?,
    remainingSeconds: Long,
    isPaused: Boolean
  ): Notification {
    ensureChannels(context)
    val title = if (!label.isNullOrBlank()) label else "Timer"
    val content = if (isPaused) {
      "Paused: ${formatDuration(remainingSeconds)} remaining"
    } else {
      "Time remaining: ${formatDuration(remainingSeconds)}"
    }
    val finishAtMs = System.currentTimeMillis() + remainingSeconds * 1000

    val pauseIntent = Intent(context, ForegroundTimerService::class.java)
      .setAction(ForegroundTimerService.ACTION_PAUSE)
      .putExtra(ForegroundTimerService.EXTRA_ID, id)
    val resumeIntent = Intent(context, ForegroundTimerService::class.java)
      .setAction(ForegroundTimerService.ACTION_RESUME)
      .putExtra(ForegroundTimerService.EXTRA_ID, id)
    val stopIntent = Intent(context, ForegroundTimerService::class.java)
      .setAction(ForegroundTimerService.ACTION_STOP)
      .putExtra(ForegroundTimerService.EXTRA_ID, id)

    val pausePi = PendingIntent.getService(
      context,
      (id + ":pause").hashCode(),
      pauseIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val resumePi = PendingIntent.getService(
      context,
      (id + ":resume").hashCode(),
      resumeIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val stopPi = PendingIntent.getService(
      context,
      (id + ":stop").hashCode(),
      stopIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, CHANNEL_TIMERS)
      .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
      .setContentTitle(title)
      .setContentText(content)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      // Live countdown in the notification (system-updated chronometer)
      .setUsesChronometer(!isPaused)
      .setChronometerCountDown(true)
      .setWhen(finishAtMs)

    if (isPaused) {
      builder.addAction(0, "Resume", resumePi)
    } else {
      builder.addAction(0, "Pause", pausePi)
    }
    builder.addAction(0, "Stop", stopPi)
    return builder.build()
  }

  fun showAlarmAlert(context: Context, id: String, label: String?) {
    ensureChannels(context)
    val title = if (!label.isNullOrBlank()) label else "Alarm"

    // Full screen intent to bring AlarmActivity to foreground
    val fsIntent = Intent(context, AlarmActivity::class.java).apply {
      putExtra(AlarmReceiver.EXTRA_ID, id)
      putExtra(AlarmReceiver.EXTRA_LABEL, label)
    }
    val stack = TaskStackBuilder.create(context).apply {
      addNextIntentWithParentStack(fsIntent)
    }
    val fsPi = stack.getPendingIntent(
      (id + ":fs").hashCode(),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // App may not have an activity we can target; show dismiss-only notification.
    val dismissIntent = Intent(context, AlarmReceiver::class.java)
      .setAction(AlarmReceiver.ACTION_DISMISS)
      .putExtra(AlarmReceiver.EXTRA_ID, id)
    val dismissPi = PendingIntent.getBroadcast(
      context,
      (id + ":dismiss").hashCode(),
      dismissIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
      .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
      .setContentTitle(title)
      .setContentText("Alarm time reached")
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setAutoCancel(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setContentIntent(fsPi)
      .setFullScreenIntent(fsPi, true)
      .addAction(0, "Dismiss", dismissPi)

    NotificationManagerCompat.from(context).notify(id.hashCode(), builder.build())
  }

  fun showCountdownInfoNotification(context: Context, id: String, label: String?, targetAtMs: Long) {
    ensureChannels(context)
    val title = if (!label.isNullOrBlank()) label else "Alarm"
    val timeText = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(targetAtMs))
    val content = "Rings at $timeText"

    // Tapping opens AlarmActivity pre-armed to Stop if already ringing
    val fsIntent = Intent(context, AlarmActivity::class.java).apply {
      putExtra(AlarmReceiver.EXTRA_ID, id)
      putExtra(AlarmReceiver.EXTRA_LABEL, label)
    }
    val stack = TaskStackBuilder.create(context).apply {
      addNextIntentWithParentStack(fsIntent)
    }
    val pi = stack.getPendingIntent(
      (id + ":info").hashCode(),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, CHANNEL_TIMERS)
      .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
      .setContentTitle(title)
      .setContentText(content)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      // System chronometer counts down to the alarm time
      .setUsesChronometer(true)
      .setChronometerCountDown(true)
      .setWhen(targetAtMs)
      .setContentIntent(pi)

    NotificationManagerCompat.from(context).notify((id + ":info").hashCode(), builder.build())
  }

  private fun formatDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
  }
}


