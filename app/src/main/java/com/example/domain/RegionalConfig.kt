package com.example.domain

import com.example.data.model.DevicePreferences
import com.example.data.network.PublicIpReport
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

data class CountryPreset(
  val code: String,
  val name: String,
  val defaultState: String,
  val states: List<String>,
  val defaultLocaleTag: String,
  val defaultLanguageName: String,
  val defaultTimezoneId: String,
  val defaultDateFormat: String,
  val defaultUse24Hour: Boolean,
  val defaultCurrencyCode: String,
  val defaultNumberFormat: String,
  val defaultTempUnit: String,
  val defaultMeasurementSystem: String,
)

data class LanguagePreset(
  val localeTag: String,
  val displayName: String,
)

data class CurrencyPreset(
  val code: String,
  val symbol: String,
  val label: String,
)

data class VerificationCheckItem(
  val key: String,
  val label: String,
  val expectedUsDefault: String,
  val actualConfigured: String,
  val isMatchingUsDefault: Boolean,
  val detail: String,
)

object RegionalCatalog {
  val countries: List<CountryPreset> =
    listOf(
      CountryPreset(
        code = "US",
        name = "United States",
        defaultState = "New York",
        states = listOf("New York", "California", "Texas", "Florida", "Illinois", "Washington", "Virginia", "Massachusetts"),
        defaultLocaleTag = "en-US",
        defaultLanguageName = "English (United States)",
        defaultTimezoneId = "America/New_York",
        defaultDateFormat = "MM/DD/YYYY",
        defaultUse24Hour = false,
        defaultCurrencyCode = "USD",
        defaultNumberFormat = "1,234.56",
        defaultTempUnit = "FAHRENHEIT",
        defaultMeasurementSystem = "US_CUSTOMARY",
      ),
      CountryPreset(
        code = "CA",
        name = "Canada",
        defaultState = "Ontario",
        states = listOf("Ontario", "British Columbia", "Quebec", "Alberta"),
        defaultLocaleTag = "en-CA",
        defaultLanguageName = "English (Canada)",
        defaultTimezoneId = "America/Toronto",
        defaultDateFormat = "YYYY-MM-DD",
        defaultUse24Hour = false,
        defaultCurrencyCode = "CAD",
        defaultNumberFormat = "1,234.56",
        defaultTempUnit = "CELSIUS",
        defaultMeasurementSystem = "METRIC",
      ),
      CountryPreset(
        code = "GB",
        name = "United Kingdom",
        defaultState = "England",
        states = listOf("England", "Scotland", "Wales", "Northern Ireland"),
        defaultLocaleTag = "en-GB",
        defaultLanguageName = "English (United Kingdom)",
        defaultTimezoneId = "Europe/London",
        defaultDateFormat = "DD/MM/YYYY",
        defaultUse24Hour = true,
        defaultCurrencyCode = "GBP",
        defaultNumberFormat = "1,234.56",
        defaultTempUnit = "CELSIUS",
        defaultMeasurementSystem = "METRIC",
      ),
      CountryPreset(
        code = "AU",
        name = "Australia",
        defaultState = "New South Wales",
        states = listOf("New South Wales", "Victoria", "Queensland", "Western Australia"),
        defaultLocaleTag = "en-AU",
        defaultLanguageName = "English (Australia)",
        defaultTimezoneId = "Australia/Sydney",
        defaultDateFormat = "DD/MM/YYYY",
        defaultUse24Hour = false,
        defaultCurrencyCode = "AUD",
        defaultNumberFormat = "1,234.56",
        defaultTempUnit = "CELSIUS",
        defaultMeasurementSystem = "METRIC",
      ),
      CountryPreset(
        code = "DE",
        name = "Germany",
        defaultState = "Berlin",
        states = listOf("Berlin", "Bavaria", "Hamburg", "Hesse"),
        defaultLocaleTag = "de-DE",
        defaultLanguageName = "German (Germany)",
        defaultTimezoneId = "Europe/Berlin",
        defaultDateFormat = "DD/MM/YYYY",
        defaultUse24Hour = true,
        defaultCurrencyCode = "EUR",
        defaultNumberFormat = "1.234,56",
        defaultTempUnit = "CELSIUS",
        defaultMeasurementSystem = "METRIC",
      ),
      CountryPreset(
        code = "PK",
        name = "Pakistan",
        defaultState = "Punjab",
        states = listOf("Punjab", "Sindh", "Islamabad", "Khyber Pakhtunkhwa"),
        defaultLocaleTag = "en-PK",
        defaultLanguageName = "English (Pakistan)",
        defaultTimezoneId = "Asia/Karachi",
        defaultDateFormat = "DD/MM/YYYY",
        defaultUse24Hour = false,
        defaultCurrencyCode = "PKR",
        defaultNumberFormat = "1,234.56",
        defaultTempUnit = "CELSIUS",
        defaultMeasurementSystem = "METRIC",
      ),
      CountryPreset(
        code = "JP",
        name = "Japan",
        defaultState = "Tokyo",
        states = listOf("Tokyo", "Osaka", "Kyoto", "Hokkaido"),
        defaultLocaleTag = "ja-JP",
        defaultLanguageName = "Japanese (Japan)",
        defaultTimezoneId = "Asia/Tokyo",
        defaultDateFormat = "YYYY-MM-DD",
        defaultUse24Hour = true,
        defaultCurrencyCode = "JPY",
        defaultNumberFormat = "1,234.56",
        defaultTempUnit = "CELSIUS",
        defaultMeasurementSystem = "METRIC",
      ),
    )

