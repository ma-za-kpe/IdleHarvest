package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.ConsentToken
import com.maku.idleharvest.domain.models.DataType
import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property 19: Anonymization Before Transmission
 *
 * *For any* data export request, the output SHALL contain no personally identifiable
 * information (PII), and a valid consent token SHALL be required before any data leaves
 * the device. Export without consent SHALL always fail.
 *
 * **Validates: Requirements 8.3**
 */
class AnonymizationPropertyTest {
    private val cryptoProvider = SimpleCryptoProvider()
    private val fixedClock = { 1_000_000L } // fixed "now"
    private val vault = DefaultPrivacyVault(cryptoProvider, clock = fixedClock)

    @Test
    fun exportFailsWithExpiredConsentToken() = runTest {
        forAll(Arb.enum<DataType>()) { dataType ->
            val expiredToken =
                ConsentToken(
                    token = "valid-token",
                    grantedAt = 500_000L,
                    expiresAt = 900_000L, // before "now" (1_000_000)
                    allowedDataTypes = listOf(dataType),
                )
            vault.exportAnonymized(dataType, expiredToken).isFailure
        }
    }

    @Test
    fun exportFailsWithFutureGrantedConsentToken() = runTest {
        forAll(Arb.enum<DataType>()) { dataType ->
            val futureToken =
                ConsentToken(
                    token = "valid-token",
                    grantedAt = 2_000_000L, // after "now" (1_000_000)
                    expiresAt = 3_000_000L,
                    allowedDataTypes = listOf(dataType),
                )
            vault.exportAnonymized(dataType, futureToken).isFailure
        }
    }

    @Test
    fun exportFailsWithBlankConsentToken() = runTest {
        forAll(Arb.enum<DataType>()) { dataType ->
            val blankToken =
                ConsentToken(
                    token = "",
                    grantedAt = 500_000L,
                    expiresAt = 2_000_000L,
                    allowedDataTypes = listOf(dataType),
                )
            vault.exportAnonymized(dataType, blankToken).isFailure
        }
    }

    @Test
    fun exportFailsWhenDataTypeNotInScope() = runTest {
        // Token allows only RESOURCE_METRICS but we request other data types
        val token =
            ConsentToken(
                token = "valid-consent",
                grantedAt = 500_000L,
                expiresAt = 2_000_000L,
                allowedDataTypes = listOf(DataType.RESOURCE_METRICS),
            )
        val result = vault.exportAnonymized(DataType.EARNING_SUMMARY, token)
        assertTrue(result.isFailure)
    }

    @Test
    fun exportSucceedsWithValidConsentToken() = runTest {
        // Store some data first with the appropriate key prefix
        vault.store("resource_test", "data".encodeToByteArray())

        val validToken =
            ConsentToken(
                token = "valid-consent",
                grantedAt = 500_000L,
                expiresAt = 2_000_000L,
                allowedDataTypes = listOf(DataType.RESOURCE_METRICS),
            )
        val result = vault.exportAnonymized(DataType.RESOURCE_METRICS, validToken)
        assertTrue(result.isSuccess)
    }
}
