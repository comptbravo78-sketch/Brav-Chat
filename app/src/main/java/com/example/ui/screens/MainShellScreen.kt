package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.SmsMessageItem
import com.example.model.UiState
import com.example.model.YouTubeTrack
import com.example.notifications.BravNotificationHelper
import com.example.ui.components.BravAvatar
import com.example.ui.components.BravLogoMark
import com.example.ui.components.BravSearchField
import com.example.ui.theme.LocalBravColors
import com.example.viewmodel.BravViewModel
import kotlinx.coroutines.launch

enum class BravTab(
  val label: String,
  val activeIcon: ImageVector,
  val inactiveIcon: ImageVector,
  val tag: String,
) {
  MESSAGES("Messages", Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline, "tab_messages"),
  ACTUS("Actus", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "tab_actus"),
  MUSIC("Musique", Icons.Rounded.MusicNote, Icons.Outlined.MusicNote, "tab_music"),
  CALLS("Appels", Icons.Rounded.Call, Icons.Outlined.Call, "tab_calls"),
  CONTACTS("Contacts", Icons.Rounded.Contacts, Icons.Outlined.Contacts, "tab_contacts"),
}

sealed interface ActiveSubScreen {
  data object None : ActiveSubScreen
  data class CloudChat(
    val conversationId: String,
    val title: String,
    val peerPhone: String,
    val peerAvatarUrl: String,
    val memberUids: List<String>,
  ) : ActiveSubScreen

  data class SmsChat(
    val threadId: Long,
    val address: String,
    val title: String,
    val avatarUrl: String?,
  ) : ActiveSubScreen

