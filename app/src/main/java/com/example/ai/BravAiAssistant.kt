package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.AiMessageItem
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

class BravAiAssistant {

  private val freeBackend by lazy { Firebase.ai(backend = GenerativeBackend.googleAI()) }

  private val chatModels: Map<String, GenerativeModel> by lazy {
    FREE_TIER_MODELS.associateWith { modelName ->
      freeBackend.generativeModel(
        modelName = modelName,
        systemInstruction =
          content {
            text(
              "Tu es BRAV IA, l'assistant intelligent officiel intégré à l'application de messagerie BRAV CHAT. " +
                "Réponds de façon claire, naturelle, concise et professionnelle en français (ou dans la langue de l'utilisateur). " +
                "Tu aides l'utilisateur à rédiger des messages, résumer des conversations, traduire et répondre à toute question."
            )
          },
      )
    }
  }

  private val autoReplyModels: Map<String, GenerativeModel> by lazy {
    FREE_TIER_MODELS.associateWith { modelName ->
      freeBackend.generativeModel(
        modelName = modelName,
        systemInstruction =
          content {
            text(
              "Tu es le module de réponse automatique de BRAV CHAT. " +
                "Génère uniquement le texte exact de la réponse prête à envoyer, sans guillemets, sans préambule et sans explication. " +
                "Garde la réponse naturelle, polie et concise (1 à 3 phrases maximum)."
            )
          },
      )
    }
  }

  fun streamChatReply(
    history: List<AiMessageItem>,
    userPrompt: String,
    attachedImage: Bitmap? = null,
  ): Flow<String> = flow {
    val sanitizedHistory = buildStrictAlternatingHistory(history)
    val inputContent =
      content("user") {
        if (attachedImage != null) {
          image(attachedImage)
        }
        text(userPrompt.ifBlank { "Décris cette image." })
      }

    var lastException: Exception? = null

    for (modelName in FREE_TIER_MODELS) {
      val model = chatModels[modelName] ?: continue
      val buffer = StringBuilder()
      try {
        val chat = model.startChat(history = sanitizedHistory)
        withTimeout(TIMEOUT_MS) {
          chat.sendMessageStream(inputContent).collect { chunk ->
            val part = chunk.text.orEmpty()
            if (part.isNotEmpty()) {
              buffer.append(part)
              emit(buffer.toString())
            }
          }
        }
        if (buffer.isNotBlank()) {
          return@flow
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Free-tier model $modelName streaming failed", e)
        lastException = e
        if (buffer.isNotBlank()) {
          // Partial text already reached the UI; stop without duplicating output.
          return@flow
        }
      }
    }

    // Final non-streaming fallback on the lightest free-tier model
    val fallbackModel = chatModels[FALLBACK_MODEL]
    if (fallbackModel != null) {
      try {
        val chat = fallbackModel.startChat(history = sanitizedHistory)
        val response = withTimeout(TIMEOUT_MS) { chat.sendMessage(inputContent) }
        val text = response.text?.trim()
        if (!text.isNullOrBlank()) {
          emit(text)
          return@flow
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Final fallback $FALLBACK_MODEL unary call failed", e)
        lastException = e
      }
    }

    throw IllegalStateException(formatReadableError(lastException), lastException)
  }

  suspend fun generateSmartReply(
    peerName: String,
    recentTranscript: List<String>,
    tone: String,
    isAutoResponder: Boolean,
  ): Result<String> {
    val contextBlock =
      if (recentTranscript.isEmpty()) "Aucun historique récent."
      else recentTranscript.takeLast(8).joinToString("\n")
    val modeNote =
      if (isAutoResponder) {
        "L'utilisateur est momentanément indisponible. Rédige une réponse automatique polie et rassurante qui accuse réception du dernier message."
      } else {
        "Rédige une réponse directe pertinente au dernier message reçu."
      }
    val prompt =
      """
      Interlocuteur : $peerName
      Ton souhaité : $tone
      Consigne : $modeNote
      Derniers messages :
      $contextBlock
      """.trimIndent()

    var lastException: Exception? = null
    for (modelName in FREE_TIER_MODELS) {
      val model = autoReplyModels[modelName] ?: continue
      try {
        val response =
          withTimeout(AUTO_REPLY_TIMEOUT_MS) { model.generateContent(prompt) }
        val reply = response.text?.trim()
        if (!reply.isNullOrBlank()) {
          return Result.success(reply)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Free-tier auto-reply $modelName failed", e)
        lastException = e
      }
    }

    return Result.failure(IllegalStateException(formatReadableError(lastException), lastException))
  }

  /**
   * Gemini startChat(history) strictly requires alternating user -> model pairs,
   * starting with "user" and ending with "model". Any failed/unpaired turn is dropped
   * so an earlier error never breaks subsequent messages in the conversation.
   */
  private fun buildStrictAlternatingHistory(history: List<AiMessageItem>): List<Content> {
    val valid = history.filter { !it.isError && !it.isStreaming && it.text.isNotBlank() }
    val paired = mutableListOf<AiMessageItem>()
    var expectUser = true
    for (msg in valid) {
      if (msg.isFromUser == expectUser) {
        paired.add(msg)
        expectUser = !expectUser
      } else if (msg.isFromUser && !expectUser) {
        // Two user turns without a model reply in between: replace the unanswered user turn.
        paired[paired.lastIndex] = msg
      }
    }
    if (paired.size % 2 != 0) {
      paired.removeAt(paired.lastIndex)
    }
    return paired.takeLast(12).map { msg ->
      content(role = if (msg.isFromUser) "user" else "model") { text(msg.text) }
    }
  }

  private fun formatReadableError(error: Exception?): String {
    if (error is TimeoutCancellationException) {
      return "Délai d'attente dépassé. Vérifiez votre connexion et réessayez."
    }
    val raw = error?.localizedMessage.orEmpty()
    return when {
      raw.contains("403") || raw.contains("AppCheck", ignoreCase = true) ->
        "Vérification App Check requise (403). Utilisez une build configurée avec le projet Firebase."
      raw.contains("network", ignoreCase = true) || raw.contains("unable to resolve", ignoreCase = true) ->
        "Connexion réseau indisponible pour joindre Gemini."
      raw.isNotBlank() -> raw
      else -> "Impossible de joindre le modèle gratuit Gemini pour le moment."
    }
  }

  companion object {
    private const val TAG = "BravAiAssistant"
    private const val PRIMARY_MODEL = "gemini-3.8-flash"
    private const val FALLBACK_MODEL = "gemini-3.5-flash-lite"
    private val FREE_TIER_MODELS = listOf(PRIMARY_MODEL, FALLBACK_MODEL)
    private const val TIMEOUT_MS = 60_000L
    private const val AUTO_REPLY_TIMEOUT_MS = 45_000L
  }
}