  val languages: List<LanguagePreset> =
    listOf(
      LanguagePreset("en-US", "English (United States)"),
      LanguagePreset("en-GB", "English (United Kingdom)"),
      LanguagePreset("en-CA", "English (Canada)"),
      LanguagePreset("es-US", "Spanish (United States)"),
      LanguagePreset("fr-CA", "French (Canada)"),
      LanguagePreset("de-DE", "German (Germany)"),
      LanguagePreset("en-PK", "English (Pakistan)"),
      LanguagePreset("ja-JP", "Japanese (Japan)"),
    )

  val allTimezones: List<UsTimezoneOption> =
    listOf(
      UsTimezoneOption("America/New_York", "EST / EDT", "New York (US Eastern)"),
      UsTimezoneOption("America/Chicago", "CST / CDT", "Chicago (US Central)"),
      UsTimezoneOption("America/Denver", "MST / MDT", "Denver (US Mountain)"),
      UsTimezoneOption("America/Los_Angeles", "PST / PDT", "Los Angeles (US Pacific)"),
      UsTimezoneOption("America/Anchorage", "AKST / AKDT", "Anchorage (US Alaska)"),
      UsTimezoneOption("Pacific/Honolulu", "HST", "Honolulu (US Hawaii)"),
      UsTimezoneOption("America/Toronto", "EST / EDT", "Toronto (Canada)"),
      UsTimezoneOption("Europe/London", "GMT / BST", "London (UK)"),
      UsTimezoneOption("Europe/Berlin", "CET / CEST", "Berlin (Germany)"),
      UsTimezoneOption("Asia/Karachi", "PKT", "Karachi (Pakistan)"),
      UsTimezoneOption("Australia/Sydney", "AEST / AEDT", "Sydney (Australia)"),
      UsTimezoneOption("Asia/Tokyo", "JST", "Tokyo (Japan)"),
    )

  val usTimezoneIds: Set<String> =
    setOf(
      "America/New_York",
      "America/Chicago",
      "America/Denver",
      "America/Los_Angeles",
      "America/Anchorage",
      "Pacific/Honolulu",
    )

  val dateFormats: List<String> = listOf("MM/DD/YYYY", "DD/MM/YYYY", "YYYY-MM-DD")

