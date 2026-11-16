package com.example.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NoiseAware
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.model.ActivityLog
import com.example.model.CallLog
import com.example.model.ConnectedDevice
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.sms.SmsRepository
import com.example.ui.components.BravAvatar
import com.example.ui.components.PinEntryDialog
import com.example.ui.theme.LocalBravColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class ProfileSectionFilter(val label: String) {
  ALL("Tout"),
  PROFILE("Profil"),
  AI("IA Gemini"),
  SECURITY("Sécurité"),
  MEDIA_CALLS("Appels & SMS"),
  ACTIVITY("Activité"),
}

private val AI_TONES = listOf("Professionnel", "Amical", "Concis", "Enthousiaste")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileAndSettingsScreen(
  profile: UserProfile?,
  callLogsState: UiState<List<CallLog>>,
  activityLogsState: UiState<List<ActivityLog>>,
  devicesState: UiState<List<ConnectedDevice>>,
  isDefaultSmsApp: Boolean,
  onBack: () -> Unit,
  onDefaultSmsResult: () -> Unit,
  onUpdateProfile: suspend (updated: UserProfile, logTitle: String) -> Result<Unit>,
  onUpdateProfilePhoto: suspend (uri: Uri?) -> Result<Unit>,
  onOpenAiAssistant: () -> Unit,
  onConfigureBiometric: (pin: String, enabled: Boolean) -> Unit,
  onRevokeDevice: (deviceId: String) -> Unit,
  onSignOut: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val bravColors = LocalBravColors.current
  val smsRepository = remember { SmsRepository(context) }
  val snackbarHostState = remember { SnackbarHostState() }

  var selectedSection by remember { mutableStateOf(ProfileSectionFilter.ALL) }
  var showEditDialog by remember { mutableStateOf(false) }
  var showPhotoPickerSheet by remember { mutableStateOf(false) }
  var isUpdatingPhoto by remember { mutableStateOf(false) }
  var showPinSetupDialog by remember { mutableStateOf(false) }
  var showDevicesDialog by remember { mutableStateOf(false) }

  fun applyPhotoUri(uri: Uri?) {
    scope.launch {
      isUpdatingPhoto = true
      val res = onUpdateProfilePhoto(uri)
      isUpdatingPhoto = false
      snackbarHostState.showSnackbar(
        if (res.isSuccess) {
          if (uri == null) "Photo de profil supprimée" else "Photo de profil mise à jour"
        } else {
          res.exceptionOrNull()?.localizedMessage ?: "Échec de mise à jour de la photo"
        }
      )
    }
  }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        applyPhotoUri(uri)
      }
    }

  var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
  val takePhotoLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
      val uri = cameraOutputUri
      if (success && uri != null) {
        applyPhotoUri(uri)
      }
    }

  val cameraPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        val photoFile = File(context.cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
        val uri =
          FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
        cameraOutputUri = uri
        takePhotoLauncher.launch(uri)
      }
    }

  val defaultSmsLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      onDefaultSmsResult()
    }

  val devices = (devicesState as? UiState.Success)?.data ?: emptyList()
  val activities = (activityLogsState as? UiState.Success)?.data ?: emptyList()
  val calls = (callLogsState as? UiState.Success)?.data ?: emptyList()

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
          navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("profile_back_button")) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
            }
          },
          title = {
            Text(
              text = "Profil & Paramètres",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
          },
          colors =
            TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        HorizontalDivider(thickness = 0.6.dp, color = bravColors.hairline)
      }
    },
  ) { innerPadding ->
    if (profile == null) {
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
      ) {
        Text("Profil indisponible")
      }
      return@Scaffold
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        LazyRow(
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(ProfileSectionFilter.entries) { filter ->
            val selected = selectedSection == filter
            FilterChip(
              selected = selected,
              onClick = { selectedSection = filter },
              label = {
                Text(
                  text = filter.label,
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
      }

      // 1. CARTE PROFIL PERSONNEL + MODIFICATION PHOTO
      if (selectedSection == ProfileSectionFilter.ALL || selectedSection == ProfileSectionFilter.PROFILE) {
        item {
          Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
          ) {
            Column(modifier = Modifier.padding(20.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier =
                    Modifier.clip(CircleShape)
                      .clickable(enabled = !isUpdatingPhoto) { showPhotoPickerSheet = true }
                      .testTag("change_profile_photo_button"),
                  contentAlignment = Alignment.Center,
                ) {
                  BravAvatar(
                    name = profile.displayName,
                    avatarUrl = profile.avatarUrl,
                    size = 76.dp,
                    isOnline = false,
                  )
                  if (isUpdatingPhoto) {
                    Box(
                      modifier =
                        Modifier.size(76.dp)
                          .clip(CircleShape)
                          .background(Color.Black.copy(alpha = 0.4f)),
                      contentAlignment = Alignment.Center,
                    ) {
                      CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(24.dp),
                      )
                    }
                  }
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                      Modifier.align(Alignment.BottomEnd)
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(2.dp),
                  ) {
                    Box(
                      modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                      contentAlignment = Alignment.Center,
                    ) {
                      Icon(
                        Icons.Rounded.CameraAlt,
                        contentDescription = "Changer la photo de profil",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp),
                      )
                    }
                  }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                  )
                  Spacer(Modifier.height(4.dp))
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                  ) {
                    Text(
                      text = profile.bravId,
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                  }
                  Spacer(Modifier.height(4.dp))
                  Text(
                    text = profile.phoneNumber.ifBlank { profile.countryCode },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  modifier = Modifier.size(42.dp),
                ) {
                  IconButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.testTag("edit_profile_button"),
                  ) {
                    Icon(
                      Icons.Rounded.Edit,
                      contentDescription = "Modifier le profil",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
              }
              if (profile.bioStatus.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = profile.bioStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                  )
                }
              }
            }
          }
        }
      }

      // 2. ASSISTANT IA GEMINI & RÉPONSE AUTOMATIQUE
      if (selectedSection == ProfileSectionFilter.ALL || selectedSection == ProfileSectionFilter.AI) {
        item {
          Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionLabel("Assistant IA Gemini")
            Spacer(Modifier.height(6.dp))
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surface,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column {
                SettingToggleRow(
                  icon = Icons.Rounded.AutoAwesome,
                  iconTint = MaterialTheme.colorScheme.primary,
                  title = "Réponse automatique IA",
                  subtitle = "Gemini répond automatiquement aux messages entrants",
                  checked = profile.aiAutoReplyEnabled,
                  testTag = "toggle_ai_autoreply",
                  onCheckedChange = { checked ->
                    scope.launch {
                      onUpdateProfile(
                        profile.copy(aiAutoReplyEnabled = checked),
                        if (checked) "Réponse automatique IA activée"
                        else "Réponse automatique IA désactivée",
                      )
                    }
                  },
                )
                HorizontalDivider(
                  thickness = 0.5.dp,
                  color = bravColors.hairline,
                  modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                )
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                  Text(
                    text = "Style des réponses Gemini",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                  )
                  Spacer(Modifier.height(8.dp))
                  FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    AI_TONES.forEach { tone ->
                      val selected = profile.aiAutoReplyTone == tone
                      FilterChip(
                        selected = selected,
                        onClick = {
                          scope.launch {
                            onUpdateProfile(
                              profile.copy(aiAutoReplyTone = tone),
                              "Style IA : $tone",
                            )
                          }
                        },
                        label = { Text(tone, style = MaterialTheme.typography.labelMedium) },
                        shape = CircleShape,
                        border = null,
                        colors =
                          FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                          ),
                      )
                    }
                  }
                }
                HorizontalDivider(
                  thickness = 0.5.dp,
                  color = bravColors.hairline,
                  modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                )
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clickable(onClick = onOpenAiAssistant)
                      .padding(horizontal = 16.dp, vertical = 14.dp)
                      .testTag("open_gemini_from_settings"),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  SettingIconBadge(icon = Icons.Rounded.AutoAwesome, tint = Color(0xFF0F9D7A))
                  Spacer(Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Discuter avec BRAV IA", style = MaterialTheme.typography.bodyLarge)
                    Text(
                      text = "Ouvrir l'assistant Gemini direct",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
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
          }
        }
      }

      // 3. SÉCURITÉ & CONFIDENTIALITÉ
      if (selectedSection == ProfileSectionFilter.ALL || selectedSection == ProfileSectionFilter.SECURITY) {
        item {
          Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionLabel("Sécurité & Confidentialité")
            Spacer(Modifier.height(6.dp))
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surface,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column {
                SettingToggleRow(
                  icon = Icons.Rounded.Fingerprint,
                  iconTint = Color(0xFF1254E8),
                  title = "Biométrie & Code PIN",
                  subtitle = "Verrouillage à l'ouverture",
                  checked = profile.biometricEnabled,
                  testTag = "toggle_biometric",
                  onCheckedChange = { wantEnable ->
                    if (wantEnable && profile.pinHash.isBlank()) {
                      showPinSetupDialog = true
                    } else {
                      onConfigureBiometric("", wantEnable)
                    }
                  },
                )
                HorizontalDivider(
                  thickness = 0.5.dp,
                  color = bravColors.hairline,
                  modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                )
                SettingToggleRow(
                  icon = Icons.Rounded.Visibility,
                  iconTint = Color(0xFF0E7490),
                  title = "Confirmations de lecture",
                  subtitle = "Afficher les coches bleues",
                  checked = profile.readReceipts,
                  onCheckedChange = { checked ->
                    scope.launch {
                      onUpdateProfile(
                        profile.copy(readReceipts = checked),
                        "Confidentialité modifiée",
                      )
                    }
                  },
                )
                HorizontalDivider(
                  thickness = 0.5.dp,
                  color = bravColors.hairline,
                  modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                )
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clickable { showDevicesDialog = true }
                      .padding(horizontal = 16.dp, vertical = 14.dp)
                      .testTag("connected_devices_row"),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  SettingIconBadge(icon = Icons.Rounded.Devices, tint = Color(0xFF5B21B6))
                  Spacer(Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Appareils connectés", style = MaterialTheme.typography.bodyLarge)
                    Text(
                      text = "${devices.size} session(s) active(s)",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
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
          }
        }
      }

      // 4. APPELS, SMS & MÉDIAS
      if (selectedSection == ProfileSectionFilter.ALL || selectedSection == ProfileSectionFilter.MEDIA_CALLS) {
        item {
          Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionLabel("Appels, SMS & Médias")
            Spacer(Modifier.height(6.dp))
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surface,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column {
                SettingToggleRow(
                  icon = Icons.Rounded.NoiseAware,
                  iconTint = Color(0xFF0F9D7A),
                  title = "Réduction du bruit DSP",
                  subtitle = "Voix claire pendant les appels",
                  checked = profile.noiseSuppressionEnabled,
                  testTag = "toggle_noise_suppression",
                  onCheckedChange = { checked ->
                    scope.launch {
                      onUpdateProfile(
                        profile.copy(noiseSuppressionEnabled = checked),
                        if (checked) "Anti-bruit activé" else "Anti-bruit désactivé",
                      )
                    }
                  },
                )
                HorizontalDivider(
                  thickness = 0.5.dp,
                  color = bravColors.hairline,
                  modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                )
                SettingToggleRow(
                  icon = Icons.Rounded.Hd,
                  iconTint = Color(0xFF1254E8),
                  title = "Appels vidéo HD",
                  subtitle = "Qualité vidéo optimisée",
                  checked = profile.hdVideoCalls,
                  onCheckedChange = { checked ->
                    scope.launch {
                      onUpdateProfile(
                        profile.copy(hdVideoCalls = checked),
                        "Qualité vidéo modifiée",
                      )
                    }
                  },
                )
                HorizontalDivider(
                  thickness = 0.5.dp,
                  color = bravColors.hairline,
                  modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                )
                SettingToggleRow(
                  icon = Icons.Rounded.Compress,
                  iconTint = Color(0xFFD97706),
                  title = "Compression intelligente",
                  subtitle = "Envoi rapide des photos",
                  checked = profile.autoCompressMedia,
                  onCheckedChange = { checked ->
                    scope.launch {
                      onUpdateProfile(
                        profile.copy(autoCompressMedia = checked),
                        "Compression médias",
                      )
                    }
                  },
                )
                HorizontalDivider(
                  thickness = 0.5.dp,
                  color = bravColors.hairline,
                  modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                )
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clickable(enabled = !isDefaultSmsApp) {
                        smsRepository.createDefaultSmsRoleIntent()?.let { defaultSmsLauncher.launch(it) }
                      }
                      .padding(horizontal = 16.dp, vertical = 14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  SettingIconBadge(icon = Icons.Rounded.Sms, tint = Color(0xFF0E7490))
                  Spacer(Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Application SMS principale", style = MaterialTheme.typography.bodyLarge)
                    Text(
                      text = if (isDefaultSmsApp) "Active par défaut" else "Touchez pour activer",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Surface(
                    shape = CircleShape,
                    color =
                      if (isDefaultSmsApp) MaterialTheme.colorScheme.secondaryContainer
                      else MaterialTheme.colorScheme.primaryContainer,
                  ) {
                    Text(
                      text = if (isDefaultSmsApp) "Active" else "Définir",
                      style = MaterialTheme.typography.labelMedium,
                      color =
                        if (isDefaultSmsApp) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary,
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 5. APPARENCE & NOTIFICATIONS
      if (selectedSection == ProfileSectionFilter.ALL || selectedSection == ProfileSectionFilter.PROFILE) {
        item {
          Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionLabel("Apparence & Notifications")
            Spacer(Modifier.height(6.dp))
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surface,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column {
                SettingToggleRow(
                  icon = Icons.Rounded.Notifications,
                  iconTint = Color(0xFFE11D48),
                  title = "Notifications",
                  subtitle = "Messages, SMS et appels",
                  checked = profile.notificationsEnabled,
                  onCheckedChange = { checked ->
                    scope.launch {
                      onUpdateProfile(
                        profile.copy(notificationsEnabled = checked),
                        "Notifications modifiées",
                      )
                    }
                  },
                )
                HorizontalDivider(
                  thickness = 0.5.dp,
                  color = bravColors.hairline,
                  modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                )
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingIconBadge(icon = Icons.Rounded.DarkMode, tint = Color(0xFF475569))
                    Spacer(Modifier.width(12.dp))
                    Text("Thème d'affichage", style = MaterialTheme.typography.bodyLarge)
                  }
                  Spacer(Modifier.height(10.dp))
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 48.dp),
                  ) {
                    listOf("LIGHT" to "Clair", "SYSTEM" to "Auto", "DARK" to "Sombre").forEach { (key, label) ->
                      val selected = profile.themeMode == key
                      FilterChip(
                        selected = selected,
                        onClick = {
                          scope.launch {
                            onUpdateProfile(profile.copy(themeMode = key), "Thème $label")
                          }
                        },
                        label = { Text(label) },
                        shape = CircleShape,
                        border = null,
                        colors =
                          FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                          ),
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // 6. HISTORIQUE D'ACTIVITÉ & JOURNAL D'APPELS
      if (selectedSection == ProfileSectionFilter.ALL || selectedSection == ProfileSectionFilter.ACTIVITY) {
        item {
          Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionLabel("Activité (${activities.size}) • Appels (${calls.size})")
            Spacer(Modifier.height(6.dp))
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surface,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                if (activities.isEmpty()) {
                  Text(
                    text = "Aucune activité récente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                } else {
                  activities.take(8).forEach { act ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      SettingIconBadge(
                        icon =
                          when (act.actionType) {
                            "CALL" -> Icons.Rounded.Call
                            "BIOMETRIC", "LOGIN" -> Icons.Rounded.Security
                            "AI_ASSISTANT" -> Icons.Rounded.AutoAwesome
                            else -> Icons.Rounded.History
                          },
                        tint = MaterialTheme.colorScheme.primary,
                      )
                      Spacer(Modifier.width(12.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(act.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                          text =
                            "${act.deviceModel} • ${formatDateShort(act.createdAt?.toDate()?.time ?: 0L)}",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
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

      item {
        Button(
          onClick = onSignOut,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
          shape = RoundedCornerShape(18.dp),
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp)
              .height(52.dp)
              .testTag("sign_out_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Déconnexion", style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }

  if (showPhotoPickerSheet && profile != null) {
    ModalBottomSheet(
      onDismissRequest = { showPhotoPickerSheet = false },
      containerColor = MaterialTheme.colorScheme.surface,
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(bottom = 26.dp)) {
        Text(
          text = "Photo de profil",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clickable {
                showPhotoPickerSheet = false
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              }
              .padding(horizontal = 20.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SettingIconBadge(icon = Icons.Rounded.PhotoLibrary, tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(14.dp))
          Text("Choisir dans la galerie", style = MaterialTheme.typography.bodyLarge)
        }
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clickable {
                showPhotoPickerSheet = false
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
              }
              .padding(horizontal = 20.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SettingIconBadge(icon = Icons.Rounded.CameraAlt, tint = Color(0xFF0E7490))
          Spacer(Modifier.width(14.dp))
          Text("Prendre une photo", style = MaterialTheme.typography.bodyLarge)
        }
        if (profile.avatarUrl.isNotBlank()) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable {
                  showPhotoPickerSheet = false
                  applyPhotoUri(null)
                }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            SettingIconBadge(icon = Icons.Rounded.DeleteOutline, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(14.dp))
            Text(
              text = "Supprimer la photo actuelle",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }
  }

  if (showEditDialog && profile != null) {
    var firstName by remember { mutableStateOf(profile.firstName) }
    var lastName by remember { mutableStateOf(profile.lastName) }
    var bio by remember { mutableStateOf(profile.bioStatus) }
    var phone by remember { mutableStateOf(profile.phoneNumber) }

    AlertDialog(
      onDismissRequest = { showEditDialog = false },
      containerColor = MaterialTheme.colorScheme.surface,
      title = { Text("Modifier le profil", style = MaterialTheme.typography.titleLarge) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                  showEditDialog = false
                  showPhotoPickerSheet = true
                }
                .padding(vertical = 4.dp),
          ) {
            BravAvatar(name = profile.displayName, avatarUrl = profile.avatarUrl, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Text(
              text = "Changer la photo de profil",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("Prénom") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Nom") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = bio,
            onValueChange = { if (it.length <= 140) bio = it },
            label = { Text("Statut / Bio") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Téléphone") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val full =
              listOf(firstName.trim(), lastName.trim())
                .filter { it.isNotBlank() }
                .joinToString(" ")
            scope.launch {
              onUpdateProfile(
                profile.copy(
                  displayName = full.ifBlank { profile.displayName },
                  firstName = firstName.trim(),
                  lastName = lastName.trim(),
                  bioStatus = bio.trim(),
                  phoneNumber = phone.trim(),
                ),
                "Profil mis à jour",
              )
              showEditDialog = false
            }
          },
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("Enregistrer")
        }
      },
      dismissButton = {
        TextButton(onClick = { showEditDialog = false }) { Text("Annuler") }
      },
    )
  }

  if (showPinSetupDialog) {
    PinEntryDialog(
      title = "Configurer le code PIN",
      confirmLabel = "Activer",
      onDismiss = { showPinSetupDialog = false },
      onConfirm = { pin ->
        onConfigureBiometric(pin, true)
        showPinSetupDialog = false
      },
    )
  }

  if (showDevicesDialog) {
    AlertDialog(
      onDismissRequest = { showDevicesDialog = false },
      containerColor = MaterialTheme.colorScheme.surface,
      title = { Text("Appareils connectés", style = MaterialTheme.typography.titleLarge) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          if (devices.isEmpty()) {
            Text("Aucun appareil actif")
          } else {
            devices.forEach { dev ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.Rounded.PhoneAndroid, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(dev.deviceName, style = MaterialTheme.typography.titleSmall)
                  Text(
                    text =
                      "${dev.osVersion} • ${formatDateShort(dev.lastActiveAt?.toDate()?.time ?: 0L)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(onClick = { onRevokeDevice(dev.id) }) {
                  Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Révoquer",
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showDevicesDialog = false }) { Text("Fermer") }
      },
    )
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 4.dp),
  )
}

@Composable
private fun SettingIconBadge(
  icon: ImageVector,
  tint: Color,
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = tint.copy(alpha = 0.11f),
    modifier = Modifier.size(36.dp),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
    }
  }
}

@Composable
private fun SettingToggleRow(
  icon: ImageVector,
  iconTint: Color,
  title: String,
  subtitle: String,
  checked: Boolean,
  testTag: String = "",
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable { onCheckedChange(!checked) }
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .let { if (testTag.isNotBlank()) it.testTag(testTag) else it },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SettingIconBadge(icon = icon, tint = iconTint)
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

private fun formatDateShort(timeMs: Long): String {
  if (timeMs <= 0L) return "Maintenant"
  return SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(timeMs))
}