  data object GeminiAssistant : ActiveSubScreen
  data object Profile : ActiveSubScreen
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainShellScreen(
  viewModel: BravViewModel,
  initialSmsRecipient: String? = null,
  openMusicRequestCount: Int = 0,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val bravColors = LocalBravColors.current
  val activity = context as? Activity
  val widthClass =
    if (activity != null) calculateWindowSizeClass(activity).widthSizeClass
    else WindowWidthSizeClass.Compact

  val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
  val conversationsState by viewModel.conversations.collectAsStateWithLifecycle()
  val statusesState by viewModel.statuses.collectAsStateWithLifecycle()
  val callLogsState by viewModel.callLogs.collectAsStateWithLifecycle()
  val activityLogsState by viewModel.activityLogs.collectAsStateWithLifecycle()
  val devicesState by viewModel.devices.collectAsStateWithLifecycle()
  val smsThreads by viewModel.smsThreads.collectAsStateWithLifecycle()
  val contacts by viewModel.syncedContacts.collectAsStateWithLifecycle()
  val isDefaultSms by viewModel.isDefaultSmsApp.collectAsStateWithLifecycle()
  val hasSmsPerm by viewModel.hasSmsPermission.collectAsStateWithLifecycle()
  val hasContactsPerm by viewModel.contactsPermissionGranted.collectAsStateWithLifecycle()
  val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
  val dspCapabilities by viewModel.dspCapabilities.collectAsStateWithLifecycle()
  val showBiometricOptIn by viewModel.justRegisteredPromptBiometric.collectAsStateWithLifecycle()

  val aiMessages by viewModel.aiMessages.collectAsStateWithLifecycle()
  val isAiStreaming by viewModel.isAiStreaming.collectAsStateWithLifecycle()
  val aiError by viewModel.aiError.collectAsStateWithLifecycle()

  val musicTracksState by viewModel.musicTracks.collectAsStateWithLifecycle()
  val musicQuery by viewModel.musicQuery.collectAsStateWithLifecycle()
  val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
  val isMusicPlaying by viewModel.isMusicPlaying.collectAsStateWithLifecycle()
  val isMusicBuffering by viewModel.isMusicBuffering.collectAsStateWithLifecycle()
  val clipDisplayMode by viewModel.clipDisplayMode.collectAsStateWithLifecycle()
  val autoPlayNextEnabled by viewModel.autoPlayNextEnabled.collectAsStateWithLifecycle()
  val musicPlayerError by viewModel.musicPlayerError.collectAsStateWithLifecycle()

  val userProfile = (profileState as? UiState.Success)?.data
  var currentTab by rememberSaveable { mutableStateOf(BravTab.MESSAGES) }
  var subScreen by remember { mutableStateOf<ActiveSubScreen>(ActiveSubScreen.None) }
  var isSearching by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var notifPermissionPrompted by rememberSaveable { mutableStateOf(false) }
  var trackToShare by remember { mutableStateOf<YouTubeTrack?>(null) }

  LaunchedEffect(openMusicRequestCount) {
    if (openMusicRequestCount > 0) {
      subScreen = ActiveSubScreen.None
      currentTab = BravTab.MUSIC
    }
  }

  val totalUnreadCount =
    remember(conversationsState, smsThreads) {
      val cloudUnread =
        ((conversationsState as? UiState.Success)?.data ?: emptyList())
          .filter { !it.isArchived }
          .sumOf { it.unreadCount.toInt() }
      val smsUnread = smsThreads.filter { !it.isArchived }.sumOf { it.unreadCount }
      cloudUnread + smsUnread
    }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

  LaunchedEffect(userProfile?.notificationsEnabled) {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        userProfile?.notificationsEnabled != false &&
        !notifPermissionPrompted
    ) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notifPermissionPrompted = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  LaunchedEffect(activeCall, userProfile?.notificationsEnabled) {
    val call = activeCall
    if (call != null && userProfile?.notificationsEnabled != false) {
      BravNotificationHelper.showOngoingCallNotification(context, call.peerName, call.isVideo)
    } else {
      BravNotificationHelper.cancelCallNotification(context)
    }
  }

  LaunchedEffect(initialSmsRecipient) {
    if (!initialSmsRecipient.isNullOrBlank()) {
      subScreen =
        ActiveSubScreen.SmsChat(
          threadId = 0L,
          address = initialSmsRecipient,
          title = initialSmsRecipient,
          avatarUrl = null,
        )
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    if (currentTrack != null) {
      var fallbackContainer by remember { mutableStateOf<FrameLayout?>(null) }
      DisposableEffect(viewModel.musicHost, fallbackContainer) {
        val frame = fallbackContainer
        if (frame != null) {
          viewModel.musicHost.registerFallbackContainer(frame)
        }
        onDispose {
          if (frame != null) {
            viewModel.musicHost.unregisterFallbackContainer(frame)
          }
        }
      }
      AndroidView(
        factory = { ctx ->
          FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            fallbackContainer = this
            viewModel.musicHost.registerFallbackContainer(this)
          }
        },
        modifier = Modifier.size(1.dp).alpha(0.01f),
      )
    }

  AnimatedContent(
    targetState = subScreen,
    transitionSpec = { fadeIn(tween(190)) togetherWith fadeOut(tween(150)) },
    label = "subscreen_transition",
  ) { screen ->
    when (screen) {
      is ActiveSubScreen.Profile -> {
        ProfileAndSettingsScreen(
          profile = userProfile,
          callLogsState = callLogsState,
          activityLogsState = activityLogsState,
          devicesState = devicesState,
          isDefaultSmsApp = isDefaultSms,
          onBack = { subScreen = ActiveSubScreen.None },
          onDefaultSmsResult = viewModel::onDefaultSmsRoleChanged,
          onUpdateProfile = viewModel::updateUserProfile,
          onUpdateProfilePhoto = viewModel::updateProfilePhoto,
          onOpenAiAssistant = { subScreen = ActiveSubScreen.GeminiAssistant },
          onConfigureBiometric = viewModel::enableBiometricAndPin,
          onRevokeDevice = viewModel::revokeDevice,
          onSignOut = onSignOut,
        )
      }
      is ActiveSubScreen.GeminiAssistant -> {
        GeminiAssistantScreen(
          messages = aiMessages,
          isStreaming = isAiStreaming,
          errorMessage = aiError,
          autoReplyEnabled = userProfile?.aiAutoReplyEnabled ?: false,
          onBack = { subScreen = ActiveSubScreen.None },
          onSendPrompt = viewModel::sendAiPrompt,
          onRetryLast = viewModel::retryLastAiPrompt,
          onClearChat = viewModel::clearAiChat,
        )
      }
      is ActiveSubScreen.CloudChat -> {
        val messages by
          remember(screen.conversationId) { viewModel.observeMessages(screen.conversationId) }
            .collectAsState(initial = emptyList())
        LaunchedEffect(messages, userProfile?.aiAutoReplyEnabled) {
          viewModel.checkAndTriggerAutoReply(
            conversationId = screen.conversationId,
            peerTitle = screen.title,
            memberUids = screen.memberUids,
            messages = messages,
          )
        }
        ChatDetailScreen(
          title = screen.title,
          peerPhone = screen.peerPhone,
          peerAvatarUrl = screen.peerAvatarUrl,
          isSmsMode = false,
          currentUserId = viewModel.currentUserId,
          cloudMessages = messages,
          smsMessages = emptyList(),
          autoCompressMedia = userProfile?.autoCompressMedia ?: true,
          aiAutoReplyEnabled = userProfile?.aiAutoReplyEnabled ?: false,
          onGenerateAiReply = { transcript ->
            viewModel.generateSmartReply(screen.title, transcript, isAutoResponder = false)
          },
          onBack = { subScreen = ActiveSubScreen.None },
          onStartCall = { isVideo ->
            viewModel.startCall(screen.title, screen.peerPhone, screen.peerAvatarUrl, isVideo)
          },
          onSendCloudMessage = { text, draft ->
            viewModel.sendChatMessage(screen.conversationId, screen.memberUids, text, draft)
          },
          onRetryCloudMessage = { msgId -> viewModel.retryMessage(screen.conversationId, msgId) },
          onSendSms = { _, _, _ -> Result.success(Unit) },
        )
      }
      is ActiveSubScreen.SmsChat -> {
        var smsList by remember(screen.threadId, screen.address) {
          mutableStateOf<List<SmsMessageItem>>(emptyList())
        }
        LaunchedEffect(screen.threadId, screen.address, smsThreads) {
          smsList = viewModel.loadSmsMessages(screen.threadId, screen.address)
        }
        ChatDetailScreen(
          title = screen.title,
          peerPhone = screen.address,
          peerAvatarUrl = screen.avatarUrl,
          isSmsMode = true,
          currentUserId = viewModel.currentUserId,
          cloudMessages = emptyList(),
          smsMessages = smsList,
          autoCompressMedia = userProfile?.autoCompressMedia ?: true,
          aiAutoReplyEnabled = userProfile?.aiAutoReplyEnabled ?: false,
          onGenerateAiReply = { transcript ->
            viewModel.generateSmartReply(screen.title, transcript, isAutoResponder = false)
          },
          onBack = { subScreen = ActiveSubScreen.None },
          onStartCall = { isVideo ->
            viewModel.startCall(screen.title, screen.address, screen.avatarUrl.orEmpty(), isVideo)
          },
          onSendCloudMessage = { _, _ -> Result.success("") },
          onRetryCloudMessage = {},
          onSendSms = { body, uri, mime ->
            val res = viewModel.sendSms(screen.address, body, uri, mime)
            if (res.isSuccess) {
              smsList = viewModel.loadSmsMessages(screen.threadId, screen.address)
            }
            res
          },
        )
      }
      ActiveSubScreen.None -> {
        val useNavRail = widthClass != WindowWidthSizeClass.Compact
        Scaffold(
          containerColor = MaterialTheme.colorScheme.background,
          contentWindowInsets = WindowInsets.safeDrawing,
          topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
              TopAppBar(
                title = {
                  AnimatedContent(
                    targetState = isSearching,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) },
                    label = "topbar_search",
                  ) { searching ->
                    if (searching) {
                      BravSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "Rechercher...",
                        modifier = Modifier.padding(end = 4.dp).testTag("global_search_input"),
                      )
                    } else {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentTab == BravTab.MESSAGES) {
                          BravLogoMark(size = 28.dp)
                          Spacer(Modifier.width(10.dp))
                        }
                        Text(
                          text =
                            when (currentTab) {
                              BravTab.MESSAGES -> "BRAV CHAT"
                              BravTab.ACTUS -> "Actus 24H"
                              BravTab.MUSIC -> "Musique"
                              BravTab.CALLS -> "Appels"
                              BravTab.CONTACTS -> "Contacts"
                            },
                          style = MaterialTheme.typography.titleLarge,
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.onSurface,
                        )
                      }
                    }
                  }
                },
                actions = {
                  if (currentTab != BravTab.MUSIC) {
                    Surface(
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                      modifier = Modifier.size(40.dp),
                    ) {
                      IconButton(
                        onClick = {
                          isSearching = !isSearching
                          if (!isSearching) searchQuery = ""
                        },
                        modifier = Modifier.testTag("toggle_search_button"),
                      ) {
                        Icon(
                          if (isSearching) Icons.Rounded.Close else Icons.Rounded.Search,
                          contentDescription =
                            if (isSearching) "Fermer la recherche" else "Rechercher",
                          tint = MaterialTheme.colorScheme.onSurface,
                          modifier = Modifier.size(20.dp),
                        )
                      }
                    }
                    Spacer(Modifier.width(8.dp))
                  }
                  Box(
                    modifier =
                      Modifier.padding(end = 14.dp)
                        .clip(CircleShape)
                        .clickable { subScreen = ActiveSubScreen.Profile }
                        .testTag("profile_avatar_button"),
                    contentAlignment = Alignment.Center,
                  ) {
                    BravAvatar(
                      name = userProfile?.displayName ?: "Profil",
                      avatarUrl = userProfile?.avatarUrl,
                      size = 38.dp,
                      isOnline = true,
                    )
                  }
                },
                colors =
                  TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                  ),
              )
              HorizontalDivider(thickness = 0.6.dp, color = bravColors.hairline)
            }
          },
          bottomBar = {
            if (!useNavRail) {
              Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                val track = currentTrack
                if (track != null && currentTab != BravTab.MUSIC) {
                  HorizontalDivider(thickness = 0.6.dp, color = bravColors.hairline)
                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .clickable { currentTab = BravTab.MUSIC }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Surface(
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.primaryContainer,
                      modifier = Modifier.size(36.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          if (isMusicPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.Headphones,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(18.dp),
                        )
                      }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = track.title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text =
                          "${track.channelTitle} • ${if (isMusicPlaying) "En lecture" else "En pause"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    IconButton(
                      onClick = viewModel::togglePlayPauseMusic,
                      modifier = Modifier.size(36.dp).testTag("mini_player_play_pause"),
                    ) {
                      Icon(
                        if (isMusicPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isMusicPlaying) "Pause" else "Lire",
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    }
                    IconButton(
                      onClick = viewModel::playNextTrack,
                      modifier = Modifier.size(36.dp).testTag("mini_player_next"),
                    ) {
                      Icon(Icons.Rounded.SkipNext, contentDescription = "Suivant")
                    }
                    IconButton(
                      onClick = viewModel::stopMusic,
                      modifier = Modifier.size(34.dp).testTag("mini_player_stop"),
                    ) {
                      Icon(Icons.Rounded.Close, contentDescription = "Arrêter")
                    }
                  }
                }
                HorizontalDivider(thickness = 0.6.dp, color = bravColors.hairline)
                NavigationBar(
                  containerColor = MaterialTheme.colorScheme.surface,
                  tonalElevation = 0.dp,
                ) {
                  BravTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                      selected = selected,
                      onClick = { currentTab = tab },
                      icon = {
                        val iconVec = if (selected) tab.activeIcon else tab.inactiveIcon
                        if (tab == BravTab.MESSAGES && totalUnreadCount > 0) {
                          BadgedBox(
                            badge = {
                              Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                              ) {
                                Text(
                                  text = if (totalUnreadCount > 99) "99+" else "$totalUnreadCount",
                                  style = MaterialTheme.typography.labelSmall,
                                )
                              }
                            }
                          ) {
                            Icon(iconVec, contentDescription = tab.label)
                          }
                        } else {
                          Icon(iconVec, contentDescription = tab.label)
                        }
                      },
                      label = {
                        Text(
                          text = tab.label,
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                      },
                      colors =
                        NavigationBarItemDefaults.colors(
                          selectedIconColor = MaterialTheme.colorScheme.primary,
                          selectedTextColor = MaterialTheme.colorScheme.primary,
                          indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                          unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                          unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                      modifier = Modifier.testTag(tab.tag),
                    )
                  }
                }
              }
            }
          },
        ) { innerPadding ->
          Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (useNavRail) {
              NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxHeight(),
                header = {
                  BravLogoMark(size = 34.dp, modifier = Modifier.padding(vertical = 10.dp))
                },
              ) {
                BravTab.entries.forEach { tab ->
                  val selected = currentTab == tab
                  NavigationRailItem(
                    selected = selected,
                    onClick = { currentTab = tab },
                    icon = {
                      Icon(
                        if (selected) tab.activeIcon else tab.inactiveIcon,
                        contentDescription = tab.label,
                      )
                    },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                    colors =
                      NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                      ),
                    modifier = Modifier.testTag(tab.tag),
                  )
                }
              }
            }

            AnimatedContent(
              targetState = currentTab,
              transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
              modifier = Modifier.weight(1f).fillMaxSize(),
              label = "tab_content_transition",
            ) { tab ->
              when (tab) {
                BravTab.MESSAGES -> {
                  MessagesListScreen(
                    conversationsState = conversationsState,
                    smsThreads = smsThreads,
                    isDefaultSmsApp = isDefaultSms,
                    hasSmsPermission = hasSmsPerm,
                    aiAutoReplyEnabled = userProfile?.aiAutoReplyEnabled ?: false,
                    searchQuery = searchQuery,
                    onDefaultSmsResult = viewModel::onDefaultSmsRoleChanged,
                    onSmsPermissionGranted = viewModel::refreshSmsSync,
                    onOpenGeminiAssistant = { subScreen = ActiveSubScreen.GeminiAssistant },
                    onOpenCloudConversation = { conv ->
                      subScreen =
                        ActiveSubScreen.CloudChat(
                          conversationId = conv.id,
                          title = conv.title,
                          peerPhone = conv.peerPhone,
                          peerAvatarUrl = conv.peerAvatarUrl,
                          memberUids = conv.memberUids,
                        )
                    },
                    onOpenSmsThread = { thread ->
                      subScreen =
                        ActiveSubScreen.SmsChat(
                          threadId = thread.threadId,
                          address = thread.address,
                          title = thread.contactName,
                          avatarUrl = thread.contactPhotoUri,
                        )
                    },
                    onNewChatWithNumber = { name, phone, asSms ->
                      if (asSms) {
                        subScreen =
                          ActiveSubScreen.SmsChat(
                            threadId = 0L,
                            address = phone,
                            title = name,
                            avatarUrl = null,
                          )
                      } else {
                        scope.launch {
                          val res = viewModel.getOrCreateConversation(name, "", phone, "")
                          res.getOrNull()?.let { id ->
                            subScreen =
                              ActiveSubScreen.CloudChat(
                                conversationId = id,
                                title = name,
                                peerPhone = phone,
                                peerAvatarUrl = "",
                                memberUids = listOf(viewModel.currentUserId),
                              )
                          }
                        }
                      }
                    },
                    onTogglePinCloud = viewModel::togglePinConversation,
                    onToggleArchiveCloud = viewModel::toggleArchiveConversation,
                    onDeleteCloud = { viewModel.deleteConversation(it.id) },
                    onTogglePinSms = { viewModel.togglePinSms(it.threadId) },
                    onToggleArchiveSms = { viewModel.toggleArchiveSms(it.threadId) },
                    onDeleteSms = { viewModel.deleteSms(it.threadId) },
                  )
                }
                BravTab.ACTUS -> {
                  StatusActusScreen(
                    statusesState = statusesState,
                    currentUserId = viewModel.currentUserId,
                    userProfile = userProfile,
                    searchQuery = searchQuery,
                    onPublishTextStatus = viewModel::publishTextStatus,
                    onPublishMediaStatus = viewModel::publishMediaStatus,
                    onMarkStatusViewed = viewModel::markStatusViewed,
                    onObserveViews = viewModel::observeStatusViews,
                    onDeleteStatus = viewModel::deleteStatus,
                  )
                }
                BravTab.MUSIC -> {
                  MusicSpaceScreen(
                    tracksState = musicTracksState,
                    searchQuery = musicQuery,
                    currentTrack = currentTrack,
                    isPlaying = isMusicPlaying,
                    isBuffering = isMusicBuffering,
                    clipDisplayMode = clipDisplayMode,
                    autoPlayNextEnabled = autoPlayNextEnabled,
                    playerError = musicPlayerError,
                    musicHost = viewModel.musicHost,
                    isApiKeyConfigured = viewModel.isYouTubeApiConfigured,
                    onEnsureTrendingLoaded = viewModel::ensureTrendingMusicLoaded,
                    onSearchQuery = viewModel::searchYouTubeMusic,
                    onPlayTrack = viewModel::playTrack,
                    onPlayUrlOrId = viewModel::playTrackFromUrl,
                    onTogglePlayPause = viewModel::togglePlayPauseMusic,
                    onPreviousTrack = viewModel::playPreviousTrack,
                    onNextTrack = viewModel::playNextTrack,
                    onStopTrack = viewModel::stopMusic,
                    onChangeClipMode = viewModel::setClipDisplayMode,
                    onToggleAutoPlay = viewModel::toggleAutoPlayNext,
                    onShareTrack = { trackToShare = it },
                  )
                }
                BravTab.CALLS -> {
                  CallLogsScreen(
                    callLogsState = callLogsState,
                    searchQuery = searchQuery,
                    onStartCall = viewModel::startCall,
                  )
                }
                BravTab.CONTACTS -> {
                  ContactsScreen(
                    contacts = contacts,
                    hasContactsPermission = hasContactsPerm,
                    searchQuery = searchQuery,
                    onPermissionGranted = viewModel::refreshContactsSync,
                    onRefreshSync = viewModel::refreshContactsSync,
                    onOpenChat = { contact ->
                      if (contact.bravUser != null) {
                        scope.launch {
                          val peer = contact.bravUser
                          val res =
                            viewModel.getOrCreateConversation(
                              title = contact.name,
                              peerUserId = peer.userId,
                              peerPhone = contact.phoneNumber,
                              peerAvatarUrl = peer.avatarUrl,
                            )
                          res.getOrNull()?.let { id ->
                            subScreen =
                              ActiveSubScreen.CloudChat(
                                conversationId = id,
                                title = contact.name,
                                peerPhone = contact.phoneNumber,
                                peerAvatarUrl = peer.avatarUrl,
                                memberUids = listOf(viewModel.currentUserId, peer.userId).sorted(),
                              )
                          }
                        }
                      } else {
                        subScreen =
                          ActiveSubScreen.SmsChat(
                            threadId = 0L,
                            address = contact.phoneNumber,
                            title = contact.name,
                            avatarUrl = contact.photoUri,
                          )
                      }
                    },
                    onStartAudioCall = { contact ->
                      viewModel.startCall(
                        peerName = contact.name,
                        peerPhone = contact.phoneNumber,
                        peerAvatarUrl =
                          contact.bravUser?.avatarUrl?.takeIf { it.isNotBlank() }
                            ?: contact.photoUri.orEmpty(),
                        isVideo = false,
                      )
                    },
                    onStartVideoCall = { contact ->
                      viewModel.startCall(
                        peerName = contact.name,
                        peerPhone = contact.phoneNumber,
                        peerAvatarUrl =
                          contact.bravUser?.avatarUrl?.takeIf { it.isNotBlank() }
                            ?: contact.photoUri.orEmpty(),
                        isVideo = true,
                      )
                    },
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  trackToShare?.let { track ->
    val convList = (conversationsState as? UiState.Success)?.data ?: emptyList()
    AlertDialog(
      onDismissRequest = { trackToShare = null },
      containerColor = MaterialTheme.colorScheme.surface,
      title = { Text("Partager la musique", style = MaterialTheme.typography.titleLarge) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "${track.title} — ${track.channelTitle}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          if (convList.isEmpty()) {
            Text(
              text = "Aucune discussion disponible pour le partage.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            LazyColumn(modifier = Modifier.height(220.dp)) {
              items(convList, key = { it.id }) { conv ->
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(12.dp))
                      .clickable {
                        scope.launch {
                          val text =
                            "🎵 ${track.title} — ${track.channelTitle}\nhttps://youtu.be/${track.videoId}"
                          viewModel.sendChatMessage(conv.id, conv.memberUids, text, null)
                          trackToShare = null
                          subScreen =
                            ActiveSubScreen.CloudChat(
                              conversationId = conv.id,
                              title = conv.title,
                              peerPhone = conv.peerPhone,
                              peerAvatarUrl = conv.peerAvatarUrl,
                              memberUids = conv.memberUids,
                            )
                        }
                      }
                      .padding(horizontal = 8.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  BravAvatar(name = conv.title, avatarUrl = conv.peerAvatarUrl, size = 38.dp)
                  Spacer(Modifier.width(12.dp))
                  Text(
                    text = conv.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { trackToShare = null }) { Text("Fermer") }
      },
    )
  }

  activeCall?.let { session ->
    ActiveCallOverlay(
      session = session,
      dspCapabilities = dspCapabilities,
      onToggleMute = viewModel::toggleCallMute,
      onToggleSpeaker = viewModel::toggleCallSpeaker,
      onToggleCamera = viewModel::toggleCallCamera,
      onSwitchCamera = viewModel::switchCallCamera,
      onToggleNoiseSuppression = viewModel::toggleCallNoiseSuppression,
      onEndCall = viewModel::endCall,
    )
  }

  if (showBiometricOptIn) {
    FirstLoginBiometricOptInDialog(
      onEnable = { pin -> viewModel.enableBiometricAndPin(pin, true) },
      onSkip = viewModel::dismissFirstLoginBiometricPrompt,
    )
  }
  }
}
