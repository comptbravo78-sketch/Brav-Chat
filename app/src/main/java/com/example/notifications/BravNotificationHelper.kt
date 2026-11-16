package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object BravNotificationHelper {

  const val CHANNEL_MESSAGES = "brav_messages_channel"
  const val CHANNEL_CALLS = "brav_calls_channel"
  const val CHANNEL_SOCIAL = "brav_social_channel"
  const val CHANNEL_MUSIC = "brav_music_channel"
  const val MUSIC_NOTIFICATION_ID = 9002
  private const val CALL_NOTIFICATION_ID = 9001

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channels =
      listOf(
        NotificationChannel(
          CHANNEL_MESSAGES,
          context.getString(R.string.notification_channel_messages_name),
          NotificationManager.IMPORTANCE_HIGH,
        ),
        NotificationChannel(
          CHANNEL_CALLS,
          context.getString(R.string.notification_channel_calls_name),
          NotificationManager.IMPORTANCE_HIGH,
        ),
        NotificationChannel(
          CHANNEL_SOCIAL,
          context.getString(R.string.notification_channel_social_name),
          NotificationManager.IMPORTANCE_DEFAULT,
        ),
        NotificationChannel(
          CHANNEL_MUSIC,
          context.getString(R.string.notification_channel_music_name),
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          setShowBadge(false)
        },
      )
    nm.createNotificationChannels(channels)
  }

  /**
   * Renders the complete official BRAV Chat logo centered inside a safe circular zone
   * so Android notification masks (round/squircle largeIcon) never clip any part of it.
   */
  fun buildFullLogoLargeIcon(context: Context): Bitmap? {
    return runCatching {
      val source = BitmapFactory.decodeResource(context.resources, R.drawable.ic_brav_logo)
        ?: return null
      val size = 192
      val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(output)
      canvas.drawColor(Color.WHITE)

      // Fit entire logo cleanly inside inscribed circle (60% of canvas => zero edge clipping)
      val maxLogoDim = (size * 0.60f)
      val scale = minOf(maxLogoDim / source.width, maxLogoDim / source.height)
      val drawW = (source.width * scale).toInt().coerceAtLeast(1)
      val drawH = (source.height * scale).toInt().coerceAtLeast(1)
      val left = (size - drawW) / 2
      val top = (size - drawH) / 2
      val dst = Rect(left, top, left + drawW, top + drawH)
      val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
      canvas.drawBitmap(source, null, dst, paint)
      if (source != output) source.recycle()
      output
    }.getOrNull()
  }

  fun showIncomingSmsNotification(context: Context, sender: String, body: String) {
    if (!canPostNotifications(context)) return
    ensureChannels(context)
    val appCtx = context.applicationContext
    val intent =
      Intent(appCtx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra("address", sender)
      }
    val pendingIntent =
      PendingIntent.getActivity(
        appCtx,
        sender.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val fullLogo = buildFullLogoLargeIcon(appCtx)
    val notification =
      NotificationCompat.Builder(appCtx, CHANNEL_MESSAGES)
        .setSmallIcon(R.drawable.ic_notification_brav)
        .setColor(0xFF0057FF.toInt())
        .apply { if (fullLogo != null) setLargeIcon(fullLogo) }
        .setContentTitle(sender.ifBlank { "Nouveau SMS" })
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    runCatching { nm.notify(("sms_$sender").hashCode(), notification) }
  }

  fun showIncomingMmsNotification(context: Context, sender: String) {
    if (!canPostNotifications(context)) return
    ensureChannels(context)
    val appCtx = context.applicationContext
    val intent =
      Intent(appCtx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
    val pendingIntent =
      PendingIntent.getActivity(
        appCtx,
        "mms_incoming".hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val fullLogo = buildFullLogoLargeIcon(appCtx)
    val notification =
      NotificationCompat.Builder(appCtx, CHANNEL_MESSAGES)
        .setSmallIcon(R.drawable.ic_notification_brav)
        .setColor(0xFF0057FF.toInt())
        .apply { if (fullLogo != null) setLargeIcon(fullLogo) }
        .setContentTitle(sender.ifBlank { "Nouveau MMS" })
        .setContentText("Message multimédia reçu")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    runCatching { nm.notify("mms_incoming".hashCode(), notification) }
  }

  fun showOngoingCallNotification(context: Context, peerName: String, isVideo: Boolean) {
    if (!canPostNotifications(context)) return
    ensureChannels(context)
    val appCtx = context.applicationContext
    val intent =
      Intent(appCtx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
    val pendingIntent =
      PendingIntent.getActivity(
        appCtx,
        CALL_NOTIFICATION_ID,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val fullLogo = buildFullLogoLargeIcon(appCtx)
    val notification =
      NotificationCompat.Builder(appCtx, CHANNEL_CALLS)
        .setSmallIcon(R.drawable.ic_notification_brav)
        .setColor(0xFF0057FF.toInt())
        .apply { if (fullLogo != null) setLargeIcon(fullLogo) }
        .setContentTitle(if (isVideo) "Appel vidéo BRAV Chat" else "Appel audio BRAV Chat")
        .setContentText(peerName.ifBlank { "En ligne" })
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setOngoing(true)
        .setContentIntent(pendingIntent)
        .build()

    val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    runCatching { nm.notify(CALL_NOTIFICATION_ID, notification) }
  }

  fun cancelCallNotification(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    runCatching { nm.cancel(CALL_NOTIFICATION_ID) }
  }

  fun buildMusicPlaybackNotification(
    context: Context,
    title: String,
    artist: String,
    isPlaying: Boolean,
    openIntent: PendingIntent,
    prevIntent: PendingIntent,
    playPauseIntent: PendingIntent,
    nextIntent: PendingIntent,
    stopIntent: PendingIntent,
  ): android.app.Notification {
    ensureChannels(context)
    val appCtx = context.applicationContext
    val fullLogo = buildFullLogoLargeIcon(appCtx)
    return NotificationCompat.Builder(appCtx, CHANNEL_MUSIC)
      .setSmallIcon(R.drawable.ic_notification_brav)
      .setColor(0xFF1254E8.toInt())
      .apply { if (fullLogo != null) setLargeIcon(fullLogo) }
      .setContentTitle(title.ifBlank { "BRAV Musique" })
      .setContentText(
        buildString {
          append(artist.ifBlank { "YouTube Music" })
          append(if (isPlaying) " • En lecture" else " • En pause")
        }
      )
      .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOngoing(isPlaying)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setContentIntent(openIntent)
      .addAction(
        android.R.drawable.ic_media_previous,
        "Précédent",
        prevIntent,
      )
      .addAction(
        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        if (isPlaying) "Pause" else "Lire",
        playPauseIntent,
      )
      .addAction(
        android.R.drawable.ic_media_next,
        "Suivant",
        nextIntent,
      )
      .addAction(
        android.R.drawable.ic_menu_close_clear_cancel,
        "Arrêter",
        stopIntent,
      )
      .build()
  }

  private fun canPostNotifications(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }
}
