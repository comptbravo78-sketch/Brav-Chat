package com.example.sms

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log

class HeadlessSmsSendService : Service() {
  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == "android.intent.action.RESPOND_VIA_MESSAGE") {
      val destination = intent.data?.schemeSpecificPart?.trim()
      val message = intent.getStringExtra(Intent.EXTRA_TEXT)
      if (!destination.isNullOrBlank() && !message.isNullOrBlank()) {
        runCatching {
          val smsManager =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
              getSystemService(SmsManager::class.java)
            } else {
              @Suppress("DEPRECATION")
              SmsManager.getDefault()
            }
          val parts = smsManager.divideMessage(message)
          if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(destination, null, parts, null, null)
          } else {
            smsManager.sendTextMessage(destination, null, message, null, null)
          }
          SmsEventBus.notifyUpdate()
        }.onFailure { err ->
          Log.e("HeadlessSmsSend", "Quick reply failed for ${Uri.encode(destination)}", err)
        }
      }
    }
    stopSelf(startId)
    return START_NOT_STICKY
  }
}
