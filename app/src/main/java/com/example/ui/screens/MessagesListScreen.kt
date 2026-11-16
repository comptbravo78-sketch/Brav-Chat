package com.example.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Conversation
import com.example.model.SmsThread
import com.example.model.UiState
import com.example.sms.SmsRepository
import com.example.ui.components.BravAvatar
import com.example.ui.components.BravEmptyState
import com.example.ui.theme.LocalBravColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class MessageFilter(val label: String) {
  ALL("Tous"),
  UNREAD("Non lus"),
  SMS("SMS"),
  ARCHIVED("Archivés"),
}

private sealed class ThreadRowItem {
  abstract val sortMs: Long
  abstract val isPinned: Boolean
  abstract val isArchived: Boolean
  abstract val unreadCount: Int
  abstract val title: String

  data class CloudChat(val conversation: Conversation) : ThreadRowItem() {
    override val sortMs: Long = conversation.updatedAt?.toDate()?.time ?: 0L
    override val isPinned: Boolean = conversation.isPinned
    override val isArchived: Boolean = conversation.isArchived
    override val unreadCount: Int = conversation.unreadCount.toInt()
    override val title: String = conversation.title
  }

  data class DeviceSms(val thread: SmsThread) : ThreadRowItem() {
    override val sortMs: Long = thread.lastDateMs
    override val isPinned: Boolean = thread.isPinned
    override val isArchived: Boolean = thread.isArchived
    override val unreadCount: Int = thread.unreadCount
    override val title: String = thread.contactName
  }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MessagesListScreen(
  conversationsState: UiState<List<Conversation>>,
  smsThreads: List<SmsThread>,
  isDefaultSmsApp: Boolean,
  hasSmsPermission: Boolean,
  aiAutoReplyEnabled: Boolean,
  searchQuery: String,
  onDefaultSmsResult: () -> Unit,
  onSmsPermissionGranted: () -> Unit,
  onOpenGeminiAssistant: () -> Unit,
  onOpenCloudConversation: (Conversation) -> Unit,
  onOpenSmsThread: (SmsThread) -> Unit,
  onNewChatWithNumber: (name: String, phone: String, isSms: Boolean) -> Unit,
  onTogglePinCloud: (Conversation) -> Unit,
  onToggleArchiveCloud: (Conversation) -> Unit,
  onDeleteCloud: (Conversation) -> Unit,
  onTogglePinSms: (SmsThread) -> Unit,
  onToggleArchiveSms: (SmsThread) -> Unit,
  onDeleteSms: (SmsThread) -> Unit,
) {
  val context = LocalContext.current
  val bravColors = LocalBravColors.current
  val smsRepository = remember { SmsRepository(context) }

  var selectedFilter by remember { mutableStateOf(MessageFilter.ALL) }
  var actionTarget by remember { mutableStateOf<ThreadRowItem?>(null) }
  var showNewComposeDialog by remember { mutableStateOf(false) }

  val defaultSmsLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      onDefaultSmsResult()
    }

