package com.example.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ProxyCredentials
import com.example.data.local.SecureNetworkConfigStore
import com.example.data.model.DevicePreferences
import com.example.data.network.AppDiagnosticsLogger
import com.example.data.network.DeviceGeolocationSnapshot
import com.example.data.network.DeviceTelemetry
import com.example.data.network.DiagnosticLogEntry
import com.example.data.network.DiagnosticsNetworkService
import com.example.data.network.DnsDiagnosticResult
import com.example.data.network.HttpsProbeResult
import com.example.data.network.LeakCheckReport
import com.example.data.network.ProxyTunnelController
import com.example.data.network.ProxyTunnelRuntimeState
import com.example.data.network.PublicIpReport
import com.example.data.network.SelfTestItem
import com.example.domain.BrowserGeoPermissionMode
import com.example.domain.ProxyProtocol
import com.example.domain.VirtualLocationCatalog
import com.example.domain.VirtualLocationConfig
import com.example.domain.VirtualLocationMode
import com.example.domain.VirtualLocationPreset
import com.example.domain.VpnConnectionMode
import com.example.domain.VpnProxyConfig
import java.util.UUID
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
  val leakReport: LeakCheckReport? = null,
  val isRunningLeakCheck: Boolean = false,
  val isRunningTests: Boolean = false,
  val selfTests: List<SelfTestItem> = emptyList(),
  val isRunningVerification: Boolean = false,
  val verificationItems: List<SelfTestItem> = emptyList(),
  val browserGeoProbeResult: String? = null,
  val actionFeedback: String? = null,
)

