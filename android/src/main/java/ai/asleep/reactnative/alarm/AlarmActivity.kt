package ai.asleep.reactnative.alarm

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AlarmActivity : Activity() {
  private var alarmId: String = "unknown"
  private var label: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    alarmId = intent.getStringExtra(AlarmReceiver.EXTRA_ID) ?: "unknown"
    label = intent.getStringExtra(AlarmReceiver.EXTRA_LABEL)

    // Ensure screen turns on and shows above lock screen
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
          WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
          WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
      )
    }

    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(60, 80, 60, 80)
    }
    val title = TextView(this).apply {
      textSize = 26f
      text = label ?: "Alarm"
    }
    val stop = Button(this).apply {
      text = "Stop"
      setOnClickListener { stopAlarmAndFinish() }
    }
    container.addView(title)
    container.addView(stop)
    setContentView(container)
  }

  private fun stopAlarmAndFinish() {
    // Stop ringing service if running
    try {
      AlarmRingingService.stop(this)
    } catch (_: Throwable) {}
    // Dismiss notification if present
    val mgr = getSystemService(android.app.NotificationManager::class.java)
    mgr.cancel(alarmId.hashCode())
    // Mark disabled in storage
    val storage = AlarmStorage(this)
    val items = storage.loadAll().map {
      if (it.id == alarmId) it.copy(enabled = false) else it
    }
    storage.saveAll(items)
    finish()
  }

  override fun onDestroy() {
    super.onDestroy()
  }
}


