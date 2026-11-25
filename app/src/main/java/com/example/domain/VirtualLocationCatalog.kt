package com.example.domain

enum class VirtualLocationMode(val label: String) {
  VIRTUAL_GPS("Virtual USA GPS"),
  PHYSICAL_DEVICE("Physical Device GPS"),
  DISABLED("Disabled"),
}

enum class BrowserGeoPermissionMode(val label: String) {
  ALLOW("Auto-Allow"),
  ASK("Prompt Per Site"),
  BLOCK("Block All"),
}

enum class ProxyProtocol(val label: String, val scheme: String, val defaultPort: Int) {
  HTTPS("HTTPS Proxy", "https", 443),
  HTTP("HTTP Proxy", "http", 8080),
  SOCKS5("SOCKS5 Proxy", "socks5", 1080),
}

enum class VpnConnectionMode(val label: String) {
  DIRECT("Direct Network (No Proxy)"),
  SYSTEM_VPN("Android System USA VPN"),
  PROXY_TUNNEL("Configured USA Proxy Tunnel"),
}

data class VirtualLocationPreset(
  val id: String,
  val city: String,
  val state: String,
  val stateCode: String,
  val country: String = "United States",
  val countryCode: String = "US",
  val latitude: Double,
  val longitude: Double,
  val timezoneId: String,
  val isCustom: Boolean = false,
) {
  val displayName: String
    get() = "$city, $state"

  val shortLabel: String
    get() = "$city, $stateCode"
}

data class VirtualLocationConfig(
  val enabled: Boolean = true,
  val mode: VirtualLocationMode = VirtualLocationMode.VIRTUAL_GPS,
  val selectedPresetId: String = "nyc",
  val city: String = "New York",
  val state: String = "New York",
  val stateCode: String = "NY",
  val country: String = "United States",
  val countryCode: String = "US",
  val latitude: Double = 40.7128,
  val longitude: Double = -74.0060,
  val accuracyMeters: Float = 18.0f,
  val browserPermission: BrowserGeoPermissionMode = BrowserGeoPermissionMode.ALLOW,
  val webrtcLeakProtection: Boolean = true,
) {
  val formattedCoordinates: String
    get() = String.format(java.util.Locale.US, "%.4f, %.4f", latitude, longitude)

  val displayLabel: String
    get() = "$city, $state ($countryCode)"
}

data class UsaGatewayRegion(
  val id: String,
  val city: String,
  val state: String,
  val stateCode: String,
  val timezoneId: String,
  val recommendedVpnNote: String,
)

data class VpnProxyConfig(
  val mode: VpnConnectionMode = VpnConnectionMode.SYSTEM_VPN,
  val selectedGatewayId: String = "us_east_ny",
  val protocol: ProxyProtocol = ProxyProtocol.HTTP,
  val host: String = "",
  val port: Int = 8080,
  val bypassLocalAddresses: Boolean = true,
  val hasSavedCredentials: Boolean = false,
  val providerLabel: String = "User-Authorized Server",
)

object VirtualLocationCatalog {

  val usStateNames: List<String> =
    listOf(
      "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut",
      "Delaware", "District of Columbia", "Florida", "Georgia", "Hawaii", "Idaho", "Illinois",
      "Indiana", "Iowa", "Kansas", "Kentucky", "Louisiana", "Maine", "Maryland", "Massachusetts",
      "Michigan", "Minnesota", "Mississippi", "Missouri", "Montana", "Nebraska", "Nevada",
      "New Hampshire", "New Jersey", "New Mexico", "New York", "North Carolina", "North Dakota",
      "Ohio", "Oklahoma", "Oregon", "Pennsylvania", "Rhode Island", "South Carolina",
      "South Dakota", "Tennessee", "Texas", "Utah", "Vermont", "Virginia", "Washington",
      "West Virginia", "Wisconsin", "Wyoming",
    )

