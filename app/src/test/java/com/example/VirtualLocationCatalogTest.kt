package com.example

import com.example.domain.VirtualLocationCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualLocationCatalogTest {

  @Test
  fun tenRequestedUsPresetsPresentWithAccurateCoordinates() {
    val presets = VirtualLocationCatalog.presets
    assertEquals(10, presets.size)
    val expectedCities =
      listOf(
        "New York",
        "Los Angeles",
        "Chicago",
        "Miami",
        "Dallas",
        "Houston",
        "Seattle",
        "San Francisco",
        "Las Vegas",
        "Washington",
      )
    assertEquals(expectedCities, presets.map { it.city })
    presets.forEach { preset ->
      assertTrue("${preset.displayName} should be inside US bounds",
        VirtualLocationCatalog.isInsideUsGeographicBounds(preset.latitude, preset.longitude))
    }
  }

  @Test
  fun coordinateAndProxyValidationRejectsInvalidInputs() {
    assertTrue(VirtualLocationCatalog.validateCoordinates(40.7128, -74.0060, 18f).isSuccess)
    assertTrue(VirtualLocationCatalog.validateCoordinates(95.0, -74.0060, 18f).isFailure)
    assertTrue(VirtualLocationCatalog.validateCoordinates(40.7, -200.0, 18f).isFailure)
    assertFalse(VirtualLocationCatalog.isInsideUsGeographicBounds(31.5204, 74.3587))

    assertTrue(VirtualLocationCatalog.validateProxyEndpoint("us-ny.proxy.example", "8080").isSuccess)
    assertTrue(VirtualLocationCatalog.validateProxyEndpoint("  ", "8080").isFailure)
    assertTrue(VirtualLocationCatalog.validateProxyEndpoint("us-ny.proxy.example", "99999").isFailure)
  }
}
