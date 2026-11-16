package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.CountryData
import com.example.media.MediaHelper
import com.example.model.CountryDialInfo
import com.example.ui.theme.LocalBravColors
import kotlin.math.abs

@Composable
fun BravLogoMark(
  size: Dp = 44.dp,
  modifier: Modifier = Modifier,
) {
  Image(
    painter = painterResource(id = R.drawable.ic_brav_logo),
    contentDescription = "Logo officiel BRAV CHAT",
    modifier = modifier.size(size),
    contentScale = ContentScale.Fit,
  )
}

@Composable
fun BravAvatar(
  name: String,
  avatarUrl: String?,
  size: Dp = 48.dp,
  isOnline: Boolean = false,
  hasStatusRing: Boolean = false,
  isStatusViewed: Boolean = false,
  showBravBadge: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val bravColors = LocalBravColors.current
  val initials =
    remember(name) {
      name
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "B" }
    }
  val palette =
    listOf(
      Color(0xFF1254E8),
      Color(0xFF0E7490),
      Color(0xFF0F9D7A),
      Color(0xFF5B21B6),
      Color(0xFFBE185D),
      Color(0xFFB45309),
    )
  val bgColor = palette[abs(name.hashCode()) % palette.size]

  val ringModifier =
    if (hasStatusRing) {
      if (isStatusViewed) {
        Modifier.border(2.dp, bravColors.statusRingViewed, CircleShape).padding(3.dp)
      } else {
        Modifier.border(
            width = 2.5.dp,
            brush =
              Brush.linearGradient(
                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
              ),
            shape = CircleShape,
          )
          .padding(3.5.dp)
      }
    } else {
      Modifier
    }

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Box(
      modifier =
        Modifier.then(ringModifier)
          .size(size)
          .clip(CircleShape)
          .background(bgColor),
      contentAlignment = Alignment.Center,
    ) {
      if (!avatarUrl.isNullOrBlank()) {
        if (avatarUrl.startsWith("data:image", ignoreCase = true)) {
          val bmp = remember(avatarUrl) { MediaHelper.decodeBase64DataUri(avatarUrl) }
          if (bmp != null) {
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = name,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Text(
              text = initials,
              style =
                if (size >= 56.dp) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.titleMedium,
              color = Color.White,
              fontWeight = FontWeight.SemiBold,
            )
          }
        } else {
          AsyncImage(
            model = avatarUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }
      } else {
        Text(
          text = initials,
          style =
            if (size >= 56.dp) MaterialTheme.typography.titleLarge
            else MaterialTheme.typography.titleMedium,
          color = Color.White,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }

    if (isOnline) {
      Box(
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .size((size * 0.27f).coerceAtLeast(11.dp))
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp)
            .clip(CircleShape)
            .background(bravColors.onlineDot)
      )
    } else if (showBravBadge) {
      Icon(
        imageVector = Icons.Rounded.Verified,
        contentDescription = "BRAV CHAT",
        tint = MaterialTheme.colorScheme.primary,
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .size((size * 0.34f).coerceAtLeast(15.dp))
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(1.dp),
      )
    }
  }
}

@Composable
fun BravEmptyState(
  icon: ImageVector,
  title: String,
  subtitle: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  actionTestTag: String? = null,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        modifier = Modifier.size(76.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(34.dp),
          )
        }
      }
      Spacer(Modifier.height(18.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      if (!actionLabel.isNullOrBlank() && onAction != null) {
        Spacer(Modifier.height(20.dp))
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.primary,
          modifier =
            Modifier.clip(RoundedCornerShape(14.dp))
              .clickable(onClick = onAction)
              .minimumInteractiveComponentSize()
              .let { if (!actionTestTag.isNullOrBlank()) it.testTag(actionTestTag) else it },
        ) {
          Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
          )
        }
      }
    }
  }
}

