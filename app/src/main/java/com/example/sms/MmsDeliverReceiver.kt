package com.example.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.notifications.BravNotificationHelper

class MmsDeliverReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
    Log.i("MmsDeliverReceiver", "Incoming WAP_PUSH MMS received")
    BravNotificationHelper.showIncomingMmsNotification(context, "MMS")
    SmsEventBus.notifyUpdate()
  }
}