  val numberFormats: List<String> = listOf("1,234.56", "1.234,56", "1 234,56")

  val currencies: List<CurrencyPreset> =
    listOf(
      CurrencyPreset("USD", "$", "USD (US Dollar)"),
      CurrencyPreset("CAD", "CA$", "CAD (Canadian Dollar)"),
      CurrencyPreset("GBP", "£", "GBP (British Pound)"),
      CurrencyPreset("EUR", "€", "EUR (Euro)"),
      CurrencyPreset("AUD", "A$", "AUD (Australian Dollar)"),
      CurrencyPreset("PKR", "Rs", "PKR (Pakistani Rupee)"),
      CurrencyPreset("JPY", "¥", "JPY (Japanese Yen)"),
    )

  fun findCountry(code: String): CountryPreset =
    countries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: countries.first()

  fun findCurrency(code: String): CurrencyPreset =
    currencies.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: currencies.first()

  fun applyCountryDefaults(current: DevicePreferences, country: CountryPreset): DevicePreferences =
    current.copy(
      countryCode = country.code,
      countryName = country.name,
      stateRegion = country.defaultState,
      localeTag = country.defaultLocaleTag,
      languageName = country.defaultLanguageName,
      usTimezoneId = country.defaultTimezoneId,
      dateFormat = country.defaultDateFormat,
      use24HourTime = country.defaultUse24Hour,
      currencyCode = country.defaultCurrencyCode,
      numberFormat = country.defaultNumberFormat,
      temperatureUnit = country.defaultTempUnit,
      measurementSystem = country.defaultMeasurementSystem,
    )

  fun resetToUsaDefaults(current: DevicePreferences): DevicePreferences =
    applyCountryDefaults(current, countries.first())
}

object RegionalFormatter {

  fun localeFromTag(localeTag: String): Locale =
    Locale.forLanguageTag(localeTag.ifBlank { "en-US" }).let {
      if (it.language.isNullOrBlank()) Locale.US else it
    }

  fun shortTimezoneCode(zoneId: String): String =
    when (zoneId) {
      "America/New_York" -> "US-EST"
      "America/Chicago" -> "US-CST"
      "America/Denver" -> "US-MST"
      "America/Los_Angeles" -> "US-PST"
      "America/Anchorage" -> "US-AK"
      "Pacific/Honolulu" -> "US-HI"
      else -> TimeZone.getTimeZone(zoneId).getDisplayName(false, TimeZone.SHORT, Locale.US)
    }

  fun formatTime(epochMillis: Long, prefs: DevicePreferences): String {
    val tz = TimeZone.getTimeZone(prefs.usTimezoneId.ifBlank { "America/New_York" })
    val locale = localeFromTag(prefs.localeTag)
    val pattern = if (prefs.use24HourTime) "HH:mm" else "h:mm a"
    return SimpleDateFormat(pattern, locale).apply { timeZone = tz }.format(Date(epochMillis))
  }

  fun formatTimeWithSeconds(
    epochMillis: Long,
    zoneId: String,
    use24Hour: Boolean,
    localeTag: String = "en-US",
  ): String {
    val tz = TimeZone.getTimeZone(zoneId.ifBlank { "America/New_York" })
    val locale = localeFromTag(localeTag)
    val pattern = if (use24Hour) "HH:mm:ss" else "h:mm:ss a"
    return SimpleDateFormat(pattern, locale).apply { timeZone = tz }.format(Date(epochMillis))
  }

  fun formatDate(epochMillis: Long, prefs: DevicePreferences): String {
    val tz = TimeZone.getTimeZone(prefs.usTimezoneId.ifBlank { "America/New_York" })
    val locale = localeFromTag(prefs.localeTag)
    val pattern =
      when (prefs.dateFormat) {
        "DD/MM/YYYY" -> "dd/MM/yyyy"
        "YYYY-MM-DD" -> "yyyy-MM-dd"
        else -> "MM/dd/yyyy"
      }
    return SimpleDateFormat(pattern, locale).apply { timeZone = tz }.format(Date(epochMillis))
  }

