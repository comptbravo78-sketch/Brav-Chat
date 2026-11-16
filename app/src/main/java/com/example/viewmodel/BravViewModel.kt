package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.example.ai.BravAiAssistant
import com.example.call.AudioDspCapabilities
import com.example.call.AudioProcessingEngine
import com.example.contacts.ContactsRepository
import com.example.data.BravRepository
import com.example.media.MediaHelper
import com.example.model.ActivityLog
import com.example.model.AiMessageItem
import com.example.model.CallLog
import com.example.model.ChatMessage
import com.example.model.ConnectedDevice
import com.example.model.Conversation
import com.example.model.LocalContact
import com.example.model.MediaAttachmentDraft
import com.example.model.SmsMessageItem
import com.example.model.SmsThread
import com.example.model.StatusPost
import com.example.model.StatusView
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.YouTubeTrack
import com.example.music.BravMusicPlayerHost
import com.example.music.ClipDisplayMode
import com.example.music.YouTubeMusicRepository
import com.example.security.BiometricAuthManager
import com.example.sms.SmsRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class CallPhase {
  DIALING,
  CONNECTED,
}

data class ActiveCallSession(
  val peerName: String,
  val peerPhone: String,
  val peerAvatarUrl: String,
  val isVideo: Boolean,
  val phase: CallPhase = CallPhase.DIALING,
  val elapsedSeconds: Long = 0L,
  val isMuted: Boolean = false,
  val isSpeakerOn: Boolean = false,
  val isCameraEnabled: Boolean = isVideo,
  val isFrontCamera: Boolean = true,
  val noiseSuppressionEnabled: Boolean = true,
)

