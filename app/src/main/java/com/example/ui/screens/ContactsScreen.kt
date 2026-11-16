package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.LocalContact
import com.example.ui.components.BravAvatar
import com.example.ui.components.BravEmptyState
import com.example.ui.theme.LocalBravColors

@Composable
fun ContactsScreen(
  contacts: List<LocalContact>,
  hasContactsPermission: Boolean,
  searchQuery: String,
  onPermissionGranted: () -> Unit,
  onRefreshSync: () -> Unit,
  onOpenChat: (LocalContact) -> Unit,
  onStartAudioCall: (LocalContact) -> Unit,
  onStartVideoCall: (LocalContact) -> Unit,
) {
  val bravColors = LocalBravColors.current
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) onPermissionGranted()
    }

  val filtered =
    remember(contacts, searchQuery) {
      if (searchQuery.isBlank()) contacts
      else {
        contacts.filter {
          it.name.contains(searchQuery, ignoreCase = true) ||
            it.phoneNumber.contains(searchQuery, ignoreCase = true)
        }
      }
    }

  val bravContacts = remember(filtered) { filtered.filter { it.bravUser != null } }
  val otherContacts = remember(filtered) { filtered.filter { it.bravUser == null } }

  if (!hasContactsPermission) {
    BravEmptyState(
      icon = Icons.Rounded.Contacts,
      title = "Synchroniser vos contacts",
      subtitle = "Retrouvez instantanément vos proches déjà sur BRAV CHAT et appelez-les en un geste.",
      actionLabel = "Autoriser l'accès",
      onAction = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
      actionTestTag = "grant_contacts_permission",
    )
    return
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(top = 12.dp, bottom = 92.dp),
  ) {
    item {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column {
            Text(
              text = "${contacts.size} contacts synchronisés",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "${bravContacts.size} disponibles sur BRAV CHAT",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.size(40.dp),
          ) {
            IconButton(onClick = onRefreshSync, modifier = Modifier.testTag("sync_contacts_button")) {
              Icon(
                Icons.Rounded.Sync,
                contentDescription = "Synchroniser",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
      }
      Spacer(Modifier.height(14.dp))
    }

    if (filtered.isEmpty()) {
      item {
        BravEmptyState(
          icon = Icons.Rounded.Contacts,
          title = "Aucun contact trouvé",
          subtitle = "Vérifiez votre recherche ou synchronisez votre répertoire.",
          actionLabel = "Actualiser",
          onAction = onRefreshSync,
          modifier = Modifier.padding(top = 32.dp),
        )
      }
      return@LazyColumn
    }

    if (bravContacts.isNotEmpty()) {
      item {
        Text(
          text = "Sur BRAV CHAT",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
      }
      item {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(22.dp),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
          Column {
            bravContacts.forEachIndexed { idx, contact ->
              ContactRow(
                contact = contact,
                onChat = { onOpenChat(contact) },
                onAudioCall = { onStartAudioCall(contact) },
                onVideoCall = { onStartVideoCall(contact) },
              )
              if (idx < bravContacts.lastIndex) {
                HorizontalDivider(
                  thickness = 0.5.dp,
                  color = bravColors.hairline,
                  modifier = Modifier.padding(start = 78.dp, end = 16.dp),
                )
              }
            }
          }
        }
        Spacer(Modifier.height(16.dp))
      }
    }

    if (otherContacts.isNotEmpty()) {
      item {
        Text(
          text = "Répertoire téléphonique",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
      }
      itemsIndexed(
        items = otherContacts,
        key = { _, it -> "local_${it.id}_${it.normalizedPhone}" },
      ) { idx, contact ->
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape =
            when {
              otherContacts.size == 1 -> RoundedCornerShape(22.dp)
              idx == 0 -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
              idx == otherContacts.lastIndex ->
                RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
              else -> RoundedCornerShape(0.dp)
            },
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
          Column {
            ContactRow(
              contact = contact,
              onChat = { onOpenChat(contact) },
              onAudioCall = { onStartAudioCall(contact) },
              onVideoCall = { onStartVideoCall(contact) },
            )
            if (idx < otherContacts.lastIndex) {
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
}

@Composable
private fun ContactRow(
  contact: LocalContact,
  onChat: () -> Unit,
  onAudioCall: () -> Unit,
  onVideoCall: () -> Unit,
) {
  val isOnBrav = contact.bravUser != null
  val avatar = contact.bravUser?.avatarUrl?.takeIf { it.isNotBlank() } ?: contact.photoUri

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onChat)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BravAvatar(
      name = contact.name,
      avatarUrl = avatar,
      size = 50.dp,
      showBravBadge = isOnBrav,
      isOnline = contact.bravUser?.isOnline == true,
    )
    Spacer(Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = contact.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (isOnBrav) {
          Spacer(Modifier.width(7.dp))
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
          ) {
            Text(
              text = "BRAV",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
          }
        }
      }
      Spacer(Modifier.height(2.dp))
      Text(
        text = contact.bravUser?.bioStatus?.takeIf { it.isNotBlank() } ?: contact.phoneNumber,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Spacer(Modifier.width(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      ContactQuickIcon(
        icon = Icons.Rounded.ChatBubbleOutline,
        contentDescription = "Discuter",
        onClick = onChat,
      )
      ContactQuickIcon(
        icon = Icons.Rounded.Call,
        contentDescription = "Appel audio",
        onClick = onAudioCall,
      )
      ContactQuickIcon(
        icon = Icons.Rounded.Videocam,
        contentDescription = "Appel vidéo",
        onClick = onVideoCall,
      )
    }
  }
}

@Composable
private fun ContactQuickIcon(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
) {
  Surface(
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
    modifier =
      Modifier.size(36.dp)
        .clip(CircleShape)
        .clickable(onClick = onClick)
        .minimumInteractiveComponentSize(),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}