  val smsPermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
      if (perms.values.any { it }) {
        onSmsPermissionGranted()
      }
    }

  val cloudList = (conversationsState as? UiState.Success)?.data ?: emptyList()

  val unreadThreadsCount =
    remember(cloudList, smsThreads) {
      cloudList.count { !it.isArchived && it.unreadCount > 0 } +
        smsThreads.count { !it.isArchived && it.unreadCount > 0 }
    }

  val combinedList =
    remember(cloudList, smsThreads, selectedFilter, searchQuery) {
      val allItems =
        cloudList.map { ThreadRowItem.CloudChat(it) } +
          smsThreads.map { ThreadRowItem.DeviceSms(it) }

      allItems
        .filter { item ->
          when (selectedFilter) {
            MessageFilter.ALL -> !item.isArchived
            MessageFilter.UNREAD -> !item.isArchived && item.unreadCount > 0
            MessageFilter.SMS -> item is ThreadRowItem.DeviceSms && !item.isArchived
            MessageFilter.ARCHIVED -> item.isArchived
          }
        }
        .filter { item ->
          if (searchQuery.isBlank()) true
          else {
            val preview =
              when (item) {
                is ThreadRowItem.CloudChat -> item.conversation.lastMessage
                is ThreadRowItem.DeviceSms -> item.thread.lastBody
              }
            item.title.contains(searchQuery, ignoreCase = true) ||
              preview.contains(searchQuery, ignoreCase = true)
          }
        }
        .sortedWith(
          compareByDescending<ThreadRowItem> { it.isPinned }.thenByDescending { it.sortMs }
        )
    }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showNewComposeDialog = true },
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
        modifier = Modifier.testTag("new_message_fab"),
      ) {
        Icon(Icons.Rounded.Edit, contentDescription = "Nouveau message")
      }
    },
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      // Refined filter pill row
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(MessageFilter.entries) { filter ->
          val selected = selectedFilter == filter
          val label =
            if (filter == MessageFilter.UNREAD && unreadThreadsCount > 0) {
              "${filter.label} ($unreadThreadsCount)"
            } else {
              filter.label
            }
          FilterChip(
            selected = selected,
            onClick = { selectedFilter = filter },
            label = {
              Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
              )
            },
            shape = CircleShape,
            border = null,
            colors =
              FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.primary,
              ),
            modifier = Modifier.testTag("filter_${filter.name.lowercase()}"),
          )
        }
      }

      // Discreet single-line SMS permission / Default SMS card
      if (!hasSmsPermission) {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(16.dp),
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 4.dp)
              .clip(RoundedCornerShape(16.dp))
              .clickable {
                smsPermLauncher.launch(
                  arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS,
                  )
                )
              }
              .testTag("enable_sms_permission_banner"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(34.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  Icons.Rounded.Sms,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
            Spacer(Modifier.width(12.dp))
            Text(
              text = "Synchroniser vos SMS",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Text(
                text = "Autoriser",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              )
            }
          }
        }
      } else if (!isDefaultSmsApp) {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(16.dp),
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 4.dp)
              .clip(RoundedCornerShape(16.dp))
              .clickable {
                smsRepository.createDefaultSmsRoleIntent()?.let { intent: Intent ->
                  defaultSmsLauncher.launch(intent)
                }
              }
              .testTag("default_sms_banner"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(34.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  Icons.Rounded.Sms,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
            Spacer(Modifier.width(12.dp))
            Text(
              text = "App SMS par défaut",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Text(
                text = "Définir",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              )
            }
          }
        }
      }

      if (searchQuery.isBlank() ||
        "BRAV IA Gemini assistant".contains(searchQuery, ignoreCase = true)
      ) {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(18.dp),
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 4.dp)
              .clip(RoundedCornerShape(18.dp))
              .clickable(onClick = onOpenGeminiAssistant)
              .testTag("open_brav_ia_card"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(44.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  Icons.Rounded.AutoAwesome,
                  contentDescription = "BRAV IA",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(22.dp),
                )
              }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "BRAV IA • Gemini",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                if (aiAutoReplyEnabled) {
                  Spacer(Modifier.width(8.dp))
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                  ) {
                    Text(
                      text = "Auto-IA",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                  }
                }
              }
              Text(
                text = "Assistant intelligent et réponses automatiques",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Icon(
              Icons.Rounded.ChevronRight,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (conversationsState is UiState.Loading && combinedList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(strokeWidth = 2.5.dp)
        }
      } else if (combinedList.isEmpty()) {
        BravEmptyState(
          icon = Icons.Rounded.ChatBubbleOutline,
          title = "Aucune conversation",
          subtitle = "Démarrez une discussion chiffrée ou envoyez un SMS en un geste.",
          actionLabel = "Nouvelle discussion",
          onAction = { showNewComposeDialog = true },
        )
      } else {
        Spacer(Modifier.height(4.dp))
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 6.dp, bottom = 92.dp),
          ) {
            itemsIndexed(
              items = combinedList,
              key = { _, item ->
                when (item) {
                  is ThreadRowItem.CloudChat -> "cloud_${item.conversation.id}"
                  is ThreadRowItem.DeviceSms -> "sms_${item.thread.threadId}"
                }
              },
            ) { index, item ->
              val avatarUrl =
                when (item) {
                  is ThreadRowItem.CloudChat -> item.conversation.peerAvatarUrl
                  is ThreadRowItem.DeviceSms -> item.thread.contactPhotoUri
                }
              val lastText =
                when (item) {
                  is ThreadRowItem.CloudChat ->
                    item.conversation.lastMessage.ifBlank { "Nouvelle discussion" }
                  is ThreadRowItem.DeviceSms -> item.thread.lastBody
                }
              val isSms = item is ThreadRowItem.DeviceSms

              Column {
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .combinedClickable(
                        onClick = {
                          when (item) {
                            is ThreadRowItem.CloudChat -> onOpenCloudConversation(item.conversation)
                            is ThreadRowItem.DeviceSms -> onOpenSmsThread(item.thread)
                          }
                        },
                        onLongClick = { actionTarget = item },
                      )
                      .padding(horizontal = 18.dp, vertical = 13.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  BravAvatar(
                    name = item.title,
                    avatarUrl = avatarUrl,
                    size = 54.dp,
                    showBravBadge = !isSms,
                  )
                  Spacer(Modifier.width(14.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                      ) {
                        Text(
                          text = item.title,
                          style = MaterialTheme.typography.titleMedium,
                          fontWeight =
                            if (item.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                          color = MaterialTheme.colorScheme.onSurface,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isSms) {
                          Spacer(Modifier.width(7.dp))
                          Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                          ) {
                            Text(
                              text = "SMS",
                              style = MaterialTheme.typography.labelSmall,
                              color = MaterialTheme.colorScheme.primary,
                              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                          }
                        }
                      }
                      Spacer(Modifier.width(8.dp))
                      Text(
                        text = formatTimeShort(item.sortMs),
                        style = MaterialTheme.typography.labelSmall,
                        color =
                          if (item.unreadCount > 0) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      if (!isSms) {
                        Icon(
                          Icons.Rounded.DoneAll,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                          modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                      }
                      Text(
                        text = lastText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight =
                          if (item.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                        color =
                          if (item.unreadCount > 0) MaterialTheme.colorScheme.onSurface
                          else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                      )
                      if (item.isPinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                          Icons.Rounded.PushPin,
                          contentDescription = "Épinglé",
                          tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                          modifier = Modifier.size(15.dp),
                        )
                      }
                      if (item.unreadCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                          modifier =
                            Modifier.clip(CircleShape)
                              .background(MaterialTheme.colorScheme.primary)
                              .padding(horizontal = 7.dp, vertical = 2.dp),
                          contentAlignment = Alignment.Center,
                        ) {
                          Text(
                            text = item.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                          )
                        }
                      }
                    }
                  }
                }
                if (index < combinedList.lastIndex) {
                  HorizontalDivider(
                    thickness = 0.5.dp,
                    color = bravColors.hairline,
                    modifier = Modifier.padding(start = 86.dp, end = 18.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  actionTarget?.let { target ->
    ModalBottomSheet(
      onDismissRequest = { actionTarget = null },
      containerColor = MaterialTheme.colorScheme.surface,
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
          text = target.title,
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        SheetActionRow(
          icon = Icons.Rounded.PushPin,
          label = if (target.isPinned) "Désépingler" else "Épingler en haut",
          onClick = {
            when (target) {
              is ThreadRowItem.CloudChat -> onTogglePinCloud(target.conversation)
              is ThreadRowItem.DeviceSms -> onTogglePinSms(target.thread)
            }
            actionTarget = null
          },
        )
        SheetActionRow(
          icon = if (target.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
          label = if (target.isArchived) "Désarchiver" else "Archiver",
          onClick = {
            when (target) {
              is ThreadRowItem.CloudChat -> onToggleArchiveCloud(target.conversation)
              is ThreadRowItem.DeviceSms -> onToggleArchiveSms(target.thread)
            }
            actionTarget = null
          },
        )
        SheetActionRow(
          icon = Icons.Rounded.DeleteOutline,
          label = "Supprimer la conversation",
          isDestructive = true,
          onClick = {
            when (target) {
              is ThreadRowItem.CloudChat -> onDeleteCloud(target.conversation)
              is ThreadRowItem.DeviceSms -> onDeleteSms(target.thread)
            }
            actionTarget = null
          },
        )
      }
    }
  }

  if (showNewComposeDialog) {
    var targetName by remember { mutableStateOf("") }
    var targetPhone by remember { mutableStateOf("") }
    var asSms by remember { mutableStateOf(false) }
    val fieldColors =
      OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      )

    AlertDialog(
      onDismissRequest = { showNewComposeDialog = false },
      containerColor = MaterialTheme.colorScheme.surface,
      title = { Text("Nouvelle discussion", style = MaterialTheme.typography.titleLarge) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = targetName,
            onValueChange = { targetName = it },
            label = { Text("Nom du contact") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = targetPhone,
            onValueChange = { targetPhone = it },
            label = { Text("Numéro (+225...)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = !asSms,
              onClick = { asSms = false },
              label = { Text("BRAV CHAT") },
              shape = CircleShape,
            )
            FilterChip(
              selected = asSms,
              onClick = { asSms = true },
              label = { Text("SMS") },
              shape = CircleShape,
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (targetPhone.isNotBlank() || targetName.isNotBlank()) {
              onNewChatWithNumber(
                targetName.ifBlank { targetPhone },
                targetPhone.trim(),
                asSms,
              )
              showNewComposeDialog = false
            }
          },
          enabled = targetPhone.isNotBlank() || targetName.isNotBlank(),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("Ouvrir")
        }
      },
      dismissButton = {
        TextButton(onClick = { showNewComposeDialog = false }) { Text("Annuler") }
      },
    )
  }
}

@Composable
private fun SheetActionRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  isDestructive: Boolean = false,
  onClick: () -> Unit,
) {
  val tint =
    if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      shape = CircleShape,
      color = tint.copy(alpha = 0.1f),
      modifier = Modifier.size(36.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
      }
    }
    Spacer(Modifier.width(14.dp))
    Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
  }
}

fun formatTimeShort(timeMs: Long): String {
  if (timeMs <= 0L) return ""
  return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))
}
