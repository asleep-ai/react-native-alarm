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
  companion object {
    @Volatile
    private var instance: ReactNativeAlarmModule? = null

    fun getInstance(): ReactNativeAlarmModule? = instance

    fun sendAlarmStartedEvent(id: String, label: String?, remainingSeconds: Long) {
      instance?.sendEvent("onAlarmStarted", mapOf(
        "id" to id,
        "label" to (label ?: ""),
        "remainingSeconds" to remainingSeconds
      ))
    }

    fun sendAlarmSnoozedEvent(id: String, label: String?, snoozeUntilISO: String) {
      instance?.sendEvent("onAlarmSnoozed", mapOf(
        "id" to id,
        "label" to (label ?: ""),
        "snoozeUntilISO" to snoozeUntilISO
      ))
    }

    fun sendAlarmStoppedEvent(id: String, label: String?, stoppedAtISO: String) {
      instance?.sendEvent("onAlarmStopped", mapOf(
        "id" to id,
        "label" to (label ?: ""),
        "stoppedAtISO" to stoppedAtISO
      ))
    }

    fun sendAlarmStateChangedEvent(
      id: String,
      label: String?,
      isRinging: Boolean,
      isSnoozed: Boolean,
      remainingSeconds: Long,
      stoppedAtISO: String? = null,
      snoozeUntilISO: String? = null,
      isPaused: Boolean = false
    ) {
      val eventMap = mutableMapOf<String, Any?>(
        "id" to id,
        "label" to (label ?: ""),
        "isRinging" to isRinging,
        "isSnoozed" to isSnoozed,
        "remainingSeconds" to remainingSeconds
      )
      if (isPaused) eventMap["isPaused"] = true
      stoppedAtISO?.let { eventMap["stoppedAtISO"] = it }
      snoozeUntilISO?.let { eventMap["snoozeUntilISO"] = it }
      instance?.sendEvent("onAlarmStateChanged", eventMap)
    }

    @JvmStatic
    fun currentISO(): String {
      return Instant.now().toString()
    }
  }

  override fun definition() = ModuleDefinition {
    Name("ReactNativeAlarm")

    Events("onAlarmFired", "onAlarmStarted", "onAlarmSnoozed", "onAlarmStopped", "onAlarmStateChanged")

    OnCreate {
      instance = this@ReactNativeAlarmModule
    }

    OnDestroy {
      instance = null
    }

    // ----- Configuration
    // Accept two arguments to avoid object->Map bridge issues.
    AsyncFunction("setConfig") { android: Map<String, Any?>?, _: Map<String, Any?>? ->
      if (android != null) {
        @Suppress("UNCHECKED_CAST")
        ConfigHolder.updateFromMap(android as Map<String, Any?>)
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
        return@AsyncFunction mapOf("granted" to false, "status" to "notAvailable")
      }
      val ctx = appContext.reactContext ?: return@AsyncFunction mapOf("granted" to false, "status" to "unknown")
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
          val finalEnabled = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
          return@AsyncFunction mapOf(
            "granted" to finalEnabled,
            "status" to if (finalEnabled) "authorized" else "denied"
          )
        }
      }
      mapOf(
        "granted" to enabled,
        "status" to if (enabled) "authorized" else "denied"
      )
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
          NotificationHelper.showCountdownInfoNotification(ctx, id, label, triggerAt, styleToNotifStyle(style))
        }
      }

      val storage = AlarmStorage(ctx)
      storage.add(StoredAlarm(id = id, dateISO = outISO, label = label, enabled = true))

      val remainingSeconds = when {
        countdownSeconds != null -> countdownSeconds
        dateISO != null -> {
          val triggerAt = parseIsoToEpochMillis(dateISO)
          if (triggerAt != null && triggerAt > System.currentTimeMillis()) {
            (triggerAt - System.currentTimeMillis()) / 1000
          } else 0
        }
        else -> 0
      }

      sendAlarmStartedEvent(id, label, remainingSeconds)
      sendAlarmStateChangedEvent(
        id = id,
        label = label,
        isRinging = false,
        isSnoozed = false,
        remainingSeconds = remainingSeconds
      )

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
          val storage = AlarmStorage(ctx)
          val alarm = storage.loadAll().find { it.id == id }
          cancelExactAlarm(ctx, id)
          val stopIntent = Intent(ctx, ForegroundTimerService::class.java)
            .setAction(ForegroundTimerService.ACTION_STOP)
            .putExtra(ForegroundTimerService.EXTRA_ID, id)
          ctx.startService(stopIntent)
          storage.removeById(id)
          
          val stoppedAtISO = currentISO()
          sendAlarmStoppedEvent(id, alarm?.label, stoppedAtISO)
          sendAlarmStateChangedEvent(
            id = id,
            label = alarm?.label,
            isRinging = false,
            isSnoozed = false,
            remainingSeconds = 0,
            stoppedAtISO = stoppedAtISO
          )
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
      val alarms = storage.loadAll()
      val stateInfo = calculateAlarmStates(ctx, alarms)
      checkAndSendAlarmStateEvents(ctx, alarms)
      alarms.map { a ->
        val state = stateInfo[a.id]
        val resultMap = mutableMapOf<String, Any>(
          "id" to a.id,
          "dateISO" to a.dateISO,
          "label" to (a.label ?: ""),
          "enabled" to a.enabled
        )
        state?.let {
          resultMap["isRinging"] = it.isRinging
          resultMap["isSnoozed"] = it.isSnoozed
          resultMap["remainingSeconds"] = it.remainingSeconds
          it.snoozeUntilISO?.let { snoozeUntil -> resultMap["snoozeUntilISO"] = snoozeUntil }
        }
        resultMap
      }
    }

    AsyncFunction("getAuthorizationStatus") {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return@AsyncFunction "notAvailable"
      }
      val ctx = appContext.reactContext ?: return@AsyncFunction "unknown"
      val enabled = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
      if (enabled) "authorized" else "denied"
    }

    AsyncFunction("openSettings") {
      val ctx = appContext.reactContext ?: return@AsyncFunction Unit
      val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${ctx.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      try {
        ctx.startActivity(intent)
      } catch (_: Exception) {}
    }

    AsyncFunction("checkAlarmStates") {
      val ctx = appContext.reactContext ?: return@AsyncFunction Unit
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return@AsyncFunction Unit
      }
      val storage = AlarmStorage(ctx)
      val alarms = storage.loadAll()
      checkAndSendAlarmStateEvents(ctx, alarms)
    }
  }

  private data class AlarmStateInfo(
    val isRinging: Boolean,
    val isSnoozed: Boolean,
    val remainingSeconds: Long,
    val snoozeUntilISO: String?
  )

  private fun calculateAlarmStates(context: Context, alarms: List<StoredAlarm>): Map<String, AlarmStateInfo> {
    val prefs = context.getSharedPreferences("react_native_alarm", Context.MODE_PRIVATE)
    val lastIsSnoozedJson = prefs.getString("ReactNativeAlarm.lastIsSnoozed", "{}")
    val lastIsRingingJson = prefs.getString("ReactNativeAlarm.lastIsRinging", "{}")
    val lastRemainingJson = prefs.getString("ReactNativeAlarm.lastRemaining", "{}")
    val lastScheduleDatesJson = prefs.getString("ReactNativeAlarm.lastScheduleDates", "{}")

    val lastIsSnoozed = parseJsonBooleanMap(lastIsSnoozedJson ?: "{}")
    val lastIsRinging = parseJsonBooleanMap(lastIsRingingJson ?: "{}")
    val lastRemaining = parseJsonLongMap(lastRemainingJson ?: "{}")
    val lastScheduleDates = parseJsonMap(lastScheduleDatesJson ?: "{}")

    val stateMap = mutableMapOf<String, AlarmStateInfo>()

    for (alarm in alarms) {
      val id = alarm.id
      // If alarm is disabled, it's stopped
      if (!alarm.enabled) {
        stateMap[id] = AlarmStateInfo(
          isRinging = false,
          isSnoozed = false,
          remainingSeconds = 0,
          snoozeUntilISO = null
        )
        continue
      }
      
      val remaining = calculateRemainingSeconds(context, alarm)
      val isRinging = remaining <= 1 && alarm.enabled
      val isSnoozed = checkIfSnoozed(
        context,
        alarm,
        lastIsSnoozed[id] ?: false,
        lastIsRinging[id] ?: false,
        lastRemaining[id] ?: Long.MAX_VALUE,
        lastScheduleDates[id] ?: ""
      )
      val snoozeUntilISO = if (isSnoozed) alarm.dateISO else null
      
      stateMap[id] = AlarmStateInfo(
        isRinging = isRinging,
        isSnoozed = isSnoozed,
        remainingSeconds = remaining,
        snoozeUntilISO = snoozeUntilISO
      )
    }

    return stateMap
  }

  private fun checkAndSendAlarmStateEvents(context: Context, alarms: List<StoredAlarm>) {
    val prefs = context.getSharedPreferences("react_native_alarm", Context.MODE_PRIVATE)
    val lastStatesJson = prefs.getString("ReactNativeAlarm.lastStates", "{}")
    val lastRemainingJson = prefs.getString("ReactNativeAlarm.lastRemaining", "{}")
    val lastIsRingingJson = prefs.getString("ReactNativeAlarm.lastIsRinging", "{}")
    val lastIsSnoozedJson = prefs.getString("ReactNativeAlarm.lastIsSnoozed", "{}")
    val lastScheduleDatesJson = prefs.getString("ReactNativeAlarm.lastScheduleDates", "{}")

    val lastStates = parseJsonMap(lastStatesJson ?: "{}")
    val lastRemaining = parseJsonLongMap(lastRemainingJson ?: "{}")
    val lastIsRinging = parseJsonBooleanMap(lastIsRingingJson ?: "{}")
    val lastIsSnoozed = parseJsonBooleanMap(lastIsSnoozedJson ?: "{}")
    val lastScheduleDates = parseJsonMap(lastScheduleDatesJson ?: "{}")

    val newStates = mutableMapOf<String, String>()
    val newRemaining = mutableMapOf<String, Long>()
    val newIsRinging = mutableMapOf<String, Boolean>()
    val newIsSnoozed = mutableMapOf<String, Boolean>()
    val newScheduleDates = mutableMapOf<String, String>()

    val currentAlarmIds = alarms.map { it.id }.toSet()

    // Check for stopped alarms
    for ((id, _) in lastStates) {
      if (!currentAlarmIds.contains(id)) {
        if (lastIsRinging[id] == true || lastIsSnoozed[id] == true) {
          val alarm = alarms.find { it.id == id }
          val stoppedAtISO = currentISO()
          sendAlarmStoppedEvent(id, alarm?.label, stoppedAtISO)
          sendAlarmStateChangedEvent(
            id = id,
            label = alarm?.label,
            isRinging = false,
            isSnoozed = false,
            remainingSeconds = 0,
            stoppedAtISO = stoppedAtISO
          )
        }
      }
    }

    for (alarm in alarms) {
      val id = alarm.id
      val remaining = calculateRemainingSeconds(context, alarm)
      val isRinging = remaining <= 1 && alarm.enabled
      val isSnoozed = checkIfSnoozed(context, alarm, lastIsSnoozed[id] ?: false, lastIsRinging[id] ?: false, lastRemaining[id] ?: Long.MAX_VALUE, lastScheduleDates[id] ?: "")

      newStates[id] = if (isRinging) "ringing" else if (isSnoozed) "snoozed" else if (alarm.enabled) "scheduled" else "paused"
      newRemaining[id] = remaining
      newIsRinging[id] = isRinging
      newIsSnoozed[id] = isSnoozed
      newScheduleDates[id] = alarm.dateISO

      val wasRinging = lastIsRinging[id] ?: false
      val wasSnoozed = lastIsSnoozed[id] ?: false

      // Send events for state changes
      if (isSnoozed && !wasSnoozed) {
        val snoozeUntilISO = alarm.dateISO
        sendAlarmSnoozedEvent(id, alarm.label, snoozeUntilISO)
        sendAlarmStateChangedEvent(
          id = id,
          label = alarm.label,
          isRinging = false,
          isSnoozed = true,
          remainingSeconds = remaining,
          snoozeUntilISO = snoozeUntilISO
        )
      } else if (!isSnoozed && wasSnoozed) {
        // Snooze was cancelled (e.g., from system UI or another app)
        sendAlarmStateChangedEvent(
          id = id,
          label = alarm.label,
          isRinging = isRinging,
          isSnoozed = false,
          remainingSeconds = remaining
        )
      } else if (isRinging && !wasRinging && !isSnoozed) {
        // Edge-triggered contract (v0.2.0): emit only on real transitions.
        // The former snooze-tick and countdown-tick arms emitted
        // onAlarmStateChanged every second (the analytics flood) and were
        // removed. Derive a live countdown in JS from onAlarmStarted's
        // remainingSeconds. Snapshot bookkeeping below still runs every pass.
        sendAlarmStartedEvent(id, alarm.label, 0)
        sendAlarmStateChangedEvent(
          id = id,
          label = alarm.label,
          isRinging = true,
          isSnoozed = false,
          remainingSeconds = 0
        )
      }
    }

    // Save current states
    prefs.edit()
      .putString("ReactNativeAlarm.lastStates", mapToJson(newStates))
      .putString("ReactNativeAlarm.lastRemaining", longMapToJson(newRemaining))
      .putString("ReactNativeAlarm.lastIsRinging", booleanMapToJson(newIsRinging))
      .putString("ReactNativeAlarm.lastIsSnoozed", booleanMapToJson(newIsSnoozed))
      .putString("ReactNativeAlarm.lastScheduleDates", mapToJson(newScheduleDates))
      .apply()
  }

  private fun calculateRemainingSeconds(context: Context, alarm: StoredAlarm): Long {
    if (!alarm.enabled) return 0
    return try {
      val triggerAt = Instant.parse(alarm.dateISO).toEpochMilli()
      val now = System.currentTimeMillis()
      maxOf(0, (triggerAt - now) / 1000)
    } catch (e: Exception) {
      0
    }
  }

  private fun checkIfSnoozed(
    context: Context,
    alarm: StoredAlarm,
    wasSnoozed: Boolean,
    wasRinging: Boolean,
    lastRemaining: Long,
    lastScheduleDate: String
  ): Boolean {
    if (!alarm.enabled || alarm.dateISO.isEmpty()) {
      return false
    }
    
    // If was snoozed and date changed, still snoozed
    if (wasSnoozed && alarm.dateISO != lastScheduleDate) {
      return try {
        val currentDate = Instant.parse(alarm.dateISO).toEpochMilli()
        val now = System.currentTimeMillis()
        // Still snoozed if the date is in the future
        currentDate > now
      } catch (e: Exception) {
        false
      }
    }
    
    // If was ringing and date changed to future, it's snoozed
    if (wasRinging && alarm.dateISO != lastScheduleDate && !alarm.dateISO.isEmpty()) {
      return try {
        val currentDate = Instant.parse(alarm.dateISO).toEpochMilli()
        val lastDate = if (lastScheduleDate.isNotEmpty()) Instant.parse(lastScheduleDate).toEpochMilli() else 0
        val now = System.currentTimeMillis()
        // Snoozed if the new date is in the future and later than the last date
        currentDate > now && currentDate > lastDate
      } catch (e: Exception) {
        false
      }
    }
    
    // If was snoozed and date hasn't changed, check if still in future
    if (wasSnoozed && alarm.dateISO == lastScheduleDate) {
      return try {
        val currentDate = Instant.parse(alarm.dateISO).toEpochMilli()
        val now = System.currentTimeMillis()
        currentDate > now
      } catch (e: Exception) {
        false
      }
    }
    
    return false
  }

  private fun parseJsonMap(json: String): Map<String, String> {
    return try {
      val jsonObject = org.json.JSONObject(json)
      jsonObject.keys().asSequence().associateWith { jsonObject.getString(it) }
    } catch (e: Exception) {
      emptyMap()
    }
  }

  private fun parseJsonLongMap(json: String): Map<String, Long> {
    return try {
      val jsonObject = org.json.JSONObject(json)
      jsonObject.keys().asSequence().associateWith { jsonObject.getLong(it) }
    } catch (e: Exception) {
      emptyMap()
    }
  }

  private fun parseJsonBooleanMap(json: String): Map<String, Boolean> {
    return try {
      val jsonObject = org.json.JSONObject(json)
      jsonObject.keys().asSequence().associateWith { jsonObject.getBoolean(it) }
    } catch (e: Exception) {
      emptyMap()
    }
  }

  private fun mapToJson(map: Map<String, String>): String {
    return try {
      val jsonObject = org.json.JSONObject()
      map.forEach { (k, v) -> jsonObject.put(k, v) }
      jsonObject.toString()
    } catch (e: Exception) {
      "{}"
    }
  }

  private fun longMapToJson(map: Map<String, Long>): String {
    return try {
      val jsonObject = org.json.JSONObject()
      map.forEach { (k, v) -> jsonObject.put(k, v) }
      jsonObject.toString()
    } catch (e: Exception) {
      "{}"
    }
  }

  private fun booleanMapToJson(map: Map<String, Boolean>): String {
    return try {
      val jsonObject = org.json.JSONObject()
      map.forEach { (k, v) -> jsonObject.put(k, v) }
      jsonObject.toString()
    } catch (e: Exception) {
      "{}"
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
