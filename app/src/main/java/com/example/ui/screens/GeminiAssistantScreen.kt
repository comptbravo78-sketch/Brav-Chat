package com.example.ui.screens

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.media.MediaHelper
import com.example.model.AiMessageItem
import com.example.ui.components.ChatWatermarkBackground
import com.example.ui.theme.LocalBravColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeminiAssistantScreen(
  messages: List<AiMessageItem>,
  isStreaming: Boolean,
  errorMessage: String?,
  autoReplyEnabled: Boolean,
  onBack: () -> Unit,
  onSendPrompt: (prompt: String, imageUri: Uri?) -> Unit,
  onRetryLast: () -> Unit = {},
  onClearChat: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val bravColors = LocalBravColors.current
  val listState = rememberLazyListState()
  var promptInput by remember { mutableStateOf("") }
  var pendingImageUri by remember { mutableStateOf<Uri?>(null) }

  val imagePickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        pendingImageUri = uri
      }
    }

  LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
          navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("gemini_back_button")) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
            }
          },
          title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                  )
                }
              }
              Spacer(Modifier.width(12.dp))
              Column {
                Text(
                  text = "BRAV IA",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text =
                    if (autoReplyEnabled) "Gemini gratuit • Auto-réponse active"
                    else "Gemini gratuit • Assistant direct",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          },
          actions = {
            if (messages.isNotEmpty()) {
              IconButton(
                onClick = onClearChat,
                modifier = Modifier.testTag("gemini_clear_button"),
              ) {
                Icon(
                  Icons.Rounded.DeleteSweep,
                  contentDescription = "Effacer la discussion",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          },
          colors =
            TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        HorizontalDivider(thickness = 0.6.dp, color = bravColors.hairline)
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding().navigationBarsPadding()
    ) {
      ChatWatermarkBackground(modifier = Modifier.fillMaxSize())

      Column(modifier = Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
          Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                  )
                }
              }
              Spacer(Modifier.height(16.dp))
              Text(
                text = "Assistant Gemini BRAV IA",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
              )
              Spacer(Modifier.height(6.dp))
              Text(
                text =
                  "Posez vos questions, analysez une photo ou rédigez des réponses instantanément.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
              Spacer(Modifier.height(22.dp))
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                listOf(
                  "Rédige un message professionnel",
                  "Idée de statut 24H inspirant",
                  "Traduis : Bonjour comment allez-vous ?",
                  "Conseils pour organiser ma journée",
                ).forEach { suggestion ->
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.6.dp, bravColors.hairline),
                    modifier =
                      Modifier.clip(CircleShape).clickable { onSendPrompt(suggestion, null) },
                  ) {
                    Text(
                      text = suggestion,
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                  }
                }
              }
            }
          }
        } else {
          LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            items(messages, key = { it.id }) { msg ->
              val isMine = msg.isFromUser
              val bubbleColor =
                when {
                  msg.isError -> MaterialTheme.colorScheme.errorContainer
                  isMine -> bravColors.outgoingBubble
                  else -> bravColors.incomingBubble
                }
              val border =
                if (isMine || msg.isError) null
                else BorderStroke(0.6.dp, bravColors.incomingBubbleBorder)
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
                  modifier = Modifier.widthIn(max = 320.dp),
                ) {
                  Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (!isMine && !msg.isError) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                          Icons.Rounded.AutoAwesome,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                          text = "Gemini",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.primary,
                          fontWeight = FontWeight.SemiBold,
                        )
                      }
                      Spacer(Modifier.height(4.dp))
                    }

                    if (msg.imageBase64.isNotBlank()) {
                      val bmp = remember(msg.imageBase64) { MediaHelper.decodeBase64DataUri(msg.imageBase64) }
                      if (bmp != null) {
                        Image(
                          bitmap = bmp.asImageBitmap(),
                          contentDescription = "Image jointe",
                          contentScale = ContentScale.Crop,
                          modifier =
                            Modifier.fillMaxWidth()
                              .height(160.dp)
                              .clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.height(8.dp))
                      }
                    }

                    if (msg.isStreaming && msg.text.isBlank()) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                          modifier = Modifier.size(16.dp),
                          strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                          text = "Réflexion...",
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    } else {
                      Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color =
                          if (msg.isError) MaterialTheme.colorScheme.onErrorContainer
                          else MaterialTheme.colorScheme.onSurface,
                      )
                      if (msg.isError && !isStreaming) {
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.material3.TextButton(
                          onClick = onRetryLast,
                          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                          modifier = Modifier.testTag("gemini_retry_button"),
                        ) {
                          Text(
                            text = "Réessayer",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
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

        if (!errorMessage.isNullOrBlank()) {
          Text(
            text = errorMessage,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
          )
        }

        AnimatedVisibility(visible = pendingImageUri != null) {
          pendingImageUri?.let { uri ->
            Surface(
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(0.6.dp, bravColors.hairline),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                AsyncImage(
                  model = uri,
                  contentDescription = "Image sélectionnée",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                  text = "Image prête pour Gemini",
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { pendingImageUri = null }) {
                  Icon(Icons.Rounded.Close, contentDescription = "Retirer l'image")
                }
              }
            }
          }
        }

        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
          Column {
            HorizontalDivider(thickness = 0.6.dp, color = bravColors.hairline)
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(
                onClick = {
                  imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                modifier = Modifier.testTag("gemini_pick_image_button"),
              ) {
                Icon(
                  Icons.Rounded.Image,
                  contentDescription = "Joindre une image",
                  tint = MaterialTheme.colorScheme.primary,
                )
              }

              OutlinedTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                placeholder = {
                  Text(
                    "Demander à BRAV IA...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
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
                modifier = Modifier.weight(1f).testTag("gemini_prompt_input"),
              )

              Spacer(Modifier.width(8.dp))

              val canSend = !isStreaming && (promptInput.isNotBlank() || pendingImageUri != null)
              Surface(
                shape = CircleShape,
                color =
                  if (canSend) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.surfaceVariant,
                modifier =
                  Modifier.size(46.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canSend) {
                      val prompt = promptInput.trim()
                      val img = pendingImageUri
                      promptInput = ""
                      pendingImageUri = null
                      onSendPrompt(prompt, img)
                    }
                    .testTag("gemini_send_button"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  if (isStreaming) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                  } else {
                    Icon(
                      Icons.AutoMirrored.Rounded.Send,
                      contentDescription = "Envoyer à Gemini",
                      tint =
                        if (canSend) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
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
}
