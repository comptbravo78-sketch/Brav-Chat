package com.example.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.MediaAttachmentDraft
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaHelper {

  suspend fun buildDraftFromUri(
    context: Context,
    uri: Uri,
    forceType: String? = null,
    compressImages: Boolean = true,
  ): MediaAttachmentDraft? = withContext(Dispatchers.IO) {
    runCatching {
      val resolver = context.contentResolver
      val mimeType = resolver.getType(uri) ?: "application/octet-stream"
      var displayName = "fichier"
      var sizeBytes = 0L

      resolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
          if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
        }
      }

      val resolvedType =
        forceType
          ?: when {
            mimeType.startsWith("image/") -> "IMAGE"
            mimeType.startsWith("video/") -> "VIDEO"
            mimeType.startsWith("audio/") -> "AUDIO"
            mimeType == "application/pdf" || displayName.endsWith(".pdf", true) -> "PDF"
            else -> "DOCUMENT"
          }

      // Copy to app cache so URI remains accessible after picker closes
      val cachedFile = File(context.cacheDir, "media_${System.currentTimeMillis()}_${displayName.replace(" ", "_")}")
      resolver.openInputStream(uri)?.use { input ->
        FileOutputStream(cachedFile).use { output -> input.copyTo(output) }
      }
      if (sizeBytes <= 0L) sizeBytes = cachedFile.length()

      var thumbnailBase64 = ""
      var wasCompressed = false
      var durationMs = 0L

      if (resolvedType == "IMAGE") {
        val (b64, compressed) = compressBitmapToBase64(cachedFile, compressImages)
        thumbnailBase64 = b64
        wasCompressed = compressed
      } else if (resolvedType == "VIDEO" || resolvedType == "AUDIO") {
        runCatching {
          val retriever = MediaMetadataRetriever()
          retriever.setDataSource(cachedFile.absolutePath)
          durationMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
              ?: 0L
          if (resolvedType == "VIDEO") {
            retriever.frameAtTime?.let { bmp ->
              thumbnailBase64 = encodeScaledBitmapBase64(bmp, 420, 68)
            }
          }
          retriever.release()
        }
      }

      val contentUri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cachedFile)

      MediaAttachmentDraft(
        type = resolvedType,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        localUri = contentUri.toString(),
        thumbnailBase64 = thumbnailBase64,
        durationMs = durationMs,
        wasCompressed = wasCompressed,
      )
    }.onFailure { err ->
      Log.e("MediaHelper", "Failed building attachment draft", err)
    }.getOrNull()
  }

  private fun compressBitmapToBase64(file: File, compress: Boolean): Pair<String, Boolean> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    val maxDim = if (compress) 720 else 1080
    var inSampleSize = 1
    while (options.outWidth / inSampleSize > maxDim || options.outHeight / inSampleSize > maxDim) {
      inSampleSize *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return "" to false
    val quality = if (compress) 62 else 82
    val b64 = encodeScaledBitmapBase64(bitmap, maxDim, quality)
    return b64 to (inSampleSize > 1 || compress)
  }

  private fun encodeScaledBitmapBase64(bitmap: Bitmap, maxDim: Int, quality: Int): String {
    val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
    val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
    val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
    val scaled =
      if (ratio < 1f) Bitmap.createScaledBitmap(bitmap, targetW, targetH, true) else bitmap

    var q = quality
    var bytes: ByteArray
    do {
      val baos = ByteArrayOutputStream()
      scaled.compress(Bitmap.CompressFormat.JPEG, q, baos)
      bytes = baos.toByteArray()
      q -= 10
    } while (bytes.size > 140_000 && q >= 25)

    return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
  }

  suspend fun buildAvatarDataUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
      val bitmap = decodeBitmapFromUriSync(context, uri, maxDim = 256) ?: return@runCatching null
      val size = minOf(bitmap.width, bitmap.height)
      val xOffset = ((bitmap.width - size) / 2).coerceAtLeast(0)
      val yOffset = ((bitmap.height - size) / 2).coerceAtLeast(0)
      val squared = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)
      val target =
        if (size > 220) Bitmap.createScaledBitmap(squared, 220, 220, true) else squared
      var q = 78
      var bytes: ByteArray
      do {
        val baos = ByteArrayOutputStream()
        target.compress(Bitmap.CompressFormat.JPEG, q, baos)
        bytes = baos.toByteArray()
        q -= 10
      } while (bytes.size > 55_000 && q >= 25)
      "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.onFailure { err ->
      Log.e("MediaHelper", "Failed building avatar data URI", err)
    }.getOrNull()
  }

  suspend fun decodeBitmapFromUri(context: Context, uri: Uri, maxDim: Int = 1024): Bitmap? =
    withContext(Dispatchers.IO) {
      runCatching { decodeBitmapFromUriSync(context, uri, maxDim) }.getOrNull()
    }

  private fun decodeBitmapFromUriSync(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    val resolver = context.contentResolver
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
    var inSampleSize = 1
    while (boundsOpts.outWidth / inSampleSize > maxDim || boundsOpts.outHeight / inSampleSize > maxDim) {
      inSampleSize *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
  }

  fun decodeBase64DataUri(dataUri: String): Bitmap? {
    val clean = dataUri.substringAfter("base64,", dataUri)
    if (clean.isBlank()) return null
    return runCatching {
      val bytes = Base64.decode(clean, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
  }

  fun openAttachmentExternally(context: Context, uriString: String, mimeType: String): Boolean {
    if (uriString.isBlank()) return false
    return runCatching {
      val uri = Uri.parse(uriString)
      val intent =
        Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(uri, mimeType.ifBlank { "*/*" })
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(Intent.createChooser(intent, "Ouvrir avec"))
      true
    }.getOrElse { false }
  }

  fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 Ko"
    val kb = bytes / 1024.0
    return if (kb < 1024.0) "${kb.toInt().coerceAtLeast(1)} Ko"
    else String.format("%.1f Mo", kb / 1024.0)
  }

  fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format("%d:%02d", min, sec)
  }
}

