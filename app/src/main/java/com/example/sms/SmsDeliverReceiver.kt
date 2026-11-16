package com.example.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.notifications.BravNotificationHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SmsEventBus {
  private val _incomingEvents = MutableSharedFlow<Long>(extraBufferCapacity = 16)
  val incomingEvents = _incomingEvents.asSharedFlow()

  fun notifyUpdate() {
    _incomingEvents.tryEmit(System.currentTimeMillis())
  }
}

class SmsDeliverReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
    if (messages.isNullOrEmpty()) return

    val grouped = messages.groupBy { it.originatingAddress ?: "" }
    grouped.forEach { (sender, parts) ->
      val body = parts.joinToString("") { it.messageBody ?: "" }
      val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
      runCatching {
        val values =
          ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
          }
        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
      }.onFailure { err ->
        Log.e("SmsDeliverReceiver", "Failed inserting incoming SMS", err)
      }
      BravNotificationHelper.showIncomingSmsNotification(context, sender, body)
    }
    SmsEventBus.notifyUpdate()
  }
}
