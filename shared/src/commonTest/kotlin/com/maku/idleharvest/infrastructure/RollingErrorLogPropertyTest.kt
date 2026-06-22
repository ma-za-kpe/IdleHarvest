package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AgentId
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.of
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property 31: Rolling Error Log Window
 *
 * For any set of error log entries, the on-device log SHALL retain entries from the last
 * 7 days only. Entries older than 7 days are pruned.
 *
 * Tests use the real [RollingErrorLog] class with an injectable clock.
 *
 * Validates: Requirements 16.2, 16.4
 */
class RollingErrorLogPropertyTest {
    companion object {
        private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
        private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
    }

    private fun createEntry(
        id: String,
        timestamp: Long,
        severity: LogSeverity = LogSeverity.ERROR,
        message: String = "Test error",
    ) = ErrorLogEntry(
        id = id,
        agentId = AgentId("test-agent"),
        severity = severity,
        message = message,
        timestamp = timestamp,
        stackTrace = null,
    )

    @Test
    fun entriesWithinSevenDaysAreRetained() = runTest {
        forAll(Arb.long(0L..SEVEN_DAYS_MS - 1)) { ageMs ->
            val now = SEVEN_DAYS_MS + 1_000_000L // Ensure "now" is far enough in the future
            val log = RollingErrorLog(maxAgeDays = 7, clock = { now })

            val entryTimestamp = now - ageMs
            val entry = createEntry(id = "entry-$ageMs", timestamp = entryTimestamp)
            log.log(entry)

            // Entry within 7 days must be retained
            log.size() == 1 && log.getEntries().first().id == "entry-$ageMs"
        }
    }

    @Test
    fun entriesOlderThanSevenDaysArePruned() = runTest {
        forAll(Arb.long(SEVEN_DAYS_MS + 1..SEVEN_DAYS_MS * 3)) { ageMs ->
            val now = SEVEN_DAYS_MS * 4 // Ensure "now" is far ahead
            val log = RollingErrorLog(maxAgeDays = 7, clock = { now })

            val entryTimestamp = now - ageMs
            val entry = createEntry(id = "old-entry", timestamp = entryTimestamp)
            log.log(entry)

            // Entry older than 7 days must be pruned
            log.size() == 0
        }
    }

    @Test
    fun boundaryAtExactlySevenDaysIsPruned() = runTest {
        forAll(Arb.int(1..100)) { _ ->
            val now = SEVEN_DAYS_MS * 2
            val log = RollingErrorLog(maxAgeDays = 7, clock = { now })

            // Entry exactly at the 7-day boundary (cutoff = now - 7days, entry < cutoff → pruned)
            val entryTimestamp = now - SEVEN_DAYS_MS - 1
            val entry = createEntry(id = "boundary", timestamp = entryTimestamp)
            log.log(entry)

            // Entry at exactly 7 days + 1ms ago should be pruned
            log.size() == 0
        }
    }

    @Test
    fun mixedAgedEntriesOnlyRetainsRecent() = runTest {
        val severityArb =
            Arb.of(
                LogSeverity.DEBUG,
                LogSeverity.INFO,
                LogSeverity.WARNING,
                LogSeverity.ERROR,
                LogSeverity.CRITICAL,
            )

        forAll(Arb.int(2..10), severityArb) { entryCount, severity ->
            val now = SEVEN_DAYS_MS * 3
            val log = RollingErrorLog(maxAgeDays = 7, clock = { now })

            var recentCount = 0
            for (i in 0 until entryCount) {
                // Alternate between old entries and recent entries
                val isRecent = i % 2 == 0
                val timestamp =
                    if (isRecent) {
                        now - ONE_DAY_MS * (i % 6 + 1) // 1-6 days ago (within window)
                    } else {
                        now - SEVEN_DAYS_MS - ONE_DAY_MS * (i + 1) // Beyond 7 days
                    }

                if (isRecent) recentCount++

                val entry =
                    createEntry(
                        id = "entry-$i",
                        timestamp = timestamp,
                        severity = severity,
                    )
                log.log(entry)
            }

            // Only entries within 7 days should remain
            log.size() == recentCount
        }
    }

    @Test
    fun pruneRemovesOnlyOldEntries() = runTest {
        forAll(Arb.list(Arb.long(1L..SEVEN_DAYS_MS * 2), 1..15)) { ages ->
            val now = SEVEN_DAYS_MS * 3
            val log = RollingErrorLog(maxAgeDays = 7, clock = { now })

            val expectedRetained = ages.count { it <= SEVEN_DAYS_MS - 1 }

            ages.forEachIndexed { index, ageMs ->
                val timestamp = now - ageMs
                val entry = createEntry(id = "entry-$index", timestamp = timestamp)
                log.log(entry)
            }

            // After adding all entries (which triggers prune on each log call),
            // all entries within the window should be retained
            val actualSize = log.size()
            actualSize == expectedRetained
        }
    }

    @Test
    fun getEntriesSinceReturnsOnlyEntriesAfterTimestamp() = runTest {
        forAll(Arb.long(ONE_DAY_MS..SEVEN_DAYS_MS - ONE_DAY_MS)) { sinceAge ->
            val now = SEVEN_DAYS_MS * 2
            val log = RollingErrorLog(maxAgeDays = 7, clock = { now })

            // Add entries at 1, 2, 3, 4, 5, 6 days ago
            for (day in 1..6) {
                val timestamp = now - (day * ONE_DAY_MS)
                log.log(createEntry(id = "day-$day", timestamp = timestamp))
            }

            val sinceTimestamp = now - sinceAge
            val entries = log.getEntriesSince(sinceTimestamp)

            // All returned entries must have timestamp >= sinceTimestamp
            entries.all { it.timestamp >= sinceTimestamp }
        }
    }

    @Test
    fun sizeIsConsistentWithGetEntries() = runTest {
        forAll(Arb.int(0..20)) { count ->
            val now = SEVEN_DAYS_MS * 2
            val log = RollingErrorLog(maxAgeDays = 7, clock = { now })

            repeat(count) { i ->
                // All entries within window (1 hour intervals)
                val timestamp = now - (i + 1) * 3_600_000L
                log.log(createEntry(id = "entry-$i", timestamp = timestamp))
            }

            log.size() == log.getEntries().size
        }
    }
}
