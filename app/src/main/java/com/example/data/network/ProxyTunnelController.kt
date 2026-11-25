package com.example.data.network

import android.content.Context
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.example.data.local.ProxyCredentials
import com.example.domain.ProxyProtocol
import com.example.domain.VpnProxyConfig
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient

enum class ProxyTunnelStatus {
  DISCONNECTED,
  CONNECTING,
  CONNECTED_USA,
  CONNECTED_NON_US,
  ERROR,
}

data class ProxyTunnelRuntimeState(
  val status: ProxyTunnelStatus = ProxyTunnelStatus.DISCONNECTED,
  val activeEndpointLabel: String = "",
  val protocolLabel: String = "",
  val providerLabel: String = "",
  val webViewProxySupported: Boolean = false,
  val webViewProxyApplied: Boolean = false,
  val errorMessage: String? = null,
  val verifiedIpReport: PublicIpReport? = null,
) {
  val isConnected: Boolean
    get() = status == ProxyTunnelStatus.CONNECTED_USA || status == ProxyTunnelStatus.CONNECTED_NON_US
}

/**
 * Controls legitimate HTTP/HTTPS/SOCKS5 proxy routing for both simulator OkHttp diagnostics
 * and simulator Chrome WebView (via androidx.webkit.ProxyController when supported).
 */
