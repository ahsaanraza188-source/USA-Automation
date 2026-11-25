package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.BrowserGeoPermissionMode
import com.example.domain.ProxyProtocol
import com.example.domain.VirtualLocationConfig
import com.example.domain.VirtualLocationMode
import com.example.domain.VirtualLocationPreset
import com.example.domain.VpnConnectionMode
import com.example.domain.VpnProxyConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.networkLocationDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "virtual_location_network_store"
)

data class ProxyCredentials(
  val username: String,
  val password: String,
)

/**
 * Persists Virtual GPS config, custom US locations, and VPN/Proxy settings in DataStore,
 * while keeping sensitive proxy credentials encrypted with Android KeyStore (AES-256-GCM)
 * in app-private storage (never synced to cloud or logged).
 */
class SecureNetworkConfigStore(context: Context) {
  private val appContext = context.applicationContext
  private val dataStore = appContext.networkLocationDataStore
  private val secretPrefs =
    appContext.getSharedPreferences("secure_proxy_vault", Context.MODE_PRIVATE)

  val locationConfigFlow: Flow<VirtualLocationConfig> =
    dataStore.data.map { prefs -> parseLocationConfig(prefs[KEY_VIRTUAL_LOCATION]) }

  val customLocationsFlow: Flow<List<VirtualLocationPreset>> =
    dataStore.data.map { prefs -> parseCustomLocations(prefs[KEY_CUSTOM_LOCATIONS]) }

  val vpnProxyConfigFlow: Flow<VpnProxyConfig> =
    dataStore.data.map { prefs ->
      parseProxyConfig(prefs[KEY_PROXY_CONFIG]).copy(hasSavedCredentials = hasProxyCredentials())
    }

  suspend fun saveLocationConfig(config: VirtualLocationConfig) {
    dataStore.edit { prefs ->
      prefs[KEY_VIRTUAL_LOCATION] = serializeLocationConfig(config)
    }
  }

  suspend fun addCustomLocation(preset: VirtualLocationPreset) {
    dataStore.edit { prefs ->
      val current = parseCustomLocations(prefs[KEY_CUSTOM_LOCATIONS]).toMutableList()
      current.removeAll { it.id == preset.id }
      current.add(0, preset.copy(isCustom = true))
      prefs[KEY_CUSTOM_LOCATIONS] = serializeCustomLocations(current.take(25))
    }
  }

  suspend fun deleteCustomLocation(presetId: String) {
    dataStore.edit { prefs ->
      val updated = parseCustomLocations(prefs[KEY_CUSTOM_LOCATIONS]).filterNot { it.id == presetId }
      prefs[KEY_CUSTOM_LOCATIONS] = serializeCustomLocations(updated)
    }
  }

  suspend fun saveProxyConfig(
    config: VpnProxyConfig,
    username: String? = null,
    password: String? = null,
    clearExistingCredentials: Boolean = false,
  ) {
    if (clearExistingCredentials) {
      clearProxyCredentials()
    } else if (!username.isNullOrBlank() || !password.isNullOrBlank()) {
      saveEncryptedCredentials(username.orEmpty().trim(), password.orEmpty())
    }
    val hasCreds = hasProxyCredentials()
    dataStore.edit { prefs ->
      prefs[KEY_PROXY_CONFIG] = serializeProxyConfig(config.copy(hasSavedCredentials = hasCreds))
    }
  }

  fun readProxyCredentials(): ProxyCredentials? {
    val encUser = secretPrefs.getString(PREF_ENC_USER, null) ?: return null
    val encPass = secretPrefs.getString(PREF_ENC_PASS, null) ?: ""
    val user = decrypt(encUser).orEmpty()
    val pass = if (encPass.isNotBlank()) decrypt(encPass).orEmpty() else ""
    if (user.isBlank() && pass.isBlank()) return null
    return ProxyCredentials(user, pass)
  }

  fun hasProxyCredentials(): Boolean =
    !secretPrefs.getString(PREF_ENC_USER, null).isNullOrBlank()

  fun clearProxyCredentials() {
    secretPrefs.edit().remove(PREF_ENC_USER).remove(PREF_ENC_PASS).apply()
  }

  private fun saveEncryptedCredentials(username: String, password: String) {
    val encUser = encrypt(username) ?: return
    val encPass = if (password.isNotEmpty()) encrypt(password).orEmpty() else ""
    secretPrefs.edit()
      .putString(PREF_ENC_USER, encUser)
      .putString(PREF_ENC_PASS, encPass)
      .apply()
  }

  private fun getOrCreateAesKey(): SecretKey? =
    runCatching {
      val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
      val existing = ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
      if (existing != null) return@runCatching existing

      val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
      keyGen.init(
        KeyGenParameterSpec.Builder(
          KEYSTORE_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(256)
          .build()
      )
      keyGen.generateKey()
    }.getOrNull()

  private fun encrypt(plainText: String): String? =
    runCatching {
      val key = getOrCreateAesKey() ?: return@runCatching null
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, key)
      val iv = cipher.iv
      val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
      val combined = ByteArray(iv.size + cipherBytes.size)
      System.arraycopy(iv, 0, combined, 0, iv.size)
      System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
      Base64.encodeToString(combined, Base64.NO_WRAP)
    }.getOrNull()

  private fun decrypt(encoded: String): String? =
    runCatching {
      val combined = Base64.decode(encoded, Base64.NO_WRAP)
      if (combined.size <= 12) return@runCatching null
      val key = getOrCreateAesKey() ?: return@runCatching null
      val iv = combined.copyOfRange(0, 12)
      val cipherBytes = combined.copyOfRange(12, combined.size)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
      String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }.getOrNull()

