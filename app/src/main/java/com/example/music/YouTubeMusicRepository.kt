package com.example.music

import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.model.YouTubeTrack
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class YouTubeMusicRepository {

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .build()

  val isApiKeyConfigured: Boolean
    get() {
      val key = BuildConfig.YOUTUBE_API_KEY
      return key.isNotBlank() && key != PLACEHOLDER_KEY
    }

  suspend fun fetchTrendingMusic(regionCode: String = "FR"): Result<List<YouTubeTrack>> =
    withContext(Dispatchers.IO) {
      if (!isApiKeyConfigured) {
        return@withContext Result.failure(
          IllegalStateException(
            "Clé YOUTUBE_API_KEY manquante. Ajoutez votre clé YouTube Data API v3 dans le panneau Secrets d'AI Studio."
          )
        )
      }
      val url =
        Uri.parse("https://www.googleapis.com/youtube/v3/videos")
          .buildUpon()
          .appendQueryParameter("part", "snippet")
          .appendQueryParameter("chart", "mostPopular")
          .appendQueryParameter("videoCategoryId", "10")
          .appendQueryParameter("regionCode", regionCode)
          .appendQueryParameter("maxResults", "20")
          .appendQueryParameter("key", BuildConfig.YOUTUBE_API_KEY)
          .build()
          .toString()

      executeVideoListRequest(url, isSearchEndpoint = false)
    }

  suspend fun searchMusic(query: String): Result<List<YouTubeTrack>> =
    withContext(Dispatchers.IO) {
      if (!isApiKeyConfigured) {
        return@withContext Result.failure(
          IllegalStateException(
            "Clé YOUTUBE_API_KEY manquante. Ajoutez votre clé YouTube Data API v3 dans le panneau Secrets d'AI Studio."
          )
        )
      }
      val trimmed = query.trim()
      if (trimmed.isBlank()) return@withContext fetchTrendingMusic()

      val url =
        Uri.parse("https://www.googleapis.com/youtube/v3/search")
          .buildUpon()
          .appendQueryParameter("part", "snippet")
          .appendQueryParameter("type", "video")
          .appendQueryParameter("videoCategoryId", "10")
          .appendQueryParameter("videoEmbeddable", "true")
          .appendQueryParameter("maxResults", "20")
          .appendQueryParameter("q", trimmed)
          .appendQueryParameter("key", BuildConfig.YOUTUBE_API_KEY)
          .build()
          .toString()

      executeVideoListRequest(url, isSearchEndpoint = true)
    }

  private fun executeVideoListRequest(
    url: String,
    isSearchEndpoint: Boolean,
  ): Result<List<YouTubeTrack>> {
    return try {
      val request = Request.Builder().url(url).get().build()
      client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          val apiMsg =
            runCatching {
              JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
            }.getOrDefault("")
          val msg = apiMsg.ifBlank { "Erreur YouTube API (${response.code})" }
          return Result.failure(IllegalStateException(msg))
        }
        val json = JSONObject(body)
        val items = json.optJSONArray("items") ?: return Result.success(emptyList())
        val tracks = buildList {
          for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val videoId =
              if (isSearchEndpoint) {
                item.optJSONObject("id")?.optString("videoId").orEmpty()
              } else {
                item.optString("id")
              }
            if (videoId.isBlank()) continue
            val snippet = item.optJSONObject("snippet") ?: continue
            val thumbs = snippet.optJSONObject("thumbnails")
            val thumbUrl =
              thumbs?.optJSONObject("high")?.optString("url")
                ?: thumbs?.optJSONObject("medium")?.optString("url")
                ?: thumbs?.optJSONObject("default")?.optString("url")
                ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            add(
              YouTubeTrack(
                videoId = videoId,
                title = decodeHtmlEntities(snippet.optString("title")),
                channelTitle = decodeHtmlEntities(snippet.optString("channelTitle")),
                thumbnailUrl = thumbUrl,
                publishedAt = snippet.optString("publishedAt"),
              )
            )
          }
        }
        Result.success(tracks)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "YouTube Data API request failed", e)
      Result.failure(
        IllegalStateException(e.localizedMessage ?: "Erreur réseau lors de l'accès à YouTube.")
      )
    }
  }

  private fun decodeHtmlEntities(raw: String): String {
    return raw
      .replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
  }

  companion object {
    private const val TAG = "YouTubeMusicRepo"
    private const val PLACEHOLDER_KEY = "MY_YOUTUBE_API_KEY"

    fun extractVideoId(input: String): String? {
      val trimmed = input.trim()
      if (trimmed.matches(Regex("^[a-zA-Z0-9_\\-]{11}$"))) {
        return trimmed
      }
      val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
      val host = uri.host?.lowercase().orEmpty()
      if (host.contains("youtu.be")) {
        return uri.lastPathSegment?.takeIf { it.length == 11 }
      }
      if (host.contains("youtube.com")) {
        uri.getQueryParameter("v")?.takeIf { it.length == 11 }?.let { return it }
        val segments = uri.pathSegments
        val idx = segments.indexOfFirst { it == "embed" || it == "shorts" || it == "live" }
        if (idx >= 0 && idx + 1 < segments.size) {
          return segments[idx + 1].takeIf { it.length == 11 }
        }
      }
      return null
    }
  }
}
