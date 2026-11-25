package com.example

import com.example.data.model.DevicePreferences
import com.example.data.network.PublicIpReport
import com.example.domain.RegionalCatalog
import com.example.domain.RegionalFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalFormatterTest {

  @Test
  fun defaultPreferencesMatchUsaConfiguration() {
    val prefs = DevicePreferences()
    assertEquals("US", prefs.countryCode)
    assertEquals("United States", prefs.countryName)
    assertEquals("New York", prefs.stateRegion)
    assertEquals("en-US", prefs.localeTag)
    assertEquals("English (United States)", prefs.languageName)
    assertEquals("America/New_York", prefs.usTimezoneId)
    assertEquals("MM/DD/YYYY", prefs.dateFormat)
    assertFalse(prefs.use24HourTime)
    assertEquals("USD", prefs.currencyCode)
    assertEquals("1,234.56", prefs.numberFormat)
    assertEquals("FAHRENHEIT", prefs.temperatureUnit)
    assertEquals("US_CUSTOMARY", prefs.measurementSystem)
  }

  @Test
  fun formatsDateAndTimeAccordingToRegionPreferences() {
    val fixedMillis = 1_764_072_000_000L // Deterministic UTC timestamp
    val usPrefs = DevicePreferences()
    val dePrefs =
      RegionalCatalog.applyCountryDefaults(usPrefs, RegionalCatalog.findCountry("DE"))

    val usDate = RegionalFormatter.formatDate(fixedMillis, usPrefs)
    val deDate = RegionalFormatter.formatDate(fixedMillis, dePrefs)
    assertTrue("Expected MM/DD/YYYY slash format, got $usDate", usDate.matches(Regex("\\d{2}/\\d{2}/\\d{4}")))
    assertTrue("Expected DD/MM/YYYY format, got $deDate", deDate.matches(Regex("\\d{2}/\\d{2}/\\d{4}")))

    val time12 = RegionalFormatter.formatTime(fixedMillis, usPrefs)
    val time24 = RegionalFormatter.formatTime(fixedMillis, usPrefs.copy(use24HourTime = true))
    assertTrue("Expected AM/PM in 12h mode, got $time12", time12.contains("AM") || time12.contains("PM"))
    assertFalse("Expected no AM/PM in 24h mode, got $time24", time24.contains("AM") || time24.contains("PM"))
  }

  @Test
  fun formatsCurrencyNumbersTemperatureAndUnits() {
    val usPrefs = DevicePreferences()
    assertEquals("$1,234.56 USD", RegionalFormatter.formatCurrency(1234.56, usPrefs))
    assertEquals("72°F", RegionalFormatter.formatTemperature(72.0, usPrefs))
    assertEquals("10.0 mi", RegionalFormatter.formatDistanceFromMiles(10.0, usPrefs))
    assertEquals("5 ft 10 in", RegionalFormatter.formatLengthFromFeetInches(70.0, usPrefs))
    assertEquals("150.0 lb", RegionalFormatter.formatWeightFromPounds(150.0, usPrefs))

    val metricPrefs =
      usPrefs.copy(
        currencyCode = "EUR",
        numberFormat = "1.234,56",
        temperatureUnit = "CELSIUS",
        measurementSystem = "METRIC",
      )
    assertEquals("€1.234,56 EUR", RegionalFormatter.formatCurrency(1234.56, metricPrefs))
    assertEquals("22°C", RegionalFormatter.formatTemperature(72.0, metricPrefs))
    assertTrue(RegionalFormatter.formatDistanceFromMiles(10.0, metricPrefs).endsWith("km"))
    assertTrue(RegionalFormatter.formatWeightFromPounds(100.0, metricPrefs).endsWith("kg"))
  }

  @Test
  fun verifiesVirtualUsaRegionSeparatelyFromRealIp() {
    val usPrefs = DevicePreferences()
    val nonUsIp =
      PublicIpReport(
        ipAddress = "203.0.113.10",
        countryName = "Germany",
        countryCode = "DE",
        region = "Berlin",
        city = "Berlin",
        isp = "Test ISP",
        asn = "AS1234",
        latencyMs = 45L,
        providerName = "ipapi.co",
        isVerifiedUsa = false,
        checkedAtEpochMillis = 1000L,
      )
    val checks = RegionalFormatter.evaluateUsaRegionVerification(usPrefs, nonUsIp)
    assertTrue(checks.first { it.key == "country" }.isMatchingUsDefault)
    assertTrue(checks.first { it.key == "locale" }.isMatchingUsDefault)
    assertTrue(checks.first { it.key == "timezone" }.isMatchingUsDefault)
    assertTrue(checks.first { it.key == "datetime" }.isMatchingUsDefault)
    assertTrue(checks.first { it.key == "currency" }.isMatchingUsDefault)
    assertTrue(checks.first { it.key == "units" }.isMatchingUsDefault)
    assertFalse(checks.first { it.key == "public_ip" }.isMatchingUsDefault)

    val usIp = nonUsIp.copy(countryCode = "US", countryName = "United States", isVerifiedUsa = true)
    val checksWithUsIp = RegionalFormatter.evaluateUsaRegionVerification(usPrefs, usIp)
    assertTrue(checksWithUsIp.first { it.key == "public_ip" }.isMatchingUsDefault)
  }
}
