package com.example.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest

object BiometricAuthManager {
  fun canAuthenticate(context: Context): Boolean {
    val manager = BiometricManager.from(context)
    val authenticators =
      BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK
    return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
  }

  fun authenticate(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    negativeButtonText: String,
    onSuccess: () -> Unit,
    onFallbackPin: () -> Unit,
    onError: (String) -> Unit,
  ) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback =
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          super.onAuthenticationSucceeded(result)
          onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          super.onAuthenticationError(errorCode, errString)
          if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
            errorCode == BiometricPrompt.ERROR_USER_CANCELED
          ) {
            onFallbackPin()
          } else {
            onError(errString.toString())
          }
        }
      }

    val prompt = BiometricPrompt(activity, executor, callback)
    val promptInfo =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText(negativeButtonText)
        .setAllowedAuthenticators(
          BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        .build()

    prompt.authenticate(promptInfo)
  }

  fun hashPin(uid: String, pin: String): String {
    val salted = "brav_pin_${uid}_$pin"
    val digest = MessageDigest.getInstance("SHA-256").digest(salted.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  fun verifyPin(uid: String, pin: String, storedHash: String): Boolean {
    if (storedHash.isBlank()) return false
    return hashPin(uid, pin) == storedHash
  }
}
