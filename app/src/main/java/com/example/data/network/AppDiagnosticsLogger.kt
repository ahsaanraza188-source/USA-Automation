package com.example.data.network

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DiagnosticLogLevel {
  INFO,
  WARN,
  ERROR,
}

data class DiagnosticLogEntry(
  val id: String = UUID.randomUUID().toString(),
  val timestampEpochMillis: Long = System.currentTimeMillis(),
  val level: DiagnosticLogLevel,
  val component: String,
  val message: String,
  val rootCause: String = "",
  val recoveryHint: String = "",
)

/**
 * Thread-safe in-memory ring buffer capturing real runtime, network, OAuth,
 * Firestore, and WebView diagnostic events without synthetic filler logs.
 */
object AppDiagnosticsLogger {
  private const val MAX_ENTRIES = 100
  private val _entries = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
  val entries: StateFlow<List<DiagnosticLogEntry>> = _entries.asStateFlow()

  fun log(
    level: DiagnosticLogLevel,
    component: String,
    message: String,
    rootCause: String = "",
    recoveryHint: String = "",
  ) {
    val entry =
      DiagnosticLogEntry(
        level = level,
        component = component.ifBlank { "System" },
        message = message,
        rootCause = rootCause,
        recoveryHint = recoveryHint,
      )
    _entries.update { current ->
      (listOf(entry) + current).take(MAX_ENTRIES)
    }
  }

  fun info(component: String, message: String, detail: String = "") {
    log(DiagnosticLogLevel.INFO, component, message, rootCause = detail)
  }

  fun warn(component: String, message: String, rootCause: String = "", recoveryHint: String = "") {
    log(DiagnosticLogLevel.WARN, component, message, rootCause = rootCause, recoveryHint = recoveryHint)
  }

  fun error(
    component: String,
    message: String,
    throwable: Throwable? = null,
    recoveryHint: String = "",
  ) {
    val cause =
      throwable?.let { "${it::class.java.simpleName}: ${it.localizedMessage ?: it.message ?: "Unknown"}" }
        .orEmpty()
    log(
      level = DiagnosticLogLevel.ERROR,
      component = component,
      message = message,
      rootCause = cause,
      recoveryHint = recoveryHint,
    )
  }

  fun clear() {
    _entries.value = emptyList()
  }
}
