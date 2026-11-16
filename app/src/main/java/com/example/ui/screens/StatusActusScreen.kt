package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.media.MediaHelper
import com.example.model.MediaAttachmentDraft
import com.example.model.StatusPost
import com.example.model.StatusView
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.components.BravAvatar
import com.example.ui.components.BravEmptyState
import com.example.ui.theme.LocalBravColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private data class StatusGroup(
  val userId: String,
  val authorName: String,
  val authorAvatarUrl: String,
  val posts: List<StatusPost>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusActusScreen(
  statusesState: UiState<List<StatusPost>>,
  currentUserId: String,
  userProfile: UserProfile?,
  searchQuery: String,
  onPublishTextStatus: suspend (text: String, bgHex: String) -> Result<String>,
  onPublishMediaStatus: suspend (draft: MediaAttachmentDraft, caption: String) -> Result<String>,
  onMarkStatusViewed: (statusId: String, reaction: String) -> Unit,
  onObserveViews: (statusId: String) -> Flow<List<StatusView>>,
  onDeleteStatus: (statusId: String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val bravColors = LocalBravColors.current

  var showTextComposer by remember { mutableStateOf(false) }
  var mediaDraftForStatus by remember { mutableStateOf<MediaAttachmentDraft?>(null) }
  var viewingGroup by remember { mutableStateOf<StatusGroup?>(null) }

  val mediaPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
      if (uri != null) {
        scope.launch {
          mediaDraftForStatus =
            MediaHelper.buildDraftFromUri(
              context = context,
              uri = uri,
              forceType = null,
              compressImages = true,
            )
        }
      }
    }

  val allStatuses = (statusesState as? UiState.Success)?.data ?: emptyList()
  val myStatuses = remember(allStatuses, currentUserId) {
    allStatuses.filter { it.userId == currentUserId }.sortedBy { it.createdAt?.toDate()?.time ?: 0L }
  }

  val contactGroups =
    remember(allStatuses, currentUserId, searchQuery) {
      allStatuses
        .filter { it.userId != currentUserId }
        .filter {
          searchQuery.isBlank() ||
            it.authorName.contains(searchQuery, ignoreCase = true) ||
            it.textContent.contains(searchQuery, ignoreCase = true)
        }
        .groupBy { it.userId }
        .values
        .map { list ->
          val latest = list.maxByOrNull { it.createdAt?.toDate()?.time ?: 0L } ?: list.first()
          StatusGroup(
            userId = latest.userId,
            authorName = latest.authorName,
            authorAvatarUrl = latest.authorAvatarUrl,
            posts = list.sortedBy { it.createdAt?.toDate()?.time ?: 0L },
          )
        }
        .sortedByDescending { grp -> grp.posts.lastOrNull()?.createdAt?.toDate()?.time ?: 0L }
    }

  val myAvatar = userProfile?.avatarUrl.orEmpty()
  val myName = userProfile?.displayName?.ifBlank { "Mon statut" } ?: "Mon statut"

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SmallFloatingActionButton(
          onClick = { showTextComposer = true },
          shape = CircleShape,
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.primary,
          elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
          modifier = Modifier.testTag("add_text_status_fab"),
        ) {
          Icon(Icons.Rounded.Edit, contentDescription = "Statut texte")
        }
        FloatingActionButton(
          onClick = {
            mediaPicker.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
          },
          shape = RoundedCornerShape(18.dp),
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
          modifier = Modifier.testTag("add_media_status_fab"),
        ) {
          Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Statut photo/vidéo")
        }
      }
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(top = 12.dp, bottom = 116.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Horizontal circular status strip inside a clean white showcase surface
      item {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(22.dp),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
          LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            item {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                  Modifier.width(74.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                      if (myStatuses.isNotEmpty()) {
                        viewingGroup = StatusGroup(currentUserId, myName, myAvatar, myStatuses)
                      } else {
                        mediaPicker.launch(
                          PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                      }
                    }
                    .padding(vertical = 4.dp)
                    .testTag("my_status_ring"),
              ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                  BravAvatar(
                    name = myName,
                    avatarUrl = myAvatar,
                    size = 62.dp,
                    hasStatusRing = myStatuses.isNotEmpty(),
                  )
                  if (myStatuses.isEmpty()) {
                    Surface(
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.primary,
                      modifier =
                        Modifier.size(22.dp)
                          .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          Icons.Rounded.Add,
                          contentDescription = "Publier un statut",
                          tint = MaterialTheme.colorScheme.onPrimary,
                          modifier = Modifier.size(14.dp),
                        )
                      }
                    }
                  }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                  text = "Mon statut",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            items(contactGroups, key = { it.userId }) { grp ->
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                  Modifier.width(74.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { viewingGroup = grp }
                    .padding(vertical = 4.dp)
                    .testTag("status_ring_${grp.userId}"),
              ) {
                BravAvatar(
                  name = grp.authorName,
                  avatarUrl = grp.authorAvatarUrl,
                  size = 62.dp,
                  hasStatusRing = true,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                  text = grp.authorName.substringBefore(" "),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }

      // User's active status card with discrete view count
      if (myStatuses.isNotEmpty()) {
        item {
          val latestMine = myStatuses.last()
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable {
                  viewingGroup = StatusGroup(currentUserId, myName, myAvatar, myStatuses)
                },
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              BravAvatar(name = myName, avatarUrl = myAvatar, size = 50.dp, hasStatusRing = true)
              Spacer(Modifier.width(14.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Mon statut",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    Icons.Rounded.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                  )
                  Spacer(Modifier.width(5.dp))
                  Text(
                    text =
                      "${latestMine.viewCount} vues • ${myStatuses.size} publiés • ${formatTimeShort(latestMine.createdAt?.toDate()?.time ?: 0L)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              StatusMiniPreview(post = latestMine)
            }
          }
        }
      }

      if (statusesState is UiState.Loading && allStatuses.isEmpty()) {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(48.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(strokeWidth = 2.5.dp)
          }
        }
      } else if (contactGroups.isEmpty() && myStatuses.isEmpty()) {
        item {
          BravEmptyState(
            icon = Icons.Rounded.AutoAwesome,
            title = "Aucun statut 24H",
            subtitle = "Partagez un moment photo, vidéo ou texte visible 24 heures.",
            actionLabel = "Publier un statut",
            onAction = { showTextComposer = true },
            modifier = Modifier.padding(top = 24.dp),
          )
        }
      } else if (contactGroups.isNotEmpty()) {
        item {
          Text(
            text = "Mises à jour récentes",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
          )
        }
        item {
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
          ) {
            Column {
              contactGroups.forEachIndexed { idx, grp ->
                val latest = grp.posts.last()
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clickable { viewingGroup = grp }
                      .padding(horizontal = 16.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  BravAvatar(
                    name = grp.authorName,
                    avatarUrl = grp.authorAvatarUrl,
                    size = 50.dp,
                    hasStatusRing = true,
                  )
                  Spacer(Modifier.width(14.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = grp.authorName,
                      style = MaterialTheme.typography.titleMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text =
                        formatTimeShort(latest.createdAt?.toDate()?.time ?: 0L) +
                          if (grp.posts.size > 1) " • ${grp.posts.size} actus" else "",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  StatusMiniPreview(post = latest)
                  Spacer(Modifier.width(6.dp))
                  Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                  )
                }
                if (idx < contactGroups.lastIndex) {
                  HorizontalDivider(
                    thickness = 0.5.dp,
                    color = bravColors.hairline,
                    modifier = Modifier.padding(start = 80.dp, end = 16.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  if (showTextComposer) {
    TextStatusComposerDialog(
      onDismiss = { showTextComposer = false },
      onPublish = { text, hex ->
        val res = onPublishTextStatus(text, hex)
        if (res.isSuccess) showTextComposer = false
        res
      },
    )
  }

  mediaDraftForStatus?.let { draft ->
    MediaStatusPreviewDialog(
      draft = draft,
      onDismiss = { mediaDraftForStatus = null },
      onPublish = { caption ->
        val res = onPublishMediaStatus(draft, caption)
        if (res.isSuccess) mediaDraftForStatus = null
        res
      },
    )
  }

  viewingGroup?.let { grp ->
    ImmersiveStatusViewerDialog(
      group = grp,
      isOwner = grp.userId == currentUserId,
      onClose = { viewingGroup = null },
      onMarkViewed = onMarkStatusViewed,
      onObserveViews = onObserveViews,
      onDelete = { statusId ->
        onDeleteStatus(statusId)
        viewingGroup = null
      },
    )
  }
}

@Composable
private fun StatusMiniPreview(post: StatusPost) {
  if (post.type == "TEXT") {
    val bgColor =
      remember(post.backgroundColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(post.backgroundColorHex)) }
          .getOrElse { Color(0xFF1254E8) }
      }
    Surface(
      color = bgColor,
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.size(44.dp),
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
        Text(
          text = post.textContent,
          style = MaterialTheme.typography.labelSmall,
          color = Color.White,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
        )
      }
    }
  } else {
    val bmp = remember(post.mediaDataUri) { MediaHelper.decodeBase64DataUri(post.mediaDataUri) }
    if (bmp != null) {
      Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
      )
    }
  }
}

@Composable
private fun TextStatusComposerDialog(
  onDismiss: () -> Unit,
  onPublish: suspend (text: String, bgHex: String) -> Result<String>,
) {
  val scope = rememberCoroutineScope()
  val palette = listOf("#1254E8", "#0F9D7A", "#0E7490", "#5B21B6", "#BE185D", "#1E293B")
  var selectedHex by remember { mutableStateOf(palette.first()) }
  var text by remember { mutableStateOf("") }
  var isSending by remember { mutableStateOf(false) }

  val bgColor = remember(selectedHex) { Color(android.graphics.Color.parseColor(selectedHex)) }

  androidx.compose.ui.window.Dialog(
    onDismissRequest = onDismiss,
    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
  ) {
    BackHandler(onBack = onDismiss)
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(bgColor)
          .safeDrawingPadding()
          .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = Color.White)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          palette.forEach { hex ->
            val swatch = Color(android.graphics.Color.parseColor(hex))
            Box(
              modifier =
                Modifier.size(28.dp)
                  .clip(CircleShape)
                  .background(swatch)
                  .border(
                    width = if (hex == selectedHex) 2.5.dp else 1.dp,
                    color = Color.White,
                    shape = CircleShape,
                  )
                  .clickable { selectedHex = hex }
            )
          }
        }
      }

      OutlinedTextField(
        value = text,
        onValueChange = { if (it.length <= 280) text = it },
        placeholder = {
          Text(
            text = "Votre actu...",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
        },
        textStyle =
          MaterialTheme.typography.headlineSmall.copy(
            color = Color.White,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
          ),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = Color.White,
          ),
        modifier = Modifier.fillMaxWidth().align(Alignment.Center).testTag("status_text_input"),
      )

      FloatingActionButton(
        onClick = {
          if (text.isNotBlank() && !isSending) {
            isSending = true
            scope.launch {
              onPublish(text.trim(), selectedHex)
              isSending = false
            }
          }
        },
        containerColor = Color.White,
        contentColor = bgColor,
        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).testTag("publish_status_button"),
      ) {
        if (isSending) {
          CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
          Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Publier")
        }
      }
    }
  }
}

@Composable
private fun MediaStatusPreviewDialog(
  draft: MediaAttachmentDraft,
  onDismiss: () -> Unit,
  onPublish: suspend (caption: String) -> Result<String>,
) {
  val scope = rememberCoroutineScope()
  var caption by remember { mutableStateOf("") }
  var isSending by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    title = { Text(if (draft.type == "VIDEO") "Statut vidéo" else "Statut photo") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val bmp = remember(draft.thumbnailBase64) { MediaHelper.decodeBase64DataUri(draft.thumbnailBase64) }
        if (bmp != null) {
          Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = draft.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(14.dp)),
          )
        }
        OutlinedTextField(
          value = caption,
          onValueChange = { if (it.length <= 180) caption = it },
          placeholder = { Text("Légende (optionnel)...") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          isSending = true
          scope.launch {
            onPublish(caption.trim())
            isSending = false
          }
        },
        enabled = !isSending,
        shape = RoundedCornerShape(12.dp),
      ) {
        Text(if (isSending) "Envoi..." else "Publier")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmersiveStatusViewerDialog(
  group: StatusGroup,
  isOwner: Boolean,
  onClose: () -> Unit,
  onMarkViewed: (statusId: String, reaction: String) -> Unit,
  onObserveViews: (statusId: String) -> Flow<List<StatusView>>,
  onDelete: (statusId: String) -> Unit,
) {
  var currentIndex by remember { mutableIntStateOf(0) }
  var isPaused by remember { mutableStateOf(false) }
  var showViewsSheet by remember { mutableStateOf(false) }
  var tickKey by remember { mutableIntStateOf(0) }

  val currentPost = group.posts.getOrNull(currentIndex) ?: return

  LaunchedEffect(currentPost.id) {
    if (!isOwner) {
      onMarkViewed(currentPost.id, "")
    }
  }

  val progressAnim by
    animateFloatAsState(
      targetValue = if (isPaused || showViewsSheet) 0f else 1f,
      animationSpec = tween(durationMillis = 5000, easing = LinearEasing),
      label = "status_progress",
    )

  LaunchedEffect(currentIndex, isPaused, showViewsSheet, tickKey) {
    if (!isPaused && !showViewsSheet) {
      delay(5000L)
      if (currentIndex < group.posts.lastIndex) {
        currentIndex++
        tickKey++
      } else {
        onClose()
      }
    }
  }

  val viewsList by
    remember(currentPost.id) { onObserveViews(currentPost.id) }.collectAsState(initial = emptyList())

  androidx.compose.ui.window.Dialog(
    onDismissRequest = onClose,
    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
  ) {
    BackHandler(onBack = onClose)
    val bgColor =
      remember(currentPost.backgroundColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(currentPost.backgroundColorHex)) }
          .getOrElse { Color(0xFF1254E8) }
      }

    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(if (currentPost.type == "TEXT") bgColor else Color.Black)
          .pointerInput(currentIndex) {
            detectTapGestures(
              onLongPress = { isPaused = true },
              onPress = {
                tryAwaitRelease()
                isPaused = false
              },
              onTap = { offset ->
                if (offset.x < size.width * 0.35f) {
                  if (currentIndex > 0) {
                    currentIndex--
                    tickKey++
                  }
                } else {
                  if (currentIndex < group.posts.lastIndex) {
                    currentIndex++
                    tickKey++
                  } else {
                    onClose()
                  }
                }
              },
            )
          }
          .safeDrawingPadding()
    ) {
      when (currentPost.type) {
        "TEXT" -> {
          Text(
            text = currentPost.textContent,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
          )
        }
        else -> {
          val bmp =
            remember(currentPost.mediaDataUri) {
              MediaHelper.decodeBase64DataUri(currentPost.mediaDataUri)
            }
          if (bmp != null) {
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = currentPost.caption.ifBlank { "Statut" },
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxSize(),
            )
          }
          if (currentPost.type == "VIDEO") {
            Icon(
              Icons.Rounded.PlayArrow,
              contentDescription = null,
              tint = Color.White.copy(alpha = 0.85f),
              modifier = Modifier.align(Alignment.Center).size(64.dp),
            )
          }
        }
      }

      Column(
        modifier =
          Modifier.fillMaxWidth()
            .align(Alignment.TopCenter)
            .background(
              Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          group.posts.forEachIndexed { idx, _ ->
            val p =
              when {
                idx < currentIndex -> 1f
                idx == currentIndex -> progressAnim
                else -> 0f
              }
            LinearProgressIndicator(
              progress = { p },
              color = Color.White,
              trackColor = Color.White.copy(alpha = 0.35f),
              modifier = Modifier.weight(1f).height(3.dp).clip(CircleShape),
            )
          }
        }
        Spacer(Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          BravAvatar(name = group.authorName, avatarUrl = group.authorAvatarUrl, size = 38.dp)
          Spacer(Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = group.authorName,
              style = MaterialTheme.typography.titleMedium,
              color = Color.White,
            )
            Text(
              text = formatTimeShort(currentPost.createdAt?.toDate()?.time ?: 0L),
              style = MaterialTheme.typography.labelSmall,
              color = Color.White.copy(alpha = 0.78f),
            )
          }
          if (isOwner) {
            IconButton(onClick = { onDelete(currentPost.id) }) {
              Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Supprimer le statut",
                tint = Color.White,
              )
            }
          }
          IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = Color.White)
          }
        }
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
          Modifier.fillMaxWidth()
            .align(Alignment.BottomCenter)
            .background(
              Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))
            )
            .padding(16.dp),
      ) {
        if (currentPost.caption.isNotBlank()) {
          Text(
            text = currentPost.caption,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp),
          )
        }
        if (isOwner) {
          Surface(
            color = Color.White.copy(alpha = 0.16f),
            shape = CircleShape,
            modifier = Modifier.clip(CircleShape).clickable { showViewsSheet = true },
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Rounded.Visibility,
                contentDescription = "Vues",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = "${viewsList.size.coerceAtLeast(currentPost.viewCount.toInt())}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
              )
            }
          }
        } else {
          Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("❤️", "🔥", "👏", "😂", "👍").forEach { emoji ->
              Surface(
                color = Color.White.copy(alpha = 0.16f),
                shape = CircleShape,
                modifier =
                  Modifier.clip(CircleShape).clickable { onMarkViewed(currentPost.id, emoji) },
              ) {
                Text(
                  text = emoji,
                  style = MaterialTheme.typography.titleMedium,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
              }
            }
          }
        }
      }
    }
  }

  if (showViewsSheet) {
    ModalBottomSheet(
      onDismissRequest = { showViewsSheet = false },
      containerColor = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .fillMaxHeight(0.45f)
            .padding(horizontal = 20.dp, vertical = 8.dp)
      ) {
        Text(
          text = "Vues (${viewsList.size})",
          style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn {
          items(viewsList, key = { it.viewerId }) { view ->
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              BravAvatar(name = view.viewerName, avatarUrl = null, size = 38.dp)
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(view.viewerName, style = MaterialTheme.typography.bodyLarge)
                Text(
                  text = formatTimeShort(view.viewedAt?.toDate()?.time ?: 0L),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (view.reaction.isNotBlank()) {
                Text(view.reaction, style = MaterialTheme.typography.titleLarge)
              }
            }
          }
        }
      }
    }
  }
}