  fun formatHomeAtAGlanceDate(epochMillis: Long, prefs: DevicePreferences): String {
    val tz = TimeZone.getTimeZone(prefs.usTimezoneId.ifBlank { "America/New_York" })
    val locale = localeFromTag(prefs.localeTag)
    val weekday = SimpleDateFormat("EEE", locale).apply { timeZone = tz }.format(Date(epochMillis))
    val dateStr = formatDate(epochMillis, prefs)
    val tzShort = tz.getDisplayName(tz.inDaylightTime(Date(epochMillis)), TimeZone.SHORT, Locale.US)
    return "$weekday, $dateStr • $tzShort"
  }

  fun formatNumber(value: Double, prefs: DevicePreferences, decimals: Int = 2): String {
    val symbols = DecimalFormatSymbols(Locale.US).apply {
      when (prefs.numberFormat) {
        "1.234,56" -> {
          groupingSeparator = '.'
          decimalSeparator = ','
        }
        "1 234,56" -> {
          groupingSeparator = ' '
          decimalSeparator = ','
        }
        else -> {
          groupingSeparator = ','
          decimalSeparator = '.'
        }
      }
    }
    val frac = if (decimals > 0) "." + "0".repeat(decimals) else ""
    val df = DecimalFormat("#,##0$frac", symbols)
    return df.format(value)
  }

  fun formatCurrency(amount: Double, prefs: DevicePreferences): String {
    val preset = RegionalCatalog.findCurrency(prefs.currencyCode)
    val decimals = if (preset.code == "JPY") 0 else 2
    val formatted = formatNumber(amount, prefs, decimals)
    return "${preset.symbol}$formatted ${preset.code}"
  }

  fun formatTemperature(fahrenheit: Double, prefs: DevicePreferences): String {
    return if (prefs.temperatureUnit == "CELSIUS") {
      val c = ((fahrenheit - 32.0) * 5.0 / 9.0).roundToInt()
      "$c°C"
    } else {
      "${fahrenheit.roundToInt()}°F"
    }
  }

  fun formatDistanceFromMiles(miles: Double, prefs: DevicePreferences): String {
    return if (prefs.measurementSystem == "METRIC") {
      val km = miles * 1.60934
      "${formatNumber(km, prefs, 1)} km"
    } else {
      "${formatNumber(miles, prefs, 1)} mi"
    }
  }

  fun formatLengthFromFeetInches(totalInches: Double, prefs: DevicePreferences): String {
    return if (prefs.measurementSystem == "METRIC") {
      val cm = totalInches * 2.54
      if (cm >= 100.0) "${formatNumber(cm / 100.0, prefs, 2)} m" else "${cm.roundToInt()} cm"
    } else {
      val feet = (totalInches / 12.0).toInt()
      val inches = (totalInches % 12.0).roundToInt()
      "${feet} ft ${inches} in"
    }
  }

  fun formatWeightFromPounds(pounds: Double, prefs: DevicePreferences): String {
    return if (prefs.measurementSystem == "METRIC") {
      val kg = pounds * 0.453592
      "${formatNumber(kg, prefs, 1)} kg"
    } else {
      "${formatNumber(pounds, prefs, 1)} lb"
    }
  }