class BravViewModel(
  private val appContext: Context,
  private val bravRepository: BravRepository,
  private val smsRepository: SmsRepository,
  private val contactsRepository: ContactsRepository,
  private val audioEngine: AudioProcessingEngine,
  private val aiAssistant: BravAiAssistant,
  private val youTubeRepository: YouTubeMusicRepository,
  val currentUserId: String,
  private val googleDisplayName: String,
  private val googleAvatarUrl: String,
) : ViewModel() {

  private fun <T> Flow<T>.toUiStateFlow(errorFallback: String): StateFlow<UiState<T>> =
    map<T, UiState<T>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, errorFallback, rootError)
        emit(UiState.Error(rootError.message ?: errorFallback))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = UiState.Loading,
      )

  val userProfile: StateFlow<UiState<UserProfile?>> =
    bravRepository.observeUserProfile(currentUserId).toUiStateFlow("Erreur de chargement du profil")

  val conversations: StateFlow<UiState<List<Conversation>>> =
    bravRepository
      .observeConversations(currentUserId)
      .toUiStateFlow("Erreur de chargement des discussions")

  val statuses: StateFlow<UiState<List<StatusPost>>> =
    bravRepository
      .observeActiveStatuses(currentUserId)
      .toUiStateFlow("Erreur de chargement des statuts")

  val callLogs: StateFlow<UiState<List<CallLog>>> =
    bravRepository.observeCallLogs(currentUserId).toUiStateFlow("Erreur de chargement des appels")

  val activityLogs: StateFlow<UiState<List<ActivityLog>>> =
    bravRepository
      .observeActivityLogs(currentUserId)
      .toUiStateFlow("Erreur de chargement de l'activité")

  val devices: StateFlow<UiState<List<ConnectedDevice>>> =
    bravRepository
      .observeDevices(currentUserId)
      .toUiStateFlow("Erreur de chargement des appareils")

  private val _syncedContacts = MutableStateFlow<List<LocalContact>>(emptyList())
  val syncedContacts: StateFlow<List<LocalContact>> = _syncedContacts.asStateFlow()

  private val _contactsPermissionGranted = MutableStateFlow(contactsRepository.hasContactsPermission())
  val contactsPermissionGranted: StateFlow<Boolean> = _contactsPermissionGranted.asStateFlow()

  private val _smsThreads = MutableStateFlow<List<SmsThread>>(emptyList())
  val smsThreads: StateFlow<List<SmsThread>> = _smsThreads.asStateFlow()

  private val _isDefaultSmsApp = MutableStateFlow(smsRepository.isDefaultSmsApp())
  val isDefaultSmsApp: StateFlow<Boolean> = _isDefaultSmsApp.asStateFlow()

  private val _hasSmsPermission = MutableStateFlow(smsRepository.hasSmsPermissions())
  val hasSmsPermission: StateFlow<Boolean> = _hasSmsPermission.asStateFlow()

  private val _sessionUnlocked = MutableStateFlow(false)
  val sessionUnlocked: StateFlow<Boolean> = _sessionUnlocked.asStateFlow()

  private val _justRegisteredPromptBiometric = MutableStateFlow(false)
  val justRegisteredPromptBiometric: StateFlow<Boolean> = _justRegisteredPromptBiometric.asStateFlow()

  private val _activeCall = MutableStateFlow<ActiveCallSession?>(null)
  val activeCall: StateFlow<ActiveCallSession?> = _activeCall.asStateFlow()

  val dspCapabilities: StateFlow<AudioDspCapabilities> = audioEngine.dspState

  // --- GEMINI AI ASSISTANT STATE ---
  private val _aiMessages = MutableStateFlow<List<AiMessageItem>>(emptyList())
  val aiMessages: StateFlow<List<AiMessageItem>> = _aiMessages.asStateFlow()

  private val _isAiStreaming = MutableStateFlow(false)
  val isAiStreaming: StateFlow<Boolean> = _isAiStreaming.asStateFlow()

  private val _aiError = MutableStateFlow<String?>(null)
  val aiError: StateFlow<String?> = _aiError.asStateFlow()

  private val autoRepliedMessageIds = mutableSetOf<String>()

  // --- YOUTUBE MUSIC SPACE STATE ---
  val musicHost: BravMusicPlayerHost = BravMusicPlayerHost.getInstance(appContext)

  val isYouTubeApiConfigured: Boolean
    get() = youTubeRepository.isApiKeyConfigured

  private val _musicTracks =
    MutableStateFlow<UiState<List<YouTubeTrack>>>(UiState.Success(emptyList()))
  val musicTracks: StateFlow<UiState<List<YouTubeTrack>>> = _musicTracks.asStateFlow()

  private val _musicQuery = MutableStateFlow("")
  val musicQuery: StateFlow<String> = _musicQuery.asStateFlow()

  val currentTrack: StateFlow<YouTubeTrack?> = musicHost.currentTrack
  val isMusicPlaying: StateFlow<Boolean> = musicHost.isPlaying
  val isMusicBuffering: StateFlow<Boolean> = musicHost.isBuffering
  val clipDisplayMode: StateFlow<ClipDisplayMode> = musicHost.clipDisplayMode
  val autoPlayNextEnabled: StateFlow<Boolean> = musicHost.autoPlayNextEnabled
  val musicPlayerError: StateFlow<String?> = musicHost.playerError

  private var contactsJob: Job? = null
  private var smsJob: Job? = null
  private var callTimerJob: Job? = null
  private var aiStreamJob: Job? = null
  private var musicFetchJob: Job? = null
  private var musicTrendingLoaded = false

  init {
    registerCurrentDeviceSession()
    refreshContactsSync()
    refreshSmsSync()
  }

  private fun registerCurrentDeviceSession() {
    viewModelScope.launch {
      val deviceKey = "${Build.MANUFACTURER}_${Build.MODEL}".replace(" ", "_")
      bravRepository.registerCurrentDevice(deviceKey)
      bravRepository.recordActivity("LOGIN", "Connexion à la session BRAV CHAT")
    }
  }

  fun refreshContactsSync() {
    _contactsPermissionGranted.value = contactsRepository.hasContactsPermission()
    contactsJob?.cancel()
    if (_contactsPermissionGranted.value) {
      contactsJob =
        viewModelScope.launch {
          contactsRepository.observeSyncedContacts().collect { list ->
            _syncedContacts.value = list
          }
        }
    }
  }

  fun refreshSmsSync() {
    _isDefaultSmsApp.value = smsRepository.isDefaultSmsApp()
    _hasSmsPermission.value = smsRepository.hasSmsPermissions()
    smsJob?.cancel()
    if (_hasSmsPermission.value) {
      smsJob =
        viewModelScope.launch {
          smsRepository.observeSmsThreads().collect { threads ->
            _smsThreads.value = threads
          }
        }
    }
  }

  fun onDefaultSmsRoleChanged() {
    val isDefault = smsRepository.isDefaultSmsApp()
    _isDefaultSmsApp.value = isDefault
    refreshSmsSync()
    if (isDefault) {
      viewModelScope.launch {
        bravRepository.recordActivity("SMS_DEFAULT", "BRAV CHAT défini comme application SMS par défaut")
      }
    }
  }

  fun unlockBiometricSession() {
    _sessionUnlocked.value = true
  }

  fun dismissFirstLoginBiometricPrompt() {
    _justRegisteredPromptBiometric.value = false
  }

  suspend fun completeOnboardingRegistration(
    firstName: String,
    lastName: String,
    phoneNumber: String,
    countryCode: String,
    countryIso: String,
  ): Result<Unit> {
    val fullName =
      listOf(firstName.trim(), lastName.trim())
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { googleDisplayName.ifBlank { "Utilisateur BRAV" } }
    val normalizedPhone =
      if (phoneNumber.trim().startsWith("+")) phoneNumber.trim()
      else "$countryCode${phoneNumber.trim().trimStart('0')}"
    val cleanHandle =
      "@${fullName.lowercase().replace(Regex("[^a-z0-9]"), "_").take(18)}_${currentUserId.take(4)}"

    val profile =
      UserProfile(
        userId = currentUserId,
        displayName = fullName,
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        bravId = cleanHandle,
        phoneNumber = normalizedPhone,
        countryCode = countryCode,
        countryIso = countryIso,
        bioStatus = "Disponible sur BRAV CHAT",
        avatarUrl = googleAvatarUrl,
        biometricEnabled = false,
      )
    val res = bravRepository.createOrUpdateUserProfile(profile)
    if (res.isSuccess) {
      _sessionUnlocked.value = true
      _justRegisteredPromptBiometric.value = true
      bravRepository.recordActivity("PROFILE_UPDATE", "Inscription finalisée ($countryCode)")
    }
    return res
  }

  fun enableBiometricAndPin(pin: String, enable: Boolean) {
    val current = (userProfile.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      val newPinHash =
        if (pin.isNotBlank()) BiometricAuthManager.hashPin(currentUserId, pin) else current.pinHash
      val updated = current.copy(biometricEnabled = enable, pinHash = newPinHash)
      bravRepository.createOrUpdateUserProfile(updated)
      bravRepository.recordActivity(
        "BIOMETRIC",
        if (enable) "Authentification biométrique activée" else "Authentification biométrique désactivée",
      )
      _justRegisteredPromptBiometric.value = false
    }
  }

  fun verifyPinFallback(pin: String): Boolean {
    val current = (userProfile.value as? UiState.Success)?.data ?: return false
    val ok = BiometricAuthManager.verifyPin(currentUserId, pin, current.pinHash)
    if (ok) _sessionUnlocked.value = true
    return ok
  }

  // --- PROFILE PHOTO ---

  suspend fun updateProfilePhoto(uri: Uri?): Result<Unit> {
    val current =
      (userProfile.value as? UiState.Success)?.data
        ?: return Result.failure(IllegalStateException("Profil introuvable"))
    val newAvatar =
      if (uri == null) {
        ""
      } else {
        MediaHelper.buildAvatarDataUri(appContext, uri)
          ?: return Result.failure(IllegalStateException("Impossible de traiter la photo choisie"))
      }
    val res = bravRepository.createOrUpdateUserProfile(current.copy(avatarUrl = newAvatar))
    if (res.isSuccess) {
      bravRepository.recordActivity(
        "PROFILE_UPDATE",
        if (uri == null) "Photo de profil supprimée" else "Photo de profil mise à jour",
      )
    }
    return res
  }

  // --- GEMINI AI DIRECT CHAT & AUTO-REPLY ---

  fun sendAiPrompt(prompt: String, imageUri: Uri? = null) {
    val cleanPrompt = prompt.trim()
    if (cleanPrompt.isBlank() && imageUri == null) return

    aiStreamJob?.cancel()
    _aiError.value = null

    aiStreamJob =
      viewModelScope.launch {
        _isAiStreaming.value = true
        val bitmap = imageUri?.let { MediaHelper.decodeBitmapFromUri(appContext, it, maxDim = 960) }
        val previewBase64 =
          bitmap?.let { bmp ->
            val baos = ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, baos)
            "data:image/jpeg;base64," +
              android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
          } ?: ""

        val userMsg =
          AiMessageItem(
            id = UUID.randomUUID().toString(),
            isFromUser = true,
            text = cleanPrompt.ifBlank { "Analyser cette image" },
            imageBase64 = previewBase64,
          )
        val assistantMsgId = UUID.randomUUID().toString()
        val placeholderMsg =
          AiMessageItem(
            id = assistantMsgId,
            isFromUser = false,
            text = "",
            isStreaming = true,
          )
        val historyBeforeTurn = _aiMessages.value
        _aiMessages.value = historyBeforeTurn + userMsg + placeholderMsg

        try {
          aiAssistant.streamChatReply(historyBeforeTurn, cleanPrompt, bitmap).collect { partial ->
            _aiMessages.value =
              _aiMessages.value.map { item ->
                if (item.id == assistantMsgId) {
                  item.copy(text = partial, isStreaming = true)
                } else {
                  item
                }
              }
          }
          _aiMessages.value =
            _aiMessages.value.map { item ->
              if (item.id == assistantMsgId) item.copy(isStreaming = false) else item
            }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          val msg = e.localizedMessage ?: "Erreur lors de la réponse Gemini."
          _aiError.value = msg
          _aiMessages.value =
            _aiMessages.value.map { item ->
              if (item.id == assistantMsgId) {
                item.copy(text = msg, isStreaming = false, isError = true)
              } else {
                item
              }
            }
        } finally {
          _isAiStreaming.value = false
        }
      }
  }

  fun clearAiChat() {
    aiStreamJob?.cancel()
    _isAiStreaming.value = false
    _aiError.value = null
    _aiMessages.value = emptyList()
  }

  fun retryLastAiPrompt() {
    val current = _aiMessages.value
    val lastUserMsg = current.lastOrNull { it.isFromUser } ?: return
    // Remove trailing failed assistant message(s) and the last user message so sendAiPrompt re-adds cleanly
    val idx = current.indexOfLast { it.id == lastUserMsg.id }
    if (idx >= 0) {
      _aiMessages.value = current.take(idx)
    }
    sendAiPrompt(lastUserMsg.text, imageUri = null)
  }

  suspend fun generateSmartReply(
    peerName: String,
    recentTranscript: List<String>,
    isAutoResponder: Boolean = false,
  ): Result<String> {
    val tone = (userProfile.value as? UiState.Success)?.data?.aiAutoReplyTone ?: "Professionnel"
    return aiAssistant.generateSmartReply(
      peerName = peerName,
      recentTranscript = recentTranscript,
      tone = tone,
      isAutoResponder = isAutoResponder,
    )
  }

  fun checkAndTriggerAutoReply(
    conversationId: String,
    peerTitle: String,
    memberUids: List<String>,
    messages: List<ChatMessage>,
  ) {
    val profile = (userProfile.value as? UiState.Success)?.data ?: return
    if (!profile.aiAutoReplyEnabled) return

    val lastMsg = messages.lastOrNull() ?: return
    if (lastMsg.senderId == currentUserId) return
    if (lastMsg.type != "TEXT" || lastMsg.text.isBlank()) return
    if (lastMsg.text.startsWith("🤖")) return
    if (!autoRepliedMessageIds.add(lastMsg.id)) return

    viewModelScope.launch {
      val transcript =
        messages.takeLast(8).map { msg ->
          val who = if (msg.senderId == currentUserId) "Moi" else peerTitle
          "$who : ${msg.text}"
        }
      val replyRes =
        aiAssistant.generateSmartReply(
          peerName = peerTitle,
          recentTranscript = transcript,
          tone = profile.aiAutoReplyTone,
          isAutoResponder = true,
        )
      replyRes.fold(
        onSuccess = { replyText ->
          bravRepository.sendMessage(
            conversationId = conversationId,
            memberUids = memberUids,
            type = "TEXT",
            text = "🤖 [Auto-IA] $replyText",
          )
        },
        onFailure = {
          autoRepliedMessageIds.remove(lastMsg.id)
        },
      )
    }
  }

  // --- YOUTUBE MUSIC SPACE ---

  fun ensureTrendingMusicLoaded() {
    if (musicTrendingLoaded) return
    musicTrendingLoaded = true
    loadTrendingMusic()
  }

  fun loadTrendingMusic(regionCode: String = "FR") {
    musicFetchJob?.cancel()
    _musicQuery.value = ""
    musicFetchJob =
      viewModelScope.launch {
        _musicTracks.value = UiState.Loading
        val res = youTubeRepository.fetchTrendingMusic(regionCode)
        _musicTracks.value =
          res.fold(
            onSuccess = { tracks ->
              if (musicHost.queue.value.isEmpty()) {
                musicHost.updateQueue(tracks)
              }
              UiState.Success(tracks)
            },
            onFailure = { UiState.Error(it.localizedMessage ?: "Erreur YouTube") },
          )
      }
  }

  fun searchYouTubeMusic(query: String) {
    _musicQuery.value = query
    musicFetchJob?.cancel()
    musicFetchJob =
      viewModelScope.launch {
        _musicTracks.value = UiState.Loading
        val res = youTubeRepository.searchMusic(query)
        _musicTracks.value =
          res.fold(
            onSuccess = { tracks ->
              if (musicHost.queue.value.isEmpty()) {
                musicHost.updateQueue(tracks)
              }
              UiState.Success(tracks)
            },
            onFailure = { UiState.Error(it.localizedMessage ?: "Erreur YouTube") },
          )
      }
  }

  fun playTrack(track: YouTubeTrack) {
    val activeList = (_musicTracks.value as? UiState.Success)?.data
    musicHost.playTrack(track, queueOverride = activeList)
  }

  fun playTrackFromUrl(urlOrId: String): Boolean {
    val videoId = YouTubeMusicRepository.extractVideoId(urlOrId) ?: return false
    val track =
      YouTubeTrack(
        videoId = videoId,
        title = "Lecture YouTube ($videoId)",
        channelTitle = "YouTube Music",
        thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
      )
    val activeList = (_musicTracks.value as? UiState.Success)?.data
    musicHost.playTrack(track, queueOverride = activeList)
    return true
  }

  fun togglePlayPauseMusic() {
    musicHost.togglePlayPause()
  }

  fun setClipDisplayMode(mode: ClipDisplayMode) {
    musicHost.setClipDisplayMode(mode)
  }

  fun toggleAutoPlayNext() {
    musicHost.toggleAutoPlayNext()
  }

  fun playNextTrack() {
    musicHost.playNext(fromAutoPlay = false)
  }

  fun playPreviousTrack() {
    musicHost.playPrevious()
  }

  fun stopMusic() {
    musicHost.stopPlayback()
  }

  // --- CONVERSATIONS & MESSAGES ---

  fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
    bravRepository.observeMessages(currentUserId, conversationId)

  suspend fun getOrCreateConversation(
    title: String,
    peerUserId: String,
    peerPhone: String,
    peerAvatarUrl: String,
  ): Result<String> =
    bravRepository.createOrGetConversation(title, peerUserId, peerPhone, peerAvatarUrl)

  suspend fun getConversation(conversationId: String): Result<Conversation?> =
    bravRepository.getConversation(conversationId)

  suspend fun sendChatMessage(
    conversationId: String,
    memberUids: List<String>,
    text: String,
    draft: MediaAttachmentDraft? = null,
  ): Result<String> {
    val type = draft?.type ?: "TEXT"
    return bravRepository.sendMessage(
      conversationId = conversationId,
      memberUids = memberUids,
      type = type,
      text = text,
      attachmentName = draft?.displayName ?: "",
      attachmentMimeType = draft?.mimeType ?: "",
      attachmentSizeBytes = draft?.sizeBytes ?: 0L,
      attachmentLocalUri = draft?.localUri ?: "",
      attachmentThumbnailBase64 = draft?.thumbnailBase64 ?: "",
      durationMs = draft?.durationMs ?: 0L,
    )
  }

  fun retryMessage(conversationId: String, messageId: String) {
    viewModelScope.launch { bravRepository.retryMessage(conversationId, messageId) }
  }

  fun togglePinConversation(conversation: Conversation) {
    viewModelScope.launch {
      bravRepository.updateConversationPinArchive(
        conversationId = conversation.id,
        isPinned = !conversation.isPinned,
      )
    }
  }

  fun toggleArchiveConversation(conversation: Conversation) {
    viewModelScope.launch {
      bravRepository.updateConversationPinArchive(
        conversationId = conversation.id,
        isArchived = !conversation.isArchived,
      )
    }
  }

  fun deleteConversation(conversationId: String) {
    viewModelScope.launch { bravRepository.deleteConversation(conversationId) }
  }

  // --- SMS / MMS ---

  suspend fun loadSmsMessages(threadId: Long, address: String): List<SmsMessageItem> {
    smsRepository.markThreadRead(threadId)
    return smsRepository.loadSmsMessagesForThread(threadId, address)
  }

  suspend fun sendSms(
    destination: String,
    body: String,
    attachmentUri: Uri? = null,
    attachmentMimeType: String? = null,
  ): Result<Unit> = smsRepository.sendSms(destination, body, attachmentUri, attachmentMimeType)

  fun togglePinSms(threadId: Long) {
    viewModelScope.launch { smsRepository.togglePinThread(threadId) }
  }

  fun toggleArchiveSms(threadId: Long) {
    viewModelScope.launch { smsRepository.toggleArchiveThread(threadId) }
  }

  fun deleteSms(threadId: Long) {
    viewModelScope.launch { smsRepository.deleteThread(threadId) }
  }

  // --- 24H STATUSES (ACTUS) ---

  suspend fun publishTextStatus(text: String, bgHex: String): Result<String> {
    val profile = (userProfile.value as? UiState.Success)?.data
    val authorName = profile?.displayName ?: googleDisplayName.ifBlank { "Moi" }
    val avatar = profile?.avatarUrl ?: googleAvatarUrl
    return bravRepository.publishStatus(
      authorName = authorName,
      authorAvatarUrl = avatar,
      type = "TEXT",
      textContent = text,
      backgroundColorHex = bgHex,
    )
  }

  suspend fun publishMediaStatus(draft: MediaAttachmentDraft, caption: String): Result<String> {
    val profile = (userProfile.value as? UiState.Success)?.data
    val authorName = profile?.displayName ?: googleDisplayName.ifBlank { "Moi" }
    val avatar = profile?.avatarUrl ?: googleAvatarUrl
    return bravRepository.publishStatus(
      authorName = authorName,
      authorAvatarUrl = avatar,
      type = if (draft.type == "VIDEO") "VIDEO" else "IMAGE",
      mediaDataUri = draft.thumbnailBase64.ifBlank { draft.localUri },
      caption = caption,
    )
  }

  fun markStatusViewed(statusId: String, reaction: String = "") {
    val profile = (userProfile.value as? UiState.Success)?.data
    val viewerName = profile?.displayName ?: googleDisplayName.ifBlank { "Contact" }
    viewModelScope.launch { bravRepository.markStatusViewed(statusId, viewerName, reaction) }
  }

  fun observeStatusViews(statusId: String): Flow<List<StatusView>> =
    bravRepository.observeStatusViews(currentUserId, statusId)

  fun deleteStatus(statusId: String) {
    viewModelScope.launch { bravRepository.deleteStatus(statusId) }
  }

  // --- AUDIO / VIDEO CALLS & NOISE SUPPRESSION ---

  fun startCall(peerName: String, peerPhone: String, peerAvatarUrl: String, isVideo: Boolean) {
    val profile = (userProfile.value as? UiState.Success)?.data
    val nsEnabled = profile?.noiseSuppressionEnabled ?: true
    _activeCall.value =
      ActiveCallSession(
        peerName = peerName,
        peerPhone = peerPhone,
        peerAvatarUrl = peerAvatarUrl,
        isVideo = isVideo,
        phase = CallPhase.DIALING,
        isSpeakerOn = isVideo,
        isCameraEnabled = isVideo,
        noiseSuppressionEnabled = nsEnabled,
      )
    audioEngine.startCallAudioPipeline(
      scope = viewModelScope,
      enableNoiseSuppression = nsEnabled,
      speakerOn = isVideo,
    )
    callTimerJob?.cancel()
    callTimerJob =
      viewModelScope.launch {
        delay(1800L)
        _activeCall.value = _activeCall.value?.copy(phase = CallPhase.CONNECTED)
        while (isActive && _activeCall.value != null) {
          delay(1000L)
          _activeCall.value =
            _activeCall.value?.let { it.copy(elapsedSeconds = it.elapsedSeconds + 1L) }
        }
      }
  }

  fun toggleCallMute() {
    val current = _activeCall.value ?: return
    val next = !current.isMuted
    audioEngine.setMuted(next)
    _activeCall.value = current.copy(isMuted = next)
  }

  fun toggleCallSpeaker() {
    val current = _activeCall.value ?: return
    val next = !current.isSpeakerOn
    audioEngine.setSpeakerphone(next)
    _activeCall.value = current.copy(isSpeakerOn = next)
  }

  fun toggleCallCamera() {
    val current = _activeCall.value ?: return
    _activeCall.value = current.copy(isCameraEnabled = !current.isCameraEnabled)
  }

  fun switchCallCamera() {
    val current = _activeCall.value ?: return
    _activeCall.value = current.copy(isFrontCamera = !current.isFrontCamera)
  }

  fun toggleCallNoiseSuppression() {
    val current = _activeCall.value ?: return
    val next = !current.noiseSuppressionEnabled
    audioEngine.setNoiseSuppression(next)
    _activeCall.value = current.copy(noiseSuppressionEnabled = next)
  }

  fun endCall() {
    val session = _activeCall.value ?: return
    callTimerJob?.cancel()
    callTimerJob = null
    audioEngine.stopCallAudioPipeline()
    _activeCall.value = null
    viewModelScope.launch {
      bravRepository.recordCallLog(
        peerName = session.peerName,
        peerPhone = session.peerPhone,
        peerAvatarUrl = session.peerAvatarUrl,
        callType = if (session.isVideo) "VIDEO" else "AUDIO",
        direction = if (session.phase == CallPhase.CONNECTED) "OUTGOING" else "MISSED",
        durationSeconds = session.elapsedSeconds,
        noiseSuppressionUsed = session.noiseSuppressionEnabled,
      )
      bravRepository.recordActivity(
        "CALL",
        "${if (session.isVideo) "Appel vidéo" else "Appel audio"} avec ${session.peerName}",
      )
    }
  }

  // --- PROFILE & SETTINGS ---

  suspend fun updateUserProfile(updated: UserProfile, logTitle: String): Result<Unit> {
    val res = bravRepository.createOrUpdateUserProfile(updated)
    if (res.isSuccess) {
      val type = if (logTitle.contains("IA", ignoreCase = true)) "AI_ASSISTANT" else "PROFILE_UPDATE"
      bravRepository.recordActivity(type, logTitle)
    }
    return res
  }

  fun revokeDevice(deviceId: String) {
    viewModelScope.launch {
      bravRepository.revokeDevice(deviceId)
      bravRepository.recordActivity("DEVICE_SESSION", "Session appareil révoquée")
    }
  }

  override fun onCleared() {
    super.onCleared()
    audioEngine.stopCallAudioPipeline()
  }

  companion object {
    private const val TAG = "BravViewModel"
    private const val STOP_TIMEOUT_MS = 5000L

    fun createFactory(
      appContext: Context,
      bravRepository: BravRepository,
      currentUserId: String,
      googleDisplayName: String,
      googleAvatarUrl: String,
    ) =
      androidx.lifecycle.viewmodel.viewModelFactory {
        initializer {
          BravViewModel(
            appContext = appContext,
            bravRepository = bravRepository,
            smsRepository = SmsRepository(appContext),
            contactsRepository = ContactsRepository(appContext, bravRepository),
            audioEngine = AudioProcessingEngine(appContext),
            aiAssistant = BravAiAssistant(),
            youTubeRepository = YouTubeMusicRepository(),
            currentUserId = currentUserId,
            googleDisplayName = googleDisplayName,
            googleAvatarUrl = googleAvatarUrl,
          )
        }
      }
  }
}