class ProxyTunnelController(context: Context) {
  private val executor = Executors.newSingleThreadExecutor()
  private val proxyOverrideSupported: Boolean =
    runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) }
      .getOrDefault(false)

  private val directClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(8, TimeUnit.SECONDS)
      .readTimeout(8, TimeUnit.SECONDS)
      .build()

  @Volatile private var proxiedClient: OkHttpClient? = null
  @Volatile private var activeCredentials: ProxyCredentials? = null

  private val _state =
    MutableStateFlow(
      ProxyTunnelRuntimeState(webViewProxySupported = proxyOverrideSupported)
    )
  val state: StateFlow<ProxyTunnelRuntimeState> = _state.asStateFlow()

  fun getActiveOkHttpClient(): OkHttpClient = proxiedClient ?: directClient

  fun getDirectOkHttpClient(): OkHttpClient = directClient

  fun getActiveCredentials(): ProxyCredentials? = activeCredentials

  fun isProxyRoutingActive(): Boolean = _state.value.isConnected && proxiedClient != null

  suspend fun connect(
    config: VpnProxyConfig,
    credentials: ProxyCredentials?,
    verifyExit: suspend (OkHttpClient) -> Result<PublicIpReport>,
  ): Result<PublicIpReport> {
    val cleanHost =
      config.host.trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("socks5://")
        .substringBefore("/")
    val port = config.port
    if (cleanHost.isBlank() || port !in 1..65535) {
      val msg = "Invalid proxy host or port. Configure a valid USA proxy server first."
      _state.value =
        _state.value.copy(
          status = ProxyTunnelStatus.ERROR,
          errorMessage = msg,
          webViewProxyApplied = false,
        )
      return Result.failure(IllegalArgumentException(msg))
    }

    _state.value =
      _state.value.copy(
        status = ProxyTunnelStatus.CONNECTING,
        activeEndpointLabel = "$cleanHost:$port",
        protocolLabel = config.protocol.label,
        providerLabel = config.providerLabel.ifBlank { "Configured Proxy" },
        errorMessage = null,
      )

    val javaProxyType =
      when (config.protocol) {
        ProxyProtocol.SOCKS5 -> Proxy.Type.SOCKS
        ProxyProtocol.HTTP, ProxyProtocol.HTTPS -> Proxy.Type.HTTP
      }
    val proxy = Proxy(javaProxyType, InetSocketAddress(cleanHost, port))

    val clientBuilder =
      OkHttpClient.Builder()
        .proxy(proxy)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)

    if (credentials != null && (credentials.username.isNotBlank() || credentials.password.isNotBlank())) {
      val proxyAuth = Authenticator { _, response ->
        if (response.request.header("Proxy-Authorization") != null) return@Authenticator null
        val basic = Credentials.basic(credentials.username, credentials.password)
        response.request.newBuilder().header("Proxy-Authorization", basic).build()
      }
      clientBuilder.proxyAuthenticator(proxyAuth)
    }

    val candidateClient = clientBuilder.build()
    activeCredentials = credentials

    // Verify real connectivity & exit IP through the configured proxy BEFORE marking connected
    val verifyResult = verifyExit(candidateClient)
    if (verifyResult.isFailure) {
      val err = verifyResult.exceptionOrNull()
      val msg = explainProxyFailure(err?.message ?: "Proxy connection failed")
      proxiedClient = null
      clearWebViewProxy()
      AppDiagnosticsLogger.error(
        component = "ProxyTunnel",
        message = "Failed to verify proxy $cleanHost:$port",
        throwable = err,
        recoveryHint = msg,
      )
      _state.value =
        _state.value.copy(
          status = ProxyTunnelStatus.ERROR,
          webViewProxyApplied = false,
          errorMessage = msg,
          verifiedIpReport = null,
        )
      return Result.failure(IllegalStateException(msg, err))
    }

    val report = verifyResult.getOrThrow()
    proxiedClient = candidateClient

    val wvApplied = applyWebViewProxy(config.protocol, cleanHost, port, config.bypassLocalAddresses)
    val finalStatus =
      if (report.isVerifiedUsa) ProxyTunnelStatus.CONNECTED_USA
      else ProxyTunnelStatus.CONNECTED_NON_US

    AppDiagnosticsLogger.info(
      component = "ProxyTunnel",
      message = "Connected to ${config.protocol.label} ($cleanHost:$port) -> Exit ${report.ipAddress} (${report.countryCode})",
      detail = "WebView ProxyOverride applied: $wvApplied",
    )

    _state.value =
      _state.value.copy(
        status = finalStatus,
        activeEndpointLabel = "$cleanHost:$port",
        protocolLabel = config.protocol.label,
        providerLabel = config.providerLabel.ifBlank { report.isp },
        webViewProxySupported = proxyOverrideSupported,
        webViewProxyApplied = wvApplied,
        errorMessage =
          if (!wvApplied) "OkHttp routed via proxy, but system WebView does not support PROXY_OVERRIDE on this device."
          else null,
        verifiedIpReport = report,
      )
    return Result.success(report)
  }

  suspend fun disconnect() {
    proxiedClient = null
    activeCredentials = null
    clearWebViewProxy()
    AppDiagnosticsLogger.info(
      component = "ProxyTunnel",
      message = "Disconnected proxy tunnel and cleared WebView proxy override.",
    )
    _state.value =
      ProxyTunnelRuntimeState(
        status = ProxyTunnelStatus.DISCONNECTED,
        webViewProxySupported = proxyOverrideSupported,
        webViewProxyApplied = false,
      )
  }

  private suspend fun applyWebViewProxy(
    protocol: ProxyProtocol,
    host: String,
    port: Int,
    bypassLocal: Boolean,
  ): Boolean {
    if (!proxyOverrideSupported) return false
    val rule =
      when (protocol) {
        ProxyProtocol.HTTPS -> "https://$host:$port"
        ProxyProtocol.HTTP -> "http://$host:$port"
        ProxyProtocol.SOCKS5 -> "socks5://$host:$port"
      }
    val builder = ProxyConfig.Builder().addProxyRule(rule)
    if (bypassLocal) {
      builder.addBypassRule("localhost")
      builder.addBypassRule("127.0.0.1")
      builder.addBypassRule("10.*")
      builder.addBypassRule("192.168.*")
    }
    val proxyConfig = builder.build()
    return withContext(Dispatchers.Main) {
      runCatching {
        suspendCancellableCoroutine { cont ->
          ProxyController.getInstance().setProxyOverride(proxyConfig, executor) {
            if (cont.isActive) cont.resume(true)
          }
        }
      }.getOrDefault(false)
    }
  }

  private suspend fun clearWebViewProxy() {
    if (!proxyOverrideSupported) return
    withContext(Dispatchers.Main) {
      runCatching {
        suspendCancellableCoroutine { cont ->
          ProxyController.getInstance().clearProxyOverride(executor) {
            if (cont.isActive) cont.resume(Unit)
          }
        }
      }
    }
  }

  private fun explainProxyFailure(rawError: String): String =
    when {
      rawError.contains("407") ->
        "Proxy requires authentication (HTTP 407). Check your saved proxy username & password."
      rawError.contains("ECONNREFUSED", ignoreCase = true) ->
        "Connection refused by proxy server. Verify the host, port, and protocol."
      rawError.contains("timeout", ignoreCase = true) ->
        "Proxy connection timed out. Server may be offline or blocked by firewall."
      rawError.contains("UnknownHost", ignoreCase = true) ->
        "Cannot resolve proxy hostname. Check the server address and internet connection."
      else -> rawError
    }
}
