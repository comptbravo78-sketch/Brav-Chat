package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.data.CountryData
import com.example.security.BiometricAuthManager
import com.example.ui.components.BravLogoMark
import com.example.ui.components.CountryPickerDialog
import com.example.ui.components.PinEntryDialog
import com.example.ui.theme.LocalBravColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthSignInScreen(
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = scope,
    )
  }

  val ringColor = MaterialTheme.colorScheme.primary
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding(),
    contentAlignment = Alignment.Center,
  ) {
    // Whisper-light concentric architectural rings
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.33f)
      listOf(110.dp, 165.dp, 225.dp).forEachIndexed { idx, r ->
        drawCircle(
          color = ringColor.copy(alpha = 0.055f - idx * 0.015f),
          radius = r.toPx(),
          center = center,
          style = Stroke(width = 1.2.dp.toPx()),
        )
      }
    }

    Column(
      modifier =
        Modifier.widthIn(max = 440.dp)
          .fillMaxSize()
          .padding(horizontal = 28.dp, vertical = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Spacer(Modifier.height(12.dp))

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
          shape = RoundedCornerShape(30.dp),
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 1.dp,
          shadowElevation = 6.dp,
          modifier = Modifier.size(132.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            BravLogoMark(size = 86.dp)
          }
        }

        Spacer(Modifier.height(28.dp))

        Text(
          text = "BRAV CHAT",
          style = MaterialTheme.typography.displaySmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(8.dp))

        Text(
          text = "Messagerie fluide, statuts 24H, SMS et appels HD réunis avec élégance.",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 12.dp),
        )

        Spacer(Modifier.height(22.dp))

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf("Chiffré", "Statuts 24H", "SMS / MMS", "Audio HD").forEach { tag ->
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            ) {
              Text(
                text = tag,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              )
            }
          }
        }
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Button(
          onClick = {
            errorMessage = null
            isLoading = true
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
              },
              onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
              },
              scope = scope,
              onAuthCancelled = { isLoading = false },
            )
          },
          enabled = !isLoading,
          shape = RoundedCornerShape(18.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth().height(56.dp).testTag("google_sign_in_button"),
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              strokeWidth = 2.4.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          } else {
            Icon(Icons.Rounded.GppGood, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
              text = "Continuer avec Google",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }

        AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
          ) {
            Text(
              text = errorMessage.orEmpty(),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
          }
        }

        Spacer(Modifier.height(14.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "Connexion sécurisée par Google",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
fun OnboardingRegistrationScreen(
  initialName: String,
  onComplete: suspend (firstName: String, lastName: String, phone: String, dialCode: String, iso: String) -> Result<Unit>,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val nameParts = remember(initialName) { initialName.trim().split(Regex("\\s+"), limit = 2) }

  var firstName by remember { mutableStateOf(nameParts.getOrNull(0).orEmpty()) }
  var lastName by remember { mutableStateOf(nameParts.getOrNull(1).orEmpty()) }
  var selectedCountry by remember { mutableStateOf(CountryData.detectFromDeviceLocale(context)) }
  var gpsDetected by remember { mutableStateOf(false) }
  var localPhone by remember { mutableStateOf("") }
  var showCountryPicker by remember { mutableStateOf(false) }
  var isSubmitting by remember { mutableStateOf(false) }
  var errorText by remember { mutableStateOf<String?>(null) }

  val locationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      scope.launch {
        val (country, viaGps) = CountryData.detectCountryViaGpsOrLocale(context)
        selectedCountry = country
        gpsDetected = viaGps
      }
    }

  LaunchedEffect(Unit) {
    val (country, viaGps) = CountryData.detectCountryViaGpsOrLocale(context)
    selectedCountry = country
    gpsDetected = viaGps
    if (!viaGps) {
      locationLauncher.launch(
        arrayOf(
          Manifest.permission.ACCESS_COARSE_LOCATION,
          Manifest.permission.ACCESS_FINE_LOCATION,
        )
      )
    }
  }

  val fieldColors =
    OutlinedTextFieldDefaults.colors(
      focusedBorderColor = MaterialTheme.colorScheme.primary,
      unfocusedBorderColor = Color.Transparent,
      focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    )

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding()
        .imePadding(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 520.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      BravLogoMark(size = 44.dp)
      Spacer(Modifier.height(16.dp))
      Text(
        text = "Finaliser votre profil",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = "Configurez votre identité et votre indicatif pays.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(24.dp))

      Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
              value = firstName,
              onValueChange = { firstName = it },
              label = { Text("Prénom") },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              colors = fieldColors,
              modifier = Modifier.weight(1f).testTag("onboarding_first_name"),
            )
            OutlinedTextField(
              value = lastName,
              onValueChange = { lastName = it },
              label = { Text("Nom") },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              colors = fieldColors,
              modifier = Modifier.weight(1f).testTag("onboarding_last_name"),
            )
          }

          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { showCountryPicker = true }
                .testTag("country_selector_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(selectedCountry.flagEmoji, style = MaterialTheme.typography.titleLarge)
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${selectedCountry.name} (${selectedCountry.dialCode})",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                  )
                  Spacer(Modifier.width(4.dp))
                  Text(
                    text = if (gpsDetected) "Détection GPS" else "Région de l'appareil",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }
              Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Changer de pays")
            }
          }

          OutlinedTextField(
            value = localPhone,
            onValueChange = { input ->
              val cleaned = input.filter { it.isDigit() }.take(15)
              localPhone = cleaned
              errorText = null
            },
            label = { Text("Numéro de téléphone") },
            prefix = {
              Text(
                text = "${selectedCountry.dialCode} ",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
              )
            },
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("onboarding_phone_input"),
          )

          AnimatedVisibility(visible = !errorText.isNullOrBlank()) {
            Text(
              text = errorText.orEmpty(),
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }

      Spacer(Modifier.height(24.dp))

      Button(
        onClick = {
          val digits = localPhone.trimStart('0')
          if (firstName.isBlank()) {
            errorText = "Veuillez saisir votre prénom"
            return@Button
          }
          if (digits.length < 6) {
            errorText = "Numéro invalide pour ${selectedCountry.dialCode}"
            return@Button
          }
          isSubmitting = true
          scope.launch {
            val res =
              onComplete(
                firstName,
                lastName,
                "${selectedCountry.dialCode}$digits",
                selectedCountry.dialCode,
                selectedCountry.isoCode,
              )
            isSubmitting = false
            if (res.isFailure) {
              errorText = res.exceptionOrNull()?.localizedMessage ?: "Échec d'enregistrement"
            }
          }
        },
        enabled = !isSubmitting,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("onboarding_submit_button"),
      ) {
        if (isSubmitting) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
        } else {
          Text("Continuer", style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }

  if (showCountryPicker) {
    CountryPickerDialog(
      onDismiss = { showCountryPicker = false },
      onSelect = {
        selectedCountry = it
        gpsDetected = false
        showCountryPicker = false
      },
    )
  }
}

@Composable
fun BiometricLockScreen(
  userName: String,
  hasPinFallback: Boolean,
  onBiometricSuccess: () -> Unit,
  onVerifyPin: (String) -> Boolean,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val activity = context as? FragmentActivity
  var showPinDialog by remember { mutableStateOf(false) }
  var pinError by remember { mutableStateOf<String?>(null) }
  var authError by remember { mutableStateOf<String?>(null) }

  val triggerBiometric = {
    if (activity != null && BiometricAuthManager.canAuthenticate(context)) {
      authError = null
      BiometricAuthManager.authenticate(
        activity = activity,
        title = "Déverrouiller BRAV CHAT",
        subtitle = "Empreinte digitale ou reconnaissance faciale",
        negativeButtonText = if (hasPinFallback) "Code PIN" else "Annuler",
        onSuccess = onBiometricSuccess,
        onFallbackPin = {
          if (hasPinFallback) showPinDialog = true
        },
        onError = { msg -> authError = msg },
      )
    } else if (hasPinFallback) {
      showPinDialog = true
    } else {
      onBiometricSuccess()
    }
  }

  LaunchedEffect(Unit) { triggerBiometric() }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 380.dp).padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      BravLogoMark(size = 42.dp)
      Spacer(Modifier.height(28.dp))

      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        modifier = Modifier.size(92.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.Rounded.Fingerprint,
            contentDescription = "Authentification biométrique",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(46.dp),
          )
        }
      }

      Spacer(Modifier.height(24.dp))
      Text(
        text = "BRAV CHAT verrouillé",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = userName,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (!authError.isNullOrBlank()) {
        Spacer(Modifier.height(14.dp))
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.errorContainer,
        ) {
          Text(
            text = authError.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          )
        }
      }

      Spacer(Modifier.height(32.dp))
      Button(
        onClick = triggerBiometric,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("unlock_biometric_button"),
      ) {
        Icon(Icons.Rounded.Fingerprint, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Déverrouiller")
      }

      if (hasPinFallback) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
          onClick = { showPinDialog = true },
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("unlock_pin_button"),
        ) {
          Icon(Icons.Rounded.Pin, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Utiliser le code PIN")
        }
      }

      Spacer(Modifier.height(18.dp))
      TextButton(onClick = onSignOut) { Text("Changer de compte") }
    }
  }

  if (showPinDialog) {
    PinEntryDialog(
      title = "Code PIN de secours",
      confirmLabel = "Déverrouiller",
      errorText = pinError,
      onDismiss = {
        showPinDialog = false
        pinError = null
      },
      onConfirm = { pin ->
        if (onVerifyPin(pin)) {
          showPinDialog = false
          pinError = null
          onBiometricSuccess()
        } else {
          pinError = "Code PIN incorrect"
        }
      },
    )
  }
}

@Composable
fun FirstLoginBiometricOptInDialog(
  onEnable: (pin: String) -> Unit,
  onSkip: () -> Unit,
) {
  var pin by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onSkip,
    containerColor = MaterialTheme.colorScheme.surface,
    icon = {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(48.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.Rounded.Fingerprint,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
          )
        }
      }
    },
    title = { Text("Sécurité biométrique", style = MaterialTheme.typography.titleLarge) },
    text = {
      Column {
        Text(
          text = "Protégez vos conversations avec l'empreinte digitale ou la reconnaissance faciale et un code PIN de secours.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
          value = pin,
          onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
          label = { Text("Code PIN (4 à 6 chiffres)") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onEnable(pin) },
        enabled = pin.length >= 4,
        shape = RoundedCornerShape(12.dp),
      ) {
        Text("Activer")
      }
    },
    dismissButton = {
      TextButton(onClick = onSkip) { Text("Plus tard") }
    },
  )
}
