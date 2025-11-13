package ai.asleep.reactnative.alarm

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class StoredAlarm(
  val id: String,
  val dateISO: String,
  val label: String?,
  val enabled: Boolean
)

internal class AlarmStorage(private val context: Context) {
  private val prefs: SharedPreferences by lazy {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun loadAll(): MutableList<StoredAlarm> {
    val raw = prefs.getString(STORAGE_KEY, "[]") ?: "[]"
    val arr = JSONArray(raw)
    val out = mutableListOf<StoredAlarm>()
    for (i in 0 until arr.length()) {
      val o = arr.getJSONObject(i)
      out.add(
        StoredAlarm(
          id = o.optString("id"),
          dateISO = o.optString("dateISO"),
          label = if (o.has("label")) o.optString("label") else null,
          enabled = o.optBoolean("enabled", true)
        )
      )
    }
    return out
  }

  fun saveAll(items: List<StoredAlarm>) {
    val arr = JSONArray()
    items.forEach { a ->
      val o = JSONObject()
      o.put("id", a.id)
      o.put("dateISO", a.dateISO)
      if (a.label != null) o.put("label", a.label)
      o.put("enabled", a.enabled)
      arr.put(o)
    }
    prefs.edit().putString(STORAGE_KEY, arr.toString()).apply()
  }

  fun add(alarm: StoredAlarm) {
    val items = loadAll()
    items.add(alarm)
    saveAll(items)
  }

  fun removeById(id: String) {
    val items = loadAll()
    items.removeAll { it.id == id }
    saveAll(items)
  }

  fun clear() {
    saveAll(emptyList())
  }

  companion object {
    private const val PREFS_NAME = "react_native_alarm"
    private const val STORAGE_KEY = "ReactNativeAlarm.scheduled"
  }
}