class DiagnosticsViewModel(
  private val service: DiagnosticsNetworkService,
  private val configStore: SecureNetworkConfigStore,
  val proxyController: ProxyTunnelController,
) : ViewModel() {

  private val _uiState =
    MutableStateFlow(
      DiagnosticsUiState(
        telemetry = service.readDeviceTelemetry(),
        geolocation = service.readDeviceGeolocation(),
      )
    )
  val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

  val locationConfig: StateFlow<VirtualLocationConfig> =
    configStore.locationConfigFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = VirtualLocationConfig(),
    )

  val customLocations: StateFlow<List<VirtualLocationPreset>> =
    configStore.customLocationsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = emptyList(),
    )

  val vpnProxyConfig: StateFlow<VpnProxyConfig> =
    configStore.vpnProxyConfigFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = VpnProxyConfig(),
    )

  val proxyRuntimeState: StateFlow<ProxyTunnelRuntimeState> = proxyController.state

  val logs: StateFlow<List<DiagnosticLogEntry>> =
    AppDiagnosticsLogger.entries.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = emptyList(),
    )

  init {
    refreshNetworkDiagnostics()
  }

  fun clearFeedback() {
    _uiState.value = _uiState.value.copy(actionFeedback = null)
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

  // --- Virtual GPS Location Manager Actions ---
  fun toggleVirtualLocation(enabled: Boolean) {
    viewModelScope.launch {
      val current = locationConfig.value
      val nextMode =
        if (enabled && current.mode == VirtualLocationMode.DISABLED) VirtualLocationMode.VIRTUAL_GPS
        else current.mode
      configStore.saveLocationConfig(current.copy(enabled = enabled, mode = nextMode))
      _uiState.value =
        _uiState.value.copy(
          actionFeedback = if (enabled) "Virtual GPS enabled (${current.displayLabel})" else "Virtual GPS disabled"
        )
    }
  }

  fun setLocationMode(mode: VirtualLocationMode) {
    viewModelScope.launch {
      val current = locationConfig.value
      configStore.saveLocationConfig(
        current.copy(
          mode = mode,
          enabled = mode != VirtualLocationMode.DISABLED,
        )
      )
    }
  }

  fun setBrowserGeoPermission(permission: BrowserGeoPermissionMode) {
    viewModelScope.launch {
      configStore.saveLocationConfig(locationConfig.value.copy(browserPermission = permission))
    }
  }

  fun setWebrtcLeakProtection(enabled: Boolean) {
    viewModelScope.launch {
      configStore.saveLocationConfig(locationConfig.value.copy(webrtcLeakProtection = enabled))
    }
  }

  fun selectLocationPreset(preset: VirtualLocationPreset) {
    viewModelScope.launch {
      val current = locationConfig.value
      configStore.saveLocationConfig(
        current.copy(
          enabled = true,
          mode = VirtualLocationMode.VIRTUAL_GPS,
          selectedPresetId = preset.id,
          city = preset.city,
          state = preset.state,
          stateCode = preset.stateCode,
          country = preset.country,
          countryCode = preset.countryCode,
          latitude = preset.latitude,
          longitude = preset.longitude,
        )
      )
      _uiState.value =
        _uiState.value.copy(actionFeedback = "Virtual GPS set to ${preset.displayName}")
    }
  }

  fun applyManualCoordinates(
    city: String,
    state: String,
    latText: String,
    lonText: String,
    accuracyText: String,
  ) {
    val lat = latText.trim().toDoubleOrNull()
    val lon = lonText.trim().toDoubleOrNull()
    val acc = accuracyText.trim().toFloatOrNull() ?: 18f
    val check = VirtualLocationCatalog.validateCoordinates(lat, lon, acc)
    if (check.isFailure) {
      _uiState.value = _uiState.value.copy(actionFeedback = check.exceptionOrNull()?.message)
      return
    }
    val (validLat, validLon) = check.getOrThrow()
    viewModelScope.launch {
      val current = locationConfig.value
      configStore.saveLocationConfig(
        current.copy(
          enabled = true,
          mode = VirtualLocationMode.VIRTUAL_GPS,
          selectedPresetId = "manual",
          city = city.trim().ifBlank { "Custom Location" },
          state = state.trim().ifBlank { "United States" },
          stateCode = state.trim().take(2).uppercase().ifBlank { "US" },
          latitude = validLat,
          longitude = validLon,
          accuracyMeters = acc,
        )
      )
      _uiState.value =
        _uiState.value.copy(actionFeedback = "Updated coordinates to ${String.format(java.util.Locale.US, "%.4f, %.4f", validLat, validLon)}")
    }
  }

  fun addCustomLocationPreset(
    city: String,
    state: String,
    latText: String,
    lonText: String,
  ) {
    val cleanCity = city.trim()
    val cleanState = state.trim()
    if (cleanCity.isBlank() || cleanState.isBlank()) {
      _uiState.value = _uiState.value.copy(actionFeedback = "Enter both city and state for the custom location.")
      return
    }
    val lat = latText.trim().toDoubleOrNull()
    val lon = lonText.trim().toDoubleOrNull()
    val check = VirtualLocationCatalog.validateCoordinates(lat, lon)
    if (check.isFailure) {
      _uiState.value = _uiState.value.copy(actionFeedback = check.exceptionOrNull()?.message)
      return
    }
    val (validLat, validLon) = check.getOrThrow()
    val preset =
      VirtualLocationPreset(
        id = "custom_${UUID.randomUUID().toString().take(8)}",
        city = cleanCity,
        state = cleanState,
        stateCode = cleanState.take(2).uppercase(),
        latitude = validLat,
        longitude = validLon,
        timezoneId = "America/New_York",
        isCustom = true,
      )
    viewModelScope.launch {
      configStore.addCustomLocation(preset)
      selectLocationPreset(preset)
    }
  }

  fun deleteCustomLocationPreset(id: String) {
    viewModelScope.launch { configStore.deleteCustomLocation(id) }
  }

  // --- USA VPN & Proxy Tunnel Actions ---
  fun setVpnConnectionMode(mode: VpnConnectionMode) {
    viewModelScope.launch {
      if (mode != VpnConnectionMode.PROXY_TUNNEL && proxyController.isProxyRoutingActive()) {
        proxyController.disconnect()
      }
      configStore.saveProxyConfig(vpnProxyConfig.value.copy(mode = mode))
      refreshNetworkDiagnostics()
    }
  }

  fun selectGatewayRegion(gatewayId: String) {
    viewModelScope.launch {
      configStore.saveProxyConfig(vpnProxyConfig.value.copy(selectedGatewayId = gatewayId))
    }
  }

  fun connectProxyServer(
    protocol: ProxyProtocol,
    gatewayId: String,
    host: String,
    portStr: String,
    providerLabel: String,
    username: String,
    password: String,
    bypassLocal: Boolean,
    clearSavedCredentials: Boolean,
  ) {
    val endpointResult = VirtualLocationCatalog.validateProxyEndpoint(host, portStr)
    if (endpointResult.isFailure) {
      _uiState.value = _uiState.value.copy(actionFeedback = endpointResult.exceptionOrNull()?.message)
      return
    }
    val (cleanHost, port) = endpointResult.getOrThrow()
    viewModelScope.launch {
      val updatedConfig =
        VpnProxyConfig(
          mode = VpnConnectionMode.PROXY_TUNNEL,
          selectedGatewayId = gatewayId,
          protocol = protocol,
          host = cleanHost,
          port = port,
          bypassLocalAddresses = bypassLocal,
          providerLabel = providerLabel.trim().ifBlank { "User-Authorized Proxy" },
        )
      configStore.saveProxyConfig(
        config = updatedConfig,
        username = username.takeIf { it.isNotBlank() },
        password = password.takeIf { it.isNotBlank() },
        clearExistingCredentials = clearSavedCredentials,
      )
      val creds: ProxyCredentials? =
        when {
          clearSavedCredentials -> null
          username.isNotBlank() || password.isNotBlank() -> ProxyCredentials(username.trim(), password)
          else -> configStore.readProxyCredentials()
        }
      val result =
        proxyController.connect(
          config = updatedConfig,
          credentials = creds,
          verifyExit = { candidateClient -> service.fetchVerifiedPublicIp(candidateClient) },
        )
      result
        .onSuccess { rep ->
          _uiState.value =
            _uiState.value.copy(
              ipReport = rep,
              ipError = null,
              actionFeedback =
                if (rep.isVerifiedUsa) "Verified USA Proxy Exit: ${rep.ipAddress} (${rep.city}, ${rep.region})"
                else "Proxy connected, but exit IP is ${rep.countryName} (${rep.countryCode})",
            )
          refreshNetworkDiagnostics()
        }
        .onFailure { err ->
          _uiState.value =
            _uiState.value.copy(actionFeedback = err.message ?: "Proxy connection failed")
        }
    }
  }

  fun disconnectProxyServer() {
    viewModelScope.launch {
      proxyController.disconnect()
      _uiState.value = _uiState.value.copy(actionFeedback = "Proxy disconnected; traffic restored to system network.")
      refreshNetworkDiagnostics()
    }
  }

  // --- Verification & Leak Testing ---
  fun runLeakCheck() {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isRunningLeakCheck = true)
      val report = service.runLeakDiagnostics(locationConfig.value)
      _uiState.value = _uiState.value.copy(isRunningLeakCheck = false, leakReport = report)
    }
  }

  fun runBrowserGeoProbeOnly(probeRunner: suspend () -> String) {
    viewModelScope.launch {
      val result = probeRunner()
      _uiState.value =
        _uiState.value.copy(
          browserGeoProbeResult = result,
          actionFeedback = result,
        )
    }
  }

  fun runFullVerificationSuite(
    preferences: DevicePreferences,
    probeRunner: suspend () -> String,
  ) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isRunningVerification = true)
      val probeResult = probeRunner()
      val items =
        service.runLocationAndNetworkVerification(
          preferences = preferences,
          locationConfig = locationConfig.value,
          browserGeoProbeResult = probeResult,
        )
      val leak = service.runLeakDiagnostics(locationConfig.value)
      val ip = service.fetchVerifiedPublicIp().getOrNull()
      _uiState.value =
        _uiState.value.copy(
          isRunningVerification = false,
          browserGeoProbeResult = probeResult,
          verificationItems = items,
          leakReport = leak,
          ipReport = ip ?: _uiState.value.ipReport,
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