class VoiceNoteRecorder(private val context: Context) {
  private var recorder: MediaRecorder? = null
  private var outputFile: File? = null
  private var startedAtMs: Long = 0L

  fun start(): Boolean {
    return runCatching {
      val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
      val rec =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          MediaRecorder(context)
        } else {
          @Suppress("DEPRECATION")
          MediaRecorder()
        }
      rec.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
      rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
      rec.setAudioEncodingBitRate(96000)
      rec.setAudioSamplingRate(44100)
      rec.setOutputFile(file.absolutePath)
      rec.prepare()
      rec.start()
      recorder = rec
      outputFile = file
      startedAtMs = System.currentTimeMillis()
      true
    }.getOrElse { err ->
      Log.e("VoiceRecorder", "Failed starting voice note recorder", err)
      false
    }
  }

  fun stop(): MediaAttachmentDraft? {
    val file = outputFile ?: return null
    val duration = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(500L)
    return runCatching {
      recorder?.stop()
      recorder?.release()
      recorder = null
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      MediaAttachmentDraft(
        type = "VOICE",
        displayName = "Note vocale",
        mimeType = "audio/mp4",
        sizeBytes = file.length(),
        localUri = uri.toString(),
        durationMs = duration,
        wasCompressed = true,
      )
    }.getOrElse {
      runCatching { recorder?.release() }
      recorder = null
      null
    }
  }

  fun cancel() {
    runCatching {
      recorder?.stop()
      recorder?.release()
    }
    recorder = null
    outputFile?.delete()
    outputFile = null
  }
}

class VoiceNotePlayer {
  private var player: MediaPlayer? = null

  fun play(context: Context, uriString: String, onCompleted: () -> Unit) {
    stop()
    runCatching {
      val mp = MediaPlayer()
      mp.setDataSource(context, Uri.parse(uriString))
      mp.setOnCompletionListener {
        stop()
        onCompleted()
      }
      mp.prepare()
      mp.start()
      player = mp
    }.onFailure { onCompleted() }
  }

  fun stop() {
    runCatching {
      player?.stop()
      player?.release()
    }
    player = null
  }
}
