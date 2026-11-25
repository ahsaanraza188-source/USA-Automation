package com.example.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.OAuthTokenState
import com.example.data.local.OAuthTokenStore
import com.example.data.network.GmailMessageSummary
import com.example.data.network.GoogleApisClient
import com.example.data.network.GoogleOAuthManager
import com.example.data.network.MapSearchPlace
import com.example.data.network.YouTubeVideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoogleServicesUiState(
  val isGmailLoading: Boolean = false,
  val gmailMessages: List<GmailMessageSummary> = emptyList(),
  val gmailError: String? = null,
  val gmailSendStatus: String? = null,
  val isYouTubeLoading: Boolean = false,
  val youtubeVideos: List<YouTubeVideoItem> = emptyList(),
  val youtubeError: String? = null,
  val isMapSearching: Boolean = false,
  val mapPlaces: List<MapSearchPlace> = emptyList(),
  val mapError: String? = null,
  val oauthFeedback: String? = null,
)

class GoogleServicesViewModel(
  private val tokenStore: OAuthTokenStore,
  private val oauthManager: GoogleOAuthManager,
  private val apisClient: GoogleApisClient,
) : ViewModel() {

  val tokenState: StateFlow<OAuthTokenState> =
    tokenStore.tokenState.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = OAuthTokenState(),
    )

  private val _uiState = MutableStateFlow(GoogleServicesUiState())
  val uiState: StateFlow<GoogleServicesUiState> = _uiState.asStateFlow()

  fun connectOAuth(clientId: String) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(oauthFeedback = null)
      oauthManager
        .launchPkceAuthorization(clientId)
        .onFailure { err ->
          _uiState.value = _uiState.value.copy(oauthFeedback = err.message ?: "OAuth launch failed")
        }
    }
  }

  fun disconnectOAuth() {
    viewModelScope.launch {
      oauthManager.disconnect()
      _uiState.value =
        _uiState.value.copy(
          gmailMessages = emptyList(),
          youtubeVideos = emptyList(),
          oauthFeedback = "Disconnected OAuth token from this device.",
        )
    }
  }

  fun refreshGmail() {
    val token = tokenState.value.accessToken
    if (token.isBlank()) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isGmailLoading = true, gmailError = null)
      apisClient
        .fetchGmailInbox(token)
        .onSuccess { list ->
          _uiState.value = _uiState.value.copy(isGmailLoading = false, gmailMessages = list)
        }
        .onFailure { err ->
          _uiState.value =
            _uiState.value.copy(isGmailLoading = false, gmailError = err.message ?: "Failed to load Gmail")
        }
    }
  }

  fun sendGmail(to: String, subject: String, body: String) {
    val token = tokenState.value.accessToken
    if (token.isBlank()) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isGmailLoading = true, gmailSendStatus = null)
      apisClient
        .sendGmailMessage(token, to, subject, body)
        .onSuccess {
          _uiState.value =
            _uiState.value.copy(isGmailLoading = false, gmailSendStatus = "Email sent via Gmail API")
          refreshGmail()
        }
        .onFailure { err ->
          _uiState.value =
            _uiState.value.copy(
              isGmailLoading = false,
              gmailSendStatus = err.message ?: "Failed to send email",
            )
        }
    }
  }

  fun searchYouTube(query: String) {
    val token = tokenState.value.accessToken
    if (token.isBlank()) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isYouTubeLoading = true, youtubeError = null)
      apisClient
        .searchYouTube(token, query)
        .onSuccess { list ->
          _uiState.value = _uiState.value.copy(isYouTubeLoading = false, youtubeVideos = list)
        }
        .onFailure { err ->
          _uiState.value =
            _uiState.value.copy(
              isYouTubeLoading = false,
              youtubeError = err.message ?: "YouTube API query failed",
            )
        }
    }
  }

  fun loadLikedYouTube() {
    val token = tokenState.value.accessToken
    if (token.isBlank()) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isYouTubeLoading = true, youtubeError = null)
      apisClient
        .fetchLikedYouTubeVideos(token)
        .onSuccess { list ->
          _uiState.value = _uiState.value.copy(isYouTubeLoading = false, youtubeVideos = list)
        }
        .onFailure { err ->
          _uiState.value =
            _uiState.value.copy(
              isYouTubeLoading = false,
              youtubeError = err.message ?: "Failed to load liked videos",
            )
        }
    }
  }

  fun searchUsPlaces(query: String) {
    if (query.isBlank()) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isMapSearching = true, mapError = null)
      apisClient
        .searchPlacesNominatim(query)
        .onSuccess { places ->
          _uiState.value = _uiState.value.copy(isMapSearching = false, mapPlaces = places)
        }
        .onFailure { err ->
          _uiState.value =
            _uiState.value.copy(
              isMapSearching = false,
              mapError = err.message ?: "Map search failed",
            )
        }
    }
  }

  fun clearFeedback() {
    _uiState.value = _uiState.value.copy(oauthFeedback = null, gmailSendStatus = null)
  }
}