@Composable
fun BravSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  trailingContent: @Composable (() -> Unit)? = null,
) {
  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    placeholder = {
      Text(
        text = placeholder,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    leadingIcon = {
      Icon(
        Icons.Rounded.Search,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    },
    trailingIcon = trailingContent,
    singleLine = true,
    shape = RoundedCornerShape(16.dp),
    colors =
      OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      ),
    modifier = modifier.fillMaxWidth(),
  )
}

/**
 * Ultra-subtle geometric communication watermark for conversation backgrounds,
 * preserving effortless legibility.
 */
@Composable
fun ChatWatermarkBackground(modifier: Modifier = Modifier) {
  val bravColors = LocalBravColors.current
  val strokeTint = bravColors.watermarkTint.copy(alpha = 0.032f)

  Box(modifier = modifier.background(bravColors.chatBackdrop)) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val stepX = 114.dp.toPx()
      val stepY = 114.dp.toPx()
      val stroke = Stroke(width = 1.3.dp.toPx())
      var row = 0
      var y = 26.dp.toPx()
      while (y < size.height + stepY) {
        var col = 0
        var x = if (row % 2 == 0) 20.dp.toPx() else 72.dp.toPx()
        while (x < size.width + stepX) {
          when ((row + col) % 4) {
            0 -> {
              drawRoundRect(
                color = strokeTint,
                topLeft = Offset(x, y),
                size = Size(24.dp.toPx(), 18.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = stroke,
              )
              val tail =
                Path().apply {
                  moveTo(x + 5.dp.toPx(), y + 18.dp.toPx())
                  lineTo(x + 3.dp.toPx(), y + 23.dp.toPx())
                  lineTo(x + 11.dp.toPx(), y + 18.dp.toPx())
                }
              drawPath(tail, color = strokeTint, style = stroke)
            }
            1 -> {
              val cx = x + 12.dp.toPx()
              val cy = y + 10.dp.toPx()
              listOf(-8f, -4f, 0f, 4f, 8f).forEachIndexed { i, dx ->
                val h = if (i == 2) 14f else if (i == 1 || i == 3) 9f else 5f
                drawLine(
                  color = strokeTint,
                  start = Offset(cx + dx.dp.toPx(), cy - (h / 2f).dp.toPx()),
                  end = Offset(cx + dx.dp.toPx(), cy + (h / 2f).dp.toPx()),
                  strokeWidth = 1.4.dp.toPx(),
                )
              }
            }
            2 -> {
              drawCircle(
                color = strokeTint,
                radius = 9.dp.toPx(),
                center = Offset(x + 12.dp.toPx(), y + 12.dp.toPx()),
                style = stroke,
              )
              drawCircle(
                color = strokeTint,
                radius = 2.5.dp.toPx(),
                center = Offset(x + 12.dp.toPx(), y + 12.dp.toPx()),
              )
            }
            else -> {
              val bx = x + 4.dp.toPx()
              val by = y + 12.dp.toPx()
              val p1 =
                Path().apply {
                  moveTo(bx, by)
                  lineTo(bx + 4.dp.toPx(), by + 4.dp.toPx())
                  lineTo(bx + 11.dp.toPx(), by - 5.dp.toPx())
                }
              val p2 =
                Path().apply {
                  moveTo(bx + 6.dp.toPx(), by + 2.dp.toPx())
                  lineTo(bx + 9.dp.toPx(), by + 4.dp.toPx())
                  lineTo(bx + 16.dp.toPx(), by - 5.dp.toPx())
                }
              drawPath(p1, color = strokeTint, style = stroke)
              drawPath(p2, color = strokeTint, style = stroke)
            }
          }
          x += stepX
          col++
        }
        y += stepY
        row++
      }
    }
  }
}

