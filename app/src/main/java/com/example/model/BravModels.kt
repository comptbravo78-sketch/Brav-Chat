package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val firstName: String = "",
  val lastName: String = "",
  val bravId: String = "",
  val phoneNumber: String = "",
  val countryCode: String = "+225",
  val countryIso: String = "CI",
  val bioStatus: String = "Disponible sur BRAV CHAT",
  val avatarUrl: String = "",
  val biometricEnabled: Boolean = false,
  val pinHash: String = "",
  val privacyLastSeen: String = "CONTACTS",
  val readReceipts: Boolean = true,
  val notificationsEnabled: Boolean = true,
  val themeMode: String = "LIGHT",
  val autoCompressMedia: Boolean = true,
  val noiseSuppressionEnabled: Boolean = true,
  val hdVideoCalls: Boolean = true,
  val aiAutoReplyEnabled: Boolean = false,
  val aiAutoReplyTone: String = "Professionnel",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PublicDirectoryEntry(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val bravId: String = "",
  val phoneHash: String = "",
  val avatarUrl: String = "",
  val bioStatus: String = "",
  val isOnline: Boolean = true,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class StatusPost(
  @DocumentId val id: String = "",
  val userId: String = "",
  val authorName: String = "",
  val authorAvatarUrl: String = "",
  val type: String = "TEXT",
  val textContent: String = "",
  val backgroundColorHex: String = "#006B54",
  val mediaDataUri: String = "",
  val caption: String = "",
  val viewCount: Long = 0L,
  val isActive: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  val expiresAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class StatusView(
  @DocumentId val id: String = "",
  val viewerId: String = "",
  val viewerName: String = "",
  val reaction: String = "",
  @ServerTimestamp val viewedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Conversation(
  @DocumentId val id: String = "",
  val title: String = "",
  val peerUserId: String = "",
  val peerPhone: String = "",
  val peerAvatarUrl: String = "",
  val lastMessage: String = "",
  val lastMessageType: String = "TEXT",
  val isPinned: Boolean = false,
  val isArchived: Boolean = false,
  val unreadCount: Long = 0L,
  val memberUids: List<String> = emptyList(),
  val createdBy: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ChatMessage(
  @DocumentId val id: String = "",
  val senderId: String = "",
  val memberUids: List<String> = emptyList(),
  val type: String = "TEXT",
  val text: String = "",
  val attachmentName: String = "",
  val attachmentMimeType: String = "",
  val attachmentSizeBytes: Long = 0L,
  val attachmentLocalUri: String = "",
  val attachmentThumbnailBase64: String = "",
  val durationMs: Long = 0L,
  val deliveryStatus: String = "SENT",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CallLog(
  @DocumentId val id: String = "",
  val userId: String = "",
  val peerName: String = "",
  val peerPhone: String = "",
  val peerAvatarUrl: String = "",
  val callType: String = "AUDIO",
  val direction: String = "OUTGOING",
  val durationSeconds: Long = 0L,
  val noiseSuppressionUsed: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ActivityLog(
  @DocumentId val id: String = "",
  val userId: String = "",
  val actionType: String = "LOGIN",
  val title: String = "",
  val deviceModel: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ConnectedDevice(
  @DocumentId val id: String = "",
  val userId: String = "",
  val deviceName: String = "",
  val osVersion: String = "",
  val isActive: Boolean = true,
  @ServerTimestamp val lastActiveAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

data class LocalContact(
  val id: String,
  val name: String,
  val phoneNumber: String,
  val normalizedPhone: String,
  val phoneHash: String,
  val photoUri: String?,
  val bravUser: PublicDirectoryEntry? = null,
)

data class SmsMessageItem(
  val id: Long,
  val threadId: Long,
  val address: String,
  val body: String,
  val dateMs: Long,
  val isIncoming: Boolean,
  val isRead: Boolean,
  val isMms: Boolean = false,
  val attachmentUri: String? = null,
  val attachmentMimeType: String? = null,
  val sendFailed: Boolean = false,
)

data class SmsThread(
  val threadId: Long,
  val address: String,
  val contactName: String,
  val contactPhotoUri: String?,
  val lastBody: String,
  val lastDateMs: Long,
  val unreadCount: Int,
  val isPinned: Boolean = false,
  val isArchived: Boolean = false,
)

data class CountryDialInfo(
  val isoCode: String,
  val name: String,
  val dialCode: String,
  val flagEmoji: String,
)

data class MediaAttachmentDraft(
  val type: String,
  val displayName: String,
  val mimeType: String,
  val sizeBytes: Long,
  val localUri: String,
  val thumbnailBase64: String = "",
  val durationMs: Long = 0L,
  val wasCompressed: Boolean = false,
)

data class AiMessageItem(
  val id: String,
  val isFromUser: Boolean,
  val text: String,
  val imageBase64: String = "",
  val timestampMs: Long = System.currentTimeMillis(),
  val isStreaming: Boolean = false,
  val isError: Boolean = false,
)

data class YouTubeTrack(
  val videoId: String,
  val title: String,
  val channelTitle: String,
  val thumbnailUrl: String,
  val publishedAt: String = "",
)
