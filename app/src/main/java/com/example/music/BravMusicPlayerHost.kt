package com.example.music

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.example.model.YouTubeTrack
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ClipDisplayMode {
  EXPANDED,
  MINI,
  HIDDEN_AUDIO_ONLY,
}

class BackgroundPlayWebView(
  context: Context,
  private val shouldStayActiveInBackground: () -> Boolean,
) : WebView(context) {
  override fun onWindowVisibilityChanged(visibility: Int) {
    if (visibility != View.VISIBLE && shouldStayActiveInBackground()) {
      super.onWindowVisibilityChanged(View.VISIBLE)
    } else {
      super.onWindowVisibilityChanged(visibility)
    }
  }
}

class BravMusicPlayerHost private constructor(private val appContext: Context) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  private val _queue = MutableStateFlow<List<YouTubeTrack>>(emptyList())
  val queue: StateFlow<List<YouTubeTrack>> = _queue.asStateFlow()

  private val _currentTrack = MutableStateFlow<YouTubeTrack?>(null)
  val currentTrack: StateFlow<YouTubeTrack?> = _currentTrack.asStateFlow()

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

  private val _isBuffering = MutableStateFlow(false)
  val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

  private val _autoPlayNextEnabled = MutableStateFlow(true)
  val autoPlayNextEnabled: StateFlow<Boolean> = _autoPlayNextEnabled.asStateFlow()

  private val _clipDisplayMode = MutableStateFlow(ClipDisplayMode.EXPANDED)
  val clipDisplayMode: StateFlow<ClipDisplayMode> = _clipDisplayMode.asStateFlow()

  private val _playerError = MutableStateFlow<String?>(null)
  val playerError: StateFlow<String?> = _playerError.asStateFlow()

  private var webView: BackgroundPlayWebView? = null
  private var pageLoaded = false
  private var playerReady = false
  private var pendingVideoId: String? = null
  private var consecutiveErrors = 0
  private var pausedByTransientAudioLoss = false

  private var visibleContainerRef: WeakReference<FrameLayout>? = null
  private var fallbackContainerRef: WeakReference<FrameLayout>? = null

  private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
    when (focusChange) {
      AudioManager.AUDIOFOCUS_LOSS -> {
        pausedByTransientAudioLoss = false
        pause()
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        if (_isPlaying.value) {
          pausedByTransientAudioLoss = true
          pause()
        }
      }
      AudioManager.AUDIOFOCUS_GAIN -> {
        if (pausedByTransientAudioLoss) {
          pausedByTransientAudioLoss = false
          resume()
        }
      }
    }
  }

  private val audioFocusRequest: AudioFocusRequest? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        .setAcceptsDelayedFocusGain(true)
        .setOnAudioFocusChangeListener(audioFocusListener, mainHandler)
        .build()
    } else {
      null
    }

  fun updateQueue(tracks: List<YouTubeTrack>) {
    if (tracks.isNotEmpty()) {
      _queue.value = tracks
    }
  }

  fun playTrack(track: YouTubeTrack, queueOverride: List<YouTubeTrack>? = null) {
    mainHandler.post {
      if (!queueOverride.isNullOrEmpty()) {
        _queue.value = queueOverride
      } else if (_queue.value.none { it.videoId == track.videoId }) {
        _queue.value = listOf(track) + _queue.value
      }
      _playerError.value = null
      _currentTrack.value = track
      _isBuffering.value = true
      _isPlaying.value = true
      consecutiveErrors = 0

      requestAudioFocus()
      val wv = ensureWebView()
      wv.onResume()
      wv.resumeTimers()

      val safeId = sanitizeVideoId(track.videoId)
      if (safeId.isBlank()) {
        _playerError.value = "Identifiant vidéo YouTube invalide."
        _isPlaying.value = false
        _isBuffering.value = false
        return@post
      }

      if (!pageLoaded) {
        pageLoaded = true
        playerReady = false
        pendingVideoId = safeId
        wv.loadDataWithBaseURL(
          "https://www.youtube.com",
          buildYouTubePlayerHtml(safeId),
          "text/html",
          "utf-8",
          null,
        )
      } else if (!playerReady) {
        pendingVideoId = safeId
      } else {
        wv.evaluateJavascript("if(window.bravLoadVideo){window.bravLoadVideo('$safeId');}", null)
      }

      syncForegroundService()
    }
  }

  fun togglePlayPause() {
    mainHandler.post {
      if (_currentTrack.value == null) return@post
      if (_isPlaying.value) {
        pause()
      } else {
        resume()
      }
    }
  }

  fun pause() {
    mainHandler.post {
      _isPlaying.value = false
      _isBuffering.value = false
      webView?.evaluateJavascript("if(window.bravPause){window.bravPause();}", null)
      syncForegroundService()
    }
  }

  fun resume() {
    mainHandler.post {
      val track = _currentTrack.value ?: return@post
      requestAudioFocus()
      val wv = ensureWebView()
      wv.onResume()
      wv.resumeTimers()
      _isPlaying.value = true
      if (playerReady) {
        wv.evaluateJavascript("if(window.bravPlay){window.bravPlay();}", null)
      } else {
        playTrack(track)
      }
      syncForegroundService()
    }
  }

  fun playNext(fromAutoPlay: Boolean = false) {
    mainHandler.post {
      val list = _queue.value
      val current = _currentTrack.value
      if (list.isEmpty()) {
        if (fromAutoPlay) {
          _isPlaying.value = false
          syncForegroundService()
        }
        return@post
      }
      val idx = if (current == null) -1 else list.indexOfFirst { it.videoId == current.videoId }
      val nextTrack =
        if (idx >= 0) {
          list[(idx + 1) % list.size]
        } else {
          list.first()
        }
      playTrack(nextTrack)
    }
  }

  fun playPrevious() {
    mainHandler.post {
      val list = _queue.value
      val current = _currentTrack.value
      if (list.isEmpty()) return@post
      val idx = if (current == null) 0 else list.indexOfFirst { it.videoId == current.videoId }
      val prevTrack =
        if (idx >= 0) {
          list[(idx - 1 + list.size) % list.size]
        } else {
          list.first()
        }
      playTrack(prevTrack)
    }
  }

  fun stopPlayback() {
    mainHandler.post {
      pausedByTransientAudioLoss = false
      _isPlaying.value = false
      _isBuffering.value = false
      _currentTrack.value = null
      _playerError.value = null
      pendingVideoId = null
      webView?.evaluateJavascript("if(window.bravStop){window.bravStop();}", null)
      abandonAudioFocus()
      stopForegroundService()
    }
  }

  fun setClipDisplayMode(mode: ClipDisplayMode) {
    _clipDisplayMode.value = mode
    mainHandler.post { reattachToAppropriateContainer() }
  }

  fun toggleAutoPlayNext() {
    _autoPlayNextEnabled.value = !_autoPlayNextEnabled.value
  }

  fun setAutoPlayNext(enabled: Boolean) {
    _autoPlayNextEnabled.value = enabled
  }

  fun registerVisibleContainer(container: FrameLayout) {
    visibleContainerRef = WeakReference(container)
    if (_clipDisplayMode.value != ClipDisplayMode.HIDDEN_AUDIO_ONLY) {
      attachWebViewTo(container)
    }
  }

  fun unregisterVisibleContainer(container: FrameLayout) {
    if (visibleContainerRef?.get() === container) {
      visibleContainerRef = null
    }
    val fallback = fallbackContainerRef?.get()
    if (fallback != null) {
      attachWebViewTo(fallback)
    } else {
      (webView?.parent as? ViewGroup)?.removeView(webView)
    }
  }

  fun registerFallbackContainer(container: FrameLayout) {
    fallbackContainerRef = WeakReference(container)
    val visible = visibleContainerRef?.get()
    if (visible == null || _clipDisplayMode.value == ClipDisplayMode.HIDDEN_AUDIO_ONLY) {
      attachWebViewTo(container)
    }
  }

  fun unregisterFallbackContainer(container: FrameLayout) {
    if (fallbackContainerRef?.get() === container) {
      fallbackContainerRef = null
    }
  }

  private fun reattachToAppropriateContainer() {
    val visible = visibleContainerRef?.get()
    val fallback = fallbackContainerRef?.get()
    if (_clipDisplayMode.value != ClipDisplayMode.HIDDEN_AUDIO_ONLY && visible != null) {
      attachWebViewTo(visible)
    } else if (fallback != null) {
      attachWebViewTo(fallback)
    }
  }

  private fun attachWebViewTo(target: FrameLayout) {
    if (_currentTrack.value == null && webView == null) return
    val wv = ensureWebView()
    if (wv.parent === target) return
    (wv.parent as? ViewGroup)?.removeView(wv)
    wv.layoutParams =
      FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    target.addView(wv)
    wv.onResume()
    wv.resumeTimers()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun ensureWebView(): BackgroundPlayWebView {
    webView?.let { return it }
    val created =
      BackgroundPlayWebView(appContext) { _currentTrack.value != null }.apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webChromeClient = WebChromeClient()
        webViewClient =
          object : WebViewClient() {
            override fun onReceivedError(
              view: WebView?,
              request: WebResourceRequest?,
              error: WebResourceError?,
            ) {
              if (request?.isForMainFrame == true) {
                Log.w(TAG, "WebView main frame error: ${error?.description}")
              }
            }
          }
        addJavascriptInterface(BravPlayerJsBridge(), "BravPlayerBridge")
      }
    webView = created
    return created
  }

  private fun requestAudioFocus() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
    } else {
      @Suppress("DEPRECATION")
      audioManager.requestAudioFocus(
        audioFocusListener,
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN,
      )
    }
  }

  private fun abandonAudioFocus() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    } else {
      @Suppress("DEPRECATION")
      audioManager.abandonAudioFocus(audioFocusListener)
    }
  }

  private fun syncForegroundService() {
    val track = _currentTrack.value
    if (track == null) {
      stopForegroundService()
      return
    }
    val intent =
      Intent(appContext, MusicPlaybackService::class.java).apply {
        action = MusicPlaybackService.ACTION_SYNC_STATE
      }
    runCatching { ContextCompat.startForegroundService(appContext, intent) }
  }

  private fun stopForegroundService() {
    val intent =
      Intent(appContext, MusicPlaybackService::class.java).apply {
        action = MusicPlaybackService.ACTION_STOP
      }
    runCatching { appContext.stopService(intent) }
  }

  private inner class BravPlayerJsBridge {
    @JavascriptInterface
    fun onPlayerReady() {
      mainHandler.post {
        playerReady = true
        val pending = pendingVideoId
        if (!pending.isNullOrBlank()) {
          pendingVideoId = null
          webView?.evaluateJavascript(
            "if(window.bravLoadVideo){window.bravLoadVideo('$pending');}",
            null,
          )
        }
      }
    }

    @JavascriptInterface
    fun onStateChanged(state: Int) {
      mainHandler.post {
        when (state) {
          STATE_PLAYING -> {
            consecutiveErrors = 0
            _playerError.value = null
            _isBuffering.value = false
            _isPlaying.value = true
            syncForegroundService()
          }
          STATE_PAUSED -> {
            _isBuffering.value = false
            _isPlaying.value = false
            syncForegroundService()
          }
          STATE_BUFFERING -> {
            _isBuffering.value = true
          }
          STATE_ENDED -> {
            _isBuffering.value = false
            if (_autoPlayNextEnabled.value && _queue.value.isNotEmpty()) {
              playNext(fromAutoPlay = true)
            } else {
              _isPlaying.value = false
              syncForegroundService()
            }
          }
        }
      }
    }

    @JavascriptInterface
    fun onPlayerError(code: Int) {
      mainHandler.post {
        Log.w(TAG, "YouTube IFrame player error code=$code")
        _isBuffering.value = false
        consecutiveErrors++
        val canAutoSkip =
          _autoPlayNextEnabled.value && _queue.value.size > 1 && consecutiveErrors <= 2
        if ((code == 101 || code == 150 || code == 100 || code == 5) && canAutoSkip) {
          _playerError.value = "Titre indisponible en lecture intégrée, passage au suivant..."
          mainHandler.postDelayed({ playNext(fromAutoPlay = true) }, 750L)
        } else {
          _isPlaying.value = false
          _playerError.value =
            when (code) {
              101, 150 -> "L'éditeur de cette vidéo bloque la lecture intégrée."
              100 -> "Vidéo introuvable ou privée."
              else -> "Lecture impossible pour ce titre (code $code)."
            }
          syncForegroundService()
        }
      }
    }
  }

  private fun sanitizeVideoId(raw: String): String =
    raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(16)

  private fun buildYouTubePlayerHtml(initialVideoId: String): String {
    return """
      <!DOCTYPE html>
      <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000000; overflow: hidden; }
            #ytplayer { width: 100%; height: 100%; border: 0; }
          </style>
          <script>
            (function() {
              try {
                Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                var origDocAdd = document.addEventListener.bind(document);
                document.addEventListener = function(type, listener, options) {
                  if (type === 'visibilitychange' || type === 'webkitvisibilitychange' || type === 'pagehide' || type === 'freeze') {
                    return;
                  }
                  return origDocAdd(type, listener, options);
                };
                var origWinAdd = window.addEventListener.bind(window);
                window.addEventListener = function(type, listener, options) {
                  if (type === 'visibilitychange' || type === 'pagehide' || type === 'freeze' || type === 'blur') {
                    return;
                  }
                  return origWinAdd(type, listener, options);
                };
              } catch(e) {}
            })();
          </script>
        </head>
        <body>
          <div id="ytplayer"></div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            var bravYtPlayer = null;
            var bravReady = false;
            var bravQueuedId = "$initialVideoId";

            function onYouTubeIframeAPIReady() {
              bravYtPlayer = new YT.Player('ytplayer', {
                height: '100%',
                width: '100%',
                videoId: bravQueuedId,
                playerVars: {
                  autoplay: 1,
                  playsinline: 1,
                  rel: 0,
                  modestbranding: 1,
                  enablejsapi: 1,
                  origin: 'https://www.youtube.com'
                },
                events: {
                  'onReady': function(event) {
                    bravReady = true;
                    try { event.target.playVideo(); } catch(e) {}
                    if (window.BravPlayerBridge) { window.BravPlayerBridge.onPlayerReady(); }
                  },
                  'onStateChange': function(event) {
                    if (window.BravPlayerBridge) { window.BravPlayerBridge.onStateChanged(event.data); }
                  },
                  'onError': function(event) {
                    if (window.BravPlayerBridge) { window.BravPlayerBridge.onPlayerError(event.data); }
                  }
                }
              });
            }

            window.bravLoadVideo = function(videoId) {
              bravQueuedId = videoId;
              if (bravYtPlayer && bravReady && typeof bravYtPlayer.loadVideoById === 'function') {
                bravYtPlayer.loadVideoById(videoId);
              }
            };

            window.bravPlay = function() {
              if (bravYtPlayer && bravReady && typeof bravYtPlayer.playVideo === 'function') {
                bravYtPlayer.playVideo();
              }
            };

            window.bravPause = function() {
              if (bravYtPlayer && bravReady && typeof bravYtPlayer.pauseVideo === 'function') {
                bravYtPlayer.pauseVideo();
              }
            };

            window.bravStop = function() {
              if (bravYtPlayer && bravReady && typeof bravYtPlayer.stopVideo === 'function') {
                bravYtPlayer.stopVideo();
              }
            };
          </script>
        </body>
      </html>
    """.trimIndent()
  }

  companion object {
    private const val TAG = "BravMusicPlayerHost"
    private const val STATE_ENDED = 0
    private const val STATE_PLAYING = 1
    private const val STATE_PAUSED = 2
    private const val STATE_BUFFERING = 3

    @Volatile private var instance: BravMusicPlayerHost? = null

    fun getInstance(context: Context): BravMusicPlayerHost {
      return instance
        ?: synchronized(this) {
          instance ?: BravMusicPlayerHost(context.applicationContext).also { instance = it }
        }
    }
  }
}
