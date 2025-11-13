package ai.asleep.reactnative.alarm

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class ForegroundTimerService : Service() {
  private val handler = Handler(Looper.getMainLooper())

  private var timerId: String? = null
  private var label: String? = null
  private var targetElapsedRealtime: Long = 0L
  private var remainingWhenPaused: Long = 0L
  private var isPaused: Boolean = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START -> {
        val id = intent.getStringExtra(EXTRA_ID) ?: return START_NOT_STICKY
        val seconds = intent.getLongExtra(EXTRA_SECONDS, 0L)
        label = intent.getStringExtra(EXTRA_LABEL)
        timerId = id
        isPaused = false
        targetElapsedRealtime = System.currentTimeMillis() + seconds * 1000
        startForegroundInternal(buildNotification())
        tick()
      }
      ACTION_PAUSE -> {
        if (!isPaused) {
          val now = System.currentTimeMillis()
          remainingWhenPaused = ((targetElapsedRealtime - now) / 1000).coerceAtLeast(0)
          isPaused = true
          updateNotification()
        }
      }
      ACTION_RESUME -> {
        if (isPaused) {
          val now = System.currentTimeMillis()
          targetElapsedRealtime = now + remainingWhenPaused * 1000
          isPaused = false
          updateNotification()
          tick()
        }
      }
      ACTION_STOP -> {
        stopSelf()
      }
    }
    return START_STICKY
  }

  private fun startForegroundInternal(notification: Notification) {
    if (Build.VERSION.SDK_INT >= 29) {
      startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
    } else {
      startForeground(NOTIF_ID, notification)
    }
  }

  private fun buildNotification(): Notification {
    val id = timerId ?: "unknown"
    val remainingSeconds = remainingSeconds()
    return NotificationHelper.buildTimerNotification(
      this,
      id,
      label,
      remainingSeconds,
      isPaused
    )
  }

  private fun updateNotification() {
    val notif = buildNotification()
    startForegroundInternal(notif)
  }

  private fun remainingSeconds(): Long {
    return if (isPaused) {
      remainingWhenPaused
    } else {
      val now = System.currentTimeMillis()
      ((targetElapsedRealtime - now) / 1000).coerceAtLeast(0)
    }
  }

  private fun tick() {
    handler.removeCallbacksAndMessages(null)
    if (isPaused) return
    val remain = remainingSeconds()
    if (remain <= 0) {
      finish()
      return
    }
    updateNotification()
    handler.postDelayed({ tick() }, 1000L)
  }

  private fun finish() {
    val id = timerId ?: "unknown"
    NotificationHelper.showAlarmAlert(this, id, label)
    stopForeground(true)
    stopSelf()
  }

  override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    super.onDestroy()
  }

  companion object {
    const val NOTIF_ID = 41501

    const val ACTION_START = "ai.asleep.reactnative.alarm.action.START"
    const val ACTION_PAUSE = "ai.asleep.reactnative.alarm.action.PAUSE"
    const val ACTION_RESUME = "ai.asleep.reactnative.alarm.action.RESUME"
    const val ACTION_STOP = "ai.asleep.reactnative.alarm.action.STOP"

    const val EXTRA_ID = "extra_id"
    const val EXTRA_SECONDS = "extra_seconds"
    const val EXTRA_LABEL = "extra_label"

    fun start(context: Context, id: String, label: String?, seconds: Long) {
      val intent = Intent(context, ForegroundTimerService::class.java)
        .setAction(ACTION_START)
        .putExtra(EXTRA_ID, id)
        .putExtra(EXTRA_LABEL, label)
        .putExtra(EXTRA_SECONDS, seconds)
      if (Build.VERSION.SDK_INT >= 26) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }
  }
}