  fun evaluateUsaRegionVerification(
    prefs: DevicePreferences,
    ipReport: PublicIpReport?,
  ): List<VerificationCheckItem> {
    val isUsCountry = prefs.countryCode.equals("US", ignoreCase = true)
    val isEnUsLocale = prefs.localeTag.equals("en-US", ignoreCase = true)
    val tzValid = TimeZone.getAvailableIDs().contains(prefs.usTimezoneId)
    val isUsTz = RegionalCatalog.usTimezoneIds.contains(prefs.usTimezoneId)
    val isUsDateFmt = prefs.dateFormat == "MM/DD/YYYY"
    val is12Hour = !prefs.use24HourTime
    val isUsd = prefs.currencyCode == "USD" && prefs.numberFormat == "1,234.56"
    val isUsTempUnits =
      prefs.temperatureUnit == "FAHRENHEIT" && prefs.measurementSystem == "US_CUSTOMARY"

    return listOf(
      VerificationCheckItem(
        key = "country",
        label = "Virtual Country",
        expectedUsDefault = "United States (US)",
        actualConfigured = "${prefs.countryName} (${prefs.countryCode}) — ${prefs.stateRegion}",
        isMatchingUsDefault = isUsCountry,
        detail = if (isUsCountry) "Configured as United States" else "Customized away from US default",
      ),
      VerificationCheckItem(
        key = "locale",
        label = "Locale & Language",
        expectedUsDefault = "en-US • English (United States)",
        actualConfigured = "${prefs.localeTag} • ${prefs.languageName}",
        isMatchingUsDefault = isEnUsLocale,
        detail = if (isEnUsLocale) "Active locale matches en-US" else "Active locale is ${prefs.localeTag}",
      ),
      VerificationCheckItem(
        key = "timezone",
        label = "Time Zone",
        expectedUsDefault = "US Time Zone (America/New_York)",
        actualConfigured = prefs.usTimezoneId,
        isMatchingUsDefault = tzValid && isUsTz,
        detail =
          when {
            !tzValid -> "Invalid time zone ID"
            isUsTz -> "Valid US zone (${shortTimezoneCode(prefs.usTimezoneId)})"
            else -> "Valid non-US zone configured"
          },
      ),
      VerificationCheckItem(
        key = "datetime",
        label = "Date & Time Format",
        expectedUsDefault = "MM/DD/YYYY • 12-hour",
        actualConfigured = "${prefs.dateFormat} • ${if (prefs.use24HourTime) "24-hour" else "12-hour"}",
        isMatchingUsDefault = isUsDateFmt && is12Hour,
        detail = if (isUsDateFmt && is12Hour) "US standard MM/DD/YYYY + 12h active" else "Custom date/time format active",
      ),
      VerificationCheckItem(
        key = "currency",
        label = "Currency & Number Format",
        expectedUsDefault = "USD • 1,234.56",
        actualConfigured = "${prefs.currencyCode} • ${prefs.numberFormat}",
        isMatchingUsDefault = isUsd,
        detail = if (isUsd) "US Dollar & comma-dot formatting active" else "Custom currency/number format active",
      ),
      VerificationCheckItem(
        key = "units",
        label = "Temperature & Units",
        expectedUsDefault = "°F • Miles, Feet, Inches, Pounds",
        actualConfigured =
          "${if (prefs.temperatureUnit == "FAHRENHEIT") "Fahrenheit (°F)" else "Celsius (°C)"} • " +
            if (prefs.measurementSystem == "US_CUSTOMARY") "US Customary (mi, ft, in, lb)" else "Metric (km, m, cm, kg)",
        isMatchingUsDefault = isUsTempUnits,
        detail = if (isUsTempUnits) "US customary units & Fahrenheit active" else "Metric/Celsius override active",
      ),
      VerificationCheckItem(
        key = "public_ip",
        label = "Actual Public IP (Independent)",
        expectedUsDefault = "Verified US Exit IP (via real network/VPN)",
        actualConfigured =
          ipReport?.let { "${it.ipAddress} • ${it.countryName} (${it.countryCode})" } ?: "Not verified yet",
        isMatchingUsDefault = ipReport?.isVerifiedUsa == true,
        detail =
          when {
            ipReport == null -> "Tap Refresh Network to test actual public IP"
            ipReport.isVerifiedUsa -> "Real exit IP verified in United States (${ipReport.city}, ${ipReport.region})"
            else -> "Real network exit is ${ipReport.countryName} (${ipReport.countryCode}). Virtual US settings do NOT spoof IP."
          },
      ),
    )
  }
}
