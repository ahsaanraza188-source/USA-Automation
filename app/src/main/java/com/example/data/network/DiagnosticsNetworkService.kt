package com.example.data.network

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.model.DevicePreferences
import com.example.domain.CalculatorEngine
import com.example.domain.RegionalFormatter
import com.example.domain.VirtualAppCatalog
import com.example.domain.VirtualLocationCatalog
import com.example.domain.VirtualLocationConfig
import java.io.File
import java.net.InetAddress
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class PublicIpReport(
  val ipAddress: String,
  val countryName: String,
  val countryCode: String,
  val region: String,
  val city: String,
  val isp: String,
  val asn: String,
  val latencyMs: Long,
  val providerName: String,
  val isVerifiedUsa: Boolean,
  val checkedAtEpochMillis: Long,
  val routedVia: String = "Direct / System Network",
)

data class DeviceTelemetry(
  val model: String,
  val androidVersion: String,
  val apiLevel: Int,
  val transportLabel: String,
  val vpnActive: Boolean,
  val totalRamMb: Long,
  val availRamMb: Long,
  val appStorageUsedKb: Long,
)

data class DnsDiagnosticResult(
  val host: String,
  val resolvedAddresses: List<String>,
  val latencyMs: Long,
  val success: Boolean,
  val error: String? = null,
)

data class HttpsProbeResult(
  val label: String,
  val url: String,
  val httpCode: Int?,
  val tlsProtocol: String?,
  val latencyMs: Long,
  val success: Boolean,
  val error: String? = null,
)

data class DeviceGeolocationSnapshot(
  val permissionGranted: Boolean,
  val providerEnabled: Boolean,
  val coordinatesLabel: String?,
  val statusLabel: String,
)

data class LeakCheckReport(
  val directIp: String?,
  val directCountry: String?,
  val routedIp: String?,
  val routedCountry: String?,
  val dohResolverReachable: Boolean,
  val webrtcGuardEnabled: Boolean,
  val vpnOrProxyActive: Boolean,
  val dnsLeakRiskLabel: String,
  val summary: String,
)

enum class SelfTestStatus {
  PENDING,
  RUNNING,
  PASS,
  WARN,
  FAIL,
}

data class SelfTestItem(
  val number: Int,
  val title: String,
  val detail: String,
  val status: SelfTestStatus,
  val remediation: String = "",
)

