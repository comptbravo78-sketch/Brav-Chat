package com.example.data

import android.content.Context
import android.os.Build
import com.example.R
import com.example.model.ActivityLog
import com.example.model.CallLog
import com.example.model.ChatMessage
import com.example.model.ConnectedDevice
import com.example.model.Conversation
import com.example.model.PublicDirectoryEntry
import com.example.model.StatusPost
import com.example.model.StatusView
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import java.security.MessageDigest
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class BravRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth,
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  private fun <T> Flow<T>.catchFirestore(
    uid: String,
    operationType: OperationType,
    path: String,
  ): Flow<T> = catch { error ->
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(exceptionToLog, operationType, path)
    }
    throw error
  }

  // --- USER PROFILE ---

  fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
    val path = "users/$userId"
    emitAll(
      db.collection("users")
        .document(userId)
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(
              UserProfile::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          } else {
            null
          }
        }
        .catchFirestore(userId, OperationType.GET, path)
    )
  }

  suspend fun getUserProfile(userId: String): Result<UserProfile?> = runCatching {
    val path = "users/$userId"
    try {
      val doc = db.collection("users").document(userId).get().await()
      if (doc.exists()) {
        doc.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      } else {
        null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, path)
      throw e
    }
  }

  suspend fun createOrUpdateUserProfile(profile: UserProfile): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    val existing = docRef.get().await()
    val payload =
      mutableMapOf<String, Any>(
        "userId" to uid,
        "displayName" to profile.displayName.ifBlank { "Utilisateur BRAV" }.take(80),
        "firstName" to profile.firstName.take(40),
        "lastName" to profile.lastName.take(40),
        "bravId" to profile.bravId.ifBlank { "@brav_${uid.take(6)}" }.take(40),
        "phoneNumber" to profile.phoneNumber.take(25),
        "countryCode" to profile.countryCode.take(10),
        "countryIso" to profile.countryIso.take(5),
        "bioStatus" to profile.bioStatus.take(160),
        "avatarUrl" to profile.avatarUrl.take(100_000),
        "biometricEnabled" to profile.biometricEnabled,
        "pinHash" to profile.pinHash.take(128),
        "privacyLastSeen" to profile.privacyLastSeen,
        "readReceipts" to profile.readReceipts,
        "notificationsEnabled" to profile.notificationsEnabled,
        "themeMode" to profile.themeMode,
        "autoCompressMedia" to profile.autoCompressMedia,
        "noiseSuppressionEnabled" to profile.noiseSuppressionEnabled,
        "hdVideoCalls" to profile.hdVideoCalls,
        "aiAutoReplyEnabled" to profile.aiAutoReplyEnabled,
        "aiAutoReplyTone" to profile.aiAutoReplyTone.take(40),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    if (!existing.exists()) {
      payload["createdAt"] = FieldValue.serverTimestamp()
    }
    try {
      if (existing.exists()) {
        docRef.update(payload).await()
      } else {
        docRef.set(payload).await()
      }
      if (profile.phoneNumber.isNotBlank()) {
        upsertPublicDirectory(
          userId = uid,
          displayName = payload["displayName"] as String,
          bravId = payload["bravId"] as String,
          phoneNumber = profile.phoneNumber,
          avatarUrl = profile.avatarUrl,
          bioStatus = profile.bioStatus,
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  private suspend fun upsertPublicDirectory(
    userId: String,
    displayName: String,
    bravId: String,
    phoneNumber: String,
    avatarUrl: String,
    bioStatus: String,
  ) {
    val normalized = normalizePhone(phoneNumber)
    if (normalized.isBlank()) return
    val hash = sha256Hex(normalized)
    val docRef = db.collection("public_directory").document(hash)
    val payload =
      mapOf(
        "userId" to userId,
        "displayName" to displayName.take(80),
        "bravId" to bravId.take(40),
        "phoneHash" to hash,
        "avatarUrl" to avatarUrl.take(100_000),
        "bioStatus" to bioStatus.take(160),
        "isOnline" to true,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload, SetOptions.merge()).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  suspend fun lookupBravUsersByPhoneHashes(hashes: List<String>): Map<String, PublicDirectoryEntry> =
    coroutineScope {
      val uid = auth.currentUser?.uid ?: return@coroutineScope emptyMap()
      val uniqueHashes = hashes.filter { it.length == 64 }.distinct().take(60)
      uniqueHashes
        .map { hash ->
          async {
            val docRef = db.collection("public_directory").document(hash)
            try {
              val snap = docRef.get().await()
              if (snap.exists()) {
                val entry =
                  snap.toObject(
                    PublicDirectoryEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                  )
                if (entry != null && entry.userId != uid) hash to entry else null
              } else {
                null
              }
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              handleFirestoreError(e, OperationType.GET, docRef.path)
              null
            }
          }
        }
        .awaitAll()
        .filterNotNull()
        .toMap()
    }

  // --- 24H STATUSES (ACTUS) ---

  fun observeActiveStatuses(userId: String): Flow<List<StatusPost>> = flow {
    val path = "statuses"
    emitAll(
      db.collection(path)
        .whereEqualTo("isActive", true)
        .snapshots()
        .map { snapshot ->
          val nowMs = System.currentTimeMillis()
          snapshot
            .toObjects(StatusPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .filter { post ->
              val expMs = post.expiresAt?.toDate()?.time ?: Long.MAX_VALUE
              expMs > nowMs
            }
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        }
        .catchFirestore(userId, OperationType.LIST, path)
    )
  }

  suspend fun publishStatus(
    authorName: String,
    authorAvatarUrl: String,
    type: String,
    textContent: String = "",
    backgroundColorHex: String = "#006B54",
    mediaDataUri: String = "",
    caption: String = "",
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val statusId = UUID.randomUUID().toString()
    val docRef = db.collection("statuses").document(statusId)
    val expiresAt = Timestamp(Date(System.currentTimeMillis() + 24L * 60L * 60L * 1000L))
    val payload =
      mapOf(
        "userId" to uid,
        "authorName" to authorName.ifBlank { "Moi" }.take(80),
        "authorAvatarUrl" to authorAvatarUrl.take(100_000),
        "type" to type,
        "textContent" to textContent.take(500),
        "backgroundColorHex" to backgroundColorHex.take(16),
        "mediaDataUri" to mediaDataUri.take(250_000),
        "caption" to caption.take(200),
        "viewCount" to 0L,
        "isActive" to true,
        "createdAt" to FieldValue.serverTimestamp(),
        "expiresAt" to expiresAt,
      )
    try {
      docRef.set(payload).await()
      statusId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun markStatusViewed(
    statusId: String,
    viewerName: String,
    reaction: String = "",
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val viewRef =
      db.collection("statuses").document(statusId).collection("views").document(uid)
    val statusRef = db.collection("statuses").document(statusId)
    try {
      val existingView = viewRef.get().await()
      val viewPayload =
        mapOf(
          "viewerId" to uid,
          "viewerName" to viewerName.ifBlank { "Contact" }.take(80),
          "reaction" to reaction.take(16),
          "viewedAt" to FieldValue.serverTimestamp(),
        )
      viewRef.set(viewPayload).await()
      if (!existingView.exists()) {
        statusRef.update("viewCount", FieldValue.increment(1L)).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, viewRef.path)
      throw e
    }
  }

  fun observeStatusViews(userId: String, statusId: String): Flow<List<StatusView>> = flow {
    val path = "statuses/$statusId/views"
    emitAll(
      db.collection("statuses")
        .document(statusId)
        .collection("views")
        .snapshots()
        .map { snap ->
          snap.toObjects(StatusView::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catchFirestore(userId, OperationType.LIST, path)
    )
  }

  suspend fun deleteStatus(statusId: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection("statuses").document(statusId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  // --- CONVERSATIONS & MESSAGES ---

  fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
    val path = "conversations"
    emitAll(
      db.collection(path)
        .whereArrayContains("memberUids", userId)
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(
              compareByDescending<Conversation> { it.isPinned }
                .thenByDescending { it.updatedAt?.toDate()?.time ?: 0L }
            )
        }
        .catchFirestore(userId, OperationType.LIST, path)
    )
  }

  suspend fun createOrGetConversation(
    title: String,
    peerUserId: String,
    peerPhone: String,
    peerAvatarUrl: String,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val members =
      if (peerUserId.isNotBlank() && peerUserId != uid) listOf(uid, peerUserId).sorted()
      else listOf(uid)
    val existing =
      db.collection("conversations")
        .whereArrayContains("memberUids", uid)
        .get()
        .await()
        .toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        .firstOrNull { conv ->
          (peerUserId.isNotBlank() && conv.peerUserId == peerUserId) ||
            (peerPhone.isNotBlank() && normalizePhone(conv.peerPhone) == normalizePhone(peerPhone))
        }
    if (existing != null) {
      return@runCatching existing.id
    }

    val convId = UUID.randomUUID().toString()
    val docRef = db.collection("conversations").document(convId)
    val payload =
      mapOf(
        "title" to title.ifBlank { peerPhone.ifBlank { "Discussion" } }.take(100),
        "peerUserId" to peerUserId.take(128),
        "peerPhone" to peerPhone.take(30),
        "peerAvatarUrl" to peerAvatarUrl.take(100_000),
        "lastMessage" to "",
        "lastMessageType" to "TEXT",
        "isPinned" to false,
        "isArchived" to false,
        "unreadCount" to 0L,
        "memberUids" to members,
        "createdBy" to uid,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
      convId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun getConversation(conversationId: String): Result<Conversation?> = runCatching {
    requireUserId()
    val docRef = db.collection("conversations").document(conversationId)
    try {
      val snap = docRef.get().await()
      if (snap.exists()) {
        snap.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      } else {
        null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      throw e
    }
  }

  fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> = flow {
    val path = "conversations/$conversationId/messages"
    emitAll(
      db.collection("conversations")
        .document(conversationId)
        .collection("messages")
        .whereArrayContains("memberUids", userId)
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.createdAt?.toDate()?.time ?: 0L }
        }
        .catchFirestore(userId, OperationType.LIST, path)
    )
  }

  suspend fun sendMessage(
    conversationId: String,
    memberUids: List<String>,
    type: String,
    text: String = "",
    attachmentName: String = "",
    attachmentMimeType: String = "",
    attachmentSizeBytes: Long = 0L,
    attachmentLocalUri: String = "",
    attachmentThumbnailBase64: String = "",
    durationMs: Long = 0L,
    deliveryStatus: String = "SENT",
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val msgId = UUID.randomUUID().toString()
    val members = if (uid in memberUids) memberUids else (memberUids + uid)
    val convRef = db.collection("conversations").document(conversationId)
    val msgRef = convRef.collection("messages").document(msgId)
    val msgPayload =
      mapOf(
        "senderId" to uid,
        "memberUids" to members,
        "type" to type,
        "text" to text.take(4000),
        "attachmentName" to attachmentName.take(200),
        "attachmentMimeType" to attachmentMimeType.take(100),
        "attachmentSizeBytes" to attachmentSizeBytes.coerceAtLeast(0L),
        "attachmentLocalUri" to attachmentLocalUri.take(1000),
        "attachmentThumbnailBase64" to attachmentThumbnailBase64.take(250_000),
        "durationMs" to durationMs.coerceAtLeast(0L),
        "deliveryStatus" to deliveryStatus,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    val preview =
      when (type) {
        "TEXT" -> text.ifBlank { "Message" }
        "IMAGE" -> if (text.isNotBlank()) "📷 $text" else "📷 Photo"
        "VIDEO" -> if (text.isNotBlank()) "🎥 $text" else "🎥 Vidéo"
        "VOICE" -> "🎤 Message vocal"
        "AUDIO" -> "🎵 Audio"
        "PDF" -> "📄 ${attachmentName.ifBlank { "Document PDF" }}"
        else -> "📎 ${attachmentName.ifBlank { "Fichier" }}"
      }.take(500)

    try {
      val batch = db.batch()
      batch.set(msgRef, msgPayload)
      batch.update(
        convRef,
        mapOf(
          "lastMessage" to preview,
          "lastMessageType" to type,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      msgId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, msgRef.path)
      throw e
    }
  }

  suspend fun retryMessage(conversationId: String, messageId: String): Result<Unit> = runCatching {
    requireUserId()
    val msgRef =
      db.collection("conversations").document(conversationId).collection("messages").document(messageId)
    try {
      msgRef.update(
        mapOf("deliveryStatus" to "SENT", "updatedAt" to FieldValue.serverTimestamp())
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
      throw e
    }
  }

  suspend fun updateConversationPinArchive(
    conversationId: String,
    isPinned: Boolean? = null,
    isArchived: Boolean? = null,
    unreadCount: Long? = null,
  ): Result<Unit> = runCatching {
    requireUserId()
    val convRef = db.collection("conversations").document(conversationId)
    val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
    isPinned?.let { updates["isPinned"] = it }
    isArchived?.let { updates["isArchived"] = it }
    unreadCount?.let { updates["unreadCount"] = it.coerceAtLeast(0L) }
    try {
      convRef.update(updates).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, convRef.path)
      throw e
    }
  }

  suspend fun deleteConversation(conversationId: String): Result<Unit> = runCatching {
    requireUserId()
    val convRef = db.collection("conversations").document(conversationId)
    try {
      convRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, convRef.path)
      throw e
    }
  }

  // --- CALL LOGS, ACTIVITY LOGS, DEVICES ---

  fun observeCallLogs(userId: String): Flow<List<CallLog>> = flow {
    val path = "users/$userId/call_logs"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("call_logs")
        .snapshots()
        .map { snap ->
          snap
            .toObjects(CallLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        }
        .catchFirestore(userId, OperationType.LIST, path)
    )
  }

  suspend fun recordCallLog(
    peerName: String,
    peerPhone: String,
    peerAvatarUrl: String,
    callType: String,
    direction: String,
    durationSeconds: Long,
    noiseSuppressionUsed: Boolean,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val callId = UUID.randomUUID().toString()
    val docRef = db.collection("users").document(uid).collection("call_logs").document(callId)
    val payload =
      mapOf(
        "userId" to uid,
        "peerName" to peerName.ifBlank { "Contact" }.take(80),
        "peerPhone" to peerPhone.take(30),
        "peerAvatarUrl" to peerAvatarUrl.take(100_000),
        "callType" to callType,
        "direction" to direction,
        "durationSeconds" to durationSeconds.coerceAtLeast(0L),
        "noiseSuppressionUsed" to noiseSuppressionUsed,
        "createdAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  fun observeActivityLogs(userId: String): Flow<List<ActivityLog>> = flow {
    val path = "users/$userId/activity_logs"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("activity_logs")
        .snapshots()
        .map { snap ->
          snap
            .toObjects(ActivityLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        }
        .catchFirestore(userId, OperationType.LIST, path)
    )
  }

  suspend fun recordActivity(actionType: String, title: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val activityId = UUID.randomUUID().toString()
    val docRef =
      db.collection("users").document(uid).collection("activity_logs").document(activityId)
    val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android" }.take(100)
    val payload =
      mapOf(
        "userId" to uid,
        "actionType" to actionType,
        "title" to title.take(120),
        "deviceModel" to deviceModel,
        "createdAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  fun observeDevices(userId: String): Flow<List<ConnectedDevice>> = flow {
    val path = "users/$userId/devices"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("devices")
        .snapshots()
        .map { snap ->
          snap
            .toObjects(
              ConnectedDevice::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.lastActiveAt?.toDate()?.time ?: 0L }
        }
        .catchFirestore(userId, OperationType.LIST, path)
    )
  }

  suspend fun registerCurrentDevice(deviceId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val safeId = deviceId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "android_main" }
    val docRef = db.collection("users").document(uid).collection("devices").document(safeId)
    val existing = docRef.get().await()
    val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Appareil Android" }.take(100)
    val osVersion = "Android ${Build.VERSION.RELEASE}".take(40)
    try {
      if (existing.exists()) {
        docRef.update(
          mapOf(
            "deviceName" to deviceName,
            "osVersion" to osVersion,
            "isActive" to true,
            "lastActiveAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        docRef.set(
          mapOf(
            "userId" to uid,
            "deviceName" to deviceName,
            "osVersion" to osVersion,
            "isActive" to true,
            "lastActiveAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  suspend fun revokeDevice(deviceId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("devices").document(deviceId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  companion object {
    fun normalizePhone(raw: String): String {
      val digits = raw.filter { it.isDigit() || it == '+' }
      return if (digits.startsWith("00")) "+${digits.drop(2)}" else digits
    }

    fun sha256Hex(input: String): String {
      val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
      return digest.joinToString("") { "%02x".format(it) }
    }
  }
}
