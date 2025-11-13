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
import android.os.PowerManager
import android.graphics.Color
import android.util.Log
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

class ReactNativeAlarmModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ReactNativeAlarm")

    Events("onAlarmFired")

    // ----- Configuration
    // Accept two arguments to avoid object->Map bridge issues.
    AsyncFunction("setConfig") { android: Map<String, Any?>?, _: Map<String, Any?>? ->
      if (android != null) {
        @Suppress("UNCHECKED_CAST")
        ConfigHolder.updateFromMap(android as Map<String, Any?>)
        Log.d("RNAlarm", "Module setConfig android=$android")
      }
    }

    Function("isAlarmKitAvailable") {
      // Android 13+ implementation only
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    Function("hasOverlayPermission") {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val ctx = appContext.reactContext
        if (ctx != null) {
          Settings.canDrawOverlays(ctx)
        } else {
          false
        }
      } else false
    }

    Function("isIgnoringBatteryOptimizations") {
      val ctx = appContext.reactContext
      if (ctx != null) {
        val pm = ctx.getSystemService(PowerManager::class.java)
        pm.isIgnoringBatteryOptimizations(ctx.packageName)
      } else false
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

    AsyncFunction("getAndroidPermissionStatus") {
      val ctx = appContext.reactContext ?: return@AsyncFunction mapOf<String, Any>(
        "notifications" to false,
        "exactAlarmAllowed" to false,
        "overlayAllowed" to false,
        "ignoringBatteryOptimizations" to false
      )
      val notifications = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
      val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.canScheduleExactAlarms()
      } else false
      val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(ctx) else true
      val pm = ctx.getSystemService(PowerManager::class.java)
      val ignore = pm.isIgnoringBatteryOptimizations(ctx.packageName)
      mapOf(
        "notifications" to notifications,
        "exactAlarmAllowed" to exact,
        "overlayAllowed" to overlay,
        "ignoringBatteryOptimizations" to ignore
      )
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

    AsyncFunction("openOverlayPermissionSettings") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
          data = Uri.parse("package:${ctx.packageName}")
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return@AsyncFunction try {
          ctx.startActivity(intent)
          true
        } catch (e: Exception) {
          false
        }
      } else false
    }

    AsyncFunction("openBatteryOptimizationSettings") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
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
      val style = ConfigHolder.mergeWithOverrides(options["android"] as? Map<String, Any?>)
      NotificationHelper.ensureChannels(ctx, styleToNotifStyle(style))

      val label = (options["label"] as? String)?.takeIf { it.isNotBlank() }
      val dateISO = (options["dateISO"] as? String)?.takeIf { it.isNotBlank() }
      val countdownSeconds = (options["countdownSeconds"] as? Number)?.toLong()?.takeIf { it > 0L }

      val id = UUID.randomUUID().toString()
      var outISO = ""
      Log.d("RNAlarm", "Module scheduleAlarm id=$id label=$label dateISO=$dateISO seconds=$countdownSeconds style=$style")

      // Timer (countdown) via foreground service
      if (countdownSeconds != null) {
        ForegroundTimerService.start(ctx, id, label, countdownSeconds, styleToMap(style))
        outISO = isoAfterSeconds(countdownSeconds)
      }

      // Fixed date via AlarmManager
      if (dateISO != null) {
        val triggerAt = parseIsoToEpochMillis(dateISO)
        if (triggerAt != null && triggerAt > System.currentTimeMillis()) {
          scheduleExactAlarm(ctx, id, label, triggerAt)
          outISO = dateISO
          // Show live countdown notification without FGS (chronometer-based)
          NotificationHelper.showCountdownInfoNotification(ctx, id, label, triggerAt, styleToNotifStyle(style))
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
    // Persist current style into the broadcast so it survives process restarts
    val s = ConfigHolder.globalStyle
    fireIntent.putExtra(AlarmRingingService.EXTRA_STYLE_TIMER_CHANNEL_ID, s.timerChannelId)
    fireIntent.putExtra(AlarmRingingService.EXTRA_STYLE_ALERT_CHANNEL_ID, s.alertChannelId)
    s.smallIconName?.let { fireIntent.putExtra(AlarmRingingService.EXTRA_STYLE_SMALL_ICON, it) }
    s.accentColor?.let { fireIntent.putExtra(AlarmRingingService.EXTRA_STYLE_ACCENT_COLOR, it) }
    fireIntent.putExtra(AlarmRingingService.EXTRA_STYLE_USE_CHRONOMETER, s.useChronometer)
    fireIntent.putExtra(AlarmRingingService.EXTRA_STYLE_SHOW_OVERLAY, s.showOverlayWhenUnlocked)
    s.overlayBackgroundColor?.let { fireIntent.putExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_BG, it) }
    s.overlayTextColor?.let { fireIntent.putExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_TEXT, it) }
    s.overlayButtonBackgroundColor?.let { fireIntent.putExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_BTN_BG, it) }
    s.overlayButtonTextColor?.let { fireIntent.putExtra(AlarmRingingService.EXTRA_STYLE_OVERLAY_BTN_TEXT, it) }
    fireIntent.putExtra(AlarmRingingService.EXTRA_STYLE_SNOOZE_MIN, s.snoozeMinutes)
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