@Composable
fun CountryPickerDialog(
  onDismiss: () -> Unit,
  onSelect: (CountryDialInfo) -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val filtered =
    remember(query) {
      if (query.isBlank()) CountryData.countries
      else {
        CountryData.countries.filter {
          it.name.contains(query, ignoreCase = true) ||
            it.dialCode.contains(query, ignoreCase = true) ||
            it.isoCode.contains(query, ignoreCase = true)
        }
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    title = { Text("Sélectionner un pays", style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        BravSearchField(
          query = query,
          onQueryChange = { query = it },
          placeholder = "Pays ou indicatif (+225)",
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.height(320.dp)) {
          items(filtered, key = { it.isoCode }) { country ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { onSelect(country) }
                  .padding(horizontal = 12.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(country.flagEmoji, style = MaterialTheme.typography.titleLarge)
              Spacer(Modifier.width(12.dp))
              Text(
                text = country.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = country.dialCode,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Fermer") }
    },
  )
}

@Composable
fun PinEntryDialog(
  title: String,
  confirmLabel: String,
  errorText: String? = null,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var pin by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          repeat(4) { idx ->
            val filled = idx < pin.length
            Box(
              modifier =
                Modifier.size(14.dp)
                  .clip(CircleShape)
                  .background(
                    if (filled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                  )
            )
          }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
          value = pin,
          onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
          label = { Text("Code PIN (4 à 6 chiffres)") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          trailingIcon = {
            if (pin.isNotEmpty()) {
              IconButton(onClick = { pin = pin.dropLast(1) }) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Effacer")
              }
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("pin_input_field"),
        )
        if (!errorText.isNullOrBlank()) {
          Spacer(Modifier.height(8.dp))
          Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { if (pin.length >= 4) onConfirm(pin) },
        enabled = pin.length >= 4,
      ) {
        Text(confirmLabel)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Annuler") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
  onDismiss: () -> Unit,
  onPickMedia: () -> Unit,
  onCapturePhoto: () -> Unit,
  onPickDocument: () -> Unit,
  onPickAudio: () -> Unit,
  onRecordVoice: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
      Text(
        text = "Joindre un contenu",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      )
      Spacer(Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        AttachmentTile(
          icon = Icons.Rounded.PhotoLibrary,
          label = "Galerie",
          tint = MaterialTheme.colorScheme.primary,
          testTag = "attach_gallery",
          onClick = {
            onDismiss()
            onPickMedia()
          },
        )
        AttachmentTile(
          icon = Icons.Rounded.CameraAlt,
          label = "Caméra",
          tint = Color(0xFF0E7490),
          testTag = "attach_camera",
          onClick = {
            onDismiss()
            onCapturePhoto()
          },
        )
        AttachmentTile(
          icon = Icons.Rounded.Description,
          label = "PDF / Doc",
          tint = Color(0xFF6D28D9),
          testTag = "attach_document",
          onClick = {
            onDismiss()
            onPickDocument()
          },
        )
      }
      Spacer(Modifier.height(14.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        AttachmentTile(
          icon = Icons.Rounded.AudioFile,
          label = "Audio",
          tint = Color(0xFFD97706),
          testTag = "attach_audio",
          onClick = {
            onDismiss()
            onPickAudio()
          },
        )
        AttachmentTile(
          icon = Icons.Rounded.Mic,
          label = "Vocal",
          tint = Color(0xFFE11D48),
          testTag = "attach_voice",
          onClick = {
            onDismiss()
            onRecordVoice()
          },
        )
        Spacer(Modifier.width(84.dp))
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun AttachmentTile(
  icon: ImageVector,
  label: String,
  tint: Color,
  testTag: String,
  onClick: () -> Unit,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      Modifier.width(84.dp)
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .minimumInteractiveComponentSize()
        .padding(8.dp)
        .testTag(testTag),
  ) {
    Surface(
      shape = CircleShape,
      color = tint.copy(alpha = 0.11f),
      modifier = Modifier.size(54.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(25.dp))
      }
    }
    Spacer(Modifier.height(8.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
    )
  }
}
