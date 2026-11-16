package com.example.sms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.SmsMessageItem
import com.example.model.SmsThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.smsMetaStore by preferencesDataStore(name = "brav_sms_meta")

class SmsRepository(private val context: Context) {

  private val pinnedKey = stringSetPreferencesKey("pinned_threads")
  private val archivedKey = stringSetPreferencesKey("archived_threads")
  private val hiddenKey = stringSetPreferencesKey("hidden_threads")

  fun isDefaultSmsApp(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val roleManager = context.getSystemService(RoleManager::class.java)
      roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
    } else {
      Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
  }

  fun createDefaultSmsRoleIntent(): Intent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val roleManager = context.getSystemService(RoleManager::class.java)
      if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
        roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
      } else {
        null
      }
    } else {
      Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
        putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
      }
    }
  }

  fun hasSmsPermissions(): Boolean {
    val readGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED
    val sendGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
        PackageManager.PERMISSION_GRANTED
    return readGranted && sendGranted
  }

  fun observeSmsThreads(): Flow<List<SmsThread>> = callbackFlow {
    val reload = {
      launch(Dispatchers.IO) {
        trySend(loadSmsThreads())
      }
    }
    reload()

    val observer =
      object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
          reload()
        }
      }
    runCatching {
      context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
    }
    val busJob = launch { SmsEventBus.incomingEvents.collect { reload() } }
    val storeJob = launch { context.smsMetaStore.data.collect { reload() } }

    awaitClose {
      runCatching { context.contentResolver.unregisterContentObserver(observer) }
      busJob.cancel()
      storeJob.cancel()
    }
  }

  suspend fun loadSmsThreads(): List<SmsThread> = withContext(Dispatchers.IO) {
    if (
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      return@withContext emptyList()
    }

    val prefs = context.smsMetaStore.data.first()
    val pinned = prefs[pinnedKey] ?: emptySet()
    val archived = prefs[archivedKey] ?: emptySet()
    val hidden = prefs[hiddenKey] ?: emptySet()

    val threadMap = linkedMapOf<Long, MutableList<SmsRow>>()
    val projection =
      arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.THREAD_ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.READ,
      )

    runCatching {
      context.contentResolver
        .query(
          Telephony.Sms.CONTENT_URI,
          projection,
          null,
          null,
          "${Telephony.Sms.DATE} DESC",
        )
        ?.use { cursor ->
          val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
          val threadIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
          val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
          val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
          val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
          val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)

          var count = 0
          while (cursor.moveToNext() && count < 400) {
            count++
            val threadId = cursor.getLong(threadIdx)
            val address = cursor.getString(addrIdx).orEmpty()
            if (address.isBlank() || hidden.contains(threadId.toString())) continue
            val row =
              SmsRow(
                id = cursor.getLong(idIdx),
                threadId = threadId,
                address = address,
                body = cursor.getString(bodyIdx).orEmpty(),
                dateMs = cursor.getLong(dateIdx),
                isRead = cursor.getInt(readIdx) == 1,
              )
            threadMap.getOrPut(threadId) { mutableListOf() }.add(row)
          }
        }
    }.onFailure { err ->
      Log.w("SmsRepository", "Unable to query SMS provider", err)
    }

    threadMap.entries
      .map { (threadId, rows) ->
        val latest = rows.first()
        val (name, photo) = resolveContactInfo(latest.address)
        val unread = rows.count { !it.isRead }
        val key = threadId.toString()
        SmsThread(
          threadId = threadId,
          address = latest.address,
          contactName = name ?: latest.address,
          contactPhotoUri = photo,
          lastBody = latest.body,
          lastDateMs = latest.dateMs,
          unreadCount = unread,
          isPinned = pinned.contains(key),
          isArchived = archived.contains(key),
        )
      }
      .sortedWith(compareByDescending<SmsThread> { it.isPinned }.thenByDescending { it.lastDateMs })
  }

  suspend fun loadSmsMessagesForThread(threadId: Long, address: String): List<SmsMessageItem> =
    withContext(Dispatchers.IO) {
      if (
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) !=
          PackageManager.PERMISSION_GRANTED
      ) {
        return@withContext emptyList()
      }

      val items = mutableListOf<SmsMessageItem>()
      val selection =
        if (threadId > 0L) "${Telephony.Sms.THREAD_ID} = ?" else "${Telephony.Sms.ADDRESS} = ?"
      val selectionArgs = arrayOf(if (threadId > 0L) threadId.toString() else address)
      val projection =
        arrayOf(
          Telephony.Sms._ID,
          Telephony.Sms.THREAD_ID,
          Telephony.Sms.ADDRESS,
          Telephony.Sms.BODY,
          Telephony.Sms.DATE,
          Telephony.Sms.TYPE,
          Telephony.Sms.READ,
        )

      runCatching {
        context.contentResolver
          .query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} ASC",
          )
          ?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val tIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)

            while (cursor.moveToNext()) {
              val msgType = cursor.getInt(typeIdx)
              items.add(
                SmsMessageItem(
                  id = cursor.getLong(idIdx),
                  threadId = cursor.getLong(tIdx),
                  address = cursor.getString(addrIdx).orEmpty(),
                  body = cursor.getString(bodyIdx).orEmpty(),
                  dateMs = cursor.getLong(dateIdx),
                  isIncoming = msgType == Telephony.Sms.MESSAGE_TYPE_INBOX,
                  isRead = cursor.getInt(readIdx) == 1,
                  sendFailed = msgType == Telephony.Sms.MESSAGE_TYPE_FAILED,
                )
              )
            }
          }
      }
      items
    }

  suspend fun sendSms(
    destination: String,
    body: String,
    attachmentUri: Uri? = null,
    attachmentMimeType: String? = null,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      require(destination.isNotBlank()) { "Numéro de destinataire requis" }
      if (attachmentUri != null) {
        // MMS dispatch via system carrier intent / SendMultimediaMessage when attachment is provided
        val mmsIntent =
          Intent(Intent.ACTION_SEND).apply {
            putExtra("address", destination)
            putExtra("sms_body", body)
            putExtra(Intent.EXTRA_STREAM, attachmentUri)
            type = attachmentMimeType ?: "*/*"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
        context.startActivity(Intent.createChooser(mmsIntent, "Envoyer MMS"))
        return@runCatching
      }

      require(body.isNotBlank()) { "Le message ne peut pas être vide" }
      val smsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          context.getSystemService(SmsManager::class.java)
        } else {
          @Suppress("DEPRECATION")
          SmsManager.getDefault()
        }
      val parts = smsManager.divideMessage(body)
      if (parts.size > 1) {
        smsManager.sendMultipartTextMessage(destination, null, parts, null, null)
      } else {
        val sentAction = "com.example.sms.SMS_SENT_${System.currentTimeMillis()}"
        val sentPending =
          PendingIntent.getBroadcast(
            context,
            0,
            Intent(sentAction).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
        smsManager.sendTextMessage(destination, null, body, sentPending, null)
      }

      if (isDefaultSmsApp()) {
        runCatching {
          val values =
            ContentValues().apply {
              put(Telephony.Sms.ADDRESS, destination)
              put(Telephony.Sms.BODY, body)
              put(Telephony.Sms.DATE, System.currentTimeMillis())
              put(Telephony.Sms.READ, 1)
              put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
          context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        }
      }
      SmsEventBus.notifyUpdate()
    }
  }

  suspend fun markThreadRead(threadId: Long) = withContext(Dispatchers.IO) {
    if (!isDefaultSmsApp() || threadId <= 0L) return@withContext
    runCatching {
      val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
      context.contentResolver.update(
        Telephony.Sms.Inbox.CONTENT_URI,
        values,
        "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
        arrayOf(threadId.toString()),
      )
      SmsEventBus.notifyUpdate()
    }
  }

  suspend fun togglePinThread(threadId: Long) {
    val key = threadId.toString()
    context.smsMetaStore.edit { prefs ->
      val current = prefs[pinnedKey]?.toMutableSet() ?: mutableSetOf()
      if (!current.add(key)) current.remove(key)
      prefs[pinnedKey] = current
    }
  }

  suspend fun toggleArchiveThread(threadId: Long) {
    val key = threadId.toString()
    context.smsMetaStore.edit { prefs ->
      val current = prefs[archivedKey]?.toMutableSet() ?: mutableSetOf()
      if (!current.add(key)) current.remove(key)
      prefs[archivedKey] = current
    }
  }

  suspend fun deleteThread(threadId: Long) {
    val key = threadId.toString()
    if (isDefaultSmsApp() && threadId > 0L) {
      runCatching {
        context.contentResolver.delete(
          Telephony.Sms.CONTENT_URI,
          "${Telephony.Sms.THREAD_ID} = ?",
          arrayOf(threadId.toString()),
        )
      }
    }
    context.smsMetaStore.edit { prefs ->
      val current = prefs[hiddenKey]?.toMutableSet() ?: mutableSetOf()
      current.add(key)
      prefs[hiddenKey] = current
    }
    SmsEventBus.notifyUpdate()
  }

  private fun resolveContactInfo(phoneNumber: String): Pair<String?, String?> {
    if (
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      return null to null
    }
    if (phoneNumber.isBlank()) return null to null
    return runCatching {
      val uri =
        Uri.withAppendedPath(
          ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
          Uri.encode(phoneNumber),
        )
      context.contentResolver
        .query(
          uri,
          arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_URI,
          ),
          null,
          null,
          null,
        )
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            val name = cursor.getString(0)
            val photo = cursor.getString(1)
            name to photo
          } else {
            null to null
          }
        } ?: (null to null)
    }.getOrElse { null to null }
  }

  private data class SmsRow(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val dateMs: Long,
    val isRead: Boolean,
  )
}
