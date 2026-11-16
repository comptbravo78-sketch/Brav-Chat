package com.example.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.model.UiState
import com.example.model.YouTubeTrack
import com.example.music.BravMusicPlayerHost
import com.example.music.ClipDisplayMode
import com.example.ui.components.BravEmptyState
import com.example.ui.theme.LocalBravColors

private val MUSIC_CATEGORIES =
  listOf(
    "Tendances" to "",
    "Afrobeats" to "Afrobeats hits",
    "Coupé-Décalé" to "Coupé-Décalé hits",
    "R&B / Soul" to "R&B Soul music",
    "Lo-Fi Chill" to "Lofi hip hop beats",
    "Jazz" to "Jazz instrumental",
    "Gospel" to "Gospel worship music",
  )

@Composable
fun MusicSpaceScreen(
  tracksState: UiState<List<YouTubeTrack>>,
  searchQuery: String,
  currentTrack: YouTubeTrack?,
  isPlaying: Boolean,
  isBuffering: Boolean,
  clipDisplayMode: ClipDisplayMode,
  autoPlayNextEnabled: Boolean,
  playerError: String?,
  musicHost: BravMusicPlayerHost,
  isApiKeyConfigured: Boolean,
  onEnsureTrendingLoaded: () -> Unit,
  onSearchQuery: (String) -> Unit,
  onPlayTrack: (YouTubeTrack) -> Unit,
  onPlayUrlOrId: (String) -> Boolean,
  onTogglePlayPause: () -> Unit,
  onPreviousTrack: () -> Unit,
  onNextTrack: () -> Unit,
  onStopTrack: () -> Unit,
  onChangeClipMode: (ClipDisplayMode) -> Unit,
  onToggleAutoPlay: () -> Unit,
  onShareTrack: (YouTubeTrack) -> Unit,
) {
  val bravColors = LocalBravColors.current
  var inputQuery by remember(searchQuery) { mutableStateOf(searchQuery) }
  var selectedCategory by remember { mutableStateOf("Tendances") }
  var showDirectUrlDialog by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    if (isApiKeyConfigured) {
      onEnsureTrendingLoaded()
    }
  }

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Search + Direct URL row
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = inputQuery,
        onValueChange = { inputQuery = it },
        placeholder = {
          Text(
            "Rechercher une musique YouTube...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        leadingIcon = {
          Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        trailingIcon = {
          if (inputQuery.isNotBlank()) {
            IconButton(
              onClick = {
                selectedCategory = "Rechercher"
                onSearchQuery(inputQuery)
              }
            ) {
              Icon(
                Icons.AutoMirrored.Rounded.Send,
                contentDescription = "Rechercher",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
          KeyboardActions(
            onSearch = {
              selectedCategory = "Rechercher"
              onSearchQuery(inputQuery)
            }
          ),
        shape = RoundedCornerShape(16.dp),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
          ),
        modifier = Modifier.weight(1f).testTag("music_search_input"),
      )

      Spacer(Modifier.width(8.dp))

      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier =
          Modifier.size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { showDirectUrlDialog = true }
            .testTag("music_direct_link_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.Rounded.Link,
            contentDescription = "Lire un lien YouTube",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }

    // Genre filter chips
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(MUSIC_CATEGORIES) { (label, query) ->
        val selected = selectedCategory == label
        FilterChip(
          selected = selected,
          onClick = {
            selectedCategory = label
            inputQuery = query
            onSearchQuery(query)
          },
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
        )
      }
    }

    // Persistent YouTube Player Card (supports Expanded Clip, Reduced Mini Clip, and Hidden Audio-Only Background)
    if (currentTrack != null) {
      Spacer(Modifier.height(8.dp))
      AnimatedContent(
        targetState = clipDisplayMode,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
        label = "clip_display_mode",
      ) { mode ->
        when (mode) {
          ClipDisplayMode.EXPANDED -> {
            ExpandedYouTubeClipCard(
              track = currentTrack,
              isPlaying = isPlaying,
              isBuffering = isBuffering,
              autoPlayNextEnabled = autoPlayNextEnabled,
              playerError = playerError,
              musicHost = musicHost,
              onTogglePlayPause = onTogglePlayPause,
              onPreviousTrack = onPreviousTrack,
              onNextTrack = onNextTrack,
              onMinimizeClip = { onChangeClipMode(ClipDisplayMode.MINI) },
              onCloseClipKeepAudio = { onChangeClipMode(ClipDisplayMode.HIDDEN_AUDIO_ONLY) },
              onStopTrack = onStopTrack,
              onToggleAutoPlay = onToggleAutoPlay,
              onShareTrack = { onShareTrack(currentTrack) },
            )
          }
          ClipDisplayMode.MINI -> {
            MiniYouTubeClipCard(
              track = currentTrack,
              isPlaying = isPlaying,
              autoPlayNextEnabled = autoPlayNextEnabled,
              musicHost = musicHost,
              onTogglePlayPause = onTogglePlayPause,
              onNextTrack = onNextTrack,
              onExpandClip = { onChangeClipMode(ClipDisplayMode.EXPANDED) },
              onCloseClipKeepAudio = { onChangeClipMode(ClipDisplayMode.HIDDEN_AUDIO_ONLY) },
              onStopTrack = onStopTrack,
            )
          }
          ClipDisplayMode.HIDDEN_AUDIO_ONLY -> {
            AudioOnlyBackgroundCard(
              track = currentTrack,
              isPlaying = isPlaying,
              autoPlayNextEnabled = autoPlayNextEnabled,
              onTogglePlayPause = onTogglePlayPause,
              onNextTrack = onNextTrack,
              onShowClip = { onChangeClipMode(ClipDisplayMode.EXPANDED) },
              onToggleAutoPlay = onToggleAutoPlay,
              onStopTrack = onStopTrack,
            )
          }
        }
      }
    }

    Spacer(Modifier.height(8.dp))

    if (!isApiKeyConfigured) {
      Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(40.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  Icons.Rounded.MusicNote,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            }
            Spacer(Modifier.width(12.dp))
            Text(
              text = "Connexion YouTube Data API v3",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
          Text(
            text =
              "Ajoutez votre clé Google YouTube Data API v3 (YOUTUBE_API_KEY) dans le panneau Secrets d'AI Studio pour activer la recherche et les tendances musicales en direct.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          TextButton(onClick = { showDirectUrlDialog = true }) {
            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Lire un lien YouTube directement")
          }
        }
      }
      Spacer(Modifier.height(12.dp))
    }

    when (tracksState) {
      is UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(strokeWidth = 2.5.dp)
        }
      }
      is UiState.Error -> {
        if (isApiKeyConfigured) {
          BravEmptyState(
            icon = Icons.Rounded.MusicNote,
            title = "Impossible de charger YouTube",
            subtitle = tracksState.message,
            actionLabel = "Réessayer",
            onAction = { onSearchQuery(inputQuery) },
          )
        }
      }
      is UiState.Success -> {
        val tracks = tracksState.data
        if (tracks.isEmpty() && isApiKeyConfigured) {
          BravEmptyState(
            icon = Icons.Rounded.MusicNote,
            title = "Aucun titre trouvé",
            subtitle = "Essayez un autre artiste, genre musical ou collez un lien YouTube.",
            actionLabel = "Tendances",
            onAction = { onSearchQuery("") },
          )
        } else if (tracks.isNotEmpty()) {
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(top = 8.dp, bottom = 92.dp),
            ) {
              itemsIndexed(tracks, key = { _, t -> t.videoId }) { index, track ->
                val isCurrentTrack = currentTrack?.videoId == track.videoId
                Column {
                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .clickable { onPlayTrack(track) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Box(
                      modifier =
                        Modifier.width(86.dp)
                          .height(54.dp)
                          .clip(RoundedCornerShape(12.dp))
                          .background(MaterialTheme.colorScheme.surfaceVariant),
                      contentAlignment = Alignment.Center,
                    ) {
                      AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                      )
                      Surface(
                        shape = CircleShape,
                        color =
                          if (isCurrentTrack) MaterialTheme.colorScheme.primary
                          else Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.size(28.dp),
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Icon(
                            if (isCurrentTrack && isPlaying) Icons.Rounded.GraphicEq
                            else Icons.Rounded.PlayArrow,
                            contentDescription = "Lire",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                          )
                        }
                      }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.SemiBold,
                        color =
                          if (isCurrentTrack) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Spacer(Modifier.height(2.dp))
                      Text(
                        text = track.channelTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }

                    IconButton(onClick = { onShareTrack(track) }) {
                      Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Partager",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                      )
                    }
                  }
                  if (index < tracks.lastIndex) {
                    HorizontalDivider(
                      thickness = 0.5.dp,
                      color = bravColors.hairline,
                      modifier = Modifier.padding(start = 114.dp, end = 16.dp),
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

  if (showDirectUrlDialog) {
    var linkText by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { showDirectUrlDialog = false },
      containerColor = MaterialTheme.colorScheme.surface,
      title = { Text("Lire depuis un lien YouTube", style = MaterialTheme.typography.titleLarge) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = linkText,
            onValueChange = {
              linkText = it
              invalid = false
            },
            label = { Text("Lien YouTube ou ID vidéo") },
            placeholder = { Text("https://youtu.be/...") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          )
          if (invalid) {
            Text(
              text = "Lien ou identifiant YouTube invalide.",
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (onPlayUrlOrId(linkText)) {
              showDirectUrlDialog = false
            } else {
              invalid = true
            }
          },
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("Écouter")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDirectUrlDialog = false }) { Text("Annuler") }
      },
    )
  }
}

@Composable
private fun ExpandedYouTubeClipCard(
  track: YouTubeTrack,
  isPlaying: Boolean,
  isBuffering: Boolean,
  autoPlayNextEnabled: Boolean,
  playerError: String?,
  musicHost: BravMusicPlayerHost,
  onTogglePlayPause: () -> Unit,
  onPreviousTrack: () -> Unit,
  onNextTrack: () -> Unit,
  onMinimizeClip: () -> Unit,
  onCloseClipKeepAudio: () -> Unit,
  onStopTrack: () -> Unit,
  onToggleAutoPlay: () -> Unit,
  onShareTrack: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surface,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      // Header bar: Auto-play pill + Minimize Clip + Close Clip (Audio-Only)
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = CircleShape,
          color =
            if (autoPlayNextEnabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
          modifier =
            Modifier.clip(CircleShape)
              .clickable(onClick = onToggleAutoPlay)
              .testTag("music_autoplay_chip"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.AutoMirrored.Rounded.PlaylistPlay,
              contentDescription = null,
              tint =
                if (autoPlayNextEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = if (autoPlayNextEnabled) "Auto-suite ON" else "Auto-suite OFF",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color =
                if (autoPlayNextEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Spacer(Modifier.weight(1f))

        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
          modifier =
            Modifier.clip(CircleShape)
              .clickable(onClick = onMinimizeClip)
              .testTag("music_minimize_clip_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.KeyboardArrowDown,
              contentDescription = "Réduire le clip",
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
              text = "Réduire",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Medium,
            )
          }
        }

        Spacer(Modifier.width(6.dp))

        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
          modifier =
            Modifier.clip(CircleShape)
              .clickable(onClick = onCloseClipKeepAudio)
              .testTag("music_hide_clip_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.VisibilityOff,
              contentDescription = "Fermer le clip et garder le son",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = "Audio seul",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }

      Spacer(Modifier.height(8.dp))

      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
      ) {
        YouTubeClipSlot(
          musicHost = musicHost,
          modifier = Modifier.fillMaxSize(),
        )
        if (isBuffering) {
          CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp),
          )
        }
      }

      if (!playerError.isNullOrBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(
          text = playerError,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
          maxLines = 2,
        )
      }

      Spacer(Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${track.channelTitle} • ${if (isPlaying) "Lecture en cours" else "En pause"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(onClick = onPreviousTrack) {
          Icon(Icons.Rounded.SkipPrevious, contentDescription = "Précédent")
        }
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(38.dp),
        ) {
          IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.testTag("music_play_pause_button"),
          ) {
            Icon(
              if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription = if (isPlaying) "Pause" else "Lire",
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(20.dp),
            )
          }
        }
        IconButton(onClick = onNextTrack) {
          Icon(Icons.Rounded.SkipNext, contentDescription = "Suivant")
        }
        IconButton(onClick = onShareTrack) {
          Icon(
            Icons.AutoMirrored.Rounded.Send,
            contentDescription = "Partager dans une discussion",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(
          onClick = onStopTrack,
          modifier = Modifier.testTag("music_stop_button"),
        ) {
          Icon(
            Icons.Rounded.Close,
            contentDescription = "Arrêter la musique",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun MiniYouTubeClipCard(
  track: YouTubeTrack,
  isPlaying: Boolean,
  autoPlayNextEnabled: Boolean,
  musicHost: BravMusicPlayerHost,
  onTogglePlayPause: () -> Unit,
  onNextTrack: () -> Unit,
  onExpandClip: () -> Unit,
  onCloseClipKeepAudio: () -> Unit,
  onStopTrack: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.width(74.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black)
            .clickable(onClick = onExpandClip),
      ) {
        YouTubeClipSlot(
          musicHost = musicHost,
          modifier = Modifier.fillMaxSize(),
        )
      }

      Spacer(Modifier.width(10.dp))

      Column(
        modifier = Modifier.weight(1f).clickable(onClick = onExpandClip),
      ) {
        Text(
          text = track.title,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            "${track.channelTitle}${if (autoPlayNextEnabled) " • Auto" else ""}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(
        onClick = onTogglePlayPause,
        modifier = Modifier.size(36.dp).testTag("music_play_pause_button"),
      ) {
        Icon(
          if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
          contentDescription = if (isPlaying) "Pause" else "Lire",
          tint = MaterialTheme.colorScheme.primary,
        )
      }
      IconButton(onClick = onNextTrack, modifier = Modifier.size(36.dp)) {
        Icon(Icons.Rounded.SkipNext, contentDescription = "Suivant")
      }
      IconButton(
        onClick = onExpandClip,
        modifier = Modifier.size(36.dp).testTag("music_expand_clip_button"),
      ) {
        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Agrandir le clip")
      }
      IconButton(
        onClick = onCloseClipKeepAudio,
        modifier = Modifier.size(36.dp).testTag("music_hide_clip_button"),
      ) {
        Icon(
          Icons.Rounded.VisibilityOff,
          contentDescription = "Fermer le clip (garder le son)",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
      }
      IconButton(
        onClick = onStopTrack,
        modifier = Modifier.size(34.dp).testTag("music_stop_button"),
      ) {
        Icon(
          Icons.Rounded.Close,
          contentDescription = "Arrêter",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Composable
private fun AudioOnlyBackgroundCard(
  track: YouTubeTrack,
  isPlaying: Boolean,
  autoPlayNextEnabled: Boolean,
  onTogglePlayPause: () -> Unit,
  onNextTrack: () -> Unit,
  onShowClip: () -> Unit,
  onToggleAutoPlay: () -> Unit,
  onStopTrack: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(38.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.Rounded.Headphones,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Spacer(Modifier.width(10.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = track.title,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            buildString {
              append(if (isPlaying) "Lecture audio arrière-plan" else "Audio en pause")
              if (autoPlayNextEnabled) append(" • Auto-suite")
            },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.clickable(onClick = onToggleAutoPlay),
        )
      }

      IconButton(
        onClick = onTogglePlayPause,
        modifier = Modifier.size(36.dp).testTag("music_play_pause_button"),
      ) {
        Icon(
          if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
          contentDescription = if (isPlaying) "Pause" else "Lire",
          tint = MaterialTheme.colorScheme.primary,
        )
      }
      IconButton(onClick = onNextTrack, modifier = Modifier.size(36.dp)) {
        Icon(Icons.Rounded.SkipNext, contentDescription = "Suivant")
      }
      IconButton(
        onClick = onShowClip,
        modifier = Modifier.size(36.dp).testTag("music_expand_clip_button"),
      ) {
        Icon(
          Icons.Rounded.SmartDisplay,
          contentDescription = "Afficher le clip",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
      }
      IconButton(
        onClick = onStopTrack,
        modifier = Modifier.size(34.dp).testTag("music_stop_button"),
      ) {
        Icon(
          Icons.Rounded.Close,
          contentDescription = "Arrêter",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Composable
fun YouTubeClipSlot(
  musicHost: BravMusicPlayerHost,
  modifier: Modifier = Modifier,
) {
  var containerRef by remember { mutableStateOf<FrameLayout?>(null) }

  DisposableEffect(musicHost, containerRef) {
    val container = containerRef
    if (container != null) {
      musicHost.registerVisibleContainer(container)
    }
    onDispose {
      if (container != null) {
        musicHost.unregisterVisibleContainer(container)
      }
    }
  }

  AndroidView(
    factory = { ctx ->
      FrameLayout(ctx).apply {
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
        containerRef = this
        musicHost.registerVisibleContainer(this)
      }
    },
    modifier = modifier,
  )
}
