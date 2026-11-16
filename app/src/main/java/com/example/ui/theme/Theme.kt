package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class BravChatColors(
  val outgoingBubble: Color,
  val incomingBubble: Color,
  val incomingBubbleBorder: Color,
  val onlineDot: Color,
  val readCheck: Color,
  val watermarkTint: Color,
  val callAccept: Color,
  val callEnd: Color,
  val statusRingActive: Color,
  val statusRingViewed: Color,
  val chatBackdrop: Color,
  val hairline: Color,
  val cardElevated: Color,
)

val LocalBravColors = staticCompositionLocalOf {
  BravChatColors(
    outgoingBubble = BravOutgoingBubbleLight,
    incomingBubble = BravIncomingBubbleLight,
    incomingBubbleBorder = Color(0xFFE6ECF4),
    onlineDot = BravOnlineGreen,
    readCheck = BravReadBlue,
    watermarkTint = BravCobaltLight,
    callAccept = BravCallAcceptGreen,
    callEnd = BravCallEndRed,
    statusRingActive = BravCobaltLight,
    statusRingViewed = Color(0xFFCBD5E1),
    chatBackdrop = Color(0xFFF3F6FB),
    hairline = Color(0xFFE8EEF5),
    cardElevated = Color(0xFFFFFFFF),
  )
}

private val LightColorScheme =
  lightColorScheme(
    primary = BravCobaltLight,
    onPrimary = BravOnCobaltLight,
    primaryContainer = BravCobaltContainerLight,
    onPrimaryContainer = BravOnCobaltContainerLight,
    secondary = BravEmeraldLight,
    onSecondary = BravOnEmeraldLight,
    secondaryContainer = BravEmeraldContainerLight,
    onSecondaryContainer = BravOnEmeraldContainerLight,
    background = BravBackgroundLight,
    onBackground = BravOnSurfaceLight,
    surface = BravSurfaceLight,
    onSurface = BravOnSurfaceLight,
    surfaceVariant = BravSurfaceVariantLight,
    onSurfaceVariant = BravOnSurfaceVariantLight,
    outline = BravOutlineLight,
    outlineVariant = Color(0xFFE9EFF6),
    error = BravError,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E8),
    onErrorContainer = Color(0xFF881337),
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = BravCobaltDark,
    onPrimary = BravOnCobaltDark,
    primaryContainer = BravCobaltContainerDark,
    onPrimaryContainer = BravOnCobaltContainerDark,
    secondary = BravEmeraldDark,
    onSecondary = BravOnEmeraldDark,
    secondaryContainer = BravEmeraldContainerDark,
    onSecondaryContainer = BravOnEmeraldContainerDark,
    background = BravBackgroundDark,
    onBackground = BravOnSurfaceDark,
    surface = BravSurfaceDark,
    onSurface = BravOnSurfaceDark,
    surfaceVariant = BravSurfaceVariantDark,
    onSurfaceVariant = BravOnSurfaceVariantDark,
    outline = BravOutlineDark,
    outlineVariant = Color(0xFF232C39),
    error = BravError,
    onError = Color.White,
  )

private val BravShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun BravChatTheme(
  themeMode: String = "LIGHT",
  content: @Composable () -> Unit,
) {
  val systemDark = isSystemInDarkTheme()
  val isDark =
    when (themeMode) {
      "DARK" -> true
      "LIGHT" -> false
      else -> systemDark
    }

  val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
  val extendedColors =
    if (isDark) {
      BravChatColors(
        outgoingBubble = BravOutgoingBubbleDark,
        incomingBubble = BravIncomingBubbleDark,
        incomingBubbleBorder = Color(0xFF2B3544),
        onlineDot = BravOnlineGreen,
        readCheck = BravCobaltDark,
        watermarkTint = Color(0xFF94A3B8),
        callAccept = BravCallAcceptGreen,
        callEnd = BravCallEndRed,
        statusRingActive = BravCobaltDark,
        statusRingViewed = BravOutlineDark,
        chatBackdrop = Color(0xFF0D1117),
        hairline = Color(0xFF222A36),
        cardElevated = Color(0xFF151B23),
      )
    } else {
      BravChatColors(
        outgoingBubble = BravOutgoingBubbleLight,
        incomingBubble = BravIncomingBubbleLight,
        incomingBubbleBorder = Color(0xFFE6ECF4),
        onlineDot = BravOnlineGreen,
        readCheck = BravReadBlue,
        watermarkTint = BravCobaltLight,
        callAccept = BravCallAcceptGreen,
        callEnd = BravCallEndRed,
        statusRingActive = BravCobaltLight,
        statusRingViewed = Color(0xFFCBD5E1),
        chatBackdrop = Color(0xFFF3F6FB),
        hairline = Color(0xFFE8EEF5),
        cardElevated = Color(0xFFFFFFFF),
      )
    }

  CompositionLocalProvider(LocalBravColors provides extendedColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = BravShapes,
      content = content,
    )
  }
}
