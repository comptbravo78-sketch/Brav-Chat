package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NoiseAware
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.call.AudioDspCapabilities
import com.example.model.CallLog
import com.example.model.UiState
import com.example.ui.components.BravAvatar
import com.example.ui.components.BravEmptyState
import com.example.ui.theme.LocalBravColors
import com.example.viewmodel.ActiveCallSession
import com.example.viewmodel.CallPhase

@Composable
fun CallLogsScreen(
  callLogsState: UiState<List<CallLog>>,
  searchQuery: String,
  onStartCall: (peerName: String, peerPhone: String, peerAvatarUrl: String, isVideo: Boolean) -> Unit,
) {
  val bravColors = LocalBravColors.current
  val logs = (callLogsState as? UiState.Success)?.data ?: emptyList()
  val filtered =
    remember(logs, searchQuery) {
      if (searchQuery.isBlank()) logs
      else logs.filter {
        it.peerName.contains(searchQuery, ignoreCase = true) ||
          it.peerPhone.contains(searchQuery, ignoreCase = true)
      }
    }

  if (callLogsState is UiState.Loading && logs.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(strokeWidth = 2.5.dp)
    }
    return
  }

  if (filtered.isEmpty()) {
    BravEmptyState(
      icon = Icons.Rounded.Call,
      title = "Aucun appel récent",
      subtitle = "Passez un appel audio ou vidéo haute définition depuis vos discussions ou contacts.",
    )
    return
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(top = 12.dp, bottom = 92.dp),
  ) {
    item {
      Text(
        text = "Récents",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
      )
    }
    itemsIndexed(filtered, key = { _, it -> it.id }) { idx, log ->
      val isMissed = log.direction == "MISSED"
      Surface(
        color = MaterialTheme.colorScheme.surface,
        shape =
          when {
            filtered.size == 1 -> RoundedCornerShape(22.dp)
            idx == 0 -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            idx == filtered.lastIndex -> RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
            else -> RoundedCornerShape(0.dp)
          },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      ) {
        Column {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable {
                  onStartCall(
                    log.peerName,
                    log.peerPhone,
                    log.peerAvatarUrl,
                    log.callType == "VIDEO",
                  )
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            BravAvatar(name = log.peerName, avatarUrl = log.peerAvatarUrl, size = 50.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = log.peerName,
                  style = MaterialTheme.typography.titleMedium,
                  color =
                    if (isMissed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (log.noiseSuppressionUsed) {
                  Spacer(Modifier.width(7.dp))
                  Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                  ) {
                    Text(
                      text = "HD",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                  }
                }
              }
              Spacer(Modifier.height(2.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector =
                    when (log.direction) {
                      "INCOMING" -> Icons.AutoMirrored.Rounded.CallReceived
                      "MISSED" -> Icons.AutoMirrored.Rounded.CallMissed
                      else -> Icons.AutoMirrored.Rounded.CallMade
                    },
                  contentDescription = null,
                  tint =
                    if (isMissed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                  text =
                    buildString {
                      append(formatTimeShort(log.createdAt?.toDate()?.time ?: 0L))
                      if (log.durationSeconds > 0L) {
                        append(" • ${formatCallDuration(log.durationSeconds)}")
                      }
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                modifier =
                  Modifier.size(38.dp)
                    .clip(CircleShape)
                    .clickable {
                      onStartCall(log.peerName, log.peerPhone, log.peerAvatarUrl, false)
                    }
                    .minimumInteractiveComponentSize(),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    Icons.Rounded.Call,
                    contentDescription = "Appel audio",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                modifier =
                  Modifier.size(38.dp)
                    .clip(CircleShape)
                    .clickable {
                      onStartCall(log.peerName, log.peerPhone, log.peerAvatarUrl, true)
                    }
                    .minimumInteractiveComponentSize(),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    Icons.Rounded.Videocam,
                    contentDescription = "Appel vidéo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                  )
                }
              }
            }
          }
          if (idx < filtered.lastIndex) {
            HorizontalDivider(
              thickness = 0.5.dp,
              color = bravColors.hairline,
              modifier = Modifier.padding(start = 78.dp, end = 16.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
fun ActiveCallOverlay(
  session: ActiveCallSession,
  dspCapabilities: AudioDspCapabilities,
  onToggleMute: () -> Unit,
  onToggleSpeaker: () -> Unit,
  onToggleCamera: () -> Unit,
  onSwitchCamera: () -> Unit,
  onToggleNoiseSuppression: () -> Unit,
  onEndCall: () -> Unit,
) {
  BackHandler(onBack = onEndCall)

  val context = LocalContext.current
  val bravColors = LocalBravColors.current
  var showKeypad by remember { mutableStateOf(false) }
  var dtmfDigits by remember { mutableStateOf("") }

  var hasMicPerm by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var hasCameraPerm by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val permLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
      hasMicPerm = map[Manifest.permission.RECORD_AUDIO] ?: hasMicPerm
      hasCameraPerm = map[Manifest.permission.CAMERA] ?: hasCameraPerm
    }

  LaunchedEffect(session.isVideo) {
    val needed = buildList {
      if (!hasMicPerm) add(Manifest.permission.RECORD_AUDIO)
      if (session.isVideo && !hasCameraPerm) add(Manifest.permission.CAMERA)
    }
    if (needed.isNotEmpty()) {
      permLauncher.launch(needed.toTypedArray())
    }
  }

  val voicePulseScale by
    animateFloatAsState(
      targetValue = 1f + (dspCapabilities.micLevelNormalized * 0.24f),
      label = "mic_pulse",
    )

  androidx.compose.ui.window.Dialog(
    onDismissRequest = {},
    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              listOf(Color(0xFF0E1F3D), Color(0xFF0B1426), Color(0xFF070C17))
            )
          )
          .safeDrawingPadding()
    ) {
      if (session.isVideo && session.isCameraEnabled && hasCameraPerm) {
        CameraXPreviewSurface(
          isFrontCamera = session.isFrontCamera,
          modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
      }

      Column(
        modifier =
          Modifier.fillMaxWidth()
            .align(Alignment.TopCenter)
            .padding(top = 28.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Surface(
          color = Color.White.copy(alpha = 0.12f),
          shape = CircleShape,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              if (session.noiseSuppressionEnabled) Icons.Rounded.NoiseAware
              else Icons.Rounded.GraphicEq,
              contentDescription = null,
              tint = if (session.noiseSuppressionEnabled) Color(0xFF34D399) else Color.White,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
              text =
                if (session.noiseSuppressionEnabled) {
                  if (dspCapabilities.noiseSuppressorActive) "Voix HD • Anti-bruit DSP actif"
                  else "Voix HD • Anti-bruit prêt"
                } else {
                  "Audio standard"
                },
              style = MaterialTheme.typography.labelMedium,
              color = Color.White,
            )
          }
        }

        Spacer(Modifier.height(34.dp))

        if (!session.isVideo || !session.isCameraEnabled) {
          Box(contentAlignment = Alignment.Center) {
            Box(
              modifier =
                Modifier.size(156.dp)
                  .scale(if (session.isMuted) 1f else voicePulseScale)
                  .clip(CircleShape)
                  .background(Color(0xFF3B82F6).copy(alpha = 0.18f))
            )
            Box(
              modifier =
                Modifier.size(134.dp)
                  .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                  .padding(4.dp),
              contentAlignment = Alignment.Center,
            ) {
              BravAvatar(
                name = session.peerName,
                avatarUrl = session.peerAvatarUrl,
                size = 122.dp,
              )
            }
          }
          Spacer(Modifier.height(22.dp))
        }

        Text(
          text = session.peerName,
          style = MaterialTheme.typography.headlineMedium,
          color = Color.White,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
          color = Color.White.copy(alpha = 0.1f),
          shape = CircleShape,
        ) {
          Text(
            text =
              when (session.phase) {
                CallPhase.DIALING -> if (session.isVideo) "Appel vidéo..." else "Appel en cours..."
                CallPhase.CONNECTED -> formatCallDuration(session.elapsedSeconds)
              },
            style = MaterialTheme.typography.titleSmall,
            color = Color(0xFFCBD5E1),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
          )
        }

        if (dtmfDigits.isNotEmpty()) {
          Spacer(Modifier.height(10.dp))
          Text(
            text = dtmfDigits,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
          )
        }
      }

      AnimatedVisibility(
        visible = showKeypad,
        modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
      ) {
        Surface(
          color = Color(0xFF0F172A).copy(alpha = 0.85f),
          shape = RoundedCornerShape(24.dp),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            listOf(
              listOf("1", "2", "3"),
              listOf("4", "5", "6"),
              listOf("7", "8", "9"),
              listOf("*", "0", "#"),
            ).forEach { row ->
              Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { d ->
                  Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.14f),
                    modifier =
                      Modifier.size(56.dp).clip(CircleShape).clickable { dtmfDigits += d },
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(d, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                  }
                }
              }
            }
          }
        }
      }

      Column(
        modifier =
          Modifier.fillMaxWidth()
            .align(Alignment.BottomCenter)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Surface(
          color = Color.White.copy(alpha = 0.11f),
          shape = RoundedCornerShape(28.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
          ) {
            CallControlButton(
              icon = if (session.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
              label = "Muet",
              active = session.isMuted,
              testTag = "call_mute_button",
              onClick = onToggleMute,
            )
            CallControlButton(
              icon = Icons.AutoMirrored.Rounded.VolumeUp,
              label = "HP",
              active = session.isSpeakerOn,
              testTag = "call_speaker_button",
              onClick = onToggleSpeaker,
            )
            CallControlButton(
              icon = Icons.Rounded.NoiseAware,
              label = "Anti-bruit",
              active = session.noiseSuppressionEnabled,
              testTag = "call_noise_suppression_button",
              onClick = onToggleNoiseSuppression,
            )
            if (session.isVideo) {
              CallControlButton(
                icon =
                  if (session.isCameraEnabled) Icons.Rounded.Videocam
                  else Icons.Rounded.VideocamOff,
                label = "Caméra",
                active = !session.isCameraEnabled,
                testTag = "call_camera_button",
                onClick = onToggleCamera,
              )
              CallControlButton(
                icon = Icons.Rounded.Cameraswitch,
                label = "Pivoter",
                active = false,
                testTag = "call_switch_camera_button",
                onClick = onSwitchCamera,
              )
            } else {
              CallControlButton(
                icon = Icons.Rounded.Dialpad,
                label = "Clavier",
                active = showKeypad,
                testTag = "call_keypad_button",
                onClick = { showKeypad = !showKeypad },
              )
            }
          }
        }

        Spacer(Modifier.height(22.dp))

        Surface(
          shape = CircleShape,
          color = bravColors.callEnd,
          modifier =
            Modifier.size(68.dp)
              .clip(CircleShape)
              .clickable(onClick = onEndCall)
              .testTag("call_hangup_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Rounded.CallEnd,
              contentDescription = "Raccrocher",
              tint = Color.White,
              modifier = Modifier.size(30.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CallControlButton(
  icon: ImageVector,
  label: String,
  active: Boolean,
  testTag: String,
  onClick: () -> Unit,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      Modifier.clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .padding(6.dp)
        .testTag(testTag),
  ) {
    Surface(
      shape = CircleShape,
      color = if (active) Color.White else Color.White.copy(alpha = 0.14f),
      modifier = Modifier.size(48.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = if (active) Color(0xFF0E1F3D) else Color.White,
        )
      }
    }
    Spacer(Modifier.height(5.dp))
    Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White)
  }
}

@Composable
private fun CameraXPreviewSurface(
  isFrontCamera: Boolean,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val previewView = remember { PreviewView(context) }

  DisposableEffect(lifecycleOwner, isFrontCamera) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val executor = ContextCompat.getMainExecutor(context)
    var providerRef: ProcessCameraProvider? = null

    cameraProviderFuture.addListener(
      {
        runCatching {
          val provider = cameraProviderFuture.get()
          providerRef = provider
          val preview =
            Preview.Builder().build().apply {
              setSurfaceProvider(previewView.surfaceProvider)
            }
          val selector =
            if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA
          provider.unbindAll()
          provider.bindToLifecycle(lifecycleOwner, selector, preview)
        }
      },
      executor,
    )

    onDispose {
      runCatching { providerRef?.unbindAll() }
    }
  }

  AndroidView(factory = { previewView }, modifier = modifier)
}

private fun formatCallDuration(seconds: Long): String {
  val min = seconds / 60L
  val sec = seconds % 60L
  return String.format("%02d:%02d", min, sec)
}
