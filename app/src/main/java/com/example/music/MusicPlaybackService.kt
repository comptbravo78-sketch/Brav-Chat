package com.example.music

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.notifications.BravNotificationHelper

class MusicPlaybackService : Service() {

  private var wakeLock: PowerManager.WakeLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val host = BravMusicPlayerHost.getInstance(applicationContext)
    when (intent?.action) {
      ACTION_PLAY_PAUSE -> host.togglePlayPause()
      ACTION_NEXT -> host.playNext(fromAutoPlay = false)
      ACTION_PREV -> host.playPrevious()
      ACTION_STOP -> {
        host.stopPlayback()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
      }
    }

    val track = host.currentTrack.value
    if (track == null) {
      releaseWakeLock()
      ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }

    val isPlaying = host.isPlaying.value
    if (isPlaying) {
      acquireWakeLock()
    } else {
      releaseWakeLock()
    }

    val notification =
      BravNotificationHelper.buildMusicPlaybackNotification(
        context = this,
        title = track.title,
        artist = track.channelTitle,
        isPlaying = isPlaying,
        openIntent = buildOpenAppPendingIntent(),
        prevIntent = buildServiceActionPendingIntent(ACTION_PREV, 101),
        playPauseIntent = buildServiceActionPendingIntent(ACTION_PLAY_PAUSE, 102),
        nextIntent = buildServiceActionPendingIntent(ACTION_NEXT, 103),
        stopIntent = buildServiceActionPendingIntent(ACTION_STOP, 104),
      )

    val fgType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
      } else {
        0
      }

    ServiceCompat.startForeground(
      this,
      BravNotificationHelper.MUSIC_NOTIFICATION_ID,
      notification,
      fgType,
    )

    return START_NOT_STICKY
  }

  override fun onDestroy() {
    releaseWakeLock()
    super.onDestroy()
  }

  private fun acquireWakeLock() {
    if (wakeLock?.isHeld == true) return
    val pm = getSystemService(POWER_SERVICE) as PowerManager
    wakeLock =
      pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BravChat::MusicPlaybackWakeLock").apply {
        setReferenceCounted(false)
        acquire(6 * 60 * 60 * 1000L)
      }
  }

  private fun releaseWakeLock() {
    runCatching {
      if (wakeLock?.isHeld == true) {
        wakeLock?.release()
      }
    }
    wakeLock = null
  }

  private fun buildOpenAppPendingIntent(): PendingIntent {
    val openIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_OPEN_MUSIC_TAB, true)
      }
    return PendingIntent.getActivity(
      this,
      100,
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun buildServiceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
    val intent = Intent(this, MusicPlaybackService::class.java).apply { this.action = action }
    return PendingIntent.getService(
      this,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  companion object {
    const val ACTION_SYNC_STATE = "com.example.music.ACTION_SYNC_STATE"
    const val ACTION_PLAY_PAUSE = "com.example.music.ACTION_PLAY_PAUSE"
    const val ACTION_NEXT = "com.example.music.ACTION_NEXT"
    const val ACTION_PREV = "com.example.music.ACTION_PREV"
    const val ACTION_STOP = "com.example.music.ACTION_STOP"
    const val EXTRA_OPEN_MUSIC_TAB = "open_music_tab"
  }
}
