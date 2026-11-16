package com.example.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudioDspCapabilities(
  val noiseSuppressorSupported: Boolean = false,
  val noiseSuppressorActive: Boolean = false,
  val echoCancelerSupported: Boolean = false,
  val echoCancelerActive: Boolean = false,
  val agcSupported: Boolean = false,
  val agcActive: Boolean = false,
  val micLevelNormalized: Float = 0f,
)

class AudioProcessingEngine(private val context: Context) {

  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private var audioRecord: AudioRecord? = null
  private var noiseSuppressor: NoiseSuppressor? = null
  private var echoCanceler: AcousticEchoCanceler? = null
  private var automaticGainControl: AutomaticGainControl? = null
  private var monitorJob: Job? = null

  private val _dspState =
    MutableStateFlow(
      AudioDspCapabilities(
        noiseSuppressorSupported = NoiseSuppressor.isAvailable(),
        echoCancelerSupported = AcousticEchoCanceler.isAvailable(),
        agcSupported = AutomaticGainControl.isAvailable(),
      )
    )
  val dspState = _dspState.asStateFlow()

  @SuppressLint("MissingPermission")
  fun startCallAudioPipeline(
    scope: CoroutineScope,
    enableNoiseSuppression: Boolean,
    speakerOn: Boolean,
  ) {
    stopCallAudioPipeline()

    runCatching {
      audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
      setSpeakerphone(speakerOn)
      audioManager.isMicrophoneMute = false
    }

    val hasMicPerm =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasMicPerm) {
      _dspState.value =
        AudioDspCapabilities(
          noiseSuppressorSupported = NoiseSuppressor.isAvailable(),
          echoCancelerSupported = AcousticEchoCanceler.isAvailable(),
          agcSupported = AutomaticGainControl.isAvailable(),
        )
      return
    }

    val sampleRate = 16000
    val minBuffer =
      AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
    if (minBuffer <= 0) return

    runCatching {
      val record =
        AudioRecord(
          MediaRecorder.AudioSource.VOICE_COMMUNICATION,
          sampleRate,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          minBuffer * 2,
        )
      if (record.state != AudioRecord.STATE_INITIALIZED) {
        record.release()
        return
      }
      audioRecord = record
      val sessionId = record.audioSessionId

      var nsActive = false
      if (enableNoiseSuppression && NoiseSuppressor.isAvailable()) {
        noiseSuppressor =
          NoiseSuppressor.create(sessionId)?.apply {
            enabled = true
            nsActive = enabled
          }
      }

      var aecActive = false
      if (AcousticEchoCanceler.isAvailable()) {
        echoCanceler =
          AcousticEchoCanceler.create(sessionId)?.apply {
            enabled = true
            aecActive = enabled
          }
      }

      var agcActive = false
      if (AutomaticGainControl.isAvailable()) {
        automaticGainControl =
          AutomaticGainControl.create(sessionId)?.apply {
            enabled = true
            agcActive = enabled
          }
      }

      _dspState.value =
        AudioDspCapabilities(
          noiseSuppressorSupported = NoiseSuppressor.isAvailable(),
          noiseSuppressorActive = nsActive,
          echoCancelerSupported = AcousticEchoCanceler.isAvailable(),
          echoCancelerActive = aecActive,
          agcSupported = AutomaticGainControl.isAvailable(),
          agcActive = agcActive,
        )

      record.startRecording()
      monitorJob =
        scope.launch(Dispatchers.IO) {
          val buffer = ShortArray(minBuffer)
          while (isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val read = record.read(buffer, 0, buffer.size)
            if (read > 0) {
              var sumSq = 0.0
              for (i in 0 until read) {
                val sample = buffer[i].toDouble()
                sumSq += sample * sample
              }
              val rms = sqrt(sumSq / read)
              val normalized = (rms / 6500.0).toFloat().coerceIn(0f, 1f)
              _dspState.value = _dspState.value.copy(micLevelNormalized = normalized)
            }
          }
        }
    }.onFailure { err ->
      Log.w("AudioProcessingEngine", "Could not initialize hardware DSP pipeline", err)
    }
  }

  fun setMuted(muted: Boolean) {
    runCatching { audioManager.isMicrophoneMute = muted }
  }

  fun setSpeakerphone(enabled: Boolean) {
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (enabled) {
          val speaker =
            audioManager.availableCommunicationDevices.firstOrNull {
              it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
          if (speaker != null) {
            audioManager.setCommunicationDevice(speaker)
          }
        } else {
          audioManager.clearCommunicationDevice()
        }
      } else {
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = enabled
      }
    }
  }

  fun setNoiseSuppression(enabled: Boolean) {
    runCatching {
      noiseSuppressor?.enabled = enabled
      _dspState.value =
        _dspState.value.copy(noiseSuppressorActive = noiseSuppressor?.enabled == true)
    }
  }

  fun stopCallAudioPipeline() {
    monitorJob?.cancel()
    monitorJob = null
    runCatching { noiseSuppressor?.release() }
    noiseSuppressor = null
    runCatching { echoCanceler?.release() }
    echoCanceler = null
    runCatching { automaticGainControl?.release() }
    automaticGainControl = null
    runCatching {
      audioRecord?.stop()
      audioRecord?.release()
    }
    audioRecord = null
    runCatching {
      audioManager.isMicrophoneMute = false
      setSpeakerphone(false)
      audioManager.mode = AudioManager.MODE_NORMAL
    }
  }
}
