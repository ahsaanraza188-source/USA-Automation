package com.example.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DevicePreferences
import com.example.data.network.AppDiagnosticsLogger
import com.example.data.network.DeviceGeolocationSnapshot
import com.example.data.network.DeviceTelemetry
import com.example.data.network.DiagnosticLogEntry
import com.example.data.network.DiagnosticsNetworkService
import com.example.data.network.DnsDiagnosticResult
import com.example.data.network.HttpsProbeResult
import com.example.data.network.PublicIpReport
import com.example.data.network.SelfTestItem
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
  val isCheckingIp: Boolean = false,
  val ipReport: PublicIpReport? = null,
  val ipError: String? = null,
  val telemetry: DeviceTelemetry? = null,
  val geolocation: DeviceGeolocationSnapshot? = null,
  val isRunningNetSuite: Boolean = false,
  val dnsResults: List<DnsDiagnosticResult> = emptyList(),
  val httpsResults: List<HttpsProbeResult> = emptyList(),
  val isRunningTests: Boolean = false,
  val selfTests: List<SelfTestItem> = emptyList(),
)

class DiagnosticsViewModel(
  private val service: DiagnosticsNetworkService
) : ViewModel() {

  private val _uiState =
    MutableStateFlow(
      DiagnosticsUiState(
        telemetry = service.readDeviceTelemetry(),
        geolocation = service.readDeviceGeolocation(),
      )
    )
  val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

  val logs: StateFlow<List<DiagnosticLogEntry>> =
    AppDiagnosticsLogger.entries.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = emptyList(),
    )

  init {
    refreshNetworkDiagnostics()
  }

  fun refreshGeolocationAndTelemetry() {
    _uiState.value =
      _uiState.value.copy(
        telemetry = service.readDeviceTelemetry(),
        geolocation = service.readDeviceGeolocation(),
      )
  }

  fun refreshPublicIp() {
    refreshNetworkDiagnostics()
  }

  fun refreshNetworkDiagnostics() {
    viewModelScope.launch {
      _uiState.value =
        _uiState.value.copy(
          isCheckingIp = true,
          isRunningNetSuite = true,
          ipError = null,
          telemetry = service.readDeviceTelemetry(),
          geolocation = service.readDeviceGeolocation(),
        )
      val ipDeferred = async { service.fetchVerifiedPublicIp() }
      val dnsDeferred = async { service.runDnsDiagnostics() }
      val httpsDeferred = async { service.runHttpsConnectivityTests() }

      val ipResult = ipDeferred.await()
      val dns = dnsDeferred.await()
      val https = httpsDeferred.await()

      _uiState.value =
        _uiState.value.copy(
          isCheckingIp = false,
          isRunningNetSuite = false,
          ipReport = ipResult.getOrNull() ?: _uiState.value.ipReport,
          ipError = ipResult.exceptionOrNull()?.message,
          dnsResults = dns,
          httpsResults = https,
          telemetry = service.readDeviceTelemetry(),
          geolocation = service.readDeviceGeolocation(),
        )
    }
  }

  fun runFifteenTests(
    preferences: DevicePreferences,
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
          preferences = preferences,
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
          geolocation = service.readDeviceGeolocation(),
        )
    }
  }

  fun clearLogs() {
    AppDiagnosticsLogger.clear()
  }
}
