package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {
  @Test
  fun evaluatesArithmeticAndPrecedence() {
    assertEquals("30", CalculatorEngine.evaluate("(12+8)×3÷2").getOrNull())
    assertEquals("10.5", CalculatorEngine.evaluate("50%+10").getOrNull())
    assertEquals("8", CalculatorEngine.evaluate("2^3").getOrNull())
  }

  @Test
  fun evaluatesScientificFunctionsAndConstants() {
    assertEquals("12", CalculatorEngine.evaluate("sqrt(144)").getOrNull())
    assertEquals("0.5", CalculatorEngine.evaluate("sin(30)", useDegrees = true).getOrNull())
    assertEquals("1", CalculatorEngine.evaluate("ln(e)").getOrNull())
    assertEquals("2", CalculatorEngine.evaluate("log(100)").getOrNull())
  }

  @Test
  fun rejectsDivideByZeroAndEmpty() {
    assertTrue(CalculatorEngine.evaluate("5÷0").isFailure)
    assertTrue(CalculatorEngine.evaluate("   ").isFailure)
  }
}
