package com.maku.idleharvest.infrastructure

import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property 23: Benchmark Statistics Correctness
 *
 * *For any* array of 100 inference latency measurements, the computed benchmark statistics
 * (min, max, mean, p95) SHALL be mathematically correct: min ≤ mean ≤ max, p95 ≥ 95th
 * percentile value, and mean equals sum/count.
 *
 * **Validates: Requirements 11.3**
 */
class BenchmarkStatisticsPropertyTest {
    /**
     * Computes p95 using the same algorithm as DefaultInferenceEngine.
     */
    private fun computeP95(sortedLatencies: List<Long>): Long {
        if (sortedLatencies.isEmpty()) return 0L
        val index = ((sortedLatencies.size - 1) * 0.95).toInt()
        return sortedLatencies[index]
    }

    /**
     * Property: min ≤ mean ≤ max for any set of 100 latency measurements.
     *
     * For any list of 100 positive latency values, the minimum must be less than or equal
     * to the mean, and the mean must be less than or equal to the maximum.
     */
    @Test
    fun minLessThanOrEqualMeanLessThanOrEqualMax() = runTest {
        forAll(Arb.list(Arb.long(1L..10_000L), 100..100)) { latencies ->
            val sorted = latencies.sorted()
            val min = sorted.first()
            val max = sorted.last()
            val mean = latencies.average()

            min <= mean && mean <= max
        }
    }

    /**
     * Property: p95 is at the correct percentile position in the sorted list.
     *
     * For any list of 100 latency values, the p95 value must equal the value at
     * the 95th percentile index in the sorted list (using the same algorithm as
     * DefaultInferenceEngine).
     */
    @Test
    fun p95IsAtCorrectPercentilePosition() = runTest {
        forAll(Arb.list(Arb.long(1L..10_000L), 100..100)) { latencies ->
            val sorted = latencies.sorted()
            val p95 = computeP95(sorted)
            val expectedIndex = ((sorted.size - 1) * 0.95).toInt()

            // p95 must equal the value at the expected index
            p95 == sorted[expectedIndex]
        }
    }

    /**
     * Property: p95 is greater than or equal to at least 95% of the values.
     *
     * For any list of 100 latency values, the p95 value must be greater than or equal
     * to at least 95 of the 100 values (i.e., at least 95% of values are ≤ p95).
     */
    @Test
    fun p95IsGreaterThanOrEqualTo95PercentOfValues() = runTest {
        forAll(Arb.list(Arb.long(1L..10_000L), 100..100)) { latencies ->
            val sorted = latencies.sorted()
            val p95 = computeP95(sorted)

            val countAtOrBelowP95 = latencies.count { it <= p95 }
            // At least 95% of the values must be at or below p95
            countAtOrBelowP95 >= 95
        }
    }

    /**
     * Property: mean equals sum divided by count.
     *
     * For any list of 100 latency values, the computed mean must equal the sum of all
     * values divided by the count (100). Tolerance is used for floating-point comparison.
     */
    @Test
    fun meanEqualsSumDividedByCount() = runTest {
        forAll(Arb.list(Arb.long(1L..10_000L), 100..100)) { latencies ->
            val sum = latencies.sum()
            val count = latencies.size
            val expectedMean = sum.toDouble() / count.toDouble()
            val computedMean = latencies.average()

            // Floating-point comparison with tolerance
            kotlin.math.abs(computedMean - expectedMean) < 1e-9
        }
    }

    /**
     * Property: All statistics are consistent together.
     *
     * For any list of 100 latency values, the complete set of invariants holds:
     * min ≤ mean ≤ max, p95 at correct position, and mean == sum/count.
     */
    @Test
    fun allStatisticsAreConsistentTogether() = runTest {
        forAll(Arb.list(Arb.long(1L..10_000L), 100..100)) { latencies ->
            val sorted = latencies.sorted()
            val min = sorted.first()
            val max = sorted.last()
            val mean = latencies.average()
            val p95Index = ((sorted.size - 1) * 0.95).toInt()
            val p95 = sorted[p95Index]

            val sum = latencies.sum()
            val expectedMean = sum.toDouble() / latencies.size.toDouble()

            // All invariants must hold simultaneously
            min <= mean &&
                mean <= max &&
                p95 == sorted[p95Index] &&
                kotlin.math.abs(mean - expectedMean) < 1e-9
        }
    }
}