  val presets: List<VirtualLocationPreset> =
    listOf(
      VirtualLocationPreset(
        id = "nyc",
        city = "New York",
        state = "New York",
        stateCode = "NY",
        latitude = 40.7128,
        longitude = -74.0060,
        timezoneId = "America/New_York",
      ),
      VirtualLocationPreset(
        id = "la",
        city = "Los Angeles",
        state = "California",
        stateCode = "CA",
        latitude = 34.0522,
        longitude = -118.2437,
        timezoneId = "America/Los_Angeles",
      ),
      VirtualLocationPreset(
        id = "chi",
        city = "Chicago",
        state = "Illinois",
        stateCode = "IL",
        latitude = 41.8781,
        longitude = -87.6298,
        timezoneId = "America/Chicago",
      ),
      VirtualLocationPreset(
        id = "mia",
        city = "Miami",
        state = "Florida",
        stateCode = "FL",
        latitude = 25.7617,
        longitude = -80.1918,
        timezoneId = "America/New_York",
      ),
      VirtualLocationPreset(
        id = "dal",
        city = "Dallas",
        state = "Texas",
        stateCode = "TX",
        latitude = 32.7767,
        longitude = -96.7970,
        timezoneId = "America/Chicago",
      ),
      VirtualLocationPreset(
        id = "hou",
        city = "Houston",
        state = "Texas",
        stateCode = "TX",
        latitude = 29.7604,
        longitude = -95.3698,
        timezoneId = "America/Chicago",
      ),
      VirtualLocationPreset(
        id = "sea",
        city = "Seattle",
        state = "Washington",
        stateCode = "WA",
        latitude = 47.6062,
        longitude = -122.3321,
        timezoneId = "America/Los_Angeles",
      ),
      VirtualLocationPreset(
        id = "sf",
        city = "San Francisco",
        state = "California",
        stateCode = "CA",
        latitude = 37.7749,
        longitude = -122.4194,
        timezoneId = "America/Los_Angeles",
      ),
      VirtualLocationPreset(
        id = "las",
        city = "Las Vegas",
        state = "Nevada",
        stateCode = "NV",
        latitude = 36.1699,
        longitude = -115.1398,
        timezoneId = "America/Los_Angeles",
      ),
      VirtualLocationPreset(
        id = "dc",
        city = "Washington",
        state = "District of Columbia",
        stateCode = "DC",
        latitude = 38.9072,
        longitude = -77.0369,
        timezoneId = "America/New_York",
      ),
    )

  val usaGatewayRegions: List<UsaGatewayRegion> =
    listOf(
      UsaGatewayRegion("us_east_ny", "New York", "New York", "NY", "America/New_York", "US East (NY / NJ)"),
      UsaGatewayRegion("us_east_va", "Ashburn", "Virginia", "VA", "America/New_York", "US East (Virginia)"),
      UsaGatewayRegion("us_central_chi", "Chicago", "Illinois", "IL", "America/Chicago", "US Central (Chicago)"),
      UsaGatewayRegion("us_south_dal", "Dallas", "Texas", "TX", "America/Chicago", "US South (Dallas)"),
      UsaGatewayRegion("us_east_mia", "Miami", "Florida", "FL", "America/New_York", "US Southeast (Miami)"),
      UsaGatewayRegion("us_west_la", "Los Angeles", "California", "CA", "America/Los_Angeles", "US West (Los Angeles)"),
      UsaGatewayRegion("us_west_sea", "Seattle", "Washington", "WA", "America/Los_Angeles", "US Northwest (Seattle)"),
    )

  fun findGateway(id: String): UsaGatewayRegion =
    usaGatewayRegions.firstOrNull { it.id == id } ?: usaGatewayRegions.first()

  fun validateCoordinates(lat: Double?, lon: Double?, accuracyMeters: Float? = 18f): Result<Pair<Double, Double>> {
    if (lat == null || lon == null || lat.isNaN() || lon.isNaN()) {
      return Result.failure(IllegalArgumentException("Latitude and longitude must be valid numbers."))
    }
    if (lat < -90.0 || lat > 90.0) {
      return Result.failure(IllegalArgumentException("Latitude must be between -90.0 and 90.0."))
    }
    if (lon < -180.0 || lon > 180.0) {
      return Result.failure(IllegalArgumentException("Longitude must be between -180.0 and 180.0."))
    }
    if (accuracyMeters != null && (accuracyMeters < 1f || accuracyMeters > 5000f)) {
      return Result.failure(IllegalArgumentException("Accuracy must be between 1 and 5000 meters."))
    }
    return Result.success(lat to lon)
  }

  fun isInsideUsGeographicBounds(lat: Double, lon: Double): Boolean {
    val contiguous = lat in 24.3963..49.3844 && lon in -124.8489..-66.8854
    val alaska = lat in 51.2..71.5 && lon in -179.2..-129.9
    val hawaii = lat in 18.8..22.3 && lon in -160.3..-154.7
    return contiguous || alaska || hawaii
  }

  fun validateProxyEndpoint(host: String, portStr: String): Result<Pair<String, Int>> {
    val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").removePrefix("socks5://").substringBefore("/")
    if (cleanHost.isBlank()) {
      return Result.failure(IllegalArgumentException("Enter a valid USA proxy server hostname or IP address."))
    }
    if (cleanHost.contains(" ") || !cleanHost.matches(Regex("^[A-Za-z0-9._:-]+$"))) {
      return Result.failure(IllegalArgumentException("Proxy host contains invalid characters."))
    }
    val port = portStr.trim().toIntOrNull()
      ?: return Result.failure(IllegalArgumentException("Proxy port must be a valid number (1–65535)."))
    if (port !in 1..65535) {
      return Result.failure(IllegalArgumentException("Proxy port must be between 1 and 65535."))
    }
    return Result.success(cleanHost to port)
  }
}
