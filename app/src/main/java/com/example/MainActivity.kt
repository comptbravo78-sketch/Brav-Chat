package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.BravRepository
import com.example.model.UiState
import com.example.music.MusicPlaybackService
import com.example.notifications.BravNotificationHelper
import com.example.security.configureAppCheck
import com.example.ui.screens.AuthSignInScreen
import com.example.ui.screens.BiometricLockScreen
import com.example.ui.screens.MainShellScreen
import com.example.ui.screens.OnboardingRegistrationScreen
import com.example.ui.theme.BravChatTheme
import com.example.viewmodel.BravViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : FragmentActivity() {

  private var openMusicRequestCount by mutableIntStateOf(0)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    enableEdgeToEdge()
    BravNotificationHelper.ensureChannels(this)
    val initialSmsRecipient = extractSmsRecipient(intent)
    if (intent?.getBooleanExtra(MusicPlaybackService.EXTRA_OPEN_MUSIC_TAB, false) == true) {
      openMusicRequestCount++
    }

    setContent {
      BravChatRoot(
        initialSmsRecipient = initialSmsRecipient,
        openMusicRequestCount = openMusicRequestCount,
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (intent.getBooleanExtra(MusicPlaybackService.EXTRA_OPEN_MUSIC_TAB, false)) {
      openMusicRequestCount++
    }
  }

  private fun extractSmsRecipient(intent: Intent?): String? {
    val action = intent?.action ?: return null
    if (action == Intent.ACTION_SENDTO || action == Intent.ACTION_VIEW) {
      val schemeSpecific = intent.data?.schemeSpecificPart
      if (!schemeSpecific.isNullOrBlank()) {
        return schemeSpecific.substringBefore("?").trim()
      }
    }
    return intent.getStringExtra("address")?.trim()?.takeIf { it.isNotBlank() }
  }
}

@Composable
private fun BravChatRoot(
  initialSmsRecipient: String?,
  openMusicRequestCount: Int,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val auth = remember { Firebase.auth }
  val credentialManager = remember { CredentialManager.create(context) }
  val bravRepository = remember {
    val dbId = context.applicationContext.getString(R.string.firestore_database_id)
    BravRepository(FirebaseFirestore.getInstance(dbId), auth)
  }

  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

  if (currentUser == null) {
    BravChatTheme {
      AuthSignInScreen(onAuthSuccess = {})
    }
    return
  }

  val user = currentUser!!
  val viewModel: BravViewModel =
    viewModel(
      key = user.uid,
      factory =
        BravViewModel.createFactory(
          appContext = context.applicationContext,
          bravRepository = bravRepository,
          currentUserId = user.uid,
          googleDisplayName = user.displayName.orEmpty(),
          googleAvatarUrl = user.photoUrl?.toString().orEmpty(),
        ),
    )

  val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
  val sessionUnlocked by viewModel.sessionUnlocked.collectAsStateWithLifecycle()
  val themeMode = (profileState as? UiState.Success)?.data?.themeMode ?: "LIGHT"

  BravChatTheme(themeMode = themeMode) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      when (val state = profileState) {
        is UiState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
        is UiState.Error -> {
          Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Text(
              text = state.message,
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { signOut(credentialManager, scope) }) {
              Text("Changer de compte")
            }
          }
        }
        is UiState.Success -> {
          val profile = state.data
          when {
            profile == null -> {
              OnboardingRegistrationScreen(
                initialName = user.displayName.orEmpty(),
                onComplete = { firstName, lastName, phone, dialCode, iso ->
                  viewModel.completeOnboardingRegistration(firstName, lastName, phone, dialCode, iso)
                },
              )
            }
            profile.biometricEnabled && !sessionUnlocked -> {
              BiometricLockScreen(
                userName = profile.displayName,
                hasPinFallback = profile.pinHash.isNotBlank(),
                onBiometricSuccess = viewModel::unlockBiometricSession,
                onVerifyPin = viewModel::verifyPinFallback,
                onSignOut = { signOut(credentialManager, scope) },
              )
            }
            else -> {
              MainShellScreen(
                viewModel = viewModel,
                initialSmsRecipient = initialSmsRecipient,
                openMusicRequestCount = openMusicRequestCount,
                onSignOut = { signOut(credentialManager, scope) },
              )
            }
          }
        }
      }
    }
  }
}
