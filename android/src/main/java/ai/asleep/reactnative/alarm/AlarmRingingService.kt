package ai.asleep.reactnative.alarm

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import android.app.KeyguardManager
import android.os.PowerManager

class AlarmRingingService : Service() {
  private var mediaPlayer: MediaPlayer? = null
  private var alarmId: String = "unknown"
  private var label: String? = null
  private var isRinging: Boolean = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_RING -> {
        alarmId = intent.getStringExtra(EXTRA_ID) ?: "unknown"
        label = intent.getStringExtra(EXTRA_LABEL)
        val km = getSystemService(KeyguardManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        val isLocked = (km?.isKeyguardLocked == true) || (km?.isDeviceLocked == true)
        val isInteractive = pm?.isInteractive == true
        val shouldFullScreen = isLocked || !isInteractive

        val notif = buildForegroundNotification(shouldFullScreen)
        if (Build.VERSION.SDK_INT >= 29) {
          startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
          startForeground(NOTIF_ID, notif)
        }
        // Cancel any pre-alarm countdown notification
        try {
          NotificationManagerCompat.from(this).cancel((alarmId + ":info").hashCode())
        } catch (_: Throwable) {}
        if (!isRinging) {
          startTone()
        }
        if (shouldFullScreen) {
          // Lock screen or not interactive: show full-screen activity only
          try {
            val fs = Intent(this, AlarmActivity::class.java)
              .putExtra(AlarmReceiver.EXTRA_ID, alarmId)
              .putExtra(AlarmReceiver.EXTRA_LABEL, label)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(fs)
          } catch (_: Throwable) {}
        } else {
          // Device is unlocked and interactive: prefer overlay; avoid launching full-screen/app
          try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
              AlarmOverlayService.show(this, alarmId, label)
            } else {
              // Fallback to activity if no overlay permission
              val fs = Intent(this, AlarmActivity::class.java)
                .putExtra(AlarmReceiver.EXTRA_ID, alarmId)
                .putExtra(AlarmReceiver.EXTRA_LABEL, label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
              startActivity(fs)
            }
          } catch (_: Throwable) {}
        }
      }
      ACTION_STOP -> {
        stopTone()
        try { AlarmOverlayService.hide(this) } catch (_: Throwable) {}
        stopSelf()
      }
    }
    return START_NOT_STICKY
  }

  private fun buildForegroundNotification(fullScreen: Boolean): Notification {
    return NotificationHelper.buildAlarmAlertNotification(this, alarmId, label, fullScreen)
  }

  private fun startTone() {
    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    mediaPlayer = MediaPlayer().apply {
      setDataSource(this@AlarmRingingService, uri)
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
    isRinging = true
  }

  private fun stopTone() {
    mediaPlayer?.let {
      try { it.stop() } catch (_: Throwable) {}
      try { it.release() } catch (_: Throwable) {}
    }
    mediaPlayer = null
    isRinging = false
  }

  override fun onDestroy() {
    stopTone()
    super.onDestroy()
  }

  companion object {
    const val NOTIF_ID = 41502
    const val ACTION_RING = "ai.asleep.reactnative.alarm.action.RING"
    const val ACTION_STOP = "ai.asleep.reactnative.alarm.action.STOP_RING"
    const val EXTRA_ID = "extra_id"
    const val EXTRA_LABEL = "extra_label"

    fun start(context: Context, id: String, label: String?) {
      val i = Intent(context, AlarmRingingService::class.java)
        .setAction(ACTION_RING)
        .putExtra(EXTRA_ID, id)
        .putExtra(EXTRA_LABEL, label)
      if (Build.VERSION.SDK_INT >= 26) {
        context.startForegroundService(i)
      } else {
        context.startService(i)
      }
    }

    fun stop(context: Context) {
      val i = Intent(context, AlarmRingingService::class.java)
        .setAction(ACTION_STOP)
      context.startService(i)
    }
  }
}


