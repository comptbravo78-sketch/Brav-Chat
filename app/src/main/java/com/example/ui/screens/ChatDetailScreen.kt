package com.example.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.media.MediaHelper
import com.example.media.VoiceNotePlayer
import com.example.media.VoiceNoteRecorder
import com.example.model.ChatMessage
import com.example.model.MediaAttachmentDraft
import com.example.model.SmsMessageItem
import com.example.ui.components.AttachmentPickerSheet
import com.example.ui.components.BravAvatar
import com.example.ui.components.ChatWatermarkBackground
import com.example.ui.theme.LocalBravColors
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
  title: String,
  peerPhone: String,
  peerAvatarUrl: String?,
  isSmsMode: Boolean,
  currentUserId: String,
  cloudMessages: List<ChatMessage>,
  smsMessages: List<SmsMessageItem>,
  autoCompressMedia: Boolean,
  aiAutoReplyEnabled: Boolean = false,
  onGenerateAiReply: suspend (recentTranscript: List<String>) -> Result<String> = {
    Result.failure(IllegalStateException("Assistant IA indisponible"))
  },
  onBack: () -> Unit,
  onStartCall: (isVideo: Boolean) -> Unit,
  onSendCloudMessage: suspend (text: String, draft: MediaAttachmentDraft?) -> Result<String>,
  onRetryCloudMessage: (messageId: String) -> Unit,
  onSendSms: suspend (body: String, attachmentUri: Uri?, mimeType: String?) -> Result<Unit>,
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val bravColors = LocalBravColors.current
  val listState = rememberLazyListState()

  var messageInput by remember { mutableStateOf("") }
  var showAttachSheet by remember { mutableStateOf(false) }
  var pendingDraft by remember { mutableStateOf<MediaAttachmentDraft?>(null) }
  var isSending by remember { mutableStateOf(false) }
  var isGeneratingAi by remember { mutableStateOf(false) }
  var sendError by remember { mutableStateOf<String?>(null) }
  var previewImageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

  // Voice note recording
  val voiceRecorder = remember { VoiceNoteRecorder(context) }
  var isRecordingVoice by remember { mutableStateOf(false) }
  var recordingElapsedMs by remember { mutableLongStateOf(0L) }

  // Voice note playback
  val voicePlayer = remember { VoiceNotePlayer() }
  var playingUri by remember { mutableStateOf<String?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      voiceRecorder.cancel()
      voicePlayer.stop()
    }
  }

  LaunchedEffect(isRecordingVoice) {
    if (isRecordingVoice) {
      recordingElapsedMs = 0L
      while (isActive && isRecordingVoice) {
        delay(250L)
        recordingElapsedMs += 250L
      }
    }
  }

  var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
  val takePictureLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
      val uri = cameraOutputUri
      if (success && uri != null) {
        scope.launch {
          pendingDraft = MediaHelper.buildDraftFromUri(context, uri, "IMAGE", autoCompressMedia)
        }
      }
    }

  val cameraPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        val photoFile = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
        val uri =
          FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
        cameraOutputUri = uri
        takePictureLauncher.launch(uri)
      }
    }

  val audioRecordPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted && voiceRecorder.start()) {
        isRecordingVoice = true
      }
    }

  val mediaPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          pendingDraft = MediaHelper.buildDraftFromUri(context, uri, null, autoCompressMedia)
        }
      }
    }

  val documentPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        scope.launch {
          pendingDraft = MediaHelper.buildDraftFromUri(context, uri, null, autoCompressMedia)
        }
      }
    }

  val itemCount = if (isSmsMode) smsMessages.size else cloudMessages.size
  LaunchedEffect(itemCount) {
    if (itemCount > 0) {
      listState.animateScrollToItem(itemCount - 1)
    }
  }

  Scaffold(
    topBar = {
      Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
          navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
            }
          },
          title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              BravAvatar(name = title, avatarUrl = peerAvatarUrl, size = 40.dp)
              Spacer(Modifier.width(12.dp))
              Column {
                Text(
                  text = title,
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text =
                    when {
                      isSmsMode && aiAutoReplyEnabled -> "SMS • Auto-IA actif"
                      isSmsMode -> "SMS • $peerPhone"
                      aiAutoReplyEnabled -> "Chiffré • Auto-IA Gemini"
                      else -> "Chiffré • BRAV CHAT"
                    },
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          },
          actions = {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
              modifier = Modifier.size(38.dp),
            ) {
              IconButton(
                onClick = { onStartCall(false) },
                modifier = Modifier.testTag("chat_audio_call"),
              ) {
                Icon(
                  Icons.Rounded.Call,
                  contentDescription = "Appel audio",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(19.dp),
                )
              }
            }
            Spacer(Modifier.width(6.dp))
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
              modifier = Modifier.padding(end = 10.dp).size(38.dp),
            ) {
              IconButton(
                onClick = { onStartCall(true) },
                modifier = Modifier.testTag("chat_video_call"),
              ) {
                Icon(
                  Icons.Rounded.Videocam,
                  contentDescription = "Appel vidéo",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          },
          colors =
            TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        HorizontalDivider(thickness = 0.6.dp, color = bravColors.hairline)
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding().navigationBarsPadding()
    ) {
      ChatWatermarkBackground(modifier = Modifier.fillMaxSize())

      Column(modifier = Modifier.fillMaxSize()) {
        if (isSending) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }

        LazyColumn(
          state = listState,
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (isSmsMode) {
            items(smsMessages, key = { it.id }) { sms ->
              val isMine = !sms.isIncoming
              MessageBubbleContainer(isMine = isMine) {
                Column {
                  Text(
                    text = sms.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Spacer(Modifier.height(4.dp))
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.align(Alignment.End),
                  ) {
                    Text(
                      text = formatTimeShort(sms.dateMs),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (sms.sendFailed) {
                      Spacer(Modifier.width(4.dp))
                      Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = "Échec",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                      )
                    }
                  }
                }
              }
            }
          } else {
            items(cloudMessages, key = { it.id }) { msg ->
              val isMine = msg.senderId == currentUserId
              MessageBubbleContainer(isMine = isMine) {
                CloudMessageContent(
                  msg = msg,
                  isPlaying = playingUri != null && playingUri == msg.attachmentLocalUri,
                  onImageClick = { b64 ->
                    previewImageBitmap = MediaHelper.decodeBase64DataUri(b64)
                  },
                  onTogglePlayVoice = { uri ->
                    if (playingUri == uri) {
                      voicePlayer.stop()
                      playingUri = null
                    } else {
                      playingUri = uri
                      voicePlayer.play(context, uri) { playingUri = null }
                    }
                  },
                  onOpenDocument = { uri, mime ->
                    MediaHelper.openAttachmentExternally(context, uri, mime)
                  },
                  onRetry = { onRetryCloudMessage(msg.id) },
                )
              }
            }
          }
        }

        // Attachment draft preview bar before sending
        AnimatedVisibility(visible = pendingDraft != null) {
          pendingDraft?.let { draft ->
            Surface(
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(0.6.dp, bravColors.hairline),
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
              shape = RoundedCornerShape(16.dp),
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (draft.thumbnailBase64.isNotBlank()) {
                  val bmp =
                    remember(draft.thumbnailBase64) {
                      MediaHelper.decodeBase64DataUri(draft.thumbnailBase64)
                    }
                  if (bmp != null) {
                    Image(
                      bitmap = bmp.asImageBitmap(),
                      contentDescription = draft.displayName,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                    )
                  }
                } else {
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        if (draft.type == "PDF") Icons.Rounded.PictureAsPdf
                        else Icons.Rounded.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = draft.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text =
                      "${MediaHelper.formatFileSize(draft.sizeBytes)}" +
                        if (draft.wasCompressed) " • Optimisé" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                IconButton(onClick = { pendingDraft = null }) {
                  Icon(Icons.Rounded.Close, contentDescription = "Retirer")
                }
              }
            }
          }
        }

        if (!sendError.isNullOrBlank()) {
          Text(
            text = sendError.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
          )
        }

        // Refined Composer Dock
        Surface(
          color = MaterialTheme.colorScheme.surface,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column {
            HorizontalDivider(thickness = 0.6.dp, color = bravColors.hairline)
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (isRecordingVoice) {
                IconButton(
                  onClick = {
                    voiceRecorder.cancel()
                    isRecordingVoice = false
                  }
                ) {
                  Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Annuler",
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
                Row(
                  modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Box(
                    modifier =
                      Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error)
                  )
                  Spacer(Modifier.width(10.dp))
                  Text(
                    text = MediaHelper.formatDuration(recordingElapsedMs),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
                Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.primary,
                  modifier =
                    Modifier.size(46.dp)
                      .clip(CircleShape)
                      .clickable {
                        isRecordingVoice = false
                        val draft = voiceRecorder.stop()
                        if (draft != null) {
                          scope.launch {
                            isSending = true
                            onSendCloudMessage("", draft)
                            isSending = false
                          }
                        }
                      },
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      Icons.AutoMirrored.Rounded.Send,
                      contentDescription = "Envoyer note vocale",
                      tint = MaterialTheme.colorScheme.onPrimary,
                    )
                  }
                }
              } else {
                IconButton(
                  onClick = { showAttachSheet = true },
                  modifier = Modifier.testTag("chat_attach_button"),
                ) {
                  Icon(
                    Icons.Rounded.AttachFile,
                    contentDescription = "Pièce jointe",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                OutlinedTextField(
                  value = messageInput,
                  onValueChange = { messageInput = it },
                  placeholder = {
                    Text(
                      text = if (isSmsMode) "Message SMS..." else "Écrire un message...",
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  },
                  trailingIcon = {
                    IconButton(
                      enabled = !isGeneratingAi,
                      onClick = {
                        val transcript =
                          if (isSmsMode) {
                            smsMessages.takeLast(8).map { sms ->
                              val who = if (sms.isIncoming) title else "Moi"
                              "$who : ${sms.body}"
                            }
                          } else {
                            cloudMessages.takeLast(8).filter { it.text.isNotBlank() }.map { msg ->
                              val who = if (msg.senderId == currentUserId) "Moi" else title
                              "$who : ${msg.text}"
                            }
                          }
                        sendError = null
                        scope.launch {
                          isGeneratingAi = true
                          val res = onGenerateAiReply(transcript)
                          isGeneratingAi = false
                          res.fold(
                            onSuccess = { reply -> messageInput = reply },
                            onFailure = { err ->
                              sendError = err.localizedMessage ?: "Erreur Gemini"
                            },
                          )
                        }
                      },
                      modifier = Modifier.testTag("chat_ai_reply_button"),
                    ) {
                      if (isGeneratingAi) {
                        CircularProgressIndicator(
                          modifier = Modifier.size(18.dp),
                          strokeWidth = 2.dp,
                        )
                      } else {
                        Icon(
                          Icons.Rounded.AutoAwesome,
                          contentDescription = "Réponse automatique Gemini",
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(20.dp),
                        )
                      }
                    }
                  },
                  shape = RoundedCornerShape(24.dp),
                  maxLines = 4,
                  colors =
                    OutlinedTextFieldDefaults.colors(
                      unfocusedBorderColor = Color.Transparent,
                      focusedBorderColor = Color.Transparent,
                      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                      focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                  modifier = Modifier.weight(1f).testTag("chat_message_input"),
                )

                Spacer(Modifier.width(8.dp))

                val canSend = messageInput.isNotBlank() || pendingDraft != null
                Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.primary,
                  modifier =
                    Modifier.size(46.dp)
                      .clip(CircleShape)
                      .clickable {
                        if (canSend) {
                          val txt = messageInput.trim()
                          val draft = pendingDraft
                          messageInput = ""
                          pendingDraft = null
                          sendError = null
                          scope.launch {
                            isSending = true
                            val res =
                              if (isSmsMode) {
                                val uri = draft?.localUri?.let { Uri.parse(it) }
                                onSendSms(txt, uri, draft?.mimeType)
                              } else {
                                onSendCloudMessage(txt, draft).map {}
                              }
                            isSending = false
                            if (res.isFailure) {
                              sendError = res.exceptionOrNull()?.localizedMessage ?: "Échec d'envoi"
                            }
                          }
                        } else if (!isSmsMode) {
                          audioRecordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                      }
                      .testTag("chat_send_button"),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      if (canSend || isSmsMode) Icons.AutoMirrored.Rounded.Send else Icons.Rounded.Mic,
                      contentDescription = if (canSend) "Envoyer" else "Message vocal",
                      tint = MaterialTheme.colorScheme.onPrimary,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showAttachSheet) {
    AttachmentPickerSheet(
      onDismiss = { showAttachSheet = false },
      onPickMedia = {
        mediaPickerLauncher.launch(
          PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
      },
      onCapturePhoto = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
      onPickDocument = {
        documentPickerLauncher.launch(
          arrayOf("application/pdf", "application/*", "text/*")
        )
      },
      onPickAudio = { documentPickerLauncher.launch(arrayOf("audio/*")) },
      onRecordVoice = { audioRecordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
    )
  }

  previewImageBitmap?.let { bmp ->
    AlertDialog(
      onDismissRequest = { previewImageBitmap = null },
      containerColor = MaterialTheme.colorScheme.surface,
      confirmButton = {
        TextButton(onClick = { previewImageBitmap = null }) { Text("Fermer") }
      },
      text = {
        Image(
          bitmap = bmp.asImageBitmap(),
          contentDescription = "Aperçu image",
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(16.dp)),
        )
      },
    )
  }
}

@Composable
private fun MessageBubbleContainer(
  isMine: Boolean,
  content: @Composable () -> Unit,
) {
  val bravColors = LocalBravColors.current
  val bubbleColor = if (isMine) bravColors.outgoingBubble else bravColors.incomingBubble
  val border = if (isMine) null else BorderStroke(0.6.dp, bravColors.incomingBubbleBorder)
  val shape =
    if (isMine) RoundedCornerShape(20.dp, 6.dp, 20.dp, 20.dp)
    else RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)

  Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart,
  ) {
    Surface(
      color = bubbleColor,
      shape = shape,
      border = border,
      modifier = Modifier.widthIn(max = 310.dp),
    ) {
      Box(modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) { content() }
    }
  }
}

@Composable
private fun CloudMessageContent(
  msg: ChatMessage,
  isPlaying: Boolean,
  onImageClick: (String) -> Unit,
  onTogglePlayVoice: (String) -> Unit,
  onOpenDocument: (uri: String, mime: String) -> Unit,
  onRetry: () -> Unit,
) {
  val bravColors = LocalBravColors.current
  val timeMs = msg.createdAt?.toDate()?.time ?: System.currentTimeMillis()

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    when (msg.type) {
      "IMAGE", "VIDEO" -> {
        if (msg.attachmentThumbnailBase64.isNotBlank()) {
          val bmp =
            remember(msg.attachmentThumbnailBase64) {
              MediaHelper.decodeBase64DataUri(msg.attachmentThumbnailBase64)
            }
          if (bmp != null) {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(185.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable {
                    if (msg.type == "IMAGE") onImageClick(msg.attachmentThumbnailBase64)
                    else onOpenDocument(msg.attachmentLocalUri, msg.attachmentMimeType)
                  }
            ) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = msg.attachmentName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
              if (msg.type == "VIDEO") {
                Surface(
                  shape = CircleShape,
                  color = Color.Black.copy(alpha = 0.55f),
                  modifier = Modifier.align(Alignment.Center).size(46.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      Icons.Rounded.PlayArrow,
                      contentDescription = "Lire vidéo",
                      tint = Color.White,
                    )
                  }
                }
              }
            }
          }
        }
      }
      "VOICE", "AUDIO" -> {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
              .clickable {
                if (msg.attachmentLocalUri.isNotBlank()) onTogglePlayVoice(msg.attachmentLocalUri)
              }
              .padding(9.dp),
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(38.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Écouter",
                tint = MaterialTheme.colorScheme.onPrimary,
              )
            }
          }
          Spacer(Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text =
                if (msg.type == "VOICE") "Message vocal"
                else msg.attachmentName.ifBlank { "Audio" },
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
              listOf(5, 10, 7, 13, 8, 12, 6, 11, 7, 9, 5).forEach { barH ->
                Box(
                  modifier =
                    Modifier.width(2.5.dp)
                      .height(barH.dp)
                      .clip(CircleShape)
                      .background(
                        if (isPlaying) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                      )
                )
              }
              Spacer(Modifier.width(6.dp))
              Text(
                text = MediaHelper.formatDuration(msg.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Icon(
            Icons.Rounded.AudioFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      "PDF", "DOCUMENT" -> {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
              .clickable {
                onOpenDocument(msg.attachmentLocalUri, msg.attachmentMimeType)
              }
              .padding(10.dp),
        ) {
          Icon(
            if (msg.type == "PDF") Icons.Rounded.PictureAsPdf else Icons.Rounded.Description,
            contentDescription = null,
            tint = if (msg.type == "PDF") Color(0xFFE11D48) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(30.dp),
          )
          Spacer(Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = msg.attachmentName.ifBlank { "Document" },
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = MediaHelper.formatFileSize(msg.attachmentSizeBytes),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    if (msg.text.isNotBlank()) {
      Text(
        text = msg.text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.End,
      modifier = Modifier.align(Alignment.End),
    ) {
      Text(
        text = formatTimeShort(timeMs),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.width(4.dp))
      when (msg.deliveryStatus) {
        "SENDING" ->
          Icon(
            Icons.Rounded.Schedule,
            contentDescription = "Envoi",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
          )
        "READ" ->
          Icon(
            Icons.Rounded.DoneAll,
            contentDescription = "Lu",
            tint = bravColors.readCheck,
            modifier = Modifier.size(15.dp),
          )
        "DELIVERED" ->
          Icon(
            Icons.Rounded.DoneAll,
            contentDescription = "Reçu",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
          )
        "FAILED" ->
          IconButton(onClick = onRetry, modifier = Modifier.size(20.dp)) {
            Icon(
              Icons.Rounded.Refresh,
              contentDescription = "Réessayer",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(16.dp),
            )
          }
        else ->
          Icon(
            Icons.Rounded.Done,
            contentDescription = "Envoyé",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
          )
      }
    }
  }
}
