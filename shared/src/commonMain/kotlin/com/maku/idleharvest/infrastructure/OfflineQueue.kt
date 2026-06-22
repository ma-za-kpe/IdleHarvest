package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.PrivacyVault
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Queue for pending transactions, proofs, and submissions during offline periods.
 *
 * Key behaviors:
 * - **Order preservation**: Items are dequeued in FIFO order (first enqueued = first retried).
 * - **Persistence**: Queue state is persisted to [PrivacyVault] for crash recovery.
 * - **Automatic retry**: When connectivity returns, all queued items can be retried via [retryAll].
 * - **Retry tracking**: Each item tracks its retry count to support backoff strategies.
 *
 * Validates: Requirements 16.3
 */
class OfflineQueue(
    private val vault: PrivacyVault,
    private val clock: () -> Long = { currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val queue = mutableListOf<QueuedItem>()

    /**
     * Add an item to the end of the offline queue.
     * The item is also persisted to the vault for crash recovery.
     */
    suspend fun enqueue(item: QueuedItem) {
        queue.add(item)
        persistQueue()
    }

    /**
     * Remove and return the first (oldest) item from the queue.
     * Returns null if the queue is empty.
     */
    suspend fun dequeue(): QueuedItem? {
        if (queue.isEmpty()) return null
        val item = queue.removeAt(0)
        persistQueue()
        return item
    }

    /**
     * Peek at the first item without removing it.
     */
    fun peek(): QueuedItem? = queue.firstOrNull()

    /**
     * Current number of items in the queue.
     */
    fun size(): Int = queue.size

    /**
     * Whether the queue is empty.
     */
    fun isEmpty(): Boolean = queue.isEmpty()

    /**
     * Get all items currently in the queue (read-only snapshot).
     */
    fun items(): List<QueuedItem> = queue.toList()

    /**
     * Retry all queued items using the provided executor function.
     * Successfully processed items are removed from the queue.
     * Failed items remain in the queue with incremented retry count.
     *
     * @param executor Suspend function that processes a single queued item.
     *                 Returns [Result.success] if the item was processed, [Result.failure] otherwise.
     * @return List of results corresponding to each item in queue order.
     */
    suspend fun retryAll(executor: suspend (QueuedItem) -> Result<Unit>): List<Result<Unit>> {
        val results = mutableListOf<Result<Unit>>()
        val snapshot = queue.toList()
        val toRemove = mutableListOf<QueuedItem>()

        for (item in snapshot) {
            val result = executor(item)
            results.add(result)

            if (result.isSuccess) {
                toRemove.add(item)
            } else {
                // Increment retry count for failed items
                val index = queue.indexOf(item)
                if (index >= 0) {
                    queue[index] = item.copy(retryCount = item.retryCount + 1)
                }
            }
        }

        // Remove successfully processed items
        queue.removeAll(toRemove.toSet())
        persistQueue()

        return results
    }

    /**
     * Restore queue from vault persistence (for crash recovery).
     */
    suspend fun restoreFromVault() {
        val stored = vault.retrieve(QUEUE_VAULT_KEY).getOrNull() ?: return
        val decoded = stored.decodeToString()
        if (decoded.isBlank()) return

        val items = try {
            json.decodeFromString<List<QueuedItem>>(decoded)
        } catch (_: Exception) {
            emptyList()
        }
        queue.clear()
        queue.addAll(items)
    }

    /**
     * Clear all items from the queue.
     */
    suspend fun clear() {
        queue.clear()
        persistQueue()
    }

    /**
     * Persist the current queue state to the Privacy_Vault.
     */
    private suspend fun persistQueue() {
        val encoded = json.encodeToString(queue.toList())
        vault.store(QUEUE_VAULT_KEY, encoded.encodeToByteArray())
    }

    companion object {
        /** Key used for vault persistence. */
        const val QUEUE_VAULT_KEY = "offline_queue_state"
    }
}

/**
 * A single item queued for later processing when connectivity returns.
 */
@Serializable
data class QueuedItem(
    /** Unique identifier for this queued item. */
    val id: String,
    /** The category of queued operation. */
    val type: QueueItemType,
    /** Serialized payload for the operation. */
    val payload: ByteArray,
    /** Timestamp when the item was originally enqueued. */
    val enqueuedAt: Long,
    /** Number of retry attempts made so far. */
    val retryCount: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as QueuedItem
        return id == other.id &&
            type == other.type &&
            payload.contentEquals(other.payload) &&
            enqueuedAt == other.enqueuedAt &&
            retryCount == other.retryCount
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + enqueuedAt.hashCode()
        result = 31 * result + retryCount
        return result
    }
}

/**
 * Type of operation stored in the offline queue.
 */
@Serializable
enum class QueueItemType {
    /** A pending financial transaction (payout request). */
    TRANSACTION,
    /** A pending DePIN contribution proof. */
    PROOF,
    /** A pending submission (metrics, crash reports, etc.). */
    SUBMISSION,
}