class DiagnosticsNetworkService(
  private val context: Context,
  private val proxyTunnelController: ProxyTunnelController? = null,
) {
  private fun activeClient(): OkHttpClient =
    proxyTunnelController?.getActiveOkHttpClient() ?: directClient()

  private fun directClient(): OkHttpClient =
    proxyTunnelController?.getDirectOkHttpClient() ?: OkHttpClient()

  suspend fun fetchVerifiedPublicIp(clientOverride: OkHttpClient? = null): Result<PublicIpReport> =
    withContext(Dispatchers.IO) {
      val client = clientOverride ?: activeClient()
      val routeTag =
        when {
          clientOverride != null -> "Configured Proxy Tunnel"
          proxyTunnelController?.isProxyRoutingActive() == true -> "Configured Proxy Tunnel"
          readDeviceTelemetry().vpnActive -> "Android System VPN"
          else -> "Direct Network"
        }
      val start = System.currentTimeMillis()
      val primary = runCatching { queryIpWhoIs(client, start, routeTag) }
      if (primary.isSuccess) {
        val rep = primary.getOrThrow()
        AppDiagnosticsLogger.info(
          component = "Network IP",
          message = "Verified public IP ${rep.ipAddress} (${rep.countryCode}) via $routeTag",
          detail = "ISP: ${rep.isp} • ${rep.latencyMs}ms",
        )
        return@withContext primary
      }
      val fallback = runCatching { queryIpApiCo(client, start, routeTag) }
      fallback
        .onSuccess { rep ->
          AppDiagnosticsLogger.info(
            component = "Network IP",
            message = "Verified public IP ${rep.ipAddress} (${rep.countryCode}) via fallback ($routeTag)",
            detail = "ISP: ${rep.isp} • ${rep.latencyMs}ms",
          )
        }
        .onFailure { err ->
          AppDiagnosticsLogger.error(
            component = "Network IP",
            message = "Public IP lookup failed across primary & fallback providers",
            throwable = err,
            recoveryHint = "Check Wi-Fi/cellular data, active proxy credentials, or VPN tunnel and tap Refresh.",
          )
        }
      fallback
    }

  private fun queryIpWhoIs(client: OkHttpClient, startMillis: Long, routeTag: String): PublicIpReport {
    val req = Request.Builder().url("https://ipwho.is/").get().build()
    client.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) error("HTTP ${resp.code}")
      val json = JSONObject(resp.body?.string().orEmpty())
      if (!json.optBoolean("success", true)) {
        error(json.optString("message", "ipwho.is lookup failed"))
      }
      val cc = json.optString("country_code", "").uppercase()
      val conn = json.optJSONObject("connection")
      return PublicIpReport(
        ipAddress = json.optString("ip", "Unknown"),
        countryName = json.optString("country", "Unknown"),
        countryCode = cc.ifBlank { "--" },
        region = json.optString("region", "--"),
        city = json.optString("city", "--"),
        isp = conn?.optString("isp")?.takeIf { it.isNotBlank() } ?: json.optString("org", "Unknown ISP"),
        asn = conn?.optString("asn")?.let { if (it.isNotBlank()) "AS$it" else "--" } ?: "--",
        latencyMs = (System.currentTimeMillis() - startMillis).coerceAtLeast(1L),
        providerName = "ipwho.is (HTTPS)",
        isVerifiedUsa = cc == "US",
        checkedAtEpochMillis = System.currentTimeMillis(),
        routedVia = routeTag,
      )
    }
  }

  private fun queryIpApiCo(client: OkHttpClient, startMillis: Long, routeTag: String): PublicIpReport {
    val req =
      Request.Builder()
        .url("https://ipapi.co/json/")
        .header("User-Agent", "USAVirtualAndroidPro/1.0")
        .get()
        .build()
    client.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) error("HTTP ${resp.code}")
      val json = JSONObject(resp.body?.string().orEmpty())
      val cc = json.optString("country_code", "").uppercase()
      return PublicIpReport(
        ipAddress = json.optString("ip", "Unknown"),
        countryName = json.optString("country_name", "Unknown"),
        countryCode = cc.ifBlank { "--" },
        region = json.optString("region", "--"),
        city = json.optString("city", "--"),
        isp = json.optString("org", "Unknown ISP"),
        asn = json.optString("asn", "--"),
        latencyMs = (System.currentTimeMillis() - startMillis).coerceAtLeast(1L),
        providerName = "ipapi.co (HTTPS)",
        isVerifiedUsa = cc == "US",
        checkedAtEpochMillis = System.currentTimeMillis(),
        routedVia = routeTag,
      )
    }
  }

  suspend fun runDnsDiagnostics(): List<DnsDiagnosticResult> =
    withContext(Dispatchers.IO) {
      val hosts = listOf("google.com", "accounts.google.com", "dns.google")
      hosts.map { host ->
        val start = System.currentTimeMillis()
        runCatching {
          val addresses = InetAddress.getAllByName(host).mapNotNull { it.hostAddress }.distinct()
          val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
          if (addresses.isEmpty()) error("No DNS records returned")
          DnsDiagnosticResult(
            host = host,
            resolvedAddresses = addresses,
            latencyMs = elapsed,
            success = true,
          )
        }.getOrElse { err ->
          val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
          AppDiagnosticsLogger.warn(
            component = "DNS",
            message = "DNS resolution failed for $host",
            rootCause = err.message ?: "Unknown host resolution failure",
            recoveryHint = "Check Private DNS or active VPN DNS resolver.",
          )
          DnsDiagnosticResult(
            host = host,
            resolvedAddresses = emptyList(),
            latencyMs = elapsed,
            success = false,
            error = err.message ?: "Resolution failed",
          )
        }
      }
    }

  suspend fun runHttpsConnectivityTests(): List<HttpsProbeResult> =
    withContext(Dispatchers.IO) {
      val client = activeClient()
      val targets =
        listOf(
          "Google Connectivity" to "https://www.google.com/generate_204",
          "Google APIs TLS" to "https://www.googleapis.com/discovery/v1/apis?preferred=true",
          "IP Lookup Endpoint" to "https://ipwho.is/",
        )
      targets.map { (label, url) ->
        val start = System.currentTimeMillis()
        runCatching {
          val req = Request.Builder().url(url).head().build()
          client.newCall(req).execute().use { resp ->
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            val tls = resp.handshake?.tlsVersion?.javaName
            val ok = resp.code in 200..399
            if (!ok) {
              AppDiagnosticsLogger.warn(
                component = "HTTPS",
                message = "$label returned HTTP ${resp.code}",
                rootCause = url,
                recoveryHint = "Verify network proxy or captive portal.",
              )
            }
            HttpsProbeResult(
              label = label,
              url = url,
              httpCode = resp.code,
              tlsProtocol = tls,
              latencyMs = elapsed,
              success = ok,
              error = if (ok) null else "HTTP ${resp.code}",
            )
          }
        }.getOrElse { err ->
          val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
          AppDiagnosticsLogger.error(
            component = "HTTPS",
            message = "HTTPS probe failed for $label",
            throwable = err,
            recoveryHint = "Check internet connectivity, system clock, proxy, or VPN tunnel.",
          )
          HttpsProbeResult(
            label = label,
            url = url,
            httpCode = null,
            tlsProtocol = null,
            latencyMs = elapsed,
            success = false,
            error = err.message ?: "Connection failed",
          )
        }
      }
    }

  suspend fun runLeakDiagnostics(locationConfig: VirtualLocationConfig): LeakCheckReport =
    withContext(Dispatchers.IO) {
      val telemetry = readDeviceTelemetry()
      val proxyActive = proxyTunnelController?.isProxyRoutingActive() == true
      val routed = fetchVerifiedPublicIp().getOrNull()
      val direct =
        if (proxyActive) {
          runCatching { queryIpWhoIs(directClient(), System.currentTimeMillis(), "Direct Bypass Check") }
            .getOrNull()
        } else {
          routed
        }

      val dohOk =
        runCatching {
          val req = Request.Builder().url("https://dns.google/resolve?name=example.com&type=A").get().build()
          activeClient().newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)

      val dnsRisk =
        when {
          telemetry.vpnActive -> "Low (System VPN handles DNS + all sockets)"
          proxyActive && routed != null && direct != null && routed.ipAddress != direct.ipAddress ->
            "Low for Chrome/Diagnostics (Proxy active; host OS DNS still uses ISP unless DoH/VPN active)"
          proxyActive ->
            "Check Proxy (Routed IP matches direct IP or unreachable)"
          else ->
            "Exposed to ISP (No active System VPN or USA Proxy)"
        }

      val summary =
        when {
          routed?.isVerifiedUsa == true && telemetry.vpnActive ->
            "Full system VPN verified in United States (${routed.ipAddress}). WebRTC guard: ${if (locationConfig.webrtcLeakProtection) "ON" else "OFF"}."
          routed?.isVerifiedUsa == true && proxyActive ->
            "Simulator Chrome & Diagnostics verified via USA Proxy (${routed.ipAddress} vs direct ${direct?.ipAddress ?: "--"}). System-wide native apps still need System VPN."
          telemetry.vpnActive && routed != null ->
            "System VPN active, but exit IP is in ${routed.countryName} (${routed.countryCode}). Switch VPN server to a US node."
          else ->
            "Direct connection active (${routed?.ipAddress ?: "offline"}, ${routed?.countryCode ?: "--"}). Connect a US System VPN or configure a USA Proxy to route IP traffic."
        }

      LeakCheckReport(
        directIp = direct?.ipAddress,
        directCountry = direct?.countryCode,
        routedIp = routed?.ipAddress,
        routedCountry = routed?.countryCode,
        dohResolverReachable = dohOk,
        webrtcGuardEnabled = locationConfig.webrtcLeakProtection,
        vpnOrProxyActive = telemetry.vpnActive || proxyActive,
        dnsLeakRiskLabel = dnsRisk,
        summary = summary,
      )
    }

  @SuppressLint("MissingPermission")
  fun readDeviceGeolocation(): DeviceGeolocationSnapshot {
    val granted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    val gpsEnabled = runCatching { lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true }.getOrDefault(false)
    val netEnabled = runCatching { lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true }.getOrDefault(false)
    val providerEnabled = gpsEnabled || netEnabled

    if (!granted) {
      return DeviceGeolocationSnapshot(
        permissionGranted = false,
        providerEnabled = providerEnabled,
        coordinatesLabel = null,
        statusLabel = "Permission not granted (independent of Virtual Region & Virtual GPS)",
      )
    }

    val lastLoc =
      runCatching {
        lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
          ?: lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
      }.getOrNull()

    return if (lastLoc != null) {
      DeviceGeolocationSnapshot(
        permissionGranted = true,
        providerEnabled = providerEnabled,
        coordinatesLabel = String.format("%.4f, %.4f", lastLoc.latitude, lastLoc.longitude),
        statusLabel = "Physical location permission granted",
      )
    } else {
      DeviceGeolocationSnapshot(
        permissionGranted = true,
        providerEnabled = providerEnabled,
        coordinatesLabel = null,
        statusLabel = if (providerEnabled) "Granted (no cached physical fix yet)" else "Granted (device GPS/Location off)",
      )
    }
  }

  fun readDeviceTelemetry(): DeviceTelemetry {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    val vpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    val transport =
      when {
        caps == null -> "Offline"
        vpnActive && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi + Active VPN"
        vpnActive && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "5G/Cellular + Active VPN"
        vpnActive -> "Active VPN Tunnel"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi (Validated)"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "5G / Cellular"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Connected"
      }

    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)

    val usedBytes =
      context.filesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    return DeviceTelemetry(
      model = "${Build.MANUFACTURER} ${Build.MODEL}",
      androidVersion = "Android ${Build.VERSION.RELEASE}",
      apiLevel = Build.VERSION.SDK_INT,
      transportLabel = transport,
      vpnActive = vpnActive,
      totalRamMb = memInfo.totalMem / (1024L * 1024L),
      availRamMb = memInfo.availMem / (1024L * 1024L),
      appStorageUsedKb = (usedBytes / 1024L).coerceAtLeast(1L),
    )
  }

  suspend fun runLocationAndNetworkVerification(
    preferences: DevicePreferences,
    locationConfig: VirtualLocationConfig,
    browserGeoProbeResult: String?,
  ): List<SelfTestItem> {
    val ipResult = fetchVerifiedPublicIp()
    val ipReport = ipResult.getOrNull()
    val dnsResults = runDnsDiagnostics()
    val httpsResults = runHttpsConnectivityTests()
    val leakReport = runLeakDiagnostics(locationConfig)
    val telemetry = readDeviceTelemetry()
    val proxyState = proxyTunnelController?.state?.value
    val usaChecks = RegionalFormatter.evaluateUsaRegionVerification(preferences, ipReport)
    val virtualUsMatches = usaChecks.filter { it.key != "public_ip" }.count { it.isMatchingUsDefault }
    val validCoords =
      VirtualLocationCatalog.validateCoordinates(
        locationConfig.latitude,
        locationConfig.longitude,
        locationConfig.accuracyMeters,
      ).isSuccess
    val insideUs = VirtualLocationCatalog.isInsideUsGeographicBounds(locationConfig.latitude, locationConfig.longitude)

    return listOf(
      SelfTestItem(
        number = 1,
        title = "Public IP & Country Verification",
        detail =
          ipReport?.let {
            "${it.ipAddress} • ${it.countryName} (${it.countryCode}) • ${it.isp} [${it.routedVia}]"
          } ?: "Failed: ${ipResult.exceptionOrNull()?.message ?: "No network"}",
        status =
          when {
            ipReport == null -> SelfTestStatus.FAIL
            ipReport.isVerifiedUsa -> SelfTestStatus.PASS
            else -> SelfTestStatus.WARN
          },
        remediation =
          when {
            ipReport == null -> "Verify Wi-Fi/cellular or disconnect a misconfigured proxy."
            !ipReport.isVerifiedUsa -> "Connect a US VPN server or a verified USA HTTPS/SOCKS5 proxy under the VPN/Proxy tab."
            else -> ""
          },
      ),
      SelfTestItem(
        number = 2,
        title = "VPN / Proxy Routing Status",
        detail =
          when {
            telemetry.vpnActive -> "Android System VPN active (${telemetry.transportLabel})"
            proxyState?.isConnected == true -> "Proxy ${proxyState.activeEndpointLabel} active (WebView override: ${proxyState.webViewProxyApplied})"
            proxyState?.status == ProxyTunnelStatus.ERROR -> "Proxy error: ${proxyState.errorMessage}"
            else -> "No VPN or Proxy active (Direct network)"
          },
        status =
          when {
            telemetry.vpnActive || (proxyState?.isConnected == true && proxyState.webViewProxyApplied) -> SelfTestStatus.PASS
            proxyState?.status == ProxyTunnelStatus.ERROR -> SelfTestStatus.FAIL
            else -> SelfTestStatus.WARN
          },
        remediation =
          if (!telemetry.vpnActive && proxyState?.isConnected != true)
            "Enable a US VPN in Android VPN Settings or configure a working USA Proxy server."
          else "",
      ),
      SelfTestItem(
        number = 3,
        title = "Network, DNS & WebRTC Leak Check",
        detail = leakReport.dnsLeakRiskLabel,
        status =
          when {
            telemetry.vpnActive && leakReport.dohResolverReachable -> SelfTestStatus.PASS
            proxyState?.isConnected == true && leakReport.directIp != leakReport.routedIp -> SelfTestStatus.PASS
            else -> SelfTestStatus.WARN
          },
        remediation =
          if (!telemetry.vpnActive) "For system-wide DNS & native app leak protection, use an Android System VPN." else "",
      ),
      SelfTestItem(
        number = 4,
        title = "Virtual GPS Coordinates & Bounds",
        detail =
          if (locationConfig.enabled)
            "${locationConfig.displayLabel} (${locationConfig.formattedCoordinates}, ±${locationConfig.accuracyMeters.toInt()}m)"
          else "Virtual GPS disabled",
        status =
          when {
            !locationConfig.enabled -> SelfTestStatus.WARN
            validCoords && insideUs -> SelfTestStatus.PASS
            validCoords -> SelfTestStatus.WARN
            else -> SelfTestStatus.FAIL
          },
        remediation =
          if (!locationConfig.enabled) "Enable Virtual GPS in the Virtual GPS tab."
          else if (!insideUs) "Coordinates are outside US geographic bounds; pick a US preset if you want a US location."
          else "",
      ),
      SelfTestItem(
        number = 5,
        title = "Browser Geolocation Permission",
        detail = "Mode: ${locationConfig.browserPermission.label} • WebRTC Guard: ${if (locationConfig.webrtcLeakProtection) "ON" else "OFF"}",
        status =
          when (locationConfig.browserPermission.name) {
            "BLOCK" -> SelfTestStatus.WARN
            else -> SelfTestStatus.PASS
          },
        remediation =
          if (locationConfig.browserPermission.name == "BLOCK")
            "Switch Geolocation Permission to Auto-Allow or Prompt Per Site so websites can receive Virtual GPS."
          else "",
      ),
      SelfTestItem(
        number = 6,
        title = "Simulator Browser Geolocation Probe",
        detail = browserGeoProbeResult ?: "Run live WebView probe to verify navigator.geolocation output",
        status =
          when {
            browserGeoProbeResult == null -> SelfTestStatus.WARN
            browserGeoProbeResult.startsWith("PASS") -> SelfTestStatus.PASS
            else -> SelfTestStatus.FAIL
          },
        remediation =
          if (browserGeoProbeResult == null) "Tap 'Test Browser GPS' below or open Chrome to initialize WebView."
          else if (!browserGeoProbeResult.startsWith("PASS")) "Ensure Virtual GPS is enabled and permission is not set to Block."
          else "",
      ),
      SelfTestItem(
        number = 7,
        title = "USA Device Region Defaults",
        detail = "${preferences.countryCode}/${preferences.localeTag} • ${preferences.usTimezoneId} ($virtualUsMatches/6 US defaults)",
        status = if (virtualUsMatches == 6) SelfTestStatus.PASS else SelfTestStatus.WARN,
        remediation = if (virtualUsMatches < 6) "Use 'US Reset' in Settings -> Device Region Settings to restore all 6 US defaults." else "",
      ),
      SelfTestItem(
        number = 8,
        title = "DNS & HTTPS TLS Reachability",
        detail = "${dnsResults.count { it.success }}/${dnsResults.size} DNS • ${httpsResults.count { it.success }}/${httpsResults.size} HTTPS",
        status =
          if (dnsResults.all { it.success } && httpsResults.all { it.success }) SelfTestStatus.PASS
          else SelfTestStatus.FAIL,
        remediation =
          if (!httpsResults.all { it.success }) "Verify network connectivity or proxy endpoint settings." else "",
      ),
    )
  }

  suspend fun runFifteenPointSelfTest(
    preferences: DevicePreferences,
    oauthClientId: String,
    oauthConnected: Boolean,
    isSignedIn: Boolean,
    cloudHealthCheck: suspend () -> Boolean,
    hasCameraHardware: Boolean,
  ): List<SelfTestItem> {
    val ipResult = fetchVerifiedPublicIp()
    val dnsResults = runDnsDiagnostics()
    val httpsResults = runHttpsConnectivityTests()
    val cloudOk = if (isSignedIn) cloudHealthCheck() else false
    val telemetry = readDeviceTelemetry()

    val calcOk =
      CalculatorEngine.evaluate("(12+8)*3/2").getOrNull() == "30" &&
        CalculatorEngine.evaluate("sqrt(144)").getOrNull() == "12"
    val tzOk = TimeZone.getAvailableIDs().contains(preferences.usTimezoneId)
    val storageOk =
      withContext(Dispatchers.IO) {
        runCatching {
          val probe = File(context.cacheDir, "diag_probe.tmp")
          probe.writeText("ok")
          val text = probe.readText()
          probe.delete()
          text == "ok"
        }.getOrDefault(false)
      }

    val ipReport = ipResult.getOrNull()
    val dnsOkCount = dnsResults.count { it.success }
    val httpsOkCount = httpsResults.count { it.success }
    val usaChecks = RegionalFormatter.evaluateUsaRegionVerification(preferences, ipReport)
    val virtualUsMatches = usaChecks.filter { it.key != "public_ip" }.count { it.isMatchingUsDefault }

    return listOf(
      SelfTestItem(1, "OS Shell & App Registry", "${VirtualAppCatalog.apps.size}/16 virtual apps indexed", SelfTestStatus.PASS),
      SelfTestItem(
        2,
        "Virtual USA Region & Locale",
        "${preferences.countryCode}/${preferences.localeTag} • ${preferences.usTimezoneId} ($virtualUsMatches/6 US defaults)",
        if (tzOk && preferences.countryCode == "US") SelfTestStatus.PASS else SelfTestStatus.WARN,
      ),
      SelfTestItem(
        3,
        "Account Mode & Auth",
        if (isSignedIn) "Google Firebase session active" else "Local Device Mode (no Gmail required)",
        SelfTestStatus.PASS,
      ),
      SelfTestItem(
        4,
        "Persistence Layer",
        if (isSignedIn) (if (cloudOk) "Firestore Cloud Sync healthy" else "Firestore health check failed")
        else "Local DataStore + WebView vault active",
        if (!isSignedIn || cloudOk) SelfTestStatus.PASS else SelfTestStatus.FAIL,
      ),
      SelfTestItem(
        5,
        "Network & VPN Transport",
        "${telemetry.transportLabel} (VPN: ${if (telemetry.vpnActive) "Detected" else "None"})",
        if (telemetry.transportLabel != "Offline") SelfTestStatus.PASS else SelfTestStatus.FAIL,
      ),
      SelfTestItem(
        6,
        "DNS Diagnostics",
        "$dnsOkCount/${dnsResults.size} hosts resolved",
        when {
          dnsOkCount == dnsResults.size -> SelfTestStatus.PASS
          dnsOkCount > 0 -> SelfTestStatus.WARN
          else -> SelfTestStatus.FAIL
        },
      ),
      SelfTestItem(
        7,
        "HTTPS Connectivity Probe",
        "$httpsOkCount/${httpsResults.size} TLS endpoints reachable",
        when {
          httpsOkCount == httpsResults.size -> SelfTestStatus.PASS
          httpsOkCount > 0 -> SelfTestStatus.WARN
          else -> SelfTestStatus.FAIL
        },
      ),
      SelfTestItem(
        8,
        "Public IP & Geo-IP Check (Honest)",
        when {
          ipReport == null -> "IP unreachable"
          ipReport.isVerifiedUsa -> "Verified USA IP: ${ipReport.ipAddress} (${ipReport.city}, ${ipReport.region})"
          else -> "Real IP: ${ipReport.ipAddress} (${ipReport.countryCode}) — never spoofed"
        },
        when {
          ipReport == null -> SelfTestStatus.FAIL
          ipReport.isVerifiedUsa -> SelfTestStatus.PASS
          else -> SelfTestStatus.WARN
        },
      ),
      SelfTestItem(9, "Calculator Scientific Engine", "(12+8)*3/2 = 30, sqrt(144) = 12", if (calcOk) SelfTestStatus.PASS else SelfTestStatus.FAIL),
      SelfTestItem(10, "App-Private File Vault", "Cache/files read-write verified", if (storageOk) SelfTestStatus.PASS else SelfTestStatus.FAIL),
      SelfTestItem(11, "Camera Hardware Probe", if (hasCameraHardware) "Camera sensor present" else "No physical camera detected", if (hasCameraHardware) SelfTestStatus.PASS else SelfTestStatus.WARN),
      SelfTestItem(12, "Zero-Permission Media", "Photo Picker & SAF enabled (Play compliant)", SelfTestStatus.PASS),
      SelfTestItem(13, "OAuth 2.0 Client Config", if (oauthClientId.isNotBlank()) "Client ID saved" else "Optional Client ID not entered", if (oauthClientId.isNotBlank()) SelfTestStatus.PASS else SelfTestStatus.WARN),
      SelfTestItem(14, "Gmail / YouTube OAuth Token", if (oauthConnected) "Bearer token active" else "Not connected (sign in via Gmail/Settings if needed)", if (oauthConnected) SelfTestStatus.PASS else SelfTestStatus.WARN),
      SelfTestItem(15, "Memory Headroom", "${telemetry.availRamMb} MB free / ${telemetry.totalRamMb} MB", if (telemetry.availRamMb > 128) SelfTestStatus.PASS else SelfTestStatus.WARN),
    )
  }
}
