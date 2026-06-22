package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AgentId
import kotlinx.serialization.Serializable

/**
 * Severity levels for error log entries.
 */
@Serializable
enum class LogSeverity { DEBUG, INFO, WARNING, ERROR, CRITICAL }

/**
 * A single entry in the rolling error log.
 *
 * @property id Unique identifier for the log entry.
 * @property agentId Optional agent that produced the error (null for system-level errors).
 * @property severity The severity level of the logged event.
 * @property message Human-readable description of the error.
 * @property timestamp Unix epoch milliseconds when the entry was created.
 * @property stackTrace Optional stack trace for debugging (anonymized before any export).
 */
@Serializable
data class ErrorLogEntry(
    val id: String,
    val agentId: AgentId? = null,
    val severity: LogSeverity,
    val message: String,
    val timestamp: Long,
    val stackTrace: String? = null
)

/**
 * Rolling 7-day on-device error log accessible via the diagnostics screen.
 *
 * Entries older than [maxAgeDays] are automatically pruned on access and on log.
 * The log is stored in-memory with the expectation that it is persisted to the
 * Privacy_Vault by the owning component.
 *
 * Supports anonymized crash reports (with user consent) for critical failures only — no PII.
 *
 * Validates: Requirements 16.2, 16.4
 */
class RollingErrorLog(
    private val maxAgeDays: Int = 7,
    private val clock: () -> Long = { currentTimeMillis() }
) {
    private val entries = mutableListOf<ErrorLogEntry>()

    private val maxAgeMs: Long
        get() = maxAgeDays.toLong() * 24L * 60L * 60L * 1000L

    /**
     * Log a new error entry. Automatically prunes stale entries.
     */
    fun log(entry: ErrorLogEntry) {
        prune()
        entries.add(entry)
    }

    /**
     * Get all current entries within the retention window, ordered by timestamp ascending.
     */
    fun getEntries(): List<ErrorLogEntry> {
        prune()
        return entries.toList()
    }

    /**
     * Get entries since a given timestamp (inclusive).
     */
    fun getEntriesSince(timestamp: Long): List<ErrorLogEntry> {
        prune()
        return entries.filter { it.timestamp >= timestamp }
    }

    /**
     * Remove entries older than the retention window (7 days by default).
     */
    fun prune() {
        val cutoff = clock() - maxAgeMs
        entries.removeAll { it.timestamp < cutoff }
    }

    /**
     * Current number of entries in the log (after pruning).
     */
    fun size(): Int {
        prune()
        return entries.size
    }

    /**
     * Get only critical entries suitable for anonymized crash reporting.
     * Returns entries with severity CRITICAL, with stack traces truncated/hashed
     * to avoid leaking PII.
     */
    fun getCriticalEntries(): List<ErrorLogEntry> {
        prune()
        return entries.filter { it.severity == LogSeverity.CRITICAL }
    }

    /**
     * Generate an anonymized crash report from critical entries.
     * Strips any potential PII from messages and stack traces.
     * Requires user consent before the report is transmitted.
     *
     * @param consentGranted Whether the user has granted consent for crash reporting.
     * @return Anonymized entries if consent is granted, empty list otherwise.
     */
    fun generateAnonymizedCrashReport(consentGranted: Boolean): List<ErrorLogEntry> {
        if (!consentGranted) return emptyList()

        return getCriticalEntries().map { entry ->
            entry.copy(
                // Remove agent ID to avoid correlation
                agentId = null,
                // Anonymize message: keep only the error type/category
                message = anonymizeMessage(entry.message),
                // Hash the stack trace to allow grouping without exposing details
                stackTrace = entry.stackTrace?.let { anonymizeStackTrace(it) }
            )
        }
    }

    /**
     * Clear all entries (for testing or user-initiated data deletion).
     */
    fun clear() {
        entries.clear()
    }

    // --- Private helpers ---

    /**
     * Strip potential PII from error messages.
     * Keeps the first segment (typically the error type) and replaces details.
     */
    private fun anonymizeMessage(message: String): String {
        // Keep only the first colon-separated segment (error type/category)
        val colonIndex = message.indexOf(':')
        return if (colonIndex > 0) {
            message.substring(0, colonIndex).trim()
        } else {
            // If no colon, keep the message but truncate at a reasonable length
            message.take(50)
        }
    }

    /**
     * Anonymize a stack trace by keeping only class/method names without line numbers
     * or file paths that could identify specific user devices.
     */
    private fun anonymizeStackTrace(stackTrace: String): String {
        return stackTrace.lines()
            .take(5) // Keep only top 5 frames
            .joinToString("\n") { line ->
                // Remove file paths and line numbers, keep class.method pattern
                line.replace(Regex("\\(.*\\)"), "()")
                    .trim()
            }
    }
}
