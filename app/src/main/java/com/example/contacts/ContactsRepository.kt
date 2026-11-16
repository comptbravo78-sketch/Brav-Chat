package com.example.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.BravRepository
import com.example.model.LocalContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsRepository(
  private val context: Context,
  private val bravRepository: BravRepository,
) {

  fun hasContactsPermission(): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
      PackageManager.PERMISSION_GRANTED
  }

  fun observeSyncedContacts(): Flow<List<LocalContact>> = callbackFlow {
    val syncNow = {
      launch(Dispatchers.IO) {
        trySend(loadAndMatchContacts())
      }
    }
    syncNow()

    val observer =
      object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
          syncNow()
        }
      }

    if (hasContactsPermission()) {
      runCatching {
        context.contentResolver.registerContentObserver(
          ContactsContract.Contacts.CONTENT_URI,
          true,
          observer,
        )
      }
    }

    awaitClose {
      runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }
  }

  suspend fun loadAndMatchContacts(): List<LocalContact> = withContext(Dispatchers.IO) {
    if (!hasContactsPermission()) return@withContext emptyList()

    val seenNumbers = hashSetOf<String>()
    val rawContacts = mutableListOf<LocalContact>()

    val projection =
      arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
      )

    runCatching {
      context.contentResolver
        .query(
          ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
          projection,
          null,
          null,
          "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
        )
        ?.use { cursor ->
          val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
          val nameIdx =
            cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
          val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
          val photoIdx =
            cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

          while (cursor.moveToNext()) {
            val id = cursor.getString(idIdx).orEmpty()
            val name = cursor.getString(nameIdx).orEmpty().ifBlank { "Contact" }
            val number = cursor.getString(numIdx).orEmpty().trim()
            val normalized = BravRepository.normalizePhone(number)
            if (normalized.length < 6 || !seenNumbers.add(normalized)) continue
            val hash = BravRepository.sha256Hex(normalized)
            val photo = cursor.getString(photoIdx)
            rawContacts.add(
              LocalContact(
                id = id,
                name = name,
                phoneNumber = number,
                normalizedPhone = normalized,
                phoneHash = hash,
                photoUri = photo,
              )
            )
          }
        }
    }.onFailure { err ->
      Log.w("ContactsRepository", "Error reading local contacts", err)
    }

    if (rawContacts.isEmpty()) return@withContext emptyList()

    val matchedMap =
      runCatching {
        bravRepository.lookupBravUsersByPhoneHashes(rawContacts.map { it.phoneHash })
      }.getOrElse { emptyMap() }

    rawContacts
      .map { contact ->
        contact.copy(bravUser = matchedMap[contact.phoneHash])
      }
      .sortedWith(
        compareByDescending<LocalContact> { it.bravUser != null }.thenBy { it.name.lowercase() }
      )
  }
}