// ---- Config holder and helpers
data class AndroidStyle(
  val timerChannelId: String = NotificationHelper.CHANNEL_TIMERS_HIGH,
  val alertChannelId: String = NotificationHelper.CHANNEL_ALERTS,
  val smallIconName: String? = null,
  val accentColor: Int? = null,
  val useChronometer: Boolean = true,
  val showOverlayWhenUnlocked: Boolean = true,
  val overlayBackgroundColor: Int? = null,
  val overlayTextColor: Int? = null,
  val overlayButtonBackgroundColor: Int? = null,
  val overlayButtonTextColor: Int? = null,
  val snoozeMinutes: Int = 5
)

object ConfigHolder {
  @Volatile var globalStyle: AndroidStyle = AndroidStyle()

  fun updateFromMap(map: Map<String, Any?>) {
    globalStyle = merge(globalStyle, fromMap(map))
  }

  fun mergeWithOverrides(overrideMap: Map<String, Any?>?): AndroidStyle {
    if (overrideMap == null) return globalStyle
    return merge(globalStyle, fromMap(overrideMap))
  }

  private fun fromMap(map: Map<String, Any?>): AndroidStyle {
    val timerChannelId = (map["timerChannelId"] as? String)
    val alertChannelId = (map["alertChannelId"] as? String)
    val smallIconName = (map["smallIconName"] as? String)
    val accentColorHex = (map["accentColor"] as? String)
    val useChronometer = (map["useChronometer"] as? Boolean)
    val showOverlayWhenUnlocked = (map["showOverlayWhenUnlocked"] as? Boolean)
    val overlayBgHex = (map["overlayBackgroundColor"] as? String)
    val overlayTextHex = (map["overlayTextColor"] as? String)
    val overlayBtnBgHex = (map["overlayButtonBackgroundColor"] as? String)
    val overlayBtnTextHex = (map["overlayButtonTextColor"] as? String)
    val snoozeMinutes = (map["snoozeMinutes"] as? Number)?.toInt()
    return AndroidStyle(
      timerChannelId = timerChannelId ?: NotificationHelper.CHANNEL_TIMERS_HIGH,
      alertChannelId = alertChannelId ?: NotificationHelper.CHANNEL_ALERTS,
      smallIconName = smallIconName,
      accentColor = accentColorHex?.let { parseColor(it) },
      useChronometer = useChronometer ?: true,
      showOverlayWhenUnlocked = showOverlayWhenUnlocked ?: true,
      overlayBackgroundColor = overlayBgHex?.let { parseColor(it) },
      overlayTextColor = overlayTextHex?.let { parseColor(it) },
      overlayButtonBackgroundColor = overlayBtnBgHex?.let { parseColor(it) },
      overlayButtonTextColor = overlayBtnTextHex?.let { parseColor(it) },
      snoozeMinutes = snoozeMinutes ?: 5
    )
  }

  private fun merge(base: AndroidStyle, inc: AndroidStyle): AndroidStyle {
    return AndroidStyle(
      timerChannelId = inc.timerChannelId.ifBlank { base.timerChannelId },
      alertChannelId = inc.alertChannelId.ifBlank { base.alertChannelId },
      smallIconName = inc.smallIconName ?: base.smallIconName,
      accentColor = inc.accentColor ?: base.accentColor,
      useChronometer = inc.useChronometer,
      showOverlayWhenUnlocked = inc.showOverlayWhenUnlocked,
      overlayBackgroundColor = inc.overlayBackgroundColor ?: base.overlayBackgroundColor,
      overlayTextColor = inc.overlayTextColor ?: base.overlayTextColor,
      overlayButtonBackgroundColor = inc.overlayButtonBackgroundColor ?: base.overlayButtonBackgroundColor,
      overlayButtonTextColor = inc.overlayButtonTextColor ?: base.overlayButtonTextColor,
      snoozeMinutes = if (inc.snoozeMinutes != 5) inc.snoozeMinutes else base.snoozeMinutes
    )
  }

  private fun parseColor(hex: String): Int? {
    return try {
      Color.parseColor(hex)
    } catch (_: Throwable) { null }
  }
}

// Convert internal AndroidStyle into maps/styles for other components
fun styleToMap(s: AndroidStyle): Map<String, Any?> {
  val out = mutableMapOf<String, Any?>()
  out["timerChannelId"] = s.timerChannelId
  out["alertChannelId"] = s.alertChannelId
  s.smallIconName?.let { out["smallIconName"] = it }
  s.accentColor?.let { out["accentColor"] = it }
  out["useChronometer"] = s.useChronometer
  out["showOverlayWhenUnlocked"] = s.showOverlayWhenUnlocked
  s.overlayBackgroundColor?.let { out["overlayBackgroundColor"] = it }
  s.overlayTextColor?.let { out["overlayTextColor"] = it }
  s.overlayButtonBackgroundColor?.let { out["overlayButtonBackgroundColor"] = it }
  s.overlayButtonTextColor?.let { out["overlayButtonTextColor"] = it }
  out["snoozeMinutes"] = s.snoozeMinutes
  return out
}

private fun styleToNotifStyle(s: AndroidStyle): NotificationHelper.Style {
  return NotificationHelper.Style(
    timerChannelId = s.timerChannelId,
    alertChannelId = s.alertChannelId,
    smallIconName = s.smallIconName,
    accentColor = s.accentColor,
    useChronometer = s.useChronometer
  )
}
