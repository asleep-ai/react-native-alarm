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
import java.text.DateFormat
import java.util.Date
import android.util.Log

internal object NotificationHelper {
  const val CHANNEL_TIMERS = "react_native_alarm_timers" // legacy low-importance
  const val CHANNEL_TIMERS_HIGH = "react_native_alarm_timers_high"
  const val CHANNEL_ALERTS = "react_native_alarm_alerts"

  data class Style(
    val timerChannelId: String = CHANNEL_TIMERS_HIGH,
    val alertChannelId: String = CHANNEL_ALERTS,
    val smallIconName: String? = null,
    val accentColor: Int? = null,
    val useChronometer: Boolean = true
  )

  fun ensureChannels(context: Context, style: Style) {
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
      ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      nm.createNotificationChannel(ch)
    }

    // Timers high-importance (for keyguard visibility)
    if (nm.getNotificationChannel(style.timerChannelId) == null) {
      val ch = NotificationChannel(
        style.timerChannelId,
        "Alarms & timers (keyguard visible)",
        NotificationManager.IMPORTANCE_HIGH
      )
      ch.description = "Countdown timers visible on the lock screen"
      ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      nm.createNotificationChannel(ch)
    }

    // Alerts channel (fires at the end)
    if (nm.getNotificationChannel(style.alertChannelId) == null) {
      val ch = NotificationChannel(
        style.alertChannelId,
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
      ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      nm.createNotificationChannel(ch)
    }
  }

  fun buildTimerNotification(
    context: Context,
    id: String,
    label: String?,
    remainingSeconds: Long,
    isPaused: Boolean,
    style: Style
  ): Notification {
    ensureChannels(context, style)
    val title = if (!label.isNullOrBlank()) label else "Timer"
    val content = if (isPaused) {
      "Paused: ${formatDuration(remainingSeconds)} remaining"
    } else {
      "Time remaining: ${formatDuration(remainingSeconds)}"
    }
    val finishAtMs = System.currentTimeMillis() + remainingSeconds * 1000
    val smallIcon = resolveSmallIcon(context, style.smallIconName)

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

    val builder = NotificationCompat.Builder(context, style.timerChannelId)
      .setSmallIcon(smallIcon)
      .setContentTitle(title)
      .setContentText(content)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      // Live countdown in the notification (system-updated chronometer)
      .setUsesChronometer(style.useChronometer && !isPaused)
      .setChronometerCountDown(true)
      .setWhen(finishAtMs)

    style.accentColor?.let {
      builder.setColor(it)
      builder.setColorized(true)
    }

    if (isPaused) {
      builder.addAction(0, "Resume", resumePi)
    } else {
      builder.addAction(0, "Pause", pausePi)
    }
    builder.addAction(0, "Stop", stopPi)
    return builder.build()
  }

  fun buildAlarmAlertNotification(
    context: Context,
    id: String,
    label: String?,
    fullScreen: Boolean,
    style: Style,
    overlayBgColor: Int? = null,
    overlayTextColor: Int? = null
  ): Notification {
    ensureChannels(context, style)
    val title = if (!label.isNullOrBlank()) label else "Alarm"
    val smallIcon = resolveSmallIcon(context, style.smallIconName)

    // Full screen intent to bring AlarmActivity to foreground
    val fsIntent = Intent(context, AlarmActivity::class.java).apply {
      putExtra(AlarmReceiver.EXTRA_ID, id)
      putExtra(AlarmReceiver.EXTRA_LABEL, label)
      // Pass overlay theming to activity if available
      overlayBgColor?.let { putExtra(AlarmActivity.EXTRA_OVERLAY_BG, it) }
      overlayTextColor?.let { putExtra(AlarmActivity.EXTRA_OVERLAY_TEXT, it) }
      // AlarmActivity should run independently without parent stack
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    Log.d("RNAlarm", "NotificationHelper buildAlarmAlertNotification id=$id fullScreen=$fullScreen bg=$overlayBgColor text=$overlayTextColor")
    // Use direct PendingIntent instead of TaskStackBuilder to avoid parent stack conflicts
    val fsPi = PendingIntent.getActivity(
      context,
      (id + ":fs").hashCode(),
      fsIntent,
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

    val builder = NotificationCompat.Builder(context, style.alertChannelId)
      .setSmallIcon(smallIcon)
      .setContentTitle(title)
      .setContentText("Alarm time reached")
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setAutoCancel(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setContentIntent(fsPi)
      .addAction(0, "Dismiss", dismissPi)

    if (fullScreen) {
      builder.setFullScreenIntent(fsPi, true)
    }

    style.accentColor?.let {
      builder.setColor(it)
      builder.setColorized(true)
    }

    return builder.build()
  }

  fun showAlarmAlert(context: Context, id: String, label: String?) {
    val notif = buildAlarmAlertNotification(context, id, label, true, Style())
    NotificationManagerCompat.from(context).notify(id.hashCode(), notif)
  }

  fun showCountdownInfoNotification(context: Context, id: String, label: String?, targetAtMs: Long, style: Style) {
    ensureChannels(context, style)
    val title = if (!label.isNullOrBlank()) label else "Alarm"
    val timeText = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(targetAtMs))
    val content = "Rings at $timeText"
    val smallIcon = resolveSmallIcon(context, style.smallIconName)

    // Tapping opens AlarmActivity pre-armed to Stop if already ringing
    val fsIntent = Intent(context, AlarmActivity::class.java).apply {
      putExtra(AlarmReceiver.EXTRA_ID, id)
      putExtra(AlarmReceiver.EXTRA_LABEL, label)
      // AlarmActivity should run independently without parent stack
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    // Use direct PendingIntent instead of TaskStackBuilder to avoid parent stack conflicts
    val pi = PendingIntent.getActivity(
      context,
      (id + ":info").hashCode(),
      fsIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, style.timerChannelId)
      .setSmallIcon(smallIcon)
      .setContentTitle(title)
      .setContentText(content)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      // System chronometer counts down to the alarm time
      .setUsesChronometer(style.useChronometer)
      .setChronometerCountDown(true)
      .setWhen(targetAtMs)
      .setContentIntent(pi)

    style.accentColor?.let {
      builder.setColor(it)
      builder.setColorized(true)
    }

    NotificationManagerCompat.from(context).notify((id + ":info").hashCode(), builder.build())
  }

  private fun formatDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
  }

  private fun resolveSmallIcon(context: Context, name: String?): Int {
    if (name.isNullOrBlank()) return android.R.drawable.ic_lock_idle_alarm
    val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
    return if (resId != 0) resId else android.R.drawable.ic_lock_idle_alarm
  }
}


