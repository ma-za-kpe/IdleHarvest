package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.PrivacyVault
import com.maku.idleharvest.domain.models.ConsentToken
import com.maku.idleharvest.domain.models.DataType
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property 32: Offline Queue and Retry
 *
 * When the device is offline, pending items are queued and retried when connectivity
 * returns. Items are processed in FIFO order. Successfully processed items are removed,
 * failed items remain with incremented retry count.
 *
 * Tests use the real [OfflineQueue] class with a fake in-memory PrivacyVault.
 *
 * Validates: Requirements 16.3
 */
class OfflineQueuePropertyTest {
    /**
     * In-memory fake PrivacyVault for testing OfflineQueue without real encryption.
     */
    private class FakePrivacyVault : PrivacyVault {
        private val store = mutableMapOf<String, ByteArray>()

        override suspend fun store(
            key: String,
            data: ByteArray,
        ): Result<Unit> {
            store[key] = data.copyOf()
            return Result.success(Unit)
        }

        override suspend fun retrieve(key: String): Result<ByteArray?> = Result.success(store[key]?.copyOf())

        override suspend fun delete(key: String): Result<Unit> {
            store.remove(key)
            return Result.success(Unit)
        }

        override suspend fun deleteAll(): Result<Unit> {
            store.clear()
            return Result.success(Unit)
        }

        override suspend fun exportAnonymized(
            dataType: DataType,
            consentToken: ConsentToken,
        ): Result<ByteArray> = Result.success(byteArrayOf())

        override fun isIntegrityValid(): Boolean = true
    }

    private fun createQueue(): OfflineQueue = OfflineQueue(vault = FakePrivacyVault())

    private fun createItem(
        id: String,
        type: QueueItemType = QueueItemType.TRANSACTION,
        enqueuedAt: Long = 1_000_000L,
    ) = QueuedItem(
        id = id,
        type = type,
        payload = "payload-$id".encodeToByteArray(),
        enqueuedAt = enqueuedAt,
        retryCount = 0,
    )

    @Test
    fun enqueuedItemsAreRetrievedInFifoOrder() = runTest {
        forAll(Arb.int(1..20)) { count ->
            val queue = createQueue()

            // Enqueue items in order
            for (i in 0 until count) {
                queue.enqueue(createItem(id = "item-$i"))
            }

            // Dequeue should return in FIFO order
            var inOrder = true
            for (i in 0 until count) {
                val item = queue.dequeue()
                if (item?.id != "item-$i") {
                    inOrder = false
                    break
                }
            }

            inOrder && queue.isEmpty()
        }
    }

    @Test
    fun sizeReflectsEnqueuedItems() = runTest {
        forAll(Arb.int(0..30)) { count ->
            val queue = createQueue()

            repeat(count) { i ->
                queue.enqueue(createItem(id = "item-$i"))
            }

            queue.size() == count
        }
    }

    @Test
    fun isEmptyIsTrueOnlyWhenQueueHasNoItems() = runTest {
        forAll(Arb.int(0..10)) { count ->
            val queue = createQueue()

            val emptyBefore = queue.isEmpty()

            repeat(count) { i ->
                queue.enqueue(createItem(id = "item-$i"))
            }

            val emptyAfter = queue.isEmpty()

            emptyBefore && (if (count == 0) emptyAfter else !emptyAfter)
        }
    }

    @Test
    fun retryAllRemovesSuccessfulItems() = runTest {
        forAll(Arb.int(1..10)) { count ->
            val queue = createQueue()

            repeat(count) { i ->
                queue.enqueue(createItem(id = "item-$i"))
            }

            // All items succeed during retry
            val results = queue.retryAll { Result.success(Unit) }

            results.size == count && results.all { it.isSuccess } && queue.isEmpty()
        }
    }

    @Test
    fun retryAllKeepsFailedItemsWithIncrementedRetryCount() = runTest {
        forAll(Arb.int(1..10)) { count ->
            val queue = createQueue()

            repeat(count) { i ->
                queue.enqueue(createItem(id = "item-$i"))
            }

            // All items fail during retry
            queue.retryAll { Result.failure(RuntimeException("offline")) }

            // All items should remain in queue with retryCount = 1
            val items = queue.items()
            items.size == count && items.all { it.retryCount == 1 }
        }
    }

    @Test
    fun retryAllHandlesPartialSuccess() = runTest {
        val typeArb = Arb.of(QueueItemType.TRANSACTION, QueueItemType.PROOF, QueueItemType.SUBMISSION)

        forAll(Arb.int(2..8), typeArb) { count, type ->
            val queue = createQueue()

            repeat(count) { i ->
                queue.enqueue(createItem(id = "item-$i", type = type))
            }

            // Even-indexed items succeed, odd-indexed items fail
            var index = 0
            queue.retryAll { item ->
                val result =
                    if (item.id.substringAfter("-").toInt() % 2 == 0) {
                        Result.success(Unit)
                    } else {
                        Result.failure(RuntimeException("failed"))
                    }
                index++
                result
            }

            // Only failed items (odd-indexed) should remain
            val remainingItems = queue.items()
            val expectedRemaining = (0 until count).count { it % 2 != 0 }
            remainingItems.size == expectedRemaining &&
                remainingItems.all { it.retryCount == 1 }
        }
    }

    @Test
    fun dequeueReturnsNullWhenEmpty() = runTest {
        forAll(Arb.int(1..5)) { _ ->
            val queue = createQueue()
            queue.dequeue() == null
        }
    }

    @Test
    fun multipleRetryRoundsIncrementRetryCount() = runTest {
        forAll(Arb.int(1..5)) { retryRounds ->
            val queue = createQueue()
            queue.enqueue(createItem(id = "persistent-item"))

            // Retry multiple rounds, all failing
            repeat(retryRounds) {
                queue.retryAll { Result.failure(RuntimeException("still offline")) }
            }

            // Retry count should equal the number of retry rounds
            val item = queue.items().first()
            item.retryCount == retryRounds && queue.size() == 1
        }
    }

    @Test
    fun queueItemTypeIsPreserved() = runTest {
        val typeArb = Arb.of(QueueItemType.TRANSACTION, QueueItemType.PROOF, QueueItemType.SUBMISSION)

        forAll(typeArb, Arb.string(4..12)) { type, id ->
            val queue = createQueue()
            val item = createItem(id = id, type = type)
            queue.enqueue(item)

            val dequeued = queue.dequeue()
            dequeued != null && dequeued.type == type && dequeued.id == id
        }
    }
}
