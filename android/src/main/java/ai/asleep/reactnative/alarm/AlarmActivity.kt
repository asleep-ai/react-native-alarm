package ai.asleep.reactnative.alarm

import android.app.Activity
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AlarmActivity : Activity() {
  private var mediaPlayer: MediaPlayer? = null
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

    startRingtone()
  }

  private fun startRingtone() {
    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    mediaPlayer = MediaPlayer().apply {
      setDataSource(this@AlarmActivity, uri)
      setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
      )
      isLooping = true
      prepare()
      start()
    }
  }

  private fun stopAlarmAndFinish() {
    // Stop audio
    mediaPlayer?.let {
      try { it.stop() } catch (_: Throwable) {}
      try { it.release() } catch (_: Throwable) {}
    }
    mediaPlayer = null
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
    mediaPlayer?.let {
      try { it.stop() } catch (_: Throwable) {}
      try { it.release() } catch (_: Throwable) {}
    }
    mediaPlayer = null
    super.onDestroy()
  }
}


