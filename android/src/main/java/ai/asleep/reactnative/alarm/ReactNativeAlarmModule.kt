package ai.asleep.reactnative.alarm

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.util.UUID

class ReactNativeAlarmModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ReactNativeAlarm")

    Events("onAlarmFired")

    Function("isAlarmKitAvailable") {
      // AlarmKit is iOS-only
      false
    }

    AsyncFunction("requestPermission") {
      // No-op on Android
      true
    }

    AsyncFunction("scheduleAlarm") { _: Map<String, Any?> ->
      // Stub to satisfy cross-platform API
      mapOf(
        "id" to "unsupported-android-${UUID.randomUUID()}",
        "dateISO" to "",
        "label" to "",
        "enabled" to false
      )
    }

    AsyncFunction("cancelAlarm") { _: String ->
      // No-op
      Unit
    }

    AsyncFunction("getAlarms") {
      emptyList<Map<String, Any>>()
    }
  }
}
