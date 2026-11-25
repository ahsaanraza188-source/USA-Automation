package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object CalculatorEngine {

  fun evaluate(rawExpression: String, useDegrees: Boolean = true): Result<String> {
    val cleaned =
      rawExpression
        .replace("×", "*")
        .replace("÷", "/")
        .replace("−", "-")
        .replace("π", PI.toString())
        .replace(" ", "")

    if (cleaned.isBlank()) return Result.failure(IllegalArgumentException("Empty expression"))

    return runCatching {
      val parser = Parser(cleaned, useDegrees)
      val value = parser.parseExpression()
      if (parser.pos < cleaned.length) {
        throw IllegalArgumentException("Unexpected token '${cleaned[parser.pos]}'")
      }
      if (value.isNaN() || value.isInfinite()) {
        throw ArithmeticException("Undefined")
      }
      formatNumber(value)
    }
  }

  private fun formatNumber(value: Double): String {
    if (value == 0.0) return "0"
    val rounded = BigDecimal.valueOf(value).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros()
    val plain = rounded.toPlainString()
    return if (plain.length > 16) String.format("%.6E", value) else plain
  }

  private class Parser(private val str: String, private val useDegrees: Boolean) {
    var pos = 0

    private fun ch(): Int = if (pos < str.length) str[pos].code else -1

    private fun eat(charToEat: Char): Boolean {
      while (ch() == ' '.code) pos++
      if (ch() == charToEat.code) {
        pos++
        return true
      }
      return false
    }

    fun parseExpression(): Double {
      var x = parseTerm()
      while (true) {
        x =
          when {
            eat('+') -> x + parseTerm()
            eat('-') -> x - parseTerm()
            else -> return x
          }
      }
    }

    private fun parseTerm(): Double {
      var x = parseFactor()
      while (true) {
        x =
          when {
            eat('*') -> x * parseFactor()
            eat('/') -> {
              val denom = parseFactor()
              if (denom == 0.0) throw ArithmeticException("Divide by zero")
              x / denom
            }
            eat('%') -> x / 100.0
            else -> return x
          }
      }
    }

    private fun parseFactor(): Double {
      if (eat('+')) return parseFactor()
      if (eat('-')) return -parseFactor()

      var x: Double
      val startPos = pos
      if (eat('(')) {
        x = parseExpression()
        if (!eat(')')) throw IllegalArgumentException("Missing ')'")
      } else if ((ch() in '0'.code..'9'.code) || ch() == '.'.code) {
        while ((ch() in '0'.code..'9'.code) || ch() == '.'.code) pos++
        x = str.substring(startPos, pos).toDouble()
      } else if (ch() in 'a'.code..'z'.code) {
        while (ch() in 'a'.code..'z'.code) pos++
        val func = str.substring(startPos, pos)
        if (func == "e") {
          x = kotlin.math.E
        } else {
          x = parseFactor()
          val angle = if (useDegrees) Math.toRadians(x) else x
          x =
            when (func) {
              "sqrt" -> {
                if (x < 0) throw ArithmeticException("Negative sqrt")
                sqrt(x)
              }
              "sin" -> sin(angle)
              "cos" -> cos(angle)
              "tan" -> tan(angle)
              "log" -> {
                if (x <= 0) throw ArithmeticException("Invalid log")
                log10(x)
              }
              "ln" -> {
                if (x <= 0) throw ArithmeticException("Invalid ln")
                ln(x)
              }
              else -> throw IllegalArgumentException("Unknown function: $func")
            }
        }
      } else {
        throw IllegalArgumentException("Unexpected character")
      }

      if (eat('^')) x = x.pow(parseFactor())
      if (eat('%')) x /= 100.0
      return x
    }
  }
}
