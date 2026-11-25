package com.example.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.network.DeviceTelemetry
import com.example.data.network.DiagnosticsNetworkService
import com.example.data.network.PublicIpReport
import com.example.data.network.SelfTestItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
  val isCheckingIp: Boolean = false,
  val ipReport: PublicIpReport? = null,
  val ipError: String? = null,
  val telemetry: DeviceTelemetry? = null,
  val isRunningTests: Boolean = false,
  val selfTests: List<SelfTestItem> = emptyList(),
)

class DiagnosticsViewModel(
  private val service: DiagnosticsNetworkService
) : ViewModel() {

  private val _uiState = MutableStateFlow(DiagnosticsUiState(telemetry = service.readDeviceTelemetry()))
  val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

  init {
    refreshPublicIp()
  }

  fun refreshPublicIp() {
    viewModelScope.launch {
      _uiState.value =
        _uiState.value.copy(
          isCheckingIp = true,
          ipError = null,
          telemetry = service.readDeviceTelemetry(),
        )
      service
        .fetchVerifiedPublicIp()
        .onSuccess { report ->
          _uiState.value = _uiState.value.copy(isCheckingIp = false, ipReport = report, ipError = null)
        }
        .onFailure { err ->
          _uiState.value =
            _uiState.value.copy(
              isCheckingIp = false,
              ipError = err.message ?: "Public IP verification failed",
            )
        }
    }
  }

  fun runFifteenTests(
    usTimezoneId: String,
    oauthClientId: String,
    oauthConnected: Boolean,
    isSignedIn: Boolean,
    cloudHealthCheck: suspend () -> Boolean,
    hasCameraHardware: Boolean,
  ) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isRunningTests = true)
      val results =
        service.runFifteenPointSelfTest(
          usTimezoneId = usTimezoneId,
          oauthClientId = oauthClientId,
          oauthConnected = oauthConnected,
          isSignedIn = isSignedIn,
          cloudHealthCheck = cloudHealthCheck,
          hasCameraHardware = hasCameraHardware,
        )
      _uiState.value =
        _uiState.value.copy(
          isRunningTests = false,
          selfTests = results,
          telemetry = service.readDeviceTelemetry(),
        )
    }
  }
}
