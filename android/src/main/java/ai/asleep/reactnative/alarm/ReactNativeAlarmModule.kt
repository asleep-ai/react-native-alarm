package ai.asleep.reactnative.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import android.provider.Settings
import android.net.Uri
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

class ReactNativeAlarmModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ReactNativeAlarm")

    Events("onAlarmFired")

    Function("isAlarmKitAvailable") {
      // Android 13+ implementation only
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    Function("canScheduleExactAlarms") {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val ctx = appContext.reactContext
        if (ctx != null) {
          val am = ctx.getSystemService(AlarmManager::class.java)
          am.canScheduleExactAlarms()
        } else {
          false
        }
      } else {
        false
      }
    }

    AsyncFunction("requestPermission") {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return@AsyncFunction false
      }
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      val enabled = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
      // Best-effort prompt: if disabled and we have an activity, request runtime permission.
      if (!enabled) {
        val activity = appContext.activityProvider?.currentActivity
        if (activity != null) {
          androidx.core.app.ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            5011
          )
          // Return current state; caller can call again if user interacts.
          return@AsyncFunction NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        }
      }
      enabled
    }

    AsyncFunction("openExactAlarmSettings") {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return@AsyncFunction false
      }
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${ctx.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      return@AsyncFunction try {
        ctx.startActivity(intent)
        true
      } catch (e: Exception) {
        false
      }
    }

    AsyncFunction("scheduleAlarm") { options: Map<String, Any?> ->
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        throw IllegalStateException("Android 13+ is required")
      }
      val ctx = appContext.reactContext ?: throw IllegalStateException("No context")
      NotificationHelper.ensureChannels(ctx)

      val label = (options["label"] as? String)?.takeIf { it.isNotBlank() }
      val dateISO = (options["dateISO"] as? String)?.takeIf { it.isNotBlank() }
      val countdownSeconds = (options["countdownSeconds"] as? Number)?.toLong()?.takeIf { it > 0L }

      val id = UUID.randomUUID().toString()
      var outISO = ""

      // Timer (countdown) via foreground service
      if (countdownSeconds != null) {
        ForegroundTimerService.start(ctx, id, label, countdownSeconds)
        outISO = isoAfterSeconds(countdownSeconds)
      }

      // Fixed date via AlarmManager
      if (dateISO != null) {
        val triggerAt = parseIsoToEpochMillis(dateISO)
        if (triggerAt != null && triggerAt > System.currentTimeMillis()) {
          scheduleExactAlarm(ctx, id, label, triggerAt)
          outISO = dateISO
        }
      }

      // Mirror in storage
      val storage = AlarmStorage(ctx)
      storage.add(StoredAlarm(id = id, dateISO = outISO, label = label, enabled = true))

      mapOf(
        "id" to id,
        "dateISO" to outISO,
        "label" to (label ?: ""),
        "enabled" to true
      )
    }

    AsyncFunction("cancelAlarm") { id: String ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val ctx = appContext.reactContext
        if (ctx != null) {
          cancelExactAlarm(ctx, id)
          // Stop timer service if running (best-effort)
          val stopIntent = Intent(ctx, ForegroundTimerService::class.java)
            .setAction(ForegroundTimerService.ACTION_STOP)
            .putExtra(ForegroundTimerService.EXTRA_ID, id)
          ctx.startService(stopIntent)
          AlarmStorage(ctx).removeById(id)
        }
      }
    }

    AsyncFunction("cancelAll") {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val ctx = appContext.reactContext
        if (ctx != null) {
          val storage = AlarmStorage(ctx)
          val all = storage.loadAll()
          all.forEach { a -> cancelExactAlarm(ctx, a.id) }
          // Stop running service
          val stopIntent = Intent(ctx, ForegroundTimerService::class.java)
            .setAction(ForegroundTimerService.ACTION_STOP)
          ctx.startService(stopIntent)
          storage.clear()
        }
      }
    }

    AsyncFunction("getAlarms") {
      val ctx = appContext.reactContext ?: return@AsyncFunction emptyList<Map<String, Any>>()
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return@AsyncFunction emptyList<Map<String, Any>>()
      }
      val storage = AlarmStorage(ctx)
      storage.loadAll().map { a ->
        mapOf(
          "id" to a.id,
          "dateISO" to a.dateISO,
          "label" to (a.label ?: ""),
          "enabled" to a.enabled
        )
      }
    }
  }

  private fun parseIsoToEpochMillis(iso: String): Long? {
    return try {
      Instant.parse(iso).toEpochMilli()
    } catch (e: DateTimeParseException) {
      null
    }
  }

  private fun isoAfterSeconds(seconds: Long): String {
    return Instant.ofEpochMilli(System.currentTimeMillis() + seconds * 1000)
      .toString()
  }

  private fun scheduleExactAlarm(context: Context, id: String, label: String?, triggerAtMillis: Long) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val fireIntent = Intent(context, AlarmReceiver::class.java)
      .setAction(AlarmReceiver.ACTION_FIRE)
      .putExtra(AlarmReceiver.EXTRA_ID, id)
      .putExtra(AlarmReceiver.EXTRA_LABEL, label)
    val firePi = PendingIntent.getBroadcast(
      context,
      id.hashCode(),
      fireIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    try {
      val showPi = firePi // We don't have an Activity reference; reuse broadcast
      val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showPi)
      alarmManager.setAlarmClock(info, firePi)
    } catch (se: SecurityException) {
      // Fallback to inexact alarm if exact permission isn't granted
      alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, firePi)
    }
  }

  private fun cancelExactAlarm(context: Context, id: String) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val fireIntent = Intent(context, AlarmReceiver::class.java)
      .setAction(AlarmReceiver.ACTION_FIRE)
      .putExtra(AlarmReceiver.EXTRA_ID, id)
    val firePi = PendingIntent.getBroadcast(
      context,
      id.hashCode(),
      fireIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(firePi)
  }
}
