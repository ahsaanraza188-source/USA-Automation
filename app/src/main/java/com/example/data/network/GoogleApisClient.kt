package com.example.data.network

import android.util.Base64
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class GmailMessageSummary(
  val id: String,
  val from: String,
  val subject: String,
  val date: String,
  val snippet: String,
)

data class YouTubeVideoItem(
  val videoId: String,
  val title: String,
  val channelTitle: String,
  val thumbnailUrl: String,
  val publishedAt: String,
)

data class MapSearchPlace(
  val displayName: String,
  val lat: Double,
  val lon: Double,
)

class GoogleApisClient {
  private val client =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(12, TimeUnit.SECONDS)
      .build()

  suspend fun fetchGmailInbox(accessToken: String, maxResults: Int = 12): Result<List<GmailMessageSummary>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val listReq =
          Request.Builder()
            .url("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=$maxResults")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val messageIds =
          client.newCall(listReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Gmail API error ${resp.code}: ${body.take(160)}")
            val json = JSONObject(body)
            val arr = json.optJSONArray("messages") ?: return@use emptyList<String>()
            (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
          }

        messageIds.map { msgId ->
          val detailReq =
            Request.Builder()
              .url(
                "https://gmail.googleapis.com/gmail/v1/users/me/messages/$msgId?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date"
              )
              .header("Authorization", "Bearer $accessToken")
              .get()
              .build()

          client.newCall(detailReq).execute().use { resp ->
            val json = JSONObject(resp.body?.string().orEmpty())
            val headers = json.optJSONObject("payload")?.optJSONArray("headers")
            var from = "Unknown sender"
            var subject = "(No subject)"
            var date = ""
            if (headers != null) {
              for (i in 0 until headers.length()) {
                val h = headers.getJSONObject(i)
                when (h.optString("name")) {
                  "From" -> from = h.optString("value", from)
                  "Subject" -> subject = h.optString("value", subject)
                  "Date" -> date = h.optString("value", date)
                }
              }
            }
            GmailMessageSummary(
              id = msgId,
              from = from,
              subject = subject,
              date = date.take(22),
              snippet = json.optString("snippet", ""),
            )
          }
        }
      }
    }

  suspend fun sendGmailMessage(
    accessToken: String,
    to: String,
    subject: String,
    bodyText: String,
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val rawMime = "To: $to\r\nSubject: $subject\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n$bodyText"
        val encoded =
          Base64.encodeToString(
            rawMime.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
          )
        val payload = JSONObject().put("raw", encoded).toString()
        val req =
          Request.Builder()
            .url("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) {
            error("Send failed (${resp.code}): ${resp.body?.string()?.take(160)}")
          }
        }
      }
    }

  suspend fun searchYouTube(accessToken: String, query: String): Result<List<YouTubeVideoItem>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val encodedQ = URLEncoder.encode(query.ifBlank { "USA tech news" }, "UTF-8")
        val url =
          "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=12&regionCode=US&q=$encodedQ"
        val req =
          Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
          val body = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) error("YouTube API error ${resp.code}: ${body.take(160)}")
          val json = JSONObject(body)
          val items = json.optJSONArray("items") ?: return@use emptyList()
          (0 until items.length()).mapNotNull { idx ->
            val item = items.getJSONObject(idx)
            val videoId = item.optJSONObject("id")?.optString("videoId").orEmpty()
            val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
            val thumb =
              snippet.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url").orEmpty()
            if (videoId.isBlank()) null
            else {
              YouTubeVideoItem(
                videoId = videoId,
                title = snippet.optString("title", "Video"),
                channelTitle = snippet.optString("channelTitle", ""),
                thumbnailUrl = thumb,
                publishedAt = snippet.optString("publishedAt", "").take(10),
              )
            }
          }
        }
      }
    }

  suspend fun fetchLikedYouTubeVideos(accessToken: String): Result<List<YouTubeVideoItem>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "https://www.googleapis.com/youtube/v3/videos?part=snippet&myRating=like&maxResults=12"
        val req =
          Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
          val body = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) error("YouTube API error ${resp.code}: ${body.take(160)}")
          val json = JSONObject(body)
          val items = json.optJSONArray("items") ?: return@use emptyList()
          (0 until items.length()).mapNotNull { idx ->
            val item = items.getJSONObject(idx)
            val videoId = item.optString("id", "")
            val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
            val thumb =
              snippet.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url").orEmpty()
            if (videoId.isBlank()) null
            else {
              YouTubeVideoItem(
                videoId = videoId,
                title = snippet.optString("title", "Video"),
                channelTitle = snippet.optString("channelTitle", ""),
                thumbnailUrl = thumb,
                publishedAt = snippet.optString("publishedAt", "").take(10),
              )
            }
          }
        }
      }
    }

  suspend fun searchPlacesNominatim(query: String): Result<List<MapSearchPlace>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val req =
          Request.Builder()
            .url("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=6&countrycodes=us")
            .header("User-Agent", "USAVirtualAndroidPro/1.0")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
          val body = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) error("Geocoding error ${resp.code}")
          val arr = org.json.JSONArray(body)
          (0 until arr.length()).map { idx ->
            val obj = arr.getJSONObject(idx)
            MapSearchPlace(
              displayName = obj.optString("display_name", query),
              lat = obj.optString("lat", "40.7128").toDoubleOrNull() ?: 40.7128,
              lon = obj.optString("lon", "-74.0060").toDoubleOrNull() ?: -74.0060,
            )
          }
        }
      }
    }
}