  private fun parseLocationConfig(raw: String?): VirtualLocationConfig {
    if (raw.isNullOrBlank()) return VirtualLocationConfig()
    return runCatching {
      val o = JSONObject(raw)
      VirtualLocationConfig(
        enabled = o.optBoolean("enabled", true),
        mode =
          runCatching { VirtualLocationMode.valueOf(o.optString("mode", "VIRTUAL_GPS")) }
            .getOrDefault(VirtualLocationMode.VIRTUAL_GPS),
        selectedPresetId = o.optString("selectedPresetId", "nyc").ifBlank { "nyc" },
        city = o.optString("city", "New York").ifBlank { "New York" },
        state = o.optString("state", "New York").ifBlank { "New York" },
        stateCode = o.optString("stateCode", "NY").ifBlank { "NY" },
        country = o.optString("country", "United States").ifBlank { "United States" },
        countryCode = o.optString("countryCode", "US").ifBlank { "US" },
        latitude = o.optDouble("latitude", 40.7128),
        longitude = o.optDouble("longitude", -74.0060),
        accuracyMeters = o.optDouble("accuracyMeters", 18.0).toFloat(),
        browserPermission =
          runCatching {
            BrowserGeoPermissionMode.valueOf(o.optString("browserPermission", "ALLOW"))
          }.getOrDefault(BrowserGeoPermissionMode.ALLOW),
        webrtcLeakProtection = o.optBoolean("webrtcLeakProtection", true),
      )
    }.getOrDefault(VirtualLocationConfig())
  }

  private fun serializeLocationConfig(c: VirtualLocationConfig): String =
    JSONObject().apply {
      put("enabled", c.enabled)
      put("mode", c.mode.name)
      put("selectedPresetId", c.selectedPresetId)
      put("city", c.city)
      put("state", c.state)
      put("stateCode", c.stateCode)
      put("country", c.country)
      put("countryCode", c.countryCode)
      put("latitude", c.latitude)
      put("longitude", c.longitude)
      put("accuracyMeters", c.accuracyMeters.toDouble())
      put("browserPermission", c.browserPermission.name)
      put("webrtcLeakProtection", c.webrtcLeakProtection)
    }.toString()

  private fun parseCustomLocations(raw: String?): List<VirtualLocationPreset> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
      val arr = JSONArray(raw)
      List(arr.length()) { idx ->
        val o = arr.getJSONObject(idx)
        VirtualLocationPreset(
          id = o.optString("id"),
          city = o.optString("city"),
          state = o.optString("state"),
          stateCode = o.optString("stateCode", "US"),
          country = o.optString("country", "United States"),
          countryCode = o.optString("countryCode", "US"),
          latitude = o.optDouble("latitude", 40.7128),
          longitude = o.optDouble("longitude", -74.0060),
          timezoneId = o.optString("timezoneId", "America/New_York"),
          isCustom = true,
        )
      }.filter { it.id.isNotBlank() && it.city.isNotBlank() }
    }.getOrDefault(emptyList())
  }

  private fun serializeCustomLocations(list: List<VirtualLocationPreset>): String =
    JSONArray().apply {
      list.forEach { p ->
        put(
          JSONObject().apply {
            put("id", p.id)
            put("city", p.city)
            put("state", p.state)
            put("stateCode", p.stateCode)
            put("country", p.country)
            put("countryCode", p.countryCode)
            put("latitude", p.latitude)
            put("longitude", p.longitude)
            put("timezoneId", p.timezoneId)
          }
        )
      }
    }.toString()

  private fun parseProxyConfig(raw: String?): VpnProxyConfig {
    if (raw.isNullOrBlank()) return VpnProxyConfig()
    return runCatching {
      val o = JSONObject(raw)
      VpnProxyConfig(
        mode =
          runCatching { VpnConnectionMode.valueOf(o.optString("mode", "SYSTEM_VPN")) }
            .getOrDefault(VpnConnectionMode.SYSTEM_VPN),
        selectedGatewayId = o.optString("selectedGatewayId", "us_east_ny"),
        protocol =
          runCatching { ProxyProtocol.valueOf(o.optString("protocol", "HTTP")) }
            .getOrDefault(ProxyProtocol.HTTP),
        host = o.optString("host", ""),
        port = o.optInt("port", 8080),
        bypassLocalAddresses = o.optBoolean("bypassLocalAddresses", true),
        providerLabel = o.optString("providerLabel", "User-Authorized Server"),
      )
    }.getOrDefault(VpnProxyConfig())
  }

  private fun serializeProxyConfig(p: VpnProxyConfig): String =
    JSONObject().apply {
      put("mode", p.mode.name)
      put("selectedGatewayId", p.selectedGatewayId)
      put("protocol", p.protocol.name)
      put("host", p.host)
      put("port", p.port)
      put("bypassLocalAddresses", p.bypassLocalAddresses)
      put("providerLabel", p.providerLabel)
    }.toString()

  private companion object {
    const val KEYSTORE_ALIAS = "usa_virtual_os_proxy_aes_key"
    const val PREF_ENC_USER = "enc_proxy_user"
    const val PREF_ENC_PASS = "enc_proxy_pass"
    val KEY_VIRTUAL_LOCATION = stringPreferencesKey("virtual_location_json")
    val KEY_CUSTOM_LOCATIONS = stringPreferencesKey("custom_locations_json")
    val KEY_PROXY_CONFIG = stringPreferencesKey("vpn_proxy_config_json")
  }
}
